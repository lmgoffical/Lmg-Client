package dev.lmg.client.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.cosmetic.CosmeticModel;
import dev.lmg.client.cosmetic.CosmeticRegistry;
import dev.lmg.client.pet.PetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sharing pets and cosmetics with other Lmg players (only if you switch it on).
 *
 * Saving: the game asks Mojang to "join" a random server id (what it does when you join any
 * server), then our server asks Mojang "did this player just join that id?". That proves the
 * account is yours. Your login token only ever goes to Mojang.
 *
 * Seeing others: every few seconds the game asks the database for the Lmg players near you.
 */
public final class AvatarSync {
	private static final String BASE = "https://fieakrewvbyubtyxpqhr.supabase.co";
	private static final String KEY = "sb_publishable__GJAqv2bWvhtt3UdGgAr2Q_NFI8TyG1";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Gson GSON = new Gson();
	private static final SecureRandom RANDOM = new SecureRandom();

	/** What another player shared. */
	public record Shared(List<PetConfig> pets, Map<String, String> equipped, Map<String, String> colors) {}

	private static final Map<UUID, Shared> REMOTE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CHECKED = new ConcurrentHashMap<>();
	private static volatile boolean fetching, publishing;
	private static String lastSent;
	private static long lastPublishAttempt;
	private static int ticks;
	private static volatile String status = "";

	private AvatarSync() {}

	public static Shared get(UUID uuid) {
		return LmgConfig.get().showOthers ? REMOTE.get(uuid) : null;
	}

	/** What a player shared, even with "see others" off (for matching your partner's outfit). */
	public static Shared getAlways(UUID uuid) {
		return REMOTE.get(uuid);
	}

	/** Players to keep fresh even when they aren't near you (your partner). */
	private static final java.util.Set<UUID> WATCHED = ConcurrentHashMap.newKeySet();

	public static void watch(UUID uuid, boolean on) {
		if (uuid == null) return;
		if (on) WATCHED.add(uuid);
		else WATCHED.remove(uuid);
	}

	/** A short line for the menus: shared / not shared / what went wrong. */
	public static String status() {
		if (!LmgConfig.get().shareAvatar) return "Only you can see your pets and cosmetics.";
		return status.isEmpty() ? "Sharing..." : status;
	}

	public static void tick(Minecraft mc) {
		ticks++;
		if (ticks % 40 == 0) publishIfChanged(mc);
		if (ticks % 100 == 0 && mc.level != null && mc.player != null && (LmgConfig.get().showOthers || !WATCHED.isEmpty())) fetchNearby(mc);
	}

	// ---- seeing other players --------------------------------------------------------------------

	private static void fetchNearby(Minecraft mc) {
		if (fetching) return;
		long now = System.currentTimeMillis();
		List<UUID> want = new ArrayList<>();
		for (UUID id : WATCHED) {
			Long at = CHECKED.get(id);
			if (at == null || now - at > 30_000) want.add(id);
		}
		if (LmgConfig.get().showOthers) for (Player p : mc.level.players()) {
			if (p == mc.player || p.distanceTo(mc.player) > 64) continue;
			UUID id = p.getUUID();
			Long at = CHECKED.get(id);
			if ((at == null || now - at > 60_000) && !want.contains(id)) want.add(id);
			if (want.size() >= 40) break;
		}
		if (want.isEmpty()) return;
		for (UUID id : want) CHECKED.put(id, now);
		fetching = true;
		StringBuilder ids = new StringBuilder();
		for (UUID id : want) ids.append(ids.length() == 0 ? "" : ",").append(id);
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/rest/v1/player_avatars?select=uuid,data&uuid=in.(" + ids + ")"))
				.header("apikey", KEY).timeout(Duration.ofSeconds(10)).GET().build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
			fetching = false;
			if (err != null || res.statusCode() != 200) return;
			try {
				Map<UUID, Shared> found = new HashMap<>();
				for (JsonElement row : JsonParser.parseString(res.body()).getAsJsonArray()) {
					JsonObject o = row.getAsJsonObject();
					found.put(UUID.fromString(o.get("uuid").getAsString()), parse(o.getAsJsonObject("data")));
				}
				for (UUID id : want) {
					Shared s = found.get(id);
					if (s == null) REMOTE.remove(id);
					else REMOTE.put(id, s);
				}
			} catch (Exception e) {
				LmgClient.LOGGER.debug("Bad avatar data", e);
			}
		});
	}

	private static Shared parse(JsonObject data) {
		List<PetConfig> pets = new ArrayList<>();
		JsonArray arr = data.has("pets") ? data.getAsJsonArray("pets") : new JsonArray();
		for (JsonElement e : arr) {
			PetConfig p = GSON.fromJson(e, PetConfig.class);
			if (p != null) pets.add(p);
		}
		PetConfig.sanitize(pets);
		Map<String, String> equipped = new HashMap<>(), colors = new HashMap<>();
		if (data.has("equipped")) data.getAsJsonObject("equipped").entrySet().forEach(en -> equipped.put(en.getKey(), en.getValue().getAsString()));
		if (data.has("colors")) data.getAsJsonObject("colors").entrySet().forEach(en -> colors.put(en.getKey(), en.getValue().getAsString()));
		return new Shared(pets, equipped, colors);
	}

	// ---- sharing yours ---------------------------------------------------------------------------

	/** What we'd share right now (built-in cosmetics only; your own imported models stay on your PC). */
	private static JsonObject payload() {
		LmgConfig c = LmgConfig.get();
		JsonObject data = new JsonObject();
		data.add("pets", GSON.toJsonTree(c.pets));
		JsonObject equipped = new JsonObject(), colors = new JsonObject();
		if (!c.hideCosmetics) {
			for (Map.Entry<String, String> e : c.equipped.entrySet()) {
				CosmeticModel m = CosmeticRegistry.get(e.getValue());
				if (m == null || m.custom) continue;
				equipped.addProperty(e.getKey(), e.getValue());
				String color = c.cosmeticColors.get(e.getValue());
				if (color != null) colors.addProperty(e.getValue(), color);
			}
		}
		data.add("equipped", equipped);
		data.add("colors", colors);
		return data;
	}

	private static void publishIfChanged(Minecraft mc) {
		LmgConfig c = LmgConfig.get();
		if (publishing || System.currentTimeMillis() - lastPublishAttempt < 15_000) return;
		if (!c.shareAvatar) {
			if (c.avatarShared) send(mc, null);         // you switched sharing off: remove it from the database
			return;
		}
		String json = payload().toString();
		if (json.equals(lastSent) && c.avatarShared) return;
		send(mc, json);
	}

	private static void send(Minecraft mc, String json) {
		User user = mc.getUser();
		if (user == null || user.getAccessToken() == null || user.getAccessToken().length() < 10) {
			status = "Sharing needs a real Minecraft account.";
			return;
		}
		publishing = true;
		lastPublishAttempt = System.currentTimeMillis();
		String serverId = HexFormat.of().formatHex(randomBytes());
		net.minecraft.util.Util.nonCriticalIoPool().execute(() -> {
			try {
				// 1) prove it's your account (only Mojang sees your token)
				mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
				// 2) ask our server to save (or remove) it
				JsonObject body = new JsonObject();
				body.addProperty("username", user.getName());
				body.addProperty("serverId", serverId);
				if (json == null) body.addProperty("remove", true);
				else body.add("data", JsonParser.parseString(json));
				HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/functions/v1/avatar"))
						.header("apikey", KEY).header("Content-Type", "application/json").timeout(Duration.ofSeconds(15))
						.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
				HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 200) {
					mc.execute(() -> {
						LmgConfig.get().avatarShared = json != null;
						LmgConfig.save();
					});
					lastSent = json;
					status = json == null ? "" : "Shared. Other Lmg players can see your pets and cosmetics.";
				} else if (res.statusCode() == 429) {
					status = "Saving soon...";
				} else {
					status = "Couldn't share right now (" + res.statusCode() + ").";
				}
			} catch (Exception e) {
				status = "Couldn't reach Mojang or the Lmg server.";
				LmgClient.LOGGER.debug("Avatar sync failed", e);
			} finally {
				publishing = false;
			}
		});
	}

	private static byte[] randomBytes() {
		byte[] b = new byte[20];
		RANDOM.nextBytes(b);
		return b;
	}

	/** Forget everyone else's data (e.g. when you turn "see others" off). */
	public static void clearRemote() {
		REMOTE.clear();
		CHECKED.clear();
	}
}
