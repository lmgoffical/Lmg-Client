package dev.lmg.client.cosmetic;

import dev.lmg.client.LmgClient;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A cosmetic loaded from JSON (built by tools/cosmetics/build.mjs, or imported from Blockbench).
 * Units are model pixels; y points down; the front of the player is -z.
 * Part transform: parent * translate(pivot + bob) * rotY * rotZ * rotX * scale (degrees).
 */
public final class CosmeticModel {
	public String id;
	public String name;
	public String category;
	public String description = "";
	public String anchor = "head";          // head | body | root
	public boolean tintable = true;
	public String defaultColor = "#FFFFFF";
	public boolean hideWithElytra;
	public String capeTexture;              // capes only: texture used by the vanilla cape layer
	public List<Part> parts = new ArrayList<>();

	/** Set for imported models whose textures were registered at runtime. */
	public transient Identifier customTexture;
	public transient boolean custom;

	public static final class Part {
		public String name;
		public int parent = -1;
		public float[] pivot = {0, 0, 0};
		public float[] rot = {0, 0, 0};
		public float scale = 1;
		public float[] crouchRot;
		public String render = "cutout";    // cutout | translucent | emissive
		public String texture;              // file in textures/cosmetic/, or null for plain white
		public List<Anim> anim = new ArrayList<>();
		public double[][] quads = new double[0][];

		// filled in by prepare()
		transient float[][] verts;          // per quad: 4 x (x, y, z, u, v)
		transient int[] top, bottom;
		transient boolean[] tint;
		transient float cx, cy, cz;         // centre, for outward normals
		transient float[] normals;          // per quad: nx, ny, nz (worked out once, pointing away from the centre)
		transient Identifier textureId;
	}

	public static final class Anim {
		public String type;                 // wave | spin | bob | twitch
		public String axis = "y";
		public float amp, speed, phase, movingAmp, movingSpeed, offset, movingOffset, period = 1, duration;
	}

	/** Converts the raw JSON numbers into ready-to-draw arrays. Call once after loading. */
	public CosmeticModel prepare() {
		for (Part p : parts) {
			int n = p.quads.length;
			p.verts = new float[n][20];
			p.top = new int[n];
			p.bottom = new int[n];
			p.tint = new boolean[n];
			double sx = 0, sy = 0, sz = 0;
			int count = 0;
			for (int i = 0; i < n; i++) {
				double[] q = p.quads[i];
				for (int k = 0; k < 20; k++) p.verts[i][k] = (float) q[k];
				p.top[i] = (int) (long) q[20];
				p.bottom[i] = (int) (long) q[21];
				p.tint[i] = q.length > 22 && q[22] != 0;
				for (int v = 0; v < 4; v++) {
					sx += q[v * 5];
					sy += q[v * 5 + 1];
					sz += q[v * 5 + 2];
					count++;
				}
			}
			if (count > 0) {
				p.cx = (float) (sx / count);
				p.cy = (float) (sy / count);
				p.cz = (float) (sz / count);
			}
			p.normals = new float[n * 3];
			for (int i = 0; i < n; i++) CosmeticRenderer.normal(p.verts[i], p.cx, p.cy, p.cz, p.normals, i * 3);
			if (customTexture != null) p.textureId = customTexture;
			else p.textureId = LmgClient.id("textures/cosmetic/" + (p.texture == null ? "white.png" : p.texture));
			if (p.anim == null) p.anim = new ArrayList<>();
		}
		return this;
	}

	public int defaultRgb() {
		try {
			return Integer.parseInt(defaultColor.replace("#", ""), 16) & 0xFFFFFF;
		} catch (Exception e) {
			return 0xFFFFFF;
		}
	}
}
