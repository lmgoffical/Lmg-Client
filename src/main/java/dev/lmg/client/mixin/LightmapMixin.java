package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: lights everything up like permanent night vision (client-side only). */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void lmg$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (LmgConfig.get().fullbright) {
			state.nightVisionEffectIntensity = 1f;
			state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
			state.needsUpdate = true;
		}
	}
}
