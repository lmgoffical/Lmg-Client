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
	private enum Kind { TOGGLE, CHOICE, BUTTON }

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
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int right = x + width - 6;

		if (hover > 0.01f) Draw.roundRect(g, x, y, x + width, y + height, 4, Draw.white(0.045f * hover * appear));
		g.text(font, getMessage(), x + 6, textY, Draw.withAlpha(Draw.lerpRgb(LABEL, 0xFFFFFF, hover), appear), false);

		switch (kind) {
			case TOGGLE -> {
				boolean on = isOn.get();
				if (knob < 0) knob = on ? 1 : 0;
				knob = Draw.approach(knob, on ? 1f : 0f, 18f * Theme.speed(), dt);
				int tw = 18, th = 10;
				int tx = right - tw, ty = y + (height - th) / 2;
				int track = Draw.lerpRgb(0x3A3A46, Theme.accent(), knob);
				Draw.roundRect(g, tx, ty, tx + tw, ty + th, 5, Draw.withAlpha(track, appear * (0.75f + 0.25f * knob)));
				int kx = tx + 1 + Math.round(knob * (tw - th));
				Draw.roundRect(g, kx, ty + 1, kx + th - 2, ty + th - 1, 4, Draw.withAlpha(0xFFFFFF, appear));
			}
			case CHOICE -> {
				String v = value.get();
				int vw = font.width(v);
				boolean overPrev = isHovered && mouseX >= prevArrowX0 && mouseX < prevArrowX1;
				boolean overNext = isHovered && mouseX >= right - 8 && !overPrev;
				int nextX = right - font.width("›");
				g.text(font, "›", nextX, textY, Draw.withAlpha(overNext ? 0xFFFFFF : DIM, appear), false);
				int vx = nextX - 5 - vw;
				g.text(font, v, vx, textY, Draw.withAlpha(VALUE, appear), false);
				int px = vx - 5 - font.width("‹");
				g.text(font, "‹", px, textY, Draw.withAlpha(overPrev ? 0xFFFFFF : DIM, appear), false);
				prevArrowX0 = px - 4;
				prevArrowX1 = vx - 1;
				if (swatch != null) {
					int sx = px - 11;
					Draw.roundRect(g, sx, textY - 1, sx + 7, textY + 6, 2, Draw.withAlpha(swatch.getAsInt(), appear));
				}
			}
			case BUTTON -> {
				String v = value.get() + "  ›";
				g.text(font, v, right - font.width(v), textY, Draw.withAlpha(Theme.accentSoft(), appear), false);
			}
		}
	}

	@Override
	protected boolean isValidClickButton(MouseButtonInfo info) {
		return info.button() == 0 || info.button() == 1;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
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
