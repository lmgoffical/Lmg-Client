package dev.lmg.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings saved to .minecraft/config/lmgclient.json. Add a field here to add a setting. */
public final class LmgConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(LmgClient.MOD_ID + ".json");
	private static LmgConfig instance = new LmgConfig();

	// ---- choices ----

	public enum Accent {
		WHITE("White", 0xFFFFFF),
		ICE("Ice", 0x9FD8FF),
		BLUE("Blue", 0x6FA0FF),
		PURPLE("Purple", 0xB79CFF),
		PINK("Pink", 0xFF9CD6),
		RED("Red", 0xFF7A7A),
		GOLD("Gold", 0xFFD27A),
		GREEN("Green", 0x8CFFB0);

		public final String label;
		public final int rgb;

		Accent(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}
	}

	public enum Backdrop {
		DARK("Dark", 0x17171D, 0x09090C),
		MIDNIGHT("Midnight", 0x141A2E, 0x07080F),
		PURPLE_HAZE("Purple haze", 0x1E1428, 0x09060E),
		BLACK("Pure black", 0x0A0A0A, 0x000000);

		public final String label;
		public final int top;
		public final int bottom;

		Backdrop(String label, int top, int bottom) {
			this.label = label;
			this.top = top;
			this.bottom = bottom;
		}
	}

	public enum HudPosition {
		TOP_LEFT("Top left"),
		TOP_CENTER("Top center"),
		TOP_RIGHT("Top right");

		public final String label;

		HudPosition(String label) {
			this.label = label;
		}
	}

	public enum SkyTheme {
		VANILLA("Vanilla"),
		SUNSET("Sunset"),
		INFERNO("Inferno"),
		MIDNIGHT("Midnight"),
		COTTON_CANDY("Cotton candy"),
		ACCENT("Match accent"),
		VOID("Void");

		public final String label;

		SkyTheme(String label) {
			this.label = label;
		}
	}

	public enum TimeOfDay {
		VANILLA("Vanilla", -1),
		SUNRISE("Sunrise", 23300),
		DAY("Day", 6000),
		SUNSET("Sunset", 12600),
		NIGHT("Night", 18000);

		public final String label;
		/** Ticks into the 24000-tick day, or -1 to leave the real time alone. */
		public final long ticks;

		TimeOfDay(String label, long ticks) {
			this.label = label;
			this.ticks = ticks;
		}
	}

	public enum FogLevel {
		VANILLA("Vanilla"),
		LIGHT("Light"),
		NONE("None"),
		THICK("Thick");

		public final String label;

		FogLevel(String label) {
			this.label = label;
		}
	}

	public enum CloudMode {
		VANILLA("Vanilla"),
		THEMED("Themed"),
		HIDDEN("Hidden");

		public final String label;

		CloudMode(String label) {
			this.label = label;
		}
	}

	public static final float[] ANIMATION_SPEEDS = {0.5f, 1f, 1.5f, 2f};
	public static final String[] ANIMATION_SPEED_LABELS = {"Slow", "Normal", "Fast", "Very fast"};
	public static final int[] SPARKLE_AMOUNTS = {0, 12, 26, 45};
	public static final String[] SPARKLE_AMOUNT_LABELS = {"Off", "Low", "Normal", "High"};
	public static final float[] HUD_SCALES = {0.75f, 1f, 1.25f, 1.5f};
	public static final float[] HUD_OPACITIES = {0.25f, 0.5f, 0.75f, 0.9f};

	// ---- theme ----
	public Accent accent = Accent.WHITE;
	public Backdrop backdrop = Backdrop.DARK;
	public int animationSpeed = 1; // index into ANIMATION_SPEEDS

	// ---- menus ----
	public boolean showSplash = true;
	public boolean themedMenus = true;
	public boolean curtains = true;
	public int sparkles = 2; // index into SPARKLE_AMOUNTS
	public boolean skinSpin = true;

	// ---- world visuals (client-side only) ----
	public SkyTheme skyTheme = SkyTheme.VANILLA;
	public TimeOfDay timeOfDay = TimeOfDay.VANILLA;
	public FogLevel fog = FogLevel.VANILLA;
	public CloudMode clouds = CloudMode.VANILLA;
	public boolean alwaysStars = false;
	public boolean showWeather = true;

	// ---- HUD bar ----
	public boolean hudEnabled = true;
	public HudPosition hudPosition = HudPosition.TOP_LEFT;
	public int hudScale = 1; // index into HUD_SCALES
	public int hudOpacity = 2; // index into HUD_OPACITIES
	public boolean hudShowServer = true;
	public boolean hudShowFps = true;
	public boolean hudShowPing = true;
	public boolean hudShowClock = true;
	public boolean hudShowCoords = false;

	public static LmgConfig get() {
		return instance;
	}

	public static void reset() {
		instance = new LmgConfig();
		save();
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				LmgConfig loaded = GSON.fromJson(reader, LmgConfig.class);
				if (loaded != null) instance = loaded.sanitized();
			} catch (Exception e) {
				LmgClient.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LmgClient.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	/** Fixes values that are missing or out of range (e.g. from an older or hand-edited file). */
	private LmgConfig sanitized() {
		LmgConfig d = new LmgConfig();
		if (accent == null) accent = d.accent;
		if (backdrop == null) backdrop = d.backdrop;
		if (hudPosition == null) hudPosition = d.hudPosition;
		if (skyTheme == null) skyTheme = d.skyTheme;
		if (timeOfDay == null) timeOfDay = d.timeOfDay;
		if (fog == null) fog = d.fog;
		if (clouds == null) clouds = d.clouds;
		animationSpeed = clampIndex(animationSpeed, ANIMATION_SPEEDS.length, d.animationSpeed);
		sparkles = clampIndex(sparkles, SPARKLE_AMOUNTS.length, d.sparkles);
		hudScale = clampIndex(hudScale, HUD_SCALES.length, d.hudScale);
		hudOpacity = clampIndex(hudOpacity, HUD_OPACITIES.length, d.hudOpacity);
		return this;
	}

	private static int clampIndex(int value, int length, int fallback) {
		return value >= 0 && value < length ? value : fallback;
	}
}
