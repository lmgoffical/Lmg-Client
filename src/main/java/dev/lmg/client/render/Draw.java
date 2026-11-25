package dev.lmg.client.render;

import dev.lmg.client.LmgClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** Small drawing and animation helpers shared by every Lmg Client screen. */
public final class Draw {
	public static final Identifier LOGO = LmgClient.id("textures/gui/logo.png");
	public static final Identifier SPARKLE = LmgClient.id("textures/gui/sparkle.png");
	public static final int LOGO_TEX = 256;
	public static final int SPARKLE_TEX = 32;

	private Draw() {}

	/** Draws a square texture centered on (cx, cy), scaled to {@code size} and rotated by {@code radians}. */
	public static void texture(GuiGraphicsExtractor g, Identifier tex, int texSize, float cx, float cy, float size, float radians, int argb) {
		if (alpha(argb) == 0 || size <= 0) return;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		if (radians != 0) pose.rotate(radians);
		float s = size / texSize;
		pose.scale(s, s);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, -texSize / 2, -texSize / 2, 0f, 0f, texSize, texSize, texSize, texSize, argb);
		pose.popMatrix();
	}

	public static void logo(GuiGraphicsExtractor g, float cx, float cy, float size, float radians, float alpha) {
		texture(g, LOGO, LOGO_TEX, cx, cy, size, radians, white(alpha));
	}

	/** Centered text drawn at a fractional scale (e.g. 2.0 for a big title). */
	public static void scaledText(GuiGraphicsExtractor g, Font font, String text, float cx, float y, float scale, int argb) {
		if (alpha(argb) < 5) return;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, y);
		pose.scale(scale, scale);
		g.centeredText(font, text, 0, 0, argb);
		pose.popMatrix();
	}

	/** Filled rectangle with rounded corners. Rows never overlap, so translucent colors stay even. */
	public static void roundRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int argb) {
		if (alpha(argb) == 0 || x2 <= x1 || y2 <= y1) return;
		int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
		g.fill(x1, y1 + r, x2, y2 - r, argb);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, argb);
			g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, argb);
		}
	}

	// ---- colors ----

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	public static int white(float alpha) {
		return withAlpha(0xFFFFFF, alpha);
	}

	/** Replaces the alpha of an RGB color with {@code alpha} (0..1). */
	public static int withAlpha(int rgb, float alpha) {
		int a = Math.round(clamp01(alpha) * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Multiplies the existing alpha of an ARGB color by {@code mul}. */
	public static int fade(int argb, float mul) {
		int a = Math.round(alpha(argb) * clamp01(mul));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int lerpRgb(int from, int to, float t) {
		t = clamp01(t);
		int r = Math.round(lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t));
		int gr = Math.round(lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t));
		int b = Math.round(lerp(from & 0xFF, to & 0xFF, t));
		return (r << 16) | (gr << 8) | b;
	}

	// ---- easing ----

	public static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	public static float easeOutCubic(float t) {
		t = clamp01(t);
		float u = 1 - t;
		return 1 - u * u * u;
	}

	public static float easeInCubic(float t) {
		t = clamp01(t);
		return t * t * t;
	}

	public static float easeInOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	/** Framerate-independent smoothing: moves {@code current} toward {@code target}. */
	public static float approach(float current, float target, float speed, float dtSeconds) {
		return current + (target - current) * clamp01(dtSeconds * speed);
	}
}
