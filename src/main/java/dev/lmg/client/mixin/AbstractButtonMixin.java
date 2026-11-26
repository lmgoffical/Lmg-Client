package dev.lmg.client.mixin;

import dev.lmg.client.screen.VanillaSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's buttons get the Lmg look (only the background - text and clicks stay vanilla). */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void lmg$button(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (!VanillaSkin.on()) return;
		AbstractWidget self = (AbstractWidget) (Object) this;
		VanillaSkin.button(g, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.isHoveredOrFocused(), self.active, self.getAlpha());
		ci.cancel();
	}
}
