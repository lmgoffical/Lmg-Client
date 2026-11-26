package dev.lmg.client.screen;

import dev.lmg.client.emote.Emote;
import dev.lmg.client.emote.Emotes;
import dev.lmg.client.feature.LmgKeys;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import dev.lmg.client.social.Social;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Hold the emote key: eight emotes in a circle around you. Point at one (the middle shows you
 * doing it) and let go of the key to play it. Click works too. Esc or letting go in the middle
 * cancels.
 */
public class EmoteWheelScreen extends Screen {
	private static final Emote[] ORDER = {Emote.WAVE, Emote.HUG, Emote.CHEER, Emote.DANCE, Emote.CLAP, Emote.SIT, Emote.POINT, Emote.BOW};
	private static final int CARD_W = 60, CARD_H = 21, DEAD_ZONE = 22;

	private final long openedAt = Util.getMillis();
	private final float[] grow = new float[ORDER.length];
	private int selected = -1;
	private long lastFrame = -1, selectedAt;

	public EmoteWheelScreen() {
		super(Component.literal("Emotes"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// keep the world in view: just a soft dark circle behind the wheel
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 180f));
		g.fill(0, 0, width, height, Draw.withAlpha(0x000000, 0.42f * a));
	}

	private int cx() {
		return width / 2;
	}

	private int cy() {
		return height / 2 - 8;
	}

	/** How far the cards sit from the middle: smaller on short screens so nothing runs off. */
	private int radius() {
		return Math.max(62, Math.min(92, height / 2 - 40));
	}

	/** Sideways the ring is wider (screens are wide), so the cards never touch. */
	private int radiusX() {
		return Math.max(radius(), Math.min(150, Math.min(width / 2 - 40, radius() + 50)));
	}

	private boolean hudWasHidden;

	@Override
	protected void init() {
		// a clean view while you choose: no hotbar or HUD, like a console game's wheel
		hudWasHidden = minecraft.gui.hud.isHidden();
		if (!hudWasHidden) minecraft.gui.hud.toggle();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float open = Draw.easeOutCubic(Theme.progress(openedAt, 220f));
		int cx = cx(), cy = cy();

		// which one you're pointing at
		double dx = mouseX - cx, dy = mouseY - cy;
		int pick = -1;
		if (dx * dx + dy * dy > DEAD_ZONE * DEAD_ZONE) {
			double ang = Math.atan2(dy * radiusX() / (double) radius(), dx) + Math.PI / 2;   // 0 = up, clockwise
			pick = Math.floorMod((int) Math.round(ang / (Math.PI * 2 / ORDER.length)), ORDER.length);
		}
		if (pick != selected) {
			selected = pick;
			selectedAt = now;
		}

		int primary = Theme.primary();
		// a faint ring the cards sit on
		float r = radius() * (0.85f + 0.15f * open);
		float rx = radiusX() * (0.85f + 0.15f * open);
		for (int i = 0; i < 96; i++) {
			double a0 = i * Math.PI * 2 / 96;
			int px = cx + (int) Math.round(Math.cos(a0) * rx), py = cy + (int) Math.round(Math.sin(a0) * r);
			g.fill(px, py, px + 1, py + 1, Draw.withAlpha(0xFFFFFF, 0.12f * open));
		}

		// you, in the middle, doing the one you're pointing at
		Emote shown = selected >= 0 ? ORDER[selected] : null;
		int ph = Math.min(42, radius() - 24), pw = 30;
		Draw.roundRect(g, cx - pw, cy - ph, cx + pw, cy + ph, 8, Draw.withAlpha(0x0C1322, 0.82f * open));
		Draw.roundRect(g, cx - pw + 6, cy - ph, cx + pw - 6, cy - ph + 1, 1, Draw.withAlpha(primary, 0.7f * open));
		if (minecraft.player != null) {
			Emotes.preview = shown;
			Emotes.previewStart = selectedAt;
			try {
				InventoryScreen.extractEntityInInventoryFollowsMouse(g, cx - pw + 2, cy - ph + 4, cx + pw - 2, cy + ph - 12,
						Math.max(18, ph * 2 / 3), 0.0625f, cx, cy - 20, minecraft.player);
			} finally {
				Emotes.preview = null;
			}
		}
		String title = shown != null ? shown.label : "Emotes";
		g.centeredText(font, title, cx, cy + ph - 11, Draw.withAlpha(0xFFFFFF, open));

		// the eight cards
		for (int i = 0; i < ORDER.length; i++) {
			grow[i] = Draw.approach(grow[i], i == selected ? 1f : 0f, 14f, dt);
			double ang = i * Math.PI * 2 / ORDER.length - Math.PI / 2;
			int x = cx + (int) Math.round(Math.cos(ang) * (rx + 4 * grow[i])), y = cy + (int) Math.round(Math.sin(ang) * (r + 4 * grow[i]));
			float s = 1f + 0.12f * grow[i];
			int w = Math.round(CARD_W * s), h = Math.round(CARD_H * s);
			int x0 = x - w / 2, y0 = y - h / 2;
			// stagger the cards in as the wheel opens
			float appear = Draw.easeOutCubic(Draw.clamp01((now - openedAt - i * 18) / 200f));
			if (appear <= 0) continue;
			int bg = Draw.lerpRgb(0x111A2E, primary, 0.85f * grow[i]);
			Draw.roundRect(g, x0, y0 + 1, x0 + w, y0 + h + 1, 6, Draw.withAlpha(0x000000, 0.35f * appear));
			Draw.roundRect(g, x0, y0, x0 + w, y0 + h, 6, Draw.withAlpha(bg, 0.94f * appear));
			if (grow[i] < 0.5f) Draw.roundRect(g, x0, y0, x0 + w, y0 + 1, 1, Draw.withAlpha(0x22304D, appear));
			String label = ORDER[i].label;
			int tc = grow[i] > 0.5f ? Theme.textOn(bg) : 0xE8EDF5;
			g.text(font, label, x - font.width(label) / 2, y - 4 - (ORDER[i].lasting() ? 3 : 0), Draw.withAlpha(tc, appear), false);
			if (ORDER[i].lasting()) {
				String sub = ORDER[i].loops() ? "until you move" : "8 sec";
				Draw.scaledText(g, font, sub, x, y + 4, 0.65f, Draw.withAlpha(grow[i] > 0.5f ? tc : 0x7C8BA3, 0.9f * appear));
			}
		}

		// how it works, at the bottom
		String key = LmgKeys.EMOTE.getTranslatedKeyMessage().getString();
		String hint = selected >= 0 ? "Let go of " + key + " to play it  -  moving stops it" : "Point at an emote, then let go of " + key;
		int hy = Math.min(height - 24, cy + radius() + 18);
		g.centeredText(font, hint, cx, hy, Draw.withAlpha(0xC9D3E3, open));
		if (!Social.signedIn()) {
			g.centeredText(font, "Only you see emotes until you turn on Friends (O).", cx, hy + 11, Draw.withAlpha(0x7C8BA3, open));
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void playSelected() {
		Emote e = selected >= 0 ? ORDER[selected] : null;
		onClose();
		if (e != null) Emotes.play(e);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (LmgKeys.EMOTE.matches(event)) {
			playSelected();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		playSelected();
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		Emotes.preview = null;
		if (!hudWasHidden && minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
	}
}
