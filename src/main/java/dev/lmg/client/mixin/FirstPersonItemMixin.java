package dev.lmg.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smaller / lower held items in first person. Runs right before the item itself is drawn,
 * after the arm movement, so only the item shrinks (swing and use animations still work).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonItemMixin {
	@Inject(method = "submitArmWithItem",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void lmg$smallItems(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partialTick,
								float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
								PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
		LmgConfig cfg = LmgConfig.get();
		if (cfg.lowerItems) pose.translate(0f, -0.08f, 0f);
		float scale = LmgConfig.ITEM_SCALES[cfg.itemScale];
		if (scale != 1f) pose.scale(scale, scale, scale);
	}
}
