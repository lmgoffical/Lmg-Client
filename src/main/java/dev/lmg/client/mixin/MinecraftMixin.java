package dev.lmg.client.mixin;

import dev.lmg.client.feature.Freecam;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No hitting, placing or picking blocks while you're flying the freecam around. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void lmg$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.on()) cir.setReturnValue(false);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void lmg$noMining(boolean down, CallbackInfo ci) {
		if (Freecam.on()) ci.cancel();
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void lmg$noUse(CallbackInfo ci) {
		if (Freecam.on()) ci.cancel();
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void lmg$noPick(CallbackInfo ci) {
		if (Freecam.on()) ci.cancel();
	}
}
