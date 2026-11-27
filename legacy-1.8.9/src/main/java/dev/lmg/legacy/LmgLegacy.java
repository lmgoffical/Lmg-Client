package dev.lmg.legacy;

import dev.lmg.legacy.feature.Keys;
import dev.lmg.legacy.render.CapeLayer;
import dev.lmg.legacy.render.CosmeticLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Lmg Client for Minecraft 1.8.9 (Forge): HUD, zoom, crosshair, capes, the Lmg menu. Client-side and visual only. */
@Mod(modid = LmgLegacy.MODID, name = "Lmg Client", version = LmgLegacy.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class LmgLegacy {
	public static final String MODID = "lmgclient";
	public static final String VERSION = "1.0.0";
	public static final Logger LOGGER = LogManager.getLogger("Lmg Client");

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		Config.load();
		Keys.register();
		MinecraftForge.EVENT_BUS.register(new Events());
		LOGGER.info("Lmg Client 1.8.9 loaded");
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		for (RenderPlayer r : Minecraft.getMinecraft().getRenderManager().getSkinMap().values()) {
			r.addLayer(new CapeLayer(r));
			r.addLayer(new CosmeticLayer(r));
		}
	}
}
