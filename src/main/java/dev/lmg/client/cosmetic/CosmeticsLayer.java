package dev.lmg.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.LmgConfig.CosmeticColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/**
 * Draws Lmg Client cosmetics on YOUR player only: wings, halo, crown, cat ears, China hat.
 * Everything is built from a few textured quads, so no model files are needed.
 * Sizes below are in model pixels (1/16 of a block); y points DOWN in model space.
 */
public class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier WING = LmgClient.id("textures/cosmetic/wing.png");
	private static final Identifier HALO = LmgClient.id("textures/cosmetic/halo.png");
	private static final Identifier CROWN = LmgClient.id("textures/cosmetic/crown.png");
	private static final Identifier EAR = LmgClient.id("textures/cosmetic/cat_ear.png");
	private static final Identifier WHITE = LmgClient.id("textures/cosmetic/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float PX = 1f / 16f;

	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	/** True when this render state is the local player (you), not someone else. */
	public static boolean isYou(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && state.id == mc.player.getId();
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector nodes, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !isYou(state)) return;
		LmgConfig cfg = LmgConfig.get();
		float t = state.ageInTicks;
		PlayerModel model = getParentModel();

		boolean headStuff = cfg.halo != CosmeticColor.OFF || cfg.crown
				|| cfg.catEars != CosmeticColor.OFF || cfg.chinaHat != CosmeticColor.OFF;
		if (headStuff) {
			pose.pushPose();
			model.head.translateAndRotate(pose);
			if (cfg.chinaHat != CosmeticColor.OFF) chinaHat(pose, nodes, light, cfg.chinaHat, t);
			if (cfg.crown && cfg.chinaHat == CosmeticColor.OFF) crown(pose, nodes, light);
			if (cfg.catEars != CosmeticColor.OFF && cfg.chinaHat == CosmeticColor.OFF) catEars(pose, nodes, light, cfg.catEars.rgb(t), t);
			if (cfg.halo != CosmeticColor.OFF) halo(pose, nodes, cfg.halo.rgb(t), t);
			pose.popPose();
		}

		if (cfg.shield != CosmeticColor.OFF) shield(pose, nodes, cfg.shield.rgb(t), t);

		boolean elytra = state.chestEquipment.is(Items.ELYTRA) || state.isFallFlying;
		if (cfg.wings != CosmeticColor.OFF && !elytra) {
			pose.pushPose();
			model.body.translateAndRotate(pose);
			wings(pose, nodes, light, cfg.wings.rgb(t), state, t);
			pose.popPose();
		}
	}

	// ------------------------------------------------------------------------------------------
	// Wings: two textured feather planes on the upper back that flap gently.
	// ------------------------------------------------------------------------------------------

	private static void wings(PoseStack pose, SubmitNodeCollector nodes, int light, int rgb, AvatarRenderState s, float t) {
		float moving = Math.min(1f, s.walkAnimationSpeed * 1.5f);
		float speed = 0.09f + 0.10f * moving;
		float flap = (float) Math.sin(t * speed) * (10f + 10f * moving);
		float open = s.isCrouching ? 12f : 28f;      // folded a bit when sneaking
		float size = 17f * PX;
		int color = 0xFF000000 | rgb;
		RenderType type = RenderTypes.entityTranslucent(WING);

		for (int side = -1; side <= 1; side += 2) {
			final int sgn = side;
			pose.pushPose();
			pose.translate(sgn * 1.0f * PX, 2.0f * PX, 2.3f * PX);
			pose.mulPose(new Matrix4f()
					.rotateY((float) Math.toRadians(-sgn * (open + flap)))
					.rotateZ((float) Math.toRadians(-sgn * 12f)));
			nodes.submitCustomGeometry(pose, type, (p, vc) -> {
				float x1 = sgn * size;
				float y0 = -0.14f * size; // the shoulder sits near the top-left of the texture
				float y1 = y0 + size;
				quad(vc, p, 0, y0, 0, x1, y0, 0, x1, y1, 0, 0, y1, 0, 0, 0, 1, 1, color, light, 0, 0, 1);
			});
			pose.popPose();
		}
	}

	// ------------------------------------------------------------------------------------------
	// Halo: a glowing ring that floats above the head, bobbing and slowly spinning.
	// ------------------------------------------------------------------------------------------

	private static void halo(PoseStack pose, SubmitNodeCollector nodes, int rgb, float t) {
		float bob = (float) Math.sin(t * 0.08f) * 0.6f;
		pose.pushPose();
		pose.translate(0, (-12.0f + bob) * PX, 0);
		pose.mulPose(new Matrix4f().rotateY(t * 0.02f).rotateX((float) Math.toRadians(8)));
		int color = 0xF0000000 | rgb;
		float r = 5.0f * PX;
		nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(HALO), (p, vc) ->
				quad(vc, p, -r, 0, -r, r, 0, -r, r, 0, r, -r, 0, r, 0, 0, 1, 1, color, FULL_BRIGHT, 0, -1, 0));
		pose.popPose();
	}

	// ------------------------------------------------------------------------------------------
	// Crown: a gold band with spikes on all four sides of the head.
	// ------------------------------------------------------------------------------------------

	private static void crown(PoseStack pose, SubmitNodeCollector nodes, int light) {
		float h = 4.6f * PX;   // half width
		float y0 = -7.6f * PX; // bottom (just below the top of the head)
		float y1 = -11.8f * PX;
		int color = 0xFFFFFFFF;
		nodes.submitCustomGeometry(pose, RenderTypes.entityCutout(CROWN), (p, vc) -> {
			quad(vc, p, -h, y1, -h, h, y1, -h, h, y0, -h, -h, y0, -h, 0, 0, 1, 1, color, light, 0, 0, -1); // front
			quad(vc, p, h, y1, h, -h, y1, h, -h, y0, h, h, y0, h, 0, 0, 1, 1, color, light, 0, 0, 1);      // back
			quad(vc, p, -h, y1, h, -h, y1, -h, -h, y0, -h, -h, y0, h, 0, 0, 1, 1, color, light, -1, 0, 0); // left
			quad(vc, p, h, y1, -h, h, y1, h, h, y0, h, h, y0, -h, 0, 0, 1, 1, color, light, 1, 0, 0);      // right
		});
	}

	// ------------------------------------------------------------------------------------------
	// Cat ears: two triangles on top of the head that twitch now and then.
	// ------------------------------------------------------------------------------------------

	private static void catEars(PoseStack pose, SubmitNodeCollector nodes, int light, int rgb, float t) {
		int color = 0xFF000000 | rgb;
		float w = 3.4f * PX;
		float h = 3.6f * PX;
		RenderType type = RenderTypes.entityCutout(EAR);
		for (int side = -1; side <= 1; side += 2) {
			// a quick twitch every few seconds, left and right ear at different times
			float phase = (t + (side > 0 ? 47f : 0f)) % 110f;
			float twitch = phase < 6f ? (float) Math.sin(phase / 6f * Math.PI) * 14f : 0f;
			pose.pushPose();
			pose.translate(side * 2.4f * PX, -7.8f * PX, -0.6f * PX);
			pose.mulPose(new Matrix4f()
					.rotateZ((float) Math.toRadians(side * (14f + twitch)))
					.rotateX((float) Math.toRadians(-8f)));
			nodes.submitCustomGeometry(pose, type, (p, vc) ->
					quad(vc, p, -w / 2, -h, 0, w / 2, -h, 0, w / 2, 0, 0, -w / 2, 0, 0, 0, 0, 1, 1, color, light, 0, 0, -1));
			pose.popPose();
		}
	}

	// ------------------------------------------------------------------------------------------
	// China hat: a wide, shallow, see-through cone above the head.
	// ------------------------------------------------------------------------------------------

	private static void chinaHat(PoseStack pose, SubmitNodeCollector nodes, int light, CosmeticColor choice, float t) {
		int segments = 32;
		float radius = 7.5f * PX;
		float baseY = -8.6f * PX;
		float tipY = -12.6f * PX;
		nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (p, vc) -> {
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments + t * 0.01;
				double a1 = Math.PI * 2 * (i + 1) / segments + t * 0.01;
				int rgb = choice == CosmeticColor.RAINBOW
						? java.awt.Color.HSBtoRGB((float) (i / (double) segments + t / 120.0) % 1f, 0.55f, 1f) & 0xFFFFFF
						: choice.rgb(t);
				int color = 0xB0000000 | rgb;
				float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
				float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
				// triangle drawn as a quad with the tip repeated
				quad(vc, p, 0, tipY, 0, 0, tipY, 0, x1, baseY, z1, x0, baseY, z0, 0, 0, 1, 1, color, light, 0, -1, 0);
			}
		});
	}

	// ------------------------------------------------------------------------------------------
	// Energy shield: a see-through bubble around you with a glowing band sweeping over it.
	// ------------------------------------------------------------------------------------------

	private static void shield(PoseStack pose, SubmitNodeCollector nodes, int rgb, float t) {
		int lat = 14, lon = 28;
		float r = 19f * PX * (1f + 0.015f * (float) Math.sin(t * 0.15f));
		float cy = 12f * PX; // middle of the body in model space (y points down)
		float scan = (float) Math.sin(t * 0.06f);          // -1..1, sweeps up and down
		pose.pushPose();
		pose.mulPose(new Matrix4f().rotateY(t * 0.01f));
		nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (p, vc) -> {
			for (int i = 0; i < lat; i++) {
				double th0 = Math.PI * i / lat, th1 = Math.PI * (i + 1) / lat;
				float yc = (float) Math.cos((th0 + th1) / 2);   // -1..1 height of this ring
				float band = (float) Math.exp(-Math.pow((yc - scan) / 0.14, 2));
				float alpha = 0.06f + 0.30f * band;
				// faint latitude lines for a "hex grid" feel
				if (i % 2 == 0) alpha += 0.03f;
				int color = ((int) (alpha * 255) << 24) | rgb;
				for (int j = 0; j < lon; j++) {
					double ph0 = Math.PI * 2 * j / lon, ph1 = Math.PI * 2 * (j + 1) / lon;
					quad(vc, p,
							sx(r, th0, ph0), cy + sy(r, th0), sz(r, th0, ph0),
							sx(r, th0, ph1), cy + sy(r, th0), sz(r, th0, ph1),
							sx(r, th1, ph1), cy + sy(r, th1), sz(r, th1, ph1),
							sx(r, th1, ph0), cy + sy(r, th1), sz(r, th1, ph0),
							0, 0, 1, 1, color, FULL_BRIGHT, 0, 1, 0);
				}
			}
		});
		pose.popPose();
	}

	private static float sx(float r, double th, double ph) {
		return (float) (r * Math.sin(th) * Math.cos(ph));
	}

	private static float sy(float r, double th) {
		return (float) (-r * Math.cos(th));
	}

	private static float sz(float r, double th, double ph) {
		return (float) (r * Math.sin(th) * Math.sin(ph));
	}

	// ------------------------------------------------------------------------------------------

	/** One textured quad. Corners go top-left, top-right, bottom-right, bottom-left. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz,
							 float u0, float v0, float u1, float v1, int argb, int light,
							 float nx, float ny, float nz) {
		vertex(vc, p, ax, ay, az, u0, v0, argb, light, nx, ny, nz);
		vertex(vc, p, bx, by, bz, u1, v0, argb, light, nx, ny, nz);
		vertex(vc, p, cx, cy, cz, u1, v1, argb, light, nx, ny, nz);
		vertex(vc, p, dx, dy, dz, u0, v1, argb, light, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
							   int argb, int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z)
				.setColor(argb)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(p, nx, ny, nz);
	}
}
