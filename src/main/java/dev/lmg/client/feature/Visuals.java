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
			case DOT -> dot(g, col, outline, Math.max(1, LmgConfig.get().crosshairDot));
			case CROSS -> cross(g, col, outline);
			case CROSS_DOT -> {
				cross(g, col, outline);
				if (LmgConfig.get().crosshairDot > 0) dot(g, col, outline, LmgConfig.get().crosshairDot - 1);
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
		LmgConfig c = LmgConfig.get();
		int gap = c.crosshairGap, len = c.crosshairLength, th = c.crosshairThickness;
		int a = -(th - 1) / 2, b = a + th;   // the line's thickness, centred on the middle pixel
		// the dark outline first, then the colour on top
		if (outline != 0) {
			g.fill(-gap - len - 1, a - 1, -gap + 1, b + 1, outline);
			g.fill(gap, a - 1, gap + len + 2, b + 1, outline);
			g.fill(a - 1, -gap - len - 1, b + 1, -gap + 1, outline);
			g.fill(a - 1, gap, b + 1, gap + len + 2, outline);
		}
		g.fill(-gap - len, a, -gap, b, col);
		g.fill(gap + 1, a, gap + len + 1, b, col);
		g.fill(a, -gap - len, b, -gap, col);
		g.fill(a, gap + 1, b, gap + len + 1, col);
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
		long life = Math.round(LmgConfig.get().damageTime * 1000);
		numbers.removeIf(n -> now - n.born > life);
	}

	/** Drawn on the HUD: each number floats up from where it was and fades. */
	public static void drawNumbers(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (numbers.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		LmgConfig c = LmgConfig.get();
		var cam = mc.gameRenderer.mainCamera();
		Vec3 camPos = cam.position();
		Vec3 look = Vec3.directionFromRotation(cam.xRot(), cam.yRot());
		long now = Util.getMillis();
		float life = c.damageTime * 1000f;
		int rgb = c.damageRgb;
		for (Number n : List.copyOf(numbers)) {
			float t = (now - n.born) / life;
			Vec3 p = n.at.add(n.drift * t, t * 0.9, 0);
			if (p.subtract(camPos).dot(look) <= 0.1) continue;   // behind you
			Vec3 sp = mc.gameRenderer.projectPointToScreen(p);
			float x = (float) ((sp.x + 1) / 2 * g.guiWidth()), y = (float) ((1 - sp.y) / 2 * g.guiHeight());
			float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
			float pop = 1f + 0.5f * Math.max(0f, 1f - t * 6f);   // pops in, then settles
			boolean big = c.damageCritBig && n.amount >= 6;
			float scale = c.damageSize * (big ? 1.35f : 1f) * pop;
			String text = n.amount % 1f == 0f ? String.valueOf((int) n.amount) : String.format(Locale.ROOT, "%.1f", n.amount);
			int col = big ? Draw.lerpRgb(rgb, 0xFFE070, 0.35f) : rgb;
			drawNumber(g, mc, text, x, y, scale, col, alpha, c.damageStyle);
		}
	}

	private static void drawNumber(GuiGraphicsExtractor g, Minecraft mc, String text, float x, float y, float scale, int rgb, float alpha,
								   LmgConfig.DamageStyle style) {
		var font = mc.font;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		int w = font.width(text);
		int tx = -w / 2, ty = -4;
		int a = Math.round(alpha * 255);
		if (a < 8) {
			pose.popMatrix();
			return;
		}
		switch (style) {
			case PLAIN -> g.text(font, text, tx, ty, Draw.withAlpha(rgb, alpha), false);
			case SHADOW -> g.text(font, text, tx, ty, Draw.withAlpha(rgb, alpha), true);
			case OUTLINE -> {
				int dark = Draw.withAlpha(0x000000, alpha * 0.85f);
				for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) g.text(font, text, tx + o[0], ty + o[1], dark, false);
				g.text(font, text, tx, ty, Draw.withAlpha(rgb, alpha), false);
			}
			case BUBBLE -> {
				Draw.roundRect(g, tx - 4, ty - 3, tx + w + 3, ty + 10, 5, Draw.withAlpha(rgb, alpha * 0.9f));
				g.text(font, text, tx, ty, Draw.withAlpha(Draw.lerpRgb(rgb, 0x000000, 0.75f), alpha), false);
			}
		}
		pose.popMatrix();
	}

	// ---- confirmed hits: combo, reach, CPS and hit sounds ------------------------------------------

	private static int combo;
	private static long lastHitAt, lastHurtAt;
	private static double lastReach = -1;
	private static final java.util.ArrayDeque<Long> leftClicks = new java.util.ArrayDeque<>(), rightClicks = new java.util.ArrayDeque<>();

	/** A mouse button went down in game (not in a menu). */
	public static void onClick(int button) {
		long now = Util.getMillis();
		if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) leftClicks.addLast(now);
		else if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) rightClicks.addLast(now);
	}

	private static int perSecond(java.util.ArrayDeque<Long> q) {
		long now = Util.getMillis();
		while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
		return q.size();
	}

	public static int leftCps() {
		return perSecond(leftClicks);
	}

	public static int rightCps() {
		return perSecond(rightClicks);
	}

	/** Hits in a row without getting hit back (resets after 3 seconds of no hits). */
	public static int combo() {
		if (Util.getMillis() - lastHitAt > 3000) combo = 0;
		return combo;
	}

	/** How far away your last hit landed, in blocks (-1 if none lately). */
	public static double lastReach() {
		return Util.getMillis() - lastHitAt > 4000 ? -1 : lastReach;
	}

	/**
	 * The game says something took damage. If you did it, that's a real hit (counted for the combo,
	 * the reach readout and the hit sound); if it was you who got hurt, the combo ends.
	 */
	public static void onDamage(Entity victim, net.minecraft.world.damagesource.DamageSource source) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		long now = Util.getMillis();
		if (victim == mc.player) {
			combo = 0;
			lastHurtAt = now;
			return;
		}
		if (source.getEntity() != mc.player) return;
		if (now - lastHitAt > 3000) combo = 0;
		combo++;
		lastHitAt = now;
		Vec3 eye = mc.player.getEyePosition();
		var box = victim.getBoundingBox();
		double cx = Math.max(box.minX, Math.min(eye.x, box.maxX));
		double cy = Math.max(box.minY, Math.min(eye.y, box.maxY));
		double cz = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
		lastReach = eye.distanceTo(new Vec3(cx, cy, cz));
		hitSound(victim instanceof net.minecraft.world.entity.player.Player);
	}

	private static void hitSound(boolean player) {
		LmgConfig c = LmgConfig.get();
		if (c.hitSound == LmgConfig.HitSound.OFF || (c.hitSoundPlayersOnly && !player)) return;
		playHitSound(c.hitSound, c.hitSoundVolume, c.hitSoundPitch);
	}

	/** Also used by the menu, so you can hear each one. */
	public static void playHitSound(LmgConfig.HitSound sound, float volume, float pitch) {
		var sm = Minecraft.getInstance().getSoundManager();
		net.minecraft.client.resources.sounds.SimpleSoundInstance s = switch (sound) {
			case CLICK -> net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.6f * pitch, volume * 0.6f);
			case DING -> net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f * pitch, volume);
			case BELL -> net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f * pitch, volume);
			case PLING -> net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f * pitch, volume);
			case BASS -> net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f * pitch, volume);
			case POP -> net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.5f * pitch, volume);
			case CHIME -> net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f * pitch, volume);
			default -> null;
		};
		if (s != null) sm.play(s);
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
