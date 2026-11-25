package dev.lmg.client.mixin;

import dev.lmg.client.feature.Freecam;
import dev.lmg.client.feature.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (narrower field of view) and freecam (camera detached from your body). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private boolean detached;

	@Shadow protected abstract void setPosition(Vec3 pos);

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void lmg$freecam(float partialTicks, CallbackInfo ci) {
		if (!Freecam.on()) return;
		setPosition(Freecam.position(partialTicks));
		setRotation(Freecam.yaw(), Freecam.pitch());
		detached = true;
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void lmg$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float f = Zoom.factor();
		if (f != 1f) cir.setReturnValue(cir.getReturnValue() / f);
	}
}
