package dev.lmg.client.mixin;

import dev.lmg.client.world.WorldVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the Fog setting (Light / None / Thick) after vanilla works out its fog distances. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void lmg$adjustFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkness,
							   ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		FogData fog = cir.getReturnValue();
		if (fog != null) WorldVisuals.adjustFog(fog, camera);
	}
}
