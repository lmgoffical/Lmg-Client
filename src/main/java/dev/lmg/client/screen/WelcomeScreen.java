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
	private static final int W = 300, HEADER = 24;
	private static final String PRIVACY = "https://lmgoffical.github.io/Lmg-Client/#/privacy";

	private final Screen parent;
	private final boolean firstRun;
	private final long openedAt = Util.getMillis();

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

	@Override
	protected void init() {
		int y = height / 2 + bodyHeight() / 2 - 22;
		int x = width / 2 - W / 2 + 10;
		addRenderableWidget(new SkinButton(x, y, 130, 14, Component.literal(firstRun ? "Got it" : "Close"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(x + 140, y, W - 30 - 130, 14, Component.literal("Privacy policy"), openedAt, () ->
				ConfirmLinkScreen.confirmLinkNow(this, URI.create(PRIVACY))));
	}

	private int bodyHeight() {
		return 230;
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
		int x0 = width / 2 - W / 2, y0 = height / 2 - bodyHeight() / 2;
		int x1 = x0 + W, y1 = y0 + bodyHeight();
		MenuSkin.simplePanel(g, font, x0, y0, x1, y1, HEADER, "Before you start", a);

		int y = y0 + HEADER + 10;
		Font f = font;
		for (String[] section : SECTIONS) {
			g.text(f, section[0], x0 + 12, y, Draw.withAlpha(0xF2C14E, a), false);
			y += 12;
			for (var line : f.split(Component.literal(section[1]), W - 24)) {
				g.text(f, line, x0 + 12, y, Draw.withAlpha(0xA3B0C4, a), false);
				y += 10;
			}
			y += 8;
		}
		String mode = LmgConfig.get().serverSafe ? "Server-safe mode: on" : "Server-safe mode: off";
		g.text(f, mode, x0 + 12, y, Draw.withAlpha(LmgConfig.get().serverSafe ? 0x7CFF9E : 0xFF8C7C, a), false);
		y += 11;
		g.text(f, "Change it any time in Game > Tools.", x0 + 12, y, Draw.withAlpha(0x7C8BA3, a), false);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
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
