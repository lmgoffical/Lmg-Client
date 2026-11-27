package dev.lmg.legacy.ui;

import dev.lmg.legacy.Config;
import dev.lmg.legacy.feature.Hud;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

import java.io.IOException;

/** Drag the HUD panels around. Right click switches one on or off. */
public class HudEditor extends GuiScreen {
	private final GuiScreen parent;
	private Hud.Element dragging;
	private int grabX, grabY;

	public HudEditor(GuiScreen parent) {
		this.parent = parent;
	}

	private Hud.Element at(int mx, int my) {
		ScaledResolution sr = new ScaledResolution(mc);
		float s = Config.get().hudScale;
		for (int i = Hud.ALL.size() - 1; i >= 0; i--) {
			Hud.Element e = Hud.ALL.get(i);
			float[] p = e.pos();
			int x = Math.round(p[0] * sr.getScaledWidth()), y = Math.round(p[1] * sr.getScaledHeight());
			if (Ui.inside(mx, my, x, y, x + Math.round(e.w * s), y + Math.round(e.h * s))) return e;
		}
		return null;
	}

	@Override
	public void drawScreen(int mx, int my, float pt) {
		drawRect(0, 0, width, height, 0x66000000);
		// a faint grid helps line things up
		for (int x = 0; x < width; x += 20) drawRect(x, 0, x + 1, height, 0x10FFFFFF);
		for (int y = 0; y < height; y += 20) drawRect(0, y, width, y + 1, 0x10FFFFFF);
		ScaledResolution sr = new ScaledResolution(mc);
		Hud.render(sr, true);
		float s = Config.get().hudScale;
		Hud.Element over = dragging != null ? dragging : at(mx, my);
		for (Hud.Element e : Hud.ALL) {
			float[] p = e.pos();
			int x = Math.round(p[0] * sr.getScaledWidth()), y = Math.round(p[1] * sr.getScaledHeight());
			int x1 = x + Math.round(e.w * s), y1 = y + Math.round(e.h * s);
			int col = e == over ? Ui.accent() : (e.on() ? 0x80FFFFFF : 0x40FFFFFF);
			drawRect(x - 1, y - 1, x1 + 1, y, col);
			drawRect(x - 1, y1, x1 + 1, y1 + 1, col);
			drawRect(x - 1, y, x, y1, col);
			drawRect(x1, y, x1 + 1, y1, col);
			if (e == over) Ui.shadow(e.name + (e.on() ? "" : " (off)"), x, y1 + 3, 0xFFFFFFFF);
		}
		String tip = "Drag to move. Right click: on / off. Esc: done.";
		int tw = Ui.width(tip) + 12;
		Ui.round(width / 2 - tw / 2, height - 22, width / 2 + tw / 2, height - 8, 3, 0xD00D1424);
		Ui.centered(tip, width / 2, height - 18, Ui.TEXT);
		super.drawScreen(mx, my, pt);
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) throws IOException {
		super.mouseClicked(mx, my, button);
		Hud.Element e = at(mx, my);
		if (e == null) return;
		if (button == 1) {
			e.toggle();
			Config.save();
			return;
		}
		if (button == 0) {
			ScaledResolution sr = new ScaledResolution(mc);
			float[] p = e.pos();
			dragging = e;
			grabX = mx - Math.round(p[0] * sr.getScaledWidth());
			grabY = my - Math.round(p[1] * sr.getScaledHeight());
		}
	}

	@Override
	protected void mouseClickMove(int mx, int my, int button, long time) {
		if (dragging == null) return;
		ScaledResolution sr = new ScaledResolution(mc);
		float s = Config.get().hudScale;
		int x = Math.max(0, Math.min(sr.getScaledWidth() - Math.round(dragging.w * s), mx - grabX));
		int y = Math.max(0, Math.min(sr.getScaledHeight() - Math.round(dragging.h * s), my - grabY));
		Config.get().hudPos.put(dragging.id, new float[]{x / (float) sr.getScaledWidth(), y / (float) sr.getScaledHeight()});
	}

	@Override
	protected void mouseReleased(int mx, int my, int state) {
		if (dragging != null) Config.save();
		dragging = null;
	}

	@Override
	protected void keyTyped(char c, int key) throws IOException {
		if (key == 1) {
			Config.save();
			mc.displayGuiScreen(parent);
			return;
		}
		super.keyTyped(c, key);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
