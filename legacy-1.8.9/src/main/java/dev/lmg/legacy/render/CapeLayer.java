package dev.lmg.legacy.render;

import dev.lmg.legacy.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * Your Lmg cape (only on your own player, and only when you don't already have a Minecraft cape).
 * Same motion as Minecraft's own cape layer.
 */
public class CapeLayer implements LayerRenderer<AbstractClientPlayer> {
	public static final String[] CAPES = {"", "cape", "cape_aurora", "cape_royal", "cape_void", "cape_sunset", "cape_cherry",
			"cape_ocean", "cape_inferno", "cape_rainbow", "cape_forest", "cape_racer"};
	public static final String[] NAMES = {"None", "Lmg", "Aurora", "Royal", "Void", "Sunset", "Cherry Blossom",
			"Moonlit Sea", "Inferno", "Rainbow", "Night Forest", "Racer"};

	private final RenderPlayer renderer;

	public CapeLayer(RenderPlayer renderer) {
		this.renderer = renderer;
	}

	public static int index(String cape) {
		for (int i = 0; i < CAPES.length; i++) if (CAPES[i].equals(cape)) return i;
		return 0;
	}

	@Override
	public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
							  float ageInTicks, float netHeadYaw, float headPitch, float scale) {
		String cape = Config.get().cape;
		if (cape.isEmpty() || p != Minecraft.getMinecraft().thePlayer) return;
		if (p.isInvisible() || !p.isWearing(EnumPlayerModelParts.CAPE) || p.getLocationCape() != null) return;
		GlStateManager.color(1f, 1f, 1f, 1f);
		renderer.bindTexture(new ResourceLocation("lmgclient", "textures/capes/" + cape + ".png"));
		GlStateManager.pushMatrix();
		GlStateManager.translate(0f, 0f, 0.125f);
		double dx = p.prevChasingPosX + (p.chasingPosX - p.prevChasingPosX) * partialTicks - (p.prevPosX + (p.posX - p.prevPosX) * partialTicks);
		double dy = p.prevChasingPosY + (p.chasingPosY - p.prevChasingPosY) * partialTicks - (p.prevPosY + (p.posY - p.prevPosY) * partialTicks);
		double dz = p.prevChasingPosZ + (p.chasingPosZ - p.prevChasingPosZ) * partialTicks - (p.prevPosZ + (p.posZ - p.prevPosZ) * partialTicks);
		float yaw = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * partialTicks;
		double s = MathHelper.sin(yaw * (float) Math.PI / 180f), c = -MathHelper.cos(yaw * (float) Math.PI / 180f);
		float lift = MathHelper.clamp_float((float) dy * 10f, -6f, 32f);
		float back = (float) (dx * s + dz * c) * 100f;
		float side = (float) (dx * c - dz * s) * 100f;
		if (back < 0) back = 0;
		float bob = p.prevCameraYaw + (p.cameraYaw - p.prevCameraYaw) * partialTicks;
		lift += MathHelper.sin((p.prevDistanceWalkedModified + (p.distanceWalkedModified - p.prevDistanceWalkedModified) * partialTicks) * 6f) * 32f * bob;
		if (p.isSneaking()) lift += 25f;
		GlStateManager.rotate(6f + back / 2f + lift, 1f, 0f, 0f);
		GlStateManager.rotate(side / 2f, 0f, 0f, 1f);
		GlStateManager.rotate(-side / 2f, 0f, 1f, 0f);
		GlStateManager.rotate(180f, 0f, 1f, 0f);
		renderer.getMainModel().renderCape(0.0625f);
		GlStateManager.popMatrix();
	}

	@Override
	public boolean shouldCombineTextures() {
		return false;
	}
}
