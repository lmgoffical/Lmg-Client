package dev.lmg.client.mixin;

import dev.lmg.client.feature.Freecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While freecam is on, WASD moves the camera, so your body stands still. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void lmg$freecamStill(CallbackInfo ci) {
		if (Freecam.on()) {
			keyPresses = Input.EMPTY;
			moveVector = Vec2.ZERO;
		}
	}
}
