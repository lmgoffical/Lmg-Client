package dev.lmg.client.mixin;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the title screen slowly turn the skin preview. */
@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
	@Accessor("rotationY")
	float lmg$getRotationY();

	@Accessor("rotationY")
	void lmg$setRotationY(float value);
}
