package dev.lmg.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Imports Blockbench models ("Java Block/Item" export) from .minecraft/config/lmgclient-cosmetics.
 *
 * Put model.json and a PNG with the same name side by side. Build it around the player's head:
 * in Blockbench the head is the box from (4, 0, 4) to (12, 8, 12) and the face looks north.
 * Name the file "body_something.json" to attach it to the body instead (the body is the box from
 * (4, 0, 6) to (12, 12, 10), with the neck at the top). Add "_glow" to the name to make it glow.
 */
public final class CustomModels {
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("lmgclient-cosmetics");
	private static final List<Identifier> TEXTURES = new ArrayList<>();
	public static String lastMessage = "";

	private CustomModels() {}

	public static void reload() {
		CosmeticRegistry.clearCustom();
		for (Identifier t : TEXTURES) Minecraft.getInstance().getTextureManager().release(t);
		TEXTURES.clear();
		int ok = 0, bad = 0;
		try {
			if (!Files.isDirectory(DIR)) {
				Files.createDirectories(DIR);
				copyExample("devil_horns.json");
				copyExample("devil_horns.png");
			}
			List<Path> files;
			try (Stream<Path> s = Files.list(DIR)) {
				files = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted().toList();
			}
			for (Path f : files) {
				try {
					CosmeticRegistry.putCustom(load(f));
					ok++;
				} catch (Exception e) {
					bad++;
					LmgClient.LOGGER.warn("Could not import {}: {}", f.getFileName(), e.getMessage());
				}
			}
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not read custom cosmetics", e);
		}
		lastMessage = ok + " model" + (ok == 1 ? "" : "s") + " loaded" + (bad > 0 ? ", " + bad + " failed (see the log)" : "");
	}

	/** The first time the folder is made, drop in an example model to show how it works. */
	private static void copyExample(String name) {
		try (InputStream in = CustomModels.class.getResourceAsStream("/assets/lmgclient/examples/" + name)) {
			if (in != null) Files.copy(in, DIR.resolve(name));
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not copy example {}", name, e);
		}
	}

	private static CosmeticModel load(Path file) throws Exception {
		String base = file.getFileName().toString().replaceFirst("(?i)\\.json$", "");
		String lower = base.toLowerCase(Locale.ROOT);
		JsonObject root;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			root = JsonParser.parseReader(r).getAsJsonObject();
		}
		JsonArray elements = root.getAsJsonArray("elements");
		if (elements == null || elements.isEmpty()) throw new IllegalArgumentException("no elements - export as Java Block/Item");

		CosmeticModel m = new CosmeticModel();
		m.id = "custom:" + lower.replaceAll("[^a-z0-9_]", "_");
		m.name = prettify(base);
		m.description = "Imported from " + file.getFileName();
		m.anchor = lower.startsWith("body_") ? "body" : "head";
		m.tintable = false;

		Path png = file.resolveSibling(base + ".png");
		if (!Files.isRegularFile(png)) png = firstPng(file.getParent(), root);
		if (png != null) {
			try (InputStream in = Files.newInputStream(png)) {
				Identifier id = LmgClient.id("custom_cosmetic/" + m.id.substring(7) + "_" + TEXTURES.size());
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Lmg " + base, NativeImage.read(in)));
				TEXTURES.add(id);
				m.customTexture = id;
			}
		}

		CosmeticModel.Part part = new CosmeticModel.Part();
		part.name = "model";
		part.render = lower.contains("_glow") ? "emissive" : "cutout";
		List<double[]> quads = new ArrayList<>();
		boolean body = m.anchor.equals("body");
		for (JsonElement el : elements) addElement(el.getAsJsonObject(), body, quads);
		part.quads = quads.toArray(new double[0][]);
		m.parts.add(part);
		return m;
	}

	private static Path firstPng(Path dir, JsonObject root) {
		JsonObject tex = root.getAsJsonObject("textures");
		if (tex == null) return null;
		for (var e : tex.entrySet()) {
			if (!e.getValue().isJsonPrimitive()) continue;
			String v = e.getValue().getAsString();
			String name = v.substring(v.lastIndexOf('/') + 1).replace("#", "");
			Path p = dir.resolve(name.endsWith(".png") ? name : name + ".png");
			if (Files.isRegularFile(p)) return p;
		}
		return null;
	}

	private static String prettify(String s) {
		s = s.replaceFirst("(?i)^body_", "").replaceAll("(?i)_glow", "").replace('_', ' ').replace('-', ' ').trim();
		return s.isEmpty() ? "Custom" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Blockbench coordinates (y up, 0..16) to cosmetic coordinates (y down, centred on the head or body). */
	private static Vector3f map(Vector3f p, boolean body) {
		return new Vector3f(p.x - 8f, body ? 12f - p.y : -p.y, p.z - 8f);
	}

	private static void addElement(JsonObject el, boolean body, List<double[]> out) {
		float[] from = floats(el.getAsJsonArray("from")), to = floats(el.getAsJsonArray("to"));
		float x0 = from[0], y0 = from[1], z0 = from[2], x1 = to[0], y1 = to[1], z1 = to[2];
		Matrix3f rot = null;
		Vector3f origin = new Vector3f(8, 8, 8);
		JsonObject r = el.getAsJsonObject("rotation");
		if (r != null && r.has("angle") && r.get("angle").getAsFloat() != 0) {
			float ang = (float) Math.toRadians(r.get("angle").getAsFloat());
			String axis = r.has("axis") ? r.get("axis").getAsString() : "y";
			rot = new Matrix3f();
			switch (axis) {
				case "x" -> rot.rotateX(ang);
				case "z" -> rot.rotateZ(ang);
				default -> rot.rotateY(ang);
			}
			if (r.has("origin")) {
				float[] o = floats(r.getAsJsonArray("origin"));
				origin.set(o[0], o[1], o[2]);
			}
		}
		JsonObject faces = el.getAsJsonObject("faces");
		if (faces == null) return;
		for (var e : faces.entrySet()) {
			JsonObject f = e.getValue().getAsJsonObject();
			Vector3f[] c = switch (e.getKey()) {   // top-left, top-right, bottom-right, bottom-left as seen from outside
				case "north" -> corners(x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
				case "south" -> corners(x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
				case "east" -> corners(x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
				case "west" -> corners(x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
				case "up" -> corners(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
				case "down" -> corners(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
				default -> null;
			};
			if (c == null) continue;
			float[] uv = f.has("uv") ? floats(f.getAsJsonArray("uv")) : new float[]{0, 0, 16, 16};
			float[][] uvs = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
			int turns = f.has("rotation") ? Math.floorMod(f.get("rotation").getAsInt() / 90, 4) : 0;
			double[] q = new double[23];
			for (int k = 0; k < 4; k++) {
				Vector3f p = c[k];
				if (rot != null) p = rot.transform(new Vector3f(p).sub(origin)).add(origin);
				Vector3f m = map(p, body);
				float[] t = uvs[(k + 4 - turns) % 4];
				q[k * 5] = m.x;
				q[k * 5 + 1] = m.y;
				q[k * 5 + 2] = m.z;
				q[k * 5 + 3] = t[0] / 16f;
				q[k * 5 + 4] = t[1] / 16f;
			}
			q[20] = 0xFFFFFFFFL;
			q[21] = 0xFFFFFFFFL;
			q[22] = 0;
			out.add(q);
		}
	}

	private static Vector3f[] corners(float... v) {
		Vector3f[] c = new Vector3f[4];
		for (int i = 0; i < 4; i++) c[i] = new Vector3f(v[i * 3], v[i * 3 + 1], v[i * 3 + 2]);
		return c;
	}

	private static float[] floats(JsonArray a) {
		float[] f = new float[a.size()];
		for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
		return f;
	}
}
