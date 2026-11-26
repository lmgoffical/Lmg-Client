package dev.lmg.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.map.MapData;
import dev.lmg.client.map.Waypoints;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The minimap in the corner: round or square, north-up or turning with you, waypoints on the
 * edge when they're off the map, your coordinates and biome underneath.
 * The picture is drawn on the CPU into a small texture so it can be cut into a clean circle.
 */
public class MinimapHud implements HudElement {
	private static final int TEX = 128;
	private static final Identifier TEX_ID = LmgClient.id("minimap");
	private static final Identifier ARROW = LmgClient.id("textures/gui/map_arrow.png");
	private static DynamicTexture texture;
	private static long lastBuild;
	private static long lastState;
	private static int lastBottom = -1;

	/** Where the minimap ends when it sits in its default top-right spot (so other things can go below it). */
	public static int defaultBottom() {
		LmgConfig c = LmgConfig.get();
		if (!c.minimap || c.minimapX >= 0) return 0;
		return Math.max(0, lastBottom);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LmgConfig c = LmgConfig.get();
		if (!c.minimap || mc.player == null || mc.level == null || mc.gui.hud.isHidden() || !MapData.ready()) {
			lastBottom = -1;
			return;
		}
		Font font = mc.font;
		int size = LmgConfig.MINIMAP_SIZES[c.minimapSize];
		int infoH = c.minimapCoords ? 22 : 0;
		int totalH = size + infoH;
		int[] at = HudLayout.place(HudLayout.Element.MINIMAP, g.guiWidth() - size - 6, 6, size, totalH, g.guiWidth(), g.guiHeight());
		HudLayout.report(HudLayout.Element.MINIMAP, at[0], at[1], size, totalH);
		int x = at[0], y = at[1];
		lastBottom = y + totalH;

		float partial = delta.getGameTimeDeltaPartialTick(false);
		double px = Mth.lerp(partial, mc.player.xo, mc.player.getX());
		double pz = Mth.lerp(partial, mc.player.zo, mc.player.getZ());
		float yaw = mc.player.getViewYRot(partial);
		float zoom = LmgConfig.MINIMAP_ZOOMS[c.minimapZoom];
		boolean rotate = c.minimapRotate, round = c.minimapRound;

		// Redraw the picture only when something changed (you moved or turned, or a setting),
		// at most as often as the refresh setting allows, and once a second for new map data.
		long now = System.currentTimeMillis();
		long state = Double.doubleToLongBits(Math.floor(px * 2)) * 31 + Double.doubleToLongBits(Math.floor(pz * 2))
				+ (rotate ? Math.round(yaw / 2f) * 977L : 0) + (long) (zoom * 101) + (round ? 7 : 0);
		long since = now - lastBuild;
		boolean changed = state != lastState;
		if (texture == null || (changed && since >= LmgConfig.MINIMAP_REFRESH_MS[c.minimapRefresh]) || since > 1000) {
			build(px, pz, yaw, zoom, rotate, round);
			lastBuild = now;
			lastState = state;
		}

		// frame
		int acc = Theme.accent() & 0xFFFFFF;
		if (round) {
			ring(g, x + size / 2f, y + size / 2f, size / 2f + 2, 0xCC0B0F18);
			ring(g, x + size / 2f, y + size / 2f, size / 2f + 1, Draw.withAlpha(0xFFFFFF, 0.18f));
		} else {
			Draw.roundRect(g, x - 2, y - 2, x + size + 2, y + size + 2, 4, 0xCC0B0F18);
			Draw.roundRect(g, x - 1, y - 1, x + size + 1, y + size + 1, 3, Draw.withAlpha(0xFFFFFF, 0.18f));
		}
		g.blit(RenderPipelines.GUI_TEXTURED, TEX_ID, x, y, 0f, 0f, size, size, TEX, TEX, TEX, TEX, 0xFFFFFFFF);

		// waypoints (clamped to the edge when they're further away)
		float half = size / 2f, scale = size / (float) TEX * zoom;
		if (c.minimapWaypoints) {
			for (Waypoints.Waypoint w : Waypoints.all()) {
				double dx = (w.x() + 0.5 - px) * scale, dz = (w.z() + 0.5 - pz) * scale;
				if (rotate) {
					double r = Math.toRadians(-yaw + 180);
					double rx = dx * Math.cos(r) - dz * Math.sin(r), rz = dx * Math.sin(r) + dz * Math.cos(r);
					dx = rx;
					dz = rz;
				}
				double limit = half - 4;
				double len = round ? Math.sqrt(dx * dx + dz * dz) : Math.max(Math.abs(dx), Math.abs(dz));
				boolean edge = len > limit;
				if (edge) {
					dx *= limit / len;
					dz *= limit / len;
				}
				int wx = Math.round(x + half + (float) dx), wz = Math.round(y + half + (float) dz);
				g.fill(wx - 2, wz - 2, wx + 3, wz + 3, 0xFF0B0F18);
				g.fill(wx - 1, wz - 1, wx + 2, wz + 2, 0xFF000000 | w.color());
			}
		}

		// you
		float arrowRot = rotate ? 0f : (float) Math.toRadians(yaw + 180);
		Draw.texture(g, ARROW, 32, x + half, y + half, 9f, arrowRot, 0xFFFFFFFF);

		// north marker on the rim
		double ang = rotate ? Math.toRadians(180 - yaw) : 0;   // where north is, measured clockwise from "up"
		float nx = x + half + (float) (Math.sin(ang) * (half - 1));
		float ny = y + half - (float) (Math.cos(ang) * (half - 1));
		Draw.roundRect(g, Math.round(nx) - 4, Math.round(ny) - 4, Math.round(nx) + 5, Math.round(ny) + 5, 4, 0xEE0B0F18);
		g.centeredText(font, "N", Math.round(nx) + 1, Math.round(ny) - 3, 0xFF000000 | acc);

		if (c.minimapCoords) {
			var bp = mc.player.blockPosition();
			String coords = bp.getX() + ", " + bp.getY() + ", " + bp.getZ();
			String biome = mc.level.getBiome(bp).unwrapKey().map(k -> pretty(k.identifier().getPath())).orElse("");
			int cx = x + size / 2;
			g.centeredText(font, coords, cx, y + size + 4, 0xFFE8EDF5);
			g.centeredText(font, biome, cx, y + size + 14, 0xFF7C8BA3);
		}
	}

	private static String pretty(String path) {
		String[] words = path.split("_");
		StringBuilder b = new StringBuilder();
		for (String w : words) {
			if (w.isEmpty()) continue;
			if (b.length() > 0) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
		}
		return b.toString();
	}

	/** A filled circle (row by row). */
	private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, int argb) {
		int top = (int) Math.floor(cy - r), bottom = (int) Math.ceil(cy + r);
		for (int yy = top; yy < bottom; yy++) {
			float dy = yy + 0.5f - cy;
			float hw = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
			if (hw <= 0) continue;
			g.fill(Math.round(cx - hw), yy, Math.round(cx + hw), yy + 1, argb);
		}
	}

	/** Draws the map around (px, pz) into the minimap texture. */
	private static void build(double px, double pz, float yaw, float zoom, boolean rotate, boolean round) {
		if (texture == null) {
			texture = new DynamicTexture(() -> "Lmg minimap", TEX, TEX, true);
			Minecraft.getInstance().getTextureManager().register(TEX_ID, texture);
		}
		NativeImage img = texture.getPixels();
		double r = Math.toRadians(180 + yaw);
		double cos = Math.cos(r), sin = Math.sin(r);
		float half = TEX / 2f;
		int bg = 0xFF141B29;
		for (int ty = 0; ty < TEX; ty++) {
			for (int tx = 0; tx < TEX; tx++) {
				double ox = tx + 0.5 - half, oz = ty + 0.5 - half;
				double dist = Math.sqrt(ox * ox + oz * oz);
				if (round && dist > half) {
					img.setPixel(tx, ty, 0);
					continue;
				}
				double wx = ox / zoom, wz = oz / zoom;
				if (rotate) {
					double rx = wx * cos - wz * sin, rz = wx * sin + wz * cos;
					wx = rx;
					wz = rz;
				}
				int color = MapData.colorAt((int) Math.floor(px + wx), (int) Math.floor(pz + wz));
				if (color == 0) color = bg;
				if (round && dist > half - 1) color = (color & 0xFFFFFF) | ((int) (255 * (half - dist)) << 24);
				img.setPixel(tx, ty, color);
			}
		}
		texture.upload();
	}
}
