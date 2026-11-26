package dev.lmg.client.mixin;

import dev.lmg.client.emote.EmoteHolder;
import dev.lmg.client.emote.EmotePoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poses a player doing an emote (body, armour and cape all use this model shape). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void lmg$emote(HumanoidRenderState state, CallbackInfo ci) {
		if (state instanceof EmoteHolder holder && holder.lmg$emote() != null) {
			EmotePoses.apply((HumanoidModel<?>) (Object) this, holder.lmg$emote(), holder.lmg$emoteTime());
		}
		// someone pulling on your arm
		if (state instanceof EmoteHolder holder && holder.lmg$tug() > 0.01f) {
			EmotePoses.tugged((HumanoidModel<?>) (Object) this, holder.lmg$tug(), state.ageInTicks / 20f);
		}
	}
}
