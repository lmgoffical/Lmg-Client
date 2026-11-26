package dev.lmg.client.mixin;

import dev.lmg.client.feature.Visuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts your clicks for the CPS readout (only while playing, not in menus). */
@Mixin(MouseHandler.class)
public abstract class MouseClickMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void lmg$click(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && Minecraft.getInstance().gui.screen() == null) Visuals.onClick(info.button());
	}
}
