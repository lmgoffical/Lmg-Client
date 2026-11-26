package dev.lmg.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * UIDs: every account that claims one gets the next Lmg number, for good. The lower, the rarer:
 * 1-10 are Founders, up to 100 OG, up to 1000 Early. Claiming goes through the same Mojang check
 * as friends, so nobody can take yours. Other players' numbers are looked up for their name tags.
 */
public final class Uids {
	private static final Map<UUID, Integer> KNOWN = new ConcurrentHashMap<>();   // 0 = no UID
	private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();
	private static volatile boolean claiming, fetching;
	private static int ticks;

	private Uids() {}

	public static String tier(int uid) {
		if (uid <= 0) return "";
		if (uid <= 10) return "Founder";
		if (uid <= 100) return "OG";
		if (uid <= 1000) return "Early";
		return "";
	}

	public static int color(int uid) {
		if (uid <= 0) return 0x9DAAC0;
		if (uid <= 10) return 0xF2C14E;     // gold
		if (uid <= 100) return 0xFF8FC8;    // pink
		if (uid <= 1000) return 0x6FB6FF;   // blue
		return 0x9DAAC0;
	}

	/** "#7 Founder", or what to press to get one. */
	public static String myLabel() {
		int uid = LmgConfig.get().myUid;
		if (uid > 0) return "#" + uid + (tier(uid).isEmpty() ? "" : " " + tier(uid));
		return claiming ? "Checking..." : "Get mine";
	}

	/** A player's UID if we know it (looked up in the background), else null. */
	public static Integer of(UUID id) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && id.equals(mc.player.getUUID()) && LmgConfig.get().myUid > 0) return LmgConfig.get().myUid;
		Integer v = KNOWN.get(id);
		if (v == null) ASKED.putIfAbsent(id, 0L);
		return v == null || v == 0 ? null : v;
	}

	/** "Mia_Builds" -> "Mia_Builds #2" (gold for Founders). */
	public static Component withUid(Component name, int uid) {
		MutableComponent out = name.copy();
		out.append(Component.literal(" #" + uid).withStyle(Style.EMPTY.withColor(color(uid))));
		return out;
	}

	public static void remember(UUID id, int uid) {
		KNOWN.put(id, uid);
	}

	// ---- claiming yours ----------------------------------------------------------------------------

	public static void claim(Consumer<String> say) {
		LmgConfig c = LmgConfig.get();
		if (c.myUid > 0) {
			say.accept("You're #" + c.myUid + (tier(c.myUid).isEmpty() ? "." : " - " + tier(c.myUid) + "!"));
			return;
		}
		if (claiming) return;
		Minecraft mc = Minecraft.getInstance();
		User user = mc.getUser();
		if (user == null || user.getAccessToken() == null || user.getAccessToken().length() < 10) {
			say.accept("UIDs need a real Minecraft account.");
			return;
		}
		claiming = true;
		byte[] b = new byte[20];
		new SecureRandom().nextBytes(b);
		String serverId = HexFormat.of().formatHex(b);
		Util.nonCriticalIoPool().execute(() -> {
			try {
				// prove it's your account (only Mojang sees your login token)
				mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
				JsonObject body = new JsonObject();
				body.addProperty("action", "uid");
				body.addProperty("username", user.getName());
				body.addProperty("serverId", serverId);
				HttpResponse<String> res = Social.http().send(Social.function(body).build(), HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 200) {
					int uid = JsonParser.parseString(res.body()).getAsJsonObject().get("uid").getAsInt();
					mc.execute(() -> {
						LmgConfig.get().myUid = uid;
						LmgConfig.save();
						say.accept("You're #" + uid + (tier(uid).isEmpty() ? "." : " - " + tier(uid) + "!"));
					});
				} else {
					mc.execute(() -> say.accept("Couldn't get your UID right now (" + res.statusCode() + ")."));
				}
			} catch (Exception e) {
				LmgClient.LOGGER.debug("UID claim failed", e);
				mc.execute(() -> say.accept("Couldn't reach Mojang or the Lmg server."));
			} finally {
				claiming = false;
			}
		});
	}

	// ---- other players' numbers ----------------------------------------------------------------

	public static void tick(Minecraft mc) {
		if (++ticks % 100 != 0 || fetching || mc.level == null) return;
		long now = System.currentTimeMillis();
		// everyone near you, plus anyone something asked about (friends list)
		for (Player p : mc.level.players()) if (p != mc.player) ASKED.putIfAbsent(p.getUUID(), 0L);
		List<UUID> want = new ArrayList<>();
		for (var e : ASKED.entrySet()) {
			if (now - e.getValue() > 5 * 60_000) want.add(e.getKey());
			if (want.size() >= 100) break;
		}
		if (want.isEmpty()) return;
		for (UUID id : want) ASKED.put(id, now);
		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		want.forEach(id -> arr.add(id.toString()));
		body.add("p_uuids", arr);
		fetching = true;
		HttpRequest req = HttpRequest.newBuilder(URI.create(Social.BASE + "/rest/v1/rpc/lmg_uids_of"))
				.header("apikey", Social.KEY).header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		Social.http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
			fetching = false;
			if (err != null || res.statusCode() != 200) return;
			try {
				for (UUID id : want) KNOWN.putIfAbsent(id, 0);
				for (JsonElement el : JsonParser.parseString(res.body()).getAsJsonArray()) {
					JsonObject o = el.getAsJsonObject();
					KNOWN.put(UUID.fromString(o.get("uuid").getAsString()), o.get("uid").getAsInt());
				}
			} catch (Exception ex) {
				LmgClient.LOGGER.debug("Bad UID list", ex);
			}
		});
	}
}
