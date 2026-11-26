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

/**
 * Target outline: the mob/player you're aiming at gets a glowing outline, but only within the
 * highlight distance, and in a second colour once they're within your reach.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"))
	private void lmg$targetOutline(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		LmgConfig cfg = LmgConfig.get();
		if (!cfg.targetOutline || entity != Targeting.current()) return;
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) return;
		// only once they're close enough (no outlines on things far away)
		double range = LmgConfig.TARGET_RANGES[cfg.targetRange];
		if (entity.distanceTo(player) > range) return;
		// a different colour once they're within your reach (you can hit them)
		int rgb = cfg.reachColorOn && player.isWithinEntityInteractionRange(entity, 0.0) ? cfg.reachColor & 0xFFFFFF : cfg.targetRgb();
		state.outlineColor = rgb == 0 ? 0x010101 : rgb;
	}
}
