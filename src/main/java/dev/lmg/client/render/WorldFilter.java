package dev.lmg.client.render;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.feature.PhotoMode;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * A colour filter over the world (not the menus): the photo mode filter, or black and white
 * behind the Lmg menus so they stand out.
 */
public final class WorldFilter {
	public static final Identifier MENU_GREY = LmgClient.id("menu_grey");

	private WorldFilter() {}

	/** Screens that grey out the world behind them. */
	public interface GreyBehind {}

	public static Identifier current() {
		Minecraft mc = Minecraft.getInstance();
		if (PhotoMode.active()) return PhotoMode.filter.effect;
		if (mc.level != null && mc.gui.screen() instanceof GreyBehind && LmgConfig.get().menuGreyWorld) return MENU_GREY;
		return null;
	}

	/** True if the grey filter really runs (if a graphics driver can't, the menus darken more instead). */
	public static boolean greyWorking() {
		Minecraft mc = Minecraft.getInstance();
		return LmgConfig.get().menuGreyWorld && mc.gameRenderer.getAppliedPostEffects().contains(MENU_GREY);
	}
}
