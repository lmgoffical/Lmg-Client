package dev.lmg.client.mixin;

import dev.lmg.client.world.OverworldClockFlag;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the overworld clock so the Time of day setting only changes the overworld's sky. */
@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
	@Inject(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;",
			at = @At("RETURN"))
	private void lmg$markOverworld(Holder<WorldClock> clock, CallbackInfoReturnable<ClientClockManager.ClientClockInstance> cir) {
		ClientClockManager.ClientClockInstance instance = cir.getReturnValue();
		if (instance != null && clock.is(WorldClocks.OVERWORLD)) {
			((OverworldClockFlag) instance).lmg$setOverworld(true);
		}
	}
}
