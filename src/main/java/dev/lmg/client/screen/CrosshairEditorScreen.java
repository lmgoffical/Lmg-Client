package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.feature.Visuals;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.MenuSnow;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draw your own crosshair on a 15 x 15 grid. Left click draws, right click rubs out, and with
 * mirroring on you only have to draw one corner. The previews on the right show it at real size
 * on sky, grass and a dark cave, spinning if you've turned spin on.
 */
public class CrosshairEditorScreen extends Screen {
	private static final int N = LmgConfig.DRAW_SIZE;

	private enum Mirror {
		OFF("Off"), SIDES("Left / right"), FOUR("4 ways");
		final String label;

		Mirror(String label) {
			this.label = label;
		}
	}

	private enum Template {
		PLUS("Plus"), GAP_PLUS("Gap plus"), X("X"), CIRCLE("Circle"), DOT("Dot"), BOX("Box"), T("T"), EMPTY("Empty");
		final String label;

		Template(String label) {
			this.label = label;
		}
	}

	private static Mirror mirror = Mirror.FOUR;
	private static Template template = Template.GAP_PLUS;

	private final Screen parent;
	private final int[] before;
	private final LmgConfig.Crosshair shapeBefore;
	private final long openedAt = Util.getMillis();
	private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}
	private final List<Hit> hits = new ArrayList<>();
	/** While dragging: true draws, false rubs out, null = not painting. */
	private Boolean painting;
	private int lastX = -1, lastY = -1;
	private int cell, gx0, gy0;
	/** Little undo history (Ctrl+Z). */
	private final List<int[]> undo = new ArrayList<>();

	public CrosshairEditorScreen(Screen parent) {
		super(Component.literal("Draw your crosshair"));
		this.parent = parent;
		LmgConfig c = LmgConfig.get();
		this.before = c.crosshairPixels.clone();
		this.shapeBefore = c.crosshair;
	}

	private int[] pixels() {
		return LmgConfig.get().crosshairPixels;
	}

	private boolean get(int x, int y) {
		return (pixels()[y] >> x & 1) != 0;
	}

	private void set(int x, int y, boolean on) {
		int[] p = pixels();
		if (on) p[y] |= 1 << x;
		else p[y] &= ~(1 << x);
	}

	/** Sets a pixel and its mirror images. */
	private void paint(int x, int y, boolean on) {
		set(x, y, on);
		if (mirror != Mirror.OFF) set(N - 1 - x, y, on);
		if (mirror == Mirror.FOUR) {
			set(x, N - 1 - y, on);
			set(N - 1 - x, N - 1 - y, on);
		}
	}

	private void remember() {
		undo.add(pixels().clone());
		if (undo.size() > 40) undo.remove(0);
	}

	private void applyTemplate(Template t) {
		remember();
		int[] p = pixels();
		java.util.Arrays.fill(p, 0);
		int m = N / 2;
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				int dx = x - m, dy = y - m;
				boolean on = switch (t) {
					case PLUS -> (dx == 0 && Math.abs(dy) <= 5) || (dy == 0 && Math.abs(dx) <= 5);
					case GAP_PLUS -> (dx == 0 && Math.abs(dy) >= 2 && Math.abs(dy) <= 5)
							|| (dy == 0 && Math.abs(dx) >= 2 && Math.abs(dx) <= 5) || (dx == 0 && dy == 0);
					case X -> Math.abs(dx) == Math.abs(dy) && Math.abs(dx) >= 2 && Math.abs(dx) <= 4;
					case CIRCLE -> {
						double d = Math.sqrt(dx * dx + dy * dy);
						yield Math.abs(d - 4.6) < 0.55 || (dx == 0 && dy == 0);
					}
					case DOT -> Math.abs(dx) <= 1 && Math.abs(dy) <= 1;
					case BOX -> Math.max(Math.abs(dx), Math.abs(dy)) == 4 && Math.min(Math.abs(dx), Math.abs(dy)) >= 2
							|| (dx == 0 && dy == 0);
					case T -> (dy == 0 && Math.abs(dx) >= 2 && Math.abs(dx) <= 5) || (dx == 0 && dy >= 2 && dy <= 5) || (dx == 0 && dy == 0);
					case EMPTY -> false;
				};
				if (on) p[y] |= 1 << x;
			}
		}
	}

	private static <E extends Enum<E>> E cycle(E[] values, E cur, int dir) {
		return values[Math.floorMod(cur.ordinal() + dir, values.length)];
	}

	// ---- drawing ---------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick);
			MenuSnow.greyBackdrop(g, width, height);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		hits.clear();
		LmgConfig c = LmgConfig.get();
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 240f));
		int primary = Theme.primary();

		cell = Math.max(6, Math.min(11, (height - 86) / N));
		int gridW = cell * N;
		int sideW = 150;
		int cardW = 12 + gridW + 14 + sideW + 12, cardH = Math.max(gridW + 66, 196);
		int x0 = (width - cardW) / 2, y0 = Math.max(4, (height - cardH) / 2 + Math.round((1 - a) * 8));
		int x1 = x0 + cardW, y1 = y0 + cardH;
		Draw.roundRect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 8, Draw.withAlpha(0x1C2943, a));
		Draw.roundRect(g, x0, y0, x1, y1, 7, Draw.withAlpha(0x0C1322, 0.97f * a));
		g.text(font, "Draw your crosshair", x0 + 12, y0 + 10, Draw.white(a), false);
		g.text(font, "Left click draws, right click rubs out", x0 + 12, y0 + 21, Draw.withAlpha(0x7C8BA3, a), false);

		// the grid
		gx0 = x0 + 12;
		gy0 = y0 + 38;
		g.fill(gx0 - 1, gy0 - 1, gx0 + gridW + 1, gy0 + gridW + 1, Draw.withAlpha(0x1C2943, a));
		int col = 0xFF000000 | c.crosshairRgb;
		int hx = (mouseX - gx0) / Math.max(1, cell), hy = (mouseY - gy0) / Math.max(1, cell);
		boolean inGrid = mouseX >= gx0 && mouseY >= gy0 && hx < N && hy < N;
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				int cx = gx0 + x * cell, cy = gy0 + y * cell;
				boolean centre = x == N / 2 && y == N / 2;
				int bg = ((x + y) & 1) == 0 ? 0x141C2E : 0x111829;
				if (centre) bg = 0x22304C;
				g.fill(cx, cy, cx + cell, cy + cell, Draw.withAlpha(bg, a));
				if (get(x, y)) g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, Draw.withAlpha(col, a));
			}
		}
		// guides through the middle
		int mid = gx0 + (N / 2) * cell;
		g.fill(mid, gy0, mid + cell, gy0 + 1, Draw.withAlpha(primary, 0.5f * a));
		g.fill(gx0, mid, gx0 + 1, mid + cell, Draw.withAlpha(primary, 0.5f * a));
		if (inGrid) {
			// show where the mirror copies go too
			for (int[] p : mirrored(hx, hy)) {
				int cx = gx0 + p[0] * cell, cy = gy0 + p[1] * cell;
				int o = Draw.withAlpha(0xFFFFFF, 0.6f * a);
				g.fill(cx, cy, cx + cell, cy + 1, o);
				g.fill(cx, cy + cell - 1, cx + cell, cy + cell, o);
				g.fill(cx, cy, cx + 1, cy + cell, o);
				g.fill(cx + cell - 1, cy, cx + cell, cy + cell, o);
			}
		}

		// right side: previews, then options
		int sx0 = gx0 + gridW + 14, sx1 = x1 - 12;
		int tile = (sx1 - sx0 - 8) / 3;
		int ty = gy0;
		int[][] backs = {{0x86B6EE, 0xCFE3F7}, {0x5E9E3A, 0x4A8030}, {0x15171D, 0x1E2129}};
		String[] names = {"sky", "grass", "cave"};
		for (int i = 0; i < 3; i++) {
			int tx = sx0 + i * (tile + 4);
			g.fillGradient(tx, ty, tx + tile, ty + tile, Draw.withAlpha(backs[i][0], a), Draw.withAlpha(backs[i][1], a));
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(tx + tile / 2f, ty + tile / 2f);
			float s = LmgConfig.CROSSHAIR_SIZES[c.crosshairSize];
			pose.scale(s, s);
			float spin = LmgConfig.SPIN_SPEEDS[c.crosshairSpin];
			if (spin > 0) pose.rotate((float) Math.toRadians((Util.getMillis() % 360_000L) / 1000.0 * spin % 360.0));
			Visuals.drawPixels(g, pixels(), Draw.withAlpha(c.crosshairRgb, a), c.crosshairOutline ? Draw.withAlpha(0x000000, 0.69f * a) : 0);
			pose.popMatrix();
			g.text(font, names[i], tx + 2, ty + tile + 2, Draw.withAlpha(0x56637A, a), false);
		}

		int ry = ty + tile + 14;
		ry = choiceRow(g, sx0, sx1, ry, "Mirror", mirror.label, mouseX, mouseY, a, primary,
				() -> mirror = cycle(Mirror.values(), mirror, -1), () -> mirror = cycle(Mirror.values(), mirror, 1));
		ry = choiceRow(g, sx0, sx1, ry, "Start from", template.label, mouseX, mouseY, a, primary,
				() -> applyTemplate(template = cycle(Template.values(), template, -1)),
				() -> applyTemplate(template = cycle(Template.values(), template, 1)));
		ry = choiceRow(g, sx0, sx1, ry, "Spin", LmgConfig.SPIN_LABELS[c.crosshairSpin], mouseX, mouseY, a, primary,
				() -> c.crosshairSpin = Math.floorMod(c.crosshairSpin - 1, LmgConfig.SPIN_SPEEDS.length),
				() -> c.crosshairSpin = Math.floorMod(c.crosshairSpin + 1, LmgConfig.SPIN_SPEEDS.length));
		ry = choiceRow(g, sx0, sx1, ry, "Dark edge", c.crosshairOutline ? "On" : "Off", mouseX, mouseY, a, primary,
				() -> c.crosshairOutline = !c.crosshairOutline, () -> c.crosshairOutline = !c.crosshairOutline);
		// colour: label + swatch, opens the picker
		boolean overCol = mouseX >= sx0 - 2 && mouseX < sx1 + 2 && mouseY >= ry && mouseY < ry + 14;
		if (overCol) Draw.roundRect(g, sx0 - 2, ry, sx1 + 2, ry + 14, 3, Draw.white(0.04f * a));
		g.text(font, "Colour", sx0, ry + 3, Draw.withAlpha(0xA3B0C4, a), false);
		Draw.roundRect(g, sx1 - 28, ry + 2, sx1, ry + 12, 2, Draw.withAlpha(c.crosshairRgb, a));
		hits.add(new Hit(sx0, ry, sx1, ry + 14, () -> minecraft.gui.setScreen(new ColorPickerScreen(this, "Crosshair colour",
				c.crosshairRgb, v -> c.crosshairRgb = v, () -> c.crosshairRgb = 0xFFFFFF))));

		// buttons along the bottom
		int by = y1 - 24;
		int bw = (sx1 - sx0 - 8) / 3;
		button(g, sx0, by, bw, "Cancel", false, mouseX, mouseY, a, this::cancel);
		button(g, sx0 + bw + 4, by, bw, "Clear", false, mouseX, mouseY, a, () -> applyTemplate(Template.EMPTY));
		button(g, sx1 - bw, by, bw, "Use it", true, mouseX, mouseY, a, this::useIt);
		g.text(font, "Ctrl+Z undoes", gx0, gy0 + gridW + 6, Draw.withAlpha(0x56637A, a), false);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private int choiceRow(GuiGraphicsExtractor g, int x0, int x1, int y, String label, String value, int mouseX, int mouseY,
						  float a, int primary, Runnable prev, Runnable next) {
		boolean over = mouseX >= x0 - 2 && mouseX < x1 + 2 && mouseY >= y && mouseY < y + 14;
		if (over) Draw.roundRect(g, x0 - 2, y, x1 + 2, y + 14, 3, Draw.white(0.04f * a));
		g.text(font, label, x0, y + 3, Draw.withAlpha(0xA3B0C4, a), false);
		int nx = x1 - font.width(">");
		int vx = nx - 4 - font.width(value), lx = vx - 4 - font.width("<");
		g.text(font, ">", nx, y + 3, Draw.withAlpha(0x7C8BA3, a), false);
		g.text(font, value, vx, y + 3, Draw.white(a), false);
		g.text(font, "<", lx, y + 3, Draw.withAlpha(0x7C8BA3, a), false);
		int mid = (lx + nx) / 2;
		hits.add(new Hit(x0, y, mid, y + 14, prev));
		hits.add(new Hit(mid, y, x1, y + 14, next));
		return y + 16;
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, String text, boolean primary, int mouseX, int mouseY, float a, Runnable action) {
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
		PillButton.draw(g, font, x, y, w, 16, text, primary, over ? 1f : 0f, a);
		hits.add(new Hit(x, y, x + w, y + 16, action));
	}

	private List<int[]> mirrored(int x, int y) {
		List<int[]> out = new ArrayList<>();
		out.add(new int[]{x, y});
		if (mirror != Mirror.OFF) out.add(new int[]{N - 1 - x, y});
		if (mirror == Mirror.FOUR) {
			out.add(new int[]{x, N - 1 - y});
			out.add(new int[]{N - 1 - x, N - 1 - y});
		}
		return out;
	}

	// ---- input -----------------------------------------------------------------------------------

	private boolean paintAt(double mx, double my) {
		if (cell <= 0 || mx < gx0 || my < gy0) return false;
		int x = (int) ((mx - gx0) / cell), y = (int) ((my - gy0) / cell);
		if (x >= N || y >= N) return false;
		if (painting != null && (x != lastX || y != lastY)) {
			// fill in between so fast strokes don't leave gaps
			if (lastX >= 0) {
				int steps = Math.max(Math.abs(x - lastX), Math.abs(y - lastY));
				for (int i = 1; i < steps; i++) {
					paint(lastX + Math.round((x - lastX) * i / (float) steps), lastY + Math.round((y - lastY) * i / (float) steps), painting);
				}
			}
			paint(x, y, painting);
			lastX = x;
			lastY = y;
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (cell > 0 && event.x() >= gx0 && event.y() >= gy0 && event.x() < gx0 + cell * N && event.y() < gy0 + cell * N) {
			remember();
			painting = event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
			lastX = lastY = -1;
			LmgConfig.get().crosshair = LmgConfig.Crosshair.DRAWN;
			paintAt(event.x(), event.y());
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
		if (painting != null) {
			paintAt(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		painting = null;
		lastX = lastY = -1;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_Z && event.hasControlDownWithQuirk() && !undo.isEmpty()) {
			int[] last = undo.remove(undo.size() - 1);
			System.arraycopy(last, 0, pixels(), 0, N);
			return true;
		}
		return super.keyPressed(event);
	}

	private void useIt() {
		LmgConfig c = LmgConfig.get();
		c.crosshair = LmgConfig.Crosshair.DRAWN;
		LmgConfig.save();
		minecraft.gui.setScreen(parent);
	}

	private void cancel() {
		LmgConfig c = LmgConfig.get();
		System.arraycopy(before, 0, c.crosshairPixels, 0, N);
		c.crosshair = shapeBefore;
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void onClose() {
		// Esc keeps what you drew (same as "Use it" if you drew anything)
		LmgConfig.save();
		minecraft.gui.setScreen(parent);
	}
}
