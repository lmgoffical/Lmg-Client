package dev.lmg.legacy.feature;

import dev.lmg.legacy.Config;
import net.minecraft.client.Minecraft;

/**
 * Hold the zoom key (C) to zoom in, like OptiFine's zoom but with your own amount and speed.
 * Scroll while holding it to zoom further; mouse sensitivity drops while zoomed so aiming stays steady.
 */
public final class Zoom {
	private static float current = 1f, scrolled = 1f;
	private static long lastFrame = -1;
	private static float savedSensitivity = Float.NaN;
	private static boolean savedSmooth;

	private Zoom() {}

	public static boolean held() {
		Minecraft mc = Minecraft.getMinecraft();
		return mc.currentScreen == null && mc.thePlayer != null && Keys.ZOOM.isKeyDown();
	}

	/** Zoom speed 1..10 -> glide rate (per second). */
	static float rate(int speed) {
		return 1.4f * (float) Math.pow(Math.max(1, Math.min(10, speed)), 1.25);
	}

	public static String speedLabel(int speed) {
		String word = speed <= 2 ? "Very slow" : speed <= 4 ? "Slow" : speed <= 6 ? "Medium" : speed <= 8 ? "Fast" : "Very fast";
		return speed + " - " + word;
	}

	/** Called every frame from the FOV event: the number to divide the field of view by. */
	public static float factor() {
		long now = System.nanoTime();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		Config c = Config.get();
		boolean on = held();
		if (!on) scrolled = 1f;
		float target = on ? Math.max(1.5f, Math.min(50f, c.zoomAmount * scrolled)) : 1f;
		if (c.smoothZoom) {
			float lc = (float) Math.log(current), lt = (float) Math.log(target);
			float k = Math.min(1f, dt * rate(c.zoomSpeed));
			current = (float) Math.exp(lc + (lt - lc) * k);
		} else {
			current = target;
		}
		if (Math.abs(current - target) < 0.002f) current = target;
		applyMouse(on);
		return current;
	}

	public static boolean active() {
		return current > 1.01f;
	}

	public static void scroll(int wheel) {
		float base = Config.get().zoomAmount;
		scrolled = Math.max(1.5f / base, Math.min(50f / base, scrolled * (wheel > 0 ? 1.15f : 1 / 1.15f)));
	}

	/** Lower sensitivity (and optional cinematic camera) while zoomed; put them back when you let go. */
	private static void applyMouse(boolean on) {
		Minecraft mc = Minecraft.getMinecraft();
		Config c = Config.get();
		if (on && Float.isNaN(savedSensitivity)) {
			savedSensitivity = mc.gameSettings.mouseSensitivity;
			savedSmooth = mc.gameSettings.smoothCamera;
		}
		if (!Float.isNaN(savedSensitivity)) {
			if (on || active()) {
				if (c.zoomLowerSensitivity) {
					// Minecraft turns the slider into (s*0.6+0.2)^3; divide that by the zoom
					float base = savedSensitivity * 0.6f + 0.2f;
					float s = (base / (float) Math.cbrt(Math.max(1f, current)) - 0.2f) / 0.6f;
					mc.gameSettings.mouseSensitivity = Math.max(-0.3f, s);
				}
				if (c.zoomCinematic) mc.gameSettings.smoothCamera = true;
			} else {
				restore();
			}
		}
	}

	/** Puts the mouse settings back (also when a menu opens). */
	public static void restore() {
		if (Float.isNaN(savedSensitivity)) return;
		Minecraft mc = Minecraft.getMinecraft();
		mc.gameSettings.mouseSensitivity = savedSensitivity;
		mc.gameSettings.smoothCamera = savedSmooth;
		savedSensitivity = Float.NaN;
	}
}
