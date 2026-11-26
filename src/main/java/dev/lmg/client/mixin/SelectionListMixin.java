package dev.lmg.client.mixin;

import dev.lmg.client.screen.VanillaSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settings lists, world lists and server lists: a flat navy panel and thin lines instead of dirt-dark textures. */
@Mixin(AbstractSelectionList.class)
public abstract class SelectionListMixin {
	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
	private void lmg$listBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (!VanillaSkin.on()) return;
		AbstractWidget self = (AbstractWidget) (Object) this;
		VanillaSkin.listBackground(g, self.getX(), self.getY(), self.getRight(), self.getBottom());
		ci.cancel();
	}

	@Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
	private void lmg$listLines(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (!VanillaSkin.on()) return;
		AbstractWidget self = (AbstractWidget) (Object) this;
		VanillaSkin.listSeparators(g, self.getX(), self.getY(), self.getRight(), self.getBottom());
		ci.cancel();
	}
}
