package dev.lmg.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.hud.Targeting;
import dev.lmg.client.render.Quads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Things drawn into the 3D world: the block overlay, the target reticle and the aurora. */
public final class WorldRender {
	private static final Identifier WHITE = LmgClient.id("textures/cosmetic/white.png");

	private WorldRender() {}

	private static RenderType glowType() {
		return RenderTypes.entityTranslucentEmissive(WHITE);
	}

	private static float seconds() {
		return Util.getMillis() / 1000f;
	}

	// ------------------------------------------------------------------------------------------
	// Block overlay: replaces the thin black outline of the block you're looking at.
	// ------------------------------------------------------------------------------------------

	/** @return true to let vanilla draw its normal outline */
	public static boolean blockOutline(LevelRenderContext ctx, BlockOutlineRenderState outline) {
		LmgConfig cfg = LmgConfig.get();
		if (cfg.blockOverlay == LmgConfig.BlockOverlay.VANILLA || outline == null) return true;

		float t = seconds();
		int rgb = cfg.overlayRgb(t * 20f);
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4f);
		float edgeAlpha = cfg.blockOverlay == LmgConfig.BlockOverlay.GLOW ? 0.55f + 0.45f * pulse : 0.95f;
		float fillAlpha = switch (cfg.blockOverlay) {
			case FILLED -> 0.16f;
			case GLOW -> 0.08f + 0.14f * pulse;
			default -> 0f;
		};
		int edge = argb(edgeAlpha, rgb);
		int fill = argb(fillAlpha, rgb);

		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		BlockPos pos = outline.pos();
		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate((float) (pos.getX() - cam.x), (float) (pos.getY() - cam.y), (float) (pos.getZ() - cam.z));
		ctx.submitNodeCollector().submitCustomGeometry(pose, glowType(), (p, vc) -> {
			for (AABB box : outline.shape().toAabbs()) {
				float e = 0.002f;
				float x0 = (float) box.minX - e, y0 = (float) box.minY - e, z0 = (float) box.minZ - e;
				float x1 = (float) box.maxX + e, y1 = (float) box.maxY + e, z1 = (float) box.maxZ + e;
				if (fillAlpha > 0) Quads.box(vc, p, x0, y0, z0, x1, y1, z1, fill, Quads.FULL_BRIGHT);
				Quads.boxEdges(vc, p, x0, y0, z0, x1, y1, z1, 0.014f, edge, Quads.FULL_BRIGHT);
			}
		});
		pose.popPose();
		return false;
	}

	// ------------------------------------------------------------------------------------------
	// Target reticle: spinning ring under the mob/player you're aiming at or just hit.
	// ------------------------------------------------------------------------------------------

	public static void targetReticle(LevelRenderContext ctx) {
		if (!LmgConfig.get().targetReticle) return;
		Minecraft mc = Minecraft.getInstance();
		LivingEntity living = Targeting.current();
		if (living == null || living == mc.player) return;
		LmgConfig cfg = LmgConfig.get();
		if (living.distanceTo(mc.player) > LmgConfig.TARGET_RANGES[cfg.targetRange]) return;
		Entity target = living;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		Vec3 at = target.getPosition(partial);
		float t = seconds();
		float radius = target.getBbWidth() * 0.85f + 0.25f;
		int rgb = cfg.reachColorOn && mc.player.isWithinEntityInteractionRange(living, 0.0) ? cfg.reachColor & 0xFFFFFF : cfg.targetRgb();
		float hurt = living.hurtTime > 0 ? 1f : 0f;
		int color = argb(0.85f, hurt > 0 ? 0xFF5050 : rgb);

		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate((float) (at.x - cam.x), (float) (at.y - cam.y) + 0.04f, (float) (at.z - cam.z));
		ctx.submitNodeCollector().submitCustomGeometry(pose, glowType(), (p, vc) -> {
			int arcs = 3;
			int steps = 14;
			float thickness = 0.07f;
			for (int a = 0; a < arcs; a++) {
				double start = t * 1.6 + a * Math.PI * 2 / arcs;
				double span = Math.PI * 2 / arcs * 0.62;
				for (int i = 0; i < steps; i++) {
					double a0 = start + span * i / steps;
					double a1 = start + span * (i + 1) / steps;
					float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
					float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
					float ri = radius - thickness, ro = radius + thickness;
					Quads.quad(vc, p, c0 * ri, 0, s0 * ri, c1 * ri, 0, s1 * ri, c1 * ro, 0, s1 * ro, c0 * ro, 0, s0 * ro,
							0, 0, 1, 1, color, Quads.FULL_BRIGHT, 0, 1, 0);
				}
			}
		});
		pose.popPose();
	}

	// ------------------------------------------------------------------------------------------
	// Aurora: glowing curtains in the night sky that slowly wave. Follows the camera like the sky.
	// ------------------------------------------------------------------------------------------

	public static void aurora(LevelRenderContext ctx) {
		LmgConfig cfg = LmgConfig.get();
		if (cfg.aurora == LmgConfig.Aurora.OFF) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
		float night = 1f - WorldVisuals.daylight();
		if (night < 0.05f) return;

		float t = seconds();
		PoseStack pose = ctx.poseStack();
		ctx.submitNodeCollector().submitCustomGeometry(pose, glowType(), (p, vc) -> {
			int bands = 3;
			for (int b = 0; b < bands; b++) {
				float radius = 62f + b * 9f;
				float baseY = 34f + b * 5f + cfg.auroraHeight;
				double from = Math.toRadians(195 + b * 25);
				double to = from + Math.toRadians(150);
				int steps = 60;
				for (int i = 0; i < steps; i++) {
					double a0 = from + (to - from) * i / steps;
					double a1 = from + (to - from) * (i + 1) / steps;
					float w0 = wave(a0, t, b), w1 = wave(a1, t, b);
					float r0 = radius + w0 * 7f, r1 = radius + w1 * 7f;
					float x0 = (float) Math.cos(a0) * r0, z0 = (float) Math.sin(a0) * r0;
					float x1 = (float) Math.cos(a1) * r1, z1 = (float) Math.sin(a1) * r1;
					float h0 = 18f + 10f * (0.5f + 0.5f * (float) Math.sin(a0 * 5 + t * 0.4 + b));
					float h1 = 18f + 10f * (0.5f + 0.5f * (float) Math.sin(a1 * 5 + t * 0.4 + b));
					// fade in/out at the ends of each band
					float edge0 = edgeFade(i, steps), edge1 = edgeFade(i + 1, steps);
					float shimmer = 0.75f + 0.25f * (float) Math.sin(a0 * 11 + t * 1.3 + b * 2);
					int rgb0 = auroraColor(cfg, a0, t), rgb1 = auroraColor(cfg, a1, t);
					float alpha = Math.min(1f, 0.32f * night * shimmer * cfg.auroraBrightness);
					// bottom is bright, top fades to nothing
					Quads.vertex(vc, p, x0, baseY + h0, z0, 0, 0, argb(0, rgb0), Quads.FULL_BRIGHT, 0, 1, 0);
					Quads.vertex(vc, p, x1, baseY + h1, z1, 1, 0, argb(0, rgb1), Quads.FULL_BRIGHT, 0, 1, 0);
					Quads.vertex(vc, p, x1, baseY, z1, 1, 1, argb(alpha * edge1, rgb1), Quads.FULL_BRIGHT, 0, 1, 0);
					Quads.vertex(vc, p, x0, baseY, z0, 0, 1, argb(alpha * edge0, rgb0), Quads.FULL_BRIGHT, 0, 1, 0);
				}
			}
		});
	}

	private static float wave(double angle, float t, int band) {
		return (float) (Math.sin(angle * 7 + t * 0.5 + band) * 0.6 + Math.sin(angle * 13 - t * 0.8 + band * 2) * 0.4);
	}

	private static float edgeFade(int i, int steps) {
		float k = i / (float) steps;
		return (float) Math.sin(Math.PI * k);
	}

	private static int auroraColor(LmgConfig cfg, double angle, float t) {
		return switch (cfg.aurora) {
			case ACCENT -> cfg.accentRgb();
			case CUSTOM -> cfg.auroraRgb;
			case RAINBOW -> java.awt.Color.HSBtoRGB((float) ((angle / (Math.PI * 2) + t * 0.03) % 1.0), 0.6f, 1f) & 0xFFFFFF;
			default -> cfg.aurora.rgb;
		};
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}
}
