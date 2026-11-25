package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.MenuBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives vanilla menus (server list, world list, options...) the dark Lmg
 * background when you're not in a world. In-game menus keep the normal blur.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Shadow public int width;
	@Shadow public int height;
	@Shadow @Final protected Minecraft minecraft;

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void lmg$themedBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (LmgConfig.get().themedMenus && minecraft.level == null) {
			MenuBackground.draw(g, width, height, false);
			ci.cancel();
		}
	}
}
