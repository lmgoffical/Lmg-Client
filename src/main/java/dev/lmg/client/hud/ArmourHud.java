package dev.lmg.client.hud;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Your armour (and what you're holding) with a durability bar each, so you see a piece about to
 * break before it does. Bars go green, yellow, red; under 10% they blink.
 */
public class ArmourHud implements HudElement {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LmgConfig c = LmgConfig.get();
		if (!c.armourHud || mc.player == null || mc.gui.hud.isHidden()) return;
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot s : SLOTS) {
			ItemStack st = mc.player.getItemBySlot(s);
			if (!st.isEmpty()) items.add(st);
		}
		if (c.armourHeld && !mc.player.getMainHandItem().isEmpty() && mc.player.getMainHandItem().isDamageableItem()) items.add(mc.player.getMainHandItem());
		if (items.isEmpty() && HudLayout.editing) {
			items.add(new ItemStack(Items.DIAMOND_HELMET));
			items.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
			items.add(new ItemStack(Items.IRON_LEGGINGS));
			items.add(new ItemStack(Items.DIAMOND_BOOTS));
		}
		if (items.isEmpty()) return;

		Font font = mc.font;
		int row = 18;
		int w = c.armourNumbers ? 76 : 44, h = items.size() * row + 4;
		int[] at = HudLayout.place(HudLayout.Element.ARMOUR, g.guiWidth() / 2 + 96, g.guiHeight() - h - 4, w, h, g.guiWidth(), g.guiHeight());
		HudLayout.report(HudLayout.Element.ARMOUR, at[0], at[1], w, h);
		int x = at[0], y = at[1];
		Draw.roundRect(g, x, y, x + w, y + h, 5, Draw.withAlpha(0x0E0E12, 0.6f));
		long ms = System.currentTimeMillis();
		for (int i = 0; i < items.size(); i++) {
			ItemStack st = items.get(i);
			int ry = y + 2 + i * row;
			g.item(st, x + 2, ry + 1);
			if (!st.isDamageableItem()) continue;
			float left = 1f - st.getDamageValue() / (float) Math.max(1, st.getMaxDamage());
			int col = left > 0.5f ? 0x6BD58A : left > 0.2f ? 0xF2C14E : 0xFF5A5A;
			float a = left < 0.1f ? 0.45f + 0.55f * (float) Math.abs(Math.sin(ms / 220.0)) : 1f;
			int bx0 = x + 21, bx1 = x + w - 4, by = ry + 7;
			if (c.armourNumbers) {
				String n = String.valueOf(st.getMaxDamage() - st.getDamageValue());
				g.text(font, n, bx1 - font.width(n), ry + 5, Draw.withAlpha(col, a), false);
				bx1 -= font.width("0000") + 2;
			}
			Draw.roundRect(g, bx0, by, bx1, by + 3, 1, Draw.withAlpha(0x2A3550, 0.9f));
			Draw.roundRect(g, bx0, by, bx0 + Math.max(1, Math.round((bx1 - bx0) * left)), by + 3, 1, Draw.withAlpha(col, a));
		}
	}
}
