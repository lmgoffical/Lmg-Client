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
		// emotes: yours, other Lmg players', or the preview in the emote wheel
		if (state instanceof dev.lmg.client.emote.EmoteHolder holder) {
			dev.lmg.client.emote.Emote preview = dev.lmg.client.emote.Emotes.preview;
			if (preview != null) {
				holder.lmg$setEmote(preview, (net.minecraft.util.Util.getMillis() - dev.lmg.client.emote.Emotes.previewStart) / 1000f);
			} else {
				dev.lmg.client.emote.Emotes.Playing playing = dev.lmg.client.emote.Emotes.get(entity.getUUID());
				holder.lmg$setEmote(playing == null ? null : playing.emote(), playing == null ? 0f : playing.seconds());
			}
		}
		// a pink heart on your partner's name tag
		if (state.nameTag != null && entity instanceof net.minecraft.world.entity.player.Player pl
				&& LmgConfig.get().partnerHearts && dev.lmg.client.social.Partner.isPartner(pl)) {
			state.nameTag = dev.lmg.client.social.Partner.withHeart(state.nameTag);
		}
		// photo mode: "hide me"
		if (entity == Minecraft.getInstance().player && dev.lmg.client.feature.PhotoMode.active() && dev.lmg.client.feature.PhotoMode.hideMe) {
			state.isInvisible = true;
			state.nameTag = null;
		}
		if (state.skin == null) return;
		if (entity != Minecraft.getInstance().player) {
			// another Lmg player's shared cape
			if (!(entity instanceof net.minecraft.world.entity.player.Player other)) return;
			dev.lmg.client.sync.AvatarSync.Shared shared = dev.lmg.client.sync.AvatarSync.get(other.getUUID());
			if (shared == null) return;
			dev.lmg.client.cosmetic.CosmeticModel theirs = dev.lmg.client.cosmetic.CosmeticRegistry.get(shared.equipped().get("capes"));
			if (theirs == null || theirs.capeTexture == null) return;
			ClientAsset.Texture tex = lmg$capes.computeIfAbsent(theirs.capeTexture, t -> new ClientAsset.ResourceTexture(
					LmgClient.id("cosmetic/" + t), LmgClient.id("textures/cosmetic/" + t)));
			state.skin = new PlayerSkin(state.skin.body(), tex, state.skin.elytra(), state.skin.model(), state.skin.secure());
			state.showCape = true;
			return;
		}
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
