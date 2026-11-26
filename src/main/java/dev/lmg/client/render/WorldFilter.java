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

	private static final Identifier[] BLUR = {null, LmgClient.id("motion_blur_light"), LmgClient.id("motion_blur_medium"), LmgClient.id("motion_blur_strong")};

	/** The colour filter for right now (photo mode's, the grey behind menus, or the one you picked). */
	public static Identifier current() {
		Minecraft mc = Minecraft.getInstance();
		if (PhotoMode.active()) return PhotoMode.filter.effect;
		if (mc.level != null && mc.gui.screen() instanceof GreyBehind && LmgConfig.get().menuGreyWorld) return MENU_GREY;
		var f = LmgConfig.get().worldFilter;
		return f == null ? null : f.effect;
	}

	/** Motion blur, if it's on (not in photo mode, where it would smear the shot). */
	public static Identifier blur() {
		int b = LmgConfig.get().motionBlur;
		return PhotoMode.active() || b <= 0 || b >= BLUR.length ? null : BLUR[b];
	}

	/** True if the grey filter really runs (if a graphics driver can't, the menus darken more instead). */
	public static boolean greyWorking() {
		Minecraft mc = Minecraft.getInstance();
		return LmgConfig.get().menuGreyWorld && mc.gameRenderer.getAppliedPostEffects().contains(MENU_GREY);
	}
}
