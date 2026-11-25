package dev.lmg.client.hud;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** The rounded bar in the top-left: star | name | server | FPS | clock. */
public class HudPill implements HudElement {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final int X = 5;
	private static final int Y = 5;
	private static final int HEIGHT = 16;
	private static final int PAD = 6;
	private static final int ICON = 10;
	private static final int BG = 0xB80E0E12;
	private static final int DIVIDER = 0x30FFFFFF;
	private static final int TEXT = 0xFFC9C9D3;
	private static final int TEXT_STRONG = 0xFFFFFFFF;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LmgConfig cfg = LmgConfig.get();
		if (!cfg.hudEnabled || mc.player == null || mc.gui.hud.isHidden()) return;
		if (mc.getDebugOverlay().showDebugScreen()) return; // don't cover the F3 screen

		List<String> parts = new ArrayList<>();
		parts.add(mc.getGameProfile().name());
		if (cfg.hudShowServer) {
			String server = serverLabel(mc);
			if (server != null) parts.add(server);
		}
		if (cfg.hudShowFps) parts.add(mc.getFps() + " fps");
		if (cfg.hudShowClock) parts.add(LocalTime.now().format(CLOCK));

		Font font = mc.font;
		int width = PAD + ICON + PAD;
		for (int i = 0; i < parts.size(); i++) {
			width += font.width(parts.get(i)) + (i < parts.size() - 1 ? PAD * 2 + 1 : PAD);
		}

		Draw.roundRect(g, X, Y, X + width, Y + HEIGHT, 5, BG);
		Draw.texture(g, Draw.SPARKLE, Draw.SPARKLE_TEX, X + PAD + ICON / 2f, Y + HEIGHT / 2f, ICON + 2, 0, 0xFFFFFFFF);

		int x = X + PAD + ICON + PAD;
		int textY = Y + (HEIGHT - font.lineHeight) / 2 + 1;
		for (int i = 0; i < parts.size(); i++) {
			String s = parts.get(i);
			g.text(font, s, x, textY, i == 0 ? TEXT_STRONG : TEXT, false);
			x += font.width(s);
			if (i < parts.size() - 1) {
				x += PAD;
				g.fill(x, Y + 4, x + 1, Y + HEIGHT - 4, DIVIDER);
				x += 1 + PAD;
			}
		}
	}

	private static String serverLabel(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		if (server != null) return mc.options.hideServerAddress ? "Multiplayer" : server.ip;
		if (mc.hasSingleplayerServer()) return "Singleplayer";
		return null;
	}
}
