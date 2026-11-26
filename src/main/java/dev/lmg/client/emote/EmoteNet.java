package dev.lmg.client.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.social.Social;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Emotes between Lmg players on the same server.
 *
 * Sending goes through our server, which checks it's really you and signs the emote. Everyone
 * on the same server listens on a shared channel and only plays emotes with a valid signature,
 * so nobody can make your character do something you didn't. Nothing is sent in singleplayer.
 */
public final class EmoteNet {
	/** The public half of the server's emote key (the private half never leaves the server). */
	private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAYCxFwHkERE5rXERzXqKx14sbvX1tiz5GcYo30WQONlg=";
	private static final String SOCKET = "wss://fieakrewvbyubtyxpqhr.supabase.co/realtime/v1/websocket?vsn=1.0.0&apikey=";

	private static PublicKey key;
	private static volatile WebSocket socket;
	private static volatile String room, joinedRoom;
	private static volatile boolean connecting;
	private static long lastConnectTry, lastHeartbeat, lastCatchUp;
	private static int ref;

	private EmoteNet() {}

	/** The room for the server you're on (a hash, so the channel name doesn't say which server). */
	static String roomFor(Minecraft mc) {
		if (mc.level == null || mc.hasSingleplayerServer()) return null;
		var data = mc.getCurrentServer();
		if (data == null || data.isRealm()) return null;
		String server = Social.normalizeServer(data.ip);
		return server == null || server.isEmpty() ? null : Social.sha256Hex("lmg-room|" + server).substring(0, 16);
	}

	// ---- every tick ------------------------------------------------------------------------------

	public static void tick(Minecraft mc) {
		String want = Social.enabled() && Social.signedIn() ? roomFor(mc) : null;
		room = want;
		long now = System.currentTimeMillis();
		if (want == null) {
			if (socket != null) close();
			return;
		}
		if (socket == null) {
			if (!connecting && now - lastConnectTry > 10_000) connect();
			return;
		}
		if (!want.equals(joinedRoom)) join(want);
		if (now - lastHeartbeat > 25_000) {
			lastHeartbeat = now;
			sendText("{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"" + (++ref) + "\"}");
		}
		// players who were already sitting or dancing when you arrived
		if (now - lastCatchUp > 6_000 && mc.level != null) {
			lastCatchUp = now;
			catchUp(mc, want);
		}
	}

	private static void connect() {
		connecting = true;
		lastConnectTry = System.currentTimeMillis();
		Social.http().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(SOCKET + Social.publishableKey()), new Listener())
				.whenComplete((ws, err) -> {
					connecting = false;
					if (err != null) {
						LmgClient.LOGGER.debug("Emote channel couldn't connect", err);
						return;
					}
					socket = ws;
					joinedRoom = null;
					lastHeartbeat = System.currentTimeMillis();
				});
	}

	private static void join(String r) {
		if (joinedRoom != null) {
			sendText("{\"topic\":\"realtime:lmg-emotes-" + joinedRoom + "\",\"event\":\"phx_leave\",\"payload\":{},\"ref\":\"" + (++ref) + "\"}");
		}
		joinedRoom = r;
		sendText("{\"topic\":\"realtime:lmg-emotes-" + r + "\",\"event\":\"phx_join\",\"payload\":{\"config\":{\"broadcast\":{\"self\":false,\"ack\":false},"
				+ "\"presence\":{\"key\":\"\"},\"private\":false}},\"ref\":\"" + (++ref) + "\"}");
	}

	private static void close() {
		WebSocket ws = socket;
		socket = null;
		joinedRoom = null;
		if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
	}

	private static synchronized void sendText(String text) {
		WebSocket ws = socket;
		if (ws == null) return;
		try {
			ws.sendText(text, true).get();
		} catch (Exception e) {
			socket = null;
		}
	}

	// ---- sending ---------------------------------------------------------------------------------

	/** Tells the others on your server ({@code null} = you stopped a lasting emote). */
	public static void send(Emote e) {
		String r = room;
		String token = Social.token();
		if (r == null || token == null) return;
		JsonObject body = new JsonObject();
		body.addProperty("action", "emote");
		body.addProperty("token", token);
		body.addProperty("room", r);
		if (e == null) body.add("emote", com.google.gson.JsonNull.INSTANCE);
		else body.addProperty("emote", e.id);
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://fieakrewvbyubtyxpqhr.supabase.co/functions/v1/social"))
				.header("apikey", Social.publishableKey()).header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		Social.http().sendAsync(req, HttpResponse.BodyHandlers.discarding());
	}

	/**
	 * A partner emote. The server checks you're partners and that they said yes; if not,
	 * {@code refused} gets the reason. In singleplayer (no room) it just plays on your screen.
	 */
	public static void sendWith(Emote e, UUID with, java.util.function.Consumer<String> refused) {
		String r = room;
		String token = Social.token();
		if (r == null || token == null) return;
		JsonObject body = new JsonObject();
		body.addProperty("action", "emote");
		body.addProperty("token", token);
		body.addProperty("room", r);
		body.addProperty("emote", e.id);
		if (with != null) body.addProperty("with", with.toString());
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://fieakrewvbyubtyxpqhr.supabase.co/functions/v1/social"))
				.header("apikey", Social.publishableKey()).header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		Social.http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
			if (res.statusCode() == 200 || res.statusCode() == 429) return;
			String why = res.body() != null && res.body().contains("not_allowed") ? "Their setting doesn't let you climb on them."
					: res.body() != null && res.body().contains("no_partner") ? "You need a partner for that (Friends > Partner)."
					: "Couldn't do that right now.";
			refused.accept(why);
		});
	}

	private static HttpRequest function(JsonObject body) {
		return HttpRequest.newBuilder(URI.create("https://fieakrewvbyubtyxpqhr.supabase.co/functions/v1/social"))
				.header("apikey", Social.publishableKey()).header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
	}

	// ---- receiving -------------------------------------------------------------------------------

	private static final class Listener implements WebSocket.Listener {
		private final StringBuilder buf = new StringBuilder();

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			buf.append(data);
			if (last) {
				String msg = buf.toString();
				buf.setLength(0);
				try {
					handle(JsonParser.parseString(msg).getAsJsonObject());
				} catch (Exception e) {
					LmgClient.LOGGER.debug("Bad emote message", e);
				}
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			if (socket == ws) socket = null;
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			if (socket == ws) socket = null;
		}
	}

	static void handle(JsonObject msg) {
		if (!"broadcast".equals(str(msg, "event"))) return;
		JsonObject outer = msg.getAsJsonObject("payload");
		if (outer == null) return;
		String kind = str(outer, "event");
		JsonObject p = outer.getAsJsonObject("payload");
		if (p == null || kind == null) return;
		if (!"emote".equals(kind)) return;
		String u = str(p, "u"), e = str(p, "e"), r = str(p, "r"), s = str(p, "s");
		long t = p.has("t") ? p.get("t").getAsLong() : 0;
		if (u == null || e == null || r == null || s == null) return;
		// only this server's room, only fresh, only signed by our server
		if (!r.equals(room) || Math.abs(System.currentTimeMillis() - t) > 30_000) return;
		String w = str(p, "w");
		if (!verify("lmg-emote|" + u + "|" + e + "|" + r + "|" + t + (w != null ? "|" + w : ""), s)) {
			LmgClient.LOGGER.debug("Ignored an emote with a bad signature");
			return;
		}
		Emote emote = "stop".equals(e) ? null : Emote.byId(e);
		if (emote == null && !"stop".equals(e)) return;
		if (emote != null && emote.withPartner() && w == null) return;   // partner emotes always say who with
		Emotes.remote(UUID.fromString(u), emote, t, w == null ? null : UUID.fromString(w));
	}

	static boolean verify(String message, String signatureB64) {
		try {
			if (key == null) {
				key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
			}
			Signature sig = Signature.getInstance("Ed25519");
			sig.initVerify(key);
			sig.update(message.getBytes(StandardCharsets.UTF_8));
			return sig.verify(Base64.getDecoder().decode(signatureB64));
		} catch (Exception ex) {
			return false;
		}
	}

	/** Asks which nearby players are still sitting / dancing / hugging. */
	private static void catchUp(Minecraft mc, String r) {
		List<String> ids = new ArrayList<>();
		for (Player p : mc.level.players()) {
			if (p == mc.player || p.distanceTo(mc.player) > 48) continue;
			if (Emotes.get(p.getUUID()) == null) ids.add(p.getUUID().toString());
			if (ids.size() >= 60) break;
		}
		if (ids.isEmpty()) return;
		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		ids.forEach(arr::add);
		body.add("p_uuids", arr);
		body.addProperty("p_room", r);
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://fieakrewvbyubtyxpqhr.supabase.co/rest/v1/rpc/lmg_active_emotes"))
				.header("apikey", Social.publishableKey()).header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		Social.http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
			if (res.statusCode() != 200) return;
			try {
				for (JsonElement el : JsonParser.parseString(res.body()).getAsJsonArray()) {
					JsonObject o = el.getAsJsonObject();
					Emote e = Emote.byId(str(o, "emote"));
					if (e == null || !e.lasting()) continue;
					long started = java.time.OffsetDateTime.parse(str(o, "started_at")).toInstant().toEpochMilli();
					String with = str(o, "with_uuid");
					if (e.withPartner() && with == null) continue;
					Emotes.remote(UUID.fromString(str(o, "uuid")), e, started, with == null ? null : UUID.fromString(with));
				}
			} catch (Exception ex) {
				LmgClient.LOGGER.debug("Bad active emotes", ex);
			}
		});
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	/** For the in-game tests: feed a raw channel message through the same checks. */
	static void testHandle(String json, String testRoom) {
		room = testRoom;
		handle(JsonParser.parseString(json).getAsJsonObject());
	}

	static boolean testVerify(String m, String s) {
		return verify(m, s);
	}
}
