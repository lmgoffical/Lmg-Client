package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.hud.Targeting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Target outline: the mob/player you're aiming at gets a glowing outline in your accent color. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"))
	private void lmg$targetOutline(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		LmgConfig cfg = LmgConfig.get();
		if (cfg.targetOutline && entity == Targeting.current()) {
			int rgb = cfg.accent.rgb;
			state.outlineColor = rgb == 0 ? 0xFFFFFF : rgb;
		}
	}
}
