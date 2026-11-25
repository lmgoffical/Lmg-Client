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
	private enum Kind { TOGGLE, CHOICE, BUTTON, DROPDOWN }

	private static final int LABEL = 0xB9B9C4;
	private static final int VALUE = 0xEDEDF2;
	private static final int DIM = 0x6E6E7A;

	private final Kind kind;
	private final Supplier<Boolean> isOn;
	private final Supplier<String> value;
	private final Runnable next;
	private final Runnable previous;
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
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : (now - lastFrame) / 1000f;
		lastFrame = now;
		hover = Draw.approach(hover, isHoveredOrFocused() ? 1f : 0f, 16f * Theme.speed(), dt);
		float appear = Draw.easeOutCubic(Theme.progress(appearAt, 260f));
		if (appear <= 0.01f) return;

		Font font = Minecraft.getInstance().font;
		int x = getX(), y = getY() + Math.round((1 - appear) * 4);
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

	// ---- Lmg style ------------------------------------------------------------------------------

	private void drawLmg(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, float appear, float dt) {
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int right = x + width - 6;
		if (hover > 0.01f) Draw.roundRect(g, x, y, x + width, y + height, 4, Draw.white(0.045f * hover * appear));
		g.text(font, getMessage(), x + 6, textY, Draw.withAlpha(Draw.lerpRgb(LABEL, 0xFFFFFF, hover), appear), false);
		switch (kind) {
			case TOGGLE -> {
				animateKnob(dt);
				int tw = 18, th = 10;
				int tx = right - tw, ty = y + (height - th) / 2;
				int track = Draw.lerpRgb(0x3A3A46, MenuSkin.accent(), knob);
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
