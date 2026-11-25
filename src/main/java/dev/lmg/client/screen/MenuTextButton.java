package dev.lmg.client.screen;

import dev.lmg.client.render.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A text-only menu button: no box, just text. On hover a faint rounded bar
 * grows behind it and the text brightens. Fades and slides in when it appears.
 */
public class MenuTextButton extends AbstractWidget {
	private static final int IDLE_COLOR = 0xA4A4B0;
	private static final int HOVER_COLOR = 0xFFFFFF;

	private final Runnable onPress;
	private final long appearAt;
	private float hover;
	private long lastFrame = -1;

	public MenuTextButton(int x, int y, int width, int height, Component label, long appearAt, Runnable onPress) {
		super(x, y, width, height, label);
		this.onPress = onPress;
		this.appearAt = appearAt;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : (now - lastFrame) / 1000f;
		lastFrame = now;
		hover = Draw.approach(hover, isHoveredOrFocused() ? 1f : 0f, 14f, dt);

		float appear = Draw.easeOutCubic((now - appearAt) / 380f);
		if (appear <= 0.01f) return;

		int yOff = Math.round((1 - appear) * 6);
		int cx = getX() + width / 2;
		int top = getY() + yOff;

		if (hover > 0.01f) {
			int barW = Math.round(width * (0.5f + 0.5f * Draw.easeOutCubic(hover)));
			Draw.roundRect(g, cx - barW / 2, top, cx + barW / 2, top + height, 4, Draw.white(0.09f * hover * appear));
		}

		int rgb = Draw.lerpRgb(IDLE_COLOR, HOVER_COLOR, hover);
		var font = Minecraft.getInstance().font;
		g.centeredText(font, getMessage(), cx, top + (height - font.lineHeight) / 2 + 1, Draw.withAlpha(rgb, appear));
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
