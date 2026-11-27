package dev.lmg.legacy.feature;

import dev.lmg.legacy.Colors;
import dev.lmg.legacy.Config;
import dev.lmg.legacy.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Your own crosshair: shape, colour, size, gap, thickness, outline, centre dot and spin. */
public final class Crosshair {
	public static final String[] STYLES = {"Minecraft's", "Plus", "Dot", "Circle", "Circle + dot", "T shape", "Lmg star"};
	private static final ResourceLocation STAR = new ResourceLocation("lmgclient", "textures/gui/crosshair_logo.png");

	private Crosshair() {}

	/** True if Lmg draws the crosshair (false = Minecraft's own). */
	public static boolean custom() {
		return Config.get().crosshairStyle != 0;
	}

	/** Draws it centred on (cx, cy) in GUI pixels. */
	public static void draw(float cx, float cy) {
		Config c = Config.get();
		int color = Colors.argb(c.crosshairColor, 1f);
		int outline = 0xC0000000;
		float size = c.crosshairSize, gap = c.crosshairGap, th = c.crosshairThickness;
		GlStateManager.pushMatrix();
		GlStateManager.translate(cx, cy, 0);
		if (c.crosshairSpin != 0) GlStateManager.rotate((System.currentTimeMillis() % 3600000L) / 1000f * c.crosshairSpin * 30f, 0, 0, 1);
		switch (c.crosshairStyle) {
			case 1: plus(size, gap, th, color, outline, c.crosshairOutline, false); break;
			case 2: dot(Math.max(1, th) + 1, color, outline, c.crosshairOutline); break;
			case 3: circle(size + gap, th, color, outline, c.crosshairOutline); break;
			case 4: circle(size + gap, th, color, outline, c.crosshairOutline); dot(th, color, outline, c.crosshairOutline); break;
			case 5: plus(size, gap, th, color, outline, c.crosshairOutline, true); break;
			case 6: star(size, color); break;
			default: break;
		}
		if (c.crosshairDot && c.crosshairStyle != 2 && c.crosshairStyle != 4 && c.crosshairStyle != 6) dot(th, color, outline, c.crosshairOutline);
		GlStateManager.popMatrix();
		GlStateManager.color(1f, 1f, 1f, 1f);
		Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.icons);
	}

	private static void bar(float x0, float y0, float x1, float y1, int color, int outline, boolean edge) {
		if (edge) rect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, outline);
		rect(x0, y0, x1, y1, color);
	}

	private static void rect(float x0, float y0, float x1, float y1, int color) {
		Ui.beginShapes();
		Ui.line(x0, (y0 + y1) / 2, x1, (y0 + y1) / 2, y1 - y0, color);
		Ui.endShapes();
	}

	private static void plus(float size, float gap, float th, int color, int outline, boolean edge, boolean noTop) {
		float h = th / 2;
		if (!noTop) bar(-h, -gap - size, h, -gap, color, outline, edge);
		bar(-h, gap, h, gap + size, color, outline, edge);
		bar(-gap - size, -h, -gap, h, color, outline, edge);
		bar(gap, -h, gap + size, h, color, outline, edge);
	}

	private static void dot(float r, int color, int outline, boolean edge) {
		bar(-r / 2f - 0.5f, -r / 2f - 0.5f, r / 2f + 0.5f, r / 2f + 0.5f, color, outline, edge);
	}

	private static void circle(float radius, float th, int color, int outline, boolean edge) {
		Ui.beginShapes();
		if (edge) Ui.ring(0, 0, radius + 1, th + 2, outline);
		Ui.ring(0, 0, radius, th, color);
		Ui.endShapes();
	}

	private static void star(float size, int color) {
		float s = 6 + size * 1.6f;
		Minecraft.getMinecraft().getTextureManager().bindTexture(STAR);
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f);
		Gui.drawModalRectWithCustomSizedTexture((int) (-s / 2), (int) (-s / 2), 0, 0, (int) s, (int) s, s, s);
		GlStateManager.disableBlend();
	}
}
