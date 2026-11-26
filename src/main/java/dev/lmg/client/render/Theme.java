package dev.lmg.client.render;

import dev.lmg.client.LmgConfig;
import net.minecraft.util.Util;

/** Reads the current look (accent color, backdrop, animation speed) from the config. */
public final class Theme {
	private Theme() {}

	/** Accent color as RGB (no alpha). */
	public static int accent() {
		return LmgConfig.get().accentRgb();
	}

	/** Accent color with an alpha of 0..1. */
	public static int accent(float alpha) {
		return Draw.withAlpha(accent(), alpha);
	}

	/** A softer accent, halfway to white - good for text and small details. */
	public static int accentSoft() {
		return Draw.lerpRgb(0xFFFFFF, accent(), 0.6f);
	}

	/**
	 * The colour for filled buttons and switches. A white or grey accent can't carry white text
	 * or a white knob, so those use the website's blue instead.
	 */
	public static int primary() {
		int rgb = accent() & 0xFFFFFF;
		int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		boolean grey = max - min < 40;
		return grey ? 0x3D9BFF : rgb;
	}

	/** White or dark text, whichever reads better on {@code rgb}. */
	public static int textOn(int rgb) {
		int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
		return (0.299f * r + 0.587f * g + 0.114f * b) / 255f > 0.62f ? 0x0C1322 : 0xFFFFFF;
	}

	public static LmgConfig.Backdrop backdrop() {
		return LmgConfig.get().backdrop;
	}

	public static float speed() {
		return LmgConfig.ANIMATION_SPEEDS[LmgConfig.get().animationSpeed];
	}

	/** Progress 0..1 of an animation that started at {@code startMs} and lasts {@code durationMs} at normal speed. */
	public static float progress(long startMs, float durationMs) {
		return Draw.clamp01((Util.getMillis() - startMs) * speed() / durationMs);
	}
}
