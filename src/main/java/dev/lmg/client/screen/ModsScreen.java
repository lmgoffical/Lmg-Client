package dev.lmg.client.screen;

import com.mojang.blaze3d.Blaze3D;
import dev.lmg.client.mods.ModList;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Your mods folder, as a list: what's installed, what version, what it does, and a switch to
 * turn each one on or off (takes effect the next time you start the game).
 */
public class ModsScreen extends Screen {
	private static final int HEADER = 24, ROW = 32, TOP = 52;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int W, H, x0, y0, scroll;
	private String query = "";
	private List<ModList.Mod> mods = new ArrayList<>();
	private EditBox search;
	private String message = "";
	private long messageAt;

	public ModsScreen(Screen parent) {
		super(Component.literal("Mods"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		W = Math.min(440, width - 16);
		H = Math.min(272, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		mods = ModList.all();
		addRenderableWidget(new SkinButton(x0 + 6, y0 + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(x0 + W - 84, y0 + 6, 78, 13, Component.literal("Open folder"), openedAt, () -> {
			try {
				Files.createDirectories(ModList.FOLDER);
				Blaze3D.openPath(ModList.FOLDER);
			} catch (Exception e) {
				say("Couldn't open " + ModList.FOLDER);
			}
		}));
		addRenderableWidget(new SkinButton(x0 + W - 172, y0 + 6, 84, 13, Component.literal("Find more mods"), openedAt, () ->
				ConfirmLinkScreen.confirmLinkNow(this, URI.create("https://modrinth.com/mods?g=categories:fabric"))));
		search = new EditBox(font, x0 + 10, y0 + HEADER + 8, 150, 14, Component.literal("Search"));
		search.setHint(Component.literal("Search mods..."));
		search.setValue(query);
		search.setResponder(v -> {
			query = v;
			scroll = 0;
		});
		addRenderableWidget(search);
	}

	private List<ModList.Mod> shown() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return mods;
		List<ModList.Mod> out = new ArrayList<>();
		for (ModList.Mod m : mods) {
			if (m.name().toLowerCase(Locale.ROOT).contains(q) || m.id().contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
		}
		return out;
	}

	private int visibleRows() {
		return Math.max(1, (H - TOP - 26) / ROW);
	}

	private void say(String text) {
		message = text;
		messageAt = Util.getMillis();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick);
			g.fill(0, 0, width, height, 0x66000000);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 300f));
		CosmeticsScreen.Pal p = CosmeticsScreen.pal();
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "Mods", a);

		long off = mods.stream().filter(m -> !m.on()).count();
		String summary = mods.size() + " mods" + (off > 0 ? "  -  " + off + " off" : "");
		g.text(font, summary, x0 + W - 10 - font.width(summary), y0 + HEADER + 11, Draw.withAlpha(p.dim(), a), false);

		List<ModList.Mod> list = shown();
		int rows = visibleRows();
		scroll = Math.max(0, Math.min(scroll, list.size() - rows));
		int lx0 = x0 + 8, lx1 = x0 + W - 12, ly0 = y0 + TOP;
		g.enableScissor(lx0, ly0, lx1 + 4, ly0 + rows * ROW);
		for (int i = 0; i < Math.min(rows + 1, list.size() - scroll); i++) {
			ModList.Mod m = list.get(scroll + i);
			int ry = ly0 + i * ROW;
			boolean over = mouseX >= lx0 && mouseX < lx1 && mouseY >= ry && mouseY < ry + ROW - 2 && mouseY < ly0 + rows * ROW;
			Draw.roundRect(g, lx0, ry, lx1, ry + ROW - 2, 4, Draw.fade(over ? p.cardHover() : p.card(), a));

			// icon (or the first letter)
			Identifier icon = ModList.icon(m);
			if (icon != null) {
				g.blit(RenderPipelines.GUI_TEXTURED, icon, lx0 + 5, ry + 5, 0f, 0f, 20, 20, 32, 32, 32, 32, Draw.white(m.on() ? a : 0.4f * a));
			} else {
				Draw.roundRect(g, lx0 + 5, ry + 5, lx0 + 25, ry + 25, 4, Draw.withAlpha(p.accent(), 0.22f * a));
				g.centeredText(font, m.name().substring(0, 1).toUpperCase(Locale.ROOT), lx0 + 15, ry + 11, Draw.withAlpha(p.accent(), a));
			}

			int tx = lx0 + 32;
			int nameColor = m.on() ? p.text() : p.dim();
			g.text(font, m.name(), tx, ry + 5, Draw.withAlpha(nameColor, a), false);
			int vx = tx + font.width(m.name()) + 6;
			g.text(font, m.version(), vx, ry + 5, Draw.withAlpha(p.dim(), a), false);
			int bx = vx + font.width(m.version()) + 6;
			if (m.lmgPack()) bx = badge(g, "Lmg pack", bx, ry + 4, 0xF2C14E, a);
			if (m.required()) badge(g, "needed", bx, ry + 4, p.dim(), a);

			String desc = m.description().isBlank() ? (m.authors().isBlank() ? "" : "by " + m.authors()) : m.description().replace('\n', ' ');
			int room = lx1 - 60 - tx;
			if (font.width(desc) > room) desc = font.plainSubstrByWidth(desc, room - 8) + "..";
			g.text(font, desc, tx, ry + 17, Draw.withAlpha(p.dim(), a), false);

			// on/off switch
			int sx = lx1 - 30, sy = ry + 9;
			boolean on = m.on();
			int track = on ? (0xFF000000 | p.accent()) : 0xFF2A3550;
			if (m.required() && on) track = Draw.lerpRgb(0x2A3550, p.accent(), 0.45f) | 0xFF000000;
			Draw.roundRect(g, sx, sy, sx + 20, sy + 10, 5, Draw.fade(track, a));
			int kx = on ? sx + 11 : sx + 1;
			Draw.roundRect(g, kx, sy + 1, kx + 8, sy + 9, 4, Draw.white(a));
			if (ModList.changesAfterRestart(m)) {
				String t = "on restart";
				g.text(font, t, lx1 - 6 - font.width(t), ry + 21, Draw.withAlpha(0xF2C14E, a), false);
			}
		}
		g.disableScissor();
		if (list.isEmpty()) {
			g.centeredText(font, query.isBlank() ? "No mods found in your mods folder." : "Nothing matches \"" + query + "\".",
					x0 + W / 2, ly0 + 20, Draw.withAlpha(p.dim(), a));
		}
		if (list.size() > rows) {
			int barH = Math.max(12, rows * ROW * rows / list.size());
			int by = ly0 + (rows * ROW - barH) * scroll / Math.max(1, list.size() - rows);
			Draw.roundRect(g, lx1 + 2, by, lx1 + 4, by + barH, 1, Draw.withAlpha(p.accent(), 0.6f * a));
		}

		// footer: what just happened, or how switching works
		String foot;
		int footColor;
		float age = (Util.getMillis() - messageAt) / 1000f;
		if (!message.isEmpty() && age < 6f) {
			foot = message;
			footColor = 0xFF8C7C;
		} else if (mods.stream().anyMatch(ModList::changesAfterRestart)) {
			foot = "Changes apply the next time you start the game.";
			footColor = 0xF2C14E;
		} else {
			foot = "Switch a mod off here, or drop new .jar files in the folder.";
			footColor = p.dim();
		}
		if (font.width(foot) > W - 20) foot = font.plainSubstrByWidth(foot, W - 28) + "..";
		g.text(font, foot, x0 + 10, y0 + H - 16, Draw.withAlpha(footColor, a), false);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private int badge(GuiGraphicsExtractor g, String text, int x, int y, int rgb, float a) {
		int w = font.width(text) + 6;
		Draw.roundRect(g, x, y, x + w, y + 10, 3, Draw.withAlpha(rgb, 0.16f * a));
		g.text(font, text, x + 3, y + 1, Draw.withAlpha(rgb, a), false);
		return x + w + 4;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		List<ModList.Mod> list = shown();
		int lx1 = x0 + W - 12, ly0 = y0 + TOP;
		for (int i = 0; i < Math.min(visibleRows(), list.size() - scroll); i++) {
			int ry = ly0 + i * ROW;
			int sx = lx1 - 34;
			if (mx >= sx && mx < lx1 && my >= ry + 4 && my < ry + 24) {
				String problem = ModList.toggle(list.get(scroll + i));
				if (problem != null) say(problem);
				mods = ModList.all();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (int) Math.signum(scrollY);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
