package dev.lmg.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who near you lets you climb on them. Everyone picks "Who can climb on me" (nobody, their
 * partner, their friends or everyone) - there's no asking. Every few seconds we ask the server
 * which of the Lmg players standing near you have a setting that includes you.
 */
public final class ClimbAllow {
	private static final Set<UUID> ALLOWED = ConcurrentHashMap.newKeySet();
	/** When each nearby player was last checked (so "no" is a real no, not "don't know yet"). */
	private static final Map<UUID, Long> CHECKED = new ConcurrentHashMap<>();
	private static int ticks;
	private static volatile boolean busy;

	private ClimbAllow() {}

	/** Does this player's setting let you climb on them? */
	public static boolean mayClimbOn(UUID owner) {
		if (owner == null) return false;
		if (PartnerPlay.testAllowed) return true;
		if (owner.equals(Social.partner()) && Social.state().partnerAllowsClimb()) return true;
		return ALLOWED.contains(owner);
	}

	/** True if we've asked the server about them lately (so a "no" means their setting says no). */
	public static boolean known(UUID owner) {
		Long at = CHECKED.get(owner);
		return at != null && System.currentTimeMillis() - at < 20_000;
	}

	public static void tick(Minecraft mc) {
		if (++ticks % 100 != 0 || busy || mc.level == null || mc.player == null) return;
		String token = Social.token();
		if (token == null) {
			ALLOWED.clear();
			return;
		}
		JsonArray ids = new JsonArray();
		for (Player p : mc.level.players()) {
			if (p != mc.player && p.distanceTo(mc.player) < 16 && ids.size() < 40) ids.add(p.getUUID().toString());
		}
		if (ids.isEmpty()) {
			ALLOWED.clear();
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("p_token", token);
		body.add("p_uuids", ids);
		busy = true;
		HttpRequest req = HttpRequest.newBuilder(URI.create(Social.BASE + "/rest/v1/rpc/lmg_climb_ok"))
				.header("apikey", Social.KEY).header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		Social.http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
			busy = false;
			if (err != null || res.statusCode() != 200) return;
			try {
				Set<UUID> now = ConcurrentHashMap.newKeySet();
				for (JsonElement e : JsonParser.parseString(res.body()).getAsJsonArray()) now.add(UUID.fromString(e.getAsString()));
				long t = System.currentTimeMillis();
				for (JsonElement e : ids) CHECKED.put(UUID.fromString(e.getAsString()), t);
				ALLOWED.clear();
				ALLOWED.addAll(now);
			} catch (Exception ex) {
				LmgClient.LOGGER.debug("Bad climb list", ex);
			}
		});
	}
}
