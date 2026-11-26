package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.screen.LmgTitleScreen;
import dev.lmg.client.screen.SplashScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Whenever the game tries to open the vanilla title screen, open ours instead. */
@Mixin(Gui.class)
public class GuiMixin {
	@Unique
	private static boolean lmg$splashShown;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen lmg$replaceTitleScreen(Screen screen) {
		// (Fabric's automated in-game tests expect the vanilla title screen, so they keep it)
		if (screen != null && screen.getClass() == TitleScreen.class && System.getProperty("fabric.client.gametest") == null) {
			if (!lmg$splashShown && LmgConfig.get().showSplash) {
				lmg$splashShown = true;
				return new SplashScreen();
			}
			lmg$splashShown = true;
			return new LmgTitleScreen(false);
		}
		return screen;
	}
}
