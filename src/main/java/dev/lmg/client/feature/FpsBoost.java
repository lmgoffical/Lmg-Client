package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

import java.util.Map;

/**
 * FPS boost: turns down the Minecraft settings that cost the most for the least you'd notice.
 * Your old values are saved, and turning it off puts every one of them back.
 */
public final class FpsBoost {
	private FpsBoost() {}

	/** What it changes, in plain words (shown in the menu). */
	public static final String WHAT = "Entity shadows off, biome blend off, fewer particles, no improved transparency, render distance 12 max, simulation 8 max, weather radius 5 max.";

	public static void set(boolean on) {
		LmgConfig c = LmgConfig.get();
		if (on == c.fpsBoost) return;
		Options o = Minecraft.getInstance().options;
		Map<String, String> saved = c.fpsBoostSaved;
		if (on) {
			saved.clear();
			saved.put("entityShadows", String.valueOf(o.entityShadows().get()));
			saved.put("biomeBlendRadius", String.valueOf(o.biomeBlendRadius().get()));
			saved.put("particles", o.particles().get().name());
			saved.put("improvedTransparency", String.valueOf(o.improvedTransparency().get()));
			saved.put("renderDistance", String.valueOf(o.renderDistance().get()));
			saved.put("simulationDistance", String.valueOf(o.simulationDistance().get()));
			saved.put("weatherRadius", String.valueOf(o.weatherRadius().get()));

			o.entityShadows().set(false);
			o.biomeBlendRadius().set(0);
			if (o.particles().get() == ParticleStatus.ALL) o.particles().set(ParticleStatus.DECREASED);
			o.improvedTransparency().set(false);
			o.renderDistance().set(Math.min(o.renderDistance().get(), 12));
			o.simulationDistance().set(Math.min(o.simulationDistance().get(), 8));
			o.weatherRadius().set(Math.min(o.weatherRadius().get(), 5));
		} else {
			restoreBool(o.entityShadows(), saved.get("entityShadows"));
			restoreInt(o.biomeBlendRadius(), saved.get("biomeBlendRadius"));
			try {
				if (saved.containsKey("particles")) o.particles().set(ParticleStatus.valueOf(saved.get("particles")));
			} catch (Exception ignored) {
			}
			restoreBool(o.improvedTransparency(), saved.get("improvedTransparency"));
			restoreInt(o.renderDistance(), saved.get("renderDistance"));
			restoreInt(o.simulationDistance(), saved.get("simulationDistance"));
			restoreInt(o.weatherRadius(), saved.get("weatherRadius"));
			saved.clear();
		}
		c.fpsBoost = on;
		o.save();
		LmgConfig.save();
		// (Minecraft's own settings redraw the world themselves when they change.)
	}

	private static void restoreBool(OptionInstance<Boolean> opt, String v) {
		if (v != null) opt.set(Boolean.parseBoolean(v));
	}

	private static void restoreInt(OptionInstance<Integer> opt, String v) {
		try {
			if (v != null) opt.set(Integer.parseInt(v));
		} catch (Exception ignored) {
		}
	}
}
