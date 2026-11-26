package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.world.Winter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Rain & snow: Hidden" stops drawing falling rain and snow, and Winter makes it snow
 * (client-side only: nothing about the real weather changes).
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
	@Shadow
	protected abstract WeatherEffectRenderer.ColumnInstance createSnowColumnInstance(RandomSource random, long ticks, int x, int bottomY,
																				  int topY, int z, int lightCoords, float partialTicks);

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lmg$weather(ClientLevel level, float partialTick, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (!LmgConfig.get().showWeather) {
			state.rainColumns.clear();
			state.snowColumns.clear();
			state.intensity = 0f;
			return;
		}
		if (!Winter.snowing(Minecraft.getInstance())) return;
		// winter: snow in every column that gets weather at all (the same way the game draws snow)
		state.rainColumns.clear();
		state.snowColumns.clear();
		state.intensity = Math.max(state.intensity, Winter.intensity());
		state.radius = Minecraft.getInstance().options.weatherRadius().get();
		int cx = Mth.floor(cameraPos.x), cy = Mth.floor(cameraPos.y), cz = Mth.floor(cameraPos.z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		RandomSource random = RandomSource.createThreadLocalInstance();
		for (int z = cz - state.radius; z <= cz + state.radius; z++) {
			for (int x = cx - state.radius; x <= cx + state.radius; x++) {
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				int y0 = Math.max(cy - state.radius, ground), y1 = Math.max(cy + state.radius, ground);
				if (y1 == y0 || !Winter.snowsAt(level, pos.set(x, cy, z))) continue;
				random.setSeed(x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
				int light = LightCoordsUtil.getLightCoords(level, pos.set(x, Math.max(cy, ground), z));
				state.snowColumns.add(createSnowColumnInstance(random, level.getGameTime(), x, y0, y1, z, light, partialTick));
			}
		}
	}
}
