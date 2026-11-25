package dev.lmg.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Checks download/manifest.json on GitHub once per launch, in the background. */
public final class UpdateChecker {
	private static final String MANIFEST_URL =
			"https://raw.githubusercontent.com/lmgoffical/Lmg-Client/claude/modest-cray-bg98zr/download/manifest.json";

	private static volatile String newerVersion;

	private UpdateChecker() {}

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(LmgClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
	}

	/** The newer version that's available, or null if you're up to date (or it couldn't check). */
	public static String newerVersion() {
		return newerVersion;
	}

	public static void start() {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", LmgClient.CLIENT_NAME + "/" + currentVersion())
				.GET().build();
		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					if (response.statusCode() != 200) return;
					JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
					String latest = json.get("version").getAsString();
					if (isNewer(latest, currentVersion())) {
						newerVersion = latest;
						LmgClient.LOGGER.info("Update available: {} -> {}", currentVersion(), latest);
					}
				})
				.exceptionally(e -> {
					LmgClient.LOGGER.debug("Update check failed", e);
					return null;
				});
	}

	/** Compares versions like 0.10.2 and 0.9.0 number by number. */
	static boolean isNewer(String candidate, String current) {
		String[] a = candidate.split("[.+-]");
		String[] b = current.split("[.+-]");
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? parse(a[i]) : 0;
			int y = i < b.length ? parse(b[i]) : 0;
			if (x != y) return x > y;
		}
		return false;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
