package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Rain & snow: Hidden" - stops drawing falling rain and snow (client-side only). */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lmg$hideWeather(ClientLevel level, float partialTick, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (!LmgConfig.get().showWeather) {
			state.rainColumns.clear();
			state.snowColumns.clear();
			state.intensity = 0f;
		}
	}
}
