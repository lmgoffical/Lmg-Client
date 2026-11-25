package dev.lmg.client.hud;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Small panel next to the crosshair about what you're aiming at:
 * name, distance, health bar (with absorption), armor + held items, and potion effects
 * (effects only show when the server has told your game about them).
 */
public class TargetHud implements HudElement {
	private static final int WIDTH = 150;
	private static final EquipmentSlot[] GEAR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private LivingEntity shown;
	private float appear;
	private float healthShown = -1;
	private long lastNanos = -1;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LmgConfig cfg = LmgConfig.get();
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 0 : (float) Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;

		LivingEntity target = cfg.targetHud && !mc.gui.hud.isHidden() ? Targeting.current() : null;
		if (target != null && target != shown) {
			shown = target;
			healthShown = target.getHealth();
		}
		appear = Draw.approach(appear, target != null ? 1f : 0f, 12f * Theme.speed(), dt);
		if (appear < 0.02f || shown == null || mc.player == null) return;

		LivingEntity e = shown;
		Font font = mc.font;
		float health = Math.max(0, e.getHealth());
		float max = Math.max(1, e.getMaxHealth());
		float absorption = e.getAbsorptionAmount();
		healthShown = Draw.approach(healthShown, health, 8f, dt);

		List<ItemStack> gear = new ArrayList<>();
		for (EquipmentSlot slot : GEAR) {
			ItemStack stack = e.getItemBySlot(slot);
			if (!stack.isEmpty()) gear.add(stack);
		}
		List<MobEffectInstance> effects = new ArrayList<>(e.getActiveEffects());

		int height = 34 + (gear.isEmpty() ? 0 : 14) + (effects.isEmpty() ? 0 : 12);
		int x = g.guiWidth() / 2 + 14;
		int y = g.guiHeight() / 2 + 10;
		float slide = (1 - Draw.easeOutCubic(appear)) * 6;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + slide, y);

		Draw.roundRect(g, 0, 0, WIDTH, height, 6, Draw.withAlpha(0x0E0E12, 0.82f * appear));
		g.fill(6, 0, WIDTH - 6, 1, Draw.white(0.08f * appear));

		// name + distance
		String name = e.getDisplayName().getString();
		int maxName = WIDTH - 50;
		if (font.width(name) > maxName) name = font.plainSubstrByWidth(name, maxName - 6) + "...";
		g.text(font, name, 7, 6, Draw.white(appear), false);
		String dist = String.format("%.1fm", e.distanceTo(mc.player));
		g.text(font, dist, WIDTH - 7 - font.width(dist), 6, Draw.withAlpha(0x8A8A96, appear), false);

		// health bar
		int barX = 7, barY = 19, barW = WIDTH - 14, barH = 6;
		float frac = Math.min(1f, healthShown / max);
		int barColor = Draw.lerpRgb(0xFF4040, Theme.accentSoft(), Math.min(1f, frac * 1.4f));
		Draw.roundRect(g, barX, barY, barX + barW, barY + barH, 3, Draw.withAlpha(0x000000, 0.5f * appear));
		Draw.roundRect(g, barX, barY, barX + Math.max(3, Math.round(barW * frac)), barY + barH, 3, Draw.withAlpha(barColor, appear));
		if (absorption > 0) {
			int absW = Math.round(barW * Math.min(1f, absorption / max));
			Draw.roundRect(g, barX, barY, barX + Math.max(3, absW), barY + 2, 1, Draw.withAlpha(0xFFD24A, appear));
		}
		String hp = String.format(absorption > 0 ? "%.1f +%.0f ❤" : "%.1f ❤", health, absorption);
		g.text(font, hp, WIDTH - 7 - font.width(hp), barY + barH + 2, Draw.withAlpha(0xFF6B6B, appear), false);
		g.text(font, "/ " + Math.round(max), 7, barY + barH + 2, Draw.withAlpha(0x6E6E78, appear), false);

		int rowY = 34;
		// armor + held items, drawn a bit smaller
		if (!gear.isEmpty()) {
			pose.pushMatrix();
			pose.translate(7, rowY);
			pose.scale(0.75f, 0.75f);
			for (int i = 0; i < gear.size(); i++) g.item(gear.get(i), i * 18, 0);
			pose.popMatrix();
			rowY += 14;
		}
		// potion effects (only those your game knows about)
		if (!effects.isEmpty()) {
			int ex = 7;
			for (MobEffectInstance effect : effects) {
				if (ex > WIDTH - 20) break;
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()), ex, rowY, 9, 9);
				ex += 10;
				if (effect.getAmplifier() > 0) {
					String lvl = String.valueOf(effect.getAmplifier() + 1);
					g.text(font, lvl, ex, rowY + 1, Draw.white(appear), false);
					ex += font.width(lvl) + 3;
				} else {
					ex += 2;
				}
			}
		}
		pose.popMatrix();
	}
}
