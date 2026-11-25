package dev.lmg.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Draws the cosmetics YOU have equipped (only on your own player). Every cosmetic is a
 * data model from the catalog or an imported Blockbench model; see CosmeticRenderer.
 * Capes are handled separately by the vanilla cape layer (AvatarRendererMixin).
 */
public class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	/** True when this render state is the local player (you), not someone else. */
	public static boolean isYou(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && state.id == mc.player.getId();
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector nodes, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !isYou(state) || LmgConfig.get().hideCosmetics) return;
		boolean elytra = state.chestEquipment.is(Items.ELYTRA) || state.isFallFlying;
		for (Map.Entry<String, String> e : LmgConfig.get().equipped.entrySet()) {
			if ("capes".equals(e.getKey())) continue;
			CosmeticModel model = CosmeticRegistry.get(e.getValue());
			if (model == null || model.parts.isEmpty()) continue;
			if (elytra && model.hideWithElytra) continue;
			CosmeticRenderer.render(pose, nodes, light, state, getParentModel(), model,
					CosmeticRegistry.colorFor(model, state.ageInTicks));
		}
	}
}
