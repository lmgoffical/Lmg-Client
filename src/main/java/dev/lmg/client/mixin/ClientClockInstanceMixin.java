package dev.lmg.client.mixin;

import dev.lmg.client.world.OverworldClockFlag;
import dev.lmg.client.world.WorldVisuals;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time of day changer: when set, the client's overworld clock reports a fixed time,
 * so the sun, moon, stars, sky colors and lighting all follow it. Client-side only.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockInstanceMixin implements OverworldClockFlag {
	@Shadow private long totalTicks;

	@Unique private boolean lmg$overworld;

	@Override
	public void lmg$setOverworld(boolean overworld) {
		this.lmg$overworld = overworld;
	}

	@Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
	private void lmg$fixedTime(CallbackInfoReturnable<Long> cir) {
		long target = WorldVisuals.fixedTimeOfDay();
		if (!lmg$overworld || target < 0) return;
		long day = Math.floorDiv(totalTicks, 24000L); // keep the real day so the moon phase stays right
		cir.setReturnValue(day * 24000L + target);
	}

	@Inject(method = "partialTick", at = @At("HEAD"), cancellable = true)
	private void lmg$noPartialTick(CallbackInfoReturnable<Float> cir) {
		if (lmg$overworld && WorldVisuals.fixedTimeOfDay() >= 0) cir.setReturnValue(0f);
	}
}
