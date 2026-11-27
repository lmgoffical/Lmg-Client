package dev.lmg.legacy.ui;

import dev.lmg.legacy.Config;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/** A page of settings (one tile on the home screen). Left click changes, right click goes back one step. */
public class PageScreen extends GuiScreen {
	public interface Preview { void draw(int x0, int y0, int x1, int y1, int mouseX, int mouseY, float partialTicks); }

	private static final int HEADER = 24, ROW = 18;
	private final GuiScreen parent;
	private final String title, about;
	private final List<Row> rows;
	private final Preview preview;
	private int W, H, x0, y0, listX0, listX1, listY0, listY1, scroll, previewW;
	private Row dragging;

	public PageScreen(GuiScreen parent, String title, String about, List<Row> rows, Preview preview) {
		this.parent = parent;
		this.title = title;
		this.about = about;
		this.rows = rows;
		this.preview = preview;
	}

	@Override
	public void initGui() {
		W = Math.min(preview != null ? 400 : 320, width - 16);
		H = Math.min(250, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		previewW = preview != null ? Math.min(120, W / 3) : 0;
		listX0 = x0 + 10;
		listX1 = x0 + W - 10 - (previewW > 0 ? previewW + 8 : 0);
		listY0 = y0 + HEADER + 22;
		listY1 = y0 + H - 22;
		clampScroll();
	}

	private int maxScroll() {
		return Math.max(0, rows.size() * ROW - (listY1 - listY0));
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
	}

	@Override
	public void drawScreen(int mx, int my, float pt) {
		if (mc.theWorld == null) drawDefaultBackground();
		else drawGradientRect(0, 0, width, height, 0x90000000, 0xA0000000);
		Ui.round(x0, y0, x0 + W, y0 + H, 4, Ui.BORDER);
		Ui.round(x0 + 1, y0 + 1, x0 + W - 1, y0 + H - 1, 3, Ui.PANEL);
		drawRect(x0 + 8, y0 + HEADER, x0 + W - 8, y0 + HEADER + 1, Ui.BORDER);
		boolean overBack = Ui.inside(mx, my, x0 + 6, y0 + 6, x0 + 50, y0 + 19);
		Ui.text("< Back", x0 + 10, y0 + 9, overBack ? Ui.TEXT : Ui.DIM);
		Ui.centered(title, x0 + W / 2, y0 + 9, Ui.TEXT);
		Ui.text(Ui.fit(about, listX1 - listX0), listX0, y0 + HEADER + 8, Ui.DIM);

		// rows (clipped to the list area)
		Row hoverRow = null;
		int sf = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
		org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
		org.lwjgl.opengl.GL11.glScissor(listX0 * sf, mc.displayHeight - listY1 * sf, (listX1 - listX0) * sf, (listY1 - listY0) * sf);
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			int y = listY0 + i * ROW - scroll;
			if (y + ROW < listY0 || y > listY1) continue;
			boolean over = Ui.inside(mx, my, listX0, Math.max(y, listY0), listX1, Math.min(y + ROW, listY1));
			if (over) hoverRow = r;
			drawRow(r, y, over, mx);
		}
		org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
		if (maxScroll() > 0) {
			int total = rows.size() * ROW, view = listY1 - listY0;
			int bh = Math.max(12, view * view / total), by = listY0 + (view - bh) * scroll / maxScroll();
			drawRect(listX1 + 2, by, listX1 + 4, by + bh, 0x99FFFFFF & Ui.accent());
		}
		if (preview != null) {
			int px0 = listX1 + 8, px1 = x0 + W - 10;
			Ui.round(px0, listY0 - 2, px1, listY1, 3, Ui.BORDER);
			Ui.round(px0 + 1, listY0 - 1, px1 - 1, listY1 - 1, 2, 0xFF0A101D);
			preview.draw(px0 + 1, listY0 - 1, px1 - 1, listY1 - 1, mx, my, pt);
		}
		String foot = hoverRow != null && !hoverRow.hint.isEmpty() ? hoverRow.hint : "Left click changes it. Right click goes back one.";
		Ui.text(Ui.fit(foot, W - 20), x0 + 10, y0 + H - 15, Ui.DIM);
		super.drawScreen(mx, my, pt);
	}

	private void drawRow(Row r, int y, boolean over, int mx) {
		if (over && r.kind != Row.Kind.NOTE) Ui.round(listX0 - 3, y, listX1 + 1, y + ROW - 1, 2, 0x22FFFFFF);
		if (r.kind == Row.Kind.NOTE) {
			Ui.text(Ui.fit(r.label, listX1 - listX0), listX0, y + 5, Ui.GOLD);
			return;
		}
		Ui.text(Ui.fit(r.label, (listX1 - listX0) / 2), listX0, y + 5, Ui.TEXT);
		int right = listX1 - 2;
		switch (r.kind) {
			case TOGGLE: {
				boolean on = r.bool.get();
				int sw = 20, sx = right - sw;
				Ui.round(sx, y + 4, right, y + 13, 2, on ? Ui.accent() : 0xFF2A3550);
				int knob = on ? right - 8 : sx + 1;
				Ui.round(knob, y + 5, knob + 7, y + 12, 1, 0xFFFFFFFF);
				break;
			}
			case CHOICE: {
				String v = r.value.get();
				int vw = Ui.width(v);
				Ui.text(">", right - 5, y + 5, over ? Ui.TEXT : Ui.DIM);
				Ui.text(v, right - 10 - vw, y + 5, Ui.TEXT);
				Ui.text("<", right - 18 - vw, y + 5, over ? Ui.TEXT : Ui.DIM);
				break;
			}
			case SLIDER: {
				int tx1 = right, tx0 = right - Math.min(90, (listX1 - listX0) / 2 - 10);
				double f = (r.num.get() - r.min) / (r.max - r.min);
				drawRect(tx0, y + 8, tx1, y + 10, 0xFF2A3550);
				drawRect(tx0, y + 8, tx0 + (int) ((tx1 - tx0) * f), y + 10, Ui.accent());
				int kx = tx0 + (int) ((tx1 - tx0) * f);
				Ui.round(kx - 3, y + 5, kx + 3, y + 13, 2, 0xFFFFFFFF);
				String v = r.fmt.of(r.num.get());
				Ui.text(v, tx0 - 6 - Ui.width(v), y + 5, Ui.TEXT);
				break;
			}
			case BUTTON: {
				String v = r.value.get();
				int bw = Ui.width(v) + 12;
				Ui.round(right - bw, y + 2, right, y + 15, 3, over ? Ui.accent() : 0xFF22304D);
				Ui.centered(v, right - bw / 2, y + 5, 0xFFFFFFFF);
				break;
			}
			default:
				break;
		}
	}

	private Row rowAt(int mx, int my) {
		if (!Ui.inside(mx, my, listX0 - 3, listY0, listX1 + 1, listY1)) return null;
		int i = (my - listY0 + scroll) / ROW;
		return i >= 0 && i < rows.size() ? rows.get(i) : null;
	}

	private double sliderFrac(Row r, int mx) {
		int right = listX1 - 2, tx0 = right - Math.min(90, (listX1 - listX0) / 2 - 10);
		return (mx - tx0) / (double) (right - tx0);
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) throws IOException {
		super.mouseClicked(mx, my, button);
		if (button == 0 && Ui.inside(mx, my, x0 + 6, y0 + 6, x0 + 50, y0 + 19)) {
			back();
			return;
		}
		Row r = rowAt(mx, my);
		if (r == null) return;
		boolean right = button == 1;
		switch (r.kind) {
			case TOGGLE: r.flip.run(); break;
			case CHOICE:
				if (right || mx < listX1 - 2 - Ui.width(r.value.get()) - 12) r.prev.run();
				else r.next.run();
				break;
			case SLIDER:
				if (right) r.nudge(-1);
				else {
					dragging = r;
					r.slideTo(sliderFrac(r, mx));
				}
				break;
			case BUTTON: if (!right) r.action.run(); break;
			default: break;
		}
		Config.save();
	}

	@Override
	protected void mouseClickMove(int mx, int my, int button, long time) {
		if (dragging != null) dragging.slideTo(sliderFrac(dragging, mx));
	}

	@Override
	protected void mouseReleased(int mx, int my, int state) {
		if (dragging != null) Config.save();
		dragging = null;
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			scroll -= Integer.signum(wheel) * ROW;
			clampScroll();
		}
	}

	@Override
	protected void keyTyped(char c, int key) throws IOException {
		if (key == 1) {   // Esc
			back();
			return;
		}
		if (key == dev.lmg.legacy.feature.Keys.MENU.getKeyCode()) {
			Config.save();
			mc.displayGuiScreen(parent instanceof HomeScreen ? ((HomeScreen) parent).parent() : parent);
			return;
		}
		super.keyTyped(c, key);
	}

	private void back() {
		Config.save();
		mc.displayGuiScreen(parent);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
