package dev.lmg.client.hud;

import dev.lmg.client.LmgConfig;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where each draggable HUD element sits. Positions are stored as a fraction of the free space
 * (so 1.0 = touching the right/bottom edge) which keeps them on screen at any window size.
 */
public final class HudLayout {
	public enum Element {
		HUD_BAR("HUD bar"), TARGET("Target info"), POTIONS("Potion effects"), MINIMAP("Minimap");

		public final String label;

		Element(String label) {
			this.label = label;
		}
	}

	/** True while the HUD editor is open: elements show sample content so you can place them. */
	public static boolean editing;

	private static final Map<Element, int[]> BOUNDS = new EnumMap<>(Element.class);
	private static final Map<Element, Long> REPORTED_AT = new EnumMap<>(Element.class);

	private HudLayout() {}

	/** Top-left corner for an element of size w x h, using the saved spot or the default. */
	public static int[] place(Element e, int defaultX, int defaultY, int w, int h, int screenW, int screenH) {
		float fx = getX(e), fy = getY(e);
		int x = fx < 0 ? defaultX : Math.round(fx * Math.max(0, screenW - w));
		int y = fy < 0 ? defaultY : Math.round(fy * Math.max(0, screenH - h));
		x = Math.max(0, Math.min(x, screenW - w));
		y = Math.max(0, Math.min(y, screenH - h));
		return new int[]{x, y};
	}

	/** Elements call this every frame with where they actually drew (for the editor). */
	public static void report(Element e, int x, int y, int w, int h) {
		BOUNDS.put(e, new int[]{x, y, w, h});
		REPORTED_AT.put(e, System.currentTimeMillis());
	}

	/** Where the element was last drawn, or null if it hasn't been drawn recently (e.g. turned off). */
	public static int[] bounds(Element e) {
		Long at = REPORTED_AT.get(e);
		return at != null && System.currentTimeMillis() - at < 300 ? BOUNDS.get(e) : null;
	}

	/** Saves a new top-left position (in pixels) for an element. */
	public static void move(Element e, int x, int y, int w, int h, int screenW, int screenH) {
		float fx = screenW - w <= 0 ? 0 : x / (float) (screenW - w);
		float fy = screenH - h <= 0 ? 0 : y / (float) (screenH - h);
		set(e, Math.max(0, Math.min(1, fx)), Math.max(0, Math.min(1, fy)));
	}

	public static void reset(Element e) {
		set(e, -1, -1);
	}

	private static float getX(Element e) {
		LmgConfig c = LmgConfig.get();
		return switch (e) {
			case HUD_BAR -> c.hudBarX;
			case TARGET -> c.targetX;
			case POTIONS -> c.potionsX;
			case MINIMAP -> c.minimapX;
		};
	}

	private static float getY(Element e) {
		LmgConfig c = LmgConfig.get();
		return switch (e) {
			case HUD_BAR -> c.hudBarY;
			case TARGET -> c.targetY;
			case POTIONS -> c.potionsY;
			case MINIMAP -> c.minimapY;
		};
	}

	private static void set(Element e, float x, float y) {
		LmgConfig c = LmgConfig.get();
		switch (e) {
			case HUD_BAR -> { c.hudBarX = x; c.hudBarY = y; }
			case TARGET -> { c.targetX = x; c.targetY = y; }
			case POTIONS -> { c.potionsX = x; c.potionsY = y; }
			case MINIMAP -> { c.minimapX = x; c.minimapY = y; }
		}
	}
}
