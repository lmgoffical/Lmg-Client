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
	/** Photo mode filters, and the grey world behind the Lmg menus. */
	@Inject(method = "update", at = @At("TAIL"))
	private void lmg$filters(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
		var list = ((GameRenderer) (Object) this).getRequestedPostEffects();
		net.minecraft.resources.Identifier focus = dev.lmg.client.render.WorldFilter.focus();
		if (focus != null) list.add(focus);
		net.minecraft.resources.Identifier blur = dev.lmg.client.render.WorldFilter.blur();
		if (blur != null) list.add(blur);
		net.minecraft.resources.Identifier extra = dev.lmg.client.render.WorldFilter.current();
		if (extra != null) list.add(extra);
		net.minecraft.resources.Identifier lens = dev.lmg.client.render.WorldFilter.lens();
		if (lens != null) list.add(lens);
	}

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void lmg$noHurtCam(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		if (LmgConfig.get().noHurtCam) ci.cancel();
	}
}
