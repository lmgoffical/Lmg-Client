package dev.lmg.client.mixin;

import dev.lmg.client.feature.Freecam;
import dev.lmg.client.feature.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse look: steers the freecam instead of you, and is slowed down while zoomed. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void lmg$freecamLook(double dx, double dy, CallbackInfo ci) {
		if (Freecam.on() && (Object) this == Minecraft.getInstance().player) {
			Freecam.turn(dx, dy);
			ci.cancel();
		}
	}

	@ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double lmg$zoomX(double dx) {
		return (Object) this == Minecraft.getInstance().player ? dx * Zoom.sensitivity() : dx;
	}

	@ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double lmg$zoomY(double dy) {
		return (Object) this == Minecraft.getInstance().player ? dy * Zoom.sensitivity() : dy;
	}
}
