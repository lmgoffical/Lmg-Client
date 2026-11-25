package dev.lmg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Tiny helpers for drawing textured quads and boxes with the entity vertex format. */
public final class Quads {
	public static final int FULL_BRIGHT = 0xF000F0;

	private Quads() {}

	/** One quad. Corners go top-left, top-right, bottom-right, bottom-left (any winding works with no-cull types). */
	public static void quad(VertexConsumer vc, PoseStack.Pose p,
							float ax, float ay, float az, float bx, float by, float bz,
							float cx, float cy, float cz, float dx, float dy, float dz,
							float u0, float v0, float u1, float v1, int argb, int light,
							float nx, float ny, float nz) {
		vertex(vc, p, ax, ay, az, u0, v0, argb, light, nx, ny, nz);
		vertex(vc, p, bx, by, bz, u1, v0, argb, light, nx, ny, nz);
		vertex(vc, p, cx, cy, cz, u1, v1, argb, light, nx, ny, nz);
		vertex(vc, p, dx, dy, dz, u0, v1, argb, light, nx, ny, nz);
	}

	/** A quad whose top edge and bottom edge have different colors (for soft gradients). */
	public static void gradientQuad(VertexConsumer vc, PoseStack.Pose p,
									float ax, float ay, float az, float bx, float by, float bz,
									float cx, float cy, float cz, float dx, float dy, float dz,
									int topArgb, int bottomArgb, int light) {
		vertex(vc, p, ax, ay, az, 0, 0, topArgb, light, 0, 1, 0);
		vertex(vc, p, bx, by, bz, 1, 0, topArgb, light, 0, 1, 0);
		vertex(vc, p, cx, cy, cz, 1, 1, bottomArgb, light, 0, 1, 0);
		vertex(vc, p, dx, dy, dz, 0, 1, bottomArgb, light, 0, 1, 0);
	}

	/** All six faces of an axis-aligned box. */
	public static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0,
						   float x1, float y1, float z1, int argb, int light) {
		quad(vc, p, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, 1, 1, argb, light, 0, 0, -1);
		quad(vc, p, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1, 1, argb, light, 0, 0, 1);
		quad(vc, p, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, 0, 0, 1, 1, argb, light, -1, 0, 0);
		quad(vc, p, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 0, 0, 1, 1, argb, light, 1, 0, 0);
		quad(vc, p, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 0, 1, 1, argb, light, 0, 1, 0);
		quad(vc, p, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, 0, 1, 1, argb, light, 0, -1, 0);
	}

	/** The 12 edges of a box, each drawn as a thin bar of the given thickness. */
	public static void boxEdges(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0,
								float x1, float y1, float z1, float t, int argb, int light) {
		float h = t / 2;
		for (float y : new float[]{y0, y1}) {
			for (float z : new float[]{z0, z1}) box(vc, p, x0 - h, y - h, z - h, x1 + h, y + h, z + h, argb, light);
			for (float x : new float[]{x0, x1}) box(vc, p, x - h, y - h, z0 - h, x + h, y + h, z1 + h, argb, light);
		}
		for (float x : new float[]{x0, x1}) {
			for (float z : new float[]{z0, z1}) box(vc, p, x - h, y0 - h, z - h, x + h, y1 + h, z + h, argb, light);
		}
	}

	public static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
							  int argb, int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z)
				.setColor(argb)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(p, nx, ny, nz);
	}
}
