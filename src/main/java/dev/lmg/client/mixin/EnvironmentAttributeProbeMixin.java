package dev.lmg.client.mixin;

import dev.lmg.client.world.WorldVisuals;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every sky/fog/cloud/star value the client renders is read through this probe.
 * We pass each value through WorldVisuals so the themes apply everywhere at once.
 */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class EnvironmentAttributeProbeMixin {
	@Shadow private Level level;

	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void lmg$applyWorldTheme(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> cir) {
		Level lvl = this.level;
		if (lvl == null || !lvl.isClientSide()) return; // never touch the (integrated) server's values
		Object original = cir.getReturnValue();
		Object changed = WorldVisuals.modifyAttribute(lvl, attribute, original);
		if (changed != original) cir.setReturnValue(changed);
	}
}
