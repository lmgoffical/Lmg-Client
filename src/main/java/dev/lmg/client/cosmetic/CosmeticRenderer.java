package dev.lmg.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * Draws a {@link CosmeticModel}. This is the Java twin of renderCosmetic() in
 * tools/cosmetic-preview/preview.js - keep the maths identical so the preview stays truthful.
 */
public final class CosmeticRenderer {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float PX = 1f / 16f;

	private CosmeticRenderer() {}

	public static void render(PoseStack pose, SubmitNodeCollector nodes, int light, AvatarRenderState state,
							  PlayerModel model, CosmeticModel cosmetic, int tintRgb) {
		float t = state.ageInTicks;
		float moving = Math.min(1f, state.walkAnimationSpeed * 1.5f);
		boolean crouch = state.isCrouching;

		pose.pushPose();
		switch (cosmetic.anchor) {
			case "head" -> model.head.translateAndRotate(pose);
			case "body" -> model.body.translateAndRotate(pose);
			default -> {}
		}
		pose.scale(PX, PX, PX);

		Matrix4f[] mats = new Matrix4f[cosmetic.parts.size()];
		for (int i = 0; i < mats.length; i++) {
			CosmeticModel.Part part = cosmetic.parts.get(i);
			Matrix4f m = part.parent >= 0 && part.parent < i ? new Matrix4f(mats[part.parent]) : new Matrix4f();
			float px = part.pivot[0], py = part.pivot[1], pz = part.pivot[2];
			float rx = part.rot[0], ry = part.rot[1], rz = part.rot[2];
			if (crouch && part.crouchRot != null) {
				rx += part.crouchRot[0];
				ry += part.crouchRot[1];
				rz += part.crouchRot[2];
			}
			for (CosmeticModel.Anim a : part.anim) {
				float v = animValue(a, t, moving);
				if ("bob".equals(a.type)) {
					switch (a.axis) {
						case "x" -> px += v;
						case "z" -> pz += v;
						default -> py += v;
					}
				} else {
					switch (a.axis) {
						case "x" -> rx += v;
						case "z" -> rz += v;
						default -> ry += v;
					}
				}
			}
			m.translate(px, py, pz)
					.rotateY((float) Math.toRadians(ry))
					.rotateZ((float) Math.toRadians(rz))
					.rotateX((float) Math.toRadians(rx));
			if (part.scale != 1f) m.scale(part.scale);
			mats[i] = m;
			if (part.verts.length == 0) continue;

			boolean emissive = "emissive".equals(part.render);
			RenderType type = switch (part.render) {
				case "translucent" -> RenderTypes.entityTranslucent(part.textureId);
				case "emissive" -> RenderTypes.entityTranslucentEmissive(part.textureId);
				default -> RenderTypes.entityCutout(part.textureId);
			};
			int partLight = emissive ? FULL_BRIGHT : light;
			pose.pushPose();
			pose.mulPose(m);
			nodes.submitCustomGeometry(pose, type, (p, vc) -> {
				for (int q = 0; q < part.verts.length; q++) {
					int top = part.tint[q] ? tint(part.top[q], tintRgb) : part.top[q];
					int bottom = part.tint[q] ? tint(part.bottom[q], tintRgb) : part.bottom[q];
					quad(vc, p, part.verts[q], top, bottom, partLight, part.normals, q * 3);
				}
			});
			pose.popPose();
		}
		pose.popPose();
	}

	static float animValue(CosmeticModel.Anim a, float t, float m) {
		return switch (a.type) {
			case "wave" -> (a.offset + a.movingOffset * m) + (a.amp + a.movingAmp * m) * (float) Math.sin(t * (a.speed + a.movingSpeed * m) + a.phase);
			case "spin" -> t * a.speed;
			case "bob" -> a.amp * (float) Math.sin(t * a.speed + a.phase);
			case "twitch" -> {
				float ph = ((t + a.offset) % a.period + a.period) % a.period;
				yield ph < a.duration ? a.amp * (float) Math.sin(ph / a.duration * Math.PI) : 0f;
			}
			default -> 0f;
		};
	}

	private static int tint(int argb, int rgb) {
		int r = ((argb >> 16) & 255) * ((rgb >> 16) & 255) / 255;
		int g = ((argb >> 8) & 255) * ((rgb >> 8) & 255) / 255;
		int b = (argb & 255) * (rgb & 255) / 255;
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** A quad's normal from its corners, flipped to point away from the part's centre (done once, at load). */
	static void normal(float[] v, float cx, float cy, float cz, float[] out, int at) {
		float ax = v[0], ay = v[1], az = v[2], bx = v[5], by = v[6], bz = v[7];
		float qx = v[10], qy = v[11], qz = v[12], dx = v[15], dy = v[16], dz = v[17];
		float ux = qx - ax, uy = qy - ay, uz = qz - az, wx = dx - bx, wy = dy - by, wz = dz - bz;
		float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6f) {
			nx = 0;
			ny = -1;
			nz = 0;
			len = 1;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		float mx = (ax + bx + qx + dx) / 4 - cx, my = (ay + by + qy + dy) / 4 - cy, mz = (az + bz + qz + dz) / 4 - cz;
		if (nx * mx + ny * my + nz * mz < 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		out[at] = nx;
		out[at + 1] = ny;
		out[at + 2] = nz;
	}

	/** One quad, with the normal worked out at load time. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, float[] v, int top, int bottom, int light, float[] normals, int at) {
		float nx = normals[at], ny = normals[at + 1], nz = normals[at + 2];
		vertex(vc, p, v[0], v[1], v[2], v[3], v[4], top, light, nx, ny, nz);
		vertex(vc, p, v[5], v[6], v[7], v[8], v[9], top, light, nx, ny, nz);
		vertex(vc, p, v[10], v[11], v[12], v[13], v[14], bottom, light, nx, ny, nz);
		vertex(vc, p, v[15], v[16], v[17], v[18], v[19], bottom, light, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
							   int argb, int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
