package dev.lmg.legacy.render;

import dev.lmg.legacy.Colors;
import dev.lmg.legacy.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Halo, China hat and cat ears on your own head, in the colour you pick. Head space: 1 unit = 1 block,
 * the top of the head is y = -0.5, y points down, the face is -z.
 */
public class CosmeticLayer implements LayerRenderer<AbstractClientPlayer> {
	private final RenderPlayer renderer;

	public CosmeticLayer(RenderPlayer renderer) {
		this.renderer = renderer;
	}

	@Override
	public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
							  float ageInTicks, float netHeadYaw, float headPitch, float scale) {
		Config c = Config.get();
		if (p != Minecraft.getMinecraft().thePlayer || p.isInvisible()) return;
		if (!c.halo && !c.chinaHat && !c.catEars) return;
		int rgb = Colors.rgb(c.cosmeticColor);
		float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;

		GlStateManager.pushMatrix();
		if (p.isSneaking()) GlStateManager.translate(0f, 0.2f, 0f);
		renderer.getMainModel().bipedHead.postRender(0.0625f);
		GlStateManager.disableTexture2D();
		GlStateManager.disableCull();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

		if (c.chinaHat) {
			GlStateManager.pushMatrix();
			GlStateManager.rotate(ageInTicks * 0.6f, 0, 1, 0);
			cone(0.47f, -0.53f, -0.78f, r, g, b);
			GlStateManager.popMatrix();
		}
		if (c.catEars) {
			for (int s = -1; s <= 1; s += 2) ear(s, r * 0.55f, g * 0.55f, b * 0.55f);
		}
		if (c.halo) {
			// glows: ignore the world's light
			GlStateManager.disableLighting();
			float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
			GlStateManager.pushMatrix();
			GlStateManager.translate(0f, (float) (-0.72 + Math.sin(ageInTicks * 0.08) * 0.02), 0f);
			GlStateManager.rotate(8f, 1, 0, 0);
			ring(0.2f, 0.27f, r, g, b, 1f, 1f);
			ring(0.27f, 0.36f, r, g, b, 0.45f, 0f);
			ring(0.2f, 0.14f, r, g, b, 0.4f, 0f);
			GlStateManager.popMatrix();
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
			GlStateManager.enableLighting();
		}

		GlStateManager.disableBlend();
		GlStateManager.enableCull();
		GlStateManager.enableTexture2D();
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.popMatrix();
	}

	/** Flat ring in the XZ plane from radius r0 (alpha a0) to r1 (alpha a1). */
	private static void ring(float r0, float r1, float r, float g, float b, float a0, float a1) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
		for (int i = 0; i <= 40; i++) {
			double ang = Math.PI * 2 * i / 40;
			double cs = Math.cos(ang), sn = Math.sin(ang);
			w.pos(cs * r0, 0, sn * r0).color(r, g, b, a0).endVertex();
			w.pos(cs * r1, 0, sn * r1).color(r, g, b, a1).endVertex();
		}
		t.draw();
	}

	/** See-through cone: base radius at yBase, tip at yTip. */
	private static void cone(float radius, float yBase, float yTip, float r, float g, float b) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
		w.pos(0, yTip, 0).color(Math.min(1f, r * 1.15f), Math.min(1f, g * 1.15f), Math.min(1f, b * 1.15f), 0.85f).endVertex();
		for (int i = 0; i <= 32; i++) {
			double ang = Math.PI * 2 * i / 32;
			float shade = i % 2 == 0 ? 1f : 0.88f;
			w.pos(Math.cos(ang) * radius, yBase, Math.sin(ang) * radius).color(r * shade, g * shade, b * shade, 0.78f).endVertex();
		}
		t.draw();
	}

	/** One triangular cat ear on top of the head, side s = -1 (right) or 1 (left). */
	private static void ear(int s, float r, float g, float b) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		float x = s * 0.16f, top = -0.5f, h = 0.2f, half = 0.1f, z = -0.04f;
		for (float dz : new float[]{z - 0.025f, z + 0.025f}) {
			w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
			w.pos(x - half, top, dz).color(r, g, b, 1f).endVertex();
			w.pos(x + half, top, dz).color(r, g, b, 1f).endVertex();
			w.pos(x + s * 0.02f, top - h, dz).color(r, g, b, 1f).endVertex();
			t.draw();
		}
		// pink inside
		w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
		w.pos(x - half * 0.55f, top - 0.01f, z - 0.03f).color(1f, 0.66f, 0.76f, 1f).endVertex();
		w.pos(x + half * 0.55f, top - 0.01f, z - 0.03f).color(1f, 0.66f, 0.76f, 1f).endVertex();
		w.pos(x + s * 0.02f, top - h * 0.72f, z - 0.03f).color(1f, 0.66f, 0.76f, 1f).endVertex();
		t.draw();
	}

	@Override
	public boolean shouldCombineTextures() {
		return false;
	}
}
