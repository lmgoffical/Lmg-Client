package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.List;

/**
 * "Before you start": shown once, and again from Tools -> About & safety.
 * Says plainly what the mod does, what it sends, and how server-safe mode works.
 */
public class WelcomeScreen extends Screen {
	private static final int MAX_W = 330, HEADER = 24, BUTTON_H = 16, PAD = 12;
	private static final String PRIVACY = "https://lmgoffical.github.io/Lmg-Client/#/privacy";

	private final Screen parent;
	private final boolean firstRun;
	private final long openedAt = Util.getMillis();
	private int W = MAX_W, x0, y0, bodyH, textTop, textBottom, contentH, scroll;
	private SkinButton gotIt, privacy;

	public WelcomeScreen(Screen parent, boolean firstRun) {
		super(Component.literal("Before you start"));
		this.parent = parent;
		this.firstRun = firstRun;
	}

	private static final String[][] SECTIONS = {
			{"What this is", "Lmg Client changes how Minecraft looks: sky, fog, menus, the HUD and cosmetics. It doesn't change how the game plays. It's free, and it isn't made by or connected to Mojang or Microsoft."},
			{"Servers", "Some servers ban visual helpers that show things through walls. Server-safe mode is on by default: on multiplayer servers, freecam and ore glow stay off. Always read a server's rules before using anything else."},
			{"Your data", "No tracking, no analytics. The mod checks GitHub for updates when it starts. Configs are uploaded only when you press Share. Pets and cosmetics are shared with other Lmg players only if you turn that on."},
	};

	/**
	 * Sizes the card to its text: header, the sections, the server-safe line, then the buttons
	 * underneath. If the window is too short for all of it the text scrolls and the buttons stay put.
	 */
	@Override
	protected void init() {
		W = Math.min(MAX_W, width - 16);
		contentH = 0;
		for (String[] section : SECTIONS) contentH += 12 + font.split(Component.literal(section[1]), W - PAD * 2).size() * 10 + 8;
		contentH += 22;
		int wanted = HEADER + 10 + contentH + 10 + BUTTON_H + 10;
		bodyH = Math.min(wanted, height - 12);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - bodyH / 2;
		int by = y0 + bodyH - 10 - BUTTON_H;
		textTop = y0 + HEADER + 10;
		textBottom = by - 8;
		scroll = Math.max(0, Math.min(scroll, contentH - (textBottom - textTop)));
		int gap = 8, bw = (W - PAD * 2 - gap) / 2;
		gotIt = addRenderableWidget(new SkinButton(x0 + PAD, by, bw, BUTTON_H, Component.literal(firstRun ? "Got it" : "Close"), openedAt, this::onClose));
		privacy = addRenderableWidget(new SkinButton(x0 + PAD + bw + gap, by, bw, BUTTON_H, Component.literal("Privacy policy"), openedAt, () ->
				ConfirmLinkScreen.confirmLinkNow(this, URI.create(PRIVACY))));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int room = textBottom - textTop;
		if (contentH > room) {
			scroll = Math.max(0, Math.min(contentH - room, scroll - (int) Math.round(scrollY * 12)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick);
			g.fill(0, 0, width, height, 0x88000000);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 300f));
		int x1 = x0 + W, y1 = y0 + bodyH;
		MenuSkin.simplePanel(g, font, x0, y0, x1, y1, HEADER, "Before you start", a);

		int room = textBottom - textTop;
		boolean scrolls = contentH > room;
		if (scrolls) g.enableScissor(x0, textTop - 2, x1, textBottom);
		int y = textTop - scroll;
		Font f = font;
		for (String[] section : SECTIONS) {
			g.text(f, section[0], x0 + 12, y, Draw.withAlpha(0xF2C14E, a), false);
			y += 12;
			for (var line : f.split(Component.literal(section[1]), W - PAD * 2)) {
				g.text(f, line, x0 + 12, y, Draw.withAlpha(0xA3B0C4, a), false);
				y += 10;
			}
			y += 8;
		}
		String mode = LmgConfig.get().serverSafe ? "Server-safe mode: on" : "Server-safe mode: off";
		g.text(f, mode, x0 + 12, y, Draw.withAlpha(LmgConfig.get().serverSafe ? 0x7CFF9E : 0xFF8C7C, a), false);
		y += 11;
		g.text(f, "Change it any time in Game > Tools.", x0 + 12, y, Draw.withAlpha(0x7C8BA3, a), false);
		if (scrolls) {
			g.disableScissor();
			int barH = Math.max(12, room * room / contentH);
			int barY = textTop + (room - barH) * scroll / Math.max(1, contentH - room);
			g.fill(x1 - 5, barY, x1 - 3, barY + barH, Draw.withAlpha(0x7C8BA3, 0.6f * a));
		}
		g.fill(x0 + PAD, textBottom + 3, x1 - PAD, textBottom + 4, Draw.withAlpha(0x22304A, a));

		// proper boxes behind the two buttons: "Got it" filled, "Privacy policy" outlined
		buttonBox(g, gotIt, true, a);
		buttonBox(g, privacy, false, a);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void buttonBox(GuiGraphicsExtractor g, SkinButton b, boolean filled, float a) {
		if (b == null) return;
		int bx = b.getX(), by = b.getY(), bx1 = bx + b.getWidth(), by1 = by + b.getHeight();
		float h = b.isHoveredOrFocused() ? 1f : 0f;
		if (filled) {
			Draw.roundRect(g, bx, by, bx1, by1, 4, Theme.accent((0.8f + 0.2f * h) * a));
			Draw.roundRect(g, bx + 1, by + 1, bx1 - 1, by1 - 1, 3, Draw.withAlpha(0x0D1424, a));
			Draw.roundRect(g, bx + 1, by + 1, bx1 - 1, by1 - 1, 3, Theme.accent((0.32f + 0.18f * h) * a));
		} else {
			Draw.roundRect(g, bx, by, bx1, by1, 4, Theme.accent((0.45f + 0.3f * h) * a));
			Draw.roundRect(g, bx + 1, by + 1, bx1 - 1, by1 - 1, 3, Draw.withAlpha(0x0D1424, a));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (firstRun) {
			LmgConfig.get().welcomeSeen = true;
			LmgConfig.save();
		}
		minecraft.gui.setScreen(parent);
	}
}
