package dev.lmg.legacy.render;

import dev.lmg.legacy.Config;
import dev.lmg.legacy.LmgLegacy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Makes your cape move: frames come from textures/capes/anim/<cape>.png (stacked top to bottom). Only the cape you
 * wear is loaded. A texture we keep updating replaces textures/capes/<cape>.png, so CapeLayer doesn't change.
 */
public final class AnimatedCape {
	private static final int TICKS_PER_FRAME = 2;   // 10 frames a second
	private static String loadedFor;
	private static int[] strip;
	private static int w, h, frames, shown = -1;
	private static DynamicTexture texture;
	private static long ticks;

	private AnimatedCape() {}

	public static void tick() {
		Config c = Config.get();
		ticks++;
		if (c.cape.isEmpty()) return;
		if (!c.cape.equals(loadedFor)) load(c.cape);
		if (texture == null) return;
		int frame = c.animatedCape ? (int) ((ticks / TICKS_PER_FRAME) % frames) : 0;
		if (frame == shown) return;
		System.arraycopy(strip, frame * w * h, texture.getTextureData(), 0, w * h);
		texture.updateDynamicTexture();
		shown = frame;
	}

	private static void load(String cape) {
		Minecraft mc = Minecraft.getMinecraft();
		ResourceLocation still = new ResourceLocation("lmgclient", "textures/capes/" + cape + ".png");
		// put the still picture back for the cape we're leaving
		if (loadedFor != null) mc.getTextureManager().deleteTexture(new ResourceLocation("lmgclient", "textures/capes/" + loadedFor + ".png"));
		loadedFor = cape;
		texture = null;
		strip = null;
		shown = -1;
		InputStream in = null;
		try {
			in = mc.getResourceManager().getResource(new ResourceLocation("lmgclient", "textures/capes/anim/" + cape + ".png")).getInputStream();
			BufferedImage img = TextureUtil.readBufferedImage(in);
			w = img.getWidth();
			h = w / 2;
			frames = Math.max(1, img.getHeight() / h);
			strip = img.getRGB(0, 0, w, frames * h, null, 0, w);
			texture = new DynamicTexture(w, h);
			mc.getTextureManager().loadTexture(still, texture);
		} catch (Exception e) {
			LmgLegacy.LOGGER.warn("No animation for cape {}", cape);
		} finally {
			try { if (in != null) in.close(); } catch (Exception ignored) {}
		}
	}
}
