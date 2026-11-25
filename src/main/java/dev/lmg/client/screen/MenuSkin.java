package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.LmgConfig.MenuStyle;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The three looks of the Lmg menu: Lmg (default), Skeet (gamesense-style) and Neverlose-style.
 * Only drawing lives here - every style shows exactly the same settings.
 */
public final class MenuSkin {
	// Skeet palette
	static final int SK_BG = 0xFF141414, SK_BG_LINE = 0xFF171717, SK_SIDEBAR = 0xFF0C0C0C;
	static final int SK_BORDER_DARK = 0xFF000000, SK_BORDER_LIGHT = 0xFF3C3C3C, SK_FRAME = 0xFF282828;
	static final int SK_GROUP_OUT = 0xFF0A0A0A, SK_GROUP_IN = 0xFF2B2B2B;
	static final int SK_TEXT = 0xCBCBCB, SK_TEXT_DIM = 0x5C5C5C;
	static final int SK_ACCENT = 0x95B806;
	// Neverlose palette
	// (colours taken from a Neverlose menu remake used as reference)
	static final int NL_BG = 0xFF030D1A, NL_SIDEBAR = 0xFF03050D, NL_BORDER = 0xFF173253;
	static final int NL_CARD = 0xFF041224, NL_CARD_BORDER = 0xFF173253, NL_FIELD = 0xFF001C38, NL_FIELD_BORDER = 0xFF2B4376;
	static final int NL_TEXT = 0xEFF8F6, NL_TEXT_DIM = 0x4A5761;
	static final int NL_ACCENT = 0x1499FF;
	static final int NL_HOVER = 0x0A1F3A, NL_TAB_ACTIVE = 0x001C38;

	/** A titled group box (Skeet style): x0, y0, x1, y1 and a title. */
	public record Group(int x0, int y0, int x1, int y1, String title) {}

	private static java.util.List<Group> groups = java.util.List.of();

	/** The settings screen tells us where its group boxes are (Skeet style). */
	public static void setGroups(java.util.List<Group> list) {
		groups = list;
	}

	private MenuSkin() {}

	public static MenuStyle style() {
		return LmgConfig.get().menuStyle;
	}

	/** The accent color for the current menu style (RGB). */
	public static int accent() {
		LmgConfig c = LmgConfig.get();
		if (c.menuUseAccent || c.menuStyle == MenuStyle.LMG) return c.accent.rgb;
		return c.menuStyle == MenuStyle.SKEET ? SK_ACCENT : NL_ACCENT;
	}

	/** Extra space above the rows (room for the group box / card title). */
	public static int contentTopPad() {
		return switch (style()) {
			case LMG -> 14;
			case SKEET -> 10;
			case NEVERLOSE -> 16;
		};
	}

	/** Rows are inset this much inside the content box. */
	public static int contentInset() {
		return style() == MenuStyle.LMG ? 0 : 7;
	}

	// ---------------------------------------------------------------------------------------------
	// Panel
	// ---------------------------------------------------------------------------------------------

	public static void panel(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1,
							 int headerH, int sidebarW, int footerH, String tabName, float a) {
		switch (style()) {
			case LMG -> lmgPanel(g, font, x0, y0, x1, y1, headerH, sidebarW, a);
			case SKEET -> skeetPanel(g, font, x0, y0, x1, y1, headerH, sidebarW, footerH, tabName, a);
			case NEVERLOSE -> neverlosePanel(g, font, x0, y0, x1, y1, headerH, sidebarW, footerH, tabName, a);
		}
	}

	private static void lmgPanel(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1,
								 int headerH, int sidebarW, float a) {
		// Same colours as the website: navy panel, thin lines, no glow.
		Draw.roundRect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 7, Draw.withAlpha(0x1C2943, a));
		Draw.roundRect(g, x0, y0, x1, y1, 6, Draw.withAlpha(0x0C1322, 0.97f * a));
		Draw.roundRect(g, x0, y0, x0 + sidebarW - 2, y1, 6, Draw.withAlpha(0x0A101D, 0.97f * a));
		g.fill(x0 + sidebarW - 8, y0, x0 + sidebarW - 2, y1, Draw.withAlpha(0x0A101D, 0.97f * a));
		g.fill(x0 + sidebarW - 2, y0 + 6, x0 + sidebarW - 1, y1 - 6, Draw.withAlpha(0x1C2943, a));
		g.fill(x0 + sidebarW - 1, y0 + headerH, x1 - 8, y0 + headerH + 1, Draw.withAlpha(0x1C2943, a));
		// name on the left with the version in gold, like the site's header
		int ty = y0 + (headerH - font.lineHeight) / 2 + 1;
		int tx = x0 + sidebarW + 4;
		g.text(font, LmgClient.CLIENT_NAME, tx, ty, Draw.withAlpha(0xE8EDF5, a), false);
		g.text(font, "v" + version(), tx + font.width(LmgClient.CLIENT_NAME) + 5, ty, Draw.withAlpha(0xF2C14E, 0.85f * a), false);
	}

	private static String version;

	static String version() {
		if (version == null) {
			version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(LmgClient.MOD_ID)
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
			if (version.endsWith(".0")) version = version.substring(0, version.length() - 2);
		}
		return version;
	}

	private static void skeetPanel(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1,
								   int headerH, int sidebarW, int footerH, String tabName, float a) {
		// layered frame: black, light grey, thick grey, light grey, then the background
		g.fill(x0 - 5, y0 - 5, x1 + 5, y1 + 5, fade(SK_BORDER_DARK, a));
		g.fill(x0 - 4, y0 - 4, x1 + 4, y1 + 4, fade(SK_BORDER_LIGHT, a));
		g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, fade(SK_FRAME, a));
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, fade(SK_BORDER_LIGHT, a));
		g.fill(x0, y0, x1, y1, fade(SK_BG, a));
		for (int y = y0 + 3; y < y1; y += 3) g.fill(x0, y, x1, y + 1, fade(SK_BG_LINE, a)); // faint texture

		// the famous rainbow line
		rainbow(g, x0, y0, x1, a, 1f);
		rainbow(g, x0, y0 + 1, x1, a, 0.5f);

		// left tab column
		g.fill(x0, y0 + 2, x0 + sidebarW - 2, y1, fade(SK_SIDEBAR, a));
		g.fill(x0 + sidebarW - 2, y0 + 2, x0 + sidebarW - 1, y1, fade(SK_BORDER_DARK, a));
		g.fill(x0 + sidebarW - 1, y0 + 2, x0 + sidebarW, y1, fade(SK_GROUP_IN, a));

		// title: "Lmg" + "client" in accent (like game + sense)
		String a1 = "Lmg", a2 = "client";
		int tw = font.width(a1) + font.width(a2);
		int tx = x0 + sidebarW + (x1 - x0 - sidebarW) / 2 - tw / 2;
		int ty = y0 + (headerH - font.lineHeight) / 2 + 2;
		g.text(font, a1, tx, ty, Draw.withAlpha(0xFFFFFF, a), true);
		g.text(font, a2, tx + font.width(a1), ty, Draw.withAlpha(accent(), a), true);
		g.fill(x0 + sidebarW, y0 + headerH, x1, y0 + headerH + 1, fade(SK_BORDER_DARK, a));
		g.fill(x0 + sidebarW, y0 + headerH + 1, x1, y0 + headerH + 2, fade(SK_GROUP_IN, a));

		// titled group boxes (two side by side, like gamesense)
		for (Group gb : groups) {
			g.fill(gb.x0(), gb.y0(), gb.x1(), gb.y1(), fade(SK_GROUP_OUT, a));
			g.fill(gb.x0() + 1, gb.y0() + 1, gb.x1() - 1, gb.y1() - 1, fade(SK_GROUP_IN, a));
			g.fill(gb.x0() + 2, gb.y0() + 2, gb.x1() - 2, gb.y1() - 2, fade(0xFF171717, a));
			int lw = font.width(gb.title());
			g.fill(gb.x0() + 8, gb.y0() - 1, gb.x0() + 12 + lw, gb.y0() + 3, fade(SK_BG, a));
			g.text(font, gb.title(), gb.x0() + 10, gb.y0() - 3, Draw.withAlpha(0xFFFFFF, a), false);
			g.text(font, gb.title(), gb.x0() + 11, gb.y0() - 3, Draw.withAlpha(0xFFFFFF, a), false); // bold
		}
	}

	private static void neverlosePanel(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1,
									   int headerH, int sidebarW, int footerH, String tabName, float a) {
		Draw.roundRect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 8, fade(NL_BORDER, a));
		Draw.roundRect(g, x0, y0, x1, y1, 7, fade(NL_BG, a));
		// sidebar: rounded on the left, square on the right
		Draw.roundRect(g, x0, y0, x0 + sidebarW, y1, 7, fade(NL_SIDEBAR, a));
		g.fill(x0 + sidebarW - 7, y0, x0 + sidebarW, y1, fade(NL_SIDEBAR, a));
		g.fill(x0 + sidebarW, y0 + 6, x0 + sidebarW + 1, y1 - 6, fade(NL_BORDER, a));

		// logo: bold "LMG" in blue + small "CLIENT"
		int lx = x0 + 9, ly = y0 + (headerH - font.lineHeight) / 2 + 1;
		int acc = Draw.withAlpha(accent(), a);
		g.text(font, "LMG", lx, ly, acc, false);
		g.text(font, "LMG", lx + 1, ly, acc, false); // fake bold
		g.text(font, "CLIENT", lx + font.width("LMG") + 4, ly, Draw.withAlpha(NL_TEXT_DIM, a), false);

		// card around the settings with the category name on top
		int cx0 = x0 + sidebarW + 7, cy0 = y0 + headerH + 4, cx1 = x1 - 7, cy1 = y1 - footerH;
		Draw.roundRect(g, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, 6, fade(NL_CARD_BORDER, a));
		Draw.roundRect(g, cx0, cy0, cx1, cy1, 5, fade(NL_CARD, a));
		g.text(font, tabName.toUpperCase(), cx0 + 8, cy0 + 6, Draw.withAlpha(NL_TEXT_DIM, a), false);
		g.fill(cx0 + 8, cy0 + 15, cx1 - 8, cy0 + 16, fade(NL_CARD_BORDER, a));
	}

	/** A plain panel with a title (used by the config manager), drawn in the current style. */
	public static void simplePanel(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1,
								   int headerH, String title, float a) {
		int ty = y0 + (headerH - font.lineHeight) / 2 + 1;
		int cx = (x0 + x1) / 2;
		switch (style()) {
			case SKEET -> {
				g.fill(x0 - 5, y0 - 5, x1 + 5, y1 + 5, fade(SK_BORDER_DARK, a));
				g.fill(x0 - 4, y0 - 4, x1 + 4, y1 + 4, fade(SK_BORDER_LIGHT, a));
				g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, fade(SK_FRAME, a));
				g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, fade(SK_BORDER_LIGHT, a));
				g.fill(x0, y0, x1, y1, fade(SK_BG, a));
				for (int y = y0 + 3; y < y1; y += 3) g.fill(x0, y, x1, y + 1, fade(SK_BG_LINE, a));
				rainbow(g, x0, y0, x1, a, 1f);
				rainbow(g, x0, y0 + 1, x1, a, 0.5f);
				g.centeredText(font, title, cx, ty + 1, Draw.withAlpha(0xFFFFFF, a));
				g.fill(x0, y0 + headerH, x1, y0 + headerH + 1, fade(SK_BORDER_DARK, a));
				g.fill(x0, y0 + headerH + 1, x1, y0 + headerH + 2, fade(SK_GROUP_IN, a));
			}
			case NEVERLOSE -> {
				Draw.roundRect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 8, fade(NL_BORDER, a));
				Draw.roundRect(g, x0, y0, x1, y1, 7, fade(NL_BG, a));
				g.centeredText(font, title.toUpperCase(), cx, ty, Draw.withAlpha(accent(), a));
				g.fill(x0 + 8, y0 + headerH, x1 - 8, y0 + headerH + 1, fade(NL_BORDER, a));
			}
			default -> {
				Draw.roundRect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 7, Draw.withAlpha(0x1C2943, a));
				Draw.roundRect(g, x0, y0, x1, y1, 6, Draw.withAlpha(0x0C1322, 0.97f * a));
				g.text(font, title, cx - font.width(title) / 2, ty, Draw.withAlpha(0xE8EDF5, a), false);
				g.fill(x0 + 8, y0 + headerH, x1 - 8, y0 + headerH + 1, Draw.withAlpha(0x1C2943, a));
			}
		}
	}

	/** Text colours for the current style: {normal, dim}. */
	public static int[] textColors() {
		return switch (style()) {
			case SKEET -> new int[]{SK_TEXT, 0x6A6A6A};
			case NEVERLOSE -> new int[]{NL_TEXT, NL_TEXT_DIM};
			default -> new int[]{0xE8EDF5, 0x7C8BA3};
		};
	}

	/** Skeet-style horizontal rainbow: blue -> pink -> yellow. */
	private static void rainbow(GuiGraphicsExtractor g, int x0, int y, int x1, float a, float brightness) {
		int w = x1 - x0;
		for (int i = 0; i < w; i++) {
			float t = i / (float) Math.max(1, w - 1);
			int rgb = t < 0.5f ? Draw.lerpRgb(0x37B1DA, 0xCC46CD, t * 2) : Draw.lerpRgb(0xCC46CD, 0xCCE335, (t - 0.5f) * 2);
			if (brightness < 1f) rgb = Draw.lerpRgb(0x000000, rgb, brightness);
			g.fill(x0 + i, y, x0 + i + 1, y + 1, Draw.withAlpha(rgb, a));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Sidebar tab
	// ---------------------------------------------------------------------------------------------

	public static void tab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label,
						   float active, float hover) {
		int ty = y + (h - font.lineHeight) / 2 + 1;
		switch (style()) {
			case LMG -> {
				float bg = Math.max(active * 0.10f, hover * 0.05f);
				if (bg > 0.005f) Draw.roundRect(g, x, y, x + w, y + h, 4, active > 0.5f ? Theme.accent(bg) : Draw.white(bg));
				if (active > 0.01f) {
					int barH = Math.round((h - 6) * active);
					int by = y + (h - barH) / 2;
					g.fill(x, by, x + 2, by + barH, Theme.accent(active));
				}
				int rgb = Draw.lerpRgb(0x8E8E9A, 0xFFFFFF, Math.max(active, hover * 0.7f));
				g.text(font, label, x + 8, ty, 0xFF000000 | rgb, false);
			}
			case SKEET -> {
				if (active > 0.5f) {
					// selected tab "cuts into" the content side like gamesense
					g.fill(x - 6, y, x + w + 6, y + h, 0xFF141414);
					g.fill(x - 6, y, x + w + 6, y + 1, SK_GROUP_IN);
					g.fill(x - 6, y + h - 1, x + w + 6, y + h, SK_GROUP_IN);
				}
				int rgb = active > 0.5f ? 0xFFFFFF : Draw.lerpRgb(SK_TEXT_DIM, 0xA8A8A8, hover);
				g.text(font, label, x + 6, ty, 0xFF000000 | rgb, false);
			}
			case NEVERLOSE -> {
				float bg = Math.max(active, hover * 0.5f);
				if (bg > 0.01f) Draw.roundRect(g, x, y, x + w, y + h, 4, Draw.withAlpha(NL_TAB_ACTIVE, bg));
				if (active > 0.01f) {
					int cy = y + h / 2;
					Draw.roundRect(g, x + 5, cy - 2, x + 9, cy + 2, 2, Draw.withAlpha(accent(), active));
				}
				int rgb = Draw.lerpRgb(NL_TEXT_DIM, 0xFFFFFF, Math.max(active, hover * 0.6f));
				g.text(font, label, x + 13, ty, 0xFF000000 | rgb, false);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Small buttons (header / footer)
	// ---------------------------------------------------------------------------------------------

	public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label,
							  float hover, float a) {
		int ty = y + (h - font.lineHeight) / 2 + 1;
		int cx = x + w / 2;
		switch (style()) {
			case LMG -> {
				if (hover > 0.01f) {
					int bw = Math.round(w * (0.5f + 0.5f * Draw.easeOutCubic(hover)));
					Draw.roundRect(g, cx - bw / 2, y, cx + bw / 2, y + h, 4, Theme.accent(0.11f * hover * a));
				}
				g.centeredText(font, label, cx, ty, Draw.withAlpha(Draw.lerpRgb(0xA4A4B0, 0xFFFFFF, hover), a));
			}
			case SKEET -> {
				g.fill(x, y, x + w, y + h, fade(SK_BORDER_DARK, a));
				int top = Draw.lerpRgb(0x2A2A2A, 0x343434, hover), bottom = Draw.lerpRgb(0x222222, 0x2B2B2B, hover);
				g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, Draw.withAlpha(top, a), Draw.withAlpha(bottom, a));
				g.centeredText(font, label, cx, ty, Draw.withAlpha(Draw.lerpRgb(SK_TEXT, 0xFFFFFF, hover), a));
			}
			case NEVERLOSE -> {
				Draw.roundRect(g, x, y, x + w, y + h, 4, Draw.withAlpha(Draw.lerpRgb(0x001C38, 0x0A2A52, hover), a));
				g.centeredText(font, label, cx, ty, Draw.withAlpha(Draw.lerpRgb(NL_TEXT, 0xFFFFFF, hover), a));
			}
		}
	}

	static int fade(int argb, float a) {
		return Draw.fade(argb, a);
	}
}
