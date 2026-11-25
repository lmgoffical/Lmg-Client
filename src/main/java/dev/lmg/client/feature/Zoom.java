package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.util.Util;

/** Hold the zoom key to zoom in; scroll while holding it to zoom further. Smooth in and out. */
public final class Zoom {
	private static float current = 1f;   // 1 = no zoom
	private static float level = 4f;     // how far the key zooms, changed by scrolling
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
		if (!on) level = LmgConfig.ZOOM_LEVELS[LmgConfig.get().zoomLevel];
		float target = on ? level : 1f;
		current = LmgConfig.get().smoothZoom ? Draw.approach(current, target, 14f, dt) : target;
		if (Math.abs(current - target) < 0.002f) current = target;
		return current;
	}

	public static boolean active() {
		return current > 1.01f;
	}

	/** Mouse sensitivity multiplier while zoomed, so aiming doesn't feel twitchy. */
	public static double sensitivity() {
		return active() ? 1.0 / current : 1.0;
	}

	public static void scroll(double amount) {
		level = Math.max(1.5f, Math.min(50f, level * (amount > 0 ? 1.2f : 1 / 1.2f)));
	}
}
