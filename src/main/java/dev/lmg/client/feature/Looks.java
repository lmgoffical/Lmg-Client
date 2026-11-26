package dev.lmg.client.feature;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.config.ConfigProfiles;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * Three ways to change lots of settings at once:
 *  - starting looks (Clean, Showcase, PvP, Cosy): a good set of visuals to start from
 *  - profiles (Everyday, PvP, Screenshots): your own setups, swapped with one key
 *  - low-end mode: turns the heavy things down, and puts them back when you turn it off
 */
public final class Looks {
	private static final Gson GSON = new Gson();

	private Looks() {}

	// ---- starting looks -----------------------------------------------------------------------------

	public enum Start {
		CLEAN("Clean", "Plain sky, a dot, nothing in the way"),
		SHOWCASE("Showcase", "Pretty sky, sparkles, big sword, your star"),
		PVP("PvP", "Clear view, stats, hit sounds, reach colour"),
		COSY("Cosy", "Golden light, fireflies, soft corners");

		public final String label, about;

		Start(String label, String about) {
			this.label = label;
			this.about = about;
		}
	}

	/** Changes only the visual settings a look is about; everything else stays as you had it. */
	public static void apply(Start look) {
		LmgConfig c = LmgConfig.get();
		switch (look) {
			case CLEAN -> {
				c.skyTheme = LmgConfig.SkyTheme.VANILLA;
				c.timeOfDay = LmgConfig.TimeOfDay.VANILLA;
				c.aurora = LmgConfig.Aurora.OFF;
				c.trail = LmgConfig.Trail.OFF;
				c.ambient = LmgConfig.Ambient.OFF;
				c.worldFilter = null;
				c.motionBlur = 0;
				c.lensVignette = 0;
				c.lensGrain = 0;
				c.lensChroma = false;
				c.crosshair = LmgConfig.Crosshair.DOT;
				c.crosshairRgb = 0xFFFFFF;
				c.crosshairSpin = 0;
				c.damageNumbers = false;
				c.hotbarStyle = LmgConfig.HotbarStyle.CLEAR;
				c.nameTagBg = LmgConfig.NameTagBg.NONE;
				ItemLook.Preset.VANILLA.apply(c);
			}
			case SHOWCASE -> {
				c.skyTheme = LmgConfig.SkyTheme.COTTON_CANDY;
				c.timeOfDay = LmgConfig.TimeOfDay.SUNSET;
				c.aurora = LmgConfig.Aurora.PURPLE;
				c.trail = LmgConfig.Trail.SPARKLE;
				c.ambient = LmgConfig.Ambient.PETALS;
				c.worldFilter = PhotoMode.Filter.DREAM;
				c.lensVignette = 1;
				c.crosshair = LmgConfig.Crosshair.LOGO;
				c.crosshairRgb = 0xFF8FC8;
				c.crosshairSpin = 1;
				c.damageNumbers = true;
				c.damageStyle = LmgConfig.DamageStyle.BUBBLE;
				c.hotbarStyle = LmgConfig.HotbarStyle.LMG;
				c.nameTagBg = LmgConfig.NameTagBg.ACCENT;
				ItemLook.Preset.BIG_LOW.apply(c);
			}
			case PVP -> {
				c.skyTheme = LmgConfig.SkyTheme.VANILLA;
				c.timeOfDay = LmgConfig.TimeOfDay.VANILLA;
				c.fog = LmgConfig.FogLevel.LIGHT;
				c.aurora = LmgConfig.Aurora.OFF;
				c.trail = LmgConfig.Trail.OFF;
				c.ambient = LmgConfig.Ambient.OFF;
				c.worldFilter = null;
				c.motionBlur = 0;
				c.lensVignette = 0;
				c.lensGrain = 0;
				c.lensChroma = false;
				c.crosshair = LmgConfig.Crosshair.CROSS_DOT;
				c.crosshairRgb = 0xFFFFFF;
				c.crosshairSpin = 0;
				c.crosshairTargetColor = true;
				c.damageNumbers = true;
				c.damageStyle = LmgConfig.DamageStyle.OUTLINE;
				c.hitSound = LmgConfig.HitSound.DING;
				c.pvpHud = true;
				c.armourHud = true;
				c.targetOutline = true;
				c.targetHud = true;
				c.lowFire = true;
				c.noHurtCam = true;
				c.hotbarStyle = LmgConfig.HotbarStyle.OUTLINE;
				c.nameTagHealth = true;
				ItemLook.Preset.SMALL.apply(c);
			}
			case COSY -> {
				c.skyTheme = LmgConfig.SkyTheme.GOLDEN;
				c.timeOfDay = LmgConfig.TimeOfDay.SUNSET;
				c.aurora = LmgConfig.Aurora.GREEN;
				c.trail = LmgConfig.Trail.OFF;
				c.ambient = LmgConfig.Ambient.FIREFLIES;
				c.worldFilter = PhotoMode.Filter.WARM;
				c.lensVignette = 1;
				c.lensGrain = 1;
				c.crosshair = LmgConfig.Crosshair.DOT;
				c.crosshairRgb = 0xFFE3B0;
				c.hotbarStyle = LmgConfig.HotbarStyle.LMG;
				c.nameTagBg = LmgConfig.NameTagBg.DARK;
				ItemLook.Preset.LOW.apply(c);
			}
		}
		LmgConfig.save();
	}

	// ---- profiles -----------------------------------------------------------------------------------

	private static String file(String profile) {
		return "Profile - " + profile;
	}

	/**
	 * Saves what you have now into the profile you're leaving, then loads the other one. The first
	 * time you go to PvP or Screenshots it starts from your setup with the matching look on top.
	 */
	public static void switchTo(String profile, Consumer<String> say) {
		LmgConfig c = LmgConfig.get();
		if (profile.equals(c.activeProfile)) return;
		try {
			ConfigProfiles.saveCurrent(file(c.activeProfile));
			c.activeProfile = profile;
			if (ConfigProfiles.list().contains(file(profile))) {
				ConfigProfiles.load(file(profile));
				say.accept("Profile: " + profile);
			} else {
				if (profile.equals("PvP")) apply(Start.PVP);
				else if (profile.equals("Screenshots")) apply(Start.SHOWCASE);
				say.accept("Profile: " + profile + " (new - change anything, it's remembered)");
			}
			LmgConfig.save();
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Couldn't switch profile", e);
			say.accept("Couldn't switch profile: " + e.getMessage());
		}
	}

	/** The key: Everyday -> PvP -> Screenshots -> Everyday. */
	public static void next(Consumer<String> say) {
		String[] all = LmgConfig.PROFILES;
		int i = 0;
		for (int k = 0; k < all.length; k++) if (all[k].equals(LmgConfig.get().activeProfile)) i = k;
		switchTo(all[(i + 1) % all.length], say);
	}

	// ---- low-end mode -------------------------------------------------------------------------------

	/** What low-end mode turns down (and puts back exactly as it was). */
	private static final String[] HEAVY = {"fpsBoost", "aurora", "ambient", "trailFirstPerson", "motionBlur", "winterSettles",
			"minimapRefresh", "mapScan", "sparkles", "curtains", "lensChroma", "lensGrain", "followParticleSetting", "oreGlow"};

	public static void setLowEnd(boolean on) {
		LmgConfig c = LmgConfig.get();
		if (on == c.lowEnd) return;
		if (on) {
			JsonObject all = LmgConfig.toJsonTree().getAsJsonObject();
			JsonObject backup = new JsonObject();
			for (String k : HEAVY) if (all.has(k)) backup.add(k, all.get(k));
			c.lowEndBackup = backup.toString();
			FpsBoost.set(true);
			c.aurora = LmgConfig.Aurora.OFF;
			c.ambient = LmgConfig.Ambient.OFF;
			c.trailFirstPerson = false;
			c.motionBlur = 0;
			c.winterSettles = false;
			c.minimapRefresh = LmgConfig.MINIMAP_REFRESH_MS.length - 1;
			c.mapScan = LmgConfig.SCAN_SWEEP.length - 1;
			c.sparkles = 0;
			c.curtains = false;
			c.lensChroma = false;
			c.lensGrain = 0;
			c.followParticleSetting = true;
			c.oreGlow = false;
			if (Shaders.anyOn()) Shaders.off(msg -> {});
			c.lowEnd = true;
		} else {
			boolean boostBefore = false;
			try {
				JsonObject backup = JsonParser.parseString(c.lowEndBackup).getAsJsonObject();
				for (String k : HEAVY) {
					JsonElement v = backup.get(k);
					if (v == null) continue;
					if (k.equals("fpsBoost")) {
						boostBefore = v.getAsBoolean();
						continue;
					}
					Field f = LmgConfig.class.getField(k);
					f.set(c, GSON.fromJson(v, f.getGenericType()));
				}
			} catch (Exception e) {
				LmgClient.LOGGER.debug("Low-end backup unreadable", e);
			}
			FpsBoost.set(boostBefore);
			c.lowEnd = false;
			c.lowEndBackup = "";
		}
		LmgConfig.save();
	}
}
