package dev.lmg.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "No hurt camera": stops the screen from tilting when you take damage. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void lmg$noHurtCam(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		if (LmgConfig.get().noHurtCam) ci.cancel();
	}
}
