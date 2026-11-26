package dev.lmg.client.mixin;

import dev.lmg.client.feature.Visuals;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server telling us something took damage (and who did it): real hits, for the combo and hit sounds. */
@Mixin(LivingEntity.class)
public abstract class LivingDamageMixin {
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void lmg$damaged(DamageSource source, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) Visuals.onDamage(self, source);
	}
}
