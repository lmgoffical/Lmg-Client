package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
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

/** One page of a group, along the top of the menu ("Sky", "Weather", "Effects", ...). */
public class PageTab extends AbstractWidget {
	private final BooleanSupplier selected;
	private final Runnable onSelect;
	private float hover, active = -1;
	private long lastFrame = -1;

	public PageTab(int x, int y, int width, int height, String label, BooleanSupplier selected, Runnable onSelect) {
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
		var font = Minecraft.getInstance().font;
		int x = getX(), y = getY(), w = width, h = height;
		LmgConfig.MenuStyle style = MenuSkin.style();
		int acc = style == LmgConfig.MenuStyle.LMG ? Theme.primary() : MenuSkin.accent();
		int[] tc = MenuSkin.textColors();
		int idle = style == LmgConfig.MenuStyle.LMG ? 0x8E9AB0 : tc[1];
		switch (style) {
			case SKEET -> {
				// flat tabs with an underline, like Skeet's own sub tabs
				if (active > 0.01f) g.fill(x, y + h - 1, x + w, y + h, Draw.withAlpha(acc, active));
			}
			default -> {
				if (active > 0.01f || hover > 0.01f) {
					float fill = Math.max(active * 0.22f, hover * 0.08f);
					Draw.roundRect(g, x, y, x + w, y + h, 5, Draw.withAlpha(active > 0.5f ? acc : 0xFFFFFF, fill));
				}
			}
		}
		int text = Draw.lerpRgb(Draw.lerpRgb(idle, 0xFFFFFF, hover * 0.6f), 0xFFFFFF, active);
		g.text(font, getMessage(), x + (w - font.width(getMessage())) / 2, y + (h - 8) / 2 + 1, Draw.withAlpha(text, 1f), false);
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
