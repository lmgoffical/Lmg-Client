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
	public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
			"key.lmgclient.open_menu",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_RSHIFT,
			KeyMapping.Category.register(id("main")));

	@Override
	public void onInitializeClient() {
		LmgConfig.load();
		dev.lmg.client.cosmetic.CosmeticRegistry.loadBuiltIn();
		// Blockbench models need the texture manager, so load them once the game is up.
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> dev.lmg.client.cosmetic.CustomModels.reload());
		UpdateChecker.start();
		KeyMappingHelper.registerKeyMapping(OPEN_MENU_KEY);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud_pill"), new HudPill());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("target_hud"), new TargetHud());
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, id("potion_hud"), new PotionHud());
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
					Component.literal("Lmg Totem"), PackActivationType.DEFAULT_ENABLED);
		});

		// Things drawn in the world: custom block overlay, target reticle, aurora.
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(WorldRender::blockOutline);
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			WorldRender.aurora(context);
			WorldRender.targetReticle(context);
			OreGlow.render(context);
		});

		// Hit / kill effects: remember what YOU hit (client side only, never cancels the attack).
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) Combat.onAttack(entity);
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Effects.tick(mc);
			Combat.tick(mc);
			Targeting.tick(mc);
			OreGlow.tick(mc);
			while (OPEN_MENU_KEY.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new LmgSettingsScreen(null));
			}
		});
		LOGGER.info("{} loaded", CLIENT_NAME);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
