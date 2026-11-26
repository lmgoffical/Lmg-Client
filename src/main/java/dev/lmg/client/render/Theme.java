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
