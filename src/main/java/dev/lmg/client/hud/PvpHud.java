package dev.lmg.client.hud;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.feature.Visuals;
import dev.lmg.client.render.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small PvP panel: clicks per second, your combo (hits in a row without getting hit back),
 * and how far away your last hit landed. Only counts hits the server confirmed.
 */
public class PvpHud implements HudElement {
	private static long comboChangedAt;
	private static int shownCombo;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LmgConfig c = LmgConfig.get();
		if (!c.pvpHud || mc.player == null || mc.gui.hud.isHidden()) return;
		Font font = mc.font;
		boolean sample = HudLayout.editing;
		List<String[]> lines = new ArrayList<>();
		if (c.pvpCps) lines.add(new String[]{"CPS", sample ? "11 | 4" : Visuals.leftCps() + " | " + Visuals.rightCps()});
		int combo = sample ? 6 : Visuals.combo();
		if (combo != shownCombo) {
			shownCombo = combo;
			comboChangedAt = Util.getMillis();
		}
		if (c.pvpCombo) lines.add(new String[]{"Combo", combo > 0 ? combo + "x" : "-"});
		double reach = sample ? 2.84 : Visuals.lastReach();
		if (c.pvpReach) lines.add(new String[]{"Reach", reach < 0 ? "-" : String.format(Locale.ROOT, "%.2f", reach)});
		if (lines.isEmpty()) return;

		int labelW = 0, valueW = 0;
		for (String[] l : lines) {
			labelW = Math.max(labelW, font.width(l[0]));
			valueW = Math.max(valueW, font.width(l[1]));
		}
		int w = 8 + labelW + 10 + Math.max(valueW, 28) + 8, h = 6 + lines.size() * 11 + 3;
		int[] at = HudLayout.place(HudLayout.Element.PVP, 6, g.guiHeight() / 2 - h / 2, w, h, g.guiWidth(), g.guiHeight());
		HudLayout.report(HudLayout.Element.PVP, at[0], at[1], w, h);
		int x = at[0], y = at[1];
		int acc = c.accentRgb();
		Draw.roundRect(g, x, y, x + w, y + h, 5, Draw.withAlpha(0x0E0E12, 0.72f));
		g.fill(x, y + 4, x + 2, y + h - 4, Draw.withAlpha(acc, 0.9f));
		int ty = y + 6;
		for (String[] l : lines) {
			g.text(font, l[0], x + 8, ty, Draw.withAlpha(0x9DAAC0, 1f), false);
			int col = 0xE8EDF5;
			float scale = 1f;
			if (l[0].equals("Combo") && combo > 0) {
				// the combo number flashes your colour and pops when it goes up
				float since = (Util.getMillis() - comboChangedAt) / 250f;
				col = Draw.lerpRgb(acc, 0xFFFFFF, Math.min(1f, since));
				scale = 1f + 0.25f * Math.max(0f, 1f - since);
			}
			int vw = font.width(l[1]);
			if (scale != 1f) {
				Draw.scaledText(g, font, l[1], x + w - 8 - vw / 2f, ty - (scale - 1f) * 4, scale, Draw.withAlpha(col, 1f));
			} else {
				g.text(font, l[1], x + w - 8 - vw, ty, Draw.withAlpha(col, 1f), false);
			}
			ty += 11;
		}
	}
}
