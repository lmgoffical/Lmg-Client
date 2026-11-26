package dev.lmg.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lmg.client.cosmetic.Combat;
import dev.lmg.client.cosmetic.CosmeticsLayer;
import dev.lmg.client.cosmetic.Effects;
import dev.lmg.client.hud.HudPill;
import dev.lmg.client.hud.PotionHud;
import dev.lmg.client.hud.TargetHud;
import dev.lmg.client.hud.Targeting;
import dev.lmg.client.screen.LmgSettingsScreen;
import dev.lmg.client.world.OreGlow;
import dev.lmg.client.world.WorldRender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LmgClient implements ClientModInitializer {
	/** Change this one line to rename the client everywhere. */
	public static final String CLIENT_NAME = "Lmg Client";
	public static final String MOD_ID = "lmgclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(CLIENT_NAME);

	/** Right Shift by default; can be changed in Minecraft's Controls settings. */
	public static final KeyMapping OPEN_MENU_KEY = dev.lmg.client.feature.LmgKeys.OPEN_MENU;

	@Override
	public void onInitializeClient() {
		LmgConfig.load();
		dev.lmg.client.cosmetic.CosmeticRegistry.loadBuiltIn();
		// Blockbench models need the texture manager, so load them once the game is up.
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> dev.lmg.client.cosmetic.CustomModels.reload());
		UpdateChecker.start();
		dev.lmg.client.feature.LmgKeys.register();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud_pill"), new HudPill());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("target_hud"), new TargetHud());
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, id("potion_hud"), new PotionHud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("minimap"), new dev.lmg.client.hud.MinimapHud());
		// your own crosshair (or the game's, if you keep "Vanilla")
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
			if (!dev.lmg.client.feature.Visuals.drawCrosshair(graphics)) vanilla.extractRenderState(graphics, delta);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("damage_numbers"), dev.lmg.client.feature.Visuals::drawNumbers);
		// lens effects sit right on the world, under everything else on screen
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, id("lens"), dev.lmg.client.render.LensOverlay::draw);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("pvp_hud"), new dev.lmg.client.hud.PvpHud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("armour_hud"), new dev.lmg.client.hud.ArmourHud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("death_compass"),
				(graphics, delta) -> dev.lmg.client.map.DeathTrail.drawCompass(graphics, Minecraft.getInstance()));
		// Save the map when you leave a world or close the game.
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			dev.lmg.client.map.MapData.saveAll();
			dev.lmg.client.social.Social.onGameClosing();
		});
		// Our potion list replaces the vanilla effect icons in the top-right while it's on.
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (graphics, delta) -> {
			if (!LmgConfig.get().potionHud) vanilla.extractRenderState(graphics, delta);
		});

		// Equipped cosmetics (wings, hats, glasses, ears, auras, imported models) - only drawn for you.
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new CosmeticsLayer(parent));
			}
		});

		// Built-in resource packs: ore edges and the Lmg totem (toggled from the PvP tab).
		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod -> {
			ResourceLoader.registerBuiltinPack(id("ore_outlines"), mod,
					Component.literal("Lmg Ore Edges"), PackActivationType.NORMAL);
			// The Star of David totem is on by default (toggle in the PvP tab).
			ResourceLoader.registerBuiltinPack(id("lmg_totem"), mod,
					Component.literal("Lmg Totem"), PackActivationType.NORMAL);
		});

		// Things drawn in the world: custom block overlay, target reticle, aurora.
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(WorldRender::blockOutline);
		// Pets are added to the list of things the game draws, after it has collected the real entities.
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION.register(dev.lmg.client.pet.Pets::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			WorldRender.aurora(context);
			WorldRender.targetReticle(context);
			OreGlow.render(context);
			dev.lmg.client.world.Winter.render(context);
			dev.lmg.client.world.Mist.render(context);
			dev.lmg.client.map.DeathTrail.render(context);
		});

		// Hit / kill effects: remember what YOU hit (client side only, never cancels the attack).
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				Combat.onAttack(entity);
				dev.lmg.client.feature.Visuals.onAttack(entity);
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Effects.tick(mc);
			Combat.tick(mc);
			Targeting.tick(mc);
			OreGlow.tick(mc);
			dev.lmg.client.feature.LmgKeys.tick(mc);
			dev.lmg.client.pet.Pets.tick(mc);
			dev.lmg.client.sync.AvatarSync.tick(mc);
			dev.lmg.client.social.Social.tick(mc);
			dev.lmg.client.social.Partner.tick(mc);
			dev.lmg.client.emote.Emotes.tick(mc);
			dev.lmg.client.emote.EmoteNet.tick(mc);
			dev.lmg.client.world.Winter.tick(mc);
			dev.lmg.client.world.Mist.tick(mc);
			dev.lmg.client.map.DeathTrail.tick(mc);
			dev.lmg.client.feature.Visuals.tick(mc);
			dev.lmg.client.social.PartnerPlay.tick(mc);
			dev.lmg.client.social.ClimbAllow.tick(mc);
			dev.lmg.client.social.Uids.tick(mc);
			dev.lmg.client.map.MapData.tick(mc);
		});
		LOGGER.info("{} loaded", CLIENT_NAME);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
