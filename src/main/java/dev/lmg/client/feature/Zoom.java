package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.util.Util;

/** Hold the zoom key to zoom in; scroll while holding it to zoom further. Smooth in and out. */
public final class Zoom {
	private static float current = 1f;   // 1 = no zoom
	private static float scrolled = 1f;  // extra zoom from scrolling while held (reset when you let go)
	private static long lastFrame = -1;

	private Zoom() {}

	public static boolean held() {
		return LmgKeys.ZOOM.isDown() && net.minecraft.client.Minecraft.getInstance().gui.screen() == null;
	}

	/** Called every frame from the camera: returns the factor to divide the field of view by. */
	public static float factor() {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		boolean on = held();
		LmgConfig c = LmgConfig.get();
		if (!on) scrolled = 1f;
		float base = c.zoomAmount > 0 ? c.zoomAmount : LmgConfig.ZOOM_LEVELS[c.zoomLevel];
		float target = on ? Math.max(1.5f, Math.min(50f, base * scrolled)) : 1f;
		if (c.smoothZoom) {
			// glide in "times closer" steps (log scale), so 2x and 20x feel equally smooth
			float lc = (float) Math.log(current), lt = (float) Math.log(target);
			current = (float) Math.exp(Draw.approach(lc, lt, rate(c.zoomSpeed), dt));
		} else {
			current = target;
		}
		if (Math.abs(current - target) < 0.002f) current = target;
		return current;
	}

	/** Zoom speed 1..10 -> how fast it glides (the old fixed speed was about 7). */
	static float rate(int speed) {
		return 1.4f * (float) Math.pow(Math.max(1, Math.min(10, speed)), 1.25);
	}

	public static String speedLabel(int speed) {
		String word = speed <= 2 ? "Very slow" : speed <= 4 ? "Slow" : speed <= 6 ? "Medium" : speed <= 8 ? "Fast" : "Very fast";
		return speed + " - " + word;
	}

	public static boolean active() {
		return current > 1.01f;
	}

	/** Mouse sensitivity multiplier while zoomed, so aiming doesn't feel twitchy. */
	public static double sensitivity() {
		return active() ? 1.0 / current : 1.0;
	}

	public static void scroll(double amount) {
		float base = LmgConfig.get().zoomAmount > 0 ? LmgConfig.get().zoomAmount : 4f;
		scrolled = Math.max(1.5f / base, Math.min(50f / base, scrolled * (amount > 0 ? 1.15f : 1 / 1.15f)));
	}
}
