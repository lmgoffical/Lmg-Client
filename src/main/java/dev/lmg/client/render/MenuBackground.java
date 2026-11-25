package dev.lmg.client.render;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * The dark Lmg Client menu background: soft gradient, darkened edges, and
 * (optionally) drifting light curtains with star sparkles on the right side.
 */
public final class MenuBackground {
	private static final int TOP = 0xFF17171D;
	private static final int BOTTOM = 0xFF09090C;

	// Shared, so the sparkles keep drifting smoothly when you switch between menus.
	private static final Sparkles AMBIENT = new Sparkles();

	private MenuBackground() {}

	public static void draw(GuiGraphicsExtractor g, int width, int height, boolean effects) {
		g.fillGradient(0, 0, width, height, TOP, BOTTOM);
		vignette(g, width, height);
		if (effects && LmgConfig.get().menuEffects) {
			curtains(g, width, height);
			AMBIENT.keepAmbient(26, width * 0.70f, width, 0, height);
			AMBIENT.update();
			AMBIENT.draw(g, 1f);
		}
	}

	/** Darkens the left and right edges with stacked translucent strips. */
	private static void vignette(GuiGraphicsExtractor g, int width, int height) {
		int strips = 12;
		int band = Math.max(1, width / 7 / strips);
		for (int i = 0; i < strips; i++) {
			int color = Draw.withAlpha(0x000000, 0.045f * (strips - i) / strips);
			g.fill(i * band, 0, (i + 1) * band, height, color);
			g.fill(width - (i + 1) * band, 0, width - i * band, height, color);
		}
	}

	/** Three wavy, translucent "aurora curtain" bands that slowly sway. */
	private static void curtains(GuiGraphicsExtractor g, int width, int height) {
		float t = Util.getMillis() / 1000f;
		int step = 2;
		for (int b = 0; b < 3; b++) {
			float phase = b * 1.9f;
			float baseX = width * (0.80f + b * 0.065f);
			float amp = 5f + b * 3f;
			float bandW = 9f + b * 5f;
			for (int y = 0; y < height; y += step) {
				float k = y / (float) height;
				float x = baseX
						+ (float) Math.sin(y * 0.022f + t * 0.55f + phase) * amp
						+ (float) Math.sin(y * 0.009f - t * 0.30f + phase * 2f) * amp * 0.8f;
				// fade out toward the top and bottom of the screen
				float edge = (float) Math.sin(Math.PI * k);
				float a = 0.035f * edge;
				int x1 = Math.round(x);
				g.fill(x1, y, Math.round(x + bandW), y + step, Draw.white(a));
				g.fill(Math.round(x + bandW * 0.30f), y, Math.round(x + bandW * 0.70f), y + step, Draw.white(a * 1.4f));
			}
		}
	}
}
