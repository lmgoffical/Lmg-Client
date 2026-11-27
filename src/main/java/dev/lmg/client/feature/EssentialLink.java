package dev.lmg.client.feature;

import dev.lmg.client.LmgClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * Essential (installed by the Lmg app) handles playing with friends: hosting your world, inviting people and
 * its friends list. Lmg's own title screen replaces Minecraft's, which is where Essential puts its buttons, so
 * this opens Essential's friends menu for us. Uses reflection so Lmg still works without Essential.
 */
public final class EssentialLink {
	private EssentialLink() {}

	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("essential");
	}

	/** Opens Essential's friends menu; false if that didn't work (Essential missing or changed). */
	public static boolean openFriends() {
		try {
			Object menu = Class.forName("gg.essential.gui.friends.SocialMenu").getConstructor().newInstance();
			Class.forName("gg.essential.api.utils.GuiUtil").getMethod("open", Screen.class).invoke(null, menu);
			return true;
		} catch (Throwable t) {
			LmgClient.LOGGER.warn("Couldn't open Essential's friends menu", t);
			return false;
		}
	}
}
