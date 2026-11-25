package dev.lmg.client.mixin;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives YOUR player the Lmg Client cape (vanilla cape physics, only on your screen). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Unique
	private static final ClientAsset.Texture LMG_CAPE = new ClientAsset.ResourceTexture(
			LmgClient.id("cosmetic/cape"), LmgClient.id("textures/cosmetic/cape.png"));

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void lmg$cape(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (!LmgConfig.get().cape || entity != Minecraft.getInstance().player || state.skin == null) return;
		PlayerSkin skin = state.skin;
		state.skin = new PlayerSkin(skin.body(), LMG_CAPE, skin.elytra(), skin.model(), skin.secure());
	}
}
