package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.config.CloudCodes;
import dev.lmg.client.config.ConfigProfiles;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuSnow;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import dev.lmg.client.world.OreOutlines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Lmg menu (Right Shift, or main menu -> Client).
 * A compact panel: header (back, title, HUD editor), category sidebar, settings on the right.
 * Drag the header to move the panel; its position is remembered.
 */
public class LmgSettingsScreen extends Screen {
	private static final String[] TABS = {"Theme", "Configs", "World", "Cinematic", "Avatar", "Target", "PvP", "Items", "HUD", "Maps", "Keys", "Tools", "FPS"};
	/** One plain line under each tab's name, so you know what you're looking at. */
	private static final String[] SUBTITLES = {"how this menu looks", "save setups, share them with a code", "sky, fog, clouds and weather", "shaders and film mode, for videos",
			"cosmetics, trails and particles", "whatever you're aiming at", "seeing clearly in a fight", "held items and the totem",
			"the bar at the top and what's on it", "minimap, world map and waypoints", "click a key, then press the new one", "zoom, freecam and a few extras", "make the game run smoother"};
	private static final String[] TIPS = {"drag the top bar to move this menu", "double-click the top bar to re-center it",
			"right-click a setting to go back one", "M opens the world map", "hold C to zoom, scroll to zoom more",
			"every key can be changed in Keys", "Right Shift closes this"};
	private static final int PANEL_W = 300;
	private static final int PANEL_W_SKEET = 380; // skeet uses two columns of group boxes
	private static final int SIDEBAR_W = 74;
	private static final int HEADER_H = 24;
	private static final int ROW_H = 14;
	private static final int ROW_GAP = 16;
	private static final int MAX_ROWS = 11;
	private static final int FOOTER_H = 18;

	private static int lastTab = 2; // World
	private static final int TAB_GAP = 13;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int panelX, panelY;
	private boolean dragging;
	private double dragStartX, dragStartY;
	private int dragOrigOffX, dragOrigOffY;
	private boolean confirmReset;
	private SettingRow dropdown; // the dropdown whose list is open, if any
	private net.minecraft.client.KeyMapping waitingKey; // Keys tab: the binding waiting for a new key
	private final List<SettingRow> rows = new ArrayList<>();
	private static final int ITEM_H = 13;

	/** @param parent screen to go back to, or {@code null} to return to the game */
	public LmgSettingsScreen(Screen parent) {
		super(Component.literal(LmgClient.CLIENT_NAME));
		this.parent = parent;
	}

	private static int panelW() {
		return MenuSkin.style() == LmgConfig.MenuStyle.SKEET ? PANEL_W_SKEET : PANEL_W;
	}

	private static int panelH() {
		return HEADER_H + 6 + MenuSkin.contentTopPad() + MAX_ROWS * ROW_GAP + FOOTER_H;
	}

	private void computePanel() {
		LmgConfig c = LmgConfig.get();
		panelX = width / 2 - panelW() / 2 + c.menuOffsetX;
		panelY = height / 2 - panelH() / 2 + c.menuOffsetY;
		panelX = Math.max(4, Math.min(panelX, width - panelW() - 4));
		panelY = Math.max(4, Math.min(panelY, height - panelH() - 4));
	}

	@Override
	protected void init() {
		computePanel();
		dropdown = null;
		LmgConfig c = LmgConfig.get();
		long t = Util.getMillis();

		// Header buttons (Neverlose keeps its logo top-left, so Back moves to the right there)
		boolean nl = MenuSkin.style() == LmgConfig.MenuStyle.NEVERLOSE;
		int backX = nl ? panelX + panelW() - 64 - 48 : panelX + 6;
		addRenderableWidget(new SkinButton(backX, panelY + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(panelX + panelW() - 62, panelY + 6, 56, 13, Component.literal("Edit HUD"), openedAt,
				() -> minecraft.gui.setScreen(new HudEditorScreen(this))));

		// Sidebar
		int sy = panelY + HEADER_H + 6;
		for (int i = 0; i < TABS.length; i++) {
			final int index = i;
			addRenderableWidget(new SideTab(panelX + 6, sy + i * TAB_GAP, SIDEBAR_W - 10, 12, TABS[i],
					() -> lastTab == index, () -> {
						if (lastTab != index) {
							lastTab = index;
							confirmReset = false;
							rebuildWidgets();
						}
					}));
		}

		// Settings for the selected category
		rows.clear();
		rows.addAll(rowsFor(lastTab, c));
		if (MenuSkin.style() == LmgConfig.MenuStyle.SKEET) {
			layoutSkeet(rows, t);
		} else {
			MenuSkin.setGroups(List.of());
			layoutSingle(rows, sy, t);
		}

		// Footer: reset (asks twice)
		int fy = panelY + panelH() - FOOTER_H + 2;
		SkinButton[] reset = new SkinButton[1];
		reset[0] = new SkinButton(panelX + 6, fy, SIDEBAR_W - 12, 13,
				Component.literal(confirmReset ? "Sure?" : "Reset all"), openedAt, () -> {
			if (!confirmReset) {
				confirmReset = true;
				reset[0].setMessage(Component.literal("Sure?"));
			} else {
				confirmReset = false;
				LmgConfig.reset();
				rebuildWidgets();
			}
		});
		addRenderableWidget(reset[0]);
	}

	private void layoutSingle(List<SettingRow> rows, int sy, long t) {
		int inset = MenuSkin.contentInset();
		int rx = panelX + SIDEBAR_W + 2 + inset;
		int rw = panelW() - SIDEBAR_W - 8 - inset * 2;
		int ry = sy + MenuSkin.contentTopPad();
		for (int i = 0; i < rows.size(); i++) {
			addRenderableWidget(rows.get(i).at(rx, ry + i * ROW_GAP, rw, ROW_H, t + i * 25L).onOpen(row -> dropdown = row));
		}

	}

	/** Skeet: two titled group boxes side by side; rows are shared out so both columns are about even. */
	private void layoutSkeet(List<SettingRow> rows, long t) {
		int areaX0 = panelX + SIDEBAR_W + 6, areaX1 = panelX + panelW() - 6;
		int gap = 8;
		int colW = (areaX1 - areaX0 - gap) / 2;
		int boxTop = panelY + HEADER_H + 10;
		int boxBottom = panelY + panelH() - FOOTER_H;
		int[] h = new int[rows.size()];
		int total = 0;
		for (int i = 0; i < rows.size(); i++) {
			h[i] = rows.get(i).isTallInSkeet() ? 27 : 15;
			total += h[i];
		}
		// pick the split that makes the two columns closest in height
		int split = rows.size(), best = Integer.MAX_VALUE, acc = 0;
		for (int k = 0; k <= rows.size(); k++) {
			int worst = Math.max(acc, total - acc);
			if (worst < best) { best = worst; split = k; }
			if (k < rows.size()) acc += h[k];
		}
		boolean twoColumns = split > 0 && split < rows.size() && rows.size() > 3;
		if (!twoColumns) split = rows.size();
		int col1X1 = twoColumns ? areaX0 + colW : areaX1;
		List<MenuSkin.Group> groups = new ArrayList<>();
		groups.add(new MenuSkin.Group(areaX0, boxTop, col1X1, boxBottom, TABS[lastTab]));
		if (twoColumns) groups.add(new MenuSkin.Group(areaX1 - colW, boxTop, areaX1, boxBottom, "More"));
		MenuSkin.setGroups(groups);

		int y = boxTop + 8;
		for (int i = 0; i < rows.size(); i++) {
			if (i == split) y = boxTop + 8;
			int colX0 = i < split ? areaX0 : areaX1 - colW;
			int colX1 = i < split ? col1X1 : areaX1;
			addRenderableWidget(rows.get(i).at(colX0 + 2, y, colX1 - colX0 - 4, h[i] - 2, t + i * 25L).onOpen(row -> dropdown = row));
			y += h[i];
		}
	}

	private List<SettingRow> rowsFor(int tab, LmgConfig c) {
		List<SettingRow> r = new ArrayList<>();
		switch (TABS[tab]) {
			case "Theme" -> {
				r.add(SettingRow.dropdown("Menu style",
						List.of("Lmg Client (original)", "Skeet", "Neverlose"),
						List.of(c.accentRgb(), MenuSkin.SK_ACCENT, MenuSkin.NL_ACCENT),
						() -> c.menuStyle.ordinal(),
						i -> { c.menuStyle = LmgConfig.MenuStyle.values()[i]; rebuildWidgets(); })
						.withHint("Three looks, same settings."));
				r.add(SettingRow.color("Accent color", c::accentRgb, null, () -> picker("Accent color", c.accentRgb(),
						v -> c.accentRgb = v, () -> c.accentRgb = -1, null, null))
						.withHint("Switches, HUD and minimap use it."));
				r.add(SettingRow.toggle("Accent on Skeet/Neverlose", () -> c.menuUseAccent, v -> c.menuUseAccent = v)
						.withHint("Off keeps their own green/blue."));
				r.add(SettingRow.choice("Menu background", () -> c.backdrop.label,
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, 1),
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, -1), () -> c.backdrop.top)
						.withHint("Behind the main menu."));
				r.add(SettingRow.choice("Snow", () -> MenuSnow.LABELS[c.menuSnow],
						() -> c.menuSnow = step(c.menuSnow, MenuSnow.LABELS.length, 1),
						() -> c.menuSnow = step(c.menuSnow, MenuSnow.LABELS.length, -1))
						.withHint("Falls in this menu and piles up on top."));
				r.add(SettingRow.choice("Animation speed", () -> LmgConfig.ANIMATION_SPEED_LABELS[c.animationSpeed],
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, 1),
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, -1))
						.withHint("How fast menus slide and fade."));
				r.add(SettingRow.toggle("Splash screen", () -> c.showSplash, v -> c.showSplash = v)
						.withHint("The logo while the game loads."));
				r.add(SettingRow.toggle("Themed vanilla menus", () -> c.themedMenus, v -> c.themedMenus = v)
						.withHint("Our background on vanilla menus."));
				r.add(SettingRow.toggle("Light curtains", () -> c.curtains, v -> c.curtains = v)
						.withHint("Soft light beams on the main menu."));
				r.add(SettingRow.choice("Sparkles", () -> LmgConfig.SPARKLE_AMOUNT_LABELS[c.sparkles],
						() -> c.sparkles = step(c.sparkles, LmgConfig.SPARKLE_AMOUNTS.length, 1),
						() -> c.sparkles = step(c.sparkles, LmgConfig.SPARKLE_AMOUNTS.length, -1))
						.withHint("Little stars on the main menu."));
				r.add(SettingRow.toggle("Spin skin preview", () -> c.skinSpin, v -> c.skinSpin = v)
						.withHint("Your skin on the menu turns slowly."));
			}
			case "Configs" -> {
				r.add(SettingRow.button("Saved configs", "Open", () -> minecraft.gui.setScreen(new ConfigsScreen(this)))
						.withHint("Save, load and delete your setups."));
				r.add(SettingRow.button("Browse configs online", "Website", () ->
						net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,
								java.net.URI.create("https://lmgoffical.github.io/Lmg-Client/#/configs")))
						.withHint("Setups others shared on the site."));
				r.add(SettingRow.button("Share my setup", "Get code", () -> {
					String longCode = ConfigProfiles.codeForCurrent("My config");
					toast("Uploading...", "Getting a short code for your setup.");
					CloudCodes.share("My config", longCode).whenComplete((code, error) -> minecraft.execute(() -> {
						if (error == null) {
							minecraft.keyboardHandler.setClipboard(code);
							toast("Copied " + code, "Send it to a friend - they type it in Configs.");
						} else {
							minecraft.keyboardHandler.setClipboard(longCode);
							toast("Copied the long code", "Server unreachable: " + CloudCodes.messageOf(error));
						}
					}));
				}).withHint("Anyone with the code can download it."));
				r.add(SettingRow.button("Import from clipboard", "Paste", () -> {
					String text = minecraft.keyboardHandler.getClipboard();
					if (CloudCodes.isShortCode(text)) {
						toast("Downloading...", text.trim().toUpperCase());
						CloudCodes.fetch(text).whenComplete((p, error) -> minecraft.execute(() -> {
							if (error == null) {
								LmgConfig.apply(p.config());
								toast("Imported \"" + p.name() + "\"", "Save it in Saved configs to keep it.");
								rebuildWidgets();
							} else {
								toast("Couldn't import", CloudCodes.messageOf(error));
							}
						}));
						return;
					}
					try {
						ConfigProfiles.Profile p = ConfigProfiles.decode(text);
						LmgConfig.apply(p.config());
						toast("Imported \"" + p.name() + "\"", "Save it in Saved configs to keep it.");
						rebuildWidgets();
					} catch (IllegalArgumentException e) {
						toast("Couldn't import", e.getMessage());
					}
				}).withHint("Copy a friend's code, then press this."));
			}
			case "World" -> {
				r.add(enumRow("Sky theme", LmgConfig.SkyTheme.values(), () -> c.skyTheme, v -> c.skyTheme = v, v -> v.label)
						.withHint("Recolours the sky, just for you."));
				r.add(enumRow("Time of day", LmgConfig.TimeOfDay.values(), () -> c.timeOfDay, v -> c.timeOfDay = v, v -> v.label)
						.withHint("Lock it to sunset, if you like."));
				r.add(enumRow("Fog", LmgConfig.FogLevel.values(), () -> c.fog, v -> c.fog = v, v -> v.label)
						.withHint("Less fog = you see further."));
				r.add(enumRow("Clouds", LmgConfig.CloudMode.values(), () -> c.clouds, v -> c.clouds = v, v -> v.label));
				r.add(enumRow("Aurora", LmgConfig.Aurora.values(), () -> c.aurora, v -> c.aurora = v, v -> v.label)
						.withHint("Northern lights at night."));
				r.add(SettingRow.toggle("Always show stars", () -> c.alwaysStars, v -> c.alwaysStars = v)
						.withHint("Stars stay out during the day too."));
				r.add(SettingRow.toggle("Rain & snow", () -> c.showWeather, v -> c.showWeather = v)
						.withHint("Off hides falling rain and snow."));
			}
			case "Cinematic" -> {
				r.add(SettingRow.toggle("Cinematic mode", dev.lmg.client.feature.Cinematic::on, v -> {
					if (minecraft.player == null) { toast("Join a world first", "Then press F7 (or this switch)."); return; }
					if (v != dev.lmg.client.feature.Cinematic.on()) dev.lmg.client.feature.Cinematic.toggle();
				}).withHint("Film bars, no HUD, smooth camera. Key: F7"));
				r.add(SettingRow.toggle("Film bars", () -> c.cinematicBars, v -> c.cinematicBars = v));
				r.add(SettingRow.choice("Bar size", () -> LmgConfig.BAR_SIZE_LABELS[c.barSize],
						() -> c.barSize = step(c.barSize, LmgConfig.BAR_SIZES.length, 1),
						() -> c.barSize = step(c.barSize, LmgConfig.BAR_SIZES.length, -1)));
				r.add(SettingRow.toggle("Vignette", () -> c.cinematicVignette, v -> c.cinematicVignette = v)
						.withHint("Darker corners, like a camera lens."));
				r.add(SettingRow.toggle("Smooth camera", () -> c.cinematicSmoothCam, v -> c.cinematicSmoothCam = v)
						.withHint("Slow, floaty mouse look while filming."));
				r.add(SettingRow.toggle("Smooth freecam", () -> c.smoothFreecam, v -> c.smoothFreecam = v)
						.withHint("Freecam glides instead of stopping dead."));
				r.add(SettingRow.choice("Shaders", this::shaderLabel, () -> pickShader(1), () -> pickShader(-1))
						.withHint("Golden haze = the video's look. Big FPS cost."));
				r.add(SettingRow.button("Shader settings", "Open", () -> {
					Screen iris = dev.lmg.client.feature.Shaders.settingsScreen(this);
					if (iris != null) minecraft.gui.setScreen(iris);
					else toast("Shaders need Iris", "Run the Lmg installer again to add it.");
				}).withHint("Iris's own menu: fine-tune the pack."));
				r.add(SettingRow.toggle("Golden hour", () -> c.timeOfDay == LmgConfig.TimeOfDay.SUNSET,
						v -> c.timeOfDay = v ? LmgConfig.TimeOfDay.SUNSET : LmgConfig.TimeOfDay.VANILLA)
						.withHint("Keeps the sun low and warm (only for you)."));
			}
			case "Avatar" -> {
				r.add(SettingRow.button("Cosmetics & skins", "Open", () -> minecraft.gui.setScreen(new CosmeticsScreen(this)))
						.withHint("Capes, wings, hats and your own skins."));
				r.add(SettingRow.button("Pets", "Open", () -> minecraft.gui.setScreen(new PetsScreen(this)))
						.withHint("Animals or a mini player that follow you."));
				r.add(enumRow("Trail", LmgConfig.Trail.values(), () -> c.trail, v -> c.trail = v, v -> v.label)
						.withHint("Particles behind you as you walk."));
				r.add(enumRow("Ambient particles", LmgConfig.Ambient.values(), () -> c.ambient, v -> c.ambient = v, v -> v.label)
						.withHint("Things floating around you."));
				r.add(SettingRow.choice("Ambient amount", () -> LmgConfig.AMOUNT_LABELS[c.ambientAmount],
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, 1),
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, -1)));
			}
			case "Target" -> {
				r.add(SettingRow.toggle("Glowing outline", () -> c.targetOutline, v -> c.targetOutline = v)
						.withHint("Outlines whoever you aim at."));
				r.add(SettingRow.choice("Highlight distance", () -> LmgConfig.TARGET_RANGES[c.targetRange] + " blocks",
						() -> c.targetRange = step(c.targetRange, LmgConfig.TARGET_RANGES.length, 1),
						() -> c.targetRange = step(c.targetRange, LmgConfig.TARGET_RANGES.length, -1))
						.withHint("Nothing further away than this lights up."));
				r.add(SettingRow.color("Highlight color", c::targetRgb, null, () -> picker("Highlight color", c.targetRgb(),
						v -> c.targetColor = v, () -> c.targetColor = -1, null, null))
						.withHint("Default follows your accent colour."));
				r.add(SettingRow.toggle("New color in reach", () -> c.reachColorOn, v -> c.reachColorOn = v)
						.withHint("Changes colour when you can hit them."));
				r.add(SettingRow.color("In-reach color", () -> c.reachColor, null, () -> picker("In-reach color", c.reachColor,
						v -> c.reachColor = v, () -> c.reachColor = 0xFF4D5E, null, null))
						.withHint("Shown once they're close enough to hit."));
				r.add(SettingRow.toggle("Target info panel", () -> c.targetHud, v -> c.targetHud = v)
						.withHint("Their health, armour and effects."));
				r.add(SettingRow.toggle("Target reticle", () -> c.targetReticle, v -> c.targetReticle = v)
						.withHint("A ring under whoever you aim at."));
				r.add(enumRow("Hit effect", LmgConfig.HitEffect.values(), () -> c.hitEffect, v -> c.hitEffect = v, v -> v.label)
						.withHint("What you see when you land a hit."));
				r.add(enumRow("Kill effect", LmgConfig.KillEffect.values(), () -> c.killEffect, v -> c.killEffect = v, v -> v.label));
			}
			case "PvP" -> {
				r.add(enumRow("Block overlay", LmgConfig.BlockOverlay.values(), () -> c.blockOverlay, v -> c.blockOverlay = v, v -> v.label)
						.withHint("The box around the block you look at."));
				r.add(SettingRow.color("Overlay color", () -> c.overlayRgb(Util.getMillis() / 50f), () -> c.overlayRainbow,
						() -> picker("Overlay color", c.overlayRgb(0), v -> c.overlayRgb = v,
								() -> { c.overlayRgb = -1; c.overlayColor = LmgConfig.CosmeticColor.ACCENT; },
								() -> c.overlayRainbow, v -> c.overlayRainbow = v))
						.withHint("The box around the block you look at."));
				r.add(SettingRow.toggle("Fullbright", () -> c.fullbright, v -> c.fullbright = v)
						.withHint("See in caves without torches. Key: B"));
				r.add(SettingRow.toggle("Ore edges", OreOutlines::isEnabled, OreOutlines::setEnabled)
						.withHint("A texture pack. Off on strict servers."));
				r.add(SettingRow.toggle("Ore glow in the dark", () -> c.oreGlow, v -> c.oreGlow = v)
						.withHint("Outlines ores you can already see."));
				r.add(SettingRow.toggle("Potion effects HUD", () -> c.potionHud, v -> c.potionHud = v)
						.withHint("A tidy list of your effects."));
				r.add(SettingRow.toggle("Low fire", () -> c.lowFire, v -> c.lowFire = v)
						.withHint("Smaller fire when you're burning."));
				r.add(SettingRow.toggle("No hurt camera shake", () -> c.noHurtCam, v -> c.noHurtCam = v)
						.withHint("Screen doesn't tilt when you get hit."));
			}
			case "Items" -> {
				r.add(SettingRow.choice("Held item size", () -> Math.round(LmgConfig.ITEM_SCALES[c.itemScale] * 100) + "%",
						() -> c.itemScale = step(c.itemScale, LmgConfig.ITEM_SCALES.length, 1),
						() -> c.itemScale = step(c.itemScale, LmgConfig.ITEM_SCALES.length, -1))
						.withHint("Smaller sword = more screen to see."));
				r.add(SettingRow.toggle("Lower held items", () -> c.lowerItems, v -> c.lowerItems = v)
						.withHint("Pushes your hand down a bit."));
				r.add(SettingRow.toggle("Lmg totem", () -> OreOutlines.isEnabled(OreOutlines.TOTEM),
						v -> { c.lmgTotem = v; OreOutlines.setEnabled(OreOutlines.TOTEM, v); })
						.withHint("Totems become a Star of David."));
			}
			case "HUD" -> {
				r.add(SettingRow.button("HUD layout", "Edit", () -> minecraft.gui.setScreen(new HudEditorScreen(this)))
						.withHint("Move the bar, minimap and panels."));
				r.add(SettingRow.toggle("HUD bar", () -> c.hudEnabled, v -> c.hudEnabled = v));
				r.add(SettingRow.choice("Size", () -> Math.round(LmgConfig.HUD_SCALES[c.hudScale] * 100) + "%",
						() -> c.hudScale = step(c.hudScale, LmgConfig.HUD_SCALES.length, 1),
						() -> c.hudScale = step(c.hudScale, LmgConfig.HUD_SCALES.length, -1)));
				r.add(SettingRow.choice("Background", () -> Math.round(LmgConfig.HUD_OPACITIES[c.hudOpacity] * 100) + "%",
						() -> c.hudOpacity = step(c.hudOpacity, LmgConfig.HUD_OPACITIES.length, 1),
						() -> c.hudOpacity = step(c.hudOpacity, LmgConfig.HUD_OPACITIES.length, -1)));
				r.add(SettingRow.toggle("Show server", () -> c.hudShowServer, v -> c.hudShowServer = v));
				r.add(SettingRow.toggle("Show FPS", () -> c.hudShowFps, v -> c.hudShowFps = v));
				r.add(SettingRow.toggle("Show ping", () -> c.hudShowPing, v -> c.hudShowPing = v));
				r.add(SettingRow.toggle("Show clock", () -> c.hudShowClock, v -> c.hudShowClock = v));
				r.add(SettingRow.toggle("Show coordinates", () -> c.hudShowCoords, v -> c.hudShowCoords = v));
			}
			case "Maps" -> {
				r.add(SettingRow.button("World map", "Open", () -> {
					if (minecraft.level != null) minecraft.gui.setScreen(new WorldMapScreen(this));
					else toast("Join a world first", "The map fills in as you explore.");
				}).withHint("Everywhere you've been. Key: M"));
				r.add(SettingRow.toggle("Minimap", () -> c.minimap, v -> c.minimap = v)
						.withHint("Drag it anywhere in Edit HUD."));
				r.add(SettingRow.choice("Minimap size", () -> LmgConfig.MINIMAP_SIZE_LABELS[c.minimapSize],
						() -> c.minimapSize = step(c.minimapSize, LmgConfig.MINIMAP_SIZES.length, 1),
						() -> c.minimapSize = step(c.minimapSize, LmgConfig.MINIMAP_SIZES.length, -1)));
				r.add(SettingRow.choice("Minimap zoom", () -> LmgConfig.MINIMAP_ZOOM_LABELS[c.minimapZoom],
						() -> c.minimapZoom = step(c.minimapZoom, LmgConfig.MINIMAP_ZOOMS.length, 1),
						() -> c.minimapZoom = step(c.minimapZoom, LmgConfig.MINIMAP_ZOOMS.length, -1)));
				r.add(SettingRow.toggle("Round minimap", () -> c.minimapRound, v -> c.minimapRound = v));
				r.add(SettingRow.toggle("Turn with me", () -> c.minimapRotate, v -> c.minimapRotate = v)
						.withHint("Off keeps north at the top."));
				r.add(SettingRow.toggle("Coords & biome", () -> c.minimapCoords, v -> c.minimapCoords = v)
						.withHint("Shown under the minimap."));
				r.add(SettingRow.toggle("Waypoints on minimap", () -> c.minimapWaypoints, v -> c.minimapWaypoints = v));
				r.add(SettingRow.toggle("Mark where I die", () -> c.deathWaypoint, v -> c.deathWaypoint = v)
						.withHint("Drops a \"Last death\" waypoint."));
			}
			case "Keys" -> {
				for (dev.lmg.client.feature.LmgKeys.Bind b : dev.lmg.client.feature.LmgKeys.ALL) {
					r.add(SettingRow.key(b.label(), () -> keyName(b.key()), () -> waitingKey == b.key(), () -> {
						waitingKey = waitingKey == b.key() ? null : b.key();
					}).withHint(waitingKey == b.key() ? "Press a key. Esc clears it." : b.hint()));
				}
			}
			case "FPS" -> {
				r.add(SettingRow.toggle("FPS boost", () -> c.fpsBoost, dev.lmg.client.feature.FpsBoost::set)
						.withHint("Turns down Minecraft's heaviest settings. Off undoes it."));
				r.add(SettingRow.toggle("Trail in first person", () -> c.trailFirstPerson, v -> c.trailFirstPerson = v)
						.withHint("Off: the trail only spawns when you can see it."));
				r.add(SettingRow.toggle("Follow particle setting", () -> c.followParticleSetting, v -> c.followParticleSetting = v)
						.withHint("Minecraft's Particles setting limits ours too."));
				r.add(SettingRow.choice("Minimap refresh", () -> LmgConfig.REFRESH_LABELS[c.minimapRefresh],
						() -> c.minimapRefresh = step(c.minimapRefresh, LmgConfig.MINIMAP_REFRESH_MS.length, 1),
						() -> c.minimapRefresh = step(c.minimapRefresh, LmgConfig.MINIMAP_REFRESH_MS.length, -1))
						.withHint("Only redraws when you move. Saver = fewer redraws."));
				r.add(SettingRow.choice("Map scanning", () -> LmgConfig.SCAN_LABELS[c.mapScan],
						() -> c.mapScan = step(c.mapScan, LmgConfig.SCAN_SWEEP.length, 1),
						() -> c.mapScan = step(c.mapScan, LmgConfig.SCAN_SWEEP.length, -1))
						.withHint("How fast the map fills in. Slow = lighter."));
				r.add(SettingRow.choice("Others' pets & cosmetics", () -> "within " + LmgConfig.OTHERS_RANGES[c.othersRange],
						() -> c.othersRange = step(c.othersRange, LmgConfig.OTHERS_RANGES.length, 1),
						() -> c.othersRange = step(c.othersRange, LmgConfig.OTHERS_RANGES.length, -1))
						.withHint("Further players are drawn plain."));
				r.add(SettingRow.toggle("Ore glow", () -> c.oreGlow, v -> c.oreGlow = v)
						.withHint("Light now, but off is lighter still."));
				r.add(SettingRow.toggle("Aurora", () -> c.aurora != LmgConfig.Aurora.OFF,
						v -> c.aurora = v ? LmgConfig.Aurora.values()[Math.min(1, LmgConfig.Aurora.values().length - 1)] : LmgConfig.Aurora.OFF)
						.withHint("Northern lights at night."));
				r.add(SettingRow.choice("Ambient particles", () -> LmgConfig.AMOUNT_LABELS[c.ambientAmount],
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, 1),
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, -1))
						.withHint("Fewer floating particles = smoother."));
				r.add(SettingRow.choice("Shaders", this::shaderLabel, () -> pickShader(1), () -> pickShader(-1))
						.withHint("Shaders cost the most FPS by far."));
			}
			default -> { // Tools
				r.add(SettingRow.choice("Zoom amount", () -> LmgConfig.ZOOM_LABELS[c.zoomLevel],
						() -> c.zoomLevel = step(c.zoomLevel, LmgConfig.ZOOM_LEVELS.length, 1),
						() -> c.zoomLevel = step(c.zoomLevel, LmgConfig.ZOOM_LEVELS.length, -1))
						.withHint("Scroll while zooming to go further."));
				r.add(SettingRow.toggle("Smooth zoom", () -> c.smoothZoom, v -> c.smoothZoom = v)
						.withHint("Glides in instead of snapping."));
				r.add(SettingRow.choice("Freecam speed", () -> LmgConfig.FREECAM_SPEED_LABELS[c.freecamSpeed],
						() -> c.freecamSpeed = step(c.freecamSpeed, LmgConfig.FREECAM_SPEEDS.length, 1),
						() -> c.freecamSpeed = step(c.freecamSpeed, LmgConfig.FREECAM_SPEEDS.length, -1))
						.withHint("Hold sprint to go faster."));
				r.add(SettingRow.toggle("Server-safe mode", () -> c.serverSafe, v -> c.serverSafe = v)
						.withHint("Keeps freecam and ore glow off on servers."));
				r.add(SettingRow.button("Mods", "Open", () -> minecraft.gui.setScreen(new ModsScreen(this)))
						.withHint("See your mods, switch them on or off."));
				r.add(SettingRow.button("About & safety", "Open", () -> minecraft.gui.setScreen(new WelcomeScreen(this, false)))
						.withHint("What this mod does, and what it sends."));
				r.add(SettingRow.toggle("Share pets & cosmetics", () -> c.shareAvatar, v -> c.shareAvatar = v)
						.withHint("Other Lmg players see them. Off by default."));
				r.add(SettingRow.toggle("See others' pets & cosmetics", () -> c.showOthers, v -> {
					c.showOthers = v;
					if (!v) dev.lmg.client.sync.AvatarSync.clearRemote();
				}).withHint("From Lmg players near you who share theirs."));
				r.add(SettingRow.toggle("Show their pet names", () -> c.showOtherPetNames, v -> c.showOtherPetNames = v)
						.withHint("Turn off to hide names other people chose."));
				r.add(SettingRow.toggle("Hide cosmetics", () -> c.hideCosmetics, v -> c.hideCosmetics = v)
						.withHint("Your plain skin, nothing else."));
			}
		}
		return r;
	}

	private String shaderLabel() {
		if (dev.lmg.client.feature.Shaders.busy()) return "Loading...";
		if (!dev.lmg.client.feature.Shaders.irisInstalled()) return "Needs Iris";
		var p = dev.lmg.client.feature.Shaders.current();
		if (p != null) return p.label;
		return dev.lmg.client.feature.Shaders.anyOn() ? "Your own" : "Off";
	}

	/** Off -> Golden haze -> Lighter -> Off ... */
	private void pickShader(int dir) {
		var packs = dev.lmg.client.feature.Shaders.Pack.values();
		var cur = dev.lmg.client.feature.Shaders.current();
		int idx = cur == null ? 0 : cur.ordinal() + 1;
		int next = Math.floorMod(idx + dir, packs.length + 1);
		java.util.function.Consumer<String> say = msg -> minecraft.execute(() -> toast("Shaders", msg));
		if (next == 0) dev.lmg.client.feature.Shaders.off(say);
		else dev.lmg.client.feature.Shaders.use(packs[next - 1], say);
	}

	/** Opens the colour picker; this menu comes back when it closes. */
	private void picker(String title, int rgb, java.util.function.IntConsumer set, Runnable reset,
						java.util.function.BooleanSupplier rainbowGet, Consumer<Boolean> rainbowSet) {
		minecraft.gui.setScreen(new ColorPickerScreen(this, title, rgb, set, reset, rainbowGet, rainbowSet));
	}

	/** The key a binding is set to, as short as possible ("none" if it isn't set). */
	private static String keyName(net.minecraft.client.KeyMapping key) {
		if (key.isUnbound()) return "none";
		String s = key.getTranslatedKeyMessage().getString();
		return s.replace("Left ", "L ").replace("Right ", "R ").replace("Button ", "Mouse ");
	}

	private static final net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId LMG_TOAST =
			new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId();

	private void toast(String title, String message) {
		net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(minecraft.gui.toastManager(), LMG_TOAST,
				Component.literal(title), Component.literal(message));
	}

	private static <E extends Enum<E>> SettingRow enumRow(String label, E[] values, Supplier<E> get, Consumer<E> set,
														  java.util.function.Function<E, String> name) {
		return SettingRow.choice(label, () -> name.apply(get.get()),
				() -> set.accept(cycle(values, get.get(), 1)),
				() -> set.accept(cycle(values, get.get(), -1)));
	}

	private static SettingRow colorRow(String label, Supplier<LmgConfig.CosmeticColor> get,
									   Consumer<LmgConfig.CosmeticColor> set) {
		return SettingRow.choice(label, () -> get.get().label,
				() -> set.accept(cycle(LmgConfig.CosmeticColor.values(), get.get(), 1)),
				() -> set.accept(cycle(LmgConfig.CosmeticColor.values(), get.get(), -1)),
				() -> get.get() == LmgConfig.CosmeticColor.OFF ? 0x3A3A44 : get.get().rgb(Util.getMillis() / 50f));
	}

	private static <E extends Enum<E>> E cycle(E[] values, E current, int dir) {
		return values[Math.floorMod(current.ordinal() + dir, values.length)];
	}

	private static int step(int index, int length, int dir) {
		return Math.floorMod(index + dir, length);
	}

	// ---------------------------------------------------------------------------------------------
	// Drawing
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick); // keep the game visible, blurred
			MenuSnow.greyBackdrop(g, width, height);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
		MenuSnow.update(width, height);
		MenuSnow.drawBack(g, 1f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float intro = Draw.easeOutCubic(Theme.progress(openedAt, 320f));
		int x0 = panelX, y0 = panelY, x1 = panelX + panelW(), y1 = panelY + panelH();

		MenuSkin.panel(g, font, x0, y0, x1, y1, HEADER_H, SIDEBAR_W, FOOTER_H, TABS[lastTab], intro);
		MenuSnow.panelTop(x0, y0, x1);

		LmgConfig.MenuStyle style = MenuSkin.style();
		int[] tc = MenuSkin.textColors();
		int faint = style == LmgConfig.MenuStyle.LMG ? 0x7C8BA3 : tc[1];

		// what this tab is about, in one plain line
		int contentX = x0 + SIDEBAR_W + 2 + MenuSkin.contentInset();
		if (style == LmgConfig.MenuStyle.LMG) {
			int hy = y0 + HEADER_H + 8;
			g.text(font, TABS[lastTab], contentX + 6, hy, Draw.white(intro), false);
			g.text(font, SUBTITLES[lastTab], contentX + 12 + font.width(TABS[lastTab]), hy, Draw.withAlpha(faint, intro), false);
		} else if (style == LmgConfig.MenuStyle.NEVERLOSE) {
			int cx0 = x0 + SIDEBAR_W + 7, cy0 = y0 + HEADER_H + 4;
			g.text(font, SUBTITLES[lastTab], cx0 + 14 + font.width(TABS[lastTab].toUpperCase()), cy0 + 6, Draw.withAlpha(0x2E3A47, intro), false);
		}

		// footer: what the setting under the mouse does, or a small tip
		String hint = null;
		if (waitingKey != null) hint = "Press the new key. Esc clears it.";
		else for (SettingRow r : rows) if (r.hovered() && !r.hint().isEmpty()) hint = r.hint();
		boolean isTip = hint == null;
		if (isTip) hint = "tip: " + TIPS[(int) ((Util.getMillis() / 7000) % TIPS.length)];
		int hx0 = x0 + SIDEBAR_W + 6, hx1 = x1 - 10;
		if (font.width(hint) > hx1 - hx0) hint = font.plainSubstrByWidth(hint, hx1 - hx0 - 8) + "..";
		int hintColor = isTip ? (style == LmgConfig.MenuStyle.LMG ? 0x55637A : (style == LmgConfig.MenuStyle.SKEET ? 0x4A4A4A : 0x3E4766))
				: (style == LmgConfig.MenuStyle.LMG ? 0xF2C14E : tc[0]);
		g.text(font, hint, hx0, y1 - FOOTER_H + 6, Draw.withAlpha(hintColor, intro), false);

		super.extractRenderState(g, mouseX, mouseY, partialTick);

		MenuSnow.drawFront(g, 1f);
		if (dropdown != null) drawDropdown(g, mouseX, mouseY);
	}

	// ---------------------------------------------------------------------------------------------
	// Dropdown list
	// ---------------------------------------------------------------------------------------------

	/** {x0, y0, x1, y1} of the open list. Opens upwards if there's no room below. */
	private int[] dropdownRect() {
		int[] box = dropdown.box();
		int listW = box[2] - box[0];
		for (String o : dropdown.options()) listW = Math.max(listW, font.width(o) + 24);
		int listH = dropdown.options().size() * ITEM_H + 4;
		int x1 = box[2], x0 = x1 - listW;
		int y0 = box[3] + 2;
		if (y0 + listH > height - 2) y0 = box[1] - 2 - listH;
		return new int[]{x0, y0, x1, y0 + listH};
	}

	private int dropdownItemAt(double mx, double my) {
		int[] r = dropdownRect();
		if (mx < r[0] || mx >= r[2] || my < r[1] + 2 || my >= r[3] - 2) return -1;
		int i = (int) ((my - r[1] - 2) / ITEM_H);
		return i < dropdown.options().size() ? i : -1;
	}

	private void drawDropdown(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.nextStratum(); // on top of everything else
		int[] r = dropdownRect();
		int hovered = dropdownItemAt(mouseX, mouseY);
		int selected = dropdown.selected();
		int acc = MenuSkin.accent();
		LmgConfig.MenuStyle style = MenuSkin.style();
		switch (style) {
			case SKEET -> {
				g.fill(r[0], r[1], r[2], r[3], 0xFF000000);
				g.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 0xFF1A1A1A);
			}
			case NEVERLOSE -> {
				Draw.roundRect(g, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 5, 0xFF2B4376);
				Draw.roundRect(g, r[0], r[1], r[2], r[3], 4, 0xFF001C38);
			}
			default -> {
				Draw.roundRect(g, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 5, Draw.white(0.10f));
				Draw.roundRect(g, r[0], r[1], r[2], r[3], 4, 0xF2141418);
			}
		}
		for (int i = 0; i < dropdown.options().size(); i++) {
			int iy = r[1] + 2 + i * ITEM_H;
			if (i == hovered) {
				switch (style) {
					case SKEET -> g.fill(r[0] + 1, iy, r[2] - 1, iy + ITEM_H, 0xFF262626);
					case NEVERLOSE -> Draw.roundRect(g, r[0] + 2, iy, r[2] - 2, iy + ITEM_H, 3, 0xFF0A2A52);
					default -> Draw.roundRect(g, r[0] + 2, iy, r[2] - 2, iy + ITEM_H, 3, Draw.withAlpha(acc, 0.16f));
				}
			}
			int tx = r[0] + 6, ty = iy + (ITEM_H - font.lineHeight) / 2 + 1;
			Integer sw = i < dropdown.optionColors().size() ? dropdown.optionColors().get(i) : null;
			if (sw != null) {
				if (style == LmgConfig.MenuStyle.SKEET) g.fill(tx, ty, tx + 5, ty + 5, 0xFF000000 | sw);
				else Draw.roundRect(g, tx, ty, tx + 5, ty + 5, 2, 0xFF000000 | sw);
				tx += 9;
			}
			int color = i == selected ? acc : (i == hovered ? 0xFFFFFF : (style == LmgConfig.MenuStyle.SKEET ? 0x989898 : 0xC9CFE3));
			g.text(font, dropdown.options().get(i), tx, ty, 0xFF000000 | color, false);
			if (i == selected && style == LmgConfig.MenuStyle.SKEET) {
				g.text(font, dropdown.options().get(i), tx + 1, ty, 0xFF000000 | color, false); // bold, like gamesense
			}
		}
	}

	private void bindDone() {
		waitingKey = null;
		net.minecraft.client.KeyMapping.resetMapping();
		minecraft.options.save();
	}

	// ---------------------------------------------------------------------------------------------
	// Dragging the panel by its header
	// ---------------------------------------------------------------------------------------------

	private boolean inHeader(double mx, double my) {
		return mx >= panelX && mx < panelX + panelW() && my >= panelY && my < panelY + HEADER_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (waitingKey != null && event.button() > 1) {
			// middle and side mouse buttons can be bound too
			waitingKey.setKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(event.button()));
			bindDone();
			return true;
		}
		if (waitingKey != null) {
			boolean sameRow = false;
			for (SettingRow r : rows) if (r.hovered()) sameRow = true;
			if (!sameRow) {
				waitingKey = null;
				return true;
			}
		}
		if (dropdown != null) {
			// any click while the list is open either picks an option or just closes it
			int i = dropdownItemAt(event.x(), event.y());
			SettingRow row = dropdown;
			dropdown = null;
			if (i >= 0) row.choose(i);
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() == 0 && inHeader(event.x(), event.y())) {
			if (doubleClick) { // double-click the header to put the panel back in the middle
				LmgConfig.get().menuOffsetX = 0;
				LmgConfig.get().menuOffsetY = 0;
				LmgConfig.save();
				rebuildWidgets();
				return true;
			}
			dragging = true;
			dragStartX = event.x();
			dragStartY = event.y();
			dragOrigOffX = LmgConfig.get().menuOffsetX;
			dragOrigOffY = LmgConfig.get().menuOffsetY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			LmgConfig c = LmgConfig.get();
			int oldX = panelX, oldY = panelY;
			c.menuOffsetX = dragOrigOffX + (int) Math.round(event.x() - dragStartX);
			c.menuOffsetY = dragOrigOffY + (int) Math.round(event.y() - dragStartY);
			computePanel();
			int mx = panelX - oldX, my = panelY - oldY;
			for (GuiEventListener child : children()) {
				if (child instanceof AbstractWidget w) w.setPosition(w.getX() + mx, w.getY() + my);
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			LmgConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (waitingKey != null) {
			waitingKey.setKey(event.isEscape() ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN
					: com.mojang.blaze3d.platform.InputConstants.getKey(event));
			bindDone();
			return true;
		}
		if (dropdown != null && event.isEscape()) {
			dropdown = null;
			return true;
		}
		if (LmgClient.OPEN_MENU_KEY.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		LmgConfig.save();
		minecraft.gui.setScreen(parent);
	}
}
