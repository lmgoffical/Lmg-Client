package dev.lmg.legacy.ui;

import dev.lmg.legacy.Colors;
import dev.lmg.legacy.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Drawing helpers and the Lmg palette (the same dark navy look as the 26.3 client). */
public final class Ui {
	public static final int PANEL = 0xF00D1424, CARD = 0xFF111A2E, CARD_HOVER = 0xFF16213A, BORDER = 0xFF1C2943;
	public static final int TEXT = 0xFFE8EDF5, DIM = 0xFF7C8BA3, GOLD = 0xFFF2C14E;

	private Ui() {}

	public static int accent() {
		return 0xFF000000 | Colors.rgb(Config.get().accent);
	}

	public static FontRenderer font() {
		return Minecraft.getMinecraft().fontRendererObj;
	}

	/** A rectangle with its corners cut by `r` pixels (looks rounded at Minecraft's pixel size). */
	public static void round(int x0, int y0, int x1, int y1, int r, int color) {
		if (x1 - x0 < 2 * r || y1 - y0 < 2 * r) r = 0;
		Gui.drawRect(x0 + r, y0, x1 - r, y1, color);
		if (r > 0) {
			Gui.drawRect(x0, y0 + r, x0 + r, y1 - r, color);
			Gui.drawRect(x1 - r, y0 + r, x1, y1 - r, color);
			if (r > 1) {
				Gui.drawRect(x0 + 1, y0 + 1, x0 + r, y0 + r, color);
				Gui.drawRect(x1 - r, y0 + 1, x1 - 1, y0 + r, color);
				Gui.drawRect(x0 + 1, y1 - r, x0 + r, y1 - 1, color);
				Gui.drawRect(x1 - r, y1 - r, x1 - 1, y1 - 1, color);
			}
		}
	}

	/** A card: 1px border, filled inside. */
	public static void card(int x0, int y0, int x1, int y1, boolean hover, boolean active) {
		round(x0, y0, x1, y1, 3, active || hover ? (active ? accent() : 0xFF2A3A5C) : BORDER);
		round(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 2, hover ? CARD_HOVER : CARD);
	}

	public static void text(String s, int x, int y, int color) {
		font().drawString(s, x, y, color);
	}

	public static void shadow(String s, float x, float y, int color) {
		font().drawStringWithShadow(s, x, y, color);
	}

	public static void centered(String s, int cx, int y, int color) {
		font().drawString(s, cx - font().getStringWidth(s) / 2, y, color);
	}

	public static int width(String s) {
		return font().getStringWidth(s);
	}

	/** Cuts text to fit, with ".." on the end. */
	public static String fit(String s, int w) {
		if (width(s) <= w) return s;
		return font().trimStringToWidth(s, Math.max(0, w - width(".."))) + "..";
	}

	public static boolean inside(int mx, int my, int x0, int y0, int x1, int y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	/** Untextured shapes drawn with lines/triangles (crosshair circles and stars). */
	public static void beginShapes() {
		GlStateManager.enableBlend();
		GlStateManager.disableTexture2D();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
	}

	public static void endShapes() {
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	/** A ring (outer radius, thickness) made of a triangle strip. */
	public static void ring(float cx, float cy, float radius, float thickness, int argb) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		float a = (argb >>> 24) / 255f, r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
		w.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
		int segs = 32;
		for (int i = 0; i <= segs; i++) {
			double ang = Math.PI * 2 * i / segs;
			double c = Math.cos(ang), s = Math.sin(ang);
			w.pos(cx + c * radius, cy + s * radius, 0).color(r, g, b, a).endVertex();
			w.pos(cx + c * (radius - thickness), cy + s * (radius - thickness), 0).color(r, g, b, a).endVertex();
		}
		t.draw();
	}

	/** A thick line from (x0,y0) to (x1,y1). */
	public static void line(float x0, float y0, float x1, float y1, float width, int argb) {
		float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.001f) return;
		float nx = -dy / len * width / 2, ny = dx / len * width / 2;
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		float a = (argb >>> 24) / 255f, r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		w.pos(x0 + nx, y0 + ny, 0).color(r, g, b, a).endVertex();
		w.pos(x1 + nx, y1 + ny, 0).color(r, g, b, a).endVertex();
		w.pos(x1 - nx, y1 - ny, 0).color(r, g, b, a).endVertex();
		w.pos(x0 - nx, y0 - ny, 0).color(r, g, b, a).endVertex();
		t.draw();
	}
}
