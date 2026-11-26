package dev.lmg.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hotbar styles. Only the bar and the selection box are swapped; the items, counts and the
 * attack indicator are still the game's own.
 */
@Mixin(Hud.class)
public abstract class HotbarStyleMixin {
	@Redirect(method = "extractItemHotbar",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void lmg$hotbar(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		LmgConfig c = LmgConfig.get();
		String path = sprite.getPath();
		if (c.hotbarStyle == LmgConfig.HotbarStyle.VANILLA || !path.startsWith("hud/hotbar") || path.contains("attack_indicator")) {
			g.blitSprite(pipeline, sprite, x, y, w, h);
			return;
		}
		int col = c.hotbarRgb < 0 ? c.accentRgb() : c.hotbarRgb;
		float op = c.hotbarOpacity;
		switch (path) {
			case "hud/hotbar" -> bar(g, c.hotbarStyle, x, y, w, h, col, op);
			case "hud/hotbar_selection" -> selection(g, c.hotbarStyle, x + 1, y + 1, 22, 22, col);
			default -> {
				// the offhand slot: a single box the same style as the bar
				int sx = path.endsWith("left") ? x : x + w - 22;
				bar(g, c.hotbarStyle, sx, y + 1, 22, 22, col, op);
			}
		}
	}

	private static void bar(GuiGraphicsExtractor g, LmgConfig.HotbarStyle style, int x, int y, int w, int h, int col, float op) {
		switch (style) {
			case LMG -> {
				Draw.roundRect(g, x, y, x + w, y + h, 5, Draw.withAlpha(0x0C1322, op));
				// faint lines between the slots
				for (int sx = x + 21; sx < x + w - 2; sx += 20) g.fill(sx, y + 5, sx + 1, y + h - 5, Draw.withAlpha(0xFFFFFF, 0.06f * op + 0.02f));
			}
			case CLEAR -> Draw.roundRect(g, x, y, x + w, y + h, 4, Draw.withAlpha(0x000000, 0.18f * op));
			case OUTLINE -> {
				for (int sx = x + 1; sx + 20 <= x + w; sx += 20) outline(g, sx, y + 1, sx + 20, y + h - 1, Draw.withAlpha(col, 0.35f * op + 0.1f));
			}
			default -> {
			}
		}
	}

	private static void selection(GuiGraphicsExtractor g, LmgConfig.HotbarStyle style, int x, int y, int w, int h, int col) {
		switch (style) {
			case LMG -> {
				Draw.roundRect(g, x, y, x + w, y + h, 5, Draw.withAlpha(col, 0.22f));
				Draw.roundRect(g, x + 4, y + h - 3, x + w - 4, y + h - 1, 1, Draw.withAlpha(col, 1f));
			}
			case CLEAR -> {
				Draw.roundRect(g, x + 1, y + 1, x + w - 1, y + h - 1, 4, Draw.withAlpha(0xFFFFFF, 0.14f));
				g.fill(x + 5, y + h - 2, x + w - 5, y + h - 1, Draw.withAlpha(col, 1f));
			}
			case OUTLINE -> {
				outline(g, x, y, x + w, y + h, Draw.withAlpha(col, 1f));
				outline(g, x + 1, y + 1, x + w - 1, y + h - 1, Draw.withAlpha(col, 0.5f));
			}
			default -> {
			}
		}
	}

	private static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int col) {
		g.fill(x0, y0, x1, y0 + 1, col);
		g.fill(x0, y1 - 1, x1, y1, col);
		g.fill(x0, y0, x0 + 1, y1, col);
		g.fill(x1 - 1, y0, x1, y1, col);
	}
}
