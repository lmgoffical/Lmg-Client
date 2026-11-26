package dev.lmg.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Your held item's look in first person: where it sits, how it's turned and how big it is.
 * The position and angle go in right after the game places the hand, so swinging and eating
 * still animate from the new spot. The size goes in just before the item itself is drawn.
 * The left hand gets the mirror image.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonItemMixin {
	/** No hand floating in front of the camera in photo mode or freecam (it's not where you are). */
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void lmg$noHandWhenAway(CallbackInfo ci) {
		if (dev.lmg.client.feature.PhotoMode.active() || dev.lmg.client.feature.Freecam.on()
				|| dev.lmg.client.social.PartnerPlay.myCamera(0f) != null) ci.cancel();
	}

	@Inject(method = "applyItemArmTransform", at = @At("TAIL"))
	private void lmg$placeItem(PoseStack pose, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
		LmgConfig c = LmgConfig.get();
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		if (c.itemX != 0f || c.itemY != 0f || c.itemZ != 0f) pose.translate(side * c.itemX, c.itemY, c.itemZ);
		if (c.itemTurn != 0f) pose.rotateDegrees(Axis.YP, side * c.itemTurn);
		if (c.itemTilt != 0f) pose.rotateDegrees(Axis.XP, c.itemTilt);
		if (c.itemRoll != 0f) pose.rotateDegrees(Axis.ZP, side * c.itemRoll);
	}

	@Inject(method = "submitArmWithItem",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void lmg$sizeItem(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partialTick,
							  float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
							  PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
		float scale = LmgConfig.get().itemSize;
		if (scale != 1f) pose.scale(scale, scale, scale);
	}
}
