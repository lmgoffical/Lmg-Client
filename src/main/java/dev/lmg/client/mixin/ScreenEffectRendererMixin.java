package dev.lmg.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Low fire": moves the burning overlay down so it doesn't block your view. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void lmg$lowFireStart(PoseStack pose, SubmitNodeCollector nodes, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.pushPose();
		if (LmgConfig.get().lowFire) pose.translate(0f, -0.35f, 0f);
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void lmg$lowFireEnd(PoseStack pose, SubmitNodeCollector nodes, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.popPose();
	}
}
