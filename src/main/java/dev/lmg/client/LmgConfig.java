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
		OCEAN("Ocean"),
		MINT("Mint"),
		LAVENDER("Lavender"),
		GOLDEN("Golden"),
		ACCENT("Match accent"),
		VOID("Void"),
		CUSTOM("Your colours");

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
			if (rgb == -1) return LmgConfig.get().accentRgb();
			if (rgb == -2) return java.awt.Color.HSBtoRGB((ticks / 120f) % 1f, 0.55f, 1f) & 0xFFFFFF;
			return rgb;
		}
	}

	public enum Trail {
		OFF("Off"), SPARKLE("Sparkle"), ACCENT_DUST("Coloured dust"), HEARTS("Hearts"),
		NOTES("Notes"), SOUL_FLAMES("Soul flames"), SPARKS("Sparks");

		public final String label;

		Trail(String label) {
			this.label = label;
		}
	}

	public enum Ambient {
		OFF("Off"), FIREFLIES("Fireflies"), PETALS("Petals"), STARDUST("Stardust"),
		SNOW("Snow"), ASH("Ash"), SPORES("Spores"), LEAVES("Autumn leaves"), MIST("Low mist");

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
		OFF("Off"), SPARKS("Sparks"), CRITS("Crits"), STARS("Stars"), HEARTS("Hearts"), ACCENT("Coloured burst");

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
		ACCENT("Accent", -1), RAINBOW("Rainbow", -2), CUSTOM("Your colour", -3);

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
	public int menuSnow = 2; // v0.25 only: off, light, snowy, blizzard (now menuWeather)
	public dev.lmg.client.render.MenuSnow.Weather menuWeather = null; // snow / leaves / petals / fireflies, or by season

	public dev.lmg.client.render.MenuSnow.Weather weather() {
		return menuWeather == null ? dev.lmg.client.render.MenuSnow.Weather.AUTO : menuWeather;
	}
	public boolean menuGreyWorld = true; // black-and-white world behind the Lmg menus
	public boolean menuAvatar = true;    // you and your pet next to the Right Shift menu
	// ---- colours you pick yourself (-1 = follow the accent / the default) ----
	public int skyRgb = 0x7FA8FF;        // "Your colours" sky theme: the daytime sky
	public int horizonRgb = 0xFFC7A0;    // ... and the horizon / fog
	public int auroraRgb = 0x3CFFA0;
	public int trailRgb = -1;
	public int hitRgb = -1;
	public int menuBgRgb = -1;           // -1 = use the "Menu background" preset
	// ---- extra visuals ----
	public Crosshair crosshair = Crosshair.VANILLA;
	public int crosshairRgb = 0xFFFFFF;
	public int crosshairSize = 1;        // index into CROSSHAIR_SIZES
	public boolean crosshairTargetColor = true;  // turns your in-reach colour when you can hit what you aim at
	public int crosshairSpin = 0;        // index into SPIN_SPEEDS
	public boolean crosshairOutline = true;
	/** Your own drawing: 15 rows of 15 pixels (bit 0 = left). */
	public int[] crosshairPixels = defaultDrawing();
	// ---- held item look ----
	public float itemX = 0f, itemY = 0f, itemZ = 0f;
	public float itemTurn = 0f, itemTilt = 0f, itemRoll = 0f; // degrees
	public float itemSize = 1f;
	// ---- crosshair, more options ----
	public int crosshairGap = 2, crosshairLength = 4, crosshairThickness = 1, crosshairDot = 1;
	// ---- damage numbers, more options ----
	public DamageStyle damageStyle = DamageStyle.SHADOW;
	public float damageSize = 1f;        // 0.6 - 2
	public float damageTime = 1.1f;      // seconds on screen
	public boolean damageCritBig = true; // big hits pop out more
	// ---- hit sounds ----
	public HitSound hitSound = HitSound.OFF;
	public float hitSoundVolume = 0.7f, hitSoundPitch = 1f;
	public boolean hitSoundPlayersOnly = false;
	// ---- PvP panel and armour panel ----
	public boolean pvpHud = false, pvpCps = true, pvpCombo = true, pvpReach = true;
	public boolean armourHud = false, armourHeld = true, armourNumbers = false;
	public float pvpX = -1, pvpY = -1, armourX = -1, armourY = -1;
	// ---- lens: in-game vignette, film grain, colour fringes ----
	public int lensVignette = 0;         // 0 off, 1 soft, 2 strong
	public int lensGrain = 0;            // 0 off, 1 light, 2 heavy
	public boolean lensChroma = false;
	// ---- hotbar and inventory ----
	public HotbarStyle hotbarStyle = HotbarStyle.VANILLA;
	public int hotbarRgb = -1;           // -1 = your accent
	public float hotbarOpacity = 0.7f;
	public int inventoryTint = -1;       // -1 = off
	public float inventoryTintStrength = 0.3f;
	// ---- name tags ----
	public NameTagBg nameTagBg = NameTagBg.VANILLA;
	public boolean nameTagHealth = false;
	// ---- aurora, winter, trail: more options ----
	public float auroraBrightness = 1f, auroraHeight = 0f;
	public int winterSpeed = 1;          // index into WINTER_SPEED_LABELS
	public int winterDepth = 1;          // index into WINTER_DEPTH_LABELS
	public int trailAmount = 1;          // index into TRAIL_AMOUNT_LABELS
	public float trailSize = 1f;
	// ---- HUD bar ----
	public boolean hudShowFriends = true;
	// ---- profiles, starting looks, low-end mode ----
	public String activeProfile = "Everyday";
	public boolean lowEnd = false;
	public String lowEndBackup = "";     // what low-end mode turned down, to put back
	// ---- friends ----
	public java.util.Map<String, String> friendNicknames = new java.util.HashMap<>();
	public boolean damageNumbers = false;
	public int damageRgb = 0xFF5A6E;
	public int hurtRgb = 0xFF0000;       // the flash when something gets hurt
	public int motionBlur = 0;           // index into MOTION_BLUR_LABELS
	public dev.lmg.client.feature.PhotoMode.Filter worldFilter = null;   // a colour filter over the world, always on
	public boolean ownNameTag = false;   // see your own name tag in third person
	// ---- UID ----
	public int myUid = 0;                // your Lmg number once you've claimed it (0 = not yet)
	public boolean uidOnNameTags = true;
	// ---- tour ----
	public boolean tourDone = false;

	public static final String[] MOTION_BLUR_LABELS = {"Off", "Light", "Medium", "Strong"};
	public static final float[] CROSSHAIR_SIZES = {0.75f, 1f, 1.4f, 1.8f};
	public static final String[] CROSSHAIR_SIZE_LABELS = {"Small", "Normal", "Big", "Huge"};

	public static final String[] WINTER_SPEED_LABELS = {"Slow", "Normal", "Fast"};
	public static final float[] WINTER_SPEEDS = {0.5f, 1f, 4f};
	public static final String[] WINTER_DEPTH_LABELS = {"Thin", "Normal", "Deep"};
	public static final float[] WINTER_DEPTHS = {0.6f, 1f, 2.2f};
	public static final String[] TRAIL_AMOUNT_LABELS = {"Fewer", "Normal", "More"};
	public static final String[] LENS_LABELS = {"Off", "Light", "Strong"};
	public static final String[] PROFILES = {"Everyday", "PvP", "Screenshots"};

	public enum DamageStyle {
		PLAIN("Plain"), SHADOW("Shadow"), OUTLINE("Outlined"), BUBBLE("Bubble");
		public final String label;

		DamageStyle(String label) {
			this.label = label;
		}
	}

	public enum HitSound {
		OFF("Off"), CLICK("Click"), DING("Ding"), BELL("Bell"), PLING("Pling"), BASS("Bass"), POP("Pop"), CHIME("Chime");
		public final String label;

		HitSound(String label) {
			this.label = label;
		}
	}

	public enum HotbarStyle {
		VANILLA("Vanilla"), LMG("Lmg (rounded)"), CLEAR("See-through"), OUTLINE("Outline");
		public final String label;

		HotbarStyle(String label) {
			this.label = label;
		}
	}

	public enum NameTagBg {
		VANILLA("Vanilla"), NONE("No box"), DARK("Dark box"), ACCENT("Your colour");
		public final String label;

		NameTagBg(String label) {
			this.label = label;
		}
	}

	public static final float[] SPIN_SPEEDS = {0f, 40f, 120f, 360f};   // degrees a second
	public static final String[] SPIN_LABELS = {"Off", "Slow", "Medium", "Fast"};
	public static final int DRAW_SIZE = 15;

	/** A plus with a gap in the middle, to start drawing from. */
	public static int[] defaultDrawing() {
		int[] rows = new int[DRAW_SIZE];
		int mid = DRAW_SIZE / 2;
		for (int i = 0; i < DRAW_SIZE; i++) {
			if (Math.abs(i - mid) >= 2 && Math.abs(i - mid) <= 5) {
				rows[mid] |= 1 << i;
				rows[i] |= 1 << mid;
			}
		}
		rows[mid] |= 1 << mid;
		return rows;
	}

	public enum Crosshair {
		VANILLA("Vanilla"), DOT("Dot"), CROSS("Cross"), CROSS_DOT("Cross + dot"), CIRCLE("Circle"), CHEVRON("Chevron"),
		LOGO("Lmg logo"), DRAWN("Your drawing");
		public final String label;

		Crosshair(String label) {
			this.label = label;
		}
	}

	public int trailColor() {
		return trailRgb < 0 ? accentRgb() : trailRgb;
	}

	public int hitColor() {
		return hitRgb < 0 ? accentRgb() : hitRgb;
	}
	public dev.lmg.client.world.Winter.Mode winter = dev.lmg.client.world.Winter.Mode.SEASONAL; // snow in the world (only on your screen)
	public boolean winterSettles = true; // the snow settles on the ground around you

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
	public boolean lmgTotem = false;   // off until you turn it on (it changes how totems look)
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
	public float zoomAmount = -1f;              // how many times closer the zoom key gets you (-1 = from zoomLevel, for old configs)
	public int zoomSpeed = 3;                   // 1 very slow .. 10 very fast (how quickly smooth zoom glides)
	public int freecamSpeed = 1;
	/** On multiplayer servers, keep the visual helpers that some servers ban (freecam, ore glow) off. */
	public boolean serverSafe = true;
	/** False until the "before you start" screen has been shown once. */
	public boolean welcomeSeen = false;

	// ---- performance ----
	public static final int[] MINIMAP_REFRESH_MS = {50, 125, 333};
	public static final String[] REFRESH_LABELS = {"Smooth", "Normal", "Saver"};
	public static final int[] SCAN_SWEEP = {8, 4, 2};          // far chunks mapped per tick
	public static final int[] SCAN_NEAR_EVERY = {5, 10, 20};   // ticks between re-maps of the chunks around you
	public static final String[] SCAN_LABELS = {"Fast", "Normal", "Slow"};
	public static final int[] OTHERS_RANGES = {16, 32, 48};
	public boolean trailFirstPerson = false;
	public boolean followParticleSetting = true;
	public int minimapRefresh = 1;
	public int mapScan = 1;
	public int othersRange = 1;
	public boolean fpsBoost = false;
	/** Minecraft settings from before FPS boost, so turning it off puts them back. */
	public java.util.Map<String, String> fpsBoostSaved = new java.util.HashMap<>();

	// ---- custom colours (from the colour picker; -1 = use the preset) ----
	public int accentRgb = -1;
	public int overlayRgb = -1;
	public boolean overlayRainbow = false;

	/** Your accent colour: the one you picked, or the preset. */
	public int accentRgb() {
		return accentRgb >= 0 ? accentRgb & 0xFFFFFF : accent.rgb;
	}

	/** Block overlay colour right now. */
	public int overlayRgb(float ticks) {
		if (overlayRainbow) return java.awt.Color.HSBtoRGB((ticks / 120f) % 1f, 0.6f, 1f) & 0xFFFFFF;
		if (overlayRgb >= 0) return overlayRgb & 0xFFFFFF;
		return overlayColor == CosmeticColor.ACCENT ? accentRgb() : overlayColor.rgb(ticks);
	}

	// ---- target highlight ----
	public static final int[] TARGET_RANGES = {4, 8, 12, 16, 24, 32};
	public int targetRange = 3;          // index into TARGET_RANGES (16 blocks)
	public int targetColor = -1;         // -1 = accent
	public boolean reachColorOn = true;
	public int reachColor = 0xFF4D5E;

	public int targetRgb() {
		return targetColor >= 0 ? targetColor & 0xFFFFFF : accentRgb();
	}

	// ---- cinematic mode ----
	public static final float[] BAR_SIZES = {0.07f, 0.11f, 0.15f};
	public static final String[] BAR_SIZE_LABELS = {"Thin", "Normal", "Wide"};
	public boolean cinematicBars = true;
	public int barSize = 1;
	public boolean cinematicVignette = true;
	public boolean cinematicSmoothCam = true;
	public boolean smoothFreecam = true;

	// ---- sharing pets and cosmetics with other Lmg players ----
	/** Off until you turn it on. */
	public boolean shareAvatar = false;
	// ---- friends ----
	public boolean friendsEnabled = false;      // nothing is shared until you switch friends on
	public boolean friendsShowServer = true;    // friends can see (and join) the server you're on
	public boolean friendsToasts = true;        // "x is online" pop-ups
	public boolean friendsAppearOffline = false;
	public boolean matchPartner = false;        // wear the same items as your partner (in your own colours)
	public boolean partnerHearts = true;        // hearts between you and your partner when you're close
	public boolean emoteCamera = true;          // switch to the front camera while you emote (from first person)
	/** True while the database has your shared setup (so switching off removes it). */
	public boolean avatarShared = false;
	public boolean petsClimb = false;           // pets on "Follow me" climb up on your head and shoulders now and then
	public boolean showOthers = true;
	public boolean showOtherPetNames = true;

	// ---- pets (up to 3) ----
	public java.util.List<dev.lmg.client.pet.PetConfig> pets = new java.util.ArrayList<>();
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
	public boolean deathTrail = true;     // the way back to where you died: a trail, a beam and a compass
	public float minimapX = -1, minimapY = -1;

	public static LmgConfig get() {
		return instance;
	}

	/** Replaces the current settings with {@code other} (keeps where you've dragged the menu to). */
	public static void apply(LmgConfig other) {
		keepPersonal(other);
		instance = other.sanitized();
		save();
	}

	/**
	 * Things that belong to you, not to a "look": your UID, the tour, what you share and with
	 * whom. Loading a config (yours, a friend's, or a profile) never changes these, so importing
	 * someone's code can't switch on sharing or give you their number.
	 */
	private static void keepPersonal(LmgConfig other) {
		LmgConfig me = instance;
		other.menuOffsetX = me.menuOffsetX;
		other.menuOffsetY = me.menuOffsetY;
		other.myUid = me.myUid;
		other.tourDone = me.tourDone;
		other.welcomeSeen = me.welcomeSeen;
		other.shareAvatar = me.shareAvatar;
		other.friendsEnabled = me.friendsEnabled;
		other.friendsShowServer = me.friendsShowServer;
		other.friendsAppearOffline = me.friendsAppearOffline;
		other.friendsToasts = me.friendsToasts;
		other.matchPartner = me.matchPartner;
		other.friendNicknames = me.friendNicknames;
		other.activeProfile = me.activeProfile;
		other.lowEnd = me.lowEnd;
		other.lowEndBackup = me.lowEndBackup;
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
		ambientAmount = clampIndex(ambientAmount, AMOUNT_LABELS.length, d.ambientAmount);
		animationSpeed = clampIndex(animationSpeed, ANIMATION_SPEEDS.length, d.animationSpeed);
		sparkles = clampIndex(sparkles, SPARKLE_AMOUNTS.length, d.sparkles);
		if (winter == null) winter = dev.lmg.client.world.Winter.Mode.SEASONAL;
		if (crosshair == null) crosshair = Crosshair.VANILLA;
		crosshairSize = clampIndex(crosshairSize, CROSSHAIR_SIZES.length, 1);
		crosshairSpin = clampIndex(crosshairSpin, SPIN_SPEEDS.length, 0);
		crosshairGap = Math.max(0, Math.min(8, crosshairGap));
		crosshairLength = Math.max(1, Math.min(10, crosshairLength));
		crosshairThickness = Math.max(1, Math.min(3, crosshairThickness));
		crosshairDot = Math.max(0, Math.min(3, crosshairDot));
		if (damageStyle == null) damageStyle = DamageStyle.SHADOW;
		damageSize = clampF(damageSize, 0.6f, 2f);
		damageTime = clampF(damageTime, 0.5f, 3f);
		if (hitSound == null) hitSound = HitSound.OFF;
		hitSoundVolume = clampF(hitSoundVolume, 0.1f, 1f);
		hitSoundPitch = clampF(hitSoundPitch, 0.5f, 2f);
		lensVignette = clampIndex(lensVignette, LENS_LABELS.length, 0);
		lensGrain = clampIndex(lensGrain, LENS_LABELS.length, 0);
		if (hotbarStyle == null) hotbarStyle = HotbarStyle.VANILLA;
		hotbarOpacity = clampF(hotbarOpacity, 0.1f, 1f);
		inventoryTintStrength = clampF(inventoryTintStrength, 0.05f, 0.8f);
		if (nameTagBg == null) nameTagBg = NameTagBg.VANILLA;
		auroraBrightness = clampF(auroraBrightness, 0.3f, 2f);
		auroraHeight = clampF(auroraHeight, -20f, 40f);
		winterSpeed = clampIndex(winterSpeed, WINTER_SPEEDS.length, 1);
		winterDepth = clampIndex(winterDepth, WINTER_DEPTHS.length, 1);
		trailAmount = clampIndex(trailAmount, TRAIL_AMOUNT_LABELS.length, 1);
		trailSize = clampF(trailSize, 0.5f, 2f);
		if (activeProfile == null || activeProfile.isBlank()) activeProfile = PROFILES[0];
		if (lowEndBackup == null) lowEndBackup = "";
		if (friendNicknames == null) friendNicknames = new java.util.HashMap<>();
		friendNicknames.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue().isBlank());
		if (crosshairPixels == null || crosshairPixels.length != DRAW_SIZE) crosshairPixels = defaultDrawing();
		// held item: older versions had size steps and a "lower" switch
		itemScale = clampIndex(itemScale, ITEM_SCALES.length, 0);
		if (itemScale != 0) {
			itemSize = ITEM_SCALES[itemScale];
			itemScale = 0;
		}
		if (lowerItems) {
			itemY = -0.08f;
			lowerItems = false;
		}
		itemX = clampF(itemX, -0.6f, 0.6f);
		itemY = clampF(itemY, -0.6f, 0.6f);
		itemZ = clampF(itemZ, -0.6f, 0.6f);
		itemTurn = clampF(itemTurn, -180f, 180f);
		itemTilt = clampF(itemTilt, -180f, 180f);
		itemRoll = clampF(itemRoll, -180f, 180f);
		itemSize = clampF(itemSize, 0.3f, 2f);
		motionBlur = clampIndex(motionBlur, MOTION_BLUR_LABELS.length, 0);
		if (menuWeather == null) {
			// v0.25 had a snow amount: keep "off" and "blizzard", everything else follows the seasons
			menuWeather = menuSnow == 0 ? dev.lmg.client.render.MenuSnow.Weather.OFF
					: menuSnow == 3 ? dev.lmg.client.render.MenuSnow.Weather.BLIZZARD : dev.lmg.client.render.MenuSnow.Weather.AUTO;
		}
		hudScale = clampIndex(hudScale, HUD_SCALES.length, d.hudScale);
		hudOpacity = clampIndex(hudOpacity, HUD_OPACITIES.length, d.hudOpacity);
		if (pets == null) pets = new java.util.ArrayList<>();
		dev.lmg.client.pet.PetConfig.sanitize(pets);
		targetRange = clampIndex(targetRange, TARGET_RANGES.length, d.targetRange);
		minimapRefresh = clampIndex(minimapRefresh, MINIMAP_REFRESH_MS.length, d.minimapRefresh);
		mapScan = clampIndex(mapScan, SCAN_SWEEP.length, d.mapScan);
		othersRange = clampIndex(othersRange, OTHERS_RANGES.length, d.othersRange);
		if (fpsBoostSaved == null) fpsBoostSaved = new java.util.HashMap<>();
		barSize = clampIndex(barSize, BAR_SIZES.length, d.barSize);
		zoomLevel = clampIndex(zoomLevel, ZOOM_LEVELS.length, d.zoomLevel);
		if (!(zoomAmount > 0)) zoomAmount = ZOOM_LEVELS[zoomLevel];
		zoomAmount = Math.max(1.5f, Math.min(30f, zoomAmount));
		zoomSpeed = Math.max(1, Math.min(10, zoomSpeed));
		freecamSpeed = clampIndex(freecamSpeed, FREECAM_SPEEDS.length, d.freecamSpeed);
		minimapSize = clampIndex(minimapSize, MINIMAP_SIZES.length, d.minimapSize);
		minimapZoom = clampIndex(minimapZoom, MINIMAP_ZOOMS.length, d.minimapZoom);
		return this;
	}

	private static float clampF(float v, float min, float max) {
		return Float.isNaN(v) ? 0f : Math.max(min, Math.min(max, v));
	}

	private static int clampIndex(int value, int length, int fallback) {
		return value >= 0 && value < length ? value : fallback;
	}
}
