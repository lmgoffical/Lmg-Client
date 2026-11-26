package dev.lmg.client.render;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Snow for the Right Shift menu. Some flakes fall behind the panel, some in front of it, and the
 * bigger ones pile up on the panel's top edge. Shared by every Lmg screen so the snow doesn't
 * restart when you go from one page to another; it only starts over when the menu is opened again.
 */
public final class MenuSnow {
	public static final int[] AMOUNTS = {0, 55, 110, 220};
	public static final String[] LABELS = {"Off", "Light", "Snowy", "Blizzard"};

	private static final class Flake {
		float x, y, speed, sway, swayRate, phase, size, alpha;
		boolean front;
	}

	private static final List<Flake> flakes = new ArrayList<>();
	private static final Random random = new Random();
	private static long lastFrame = -1;
	private static float time;
	private static int screenW, screenH;

	// the little pile on top of the panel
	private static float[] pile = new float[0];
	private static int pileX0 = Integer.MIN_VALUE, pileY, pileX1;

	private MenuSnow() {}

	/** Greys out and darkens the (already blurred) world behind the menu. */
	public static void greyBackdrop(GuiGraphicsExtractor g, int w, int h) {
		g.fill(0, 0, w, h, 0xB8363A41);                // neutral grey wash takes most of the colour out
		g.fillGradient(0, 0, w, h / 3, 0x55000000, 0x00000000);
		g.fillGradient(0, h - h / 3, w, h, 0x00000000, 0x66000000);
	}

	private static int target(int w, int h) {
		int amount = LmgConfig.get().menuSnow;
		if (amount <= 0 || amount >= AMOUNTS.length) return 0;
		// AMOUNTS are for a 480x270 screen; bigger GUI areas get more flakes
		return Math.round(AMOUNTS[amount] * (w * h) / (480f * 270f));
	}

	/** Moves the snow along. Call once per frame, before drawing. */
	public static void update(int w, int h) {
		long now = Util.getMillis();
		boolean restart = lastFrame < 0 || now - lastFrame > 1500 || w != screenW || h != screenH;
		float dt = restart ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		screenW = w;
		screenH = h;
		int want = target(w, h);

		if (restart) {
			// menu just opened: everything starts above the screen and pours in
			flakes.clear();
			pile = new float[0];
			pileX0 = Integer.MIN_VALUE;
			for (int i = 0; i < want; i++) flakes.add(spawn(w, -4 - random.nextFloat() * h * 1.1f));
			return;
		}
		while (flakes.size() < want) flakes.add(spawn(w, -4 - random.nextFloat() * 20));
		while (flakes.size() > want) flakes.remove(flakes.size() - 1);

		time += dt;
		boolean blizzard = LmgConfig.get().menuSnow == 3;
		float wind = (float) Math.sin(time * 0.25f) * 5f + (blizzard ? 14f : 2f);

		for (Flake f : flakes) {
			float prevY = f.y;
			f.y += f.speed * dt;
			f.x += (wind * f.size * 0.6f + (float) Math.cos(time * f.swayRate + f.phase) * f.sway) * dt;
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
		f.front = random.nextFloat() < 0.3f;
		// three depths: far ones are small, faint and slow
		float depth = f.front ? 0.7f + random.nextFloat() * 0.3f : random.nextFloat() * 0.75f;
		f.size = 0.75f + depth * 1.5f;
		f.speed = 9f + depth * 26f + random.nextFloat() * 6f;
		f.alpha = f.front ? 0.55f + random.nextFloat() * 0.3f : 0.25f + depth * 0.6f;
		f.sway = 2f + random.nextFloat() * 5f;
		f.swayRate = 0.6f + random.nextFloat() * 1.4f;
		f.phase = random.nextFloat() * 6.28f;
	}

	// ---- the pile on the panel -----------------------------------------------------------------

	/** Tells the snow where the panel's top edge is. If the panel moved, the pile falls off. */
	public static void panelTop(int x0, int y0, int x1) {
		int left = x0 + 5, right = x1 - 5;
		if (left == pileX0 && y0 == pileY && right == pileX1) return;
		// shake the old pile off as flakes
		for (int i = 0; i < pile.length; i += 2) {
			if (pile[i] < 0.6f) continue;
			Flake f = spawn(screenW, pileY - pile[i]);
			f.x = pileX0 + i;
			f.front = true;
			f.speed = 30f + random.nextFloat() * 20f;
			flakes.add(f);
		}
		pileX0 = left;
		pileX1 = right;
		pileY = y0;
		pile = new float[Math.max(0, right - left)];
	}

	private static final float[] SPREAD = {1f, 0.75f, 0.45f, 0.2f};

	private static boolean landOnPile(Flake f, float prevY) {
		if (pile.length == 0) return false;
		int col = (int) (f.x - pileX0);
		if (col < 0 || col >= pile.length) return false;
		float top = pileY - pile[col];
		if (prevY > top || f.y < top) return false;
		if (random.nextFloat() < 0.25f) return false; // some slip past the edge
		float add = 0.7f * f.size;
		for (int d = -3; d <= 3; d++) {
			int c = col + d;
			if (c >= 0 && c < pile.length) pile[c] = Math.min(5f, pile[c] + add * SPREAD[Math.abs(d)]);
		}
		return true;
	}

	/** Spreads the pile out into soft lumps (like snow settling), thinner near the rounded corners. */
	private static void settlePile(float dt) {
		if (pile.length < 3) return;
		float k = Math.min(0.3f, dt * 1.5f);
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

	/** Flakes behind the panel. */
	public static void drawBack(GuiGraphicsExtractor g, float intro) {
		draw(g, false, intro);
	}

	/** Flakes in front of the panel, plus the pile on its top edge. */
	public static void drawFront(GuiGraphicsExtractor g, float intro) {
		if (LmgConfig.get().menuSnow <= 0) return;
		Matrix3x2fStack pose = g.pose();
		int body = Draw.withAlpha(0xDCE5F0, intro), top = Draw.withAlpha(0xFFFFFF, intro);
		for (int i = 0; i < pile.length; i++) {
			float hgt = pile[i];
			if (hgt < 0.15f) continue;
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
			pose.pushMatrix();
			pose.translate(f.x, f.y);
			pose.scale(f.size, f.size);
			g.fill(0, 0, 1, 1, Draw.withAlpha(0xF4F8FF, f.alpha * intro));
			pose.popMatrix();
		}
	}
}
