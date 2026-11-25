package dev.lmg.client.hud;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Your potion effects as a tidy list on the right: icon, name + level, time left. */
public class PotionHud implements HudElement {
	private static final int ROW = 20;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!LmgConfig.get().potionHud || mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
		List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
		if (effects.isEmpty()) return;
		effects.sort(Comparator.comparing((MobEffectInstance e) -> !e.getEffect().value().isBeneficial())
				.thenComparing(e -> -e.getDuration()));

		Font font = mc.font;
		float tickRate = mc.level.tickRateManager().tickrate();
		int width = 0;
		List<String> names = new ArrayList<>();
		List<String> times = new ArrayList<>();
		for (MobEffectInstance e : effects) {
			String name = e.getEffect().value().getDisplayName().getString();
			if (e.getAmplifier() > 0) name += " " + Component.translatable("enchantment.level." + (e.getAmplifier() + 1)).getString();
			String time = e.isInfiniteDuration() ? "∞" : MobEffectUtil.formatDuration(e, 1f, tickRate).getString();
			names.add(name);
			times.add(time);
			width = Math.max(width, Math.max(font.width(name), font.width(time)));
		}
		int panelW = 18 + 8 + width + 8;
		int x = g.guiWidth() - panelW - 5;
		int y = g.guiHeight() / 3 - effects.size() * ROW / 2;
		long ms = System.currentTimeMillis();

		for (int i = 0; i < effects.size(); i++) {
			MobEffectInstance e = effects.get(i);
			int ry = y + i * (ROW + 2);
			boolean good = e.getEffect().value().isBeneficial();
			// blink when about to run out
			float alpha = e.endsWithin(200) && !e.isInfiniteDuration() ? 0.55f + 0.45f * (float) Math.abs(Math.sin(ms / 250.0)) : 1f;
			Draw.roundRect(g, x, ry, x + panelW, ry + ROW, 5, Draw.withAlpha(0x0E0E12, 0.75f));
			g.fill(x, ry + 4, x + 2, ry + ROW - 4, Draw.withAlpha(good ? 0x6BFF9A : 0xFF6B6B, alpha));
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), x + 5, ry + 1, 18, 18);
			g.text(font, names.get(i), x + 27, ry + 2, Draw.white(alpha), false);
			g.text(font, times.get(i), x + 27, ry + 11, Draw.withAlpha(0x9A9AA6, alpha), false);
		}
	}
}
