package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws Minecraft's own buttons, sliders and lists in the selected Lmg menu style
 * (only while "Themed vanilla menus" is on). The text and behaviour stay vanilla.
 */
public final class VanillaSkin {
	private VanillaSkin() {}

	public static boolean on() {
		return LmgConfig.get().themedMenus;
	}

	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean active, float alpha) {
		int acc = MenuSkin.accent();
		switch (MenuSkin.style()) {
			case SKEET -> {
				g.fill(x, y, x + w, y + h, Draw.withAlpha(0x000000, alpha));
				int top = !active ? 0x1C1C1C : hover ? 0x363636 : 0x2A2A2A;
				int bottom = !active ? 0x161616 : hover ? 0x2C2C2C : 0x222222;
				g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, Draw.withAlpha(top, alpha), Draw.withAlpha(bottom, alpha));
				if (hover && active) g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Draw.withAlpha(acc, alpha));
			}
			case NEVERLOSE -> {
				int border = hover && active ? acc : (MenuSkin.NL_FIELD_BORDER & 0xFFFFFF);
				Draw.roundRect(g, x, y, x + w, y + h, 4, Draw.withAlpha(border, alpha * (active ? 1f : 0.5f)));
				Draw.roundRect(g, x + 1, y + 1, x + w - 1, y + h - 1, 3,
						Draw.withAlpha(hover && active ? 0x0A2A52 : (MenuSkin.NL_FIELD & 0xFFFFFF), alpha * (active ? 1f : 0.6f)));
			}
			default -> {
				// website colours: navy card, thin line, a soft accent edge on hover
				int border = hover && active ? acc : 0x1C2943;
				Draw.roundRect(g, x, y, x + w, y + h, 4, Draw.withAlpha(border, alpha * (active ? 1f : 0.6f)));
				Draw.roundRect(g, x + 1, y + 1, x + w - 1, y + h - 1, 3,
						Draw.withAlpha(hover && active ? 0x16213A : 0x0F1828, alpha * (active ? 0.96f : 0.7f)));
			}
		}
	}

	/** Slider track with the filled part up to {@code value} (0..1) and a small handle. */
	public static void slider(GuiGraphicsExtractor g, int x, int y, int w, int h, double value, boolean hover, boolean active, float alpha) {
		button(g, x, y, w, h, hover, active, alpha);
		int acc = MenuSkin.accent();
		int fillTo = x + 1 + (int) Math.round((w - 2) * Math.max(0, Math.min(1, value)));
		if (MenuSkin.style() == LmgConfig.MenuStyle.SKEET) {
			g.fill(x + 1, y + 1, fillTo, y + h - 1, Draw.withAlpha(acc, 0.55f * alpha));
		} else {
			Draw.roundRect(g, x + 1, y + 1, Math.max(x + 4, fillTo), y + h - 1, 3, Draw.withAlpha(acc, (hover ? 0.42f : 0.3f) * alpha));
		}
		int hx = Math.max(x + 2, Math.min(x + w - 5, fillTo - 2));
		Draw.roundRect(g, hx, y + 2, hx + 3, y + h - 2, 1, Draw.withAlpha(0xFFFFFF, (active ? 0.9f : 0.4f) * alpha));
	}

	public static void listBackground(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		int bg = switch (MenuSkin.style()) {
			case SKEET -> 0xCC141414;
			case NEVERLOSE -> 0xCC030D1A;
			default -> 0xB30C1322;
		};
		g.fill(x0, y0, x1, y1, bg);
	}

	public static void listSeparators(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		int line = switch (MenuSkin.style()) {
			case SKEET -> 0xFF2B2B2B;
			case NEVERLOSE -> 0xFF173253;
			default -> 0xFF1C2943;
		};
		g.fill(x0, y0 - 1, x1, y0, line);
		g.fill(x0, y1, x1, y1 + 1, line);
	}
}
