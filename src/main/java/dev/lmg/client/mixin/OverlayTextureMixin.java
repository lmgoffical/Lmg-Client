package dev.lmg.client.mixin;

import dev.lmg.client.feature.Visuals;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The red flash when something gets hurt, in a colour you pick. */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements Visuals.HurtTint {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void lmg$remember(CallbackInfo ci) {
		Visuals.overlay = this;
		Visuals.applyHurtColor();
	}

	@Override
	public void lmg$hurtColor(int rgb) {
		var pixels = texture.getPixels();
		if (pixels == null) return;
		// the same see-through amount as the game's red (0xB2), in your colour
		int argb = 0xB2000000 | (rgb & 0xFFFFFF);
		for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) pixels.setPixel(x, y, argb);
		texture.upload();
	}
}
