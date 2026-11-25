package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TotemParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With the Lmg totem on, the totem pop particles are gold and white instead of green/yellow. */
@Mixin(TotemParticle.class)
public abstract class TotemParticleMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void lmg$goldParticles(ClientLevel level, double x, double y, double z, double xa, double ya, double za,
								   SpriteSet sprites, CallbackInfo ci) {
		if (!LmgConfig.get().lmgTotem) return;
		SingleQuadParticle self = (SingleQuadParticle) (Object) this;
		if (level.getRandom().nextInt(3) == 0) self.setColor(1f, 1f, 0.95f);
		else self.setColor(1f, 0.78f + level.getRandom().nextFloat() * 0.15f, 0.25f);
	}
}
