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

	/** How the Lmg menu itself looks. */
	public enum MenuStyle {
		LMG("Lmg"), SKEET("Skeet"), NEVERLOSE("Neverlose");

		public final String label;

		MenuStyle(String label) {
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

	/** Color choice shared by the avatar cosmetics (wings, halo, ears, China hat). */
	public enum CosmeticColor {
		OFF("Off", 0),
		WHITE("White", 0xF6F6FF),
		BLACK("Black", 0x24222B),
		GOLD("Gold", 0xFFD36A),
		PINK("Pink", 0xFFB3CF),
		ACCENT("Accent", -1),
		RAINBOW("Rainbow", -2);

		public final String label;
		private final int rgb;

		CosmeticColor(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}

		/** RGB for this choice at the given time in ticks (Rainbow cycles, Accent follows the theme). */
		public int rgb(float ticks) {
			if (rgb == -1) return LmgConfig.get().accent.rgb;
			if (rgb == -2) return java.awt.Color.HSBtoRGB((ticks / 120f) % 1f, 0.55f, 1f) & 0xFFFFFF;
			return rgb;
		}
	}

	public enum Trail {
		OFF("Off"), SPARKLE("Sparkle"), ACCENT_DUST("Accent dust"), HEARTS("Hearts"),
		NOTES("Notes"), SOUL_FLAMES("Soul flames"), SPARKS("Sparks");

		public final String label;

		Trail(String label) {
			this.label = label;
		}
	}

	public enum Ambient {
		OFF("Off"), FIREFLIES("Fireflies"), PETALS("Petals"), STARDUST("Stardust"),
		SNOW("Snow"), ASH("Ash"), SPORES("Spores");

		public final String label;

		Ambient(String label) {
			this.label = label;
		}
	}

	public enum BlockOverlay {
		VANILLA("Vanilla"), OUTLINE("Outline"), FILLED("Outline + fill"), GLOW("Pulsing glow");

		public final String label;

		BlockOverlay(String label) {
			this.label = label;
		}
	}

	public enum HitEffect {
		OFF("Off"), SPARKS("Sparks"), CRITS("Crits"), STARS("Stars"), HEARTS("Hearts"), ACCENT("Accent burst");

		public final String label;

		HitEffect(String label) {
			this.label = label;
		}
	}

	public enum KillEffect {
		OFF("Off"), SOUL_BURST("Soul burst"), STAR_BURST("Star burst"), FIREWORK("Firework"),
		TOTEM("Totem"), HEARTS("Hearts");

		public final String label;

		KillEffect(String label) {
			this.label = label;
		}
	}

	public enum Aurora {
		OFF("Off", 0), GREEN("Green", 0x3CFFA0), PURPLE("Purple", 0xB45CFF), BLUE("Blue", 0x4FB8FF),
		ACCENT("Accent", -1), RAINBOW("Rainbow", -2);

		public final String label;
		public final int rgb;

		Aurora(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}
	}

	public static final float[] ITEM_SCALES = {1f, 0.85f, 0.7f, 0.55f};

	public static final String[] AMOUNT_LABELS = {"Low", "Normal", "High"};

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
	public MenuStyle menuStyle = MenuStyle.LMG;
	public boolean menuUseAccent = false; // recolor Skeet/Neverlose with your accent color

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
	public Aurora aurora = Aurora.OFF;

	// ---- cosmetics (only you see them) ----
	/** Equipped item per category, e.g. "wings" -> "wings_angel". Missing = nothing equipped. */
	public java.util.Map<String, String> equipped = new java.util.HashMap<>(java.util.Map.of("capes", "cape_lmg"));
	/** Colour per item: "#RRGGBB", "accent" or "rainbow". Missing = the item's own colour. */
	public java.util.Map<String, String> cosmeticColors = new java.util.HashMap<>();
	/** File name in config/lmgclient-skins/ to wear instead of your account skin (null = off). */
	public String customSkin = null;
	public boolean customSkinSlim = false;

	// ---- effects (client-side particles) ----
	public Trail trail = Trail.OFF;
	public Ambient ambient = Ambient.OFF;
	public int ambientAmount = 1; // index into AMOUNT_LABELS

	public HitEffect hitEffect = HitEffect.OFF;
	public KillEffect killEffect = KillEffect.OFF;

	// ---- PvP visuals ----
	public BlockOverlay blockOverlay = BlockOverlay.VANILLA;
	public CosmeticColor overlayColor = CosmeticColor.ACCENT;
	public boolean targetReticle = false;
	public boolean targetOutline = true;
	public boolean targetHud = true;
	public boolean potionHud = true;
	public boolean oreGlow = false;
	public int itemScale = 0; // index into ITEM_SCALES
	public boolean lowerItems = false;
	public boolean lmgTotem = true;
	public boolean fullbright = false;
	public boolean lowFire = false;
	public boolean noHurtCam = false;

	// ---- layout (dragged in the HUD editor / by the menu title bar) ----
	// HUD element positions as a fraction (0..1) of the free screen space; -1 = default spot.
	public float hudBarX = -1, hudBarY = -1;
	public float targetX = -1, targetY = -1;
	public float potionsX = -1, potionsY = -1;
	public int menuOffsetX = 0, menuOffsetY = 0;

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

	// ---- keys & tools ----
	public static final float[] ZOOM_LEVELS = {2.5f, 4f, 6f, 10f};
	public static final String[] ZOOM_LABELS = {"A bit", "Normal", "Far", "Spyglass"};
	public static final double[] FREECAM_SPEEDS = {0.25, 0.5, 1.0, 2.0};
	public static final String[] FREECAM_SPEED_LABELS = {"Slow", "Normal", "Fast", "Very fast"};
	public int zoomLevel = 1;
	public boolean smoothZoom = true;
	public int freecamSpeed = 1;
	/** On multiplayer servers, keep the visual helpers that some servers ban (freecam, ore glow) off. */
	public boolean serverSafe = true;
	/** False until the "before you start" screen has been shown once. */
	public boolean welcomeSeen = false;
	public boolean hideCosmetics = false;

	// ---- maps ----
	public static final int[] MINIMAP_SIZES = {64, 84, 104, 128};
	public static final String[] MINIMAP_SIZE_LABELS = {"Small", "Normal", "Big", "Huge"};
	public static final float[] MINIMAP_ZOOMS = {0.5f, 1f, 2f, 4f};
	public static final String[] MINIMAP_ZOOM_LABELS = {"Far out", "Normal", "Close", "Very close"};
	public boolean minimap = true;
	public int minimapSize = 1;
	public int minimapZoom = 1;
	public boolean minimapRotate = false;
	public boolean minimapRound = true;
	public boolean minimapCoords = true;
	public boolean minimapWaypoints = true;
	public boolean deathWaypoint = true;
	public float minimapX = -1, minimapY = -1;

	public static LmgConfig get() {
		return instance;
	}

	/** Replaces the current settings with {@code other} (keeps where you've dragged the menu to). */
	public static void apply(LmgConfig other) {
		other.menuOffsetX = instance.menuOffsetX;
		other.menuOffsetY = instance.menuOffsetY;
		instance = other.sanitized();
		save();
	}

	/** A copy of the current settings as JSON (used for saved configs and share codes). */
	public static com.google.gson.JsonElement toJsonTree() {
		return GSON.toJsonTree(instance);
	}

	/** Reads settings from JSON; unknown or missing values fall back to defaults. */
	public static LmgConfig fromJsonTree(com.google.gson.JsonElement json) {
		LmgConfig parsed = GSON.fromJson(json, LmgConfig.class);
		if (parsed == null) throw new IllegalArgumentException("empty config");
		return parsed.sanitized();
	}

	public static void reset() {
		instance = new LmgConfig();
		save();
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				com.google.gson.JsonElement raw = com.google.gson.JsonParser.parseReader(reader);
				LmgConfig loaded = GSON.fromJson(raw, LmgConfig.class);
				if (loaded != null) {
					instance = loaded.sanitized();
					if (raw.isJsonObject()) dev.lmg.client.cosmetic.CosmeticRegistry.migrate(instance, raw.getAsJsonObject());
				}
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
		if (menuStyle == null) menuStyle = d.menuStyle;
		if (timeOfDay == null) timeOfDay = d.timeOfDay;
		if (fog == null) fog = d.fog;
		if (clouds == null) clouds = d.clouds;
		if (equipped == null) equipped = d.equipped;
		if (cosmeticColors == null) cosmeticColors = d.cosmeticColors;
		if (trail == null) trail = d.trail;
		if (hitEffect == null) hitEffect = d.hitEffect;
		if (killEffect == null) killEffect = d.killEffect;
		if (blockOverlay == null) blockOverlay = d.blockOverlay;
		if (overlayColor == null || overlayColor == CosmeticColor.OFF) overlayColor = d.overlayColor;
		if (aurora == null) aurora = d.aurora;
		if (ambient == null) ambient = d.ambient;
		itemScale = clampIndex(itemScale, ITEM_SCALES.length, d.itemScale);
		ambientAmount = clampIndex(ambientAmount, AMOUNT_LABELS.length, d.ambientAmount);
		animationSpeed = clampIndex(animationSpeed, ANIMATION_SPEEDS.length, d.animationSpeed);
		sparkles = clampIndex(sparkles, SPARKLE_AMOUNTS.length, d.sparkles);
		hudScale = clampIndex(hudScale, HUD_SCALES.length, d.hudScale);
		hudOpacity = clampIndex(hudOpacity, HUD_OPACITIES.length, d.hudOpacity);
		zoomLevel = clampIndex(zoomLevel, ZOOM_LEVELS.length, d.zoomLevel);
		freecamSpeed = clampIndex(freecamSpeed, FREECAM_SPEEDS.length, d.freecamSpeed);
		minimapSize = clampIndex(minimapSize, MINIMAP_SIZES.length, d.minimapSize);
		minimapZoom = clampIndex(minimapZoom, MINIMAP_ZOOMS.length, d.minimapZoom);
		return this;
	}

	private static int clampIndex(int value, int length, int fallback) {
		return value >= 0 && value < length ? value : fallback;
	}
}
