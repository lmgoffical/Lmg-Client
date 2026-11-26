package dev.lmg.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.feature.PhotoMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * An in-game vignette (darker corners) and film grain, drawn over the world under the HUD.
 * Cheap: two small textures, no shaders. Photo mode has its own, so these step aside there.
 */
public final class LensOverlay {
	private static final Identifier VIGNETTE = LmgClient.id("lens/vignette");
	private static final Identifier GRAIN = LmgClient.id("lens/grain");
	private static boolean made;
	private static final Random RANDOM = new Random();

	private LensOverlay() {}

	public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		LmgConfig c = LmgConfig.get();
		if ((c.lensVignette == 0 && c.lensGrain == 0) || PhotoMode.active()) return;
		make();
		int w = g.guiWidth(), h = g.guiHeight();
		if (c.lensVignette > 0) {
			float a = c.lensVignette == 1 ? 0.55f : 0.95f;
			g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, w, h, 128, 128, 128, 128, Draw.withAlpha(0xFFFFFF, a));
		}
		if (c.lensGrain > 0) {
			int ox = RANDOM.nextInt(128), oy = RANDOM.nextInt(128);
			int tint = Draw.withAlpha(0xFFFFFF, c.lensGrain == 1 ? 0.6f : 1f);
			for (int y = -oy; y < h; y += 128) {
				for (int x = -ox; x < w; x += 128) g.blit(RenderPipelines.GUI_TEXTURED, GRAIN, x, y, 0f, 0f, 128, 128, 128, 128, 128, 128, tint);
			}
		}
	}

	private static void make() {
		if (made) return;
		made = true;
		NativeImage v = new NativeImage(128, 128, false);
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				double dx = (x + 0.5) / 64.0 - 1, dy = (y + 0.5) / 64.0 - 1;
				double d = Math.sqrt(dx * dx + dy * dy) / Math.sqrt(2);
				double t = Mth.clamp((d - 0.38) / 0.62, 0, 1);
				int a = (int) Math.round(Math.pow(t * t * (3 - 2 * t), 1.3) * 230);
				v.setPixel(x, y, a << 24);
			}
		}
		Minecraft.getInstance().getTextureManager().register(VIGNETTE, new DynamicTexture(() -> "Lmg lens vignette", v));
		NativeImage n = new NativeImage(128, 128, false);
		Random r = new Random(11);
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				int l = r.nextInt(256);
				int a = 6 + r.nextInt(18);
				n.setPixel(x, y, a << 24 | l << 16 | l << 8 | l);
			}
		}
		Minecraft.getInstance().getTextureManager().register(GRAIN, new DynamicTexture(() -> "Lmg lens grain", n));
	}
}
