package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Cinematic mode, for filming: hides the HUD, turns on Minecraft's smooth camera, and adds
 * black film bars and a soft vignette. Press the key again to go back to normal.
 */
public final class Cinematic {
	private static boolean on;
	private static boolean hudWasHidden, smoothWas;
	private static float bars;           // 0..1, slides in and out
	private static long lastFrame = -1;

	private Cinematic() {}

	public static boolean on() {
		return on;
	}

	public static void toggle() {
		Minecraft mc = Minecraft.getInstance();
		LmgConfig c = LmgConfig.get();
		on = !on;
		if (on) {
			hudWasHidden = mc.gui.hud.isHidden();
			if (!hudWasHidden) mc.gui.hud.toggle();
			smoothWas = mc.options.smoothCamera;
			if (c.cinematicSmoothCam) mc.options.smoothCamera = true;
		} else {
			if (mc.gui.hud.isHidden() != hudWasHidden) mc.gui.hud.toggle();
			mc.options.smoothCamera = smoothWas;
			LmgKeys.say("Cinematic mode off");
		}
	}

	/** Drawn on top of the world every frame (even with the HUD hidden). */
	public static void draw(GuiGraphicsExtractor g) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		LmgConfig c = LmgConfig.get();
		bars = Draw.approach(bars, on && c.cinematicBars ? 1f : 0f, 4f, dt);
		float vignette = on && c.cinematicVignette ? 1f : 0f;
		int w = g.guiWidth(), h = g.guiHeight();

		if (vignette > 0) {
			int band = Math.max(20, h / 4);
			g.fillGradient(0, 0, w, band, 0x66000000, 0x00000000);
			g.fillGradient(0, h - band, w, h, 0x00000000, 0x66000000);
			int side = Math.max(20, w / 6);
			for (int i = 0; i < side; i += 2) {
				float t = 1f - i / (float) side;
				int col = Draw.withAlpha(0x000000, 0.4f * t * t);
				g.fill(i, 0, i + 2, h, col);
				g.fill(w - i - 2, 0, w - i, h, col);
			}
		}
		if (bars > 0.001f) {
			int bh = Math.round(h * LmgConfig.BAR_SIZES[c.barSize] * Draw.easeInOutCubic(bars));
			g.fill(0, 0, w, bh, 0xFF000000);
			g.fill(0, h - bh, w, h, 0xFF000000);
		}
	}
}
