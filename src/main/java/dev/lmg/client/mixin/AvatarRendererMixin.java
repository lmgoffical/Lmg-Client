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

/** Gives YOUR player the equipped Lmg cape and your custom skin (vanilla cape physics, only on your screen). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Unique
	private static final java.util.Map<String, ClientAsset.Texture> lmg$capes = new java.util.HashMap<>();

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void lmg$cape(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (entity != Minecraft.getInstance().player || state.skin == null) return;
		PlayerSkin skin = state.skin;
		// custom skin (from config/lmgclient-skins)
		PlayerSkin custom = dev.lmg.client.cosmetic.CustomSkins.current();
		if (custom != null) skin = new PlayerSkin(custom.body(), skin.cape(), skin.elytra(), custom.model(), skin.secure());
		// equipped cape from the cosmetics catalog
		dev.lmg.client.cosmetic.CosmeticModel cape = dev.lmg.client.cosmetic.CosmeticRegistry.get(LmgConfig.get().equipped.get("capes"));
		ClientAsset.Texture capeTex = skin.cape();
		if (cape != null && cape.capeTexture != null && !LmgConfig.get().hideCosmetics) {
			capeTex = lmg$capes.computeIfAbsent(cape.capeTexture, tex -> new ClientAsset.ResourceTexture(
					LmgClient.id("cosmetic/" + tex), LmgClient.id("textures/cosmetic/" + tex)));
		}
		state.skin = new PlayerSkin(skin.body(), capeTex, skin.elytra(), skin.model(), skin.secure());
	}
}
