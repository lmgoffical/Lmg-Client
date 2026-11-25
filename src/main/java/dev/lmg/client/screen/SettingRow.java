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

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One line in the Client settings: name on the left, value on the right.
 * Left-click goes to the next value, right-click (or shift-click) to the previous.
 */
public class SettingRow extends AbstractWidget {
	private static final int LABEL_IDLE = 0xB4B4C0;
	private static final int VALUE_OFF = 0x6E6E78;
	private static final int VALUE_OTHER = 0xE6E6EE;

	private final Supplier<String> value;
	private final Runnable next;
	private final Runnable previous;
	private final IntSupplier swatch; // optional color square next to the value
	private long appearAt;
	private float hover;
	private long lastFrame = -1;

	public SettingRow(int x, int y, int width, int height, String label, Supplier<String> value,
					  Runnable next, Runnable previous, IntSupplier swatch, long appearAt) {
		super(x, y, width, height, Component.literal(label));
		this.value = value;
		this.next = next;
		this.previous = previous;
		this.swatch = swatch;
		this.appearAt = appearAt;
	}

	/** When the row starts fading in (used to stagger rows one after another). */
	public void setAppearAt(long appearAt) {
		this.appearAt = appearAt;
	}

	/** A simple On/Off row. */
	public static SettingRow toggle(int x, int y, int w, int h, String label, Supplier<Boolean> get,
									java.util.function.Consumer<Boolean> set, long appearAt) {
		Runnable flip = () -> set.accept(!get.get());
		return new SettingRow(x, y, w, h, label, () -> get.get() ? "On" : "Off", flip, flip, null, appearAt);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : (now - lastFrame) / 1000f;
		lastFrame = now;
		hover = Draw.approach(hover, isHoveredOrFocused() ? 1f : 0f, 14f * Theme.speed(), dt);
		float appear = Draw.easeOutCubic(Theme.progress(appearAt, 320f));
		if (appear <= 0.01f) return;

		Font font = Minecraft.getInstance().font;
		int yOff = Math.round((1 - appear) * 5);
		int top = getY() + yOff;
		int textY = top + (height - font.lineHeight) / 2 + 1;

		if (hover > 0.01f) {
			Draw.roundRect(g, getX(), top, getX() + width, top + height, 4, Theme.accent(0.10f * hover * appear));
		}

		g.text(font, getMessage(), getX() + 8, textY, Draw.withAlpha(Draw.lerpRgb(LABEL_IDLE, 0xFFFFFF, hover), appear), false);

		String v = value.get();
		int color = switch (v) {
			case "On" -> Theme.accentSoft();
			case "Off" -> VALUE_OFF;
			default -> VALUE_OTHER;
		};
		int right = getX() + width - 8;
		if (hover > 0.01f) {
			// little arrows to hint that you can click through the options
			int arrows = Draw.withAlpha(0xFFFFFF, 0.5f * hover * appear);
			g.text(font, ">", right - font.width(">"), textY, arrows, false);
			right -= font.width(">") + 4;
		}
		int vw = font.width(v);
		g.text(font, v, right - vw, textY, Draw.withAlpha(color, appear), false);
		if (hover > 0.01f) {
			g.text(font, "<", right - vw - 4 - font.width("<"), textY, Draw.withAlpha(0xFFFFFF, 0.5f * hover * appear), false);
		}
		if (swatch != null) {
			int sx = right - vw - (hover > 0.01f ? 14 : 6) - 8;
			Draw.roundRect(g, sx, textY - 1, sx + 8, textY + 7, 2, Draw.withAlpha(swatch.getAsInt(), appear));
		}
	}

	@Override
	protected boolean isValidClickButton(MouseButtonInfo info) {
		return info.button() == 0 || info.button() == 1;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 1 || event.hasShiftDown()) previous.run();
		else next.run();
		LmgConfig.save();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
