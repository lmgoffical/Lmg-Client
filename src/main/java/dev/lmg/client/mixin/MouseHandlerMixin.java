package dev.lmg.client.mixin;

import dev.lmg.client.feature.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while holding zoom changes how far you zoom (instead of switching hotbar slots). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void lmg$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (Zoom.held() && yOffset != 0) {
			Zoom.scroll(yOffset);
			ci.cancel();
		}
	}
}
