package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.feature.ItemLook;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Move, turn and resize what you hold, and watch it change in your hand while you drag.
 * The panel sits on the side away from your main hand so it never covers the item.
 */
public class ItemLookScreen extends Screen {
	private static final int PANEL_W = 150, ROW_H = 21;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private record Slider(String label, Supplier<String> value, DoubleSupplier get, DoubleConsumer set, float min, float max, float snap) {}
	private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}
	private final List<Slider> sliders = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private Slider dragging;
	private int px0, py0, py1;

	public ItemLookScreen(Screen parent) {
		super(Component.literal("Held item"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LmgConfig c = LmgConfig.get();
		sliders.clear();
		sliders.add(new Slider("Side", () -> num(c.itemX), () -> c.itemX, v -> c.itemX = (float) v, -0.6f, 0.6f, 0.02f));
		sliders.add(new Slider("Height", () -> num(c.itemY), () -> c.itemY, v -> c.itemY = (float) v, -0.6f, 0.6f, 0.02f));
		sliders.add(new Slider("Depth", () -> num(c.itemZ), () -> c.itemZ, v -> c.itemZ = (float) v, -0.6f, 0.6f, 0.02f));
		sliders.add(new Slider("Turn", () -> deg(c.itemTurn), () -> c.itemTurn, v -> c.itemTurn = (float) v, -180, 180, 4f));
		sliders.add(new Slider("Tilt", () -> deg(c.itemTilt), () -> c.itemTilt, v -> c.itemTilt = (float) v, -180, 180, 4f));
		sliders.add(new Slider("Roll", () -> deg(c.itemRoll), () -> c.itemRoll, v -> c.itemRoll = (float) v, -180, 180, 4f));
		sliders.add(new Slider("Size", () -> Math.round(c.itemSize * 100) + "%", () -> c.itemSize, v -> c.itemSize = (float) v, 0.3f, 2f, 0.04f));
	}

	private static String num(float v) {
		int n = Math.round(v * 100);
		return n > 0 ? "+" + n : String.valueOf(n);
	}

	private static String deg(float v) {
		return Math.round(v) + "°";
	}

	/** The panel goes on the side away from the hand you hold things in. */
	private boolean panelLeft() {
		return minecraft.options.mainHand().get() == HumanoidArm.RIGHT;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// nothing: you need to see your hand
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		hits.clear();
		LmgConfig c = LmgConfig.get();
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 240f));
		int primary = Theme.primary();
		int h = 40 + ROW_H + sliders.size() * ROW_H + 46;
		px0 = panelLeft() ? 8 : width - 8 - PANEL_W;
		py0 = Math.max(8, (height - h) / 2);
		int x0 = px0, x1 = px0 + PANEL_W, y0 = py0;
		py1 = y0 + h;
		Draw.roundRect(g, x0 - 1, y0 - 1, x1 + 1, py1 + 1, 7, Draw.withAlpha(0x1C2943, 0.92f * a));
		Draw.roundRect(g, x0, y0, x1, py1, 6, Draw.withAlpha(0x0C1322, 0.92f * a));
		g.text(font, "Held item", x0 + 10, y0 + 9, Draw.white(a), false);
		boolean empty = minecraft.player == null || minecraft.player.getMainHandItem().isEmpty();
		String sub = empty ? "hold something to see it" : "drag and watch your hand";
		g.text(font, sub, x0 + 10, y0 + 20, Draw.withAlpha(empty ? 0xF2C14E : 0x7C8BA3, a), false);
		g.fill(x0 + 8, y0 + 32, x1 - 8, y0 + 33, Draw.withAlpha(0x1C2943, a));

		// the look: < Big & low >
		int y = y0 + 38;
		int ty = y + (ROW_H - 2 - 8) / 2;
		boolean overLook = mouseX >= x0 + 6 && mouseX < x1 - 6 && mouseY >= y && mouseY < y + ROW_H - 2;
		if (overLook) Draw.roundRect(g, x0 + 6, y, x1 - 6, y + ROW_H - 2, 4, Draw.white(0.04f * a));
		g.text(font, "Look", x0 + 10, ty, Draw.withAlpha(0xA3B0C4, a), false);
		String v = ItemLook.label(c);
		int nx = x1 - 10 - font.width(">");
		int vx = nx - 4 - font.width(v), lx = vx - 4 - font.width("<");
		g.text(font, ">", nx, ty, Draw.withAlpha(0x7C8BA3, a), false);
		g.text(font, v, vx, ty, Draw.withAlpha(primary, a), false);
		g.text(font, "<", lx, ty, Draw.withAlpha(0x7C8BA3, a), false);
		int mid = (lx + nx) / 2;
		hits.add(new Hit(x0, y, mid, y + ROW_H - 2, () -> ItemLook.cycle(c, -1)));
		hits.add(new Hit(mid, y, x1, y + ROW_H - 2, () -> ItemLook.cycle(c, 1)));
		y += ROW_H;

		for (Slider s : sliders) {
			drawSlider(g, s, x0 + 10, y, x1 - 10, mouseX, mouseY, a, primary);
			y += ROW_H;
		}

		// Reset and Done
		int by = y + 8, bw = (PANEL_W - 22) / 2;
		boolean overReset = mouseX >= x0 + 8 && mouseX < x0 + 8 + bw && mouseY >= by && mouseY < by + 16;
		PillButton.draw(g, font, x0 + 8, by, bw, 16, "Reset", false, overReset ? 1f : 0f, a);
		hits.add(new Hit(x0 + 8, by, x0 + 8 + bw, by + 16, () -> ItemLook.Preset.VANILLA.apply(c)));
		int dx = x1 - 8 - bw;
		boolean overDone = mouseX >= dx && mouseX < x1 - 8 && mouseY >= by && mouseY < by + 16;
		PillButton.draw(g, font, dx, by, bw, 16, "Done", true, overDone ? 1f : 0f, a);
		hits.add(new Hit(dx, by, x1 - 8, by + 16, this::onClose));
		g.centeredText(font, "Right-click a slider to reset it", (x0 + x1) / 2, by + 22, Draw.withAlpha(0x56637A, a));
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void drawSlider(GuiGraphicsExtractor g, Slider s, int x0, int y, int x1, int mouseX, int mouseY, float a, int primary) {
		boolean over = mouseX >= x0 - 4 && mouseX < x1 + 4 && mouseY >= y && mouseY < y + ROW_H - 2;
		if (over || dragging == s) Draw.roundRect(g, x0 - 4, y, x1 + 4, y + ROW_H - 2, 4, Draw.white(0.04f * a));
		g.text(font, s.label, x0, y + 2, Draw.withAlpha(0xA3B0C4, a), false);
		String v = s.value.get();
		g.text(font, v, x1 - font.width(v), y + 2, Draw.white(a), false);
		int sy = y + 13;
		float t = Mth.clamp((float) ((s.get.getAsDouble() - s.min) / (s.max - s.min)), 0f, 1f);
		// zero sits in the middle for the +/- ones, so fill from there
		float zero = Mth.clamp(-s.min / (s.max - s.min), 0f, 1f);
		if (s.min >= 0) zero = Mth.clamp((1f - s.min) / (s.max - s.min), 0f, 1f);   // size: from 100%
		Draw.roundRect(g, x0, sy, x1, sy + 3, 1, Draw.withAlpha(0x2A3550, a));
		int zx = x0 + Math.round(zero * (x1 - x0)), kx = x0 + Math.round(t * (x1 - x0));
		g.fill(Math.min(zx, kx), sy, Math.max(zx, kx), sy + 3, Draw.withAlpha(primary, a));
		g.fill(zx, sy - 1, zx + 1, sy + 4, Draw.withAlpha(0x56637A, a));
		Draw.roundRect(g, kx - 3, sy - 2, kx + 3, sy + 5, 2, Draw.white(a));
	}

	private int[] sliderSpan() {
		return new int[]{px0 + 10, px0 + PANEL_W - 10};
	}

	private void setFromMouse(Slider s, double mouseX) {
		int[] span = sliderSpan();
		double t = Mth.clamp((mouseX - span[0]) / (double) (span[1] - span[0]), 0, 1);
		double v = s.min + t * (s.max - s.min);
		// a little stick at the default so it's easy to get back to
		double home = s.min >= 0 ? 1 : 0;
		if (Math.abs(v - home) < s.snap) v = home;
		s.set.accept(v);
	}

	private Slider sliderAt(double mx, double my) {
		int y = py0 + 38 + ROW_H;
		for (Slider s : sliders) {
			if (mx >= px0 && mx < px0 + PANEL_W && my >= y && my < y + ROW_H - 2) return s;
			y += ROW_H;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Slider s = sliderAt(event.x(), event.y());
		if (s != null) {
			if (event.button() == 1) s.set.accept(s.min >= 0 ? 1 : 0);
			else {
				dragging = s;
				setFromMouse(s, event.x());
			}
			return true;
		}
		for (Hit h : List.copyOf(hits)) {
			if (event.x() >= h.x0 && event.x() < h.x1 && event.y() >= h.y0 && event.y() < h.y1) {
				h.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			setFromMouse(dragging, event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Slider s = sliderAt(mouseX, mouseY);
		if (s == null) return false;
		double stepSize = (s.max - s.min) / 60.0;
		s.set.accept(Mth.clamp(s.get.getAsDouble() + Math.signum(scrollY) * stepSize, s.min, s.max));
		return true;
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

	/** Used by tests: a one-line summary of the current look. */
	public static String describe() {
		LmgConfig c = LmgConfig.get();
		return String.format(Locale.ROOT, "%s x=%.2f y=%.2f z=%.2f turn=%.0f tilt=%.0f roll=%.0f size=%.2f",
				ItemLook.label(c), c.itemX, c.itemY, c.itemZ, c.itemTurn, c.itemTilt, c.itemRoll, c.itemSize);
	}
}
