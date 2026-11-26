package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your colour washed over chests and the inventory (under the items, so they keep their colours). */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryTintMixin {
	@Shadow protected int leftPos;
	@Shadow protected int topPos;
	@Shadow @org.spongepowered.asm.mixin.Final protected int imageWidth;
	@Shadow @org.spongepowered.asm.mixin.Final protected int imageHeight;

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void lmg$tint(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		LmgConfig c = LmgConfig.get();
		if (c.inventoryTint < 0) return;
		// a couple of pixels in from the edge, so the rounded corners stay clean
		g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3,
				Draw.withAlpha(c.inventoryTint, c.inventoryTintStrength));
	}
}
