package dev.lmg.client.hud;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix3x2fStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The rounded HUD bar: star | name | server | FPS | ping | clock | coords.
 * Everything in it is animated: the bar slides in, sections grow and fade
 * when turned on/off, the width glides, and the numbers count smoothly.
 */
public class HudPill implements HudElement {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final int MARGIN = 5;
	private static final int HEIGHT = 16;
	private static final int PAD = 6;
	private static final int ICON = 10;
	private static final int DIVIDER = 0x30FFFFFF;
	private static final int TEXT = 0xC9C9D3;

	private static final int NAME = 0, SERVER = 1, FPS = 2, PING = 3, CLOCK_SEG = 4, COORDS = 5, COUNT = 6;

	private final float[] visible = new float[COUNT];
	private final float[] widths = new float[COUNT];
	private final String[] texts = new String[COUNT];
	private float appear;
	private long lastNanos = -1;

	// FPS smoothing: average frame time, recomputed a few times a second, then counted toward.
	private double avgFrameSeconds = 1 / 60.0;
	private long fpsSampledAt;
	private int fpsTarget;
	private float fpsShown = -1;
	private float pingShown = -1;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LmgConfig cfg = LmgConfig.get();
		LocalPlayer player = mc.player;
		if (player == null) return;

		long nanos = System.nanoTime();
		float dt = lastNanos < 0 ? 0 : (float) Math.min(0.1, (nanos - lastNanos) / 1e9);
		lastNanos = nanos;
		updateFps(dt);

		boolean show = cfg.hudEnabled && !mc.gui.hud.isHidden() && !mc.getDebugOverlay().showDebugScreen();
		appear = Draw.approach(appear, show ? 1f : 0f, 10f * Theme.speed(), dt);
		if (appear < 0.01f) return;

		Font font = mc.font;
		fillTexts(mc, cfg, player, dt);

		// Animate each section's visibility and width.
		float total = PAD + ICON + PAD;
		boolean first = true;
		for (int i = 0; i < COUNT; i++) {
			boolean on = texts[i] != null;
			if (on) {
				float target = font.width(texts[i]);
				if (i == FPS) target = Math.max(target, font.width("000 fps")); // stops the bar twitching
				if (visible[i] < 0.01f) widths[i] = target; // new sections grow via 'visible' instead
				widths[i] = Draw.approach(widths[i], target, 14f, dt);
			}
			visible[i] = Draw.approach(visible[i], on ? 1f : 0f, 12f * Theme.speed(), dt);
			if (visible[i] < 0.005f) continue;
			if (!first) total += (PAD * 2 + 1) * visible[i];
			total += widths[i] * visible[i];
			first = false;
		}
		total += PAD;

		float scale = LmgConfig.HUD_SCALES[cfg.hudScale];
		float screenW = g.guiWidth() / scale;
		float x0 = switch (cfg.hudPosition) {
			case TOP_LEFT -> MARGIN;
			case TOP_CENTER -> screenW / 2f - total / 2f;
			case TOP_RIGHT -> screenW - MARGIN - total;
		};
		float slide = (1f - Draw.easeOutCubic(appear)) * -8f;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(scale, scale);
		pose.translate(x0, MARGIN + slide);

		int w = Math.round(total);
		float bgAlpha = LmgConfig.HUD_OPACITIES[cfg.hudOpacity] * appear;
		Draw.roundRect(g, 0, 0, w, HEIGHT, 5, Draw.withAlpha(0x0E0E12, bgAlpha));
		g.fill(5, 0, w - 5, 1, Draw.white(0.07f * appear)); // soft top highlight
		Draw.texture(g, Draw.SPARKLE, Draw.SPARKLE_TEX, PAD + ICON / 2f, HEIGHT / 2f, ICON + 2, 0, Theme.accent(appear));

		float x = PAD + ICON + PAD;
		int textY = (HEIGHT - font.lineHeight) / 2 + 1;
		first = true;
		for (int i = 0; i < COUNT; i++) {
			float v = visible[i];
			if (v < 0.005f) continue;
			if (!first) {
				x += PAD * v;
				g.fill(Math.round(x), 4, Math.round(x) + 1, HEIGHT - 4, Draw.fade(DIVIDER, v * appear));
				x += (1 + PAD) * v;
			}
			first = false;
			// Text fades a bit faster than the section shrinks so it never spills out.
			float textAlpha = v * v * appear;
			String s = texts[i] != null ? texts[i] : "";
			if (!s.isEmpty() && textAlpha > 0.02f) {
				int rgb = i == NAME ? 0xFFFFFF : TEXT;
				int tx = Math.round(x + (i == FPS ? widths[i] - font.width(s) : 0)); // right-align FPS digits
				g.text(font, s, tx, textY, Draw.withAlpha(rgb, textAlpha), false);
			}
			x += widths[i] * v;
		}
		pose.popMatrix();
	}

	private void updateFps(float dt) {
		if (dt <= 0) return;
		// Exponential moving average of frame time (~ last 30 frames).
		avgFrameSeconds += (dt - avgFrameSeconds) * 0.06;
		long now = System.currentTimeMillis();
		if (now - fpsSampledAt >= 250) {
			fpsSampledAt = now;
			fpsTarget = (int) Math.round(1.0 / Math.max(1e-4, avgFrameSeconds));
		}
		fpsShown = fpsShown < 0 ? fpsTarget : Draw.approach(fpsShown, fpsTarget, 6f, dt);
	}

	private void fillTexts(Minecraft mc, LmgConfig cfg, LocalPlayer player, float dt) {
		texts[NAME] = mc.getGameProfile().name();
		texts[SERVER] = cfg.hudShowServer ? serverLabel(mc) : null;
		texts[FPS] = cfg.hudShowFps ? Math.round(fpsShown) + " fps" : null;

		Integer ping = cfg.hudShowPing ? ping(mc, player) : null;
		if (ping != null) {
			pingShown = pingShown < 0 ? ping : Draw.approach(pingShown, ping, 6f, dt);
			texts[PING] = Math.round(pingShown) + " ms";
		} else {
			texts[PING] = null;
		}

		texts[CLOCK_SEG] = cfg.hudShowClock ? LocalTime.now().format(CLOCK) : null;
		texts[COORDS] = cfg.hudShowCoords
				? player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ()
				: null;
	}

	private static String serverLabel(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		if (server != null) return mc.options.hideServerAddress ? "Multiplayer" : server.ip;
		if (mc.hasSingleplayerServer()) return "Singleplayer";
		return null;
	}

	/** Your ping on a server, or null in singleplayer. */
	private static Integer ping(Minecraft mc, LocalPlayer player) {
		if (mc.getCurrentServer() == null) return null;
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) return null;
		PlayerInfo info = connection.getPlayerInfo(player.getUUID());
		return info == null ? null : info.getLatency();
	}
}
