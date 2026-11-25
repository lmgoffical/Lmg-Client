package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.config.CloudCodes;
import dev.lmg.client.config.ConfigProfiles;
import dev.lmg.client.render.Draw;
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
	private static final String[] TABS = {"Style", "Configs", "Theme", "World", "Avatar", "Effects", "Target", "PvP", "Items", "Menus", "HUD"};
	private static final int PANEL_W = 300;
	private static final int PANEL_W_SKEET = 380; // skeet uses two columns of group boxes
	private static final int SIDEBAR_W = 74;
	private static final int HEADER_H = 24;
	private static final int ROW_H = 14;
	private static final int ROW_GAP = 16;
	private static final int MAX_ROWS = 9;
	private static final int FOOTER_H = 18;

	private static int lastTab = 3; // World
	private static final int TAB_GAP = 13;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int panelX, panelY;
	private boolean dragging;
	private double dragStartX, dragStartY;
	private int dragOrigOffX, dragOrigOffY;
	private boolean confirmReset;
	private SettingRow dropdown; // the dropdown whose list is open, if any
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
		List<SettingRow> rows = rowsFor(lastTab, c);
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
		if (tab == 0) {
			// Style: how this menu looks
			r.add(SettingRow.dropdown("Menu style",
					List.of("Lmg Client (original)", "Skeet", "Neverlose"),
					List.of(c.accent.rgb, MenuSkin.SK_ACCENT, MenuSkin.NL_ACCENT),
					() -> c.menuStyle.ordinal(),
					i -> { c.menuStyle = LmgConfig.MenuStyle.values()[i]; rebuildWidgets(); }));
			r.add(SettingRow.toggle("Use my accent color", () -> c.menuUseAccent, v -> c.menuUseAccent = v));
			r.add(SettingRow.choice("Accent color", () -> c.accent.label,
					() -> c.accent = cycle(LmgConfig.Accent.values(), c.accent, 1),
					() -> c.accent = cycle(LmgConfig.Accent.values(), c.accent, -1), () -> c.accent.rgb));
			return r;
		}
		if (tab == 1) {
			// Configs: saved setups and share codes
			r.add(SettingRow.button("Saved configs", "Open", () -> minecraft.gui.setScreen(new ConfigsScreen(this))));
			r.add(SettingRow.button("Browse configs online", "Website", () ->
					net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,
							java.net.URI.create("https://lmgoffical.github.io/Lmg-Client/#/configs"))));
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
			}));
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
			}));
			return r;
		}
		switch (tab - 2) {
			case 0 -> {
				r.add(SettingRow.choice("Accent color", () -> c.accent.label,
						() -> c.accent = cycle(LmgConfig.Accent.values(), c.accent, 1),
						() -> c.accent = cycle(LmgConfig.Accent.values(), c.accent, -1), () -> c.accent.rgb));
				r.add(SettingRow.choice("Background", () -> c.backdrop.label,
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, 1),
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, -1), () -> c.backdrop.top));
				r.add(SettingRow.choice("Animation speed", () -> LmgConfig.ANIMATION_SPEED_LABELS[c.animationSpeed],
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, 1),
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, -1)));
			}
			case 1 -> {
				r.add(enumRow("Sky theme", LmgConfig.SkyTheme.values(), () -> c.skyTheme, v -> c.skyTheme = v, v -> v.label));
				r.add(enumRow("Time of day", LmgConfig.TimeOfDay.values(), () -> c.timeOfDay, v -> c.timeOfDay = v, v -> v.label));
				r.add(enumRow("Fog", LmgConfig.FogLevel.values(), () -> c.fog, v -> c.fog = v, v -> v.label));
				r.add(enumRow("Clouds", LmgConfig.CloudMode.values(), () -> c.clouds, v -> c.clouds = v, v -> v.label));
				r.add(enumRow("Aurora", LmgConfig.Aurora.values(), () -> c.aurora, v -> c.aurora = v, v -> v.label));
				r.add(SettingRow.toggle("Always show stars", () -> c.alwaysStars, v -> c.alwaysStars = v));
				r.add(SettingRow.toggle("Rain & snow", () -> c.showWeather, v -> c.showWeather = v));
			}
			case 2 -> {
				r.add(SettingRow.toggle("Lmg cape", () -> c.cape, v -> c.cape = v));
				r.add(colorRow("Wings", () -> c.wings, v -> c.wings = v));
				r.add(colorRow("Halo", () -> c.halo, v -> c.halo = v));
				r.add(SettingRow.toggle("Crown", () -> c.crown, v -> c.crown = v));
				r.add(colorRow("Cat ears", () -> c.catEars, v -> c.catEars = v));
				r.add(colorRow("China hat", () -> c.chinaHat, v -> c.chinaHat = v));
				r.add(colorRow("Energy shield", () -> c.shield, v -> c.shield = v));
			}
			case 3 -> {
				r.add(enumRow("Trail", LmgConfig.Trail.values(), () -> c.trail, v -> c.trail = v, v -> v.label));
				r.add(enumRow("Ambient particles", LmgConfig.Ambient.values(), () -> c.ambient, v -> c.ambient = v, v -> v.label));
				r.add(SettingRow.choice("Ambient amount", () -> LmgConfig.AMOUNT_LABELS[c.ambientAmount],
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, 1),
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, -1)));
			}
			case 4 -> {
				r.add(SettingRow.toggle("Glowing outline", () -> c.targetOutline, v -> c.targetOutline = v));
				r.add(SettingRow.toggle("Target info panel", () -> c.targetHud, v -> c.targetHud = v));
				r.add(SettingRow.toggle("Target reticle", () -> c.targetReticle, v -> c.targetReticle = v));
				r.add(enumRow("Hit effect", LmgConfig.HitEffect.values(), () -> c.hitEffect, v -> c.hitEffect = v, v -> v.label));
				r.add(enumRow("Kill effect", LmgConfig.KillEffect.values(), () -> c.killEffect, v -> c.killEffect = v, v -> v.label));
			}
			case 5 -> {
				r.add(enumRow("Block overlay", LmgConfig.BlockOverlay.values(), () -> c.blockOverlay, v -> c.blockOverlay = v, v -> v.label));
				r.add(SettingRow.choice("Overlay color", () -> c.overlayColor.label,
						() -> { do c.overlayColor = cycle(LmgConfig.CosmeticColor.values(), c.overlayColor, 1); while (c.overlayColor == LmgConfig.CosmeticColor.OFF); },
						() -> { do c.overlayColor = cycle(LmgConfig.CosmeticColor.values(), c.overlayColor, -1); while (c.overlayColor == LmgConfig.CosmeticColor.OFF); },
						() -> c.overlayColor.rgb(Util.getMillis() / 50f)));
				r.add(SettingRow.toggle("Fullbright", () -> c.fullbright, v -> c.fullbright = v));
				r.add(SettingRow.toggle("Ore edges", OreOutlines::isEnabled, OreOutlines::setEnabled));
				r.add(SettingRow.toggle("Ore glow in the dark", () -> c.oreGlow, v -> c.oreGlow = v));
				r.add(SettingRow.toggle("Potion effects HUD", () -> c.potionHud, v -> c.potionHud = v));
				r.add(SettingRow.toggle("Low fire", () -> c.lowFire, v -> c.lowFire = v));
				r.add(SettingRow.toggle("No hurt camera shake", () -> c.noHurtCam, v -> c.noHurtCam = v));
			}
			case 6 -> {
				r.add(SettingRow.choice("Held item size", () -> Math.round(LmgConfig.ITEM_SCALES[c.itemScale] * 100) + "%",
						() -> c.itemScale = step(c.itemScale, LmgConfig.ITEM_SCALES.length, 1),
						() -> c.itemScale = step(c.itemScale, LmgConfig.ITEM_SCALES.length, -1)));
				r.add(SettingRow.toggle("Lower held items", () -> c.lowerItems, v -> c.lowerItems = v));
				r.add(SettingRow.toggle("Lmg totem", () -> OreOutlines.isEnabled(OreOutlines.TOTEM),
						v -> { c.lmgTotem = v; OreOutlines.setEnabled(OreOutlines.TOTEM, v); }));
			}
			case 7 -> {
				r.add(SettingRow.toggle("Splash screen", () -> c.showSplash, v -> c.showSplash = v));
				r.add(SettingRow.toggle("Themed vanilla menus", () -> c.themedMenus, v -> c.themedMenus = v));
				r.add(SettingRow.toggle("Light curtains", () -> c.curtains, v -> c.curtains = v));
				r.add(SettingRow.choice("Sparkles", () -> LmgConfig.SPARKLE_AMOUNT_LABELS[c.sparkles],
						() -> c.sparkles = step(c.sparkles, LmgConfig.SPARKLE_AMOUNTS.length, 1),
						() -> c.sparkles = step(c.sparkles, LmgConfig.SPARKLE_AMOUNTS.length, -1)));
				r.add(SettingRow.toggle("Spin skin preview", () -> c.skinSpin, v -> c.skinSpin = v));
			}
			default -> {
				r.add(SettingRow.button("HUD layout", "Edit", () -> minecraft.gui.setScreen(new HudEditorScreen(this))));
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
		}
		return r;
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
			g.fill(0, 0, width, height, 0x55000000);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float intro = Draw.easeOutCubic(Theme.progress(openedAt, 320f));
		int x0 = panelX, y0 = panelY, x1 = panelX + panelW(), y1 = panelY + panelH();

		MenuSkin.panel(g, font, x0, y0, x1, y1, HEADER_H, SIDEBAR_W, FOOTER_H, TABS[lastTab], intro);

		// small hint bottom-right
		String hint = "Right Shift to close";
		int hintColor = switch (MenuSkin.style()) {
			case SKEET -> 0x4A4A4A;
			case NEVERLOSE -> 0x3E4766;
			default -> 0x55555F;
		};
		g.text(font, hint, x1 - 10 - font.width(hint), y1 - FOOTER_H + 6, Draw.withAlpha(hintColor, intro), false);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
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

	// ---------------------------------------------------------------------------------------------
	// Dragging the panel by its header
	// ---------------------------------------------------------------------------------------------

	private boolean inHeader(double mx, double my) {
		return mx >= panelX && mx < panelX + panelW() && my >= panelY && my < panelY + HEADER_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
