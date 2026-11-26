package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small visual extras: your own crosshair, floating damage numbers, and the colour things flash
 * when they get hurt.
 */
public final class Visuals {
	private Visuals() {}

	// ---- crosshair ---------------------------------------------------------------------------------

	/** Draws the chosen crosshair; returns false to let the game draw its own. */
	public static boolean drawCrosshair(GuiGraphicsExtractor g) {
		LmgConfig c = LmgConfig.get();
		if (c.crosshair == null || c.crosshair == LmgConfig.Crosshair.VANILLA) return false;
		Minecraft mc = Minecraft.getInstance();
		if (!mc.options.getCameraType().isFirstPerson() || mc.player == null || mc.player.isSpectator()) return true;
		float s = LmgConfig.CROSSHAIR_SIZES[c.crosshairSize];
		int rgb = c.crosshairRgb;
		// turn your "in reach" colour when what you're aiming at can be hit
		if (c.crosshairTargetColor && mc.crosshairPickEntity instanceof LivingEntity l && l.isAlive()
				&& mc.player.isWithinEntityInteractionRange(l, 0.0)) rgb = c.reachColor;
		int col = 0xFF000000 | rgb, outline = c.crosshairOutline ? 0xB0000000 : 0;
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(s, s);
		float spin = LmgConfig.SPIN_SPEEDS[c.crosshairSpin];
		if (spin > 0) pose.rotate((float) Math.toRadians((Util.getMillis() % 360_000L) / 1000.0 * spin % 360.0));
		draw(g, c.crosshair, c.crosshairPixels, col, outline);
		pose.popMatrix();
		return true;
	}

	/** Draws a crosshair around (0, 0); also used for the previews in the editor. */
	public static void draw(GuiGraphicsExtractor g, LmgConfig.Crosshair shape, int[] pixels, int col, int outline) {
		Matrix3x2fStack pose = g.pose();
		switch (shape) {
			case DOT -> dot(g, col, outline, 1);
			case CROSS -> cross(g, col, outline);
			case CROSS_DOT -> {
				cross(g, col, outline);
				dot(g, col, outline, 0);
			}
			case CIRCLE -> {
				for (int pass = 0; pass < 2; pass++) {
					for (int i = 0; i < 28; i++) {
						double a = i * Math.PI * 2 / 28;
						float px = (float) Math.cos(a) * 4.5f, py = (float) Math.sin(a) * 4.5f;
						pose.pushMatrix();
						pose.translate(px - (pass == 0 ? 1 : 0.5f), py - (pass == 0 ? 1 : 0.5f));
						if (pass == 0) pose.scale(2, 2);
						g.fill(0, 0, 1, 1, pass == 0 ? outline : col);
						pose.popMatrix();
					}
				}
				dot(g, col, outline, 0);
			}
			case CHEVRON -> {
				for (int pass = 0; pass < 2; pass++) {
					for (int i = 0; i < 5; i++) {
						int o = pass == 0 ? 1 : 0;
						g.fill(-i - o, i + 1 - o, -i + 1 + o, i + 2 + o, pass == 0 ? outline : col);
						g.fill(i - o, i + 1 - o, i + 1 + o, i + 2 + o, pass == 0 ? outline : col);
					}
				}
			}
			case LOGO -> {
				// the Lmg star; its dark edge is part of the picture, the white takes your colour
				int argb = 0xFF000000 | (col & 0xFFFFFF);
				g.blit(RenderPipelines.GUI_TEXTURED, LOGO, -8, -8, 0f, 0f, 16, 16, 64, 64, 64, 64, argb);
			}
			case DRAWN -> drawPixels(g, pixels, col, outline);
			default -> {
			}
		}
	}

	private static final Identifier LOGO = dev.lmg.client.LmgClient.id("textures/gui/crosshair_logo.png");

	/** Your drawing, centred: the dark edge first, then the pixels on top. */
	public static void drawPixels(GuiGraphicsExtractor g, int[] rows, int col, int outline) {
		int n = LmgConfig.DRAW_SIZE, half = n / 2;
		if (outline != 0) {
			for (int y = 0; y < n; y++) {
				for (int x = 0; x < n; x++) {
					if ((rows[y] >> x & 1) != 0) g.fill(x - half - 1, y - half - 1, x - half + 2, y - half + 2, outline);
				}
			}
		}
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				if ((rows[y] >> x & 1) != 0) g.fill(x - half, y - half, x - half + 1, y - half + 1, col);
			}
		}
	}

	private static void dot(GuiGraphicsExtractor g, int col, int outline, int r) {
		g.fill(-1 - r, -1 - r, 2 + r, 2 + r, outline);
		g.fill(-r, -r, 1 + r, 1 + r, col);
	}

	private static void cross(GuiGraphicsExtractor g, int col, int outline) {
		int gap = 2, len = 4;
		// the dark outline first, then the colour on top
		g.fill(-gap - len - 1, -1, -gap + 1, 2, outline);
		g.fill(gap, -1, gap + len + 2, 2, outline);
		g.fill(-1, -gap - len - 1, 2, -gap + 1, outline);
		g.fill(-1, gap, 2, gap + len + 2, outline);
		g.fill(-gap - len, 0, -gap, 1, col);
		g.fill(gap + 1, 0, gap + len + 1, 1, col);
		g.fill(0, -gap - len, 1, -gap, col);
		g.fill(0, gap + 1, 1, gap + len + 1, col);
	}

	// ---- damage numbers ----------------------------------------------------------------------------

	private record Number(Vec3 at, float amount, long born, float drift) {}

	private static final List<Number> numbers = new ArrayList<>();
	private static final Map<Integer, Float> lastHealth = new HashMap<>();
	private static final Map<Integer, Long> hitByMe = new HashMap<>();

	/** You hit something: its next health drop gets a number. */
	public static void onAttack(Entity target) {
		hitByMe.put(target.getId(), Util.getMillis());
	}

	public static void tick(Minecraft mc) {
		if (!LmgConfig.get().damageNumbers || mc.level == null || mc.player == null) {
			lastHealth.clear();
			numbers.clear();
			return;
		}
		long now = Util.getMillis();
		hitByMe.values().removeIf(t -> now - t > 2000);
		for (var it = hitByMe.keySet().iterator(); it.hasNext(); ) {
			int id = it.next();
			Entity e = mc.level.getEntity(id);
			if (!(e instanceof LivingEntity l)) {
				it.remove();
				continue;
			}
			float h = l.getHealth();
			Float was = lastHealth.put(id, h);
			if (was != null && h < was - 0.01f) {
				Vec3 at = l.position().add(0, l.getBbHeight() + 0.25, 0);
				numbers.add(new Number(at, was - h, now, (float) (Math.random() - 0.5) * 0.6f));
			}
		}
		lastHealth.keySet().removeIf(id -> !hitByMe.containsKey(id));
		numbers.removeIf(n -> now - n.born > 1100);
	}

	/** Drawn on the HUD: each number floats up from where it was and fades. */
	public static void drawNumbers(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (numbers.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		var cam = mc.gameRenderer.mainCamera();
		Vec3 camPos = cam.position();
		Vec3 look = Vec3.directionFromRotation(cam.xRot(), cam.yRot());
		long now = Util.getMillis();
		int rgb = LmgConfig.get().damageRgb;
		for (Number n : List.copyOf(numbers)) {
			float t = (now - n.born) / 1100f;
			Vec3 p = n.at.add(n.drift * t, t * 0.9, 0);
			if (p.subtract(camPos).dot(look) <= 0.1) continue;   // behind you
			Vec3 sp = mc.gameRenderer.projectPointToScreen(p);
			float x = (float) ((sp.x + 1) / 2 * g.guiWidth()), y = (float) ((1 - sp.y) / 2 * g.guiHeight());
			float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
			float pop = 1f + 0.5f * Math.max(0f, 1f - t * 6f);   // pops in, then settles
			float scale = (n.amount >= 6 ? 1.35f : 1f) * pop;
			String text = n.amount % 1f == 0f ? String.valueOf((int) n.amount) : String.format(Locale.ROOT, "%.1f", n.amount);
			Draw.scaledText(g, mc.font, text, x, y, scale, Draw.withAlpha(rgb, alpha));
		}
	}

	// ---- hurt colour -------------------------------------------------------------------------------

	/** Something that can recolour the "hurt" flash (the game's overlay texture). */
	public interface HurtTint {
		void lmg$hurtColor(int rgb);
	}

	public static HurtTint overlay;

	public static void applyHurtColor() {
		if (overlay != null) overlay.lmg$hurtColor(LmgConfig.get().hurtRgb);
	}
}
