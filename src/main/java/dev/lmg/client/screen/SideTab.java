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

import java.util.function.BooleanSupplier;

/** A category in the menu's sidebar. The selected one gets an accent bar and a soft highlight. */
public class SideTab extends AbstractWidget {
	private final BooleanSupplier selected;
	private final Runnable onSelect;
	private float hover;
	private float active = -1;
	private long lastFrame = -1;

	public SideTab(int x, int y, int width, int height, String label, BooleanSupplier selected, Runnable onSelect) {
		super(x, y, width, height, Component.literal(label));
		this.selected = selected;
		this.onSelect = onSelect;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : (now - lastFrame) / 1000f;
		lastFrame = now;
		boolean sel = selected.getAsBoolean();
		if (active < 0) active = sel ? 1 : 0;
		active = Draw.approach(active, sel ? 1f : 0f, 16f * Theme.speed(), dt);
		hover = Draw.approach(hover, isHoveredOrFocused() ? 1f : 0f, 16f * Theme.speed(), dt);

		MenuSkin.tab(g, Minecraft.getInstance().font, getX(), getY(), width, height, getMessage(), active, hover);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		onSelect.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
