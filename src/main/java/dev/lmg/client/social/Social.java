package dev.lmg.client.social;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Util;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Friends: who's online, what they're playing, and joining them.
 *
 * Signing in works like pet sharing: the game asks Mojang to "join" a random server id, and our
 * server asks Mojang whether you did. Your Minecraft login token only ever goes to Mojang. We get
 * back a session key (kept in config/lmgclient-social.json) that the friends functions check.
 *
 * Only friends see what you're doing, and only friends see your server (and only if "Show my
 * server" is on). Nothing is shared until you switch friends on.
 */
public final class Social {
	static final String BASE = "https://fieakrewvbyubtyxpqhr.supabase.co";
	static final String KEY = "sb_publishable__GJAqv2bWvhtt3UdGgAr2Q_NFI8TyG1";
	static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
			.proxy(ProxySelector.getDefault()).build();
	private static final Gson GSON = new Gson();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lmgclient-social.json");

	/** One friend, as the server last described them. */
	public record Friend(UUID uuid, String name, boolean online, String activity, String server, String serverName, Instant lastSeen) {
		/** "Playing on Hypixel", "In singleplayer", "Last online 3h ago" ... */
		public String status() {
			if (!online) return lastSeen == null ? "Offline" : "Last online " + ago(lastSeen);
			if (activity == null) return "Online";
			return switch (activity) {
				case "server" -> server != null ? "Playing on " + (serverName != null && !serverName.isBlank() ? serverName : server) : "Playing on a server";
				case "singleplayer" -> "In singleplayer";
				case "lan" -> "In a LAN world";
				case "realms" -> "Playing on Realms";
				default -> "In the menus";
			};
		}

		public boolean joinable() {
			return online && "server".equals(activity) && server != null && !server.isBlank();
		}
	}

	public record Person(UUID uuid, String name) {}

	/** A friend asking you to come and play on their server. */
	public record Invite(UUID uuid, String name, String server, String serverName, Instant at) {
		public String where() {
			return serverName != null && !serverName.isBlank() ? serverName : server;
		}
	}

	/** Everything the Friends screen shows. */
	public record State(List<Friend> friends, List<Person> incoming, List<Person> outgoing, UUID partner, UUID partnerRequest,
						List<Invite> invites) {
		static final State EMPTY = new State(List.of(), List.of(), List.of(), null, null, List.of());

		public Friend friend(UUID id) {
			for (Friend f : friends) if (f.uuid.equals(id)) return f;
			return null;
		}
	}

	public enum Status { OFF, SIGNING_IN, ONLINE, NEEDS_ACCOUNT, ERROR }

	private static volatile State state = State.EMPTY;
	private static volatile Status status = Status.OFF;
	private static volatile String problem = "";
	private static volatile UUID me;
	private static volatile String token;
	private static volatile boolean busy, loaded, firstSync = true;
	private static long lastSync, lastSignInAttempt;
	private static String lastActivityKey = "";
	private static final List<Runnable> LISTENERS = new ArrayList<>();

	private Social() {}

	// ---- read --------------------------------------------------------------------------------------

	public static State state() {
		return state;
	}

	public static Status status() {
		return status;
	}

	public static String problem() {
		return problem;
	}

	public static boolean enabled() {
		return LmgConfig.get().friendsEnabled;
	}

	public static boolean signedIn() {
		return token != null && status == Status.ONLINE;
	}

	public static UUID me() {
		return me;
	}

	public static String token() {
		return token;
	}

	/** Your partner, if you both said yes. */
	public static UUID partner() {
		return enabled() ? state.partner() : null;
	}

	public static boolean isFriend(UUID id) {
		return enabled() && state.friend(id) != null;
	}

	public static int onlineCount() {
		int n = 0;
		for (Friend f : state.friends()) if (f.online()) n++;
		return n;
	}

	/** Called whenever the friends state changes (on the render thread). */
	public static void onChange(Runnable r) {
		LISTENERS.add(r);
	}

	// ---- switching on and off -----------------------------------------------------------------------

	public static void enable() {
		LmgConfig.get().friendsEnabled = true;
		LmgConfig.save();
		status = Status.SIGNING_IN;
		lastSignInAttempt = 0;
		lastSync = 0;
	}

	/**
	 * Turns friends off and deletes everything about you on our server: your status, friends,
	 * requests, partner and sessions. (Your friends' lists just lose you.)
	 */
	public static void disable() {
		LmgConfig.get().friendsEnabled = false;
		LmgConfig.get().matchPartner = false;
		LmgConfig.save();
		String t = token;
		if (t != null) Util.nonCriticalIoPool().execute(() -> rpc("lmg_social_forget", tokenBody(t)));
		forgetSession();
		loaded = true;
		firstSync = true;
		state = State.EMPTY;
		status = Status.OFF;
		changed();
	}

	/** Forgets friends on this computer only (the server side is deleted by "Delete all my Lmg data"). */
	public static void wipeLocal() {
		LmgConfig.get().friendsEnabled = false;
		LmgConfig.get().matchPartner = false;
		forgetSession();
		loaded = true;
		firstSync = true;
		state = State.EMPTY;
		status = Status.OFF;
		changed();
	}

	// ---- ticking -----------------------------------------------------------------------------------

	public static void tick(Minecraft mc) {
		if (!enabled()) {
			status = Status.OFF;
			return;
		}
		if (!loaded) loadSession(mc);
		long now = System.currentTimeMillis();
		if (token == null) {
			if (!busy && now - lastSignInAttempt > (status == Status.ERROR ? 60_000 : 5_000)) signIn(mc);
			return;
		}
		String activityKey = activityKey(mc);
		boolean activityChanged = !activityKey.equals(lastActivityKey);
		long every = mc.level != null ? 45_000 : 60_000;
		if (!busy && (activityChanged || now - lastSync > every)) {
			lastActivityKey = activityKey;
			sync(mc);
		}
	}

	/** Asks for fresh data now (e.g. when the Friends screen opens). */
	public static void refreshSoon() {
		if (System.currentTimeMillis() - lastSync > 4_000) lastSync = 0;
	}

	public static void onGameClosing() {
		String t = token;
		if (t == null || !enabled()) return;
		try {
			HttpRequest req = rpcRequest("lmg_social_offline", tokenBody(t)).timeout(Duration.ofSeconds(3)).build();
			HTTP.send(req, HttpResponse.BodyHandlers.discarding());
		} catch (Exception ignored) {
		}
	}

	// ---- what you're doing -------------------------------------------------------------------------

	record Activity(String kind, String server, String serverName) {}

	static Activity activity(Minecraft mc) {
		if (mc.level == null) return new Activity("menu", null, null);
		if (mc.hasSingleplayerServer()) {
			var sp = mc.getSingleplayerServer();
			return new Activity(sp != null && sp.isPublished() ? "lan" : "singleplayer", null, null);
		}
		ServerData data = mc.getCurrentServer();
		if (data == null) return new Activity("server", null, null);
		if (data.isRealm()) return new Activity("realms", null, null);
		if (data.type() == ServerData.Type.LAN) return new Activity("lan", null, null);
		return new Activity("server", data.ip, data.name);
	}

	private static String activityKey(Minecraft mc) {
		Activity a = activity(mc);
		LmgConfig c = LmgConfig.get();
		return a.kind + "|" + a.server + "|" + c.friendsShowServer + "|" + c.friendsAppearOffline;
	}

	/** The server you're on, cleaned up so the same server compares equal ("Play.Example.net:25565" = "play.example.net"). */
	public static String normalizeServer(String address) {
		if (address == null) return null;
		String s = address.trim().toLowerCase(Locale.ROOT);
		if (s.endsWith(":25565")) s = s.substring(0, s.length() - 6);
		while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
		return s;
	}

	// ---- network -----------------------------------------------------------------------------------

	private static void loadSession(Minecraft mc) {
		loaded = true;
		try {
			if (!Files.exists(FILE)) return;
			JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			UUID id = UUID.fromString(o.get("uuid").getAsString());
			Instant expires = Instant.parse(o.get("expires").getAsString());
			User user = mc.getUser();
			// a saved session only counts for the account it was made for, and not when it's nearly out
			if (user != null && id.equals(user.getProfileId()) && expires.isAfter(Instant.now().plus(Duration.ofDays(1)))) {
				me = id;
				token = o.get("token").getAsString();
				status = Status.ONLINE;
			}
		} catch (Exception e) {
			LmgClient.LOGGER.debug("Couldn't read the friends session", e);
		}
	}

	private static void saveSession(UUID id, String t, String expires) {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("uuid", id.toString());
			o.addProperty("token", t);
			o.addProperty("expires", expires);
			Files.writeString(FILE, o.toString());
		} catch (Exception e) {
			LmgClient.LOGGER.debug("Couldn't save the friends session", e);
		}
	}

	private static void forgetSession() {
		token = null;
		try {
			Files.deleteIfExists(FILE);
		} catch (Exception ignored) {
		}
	}

	private static void signIn(Minecraft mc) {
		User user = mc.getUser();
		lastSignInAttempt = System.currentTimeMillis();
		if (user == null || user.getAccessToken() == null || user.getAccessToken().length() < 10) {
			status = Status.NEEDS_ACCOUNT;
			problem = "Friends need a real Minecraft account.";
			return;
		}
		busy = true;
		status = Status.SIGNING_IN;
		byte[] b = new byte[20];
		new SecureRandom().nextBytes(b);
		String serverId = HexFormat.of().formatHex(b);
		Util.nonCriticalIoPool().execute(() -> {
			try {
				// 1) prove it's your account (only Mojang sees your login token)
				mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
				// 2) get a session key from our server
				JsonObject body = new JsonObject();
				body.addProperty("action", "login");
				body.addProperty("username", user.getName());
				body.addProperty("serverId", serverId);
				HttpResponse<String> res = HTTP.send(function(body).build(), HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 200) {
					JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
					UUID id = UUID.fromString(o.get("uuid").getAsString());
					String t = o.get("token").getAsString();
					saveSession(id, t, o.get("expires").getAsString());
					if (o.has("uid") && !o.get("uid").isJsonNull()) {
						int uid = o.get("uid").getAsInt();
						mc.execute(() -> {
							LmgConfig.get().myUid = uid;
							LmgConfig.save();
						});
					}
					me = id;
					token = t;
					status = Status.ONLINE;
					problem = "";
					lastSync = 0;
				} else {
					status = Status.ERROR;
					problem = res.statusCode() == 403 ? "Mojang couldn't confirm your account. Try again in a minute."
							: "Couldn't sign in to friends right now (" + res.statusCode() + ").";
				}
			} catch (Exception e) {
				status = Status.ERROR;
				problem = "Couldn't reach Mojang or the Lmg server.";
				LmgClient.LOGGER.debug("Friends sign-in failed", e);
			} finally {
				busy = false;
			}
		});
	}

	private static void sync(Minecraft mc) {
		String t = token;
		if (t == null) return;
		busy = true;
		lastSync = System.currentTimeMillis();
		Activity a = activity(mc);
		LmgConfig c = LmgConfig.get();
		JsonObject body = tokenBody(t);
		body.addProperty("p_activity", a.kind);
		body.addProperty("p_server", a.server);
		body.addProperty("p_server_name", a.serverName);
		body.addProperty("p_share_server", c.friendsShowServer);
		body.addProperty("p_appear_offline", c.friendsAppearOffline);
		Util.nonCriticalIoPool().execute(() -> {
			try {
				JsonElement res = rpc("lmg_social_sync", body);
				if (res != null && res.isJsonObject()) mc.execute(() -> apply(mc, res.getAsJsonObject()));
			} finally {
				busy = false;
			}
		});
	}

	/** Runs one of the friends functions and hands back the new state (or an error message). */
	private static void act(String fn, JsonObject args, Consumer<String> done) {
		String t = token;
		if (t == null) {
			done.accept("Not signed in yet.");
			return;
		}
		JsonObject body = tokenBody(t);
		args.entrySet().forEach(e -> body.add(e.getKey(), e.getValue()));
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.supplyAsync(() -> rpc(fn, body), Util.nonCriticalIoPool()).thenAccept(res -> mc.execute(() -> {
			if (res == null || !res.isJsonObject()) {
				done.accept("Couldn't reach the Lmg server.");
				return;
			}
			JsonObject o = res.getAsJsonObject();
			if (o.has("state")) apply(mc, o.getAsJsonObject("state"));
			done.accept(o.has("error") ? errorText(o.get("error").getAsString(), args) : null);
		}));
	}

	private static String errorText(String code, JsonObject args) {
		String name = args.has("p_name") ? args.get("p_name").getAsString() : "They";
		return switch (code) {
			case "not_found" -> name + " hasn't used Lmg Client yet (or turned friends on).";
			case "self" -> "That's you!";
			case "bad_name" -> "That isn't a Minecraft name.";
			case "too_many" -> "You've got a lot of friends. Remove some first.";
			case "not_friends" -> "You need to be friends first.";
			case "no_request" -> "That request isn't there any more.";
			case "not_on_server" -> "Join a server first (with Show my server on), then invite.";
			default -> "Something went wrong (" + code + ").";
		};
	}

	/** Invites friends to the server you're on. {@code done} gets null on success, or what went wrong. */
	public static void invite(java.util.Collection<UUID> ids, Consumer<String> done) {
		if (ids.isEmpty()) {
			done.accept("Nobody to invite.");
			return;
		}
		JsonObject a = new JsonObject();
		com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
		ids.forEach(id -> arr.add(id.toString()));
		a.add("p_uuids", arr);
		act("lmg_social_invite", a, done);
	}

	private static final java.util.Set<String> dismissed = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Hides an invite (just on this computer; it runs out on its own after 5 minutes). */
	public static void dismiss(Invite i) {
		dismissed.add(i.uuid() + "@" + (i.at() == null ? "" : i.at().toString()));
		State s = state;
		List<Invite> left = new ArrayList<>(s.invites());
		left.remove(i);
		state = new State(s.friends(), s.incoming(), s.outgoing(), s.partner(), s.partnerRequest(), left);
		changed();
	}

	/** Your own nickname for a friend, if you gave one: "Mia (Mia_Builds)". */
	public static String nameOf(UUID id, String name) {
		String nick = LmgConfig.get().friendNicknames.get(id.toString());
		return nick == null || nick.isBlank() ? name : nick + " (" + name + ")";
	}

	public static String nickname(UUID id) {
		return LmgConfig.get().friendNicknames.getOrDefault(id.toString(), "");
	}

	public static void setNickname(UUID id, String nick) {
		String n = nick == null ? "" : nick.trim();
		if (n.length() > 20) n = n.substring(0, 20);
		if (n.isEmpty()) LmgConfig.get().friendNicknames.remove(id.toString());
		else LmgConfig.get().friendNicknames.put(id.toString(), n);
		LmgConfig.save();
		changed();
	}

	public static void add(String name, Consumer<String> done) {
		JsonObject a = new JsonObject();
		a.addProperty("p_name", name.trim());
		act("lmg_social_add", a, done);
	}

	public static void accept(UUID id, Consumer<String> done) {
		JsonObject a = new JsonObject();
		a.addProperty("p_uuid", id.toString());
		act("lmg_social_accept", a, done);
	}

	public static void remove(UUID id, Consumer<String> done) {
		JsonObject a = new JsonObject();
		a.addProperty("p_uuid", id.toString());
		act("lmg_social_remove", a, done);
	}

	/** Ask (or say yes to) a friend as partner. {@code null} ends it. */
	public static void partner(UUID id, Consumer<String> done) {
		JsonObject a = new JsonObject();
		if (id == null) a.add("p_uuid", com.google.gson.JsonNull.INSTANCE);
		else a.addProperty("p_uuid", id.toString());
		act("lmg_social_partner", a, done);
	}

	// ---- applying new data ---------------------------------------------------------------------------

	private static void apply(Minecraft mc, JsonObject o) {
		State old = state;
		List<Friend> friends = new ArrayList<>();
		for (JsonElement e : arr(o, "friends")) {
			JsonObject f = e.getAsJsonObject();
			friends.add(new Friend(UUID.fromString(f.get("uuid").getAsString()), f.get("name").getAsString(),
					bool(f, "online"), str(f, "activity"), str(f, "server"), str(f, "serverName"), instant(f, "lastSeen")));
		}
		// online first, then by name
		friends.sort((x, y) -> x.online() != y.online() ? (x.online() ? -1 : 1) : x.name().compareToIgnoreCase(y.name()));
		List<Invite> invites = new ArrayList<>();
		for (JsonElement e : arr(o, "invites")) {
			JsonObject i = e.getAsJsonObject();
			Instant at = instant(i, "at");
			if (dismissed.contains(i.get("uuid").getAsString() + "@" + (at == null ? "" : at.toString()))) continue;
			invites.add(new Invite(UUID.fromString(i.get("uuid").getAsString()), i.get("name").getAsString(),
					str(i, "server"), str(i, "serverName"), at));
		}
		State next = new State(friends, people(o, "incoming"), people(o, "outgoing"), uuid(o, "partner"), uuid(o, "partnerRequest"), invites);
		state = next;
		if (!firstSync) notifyChanges(mc, old, next);
		firstSync = false;
		changed();
	}

	/** Little pop-ups: a friend came online, joined your server, sent a request. */
	private static void notifyChanges(Minecraft mc, State old, State next) {
		if (!LmgConfig.get().friendsToasts) return;
		Map<UUID, Friend> before = new HashMap<>();
		for (Friend f : old.friends()) before.put(f.uuid(), f);
		String myServer = normalizeServer(activity(mc).server());
		for (Friend f : next.friends()) {
			Friend was = before.get(f.uuid());
			if (was == null) {
				if (old.outgoing().stream().anyMatch(p -> p.uuid().equals(f.uuid())))
					FriendToast.show(f.uuid(), f.name(), "said yes! You're friends now.");
				continue;
			}
			if (f.online() && !was.online()) {
				FriendToast.show(f.uuid(), f.name(), f.joinable() ? f.status() : "is online");
			} else if (f.joinable() && myServer != null && myServer.equals(normalizeServer(f.server()))
					&& !myServer.equals(normalizeServer(was.server()))) {
				FriendToast.show(f.uuid(), f.name(), "joined your server");
			}
		}
		for (Person p : next.incoming()) {
			if (old.incoming().stream().noneMatch(q -> q.uuid().equals(p.uuid())))
				FriendToast.show(p.uuid(), p.name(), "wants to be friends");
		}
		for (Invite i : next.invites()) {
			if (old.invites().stream().noneMatch(q -> q.uuid().equals(i.uuid()) && java.util.Objects.equals(q.at(), i.at())))
				FriendToast.show(i.uuid(), i.name(), "invites you to " + i.where() + " - press O");
		}
		if (next.partnerRequest() != null && !next.partnerRequest().equals(old.partnerRequest())) {
			Friend f = next.friend(next.partnerRequest());
			if (f != null) FriendToast.show(f.uuid(), f.name(), "asked you to be partners ❤");
		}
		if (next.partner() != null && !next.partner().equals(old.partner())) {
			Friend f = next.friend(next.partner());
			if (f != null) FriendToast.show(f.uuid(), f.name(), "is your partner now ❤");
		}
	}

	private static void changed() {
		for (Runnable r : LISTENERS) r.run();
	}

	// ---- json and http helpers ---------------------------------------------------------------------

	private static JsonObject tokenBody(String t) {
		JsonObject body = new JsonObject();
		body.addProperty("p_token", t);
		return body;
	}

	private static HttpRequest.Builder rpcRequest(String fn, JsonObject body) {
		return HttpRequest.newBuilder(URI.create(BASE + "/rest/v1/rpc/" + fn))
				.header("apikey", KEY).header("Content-Type", "application/json").timeout(Duration.ofSeconds(12))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
	}

	static HttpRequest.Builder function(JsonObject body) {
		return HttpRequest.newBuilder(URI.create(BASE + "/functions/v1/social"))
				.header("apikey", KEY).header("Content-Type", "application/json").timeout(Duration.ofSeconds(15))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
	}

	/** Calls a database function; null if it failed. A dead session signs you in again. */
	static JsonElement rpc(String fn, JsonObject body) {
		try {
			HttpResponse<String> res = HTTP.send(rpcRequest(fn, body).build(), HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() == 200 || res.statusCode() == 204) {
				return res.body().isBlank() ? JsonParser.parseString("{}") : JsonParser.parseString(res.body());
			}
			if (res.body().contains("bad_session")) {
				forgetSession();
				status = Status.SIGNING_IN;
				lastSignInAttempt = 0;
			}
		} catch (Exception e) {
			LmgClient.LOGGER.debug("Friends request {} failed", fn, e);
		}
		return null;
	}

	private static JsonArray arr(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	private static boolean bool(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
	}

	private static UUID uuid(JsonObject o, String k) {
		String s = str(o, k);
		return s == null ? null : UUID.fromString(s);
	}

	private static Instant instant(JsonObject o, String k) {
		String s = str(o, k);
		try {
			return s == null ? null : java.time.OffsetDateTime.parse(s).toInstant();
		} catch (Exception e) {
			return null;
		}
	}

	private static List<Person> people(JsonObject o, String k) {
		List<Person> out = new ArrayList<>();
		for (JsonElement e : arr(o, k)) {
			JsonObject p = e.getAsJsonObject();
			out.add(new Person(UUID.fromString(p.get("uuid").getAsString()), p.get("name").getAsString()));
		}
		return out;
	}

	static String ago(Instant t) {
		long s = Math.max(0, Duration.between(t, Instant.now()).getSeconds());
		if (s < 90) return "just now";
		if (s < 3600) return (s / 60) + "m ago";
		if (s < 86400) return (s / 3600) + "h ago";
		if (s < 86400 * 30) return (s / 86400) + "d ago";
		return "a while ago";
	}

	public static HttpClient http() {
		return HTTP;
	}

	public static String publishableKey() {
		return KEY;
	}

	public static String sha256Hex(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ---- for the in-game tests only (they can't sign in to Mojang) ------------------------------

	static void testSession(UUID id, String t) {
		loaded = true;
		me = id;
		token = t;
		status = Status.ONLINE;
		lastSync = 0;
	}
}
