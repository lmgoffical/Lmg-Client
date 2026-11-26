package dev.lmg.client.screen;

import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** A filled, rounded button for the main thing on a screen ("Turn on friends", "Add", "Save photo"). */
public class PillButton extends AbstractWidget {
	private final Runnable onPress;
	private final long appearAt;
	private final boolean primary;
	private float hover;
	private long lastFrame = -1;

	public PillButton(int x, int y, int width, int height, Component label, boolean primary, long appearAt, Runnable onPress) {
		super(x, y, width, height, label);
		this.onPress = onPress;
		this.appearAt = appearAt;
		this.primary = primary;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : (now - lastFrame) / 1000f;
		lastFrame = now;
		hover = Draw.approach(hover, isHoveredOrFocused() ? 1f : 0f, 14f * Theme.speed(), dt);
		float a = Draw.easeOutCubic(Theme.progress(appearAt, 300f)) * (active ? 1f : 0.45f);
		if (a <= 0.01f) return;
		draw(g, Minecraft.getInstance().font, getX(), getY(), width, height, getMessage().getString(), primary, hover, a);
	}

	/** Also used for buttons drawn straight onto a screen (rows in a list). */
	public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String text, boolean primary, float hover, float a) {
		int base = primary ? Theme.primary() : 0x22304D;
		int bg = Draw.lerpRgb(base, 0xFFFFFF, 0.14f * hover);
		int r = Math.min(5, h / 2);
		// a darker lip under the button, so it reads as something to press
		Draw.roundRect(g, x, y + 1, x + w, y + h + 1, r, Draw.withAlpha(Draw.lerpRgb(base, 0x000000, 0.45f), a));
		Draw.roundRect(g, x, y, x + w, y + h, r, Draw.withAlpha(bg, a));
		int textColor = primary ? Theme.textOn(bg) : 0xE8EDF5;
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, Draw.withAlpha(textColor, a), false);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		onPress.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
