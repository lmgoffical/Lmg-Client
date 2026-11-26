package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;

/** Ready-made held item looks. "Custom" is whatever you've dragged the sliders to. */
public final class ItemLook {
	private ItemLook() {}

	public enum Preset {
		VANILLA("Vanilla", 0f, 0f, 0f, 0f, 0f, 0f, 1f),
		LOW("Low", 0f, -0.08f, 0f, 0f, 0f, 0f, 1f),
		SMALL("Small", 0.02f, -0.04f, 0f, 0f, 0f, 0f, 0.7f),
		TINY("Tiny", 0.04f, -0.06f, 0f, 0f, 0f, 0f, 0.5f),
		BIG_LOW("Big & low", -0.5f, -0.27f, -0.35f, 50f, 0f, -15f, 1.5f),
		STANDING("Standing", -0.5f, -0.3f, -0.35f, 50f, -25f, -15f, 1.5f),
		FLAT("Flat on", -0.12f, -0.1f, 0.04f, 70f, 0f, 0f, 1.2f),
		CENTRE("Centred", -0.56f, -0.12f, 0f, 0f, 0f, 0f, 0.9f);

		public final String label;
		final float x, y, z, turn, tilt, roll, size;

		Preset(String label, float x, float y, float z, float turn, float tilt, float roll, float size) {
			this.label = label;
			this.x = x;
			this.y = y;
			this.z = z;
			this.turn = turn;
			this.tilt = tilt;
			this.roll = roll;
			this.size = size;
		}

		public void apply(LmgConfig c) {
			c.itemX = x;
			c.itemY = y;
			c.itemZ = z;
			c.itemTurn = turn;
			c.itemTilt = tilt;
			c.itemRoll = roll;
			c.itemSize = size;
		}

		boolean matches(LmgConfig c) {
			return near(c.itemX, x, 0.005f) && near(c.itemY, y, 0.005f) && near(c.itemZ, z, 0.005f)
					&& near(c.itemTurn, turn, 0.6f) && near(c.itemTilt, tilt, 0.6f) && near(c.itemRoll, roll, 0.6f)
					&& near(c.itemSize, size, 0.01f);
		}
	}

	private static boolean near(float a, float b, float eps) {
		return Math.abs(a - b) <= eps;
	}

	/** The preset you're on, or null if you've made your own. */
	public static Preset current(LmgConfig c) {
		for (Preset p : Preset.values()) if (p.matches(c)) return p;
		return null;
	}

	public static String label(LmgConfig c) {
		Preset p = current(c);
		return p == null ? "Custom" : p.label;
	}

	/** Next / previous preset (from Custom, starts at the first). */
	public static void cycle(LmgConfig c, int dir) {
		Preset p = current(c);
		Preset[] all = Preset.values();
		int i = p == null ? (dir > 0 ? 0 : all.length - 1) : Math.floorMod(p.ordinal() + dir, all.length);
		all[i].apply(c);
	}
}
