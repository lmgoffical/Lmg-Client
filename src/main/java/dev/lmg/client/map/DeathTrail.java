package dev.lmg.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.feature.LmgKeys;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Quads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Getting back to where you died: the way you walked before it (a trail of soft dots on the
 * ground, and a dotted line on the maps), a red beam over the spot, and a little compass at the
 * top of the screen with how far it is. Gone once you get there (or after half an hour).
 */
public final class DeathTrail {
	private static final int MAX_POINTS = 1200;
	private static final double STEP = 1.6;      // a dot every this many blocks
	private static final long KEEP_MS = 30 * 60 * 1000L;

	/** Where you've been lately (this world and dimension), oldest first. */
	private static final List<Vec3> recent = new ArrayList<>();
	/** The path that led to your last death, ending where you died. */
	private static List<Vec3> path = List.of();
	private static Vec3 deathAt;
	private static ResourceKey<Level> deathDim;
	private static long diedAt;
	private static boolean wasDead;
	private static ResourceKey<Level> recordingDim;

	private DeathTrail() {}

	public static Vec3 deathPoint() {
		return deathAt;
	}

	public static List<Vec3> path() {
		return path;
	}

	/** True while there's a way back to show in this dimension. */
	public static boolean active(Minecraft mc) {
		return deathAt != null && mc.level != null && mc.level.dimension().equals(deathDim) && LmgConfig.get().deathTrail
				&& Util.getMillis() - diedAt < KEEP_MS;
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			recent.clear();
			return;
		}
		// a new world or dimension: start a new recording
		if (!mc.level.dimension().equals(recordingDim)) {
			recent.clear();
			recordingDim = mc.level.dimension();
		}
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead) onDeath(mc);
		wasDead = dead;
		if (dead) return;

		Vec3 here = mc.player.position();
		if (recent.isEmpty() || recent.get(recent.size() - 1).distanceTo(here) >= STEP) {
			// a jump (teleport, respawn) breaks the trail: start fresh
			if (!recent.isEmpty() && recent.get(recent.size() - 1).distanceTo(here) > 12) recent.clear();
			recent.add(here);
			if (recent.size() > MAX_POINTS) recent.remove(0);
		}
		// made it back
		if (active(mc) && here.distanceTo(deathAt) < 3.5) {
			LmgKeys.say("You made it back to where you died.");
			clear();
		}
	}

	private static void onDeath(Minecraft mc) {
		if (!LmgConfig.get().deathTrail) return;
		deathAt = mc.player.position();
		deathDim = mc.level.dimension();
		diedAt = Util.getMillis();
		List<Vec3> p = new ArrayList<>(recent);
		p.add(deathAt);
		path = p;
		recent.clear();
	}

	public static void clear() {
		deathAt = null;
		path = List.of();
	}

	// ---- the compass at the top --------------------------------------------------------------------

	public static void drawCompass(GuiGraphicsExtractor g, Minecraft mc) {
		if (!active(mc) || mc.player == null || mc.gui.hud.isHidden() || mc.player.isDeadOrDying()) return;
		var font = mc.font;
		Vec3 me = mc.player.position();
		double dx = deathAt.x - me.x, dz = deathAt.z - me.z;
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		// which way, compared to where you're looking (0 = straight ahead)
		double target = Math.toDegrees(Math.atan2(-dx, dz));
		float rel = Mth.wrapDegrees((float) (target - mc.player.getYRot()));
		String label = "Where you died";
		String meters = dist + " m";
		int w = 26 + font.width(label) + 8 + font.width(meters) + 10;
		// top middle, just under the HUD bar
		int x0 = g.guiWidth() / 2 - w / 2, y0 = 28;
		Draw.roundRect(g, x0, y0, x0 + w, y0 + 18, 6, 0xC80B0F18);
		Draw.roundRect(g, x0, y0, x0 + w, y0 + 1, 1, 0x66FF6B78);
		// the arrow: a little triangle turned toward the spot
		float cx = x0 + 12, cy = y0 + 9;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate((float) Math.toRadians(rel));
		for (int i = 0; i < 6; i++) g.fill(-i / 2 - 1, -4 + i, i / 2 + 1, -3 + i, 0xFFFF6B78);
		pose.popMatrix();
		g.text(font, label, x0 + 24, y0 + 5, 0xFFE8EDF5, false);
		g.text(font, meters, x0 + 24 + font.width(label) + 8, y0 + 5, 0xFFFF9AA3, false);
		if (deathAt.y < me.y - 8) g.text(font, "below", x0 + w + 4, y0 + 5, 0xAA9DAAC0, false);
		else if (deathAt.y > me.y + 8) g.text(font, "above", x0 + w + 4, y0 + 5, 0xAA9DAAC0, false);
	}

	// ---- in the world -------------------------------------------------------------------------------

	public static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!active(mc) || mc.player == null) return;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		List<Vec3> p = path;
		Vec3 d = deathAt;
		float t = (Util.getMillis() % 100000L) / 1000f;
		ctx.submitNodeCollector().submitCustomGeometry(pose,
				RenderTypes.entityTranslucentEmissive(LmgClient.id("textures/cosmetic/white.png")), (pp, vc) -> {
					// the beam over the spot: a soft red column, brighter in the middle
					float bx = (float) (d.x - cam.x), by = (float) (d.y - cam.y), bz = (float) (d.z - cam.z);
					for (int layer = 0; layer < 3; layer++) {
						float r = 0.12f + layer * 0.16f;
						int a = layer == 0 ? 0xB0 : layer == 1 ? 0x50 : 0x22;
						int top = (a / 6) << 24 | 0xFF6B78, bottom = a << 24 | 0xFF6B78;
						column(vc, pp, bx, by, bz, r, 48f, top, bottom);
					}
					// the trail: little flat diamonds on the ground; a brighter pulse runs along toward the spot
					int n = p.size();
					for (int i = 0; i < n; i++) {
						Vec3 v = p.get(i);
						if (v.distanceToSqr(cam) > 56 * 56) continue;
						float along = i / (float) Math.max(1, n - 1);
						float pulse = (float) Math.pow(0.5 + 0.5 * Math.cos((along * 30f - t * 3f) * Math.PI), 6);
						int a = (int) (120 + 120 * pulse);
						float s = 0.13f + 0.08f * pulse;
						// a little sparkle floating just above the ground (and above any settled snow):
						// three crossed diamonds, so it reads from any angle, bobbing gently
						float bob = (float) Math.sin(t * 2.4f + i * 0.7f) * 0.04f;
						float x = (float) (v.x - cam.x), y = (float) (v.y - cam.y) + 0.32f + bob, z = (float) (v.z - cam.z);
						int argb = a << 24 | 0xFF << 16 | (int) Mth.lerp(pulse, 0x6B, 0xD8) << 8 | (int) Mth.lerp(pulse, 0x78, 0xDE);
						Quads.quad(vc, pp, x, y, z - s, x + s, y, z, x, y, z + s, x - s, y, z, 0, 0, 1, 1, argb, Quads.FULL_BRIGHT, 0, 1, 0);
						Quads.quad(vc, pp, x, y + s, z, x + s, y, z, x, y - s, z, x - s, y, z, 0, 0, 1, 1, argb, Quads.FULL_BRIGHT, 0, 0, 1);
						Quads.quad(vc, pp, x, y + s, z, x, y, z + s, x, y - s, z, x, y, z - s, 0, 0, 1, 1, argb, Quads.FULL_BRIGHT, 1, 0, 0);
						// and a faint glow on the ground under it
						float g2 = s * 2.4f, gy = (float) (v.y - cam.y) + 0.16f;
						Quads.quad(vc, pp, x, gy, z - g2, x + g2, gy, z, x, gy, z + g2, x - g2, gy, z,
								0, 0, 1, 1, (a / 5) << 24 | 0xFF6B78, Quads.FULL_BRIGHT, 0, 1, 0);
					}
				});
	}

	/** A four-sided see-through column, fading toward the top. */
	private static void column(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float r, float h,
							   int topArgb, int bottomArgb) {
		Quads.gradientQuad(vc, p, x - r, y + h, z - r, x + r, y + h, z - r, x + r, y, z - r, x - r, y, z - r, topArgb, bottomArgb, Quads.FULL_BRIGHT);
		Quads.gradientQuad(vc, p, x + r, y + h, z + r, x - r, y + h, z + r, x - r, y, z + r, x + r, y, z + r, topArgb, bottomArgb, Quads.FULL_BRIGHT);
		Quads.gradientQuad(vc, p, x - r, y + h, z + r, x - r, y + h, z - r, x - r, y, z - r, x - r, y, z + r, topArgb, bottomArgb, Quads.FULL_BRIGHT);
		Quads.gradientQuad(vc, p, x + r, y + h, z - r, x + r, y + h, z + r, x + r, y, z + r, x + r, y, z - r, topArgb, bottomArgb, Quads.FULL_BRIGHT);
	}

	/** For the in-game tests: pretend you walked this way and died at the end. */
	public static void testDeath(List<Vec3> walked, ResourceKey<Level> dim) {
		List<Vec3> p = new ArrayList<>(walked);
		deathAt = p.get(p.size() - 1);
		deathDim = dim;
		diedAt = Util.getMillis();
		path = p;
	}

	static BlockPos blockOf(Vec3 v) {
		return BlockPos.containing(v);
	}
}
