package dev.lmg.client.mixin;

import dev.lmg.client.feature.Cinematic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cinematic mode's film bars and vignette (drawn even while the HUD is hidden); no HUD behind the Lmg menus. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
	private void lmg$cinematic(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		// behind the Lmg menus (and in photo mode) the HUD steps aside: the world is the background
		var screen = net.minecraft.client.Minecraft.getInstance().gui.screen();
		if (screen instanceof dev.lmg.client.render.WorldFilter.GreyBehind || dev.lmg.client.feature.PhotoMode.active()) {
			ci.cancel();
			return;
		}
		Cinematic.draw(g);
	}
}
