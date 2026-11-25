package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clouds: Hidden turns them off without touching your saved video settings. */
@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "getCloudStatus", at = @At("RETURN"), cancellable = true)
	private void lmg$hideClouds(CallbackInfoReturnable<CloudStatus> cir) {
		if (LmgConfig.get().clouds == LmgConfig.CloudMode.HIDDEN) cir.setReturnValue(CloudStatus.OFF);
	}
}
