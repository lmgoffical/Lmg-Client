package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One line in the Lmg menu. Three kinds:
 *  - toggle: an on/off switch on the right
 *  - choice: "‹ value ›" - click ‹ for the previous option, › (or the row) for the next
 *  - button: runs an action
 */
public class SettingRow extends AbstractWidget {
	private enum Kind { TOGGLE, CHOICE, BUTTON, DROPDOWN, KEY, COLOR, SLIDER }

	// same text colours as the website: muted labels, bright values, faint arrows
	private static final int LABEL = 0xA3B0C4;
	private static final int VALUE = 0xE8EDF5;
	private static final int DIM = 0x7C8BA3;

	private final Kind kind;
	private final Supplier<Boolean> isOn;
	private final Supplier<String> value;
	private Runnable next;
	private Runnable previous;
	private final IntSupplier swatch;
	private long appearAt;
	private float hover;
	private float knob = -1;
	private long lastFrame = -1;
	private int prevArrowX0 = Integer.MIN_VALUE, prevArrowX1 = Integer.MIN_VALUE;

	// dropdown rows
	private java.util.List<String> options = java.util.List.of();
	private java.util.List<Integer> optionColors = java.util.List.of();
	private java.util.function.IntSupplier selectedIndex;
	private java.util.function.IntConsumer pick;
	private Consumer<SettingRow> opener;
	private int boxX0, boxY0, boxX1, boxY1;
	private String hint = "";
	private java.util.function.BooleanSupplier waiting = () -> false;

	// "more options" (the little gear after the name), FPS cost, and where it lives (for search)
	private Runnable more;
	private int moreX0 = Integer.MIN_VALUE, moreX1 = Integer.MIN_VALUE, moreY0, moreY1;
	private int cost;
	private String where = "";
	private String keywords = "";
	private int mouseX, mouseY;

	// slider rows
	private java.util.function.DoubleSupplier sGet;
	private java.util.function.DoubleConsumer sSet;
	private double sMin, sMax, sStep;
	private java.util.function.DoubleFunction<String> sFormat;
	private int trackX0, trackX1;
	private boolean sliding;

	private SettingRow(Kind kind, String label, Supplier<Boolean> isOn, Supplier<String> value,
					   Runnable next, Runnable previous, IntSupplier swatch) {
		super(0, 0, 100, 14, Component.literal(label));
		this.kind = kind;
		this.isOn = isOn;
		this.value = value;
		this.next = next;
		this.previous = previous;
		this.swatch = swatch;
	}

	public static SettingRow toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		Runnable flip = () -> set.accept(!get.get());
		return new SettingRow(Kind.TOGGLE, label, get, null, flip, flip, null);
	}

	public static SettingRow choice(String label, Supplier<String> value, Runnable next, Runnable previous) {
		return new SettingRow(Kind.CHOICE, label, null, value, next, previous, null);
	}

	public static SettingRow choice(String label, Supplier<String> value, Runnable next, Runnable previous, IntSupplier swatch) {
		return new SettingRow(Kind.CHOICE, label, null, value, next, previous, swatch);
	}

	/**
	 * A dropdown: shows the current option in a box; clicking opens a list (drawn by the screen).
	 * @param colors one preview color per option (or null)
	 */
	public static SettingRow dropdown(String label, java.util.List<String> options, java.util.List<Integer> colors,
									  java.util.function.IntSupplier selected, java.util.function.IntConsumer pick) {
		SettingRow row = new SettingRow(Kind.DROPDOWN, label, null, () -> options.get(selected.getAsInt()), () -> {}, () -> {}, null);
		row.options = options;
		row.optionColors = colors == null ? java.util.List.of() : colors;
		row.selectedIndex = selected;
		row.pick = pick;
		return row;
	}

	/** The screen sets this so the row can ask it to open the list. */
	public SettingRow onOpen(Consumer<SettingRow> opener) {
		this.opener = opener;
		return this;
	}

	public java.util.List<String> options() { return options; }
	public java.util.List<Integer> optionColors() { return optionColors; }
	public int selected() { return selectedIndex.getAsInt(); }
	public void choose(int index) { pick.accept(index); LmgConfig.save(); }
	/** {x0, y0, x1, y1} of the value box, as last drawn. */
	public int[] box() { return new int[]{boxX0, boxY0, boxX1, boxY1}; }

	private int swatchFor(int index) {
		return index < optionColors.size() ? optionColors.get(index) : -1;
	}

	/** A key binding: shows the key in a little keycap; clicking it waits for a new key. */
	public static SettingRow key(String label, Supplier<String> keyName, java.util.function.BooleanSupplier waiting, Runnable onPress) {
		SettingRow row = new SettingRow(Kind.KEY, label, null, keyName, onPress, onPress, null);
		row.waiting = waiting;
		return row;
	}

	/** A colour: shows a swatch; clicking opens the colour picker. {@code rainbow} may be null. */
	public static SettingRow color(String label, IntSupplier rgb, java.util.function.BooleanSupplier rainbow, Runnable open) {
		SettingRow row = new SettingRow(Kind.COLOR, label, null, () -> "", open, open, rgb);
		row.waiting = rainbow == null ? () -> false : rainbow;
		return row;
	}

	/**
	 * A slider for a number between {@code min} and {@code max}, snapping to {@code step}.
	 * Click or drag along it; scroll over it (or right-click for one step back) for fine changes.
	 */
	public static SettingRow slider(String label, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set,
									double min, double max, double step, java.util.function.DoubleFunction<String> format) {
		SettingRow row = new SettingRow(Kind.SLIDER, label, null, () -> format.apply(get.getAsDouble()), () -> {}, () -> {}, null);
		row.sGet = get;
		row.sSet = set;
		row.sMin = min;
		row.sMax = max;
		row.sStep = step;
		row.sFormat = format;
		row.next = () -> row.nudge(1);
		row.previous = () -> row.nudge(-1);
		return row;
	}

	private void nudge(int dir) {
		sSet.accept(clampStep(sGet.getAsDouble() + dir * sStep));
	}

	private double clampStep(double v) {
		v = Math.max(sMin, Math.min(sMax, v));
		if (sStep > 0) v = sMin + Math.round((v - sMin) / sStep) * sStep;
		return Math.max(sMin, Math.min(sMax, v));
	}

	private void slideTo(double mouseX) {
		if (trackX1 <= trackX0) return;
		double t = Math.max(0, Math.min(1, (mouseX - trackX0) / (double) (trackX1 - trackX0)));
		sSet.accept(clampStep(sMin + t * (sMax - sMin)));
	}

	/** Adds the little gear after the name; clicking it opens more options for this setting. */
	public SettingRow withMore(Runnable openMore) {
		this.more = openMore;
		return this;
	}

	public boolean hasMore() {
		return more != null;
	}

	/** True while the mouse is over the gear (the footer says "More options"). */
	public boolean overMore() {
		return more != null && isHovered && mouseX >= moreX0 && mouseX < moreX1 && mouseY >= moreY0 && mouseY < moreY1;
	}

	/** How much this costs in FPS: 1 a little, 2 some, 3 a lot. Shown as little bars after the name. */
	public SettingRow withCost(int level) {
		this.cost = Math.max(0, Math.min(3, level));
		return this;
	}

	public int cost() {
		return cost;
	}

	/** "Visuals > Effects": shown in search results so you know where it lives. */
	public SettingRow where(String where) {
		this.where = where == null ? "" : where;
		return this;
	}

	public String whereLabel() {
		return where;
	}

	/** Extra words search should find this by ("fov" for zoom...). */
	public SettingRow keywords(String words) {
		this.keywords = words == null ? "" : words;
		return this;
	}

	/** Matches text that isn't a row (a page's name), the same way rows are matched. */
	public record Probe(String text) {
		public boolean matches(String query) {
			String hay = text.toLowerCase(java.util.Locale.ROOT).replace("colour", "color").replace("grey", "gray");
			for (String word : query.toLowerCase(java.util.Locale.ROOT).replace("colour", "color").replace("grey", "gray").split("\\s+")) {
				if (!word.isEmpty() && !hay.contains(word)) return false;
			}
			return true;
		}
	}

	/** Does this row match a search (name, hint or keywords)? */
	public boolean matches(String query) {
		String hay = (getMessage().getString() + " " + hint + " " + keywords + " " + where).toLowerCase(java.util.Locale.ROOT)
				.replace("colour", "color").replace("grey", "gray");
		for (String word : query.toLowerCase(java.util.Locale.ROOT).replace("colour", "color").replace("grey", "gray").split("\\s+")) {
			if (!word.isEmpty() && !hay.contains(word)) return false;
		}
		return true;
	}

	/**
	 * The gear and cost bars, right after the name. {@code labelX}/{@code labelY} are where the
	 * name was drawn.
	 */
	private void drawExtras(GuiGraphicsExtractor g, Font font, int labelX, int labelY, float appear) {
		int x = labelX + font.width(getMessage()) + 5;
		if (more != null) {
			boolean over = isHovered && mouseX >= x - 2 && mouseX < x + 9 && mouseY >= getY() && mouseY < getY() + height;
			int col = Draw.withAlpha(over ? MenuSkin.accent() : DIM, appear * (0.55f + 0.45f * Math.max(hover, over ? 1f : 0f)));
			gear(g, x, labelY - 1, col);
			moreX0 = x - 2;
			moreX1 = x + 9;
			moreY0 = getY();
			moreY1 = getY() + height;
			x += 11;
		} else {
			moreX0 = moreX1 = Integer.MIN_VALUE;
		}
		if (cost > 0) {
			// three little bars, like phone signal: the more that light up, the heavier it is
			int[] cols = {0x6BD58A, 0xF2C14E, 0xFF6B6B};
			int lit = cols[cost - 1];
			for (int i = 0; i < 3; i++) {
				int bh = 2 + i * 2;
				int bx = x + i * 3;
				g.fill(bx, labelY + 7 - bh, bx + 2, labelY + 7, Draw.withAlpha(i < cost ? lit : 0x3A4358, appear * 0.9f));
			}
		}
	}

	/** A 7x7 gear. */
	private static void gear(GuiGraphicsExtractor g, int x, int y, int col) {
		String[] rows = {"..X.X..", ".XXXXX.", "XX...XX", ".X...X.", "XX...XX", ".XXXXX.", "..X.X.."};
		for (int r = 0; r < rows.length; r++) {
			for (int c = 0; c < rows[r].length(); c++) {
				if (rows[r].charAt(c) == 'X') g.fill(x + c, y + r, x + c + 1, y + r + 1, col);
			}
		}
	}

	/** A short, plain-English explanation shown at the bottom of the menu while you hover the row. */
	public SettingRow withHint(String hint) {
		this.hint = hint == null ? "" : hint;
		return this;
	}

	public String hint() {
		return hint;
	}

	public boolean hovered() {
		return isHovered;
	}

	/** A button whose text can change ("Get mine" -> "#7 Founder"). */
	public static SettingRow button(String label, Supplier<String> action, Runnable onPress) {
		return new SettingRow(Kind.BUTTON, label, null, action, onPress, onPress, null);
	}

	public static SettingRow button(String label, String action, Runnable onPress) {
		return new SettingRow(Kind.BUTTON, label, null, () -> action, onPress, onPress, null);
	}

	public SettingRow at(int x, int y, int width, int height, long appearAt) {
		setPosition(x, y);
		setWidth(width);
		setHeight(height);
		this.appearAt = appearAt;
		return this;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : (now - lastFrame) / 1000f;
		lastFrame = now;
		hover = Draw.approach(hover, isHoveredOrFocused() ? 1f : 0f, 16f * Theme.speed(), dt);
		float appear = Draw.easeOutCubic(Theme.progress(appearAt, 260f));
		if (appear <= 0.01f) return;

		Font font = Minecraft.getInstance().font;
		int x = getX(), y = getY() + Math.round((1 - appear) * 4);
		if (kind == Kind.KEY) {
			drawKey(g, font, x, y, appear);
			return;
		}
		if (kind == Kind.COLOR) {
			drawColor(g, font, x, y, appear);
			return;
		}
		if (kind == Kind.SLIDER) {
			drawSlider(g, font, x, y, appear);
			return;
		}
		switch (MenuSkin.style()) {
			case SKEET -> drawSkeet(g, font, x, y, mouseX, appear, dt);
			case NEVERLOSE -> drawNeverlose(g, font, x, y, mouseX, appear, dt);
			default -> drawLmg(g, font, x, y, mouseX, appear, dt);
		}
	}

	private boolean on() {
		return isOn != null && isOn.get();
	}

	private void animateKnob(float dt) {
		boolean on = on();
		if (knob < 0) knob = on ? 1 : 0;
		knob = Draw.approach(knob, on ? 1f : 0f, 18f * Theme.speed(), dt);
	}

	// ---- colour rows (all styles): a swatch that opens the picker ----------------------------------

	private void drawColor(GuiGraphicsExtractor g, Font font, int x, int y, float appear) {
		LmgConfig.MenuStyle style = MenuSkin.style();
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int right = x + width - 6;
		int labelX = style == LmgConfig.MenuStyle.SKEET ? x + 18 : x + 6;
		int labelRgb = switch (style) {
			case SKEET -> MenuSkin.SK_TEXT;
			case NEVERLOSE -> MenuSkin.NL_TEXT;
			default -> LABEL;
		};
		if (style == LmgConfig.MenuStyle.LMG && hover > 0.01f) Draw.roundRect(g, x, y, x + width, y + height, 4, Draw.white(0.045f * hover * appear));
		g.text(font, getMessage(), labelX, textY, Draw.withAlpha(Draw.lerpRgb(labelRgb, 0xFFFFFF, hover), appear), false);
		drawExtras(g, font, labelX, textY, appear);
		int sw1 = right + 2, sw0 = sw1 - 30, sy0 = y + 2, sy1 = y + height - 2;
		// in narrow columns the swatch gets smaller rather than running into the name
		int labelEnd = labelX + font.width(getMessage()) + 4 + (more != null ? 11 : 0) + (cost > 0 ? 10 : 0);
		if (sw0 < labelEnd) sw0 = Math.min(sw1 - 12, labelEnd);
		boolean rainbow = waiting.getAsBoolean();
		int border = Draw.lerpRgb(0x1C2943, MenuSkin.accent(), hover);
		if (style == LmgConfig.MenuStyle.SKEET) g.fill(sw0 - 1, sy0 - 1, sw1 + 1, sy1 + 1, Draw.withAlpha(0x000000, appear));
		else Draw.roundRect(g, sw0 - 1, sy0 - 1, sw1 + 1, sy1 + 1, 3, Draw.withAlpha(border, appear));
		if (rainbow) {
			for (int i = 0; i < sw1 - sw0; i++) {
				int c = java.awt.Color.HSBtoRGB((i / 30f + Util.getMillis() / 3000f) % 1f, 0.6f, 1f) & 0xFFFFFF;
				g.fill(sw0 + i, sy0, sw0 + i + 1, sy1, Draw.withAlpha(c, appear));
			}
		} else if (style == LmgConfig.MenuStyle.SKEET) {
			g.fill(sw0, sy0, sw1, sy1, Draw.withAlpha(swatch.getAsInt(), appear));
		} else {
			Draw.roundRect(g, sw0, sy0, sw1, sy1, 2, Draw.withAlpha(swatch.getAsInt(), appear));
		}
		String edit = "edit";
		if (sw0 - 6 - font.width(edit) > labelEnd) g.text(font, edit, sw0 - 6 - font.width(edit), textY, Draw.withAlpha(DIM, appear * (0.4f + 0.6f * hover)), false);
	}

	// ---- slider rows (all styles) ----------------------------------------------------------------

	private void drawSlider(GuiGraphicsExtractor g, Font font, int x, int y, float appear) {
		LmgConfig.MenuStyle style = MenuSkin.style();
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int right = x + width - 6;
		int labelX = style == LmgConfig.MenuStyle.SKEET ? x + 18 : x + 6;
		int labelRgb = switch (style) {
			case SKEET -> MenuSkin.SK_TEXT;
			case NEVERLOSE -> MenuSkin.NL_TEXT;
			default -> LABEL;
		};
		if (style != LmgConfig.MenuStyle.SKEET && hover > 0.01f) {
			int bg = style == LmgConfig.MenuStyle.NEVERLOSE ? Draw.withAlpha(MenuSkin.NL_HOVER, hover * appear) : Draw.white(0.045f * hover * appear);
			Draw.roundRect(g, x, y, x + width, y + height, 4, bg);
		}
		g.text(font, getMessage(), labelX, textY, Draw.withAlpha(Draw.lerpRgb(labelRgb, 0xFFFFFF, hover), appear), false);
		drawExtras(g, font, labelX, textY, appear);
		String v = value.get();
		int trackW = Math.max(40, Math.min(78, width / 3));
		trackX1 = right;
		trackX0 = right - trackW;
		int vx = trackX0 - 6 - font.width(v);
		g.text(font, v, vx, textY, Draw.withAlpha(VALUE, appear), false);
		int ty = y + height / 2 - 1;
		double t = (sGet.getAsDouble() - sMin) / Math.max(1e-9, sMax - sMin);
		t = Math.max(0, Math.min(1, t));
		int kx = trackX0 + (int) Math.round(t * (trackX1 - trackX0));
		int acc = style == LmgConfig.MenuStyle.LMG ? Theme.primary() : MenuSkin.accent();
		if (style == LmgConfig.MenuStyle.SKEET) {
			g.fill(trackX0 - 1, ty - 2, trackX1 + 1, ty + 4, Draw.withAlpha(0x000000, appear));
			g.fill(trackX0, ty - 1, trackX1, ty + 3, Draw.withAlpha(0x2A2A2A, appear));
			g.fillGradient(trackX0, ty - 1, kx, ty + 3, Draw.withAlpha(acc, appear), Draw.withAlpha(Draw.lerpRgb(0x000000, acc, 0.6f), appear));
		} else {
			Draw.roundRect(g, trackX0, ty, trackX1, ty + 3, 1, Draw.withAlpha(0x2A3550, appear));
			Draw.roundRect(g, trackX0, ty, Math.max(trackX0 + 2, kx), ty + 3, 1, Draw.withAlpha(acc, appear));
			Draw.roundRect(g, kx - 3, ty - 2, kx + 3, ty + 5, 2, Draw.withAlpha(0xFFFFFF, appear));
		}
	}

	/** Sliders also follow the mouse while you drag. */
	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (kind == Kind.SLIDER && sliding) slideTo(event.x());
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		if (kind == Kind.SLIDER && sliding) {
			sliding = false;
			LmgConfig.save();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (kind == Kind.SLIDER && isHovered && scrollY != 0) {
			nudge(scrollY > 0 ? 1 : -1);
			return true;
		}
		return false;
	}

	// ---- key binding rows (all styles) -----------------------------------------------------------

	private void drawKey(GuiGraphicsExtractor g, Font font, int x, int y, float appear) {
		LmgConfig.MenuStyle style = MenuSkin.style();
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int right = x + width - 6;
		int labelX = style == LmgConfig.MenuStyle.SKEET ? x + 18 : x + 6;
		int labelRgb = switch (style) {
			case SKEET -> MenuSkin.SK_TEXT;
			case NEVERLOSE -> MenuSkin.NL_TEXT;
			default -> LABEL;
		};
		if (style == LmgConfig.MenuStyle.LMG && hover > 0.01f) Draw.roundRect(g, x, y, x + width, y + height, 4, Draw.white(0.045f * hover * appear));
		g.text(font, getMessage(), labelX, textY, Draw.withAlpha(Draw.lerpRgb(labelRgb, 0xFFFFFF, hover), appear), false);
		boolean wait = waiting.getAsBoolean();
		String v = wait ? "press a key" : value.get();
		int kw = Math.max(22, font.width(v) + 10);
		int kx1 = right + 2, kx0 = kx1 - kw, ky0 = y + 1, ky1 = y + height - 1;
		int acc = MenuSkin.accent();
		float pulse = wait ? 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 120.0) : 0f;
		switch (style) {
			case SKEET -> {
				g.fill(kx0, ky0, kx1, ky1, Draw.withAlpha(0x000000, appear));
				g.fill(kx0 + 1, ky0 + 1, kx1 - 1, ky1 - 1, Draw.withAlpha(wait ? Draw.lerpRgb(0x262626, acc, 0.3f * pulse) : Draw.lerpRgb(0x1F1F1F, 0x2A2A2A, hover), appear));
			}
			case NEVERLOSE -> {
				Draw.roundRect(g, kx0 - 1, ky0 - 1, kx1 + 1, ky1 + 1, 4, Draw.withAlpha(wait ? acc : MenuSkin.NL_FIELD_BORDER & 0xFFFFFF, appear));
				Draw.roundRect(g, kx0, ky0, kx1, ky1, 3, Draw.withAlpha(MenuSkin.NL_FIELD & 0xFFFFFF, appear));
			}
			default -> {
				// a little keycap: lighter top, darker lip at the bottom
				Draw.roundRect(g, kx0, ky0, kx1, ky1, 3, Draw.withAlpha(wait ? acc : 0x1C2943, appear * (wait ? 0.35f + 0.4f * pulse : 1f)));
				Draw.roundRect(g, kx0, ky0, kx1, ky1 - 2, 3, Draw.withAlpha(wait ? acc : Draw.lerpRgb(0x111A2E, 0x1C2943, hover), appear * (wait ? 0.25f + 0.3f * pulse : 1f)));
			}
		}
		boolean unbound = "none".equals(v);
		int col = wait ? 0xFFFFFF : unbound ? DIM : (style == LmgConfig.MenuStyle.LMG ? 0xF2C14E : VALUE);
		g.centeredText(font, v, (kx0 + kx1) / 2, textY - (style == LmgConfig.MenuStyle.LMG ? 1 : 0), Draw.withAlpha(col, appear));
	}

	// ---- Lmg style ------------------------------------------------------------------------------

	private void drawLmg(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, float appear, float dt) {
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int right = x + width - 6;
		if (hover > 0.01f) Draw.roundRect(g, x, y, x + width, y + height, 4, Draw.white(0.045f * hover * appear));
		g.text(font, getMessage(), x + 6, textY, Draw.withAlpha(Draw.lerpRgb(LABEL, 0xFFFFFF, hover), appear), false);
		drawExtras(g, font, x + 6, textY, appear);
		switch (kind) {
			case TOGGLE -> {
				animateKnob(dt);
				int tw = 18, th = 10;
				int tx = right - tw, ty = y + (height - th) / 2;
				int track = Draw.lerpRgb(0x3A3A46, Theme.primary(), knob);
				Draw.roundRect(g, tx, ty, tx + tw, ty + th, 5, Draw.withAlpha(track, appear * (0.75f + 0.25f * knob)));
				int kx = tx + 1 + Math.round(knob * (tw - th));
				Draw.roundRect(g, kx, ty + 1, kx + th - 2, ty + th - 1, 4, Draw.withAlpha(0xFFFFFF, appear));
			}
			case CHOICE -> {
				String v = value.get();
				int vw = font.width(v);
				boolean overPrev = isHovered && mouseX >= prevArrowX0 && mouseX < prevArrowX1;
				boolean overNext = isHovered && mouseX >= right - 8 && !overPrev;
				int nextX = right - font.width(">");
				g.text(font, ">", nextX, textY, Draw.withAlpha(overNext ? 0xFFFFFF : DIM, appear), false);
				int vx = nextX - 5 - vw;
				g.text(font, v, vx, textY, Draw.withAlpha(VALUE, appear), false);
				int px = vx - 5 - font.width("<");
				g.text(font, "<", px, textY, Draw.withAlpha(overPrev ? 0xFFFFFF : DIM, appear), false);
				prevArrowX0 = px - 4;
				prevArrowX1 = vx - 1;
				if (swatch != null) {
					int sx = px - 11;
					Draw.roundRect(g, sx, textY - 1, sx + 7, textY + 6, 2, Draw.withAlpha(swatch.getAsInt(), appear));
				}
			}
			case BUTTON -> {
				String v = value.get() + "  >";
				g.text(font, v, right - font.width(v), textY, Draw.withAlpha(MenuSkin.accent(), appear), false);
			}
			case DROPDOWN -> {
				String v = value.get();
				int bw = Math.max(70, font.width(v) + 26);
				boxX1 = right + 2; boxX0 = boxX1 - bw; boxY0 = y + 1; boxY1 = y + height - 1;
				Draw.roundRect(g, boxX0, boxY0, boxX1, boxY1, 3, Draw.white((0.06f + 0.05f * hover) * appear));
				int sw = swatchFor(selected());
				int tx = boxX0 + 5;
				if (sw != -1) {
					Draw.roundRect(g, tx, textY, tx + 5, textY + 5, 2, Draw.withAlpha(sw, appear));
					tx += 8;
				}
				g.text(font, v, tx, textY, Draw.withAlpha(VALUE, appear), false);
				g.text(font, "v", boxX1 - 9, textY - 1, Draw.withAlpha(DIM, appear), false);
			}
		}
	}

	// ---- Skeet style: checkbox on the left, dropdown-like boxes on the right ----------------------

	private void drawSkeet(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, float appear, float dt) {
		int labelX = x + 18;
		int labelColor = Draw.withAlpha(Draw.lerpRgb(MenuSkin.SK_TEXT, 0xFFFFFF, hover), appear);
		int acc = MenuSkin.accent();
		int right = x + width - 6;
		switch (kind) {
			case TOGGLE -> {
				animateKnob(dt);
				int textY = y + (height - font.lineHeight) / 2 + 1;
				int bx = x + 6, by = y + (height - 7) / 2;
				g.fill(bx, by, bx + 7, by + 7, Draw.withAlpha(0x000000, appear));
				int top = Draw.lerpRgb(0x4B4B4B, acc, knob);
				int bottom = Draw.lerpRgb(0x333333, Draw.lerpRgb(0x000000, acc, 0.7f), knob);
				g.fillGradient(bx + 1, by + 1, bx + 6, by + 6, Draw.withAlpha(top, appear), Draw.withAlpha(bottom, appear));
				g.text(font, getMessage(), labelX, textY, labelColor, false);
				drawExtras(g, font, labelX, textY, appear);
			}
			case BUTTON -> {
				// full-width button with the label in the middle
				int bx0 = labelX, by0 = y + 1, by1 = y + height - 1;
				g.fill(bx0, by0, right, by1, Draw.withAlpha(0x000000, appear));
				int top = Draw.lerpRgb(0x2A2A2A, 0x343434, hover), bottom = Draw.lerpRgb(0x222222, 0x2B2B2B, hover);
				g.fillGradient(bx0 + 1, by0 + 1, right - 1, by1 - 1, Draw.withAlpha(top, appear), Draw.withAlpha(bottom, appear));
				g.centeredText(font, getMessage(), (bx0 + right) / 2, y + (height - font.lineHeight) / 2 + 1, labelColor);
			}
			case CHOICE, DROPDOWN -> {
				// label on its own line, value box underneath (like gamesense dropdowns)
				g.text(font, getMessage(), labelX, y + 1, labelColor, false);
				drawExtras(g, font, labelX, y + 1, appear);
				int bx0 = labelX, by0 = y + 11, by1 = y + height - 1;
				g.fill(bx0, by0, right, by1, Draw.withAlpha(0x000000, appear));
				int top = Draw.lerpRgb(0x232323, 0x2C2C2C, hover), bottom = Draw.lerpRgb(0x1D1D1D, 0x242424, hover);
				g.fillGradient(bx0 + 1, by0 + 1, right - 1, by1 - 1, Draw.withAlpha(top, appear), Draw.withAlpha(bottom, appear));
				int ty = by0 + (by1 - by0 - font.lineHeight) / 2 + 1;
				int tx = bx0 + 6;
				int sw = kind == Kind.DROPDOWN ? swatchFor(selected()) : (swatch != null ? swatch.getAsInt() : -1);
				if (sw != -1) {
					g.fill(tx, ty, tx + 5, ty + 5, Draw.withAlpha(sw, appear));
					tx += 9;
				}
				String v = value.get();
				int room = right - tx - 22;
				String shown = font.width(v) > room ? font.plainSubstrByWidth(v, room - 6) + ".." : v;
				g.text(font, shown, tx, ty, Draw.withAlpha(Draw.lerpRgb(0x989898, 0xFFFFFF, hover), appear), false);
				if (kind == Kind.DROPDOWN) {
					g.text(font, "v", right - 9, ty - 1, Draw.withAlpha(0x8A8A8A, appear), false);
					boxX0 = bx0; boxY0 = by0; boxX1 = right; boxY1 = by1;
				} else {
					boolean overPrev = isHovered && mouseX >= bx0 && mouseX < bx0 + (right - bx0) / 3;
					g.text(font, "<", right - 18, ty, Draw.withAlpha(overPrev ? 0xFFFFFF : 0x6A6A6A, appear), false);
					g.text(font, ">", right - 9, ty, Draw.withAlpha(!overPrev && isHovered ? 0xFFFFFF : 0x6A6A6A, appear), false);
					prevArrowX0 = bx0;
					prevArrowX1 = bx0 + (right - bx0) / 3;
				}
			}
		}
	}

	/** Skeet draws options as label + box underneath, so those rows are taller there. */
	public boolean isTallInSkeet() {
		return kind == Kind.CHOICE || kind == Kind.DROPDOWN;
	}

	// ---- Neverlose style: pill switches and rounded fields ---------------------------------------

	private void drawNeverlose(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, float appear, float dt) {
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int right = x + width - 6;
		int acc = MenuSkin.accent();
		if (hover > 0.01f) Draw.roundRect(g, x, y, x + width, y + height, 4, Draw.withAlpha(MenuSkin.NL_HOVER, hover * appear));
		g.text(font, getMessage(), x + 6, textY, Draw.withAlpha(Draw.lerpRgb(MenuSkin.NL_TEXT, 0xFFFFFF, hover), appear), false);
		drawExtras(g, font, x + 6, textY, appear);
		switch (kind) {
			case TOGGLE -> {
				animateKnob(dt);
				int tw = 16, th = 8;
				int tx = right - tw, ty = y + (height - th) / 2;
				Draw.roundRect(g, tx, ty, tx + tw, ty + th, 4, Draw.withAlpha(Draw.lerpRgb(0x173253, acc, knob), appear));
				int kx = tx + 1 + Math.round(knob * (tw - th));
				Draw.roundRect(g, kx, ty + 1, kx + th - 2, ty + th - 1, 3,
						Draw.withAlpha(Draw.lerpRgb(0x8A93B0, 0xFFFFFF, knob), appear));
			}
			case CHOICE -> {
				int bw = Math.min(92, width / 2);
				int bx1 = right, bx0 = bx1 - bw, by0 = y + 1, by1 = y + height - 1;
				Draw.roundRect(g, bx0 - 1, by0 - 1, bx1 + 1, by1 + 1, 4, Draw.withAlpha(MenuSkin.NL_FIELD_BORDER, appear));
				Draw.roundRect(g, bx0, by0, bx1, by1, 3, Draw.withAlpha(MenuSkin.NL_FIELD, appear));
				String v = value.get();
				boolean overPrev = isHovered && mouseX >= bx0 && mouseX < bx0 + 14;
				boolean overNext = isHovered && mouseX >= bx1 - 14;
				g.text(font, "<", bx0 + 4, textY, Draw.withAlpha(overPrev ? acc : MenuSkin.NL_TEXT_DIM, appear), false);
				g.text(font, ">", bx1 - 4 - font.width(">"), textY, Draw.withAlpha(overNext ? acc : MenuSkin.NL_TEXT_DIM, appear), false);
				String shown = font.width(v) > bw - 28 ? font.plainSubstrByWidth(v, bw - 32) + ".." : v;
				int cx = (bx0 + bx1) / 2;
				if (swatch != null) {
					int sw = font.width(shown) + 8;
					int sx = cx - sw / 2;
					Draw.roundRect(g, sx, textY, sx + 5, textY + 5, 2, Draw.withAlpha(swatch.getAsInt(), appear));
					g.text(font, shown, sx + 8, textY, Draw.withAlpha(0xFFFFFF, appear), false);
				} else {
					g.centeredText(font, shown, cx, textY, Draw.withAlpha(0xFFFFFF, appear));
				}
				prevArrowX0 = bx0;
				prevArrowX1 = bx0 + 14;
			}
			case DROPDOWN -> {
				int bw = Math.min(110, width / 2 + 10);
				boxX1 = right; boxX0 = boxX1 - bw; boxY0 = y + 1; boxY1 = y + height - 1;
				Draw.roundRect(g, boxX0 - 1, boxY0 - 1, boxX1 + 1, boxY1 + 1, 4, Draw.withAlpha(MenuSkin.NL_FIELD_BORDER, appear));
				Draw.roundRect(g, boxX0, boxY0, boxX1, boxY1, 3, Draw.withAlpha(Draw.lerpRgb(MenuSkin.NL_FIELD, 0x0A2A52, hover), appear));
				int sw = swatchFor(selected());
				int tx = boxX0 + 6;
				if (sw != -1) {
					Draw.roundRect(g, tx, textY, tx + 5, textY + 5, 2, Draw.withAlpha(sw, appear));
					tx += 8;
				}
				g.text(font, value.get(), tx, textY, Draw.withAlpha(0xFFFFFF, appear), false);
				g.text(font, "v", boxX1 - 9, textY - 1, Draw.withAlpha(acc, appear), false);
			}
			case BUTTON -> {
				String v = value.get();
				int bw = font.width(v) + 16;
				int bx0 = right - bw;
				Draw.roundRect(g, bx0, y + 1, right, y + height - 1, 4, Draw.withAlpha(Draw.lerpRgb(acc, 0xFFFFFF, hover * 0.2f), appear));
				g.centeredText(font, v, bx0 + bw / 2, textY, Draw.withAlpha(0xFFFFFF, appear));
			}
		}
	}

	@Override
	protected boolean isValidClickButton(MouseButtonInfo info) {
		return info.button() == 0 || info.button() == 1;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (more != null && event.x() >= moreX0 && event.x() < moreX1) {
			more.run();
			return;
		}
		if (kind == Kind.SLIDER) {
			if (event.button() == 1) {
				nudge(-1);
				LmgConfig.save();
			} else if (event.x() >= trackX0 - 4) {
				sliding = true;
				slideTo(event.x());
			} else {
				nudge(1);
				LmgConfig.save();
			}
			return;
		}
		if (kind == Kind.DROPDOWN) {
			if (opener != null) opener.accept(this);
			return;
		}
		boolean back = event.button() == 1 || event.hasShiftDown()
				|| (kind == Kind.CHOICE && event.x() >= prevArrowX0 && event.x() < prevArrowX1);
		if (back) previous.run();
		else next.run();
		LmgConfig.save();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
