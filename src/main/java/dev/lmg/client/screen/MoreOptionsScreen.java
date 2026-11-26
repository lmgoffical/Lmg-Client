package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.function.Supplier;

/**
 * "More options" for one setting: a small card over the menu with the extra, finer controls
 * (the gear next to a setting's name opens it). The menu stays visible behind it.
 */
public class MoreOptionsScreen extends Screen {
	private static final int CARD_W = 236, ROW_GAP = 16;

	private final Screen parent;
	private final String about;
	private final Supplier<List<SettingRow>> rowsMaker;
	private final Runnable reset;
	private final long openedAt = Util.getMillis();
	private List<SettingRow> rows = List.of();
	private int cx0, cy0, cx1, cy1;
	private int resetX0, resetX1, doneX0, doneX1, btnY;

	/**
	 * @param title  shown at the top ("Crosshair")
	 * @param about  one line under the title
	 * @param rows   makes the rows (called again after Reset)
	 * @param reset  puts these options back to normal (or null for no Reset button)
	 */
	public MoreOptionsScreen(Screen parent, String title, String about, Supplier<List<SettingRow>> rows, Runnable reset) {
		super(Component.literal(title));
		this.parent = parent;
		this.about = about;
		this.rowsMaker = rows;
		this.reset = reset;
	}

	private int rowH(SettingRow r) {
		return MenuSkin.style() == LmgConfig.MenuStyle.SKEET && r.isTallInSkeet() ? 27 : ROW_GAP;
	}

	@Override
	protected void init() {
		if (parent != null) parent.init(width, height);
		rows = rowsMaker.get();
		int h = 34;
		for (SettingRow r : rows) h += rowH(r);
		h += 26;
		cx0 = width / 2 - CARD_W / 2;
		cx1 = cx0 + CARD_W;
		cy0 = Math.max(6, height / 2 - h / 2);
		cy1 = cy0 + h;
		int y = cy0 + 32;
		long t = Util.getMillis();
		for (int i = 0; i < rows.size(); i++) {
			SettingRow r = rows.get(i);
			int rh = rowH(r);
			addRenderableWidget(r.at(cx0 + 6, y, CARD_W - 12, rh - 2, t + i * 25L));
			y += rh;
		}
		btnY = cy1 - 21;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (parent != null) parent.extractBackground(g, -1, -1, partialTick);
		else super.extractBackground(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (parent != null) parent.extractRenderState(g, -1, -1, partialTick);
		g.nextStratum();
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 220f));
		g.fill(0, 0, width, height, Draw.withAlpha(0x05070D, 0.55f * a));
		int lift = Math.round((1 - a) * 6);
		LmgConfig.MenuStyle style = MenuSkin.style();
		int edge = switch (style) {
			case SKEET -> 0x3A3A3A;
			case NEVERLOSE -> 0x2B4376;
			default -> 0x2A3B5E;
		};
		int fill = switch (style) {
			case SKEET -> 0x141414;
			case NEVERLOSE -> 0x00101F;
			default -> 0x0C1322;
		};
		if (style == LmgConfig.MenuStyle.SKEET) {
			g.fill(cx0 - 1, cy0 - 1 + lift, cx1 + 1, cy1 + 1 + lift, Draw.withAlpha(edge, a));
			g.fill(cx0, cy0 + lift, cx1, cy1 + lift, Draw.withAlpha(fill, a));
			g.fill(cx0, cy0 + lift, cx1, cy0 + 2 + lift, Draw.withAlpha(MenuSkin.accent(), a));
		} else {
			Draw.roundRect(g, cx0 - 1, cy0 - 1 + lift, cx1 + 1, cy1 + 1 + lift, 8, Draw.withAlpha(edge, a));
			Draw.roundRect(g, cx0, cy0 + lift, cx1, cy1 + lift, 7, Draw.withAlpha(fill, 0.98f * a));
		}
		g.text(font, title.getString(), cx0 + 12, cy0 + 9 + lift, Draw.white(a), false);
		String sub = "more options";
		g.text(font, sub, cx1 - 12 - font.width(sub), cy0 + 9 + lift, Draw.withAlpha(0x56637A, a), false);
		g.text(font, about, cx0 + 12, cy0 + 20 + lift, Draw.withAlpha(0x7C8BA3, a), false);

		// what the row under the mouse does
		String hint = null;
		for (SettingRow r : rows) if (r.hovered() && !r.hint().isEmpty()) hint = r.hint();

		super.extractRenderState(g, mouseX, mouseY, partialTick);

		int y = btnY + lift;
		if (hint != null) {
			String h = font.width(hint) > CARD_W - 24 ? font.plainSubstrByWidth(hint, CARD_W - 30) + ".." : hint;
			g.text(font, h, cx0 + 12, y - 11, Draw.withAlpha(0xF2C14E, a), false);
		}
		String done = "Done";
		int dw = font.width(done) + 20;
		doneX1 = cx1 - 8;
		doneX0 = doneX1 - dw;
		boolean overDone = mouseX >= doneX0 && mouseX < doneX1 && mouseY >= y && mouseY < y + 14;
		PillButton.draw(g, font, doneX0, y, dw, 14, done, true, overDone ? 1f : 0f, a);
		if (reset != null) {
			String r = "Back to normal";
			int rw = font.width(r) + 16;
			resetX0 = cx0 + 8;
			resetX1 = resetX0 + rw;
			boolean overReset = mouseX >= resetX0 && mouseX < resetX1 && mouseY >= y && mouseY < y + 14;
			PillButton.draw(g, font, resetX0, y, rw, 14, r, false, overReset ? 1f : 0f, a);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x(), y = event.y();
		if (y >= btnY && y < btnY + 14) {
			if (x >= doneX0 && x < doneX1) {
				onClose();
				return true;
			}
			if (reset != null && x >= resetX0 && x < resetX1) {
				reset.run();
				LmgConfig.save();
				rebuildWidgets();
				return true;
			}
		}
		if (super.mouseClicked(event, doubleClick)) return true;
		// a click outside the card closes it
		if (x < cx0 || x >= cx1 || y < cy0 || y >= cy1) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		for (SettingRow r : rows) if (r.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (dev.lmg.client.LmgClient.OPEN_MENU_KEY.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		LmgConfig.save();
		minecraft.gui.setScreen(parent);
	}
}
