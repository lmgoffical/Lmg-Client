package dev.lmg.client;

import dev.lmg.client.hud.HudPill;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LmgClient implements ClientModInitializer {
	/** Change this one line to rename the client everywhere. */
	public static final String CLIENT_NAME = "Lmg Client";
	public static final String MOD_ID = "lmgclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(CLIENT_NAME);

	@Override
	public void onInitializeClient() {
		LmgConfig.load();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud_pill"), new HudPill());
		LOGGER.info("{} loaded", CLIENT_NAME);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
