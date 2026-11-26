package dev.lmg.client.world;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.LmgConfig.SkyTheme;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * Client-side world look: sky themes, fog, clouds, stars and time of day.
 * Nothing here is sent to servers - it only changes what you see.
 */
public final class WorldVisuals {
	private WorldVisuals() {}

	/** A sky look: colors for full day and full night; we blend between them as the sun moves. */
	private record Palette(Vector3fc skyDay, Vector3fc skyNight, Vector3fc fogDay, Vector3fc fogNight,
						   Vector3fc glow, float glowStrength, boolean darkSky) {}

	private static Palette palette(SkyTheme theme) {
		return switch (theme) {
			case VANILLA -> null;
			case SUNSET -> new Palette(rgb(1.00f, 0.56f, 0.36f), rgb(0.13f, 0.05f, 0.13f),
					rgb(1.00f, 0.68f, 0.48f), rgb(0.12f, 0.05f, 0.10f), rgb(1.00f, 0.38f, 0.18f), 1.2f, false);
			case INFERNO -> new Palette(rgb(0.95f, 0.33f, 0.07f), rgb(0.33f, 0.06f, 0.02f),
					rgb(1.00f, 0.45f, 0.14f), rgb(0.30f, 0.06f, 0.02f), rgb(1.00f, 0.55f, 0.10f), 1.3f, false);
			case MIDNIGHT -> new Palette(rgb(0.10f, 0.14f, 0.32f), rgb(0.02f, 0.02f, 0.07f),
					rgb(0.13f, 0.17f, 0.32f), rgb(0.02f, 0.02f, 0.06f), rgb(0.45f, 0.35f, 0.90f), 0.8f, true);
			case COTTON_CANDY -> new Palette(rgb(0.76f, 0.66f, 1.00f), rgb(0.16f, 0.10f, 0.26f),
					rgb(1.00f, 0.76f, 0.90f), rgb(0.18f, 0.10f, 0.22f), rgb(1.00f, 0.55f, 0.85f), 1.1f, false);
			case ACCENT -> {
				Vector3f a = fromRgbInt(LmgConfig.get().accentRgb());
				yield new Palette(mix(a, rgb(1, 1, 1), 0.15f), scale(a, 0.16f),
						mix(a, rgb(1, 1, 1), 0.30f), scale(a, 0.12f), a, 1.0f, false);
			}
			case VOID -> new Palette(rgb(0.01f, 0.01f, 0.02f), rgb(0f, 0f, 0f),
					rgb(0.04f, 0.04f, 0.05f), rgb(0.01f, 0.01f, 0.015f), rgb(0.6f, 0.6f, 0.7f), 0.3f, true);
			case CUSTOM -> {
				// your two colours: the sky, and the horizon (which also tints the fog and the sunset glow)
				Vector3f sky = fromRgbInt(LmgConfig.get().skyRgb), horizon = fromRgbInt(LmgConfig.get().horizonRgb);
				yield new Palette(sky, scale(sky, 0.14f), horizon, scale(horizon, 0.12f), horizon, 1.0f, false);
			}
		};
	}

	/** How "daytime" the vanilla sky currently is (0 = night, 1 = full day). Updated as the sky is read. */
	private static float daylight = 1f;

	public static float daylight() {
		return daylight;
	}

	// ---------------------------------------------------------------------------------------------
	// Environment attributes (sky / fog / glow / cloud colors, stars)
	// ---------------------------------------------------------------------------------------------

	/** Called with every attribute value the client reads; returns the (possibly changed) value. */
	public static Object modifyAttribute(Level level, EnvironmentAttribute<?> attribute, Object value) {
		LmgConfig cfg = LmgConfig.get();
		boolean overworld = level.dimension() == Level.OVERWORLD;
		Palette p = overworld ? palette(cfg.skyTheme) : null;

		if (attribute == EnvironmentAttributes.SKY_COLOR && value instanceof Vector3fc sky) {
			daylight = clamp01(luminance(sky) / 0.55f);
			if (p == null) return value;
			return mix(p.skyNight(), p.skyDay(), smooth(daylight));
		}
		if (attribute == EnvironmentAttributes.FOG_COLOR && value instanceof Vector3fc fog) {
			if (p == null) return value;
			return mix(p.fogNight(), p.fogDay(), smooth(daylight));
		}
		if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR && value instanceof Vector4fc glow) {
			if (p == null) return value;
			return new Vector4f(p.glow().x(), p.glow().y(), p.glow().z(), clamp01(glow.w() * p.glowStrength()));
		}
		if (attribute == EnvironmentAttributes.CLOUD_COLOR && value instanceof Vector4fc cloud) {
			if (cfg.clouds != LmgConfig.CloudMode.THEMED) return value;
			Vector3fc tint = p != null
					? mix(p.fogNight(), p.fogDay(), smooth(daylight))
					: fromRgbInt(LmgConfig.get().accentRgb());
			Vector3f t = mix(tint, rgb(1, 1, 1), 0.35f);
			return new Vector4f(clamp01(cloud.x() * t.x * 1.1f), clamp01(cloud.y() * t.y * 1.1f),
					clamp01(cloud.z() * t.z * 1.1f), cloud.w());
		}
		if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS && value instanceof Float stars) {
			float min = 0f;
			if (cfg.alwaysStars) min = 0.55f;
			if (p != null && p.darkSky()) min = Math.max(min, 0.65f);
			return Math.max(stars, min);
		}
		return value;
	}

	// ---------------------------------------------------------------------------------------------
	// Fog distances
	// ---------------------------------------------------------------------------------------------

	public static void adjustFog(FogData fog, Camera camera) {
		LmgConfig.FogLevel level = LmgConfig.get().fog;
		if (level == LmgConfig.FogLevel.VANILLA) return;
		// Leave water, lava, powder snow, blindness and darkness fog alone - they matter for gameplay.
		FogType inside = camera.getFluidInCamera();
		if (inside == FogType.WATER || inside == FogType.LAVA || inside == FogType.POWDER_SNOW) return;
		if (camera.entity() instanceof LivingEntity living
				&& (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;

		switch (level) {
			case LIGHT -> {
				fog.environmentalStart *= 1.75f;
				fog.environmentalEnd *= 1.75f;
				fog.skyEnd *= 1.5f;
			}
			case NONE -> {
				fog.environmentalStart = 1.0e5f;
				fog.environmentalEnd = 1.0e5f + 1f;
				// keep a thin fade at the very edge so chunks don't pop in harshly
				fog.renderDistanceStart = Math.max(fog.renderDistanceStart, fog.renderDistanceEnd - 6f);
				fog.skyEnd = Math.max(fog.skyEnd, fog.renderDistanceEnd * 4f);
			}
			case THICK -> {
				fog.environmentalStart = 0f;
				fog.environmentalEnd = Math.min(fog.environmentalEnd, 48f);
				fog.skyEnd = Math.min(fog.skyEnd, 48f);
			}
			default -> {}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Time of day
	// ---------------------------------------------------------------------------------------------

	/** Fixed time-of-day in ticks (0..23999), or -1 for the real time. */
	public static long fixedTimeOfDay() {
		return LmgConfig.get().timeOfDay.ticks;
	}

	// ---------------------------------------------------------------------------------------------
	// small color helpers
	// ---------------------------------------------------------------------------------------------

	private static Vector3f rgb(float r, float g, float b) {
		return new Vector3f(r, g, b);
	}

	private static Vector3f fromRgbInt(int rgb) {
		return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
	}

	private static Vector3f mix(Vector3fc a, Vector3fc b, float t) {
		return new Vector3f(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
	}

	private static Vector3f scale(Vector3fc a, float s) {
		return new Vector3f(a.x() * s, a.y() * s, a.z() * s);
	}

	private static float luminance(Vector3fc c) {
		return 0.2126f * c.x() + 0.7152f * c.y() + 0.0722f * c.z();
	}

	private static float smooth(float t) {
		t = clamp01(t);
		return t * t * (3 - 2 * t);
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
