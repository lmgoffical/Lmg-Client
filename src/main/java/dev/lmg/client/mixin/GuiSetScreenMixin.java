package dev.lmg.client.mixin;

import dev.lmg.client.screen.MinecraftSettingsScreen;
import dev.lmg.client.screen.VanillaSkin;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Opening Minecraft's Options (from the title screen or the pause menu) shows the Lmg settings hub instead. */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen lmg$optionsHub(Screen screen) {
		if (screen != null && screen.getClass() == OptionsScreen.class && VanillaSkin.on() && !MinecraftSettingsScreen.classic) {
			return new MinecraftSettingsScreen(((OptionsScreen) screen).getLastScreen());
		}
		return screen;
	}
}
