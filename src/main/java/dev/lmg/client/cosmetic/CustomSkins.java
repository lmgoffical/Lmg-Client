package dev.lmg.client.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Your own skin files: drop 64x64 (or old 64x32) PNGs into .minecraft/config/lmgclient-skins
 * and pick one in the Cosmetics screen. Only YOU see it - other players see your real skin.
 */
public final class CustomSkins {
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("lmgclient-skins");

	private record Loaded(Identifier texture, long modified, boolean slimGuess) {}

	private static final Map<String, Loaded> LOADED = new HashMap<>();
	private static final Map<String, Long> FAILED = new HashMap<>();
	private static int counter;

	private CustomSkins() {}

	/** The skin to draw for you, or null to keep your normal one. */
	public static PlayerSkin current() {
		String name = LmgConfig.get().customSkin;
		if (name == null || name.isBlank()) return null;
		Identifier tex = texture(name);
		if (tex == null) return null;
		ClientAsset.Texture body = new ClientAsset.ResourceTexture(tex, tex);
		PlayerModelType model = LmgConfig.get().customSkinSlim ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		return PlayerSkin.insecure(body, null, null, model);
	}

	/** PNG file names in the skins folder, sorted. */
	public static List<String> list() {
		List<String> out = new ArrayList<>();
		try {
			Files.createDirectories(DIR);
			try (Stream<Path> s = Files.list(DIR)) {
				s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
						.map(p -> p.getFileName().toString()).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
			}
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not list skins", e);
		}
		return out;
	}

	/** The registered texture for a skin file (loads it the first time, and again when the file changes). */
	public static Identifier texture(String name) {
		Path file = DIR.resolve(name);
		long modified;
		try {
			if (!Files.isRegularFile(file)) return null;
			modified = Files.getLastModifiedTime(file).toMillis();
		} catch (Exception e) {
			return null;
		}
		Loaded l = LOADED.get(name);
		if (l != null && l.modified == modified) return l.texture;
		if (FAILED.getOrDefault(name, -1L) == modified) return null;
		try (InputStream in = Files.newInputStream(file)) {
			NativeImage img = fixLegacy(NativeImage.read(in));
			if (img == null) throw new IllegalArgumentException("skins must be 64x64 or 64x32");
			boolean slim = (img.getPixel(54, 20) >>> 24) == 0;
			Identifier id = LmgClient.id("custom_skin/" + (counter++));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Lmg skin " + name, img));
			if (l != null) Minecraft.getInstance().getTextureManager().release(l.texture);
			LOADED.put(name, new Loaded(id, modified, slim));
			return id;
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not load skin {}: {}", name, e.getMessage());
			FAILED.put(name, modified);
			return null;
		}
	}

	/** True if the skin looks like it was made for slim (3-pixel) arms. */
	public static boolean looksSlim(String name) {
		texture(name);
		Loaded l = LOADED.get(name);
		return l != null && l.slimGuess;
	}

	public static void forgetAll() {
		for (Loaded l : LOADED.values()) Minecraft.getInstance().getTextureManager().release(l.texture);
		LOADED.clear();
		FAILED.clear();
	}

	/** Old 64x32 skins get the 64x64 layout (legs and arms mirrored onto the left side, like vanilla). */
	private static NativeImage fixLegacy(NativeImage img) {
		int w = img.getWidth(), h = img.getHeight();
		if (w == 64 && h == 64) return img;
		if (w != 64 || h != 32) {
			img.close();
			return null;
		}
		NativeImage out = new NativeImage(64, 64, true);
		for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) out.setPixel(x, y, img.getPixel(x, y));
		img.close();
		out.copyRect(4, 16, 16, 32, 4, 4, true, false);
		out.copyRect(8, 16, 16, 32, 4, 4, true, false);
		out.copyRect(0, 20, 24, 32, 4, 12, true, false);
		out.copyRect(4, 20, 16, 32, 4, 12, true, false);
		out.copyRect(8, 20, 8, 32, 4, 12, true, false);
		out.copyRect(12, 20, 16, 32, 4, 12, true, false);
		out.copyRect(44, 16, -8, 32, 4, 4, true, false);
		out.copyRect(48, 16, -8, 32, 4, 4, true, false);
		out.copyRect(40, 20, 0, 32, 4, 12, true, false);
		out.copyRect(44, 20, -8, 32, 4, 12, true, false);
		out.copyRect(48, 20, -16, 32, 4, 12, true, false);
		out.copyRect(52, 20, -8, 32, 4, 12, true, false);
		return out;
	}
}
