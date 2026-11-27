package dev.lmg.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.UpdateChecker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Short share codes like "LMG-ABC234", stored in the Lmg Client Supabase database.
 *
 * The database only exposes two functions (share_config / get_config); the table itself
 * can't be read or changed directly. The key below is a *publishable* key, which is meant
 * to ship inside apps. If the server can't be reached, the long "LMG1:..." codes still work.
 */
public final class CloudCodes {
	private static final String API = "https://fieakrewvbyubtyxpqhr.supabase.co/rest/v1/rpc/";
	private static final String KEY = "sb_publishable__GJAqv2bWvhtt3UdGgAr2Q_NFI8TyG1";
	private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private CloudCodes() {}

	/** True for things like "LMG-ABC234", "lmg abc234" or "ABC234". */
	public static boolean isShortCode(String text) {
		if (text == null) return false;
		String s = text.toUpperCase().replaceAll("[^A-Z0-9]", "");
		if (s.length() == 9 && s.startsWith("LMG")) s = s.substring(3);
		if (s.length() != 6) return false;
		for (char ch : s.toCharArray()) if (ALPHABET.indexOf(ch) < 0) return false;
		return true;
	}

	/**
	 * Uploads a long code; completes with the short code, e.g. "LMG-ABC234". Each share gets a
	 * random secret kept only on this computer, so "Delete all my Lmg data" can remove it later.
	 * Personal settings are taken out of the code first, whoever made it.
	 */
	public static CompletableFuture<String> share(String name, String longCode) {
		String clean;
		try {
			clean = ConfigProfiles.scrub(longCode);
		} catch (IllegalArgumentException e) {
			return CompletableFuture.failedFuture(e);
		}
		byte[] raw = new byte[32];
		new java.security.SecureRandom().nextBytes(raw);
		String owner = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		rememberKey(owner);
		JsonObject body = new JsonObject();
		body.addProperty("p_name", name);
		body.addProperty("p_data", clean);
		body.addProperty("p_owner", owner);
		return call("share_config", body).thenApply(json -> "LMG-" + json.getAsString());
	}

	// ---- the secrets for configs you shared ----------------------------------------------------

	private static final java.nio.file.Path KEYS = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
			.resolve(LmgClient.MOD_ID + "-shared-keys.json");

	private static synchronized java.util.List<String> keys() {
		java.util.List<String> out = new java.util.ArrayList<>();
		try {
			if (java.nio.file.Files.exists(KEYS)) {
				for (JsonElement e : JsonParser.parseString(java.nio.file.Files.readString(KEYS)).getAsJsonArray()) out.add(e.getAsString());
			}
		} catch (Exception e) {
			LmgClient.LOGGER.debug("Couldn't read shared config keys", e);
		}
		return out;
	}

	private static synchronized void rememberKey(String key) {
		java.util.List<String> all = keys();
		all.add(key);
		JsonArray arr = new JsonArray();
		all.forEach(arr::add);
		try {
			java.nio.file.Files.writeString(KEYS, arr.toString());
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Couldn't save a shared config key", e);
		}
	}

	/** Deletes every config code shared from this computer. Completes with how many went. */
	public static CompletableFuture<Integer> deleteMine() {
		java.util.List<String> all = keys();
		if (all.isEmpty()) return CompletableFuture.completedFuture(0);
		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		all.forEach(arr::add);
		body.add("p_keys", arr);
		return call("delete_my_configs", body).thenApply(json -> {
			try {
				java.nio.file.Files.deleteIfExists(KEYS);
			} catch (Exception e) {
				LmgClient.LOGGER.warn("Couldn't remove the shared config keys file", e);
			}
			return json.getAsInt();
		});
	}

	/** Downloads the config behind a short code. */
	public static CompletableFuture<ConfigProfiles.Profile> fetch(String shortCode) {
		JsonObject body = new JsonObject();
		body.addProperty("p_code", shortCode);
		return call("get_config", body).thenApply(json -> {
			JsonArray rows = json.getAsJsonArray();
			if (rows.isEmpty()) throw new IllegalArgumentException("No config found for that code");
			JsonObject row = rows.get(0).getAsJsonObject();
			ConfigProfiles.Profile p = ConfigProfiles.decode(row.get("data").getAsString());
			String name = row.has("name") && !row.get("name").isJsonNull() ? row.get("name").getAsString() : p.name();
			return new ConfigProfiles.Profile(ConfigProfiles.cleanName(name), p.config());
		});
	}

	private static CompletableFuture<JsonElement> call(String function, JsonObject body) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(API + function))
				.timeout(Duration.ofSeconds(12))
				.header("apikey", KEY)
				.header("Content-Type", "application/json")
				.header("User-Agent", LmgClient.CLIENT_NAME + "/" + UpdateChecker.currentVersion())
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.handle((response, error) -> {
					if (error != null) throw new IllegalStateException("Couldn't reach the Lmg server - check your internet");
					JsonElement json;
					try {
						json = JsonParser.parseString(response.body());
					} catch (Exception e) {
						throw new IllegalStateException("The Lmg server sent a bad reply");
					}
					if (response.statusCode() / 100 != 2) {
						String message = json.isJsonObject() && json.getAsJsonObject().has("message")
								? json.getAsJsonObject().get("message").getAsString() : "server error " + response.statusCode();
						if (response.statusCode() == 503 || response.statusCode() == 540) {
							message = "The Lmg server is asleep - ask the owner to wake it up";
						}
						throw new IllegalStateException(message);
					}
					return json;
				});
	}

	/** The message to show for a failed request. */
	public static String messageOf(Throwable t) {
		Throwable cause = t;
		while (cause.getCause() != null && (cause.getMessage() == null || cause instanceof java.util.concurrent.CompletionException)) {
			cause = cause.getCause();
		}
		String msg = cause.getMessage();
		return msg == null || msg.isBlank() ? "Something went wrong" : msg;
	}
}
