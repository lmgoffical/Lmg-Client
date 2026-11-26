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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import dev.lmg.client.sync.AvatarSync;

import java.util.Map;

/**
 * Draws the cosmetics YOU have equipped, and those of other Lmg players who chose to share theirs. Every cosmetic is a
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
		if (state.isInvisible) return;
		Map<String, String> equipped, colors;
		if (isYou(state)) {
			if (LmgConfig.get().hideCosmetics) return;
			equipped = LmgConfig.get().equipped;
			colors = LmgConfig.get().cosmeticColors;
		} else {
			// another Lmg player who shared their cosmetics
			AvatarSync.Shared shared = sharedFor(state.id);
			if (shared == null) return;
			equipped = shared.equipped();
			colors = shared.colors();
		}
		boolean elytra = state.chestEquipment.is(Items.ELYTRA) || state.isFallFlying;
		for (Map.Entry<String, String> e : equipped.entrySet()) {
			if ("capes".equals(e.getKey())) continue;
			CosmeticModel model = CosmeticRegistry.get(e.getValue());
			if (model == null || model.parts.isEmpty()) continue;
			if (elytra && model.hideWithElytra) continue;
			CosmeticRenderer.render(pose, nodes, light, state, getParentModel(), model,
					CosmeticRegistry.colorFor(model, colors.get(model.id), state.ageInTicks));
		}
	}

	/** Shared cosmetics of the player with this entity id, if they shared any. */
	public static AvatarSync.Shared sharedFor(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		Entity e = mc.level.getEntity(entityId);
		return e instanceof Player p ? AvatarSync.get(p.getUUID()) : null;
	}
}
