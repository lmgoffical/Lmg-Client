package dev.lmg.client.feature;

import dev.lmg.client.LmgClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * One-click cinematic shaders through Iris (which the Lmg installer adds).
 * Packs are downloaded from Modrinth - the same place the installer gets mods - at pinned
 * versions, and checked against their published SHA-1 before Iris loads them. We don't change
 * or re-share the packs; they keep their own licences.
 *
 * Iris is called by name (reflection) so Lmg still works if someone removes Iris.
 */
public final class Shaders {
	public enum Pack {
		GOLDEN("Golden haze", "Complementary Unbound", "ComplementaryUnbound_r5.9.3.zip",
				"https://cdn.modrinth.com/data/R6NEzAwj/versions/B1kyfoUZ/ComplementaryUnbound_r5.9.3.zip",
				"2ee08300e1d6f039e63eae8484dddf57b3aaaf67"),
		LIGHT("Lighter", "Complementary Reimagined", "ComplementaryReimagined_r5.9.3.zip",
				"https://cdn.modrinth.com/data/HVnmMxH1/versions/Bqen1mJX/ComplementaryReimagined_r5.9.3.zip",
				"838139b54cddb56b2e83cd260d8efd960ac536d6");

		public final String label, packName, file, url, sha1;

		Pack(String label, String packName, String file, String url, String sha1) {
			this.label = label;
			this.packName = packName;
			this.file = file;
			this.url = url;
			this.sha1 = sha1;
		}
	}

	private static volatile boolean busy;

	private Shaders() {}

	public static boolean irisInstalled() {
		return FabricLoader.getInstance().isModLoaded("iris");
	}

	public static boolean busy() {
		return busy;
	}

	/** The pack Iris is using right now (if it's one of ours and shaders are on), or null. */
	public static Pack current() {
		if (!irisInstalled()) return null;
		try {
			Object cfg = iris().getMethod("getIrisConfig").invoke(null);
			boolean enabled = (boolean) cfg.getClass().getMethod("areShadersEnabled").invoke(cfg);
			if (!enabled) return null;
			@SuppressWarnings("unchecked")
			java.util.Optional<String> name = (java.util.Optional<String>) cfg.getClass().getMethod("getShaderPackName").invoke(cfg);
			for (Pack p : Pack.values()) if (name.isPresent() && name.get().equals(p.file)) return p;
		} catch (Exception ignored) {
		}
		return null;
	}

	/** True if shaders are on with some pack (ours or one you added yourself). */
	public static boolean anyOn() {
		if (!irisInstalled()) return false;
		try {
			Object cfg = iris().getMethod("getIrisConfig").invoke(null);
			return (boolean) cfg.getClass().getMethod("areShadersEnabled").invoke(cfg);
		} catch (Exception e) {
			return false;
		}
	}

	/** Downloads the pack if needed, then switches Iris to it. {@code done} gets a message to show. */
	public static void use(Pack pack, Consumer<String> done) {
		if (!irisInstalled()) {
			done.accept("Shaders need Iris. Run the Lmg installer again to add it.");
			return;
		}
		if (busy) return;
		busy = true;
		Minecraft mc = Minecraft.getInstance();
		net.minecraft.util.Util.nonCriticalIoPool().execute(() -> {
			try {
				Path dir = (Path) iris().getMethod("getShaderpacksDirectory").invoke(null);
				Files.createDirectories(dir);
				Path target = dir.resolve(pack.file);
				if (!Files.exists(target) || !sha1(target).equals(pack.sha1)) {
					done.accept("Downloading " + pack.packName + "...");
					HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
					HttpRequest req = HttpRequest.newBuilder(URI.create(pack.url)).header("User-Agent", "LmgClient").timeout(Duration.ofSeconds(90)).GET().build();
					Path tmp = dir.resolve(pack.file + ".part");
					HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
					if (res.statusCode() != 200) throw new IllegalStateException("download failed (" + res.statusCode() + ")");
					try (InputStream in = res.body()) {
						Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
					}
					if (!sha1(tmp).equals(pack.sha1)) {
						Files.deleteIfExists(tmp);
						throw new IllegalStateException("the file didn't match its checksum, so it wasn't used");
					}
					Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				}
				mc.execute(() -> {
					try {
						apply(pack.file, true);
						done.accept(pack.packName + " is on. Expect lower FPS.");
					} catch (Exception e) {
						done.accept("Downloaded. Turn it on in Video settings > Shader Packs.");
						LmgClient.LOGGER.warn("Couldn't switch Iris pack", e);
					} finally {
						busy = false;
					}
				});
			} catch (Exception e) {
				busy = false;
				done.accept("Couldn't get the shaders: " + e.getMessage());
				LmgClient.LOGGER.warn("Shader download failed", e);
			}
		});
	}

	public static void off(Consumer<String> done) {
		if (!irisInstalled()) return;
		try {
			apply(null, false);
			done.accept("Shaders off.");
		} catch (Exception e) {
			done.accept("Couldn't turn shaders off: " + e.getMessage());
		}
	}

	/** Iris's own shader screen (pack list and each pack's settings). */
	public static Screen settingsScreen(Screen parent) {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object inst = api.getMethod("getInstance").invoke(null);
			return (Screen) api.getMethod("openMainIrisScreenObj", Object.class).invoke(inst, parent);
		} catch (Exception e) {
			return null;
		}
	}

	private static void apply(String file, boolean enabled) throws Exception {
		Object cfg = iris().getMethod("getIrisConfig").invoke(null);
		if (file != null) cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, file);
		cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, enabled);
		cfg.getClass().getMethod("save").invoke(cfg);
		iris().getMethod("reload").invoke(null);
	}

	private static Class<?> iris() throws ClassNotFoundException {
		return Class.forName("net.irisshaders.iris.Iris");
	}

	private static String sha1(Path file) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
		}
		return HexFormat.of().formatHex(md.digest());
	}
}
