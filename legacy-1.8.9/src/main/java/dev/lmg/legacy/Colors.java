package dev.lmg.legacy;

/** The colour choices every colour setting cycles through. The last one is a slow rainbow. */
public final class Colors {
	public static final String[] NAMES = {"White", "Lmg blue", "Sky", "Mint", "Lime", "Gold", "Orange", "Red", "Pink", "Purple", "Rainbow"};
	private static final int[] RGB = {0xFFFFFF, 0x4F8CFF, 0x6AD1FF, 0x6BF2C2, 0x9BEA5A, 0xFFD36A, 0xFF9A4A, 0xFF5A5A, 0xFF8CCB, 0xB48CFF};

	private Colors() {}

	public static int clamp(int i) {
		return Math.max(0, Math.min(NAMES.length - 1, i));
	}

	/** RGB (no alpha) for a colour choice; the rainbow moves with time. */
	public static int rgb(int i) {
		if (i >= RGB.length) {
			float h = (System.currentTimeMillis() % 6000L) / 6000f;
			return java.awt.Color.HSBtoRGB(h, 0.55f, 1f) & 0xFFFFFF;
		}
		return RGB[clamp(i)];
	}

	public static int argb(int i, float alpha) {
		return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb(i);
	}
}
