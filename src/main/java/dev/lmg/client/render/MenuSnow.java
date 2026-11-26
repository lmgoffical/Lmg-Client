package dev.lmg.client.render;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weather for the Lmg menus: snow in winter, falling leaves in autumn, cherry petals in spring,
 * fireflies in summer (or pick one). Some bits fall behind the panel, some in front, and the bigger
 * ones settle on the panel's top edge. Shared by every Lmg screen so it doesn't restart when you
 * go from page to page; it only starts over when the menu is opened again.
 */
public final class MenuSnow {
	public enum Weather {
		AUTO("Seasonal"), SNOW("Snow"), BLIZZARD("Blizzard"), LEAVES("Autumn leaves"),
		PETALS("Cherry petals"), FIREFLIES("Fireflies"), OFF("Off");

		public final String label;

		Weather(String label) {
			this.label = label;
		}

		/** What "Seasonal" means today (northern hemisphere; snow from late November). */
		public Weather resolved() {
			if (this != AUTO) return this;
			LocalDate d = LocalDate.now();
			int m = d.getMonthValue(), day = d.getDayOfMonth();
			if (m == 12 || m <= 2 || (m == 11 && day >= 20) || (m == 3 && day <= 5)) return SNOW;
			if (m <= 5) return PETALS;
			if (m <= 8) return FIREFLIES;
			return LEAVES;
		}
	}

	private static final int[] LEAF_COLORS = {0xD9622B, 0xE8A33D, 0xB8432F, 0x9C5B2E, 0xF2C14E, 0xC7512A};
	private static final int[] PETAL_COLORS = {0xF7B6D2, 0xF29BC1, 0xFFD1E3, 0xF5A3C7};

	private static final class Flake {
		float x, y, speed, sway, swayRate, phase, size, alpha, rot, spin;
		int color;
		boolean front;
	}

	private static final List<Flake> flakes = new ArrayList<>();
	private static final Random random = new Random();
	private static long lastFrame = -1;
	private static float time;
	private static int screenW, screenH;
	private static Weather kind = Weather.SNOW;

	// the little pile on top of the panel
	private static float[] pile = new float[0];
	private static int[] pileColor = new int[0];
	private static int pileX0 = Integer.MIN_VALUE, pileY, pileX1;

	private MenuSnow() {}

	public static Weather current() {
		return LmgConfig.get().weather().resolved();
	}

	/** Greys out and darkens the (already blurred) world behind the menu. */
	public static void greyBackdrop(GuiGraphicsExtractor g, int w, int h) {
		// the world is black and white behind the menus (a shader); if that can't run, a grey wash does it
		g.fill(0, 0, w, h, WorldFilter.greyWorking() ? 0x5A1A1D22 : 0xB8363A41);
		g.fillGradient(0, 0, w, h / 3, 0x55000000, 0x00000000);
		g.fillGradient(0, h - h / 3, w, h, 0x00000000, 0x66000000);
	}

	private static int target(int w, int h) {
		int base = switch (kind) {
			case SNOW -> 110;
			case BLIZZARD -> 220;
			case LEAVES -> 42;
			case PETALS -> 70;
			case FIREFLIES -> 36;
			default -> 0;
		};
		// counts are for a 480x270 screen; bigger GUI areas get more
		return Math.round(base * (w * h) / (480f * 270f));
	}

	/** Moves everything along. Call once per frame, before drawing. */
	public static void update(int w, int h) {
		long now = Util.getMillis();
		Weather want = current();
		boolean restart = lastFrame < 0 || now - lastFrame > 1500 || w != screenW || h != screenH || want != kind;
		float dt = restart ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		screenW = w;
		screenH = h;
		kind = want;
		int count = target(w, h);

		if (restart) {
			// menu just opened: everything starts above the screen and pours in (fireflies fade in where they are)
			flakes.clear();
			pile = new float[0];
			pileColor = new int[0];
			pileX0 = Integer.MIN_VALUE;
			for (int i = 0; i < count; i++) {
				flakes.add(spawn(w, kind == Weather.FIREFLIES ? random.nextFloat() * h : -4 - random.nextFloat() * h * 1.1f));
			}
			return;
		}
		while (flakes.size() < count) flakes.add(spawn(w, kind == Weather.FIREFLIES ? random.nextFloat() * h : -4 - random.nextFloat() * 20));
		while (flakes.size() > count) flakes.remove(flakes.size() - 1);

		time += dt;
		if (kind == Weather.FIREFLIES) {
			for (Flake f : flakes) {
				// a slow wander that turns gently
				f.rot += (float) Math.sin(time * f.swayRate + f.phase) * dt * 1.4f;
				f.x += (float) Math.cos(f.rot) * f.speed * dt;
				f.y += (float) Math.sin(f.rot) * f.speed * dt * 0.7f;
				if (f.x < -6) f.x = w + 5;
				if (f.x > w + 6) f.x = -5;
				if (f.y < -6) f.y = h + 5;
				if (f.y > h + 6) f.y = -5;
			}
			return;
		}
		float wind = (float) Math.sin(time * 0.25f) * 5f + (kind == Weather.BLIZZARD ? 14f : kind == Weather.LEAVES ? 6f : 2f);
		for (Flake f : flakes) {
			float prevY = f.y;
			f.y += f.speed * dt;
			f.x += (wind * Math.min(f.size, 2f) * 0.6f + (float) Math.cos(time * f.swayRate + f.phase) * f.sway) * dt;
			f.rot += f.spin * dt;
			if (f.size > 1.2f && landOnPile(f, prevY)) {
				reset(f, w);
				continue;
			}
			if (f.y > h + 4) reset(f, w);
			if (f.x > w + 4) f.x -= w + 8;
			else if (f.x < -4) f.x += w + 8;
		}
		settlePile(dt);
	}

	private static Flake spawn(int w, float y) {
		Flake f = new Flake();
		reset(f, w);
		f.y = y;
		return f;
	}

	private static void reset(Flake f, int w) {
		f.x = random.nextFloat() * (w + 40) - 20;
		f.y = -4 - random.nextFloat() * 12;
		f.front = random.nextFloat() < (kind == Weather.FIREFLIES ? 0.25f : 0.3f);
		// three depths: far ones are small, faint and slow
		float depth = f.front ? 0.7f + random.nextFloat() * 0.3f : random.nextFloat() * 0.75f;
		f.phase = random.nextFloat() * 6.28f;
		f.rot = random.nextFloat() * 6.28f;
		switch (kind) {
			case LEAVES -> {
				f.size = 2.2f + depth * 1.6f;
				f.speed = 9f + depth * 12f + random.nextFloat() * 4f;
				f.sway = 10f + random.nextFloat() * 10f;
				f.swayRate = 0.8f + random.nextFloat() * 0.8f;
				f.spin = (random.nextBoolean() ? 1 : -1) * (1.2f + random.nextFloat() * 2.2f);
				f.color = LEAF_COLORS[random.nextInt(LEAF_COLORS.length)];
				f.alpha = f.front ? 0.95f : 0.45f + depth * 0.5f;
			}
			case PETALS -> {
				f.size = 1.3f + depth * 1.1f;
				f.speed = 7f + depth * 10f + random.nextFloat() * 4f;
				f.sway = 6f + random.nextFloat() * 8f;
				f.swayRate = 1f + random.nextFloat() * 1.2f;
				f.spin = (random.nextBoolean() ? 1 : -1) * (1.5f + random.nextFloat() * 3f);
				f.color = PETAL_COLORS[random.nextInt(PETAL_COLORS.length)];
				f.alpha = f.front ? 0.9f : 0.4f + depth * 0.55f;
			}
			case FIREFLIES -> {
				f.size = 1f + depth * 0.6f;
				f.speed = 4f + random.nextFloat() * 6f;
				f.swayRate = 0.5f + random.nextFloat() * 1.2f;
				f.spin = 1.5f + random.nextFloat() * 2.5f;     // how fast it blinks
				f.color = random.nextFloat() < 0.7f ? 0xE9F77A : 0xFFE38A;
				f.alpha = f.front ? 1f : 0.5f + depth * 0.4f;
				f.x = random.nextFloat() * w;
			}
			default -> {
				f.size = 0.75f + depth * 1.5f;
				f.speed = 9f + depth * 26f + random.nextFloat() * 6f;
				f.sway = 2f + random.nextFloat() * 5f;
				f.swayRate = 0.6f + random.nextFloat() * 1.4f;
				f.spin = 0;
				f.color = 0xF4F8FF;
				f.alpha = f.front ? 0.55f + random.nextFloat() * 0.3f : 0.25f + depth * 0.6f;
			}
		}
	}

	// ---- the pile on the panel -----------------------------------------------------------------

	/** Tells the weather where the panel's top edge is. If the panel moved, the pile falls off. */
	public static void panelTop(int x0, int y0, int x1) {
		int left = x0 + 5, right = x1 - 5;
		if (left == pileX0 && y0 == pileY && right == pileX1) return;
		// shake the old pile off
		if (kind != Weather.FIREFLIES) {
			for (int i = 0; i < pile.length; i += 3) {
				if (pile[i] < 0.6f) continue;
				Flake f = spawn(screenW, pileY - pile[i]);
				f.x = pileX0 + i;
				f.front = true;
				f.speed = 30f + random.nextFloat() * 20f;
				if (pileColor[i] != 0) f.color = pileColor[i];
				flakes.add(f);
			}
		}
		pileX0 = left;
		pileX1 = right;
		pileY = y0;
		pile = new float[Math.max(0, right - left)];
		pileColor = new int[pile.length];
	}

	private static final float[] SPREAD = {1f, 0.75f, 0.45f, 0.2f};

	private static boolean landOnPile(Flake f, float prevY) {
		if (pile.length == 0 || kind == Weather.FIREFLIES) return false;
		int col = (int) (f.x - pileX0);
		if (col < 0 || col >= pile.length) return false;
		float top = pileY - pile[col];
		if (prevY > top || f.y < top) return false;
		if (random.nextFloat() < (kind == Weather.LEAVES ? 0.5f : 0.25f)) return false; // some slip past the edge
		float add = (kind == Weather.LEAVES ? 0.45f : 0.7f) * f.size;
		float max = kind == Weather.SNOW || kind == Weather.BLIZZARD ? 5f : 3.5f;
		for (int d = -3; d <= 3; d++) {
			int c = col + d;
			if (c < 0 || c >= pile.length) continue;
			pile[c] = Math.min(max, pile[c] + add * SPREAD[Math.abs(d)]);
			if (Math.abs(d) <= 1 || pileColor[c] == 0) pileColor[c] = f.color;
		}
		return true;
	}

	/** Spreads the pile out into soft lumps, thinner near the rounded corners. */
	private static void settlePile(float dt) {
		if (pile.length < 3) return;
		float k = Math.min(0.3f, dt * (kind == Weather.LEAVES ? 0.6f : 1.5f));
		float prev = pile[0];
		for (int i = 1; i < pile.length - 1; i++) {
			float here = pile[i];
			pile[i] = here + k * (prev + pile[i + 1] - 2 * here) * 0.5f;
			prev = here;
		}
		int fade = Math.min(6, pile.length / 2);
		for (int i = 0; i < fade; i++) {
			float cap = 0.6f * i;
			pile[i] = Math.min(pile[i], cap);
			pile[pile.length - 1 - i] = Math.min(pile[pile.length - 1 - i], cap);
		}
	}

	// ---- drawing ---------------------------------------------------------------------------------

	/** Bits behind the panel. */
	public static void drawBack(GuiGraphicsExtractor g, float intro) {
		draw(g, false, intro);
	}

	/** Bits in front of the panel, plus the pile on its top edge. */
	public static void drawFront(GuiGraphicsExtractor g, float intro) {
		if (kind == Weather.OFF) return;
		Matrix3x2fStack pose = g.pose();
		boolean snow = kind == Weather.SNOW || kind == Weather.BLIZZARD;
		for (int i = 0; i < pile.length; i++) {
			float hgt = pile[i];
			if (hgt < 0.15f) continue;
			int c = snow || pileColor[i] == 0 ? 0xFFFFFF : pileColor[i];
			int body = Draw.withAlpha(snow ? 0xDCE5F0 : Draw.lerpRgb(c, 0x000000, 0.25f), intro), top = Draw.withAlpha(c, intro);
			// fractional heights, so the pile is smooth at any GUI scale
			pose.pushMatrix();
			pose.translate(pileX0 + i, pileY - hgt);
			pose.scale(1f, hgt);
			g.fill(0, 0, 1, 1, body);
			pose.popMatrix();
			pose.pushMatrix();
			pose.translate(pileX0 + i, pileY - hgt);
			pose.scale(1f, Math.min(hgt, 0.6f));
			g.fill(0, 0, 1, 1, top);
			pose.popMatrix();
		}
		draw(g, true, intro);
	}

	private static void draw(GuiGraphicsExtractor g, boolean front, float intro) {
		if (flakes.isEmpty()) return;
		Matrix3x2fStack pose = g.pose();
		for (Flake f : flakes) {
			if (f.front != front || f.y < -3) continue;
			switch (kind) {
				case FIREFLIES -> {
					float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * f.spin + f.phase));
					float a = f.alpha * blink * intro;
					glow(g, pose, f.x, f.y, f.size * 6f, Draw.withAlpha(f.color, 0.06f * a));
					glow(g, pose, f.x, f.y, f.size * 3.2f, Draw.withAlpha(f.color, 0.18f * a));
					glow(g, pose, f.x, f.y, f.size * 1.3f, Draw.withAlpha(Draw.lerpRgb(f.color, 0xFFFFFF, 0.5f), a));
				}
				case LEAVES, PETALS -> {
					// a little card that tumbles: squashing one side fakes it turning over
					float flip = (float) Math.cos(time * f.swayRate * 2.2f + f.phase);
					pose.pushMatrix();
					pose.translate(f.x, f.y);
					pose.rotate(f.rot);
					float w = f.size * (0.35f + 0.65f * Math.abs(flip)), h = f.size * (kind == Weather.PETALS ? 0.62f : 0.72f);
					pose.scale(w, h);
					pose.translate(-0.5f, -0.5f);
					int c = flip < 0 ? Draw.lerpRgb(f.color, 0x000000, 0.18f) : f.color;
					g.fill(0, 0, 1, 1, Draw.withAlpha(c, f.alpha * intro));
					pose.popMatrix();
					if (kind == Weather.LEAVES) {
						// the stem
						pose.pushMatrix();
						pose.translate(f.x, f.y);
						pose.rotate(f.rot);
						pose.scale(f.size * 0.18f, f.size * 0.5f);
						pose.translate(-0.5f, 0.6f);
						g.fill(0, 0, 1, 1, Draw.withAlpha(0x5E3A1E, f.alpha * intro));
						pose.popMatrix();
					}
				}
				default -> {
					pose.pushMatrix();
					pose.translate(f.x, f.y);
					pose.scale(f.size, f.size);
					g.fill(0, 0, 1, 1, Draw.withAlpha(f.color, f.alpha * intro));
					pose.popMatrix();
				}
			}
		}
	}

	private static void glow(GuiGraphicsExtractor g, Matrix3x2fStack pose, float x, float y, float size, int argb) {
		pose.pushMatrix();
		pose.translate(x - size / 2f, y - size / 2f);
		pose.scale(size, size);
		g.fill(0, 0, 1, 1, argb);
		pose.popMatrix();
	}
}
