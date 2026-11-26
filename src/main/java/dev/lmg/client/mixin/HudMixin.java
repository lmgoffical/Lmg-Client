package dev.lmg.client.mixin;

import dev.lmg.client.feature.Cinematic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cinematic mode's film bars and vignette (drawn even while the HUD is hidden). */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
	private void lmg$cinematic(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		Cinematic.draw(g);
	}
}
