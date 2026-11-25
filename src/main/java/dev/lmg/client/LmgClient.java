package dev.lmg.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lmg.client.cosmetic.CosmeticsLayer;
import dev.lmg.client.cosmetic.Effects;
import dev.lmg.client.hud.HudPill;
import dev.lmg.client.screen.LmgSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
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
		UpdateChecker.start();
		KeyMappingHelper.registerKeyMapping(OPEN_MENU_KEY);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud_pill"), new HudPill());

		// Wings, halo, crown, cat ears and China hat on player models (only drawn for you).
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new CosmeticsLayer(parent));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Effects.tick(mc);
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
