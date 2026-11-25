package dev.lmg.client.screen;

import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** A small menu button that follows the selected menu style (Lmg / Skeet / Neverlose). */
public class SkinButton extends AbstractWidget {
	private final Runnable onPress;
	private final long appearAt;
	private float hover;
	private long lastFrame = -1;

	public SkinButton(int x, int y, int width, int height, Component label, long appearAt, Runnable onPress) {
		super(x, y, width, height, label);
		this.onPress = onPress;
		this.appearAt = appearAt;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : (now - lastFrame) / 1000f;
		lastFrame = now;
		hover = Draw.approach(hover, isHoveredOrFocused() ? 1f : 0f, 16f * Theme.speed(), dt);
		float a = Draw.easeOutCubic(Theme.progress(appearAt, 300f));
		if (a <= 0.01f) return;
		MenuSkin.button(g, Minecraft.getInstance().font, getX(), getY(), width, height, getMessage(), hover, a);
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
