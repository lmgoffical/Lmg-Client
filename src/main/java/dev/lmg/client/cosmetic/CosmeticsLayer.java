package dev.lmg.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.LmgConfig.CosmeticColor;
import dev.lmg.client.render.Quads;
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
	// Everything below works in MODEL PIXELS (we scale the pose by 1/16 first) so the numbers match
	// tools/cosmetic-preview/preview.js exactly. Check changes there before shipping them.
	// ------------------------------------------------------------------------------------------

	// Wings: two hinged panels per side (shoulder half + tip half that folds), plus a darker
	// underside layer nearer the body for thickness. Swept back and up; flapping bends the tips more.
	private static void wings(PoseStack pose, SubmitNodeCollector nodes, int light, int rgb, AvatarRenderState s, float t) {
		float moving = Math.min(1f, s.walkAnimationSpeed * 1.5f);
		float flap = (float) Math.sin(t * (0.11f + 0.12f * moving));
		float open = s.isCrouching ? 14f : 32f + 8f * moving;
		float size = 20f, hinge = 0.42f, y0 = -0.09f * size, y1 = y0 + size;
		RenderType type = RenderTypes.entityTranslucent(WING);
		for (int side = -1; side <= 1; side += 2) {
			final int sgn = side;
			for (int layer = 0; layer < 2; layer++) {
				boolean under = layer == 0;
				int color = 0xFF000000 | (under ? darken(rgb, 0.6f) : rgb);
				pose.pushPose();
				pose.scale(PX, PX, PX);
				pose.translate(sgn * 1.5f, 1.2f, under ? 2.6f : 3.1f);
				pose.mulPose(new Matrix4f()
						.rotateY((float) Math.toRadians(-sgn * (open + flap * 12f)))
						.rotateZ((float) Math.toRadians(-sgn * (30f + flap * 8f))));
				if (under) pose.scale(0.96f, 0.96f, 0.96f);
				float xi = sgn * size * hinge;
				nodes.submitCustomGeometry(pose, type, (p, vc) ->
						Quads.quad(vc, p, 0, y0, 0, xi, y0, 0, xi, y1, 0, 0, y1, 0, 0, 0, hinge, 1, color, light, 0, 0, 1));
				// outer panel, hinged at the "wrist"
				pose.translate(xi, 0, 0);
				pose.mulPose(new Matrix4f().rotateY((float) Math.toRadians(-sgn * (12f + flap * 16f))));
				float xo = sgn * size * (1 - hinge);
				nodes.submitCustomGeometry(pose, type, (p, vc) ->
						Quads.quad(vc, p, 0, y0, 0, xo, y0, 0, xo, y1, 0, 0, y1, 0, hinge, 0, 1, 1, color, light, 0, 0, 1));
				pose.popPose();
			}
		}
	}

	// Halo: a solid glowing ring with thickness plus a soft glow disc; tilted, bobbing, spinning.
	private static void halo(PoseStack pose, SubmitNodeCollector nodes, int rgb, float t) {
		float bob = (float) Math.sin(t * 0.08f) * 0.5f;
		pose.pushPose();
		pose.scale(PX, PX, PX);
		pose.translate(0, -10.8f + bob, 0);
		pose.mulPose(new Matrix4f().rotateX((float) Math.toRadians(10)).rotateY(t * 0.015f));
		int top = 0xFF000000 | mix(rgb, 0xFFFFFF, 0.45f), mid = 0xFF000000 | rgb, low = 0xFF000000 | darken(rgb, 0.75f);
		nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (p, vc) -> {
			shapeCenter(0, 0, 0);
			float R = 4.6f, tw = 0.55f, th = 0.45f, ro = R + tw, ri = R - tw;
			int segs = 40;
			for (int i = 0; i < segs; i++) {
				double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
				float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
				face(vc, p, c0 * ri, -th, s0 * ri, c1 * ri, -th, s1 * ri, c1 * ro, -th, s1 * ro, c0 * ro, -th, s0 * ro, top, top, FULL_BRIGHT);
				face(vc, p, c0 * ro, -th, s0 * ro, c1 * ro, -th, s1 * ro, c1 * ro, th, s1 * ro, c0 * ro, th, s0 * ro, mid, low, FULL_BRIGHT);
				face(vc, p, c0 * ri, -th, s0 * ri, c1 * ri, -th, s1 * ri, c1 * ri, th, s1 * ri, c0 * ri, th, s0 * ri, mid, low, FULL_BRIGHT);
				face(vc, p, c0 * ri, th, s0 * ri, c1 * ri, th, s1 * ri, c1 * ro, th, s1 * ro, c0 * ro, th, s0 * ro, low, low, FULL_BRIGHT);
			}
		});
		int glow = (Math.round(0.55f * 255) << 24) | rgb;
		nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(HALO), (p, vc) -> {
			float r = 8f;
			Quads.quad(vc, p, -r, 0, -r, r, 0, -r, r, 0, r, -r, 0, r, 0, 0, 1, 1, glow, FULL_BRIGHT, 0, -1, 0);
		});
		pose.popPose();
	}

	// Crown: thick gold band, raised spikes with gold balls, red and blue gems.
	private static void crown(PoseStack pose, SubmitNodeCollector nodes, int light) {
		pose.pushPose();
		pose.scale(PX, PX, PX);
		nodes.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, vc) -> {
			shapeCenter(0, -9.5f, 0);
			float half = 4.55f, yb = -7.6f, yt = -9.9f, th = 0.6f;
			int gold = 0xFFE7B43C, goldDark = 0xFFB98620, tipGold = 0xFFFFE9A0;
			Quads.box(vc, p, -half, yt, -half, half, yb, -half + th, gold, light);
			Quads.box(vc, p, -half, yt, half - th, half, yb, half, gold, light);
			Quads.box(vc, p, -half, yt, -half + th, -half + th, yb, half - th, gold, light);
			Quads.box(vc, p, half - th, yt, -half + th, half, yb, half - th, gold, light);
			Quads.box(vc, p, -half - 0.15f, yb - 0.5f, -half - 0.15f, half + 0.15f, yb, half + 0.15f, goldDark, light);
			int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
			for (int[] sd : sides) {
				int sx = sd[0], sz = sd[1];
				for (int k = -1; k <= 1; k++) {
					float along = k * 2.9f, hgt = k == 0 ? 3.4f : 2.5f, w = 0.95f;
					float cx = sx != 0 ? sx * (half - th / 2) : along;
					float cz = sz != 0 ? sz * (half - th / 2) : along;
					float axx = sx != 0 ? 0 : 1, azz = sx != 0 ? 1 : 0;
					float b1x = cx - axx * w, b1z = cz - azz * w, b2x = cx + axx * w, b2z = cz + azz * w;
					float b3x = b2x + sx * 0.3f, b3z = b2z + sz * 0.3f, b4x = b1x + sx * 0.3f, b4z = b1z + sz * 0.3f;
					float ax = cx + sx * 0.25f, ay = yt - hgt, az = cz + sz * 0.25f;
					face(vc, p, ax, ay, az, ax, ay, az, b2x, yt, b2z, b1x, yt, b1z, gold, gold, light);
					face(vc, p, ax, ay, az, ax, ay, az, b3x, yt, b3z, b2x, yt, b2z, goldDark, goldDark, light);
					face(vc, p, ax, ay, az, ax, ay, az, b4x, yt, b4z, b3x, yt, b3z, gold, gold, light);
					face(vc, p, ax, ay, az, ax, ay, az, b1x, yt, b1z, b4x, yt, b4z, goldDark, goldDark, light);
					float bs = 0.45f;
					Quads.box(vc, p, ax - bs, ay - bs * 2, az - bs, ax + bs, ay, az + bs, tipGold, light);
				}
			}
			float gy = (yb + yt) / 2, gs = 0.6f, out = 0.35f;
			Quads.box(vc, p, -gs, gy - gs, -half - out - gs, gs, gy + gs, -half + gs, 0xFFE0304A, light);
			Quads.box(vc, p, -gs, gy - gs, half - gs, gs, gy + gs, half + out + gs, 0xFFE0304A, light);
			Quads.box(vc, p, -half - out - gs, gy - gs, -gs, -half + gs, gy + gs, gs, 0xFF3A7BEB, light);
			Quads.box(vc, p, half - gs, gy - gs, -gs, half + out + gs, gy + gs, gs, 0xFF3A7BEB, light);
		});
		pose.popPose();
	}

	// Cat ears: 3D wedges with a pink inner ear; they twitch now and then.
	private static void catEars(PoseStack pose, SubmitNodeCollector nodes, int light, int rgb, float t) {
		int inner = 0xFF000000 | mix(rgb, 0xFFB3CF, 0.7f);
		int outer = 0xFF000000 | rgb, side = 0xFF000000 | darken(rgb, 0.8f);
		for (int s = -1; s <= 1; s += 2) {
			float phase = (t + (s > 0 ? 47f : 0f)) % 110f;
			float twitch = phase < 6f ? (float) Math.sin(phase / 6f * Math.PI) * 14f : 0f;
			pose.pushPose();
			pose.scale(PX, PX, PX);
			pose.translate(s * 2.3f, -7.9f, -0.3f);
			pose.mulPose(new Matrix4f()
					.rotateZ((float) Math.toRadians(s * (12f + twitch)))
					.rotateX((float) Math.toRadians(-6f)));
			nodes.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, vc) -> {
				shapeCenter(0, -1.4f, 0);
				float bw = 1.75f, h = 3.7f, d = 0.6f, ad = 0.2f;
				face(vc, p, 0, -h, -ad, 0, -h, -ad, bw, 0, -d, -bw, 0, -d, outer, outer, light);   // front
				face(vc, p, 0, -h, ad, 0, -h, ad, -bw, 0, d, bw, 0, d, outer, outer, light);      // back
				face(vc, p, 0, -h, -ad, 0, -h, ad, bw, 0, d, bw, 0, -d, side, side, light);        // right slope
				face(vc, p, 0, -h, ad, 0, -h, -ad, -bw, 0, -d, -bw, 0, d, side, side, light);      // left slope
				face(vc, p, 0, -h * 0.72f, -ad - 0.12f, 0, -h * 0.72f, -ad - 0.12f,
						1.0f, -0.35f, -d - 0.04f, -1.0f, -0.35f, -d - 0.04f, inner, inner, light);  // inner ear
			});
			pose.popPose();
		}
	}

	// China hat: shallow cone with a lighter tip, faint ribs and a small brim edge.
	private static void chinaHat(PoseStack pose, SubmitNodeCollector nodes, int light, CosmeticColor choice, float t) {
		pose.pushPose();
		pose.scale(PX, PX, PX);
		nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (p, vc) -> {
			shapeCenter(0, -8.0f, 0);
			int segs = 32;
			float radius = 7.5f, baseY = -8.6f, tipY = -12.4f, brim = 0.45f;
			for (int i = 0; i < segs; i++) {
				double a0 = Math.PI * 2 * i / segs + t * 0.01, a1 = Math.PI * 2 * (i + 1) / segs + t * 0.01;
				int rgb = choice == CosmeticColor.RAINBOW
						? java.awt.Color.HSBtoRGB((float) ((i / (double) segs + t / 120.0) % 1.0), 0.55f, 1f) & 0xFFFFFF
						: choice.rgb(t);
				float rib = i % 2 == 0 ? 1f : 0.9f;
				int tipC = (Math.round(0.82f * 255) << 24) | darken(mix(rgb, 0xFFFFFF, 0.35f), rib);
				int baseC = (Math.round(0.82f * 255) << 24) | darken(rgb, rib);
				int brimC = (Math.round(0.9f * 255) << 24) | darken(rgb, 0.7f);
				float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
				float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
				face(vc, p, 0, tipY, 0, 0, tipY, 0, x1, baseY, z1, x0, baseY, z0, tipC, baseC, light);
				face(vc, p, x0, baseY, z0, x1, baseY, z1, x1, baseY + brim, z1, x0, baseY + brim, z0, brimC, brimC, light);
			}
		});
		pose.popPose();
	}

	/**
	 * A quad (or triangle, by repeating the first point) on the plain white texture with its
	 * lighting normal worked out from the corners. {@code top} colours a/b, {@code bottom} colours c/d.
	 */
	private static void face(VertexConsumer vc, PoseStack.Pose p,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz,
							 int top, int bottom, int light) {
		// normal from the two diagonals (works for triangles with a repeated corner too)
		float ux = cx - ax, uy = cy - ay, uz = cz - az, vx = dx - bx, vy = dy - by, vz = dz - bz;
		float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6f) { nx = 0; ny = -1; nz = 0; len = 1; }
		nx /= len; ny /= len; nz /= len;
		// Minecraft doesn't flip normals for faces seen from behind, so make every normal point
		// away from the centre of the shape being drawn (set per cosmetic via shapeCenter()).
		float mx = (ax + bx + cx + dx) / 4 - CENTER[0], my = (ay + by + cy + dy) / 4 - CENTER[1], mz = (az + bz + cz + dz) / 4 - CENTER[2];
		if (nx * mx + ny * my + nz * mz < 0) { nx = -nx; ny = -ny; nz = -nz; }
		Quads.vertex(vc, p, ax, ay, az, 0, 0, top, light, nx, ny, nz);
		Quads.vertex(vc, p, bx, by, bz, 1, 0, top, light, nx, ny, nz);
		Quads.vertex(vc, p, cx, cy, cz, 1, 1, bottom, light, nx, ny, nz);
		Quads.vertex(vc, p, dx, dy, dz, 0, 1, bottom, light, nx, ny, nz);
	}

	private static final float[] CENTER = new float[3];

	/** Centre of the shape about to be drawn with face(), used to orient its normals outward. */
	private static void shapeCenter(float x, float y, float z) {
		CENTER[0] = x;
		CENTER[1] = y;
		CENTER[2] = z;
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int darken(int rgb, float f) {
		return mix(0x000000, rgb, f);
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
