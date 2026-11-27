package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.hud.HudLayout;
import dev.lmg.client.hud.HudLayout.Element;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Drag the HUD bar, target info and potion list wherever you like.
 * Drag to move, right-click an element to put it back, Esc or Done to save.
 * Elements snap to the screen centre and edges when you get close.
 */
public class HudEditorScreen extends Screen {
	private static final int SNAP = 5;
	private static final int MARGIN = 4;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private Element dragging;
	private int grabX, grabY;
	private int snapLineX = -1, snapLineY = -1;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		HudLayout.editing = true;
		int bw = 70;
		addRenderableWidget(new MenuTextButton(width / 2 - bw - 4, height - 26, bw, 14, Component.literal("Reset all"), openedAt, () -> {
			for (Element e : Element.values()) HudLayout.reset(e);
			LmgConfig.save();
		}));
		addRenderableWidget(new MenuTextButton(width / 2 + 4, height - 26, bw, 14, Component.literal("Done"), openedAt, this::onClose));
	}

	@Override
	public void removed() {
		HudLayout.editing = false;
		LmgConfig.save();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// No blur: the real HUD has to stay visible behind the editor.
		g.fill(0, 0, width, height, 0x30000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float intro = Draw.easeOutCubic(Theme.progress(openedAt, 300f));

		if (minecraft.level == null) {
			g.centeredText(font, "Join a world to edit the HUD layout", width / 2, height / 2 - 4, Draw.white(intro));
			super.extractRenderState(g, mouseX, mouseY, partialTick);
			return;
		}

		// faint centre guides
		g.fill(width / 2, 0, width / 2 + 1, height, Draw.white(0.05f * intro));
		g.fill(0, height / 2, width, height / 2 + 1, Draw.white(0.05f * intro));
		if (snapLineX >= 0) g.fill(snapLineX, 0, snapLineX + 1, height, Theme.accent(0.6f));
		if (snapLineY >= 0) g.fill(0, snapLineY, width, snapLineY + 1, Theme.accent(0.6f));

		Element hovered = elementAt(mouseX, mouseY);
		for (Element e : Element.values()) {
			int[] b = HudLayout.bounds(e);
			if (b == null) continue;
			boolean active = e == dragging || (dragging == null && e == hovered);
			int x0 = b[0] - 2, y0 = b[1] - 2, x1 = b[0] + b[2] + 2, y1 = b[1] + b[3] + 2;
			if (active) Draw.roundRect(g, x0, y0, x1, y1, 4, Theme.accent(0.12f));
			int line = Theme.accent(active ? 0.95f : 0.45f);
			g.fill(x0, y0, x1, y0 + 1, line);
			g.fill(x0, y1 - 1, x1, y1, line);
			g.fill(x0, y0, x0 + 1, y1, line);
			g.fill(x1 - 1, y0, x1, y1, line);
			int labelY = y0 - 10 >= 0 ? y0 - 10 : y1 + 2;
			g.text(font, e.label, x0, labelY, Draw.withAlpha(active ? 0xFFFFFF : 0xB0B0BC, intro), true);
		}

		String help = "Drag to move  ·  Right-click to reset  ·  Esc when done";
		g.centeredText(font, help, width / 2, height - 40, Draw.withAlpha(0xC8C8D2, intro));
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private Element elementAt(double mx, double my) {
		Element found = null;
		for (Element e : Element.values()) {
			int[] b = HudLayout.bounds(e);
			if (b != null && mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2) found = e;
		}
		return found;
	}

	// size of the element being dragged (taken when the drag starts)
	private int dragW, dragH;

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		Element e = elementAt(event.x(), event.y());
		if (e == null) return false;
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
			HudLayout.reset(e);
			LmgConfig.save();
			return true;
		}
		int[] b = HudLayout.bounds(e);
		dragging = e;
		grabX = (int) event.x() - b[0];
		grabY = (int) event.y() - b[1];
		dragW = b[2];
		dragH = b[3];
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		int x = (int) event.x() - grabX;
		int y = (int) event.y() - grabY;
		snapLineX = -1;
		snapLineY = -1;
		// snap: screen centre, then edges (with a small margin)
		int cx = x + dragW / 2, cy = y + dragH / 2;
		if (Math.abs(cx - width / 2) <= SNAP) { x = width / 2 - dragW / 2; snapLineX = width / 2; }
		else if (Math.abs(x - MARGIN) <= SNAP) { x = MARGIN; snapLineX = MARGIN; }
		else if (Math.abs(x + dragW - (width - MARGIN)) <= SNAP) { x = width - MARGIN - dragW; snapLineX = width - MARGIN; }
		if (Math.abs(cy - height / 2) <= SNAP) { y = height / 2 - dragH / 2; snapLineY = height / 2; }
		else if (Math.abs(y - MARGIN) <= SNAP) { y = MARGIN; snapLineY = MARGIN; }
		else if (Math.abs(y + dragH - (height - MARGIN)) <= SNAP) { y = height - MARGIN - dragH; snapLineY = height - MARGIN; }
		HudLayout.move(dragging, x, y, dragW, dragH, width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			snapLineX = -1;
			snapLineY = -1;
			LmgConfig.save();
			return true;
		}
		return super.mouseReleased(event);
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
