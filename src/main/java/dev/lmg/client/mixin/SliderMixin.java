package dev.lmg.client.mixin;

import dev.lmg.client.render.Draw;
import dev.lmg.client.screen.VanillaSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's sliders (FOV, volume, render distance...) in the Lmg look, with a filled bar. */
@Mixin(AbstractSliderButton.class)
public abstract class SliderMixin {
	@Shadow protected double value;

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
	private void lmg$slider(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!VanillaSkin.on()) return;
		AbstractWidget self = (AbstractWidget) (Object) this;
		int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
		float a = self.getAlpha();
		VanillaSkin.slider(g, x, y, w, h, value, self.isHoveredOrFocused(), self.active, a);
		Font font = Minecraft.getInstance().font;
		String text = self.getMessage().getString();
		if (font.width(text) > w - 8) text = font.plainSubstrByWidth(text, w - 12) + "..";
		g.centeredText(font, text, x + w / 2, y + (h - 8) / 2, Draw.withAlpha(self.active ? 0xFFFFFF : 0xA0A0A0, a));
		ci.cancel();
	}
}
