package dev.lmg.client.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Moving capes. Each cape has a strip of frames in textures/cosmetic/anim/ (drawn by tools/make_cape_anims.py).
 * A texture that we keep updating is registered under the cape's normal name (textures/cosmetic/cape_x.png), so
 * the vanilla cape layer, the menu preview, pets and other players' shared capes all move without knowing it.
 */
public final class AnimatedCapes {
	private static final int TICKS_PER_FRAME = 2;   // 10 frames a second

	private static final class Anim {
		final NativeImage strip;
		final DynamicTexture texture;
		final int frames, w, h;
		int shown = -1;

		Anim(NativeImage strip, DynamicTexture texture, int w, int h) {
			this.strip = strip;
			this.texture = texture;
			this.w = w;
			this.h = h;
			this.frames = Math.max(1, strip.getHeight() / h);
		}
	}

	private static final Map<String, Anim> ANIMS = new LinkedHashMap<>();
	private static boolean loaded;
	private static long ticks;

	private AnimatedCapes() {}

	/** Every client tick: shows the next frame (or the first one, held still, if animation is off). */
	public static void tick(Minecraft mc) {
		if (!loaded) load(mc);
		ticks++;
		boolean on = LmgConfig.get().animatedCapes;
		for (Anim a : ANIMS.values()) {
			int frame = on ? (int) ((ticks / TICKS_PER_FRAME) % a.frames) : 0;
			if (frame == a.shown) continue;
			NativeImage px = a.texture.getPixels();
			if (px == null) continue;
			int y0 = frame * a.h;
			for (int y = 0; y < a.h; y++) {
				for (int x = 0; x < a.w; x++) px.setPixel(x, y, a.strip.getPixel(x, y0 + y));
			}
			a.texture.upload();
			a.shown = frame;
		}
	}

	private static void load(Minecraft mc) {
		loaded = true;
		for (String cat : new String[]{"capes"}) {
			for (CosmeticModel m : CosmeticRegistry.inCategory(cat)) {
				if (m.capeTexture == null || ANIMS.containsKey(m.capeTexture)) continue;
				Identifier stripId = LmgClient.id("textures/cosmetic/anim/" + m.capeTexture);
				Optional<Resource> res = mc.getResourceManager().getResource(stripId);
				if (res.isEmpty()) continue;
				try (InputStream in = res.get().open()) {
					NativeImage strip = NativeImage.read(in);
					int w = strip.getWidth(), h = w / 2;   // capes are 2:1 (64x32 layout)
					String name = m.capeTexture;
					DynamicTexture tex = new DynamicTexture(() -> "lmg animated " + name, w, h, true);
					mc.getTextureManager().register(LmgClient.id("textures/cosmetic/" + m.capeTexture), tex);
					ANIMS.put(m.capeTexture, new Anim(strip, tex, w, h));
				} catch (Exception e) {
					LmgClient.LOGGER.warn("Couldn't load the animated cape {}", stripId, e);
				}
			}
		}
		LmgClient.LOGGER.info("Animated {} capes", ANIMS.size());
	}

	public static int count() {
		return ANIMS.size();
	}
}
