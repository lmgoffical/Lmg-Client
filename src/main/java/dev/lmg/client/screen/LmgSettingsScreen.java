package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import dev.lmg.client.world.OreOutlines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lmg Client menu (main menu → Client, or Right Shift anywhere).
 * Three tabs: Theme, Menus and HUD.
 */
public class LmgSettingsScreen extends Screen {
	private static final String[] TABS = {"Theme", "World", "Avatar", "Effects", "Target", "PvP", "Menus", "HUD"};
	private static final int PANEL_W = 320;
	private static final int ROW_H = 14;
	private static final int ROW_GAP = 15;
	private static final int MAX_ROWS = 11;

	private static int lastTab = 1; // open on World first, since it's used most in-game

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private final List<int[]> tabBounds = new ArrayList<>();
	private float tabIndicator = -1;
	private long lastFrame = -1;
	private boolean confirmReset;
	private int panelTop;

	/** @param parent screen to go back to, or {@code null} to return to the game */
	public LmgSettingsScreen(Screen parent) {
		super(Component.literal(LmgClient.CLIENT_NAME));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LmgConfig c = LmgConfig.get();
		tabBounds.clear();
		int total = 22 + 18 + MAX_ROWS * ROW_GAP + 10 + 16;
		panelTop = Math.max(6, height / 2 - total / 2);
		int left = width / 2 - PANEL_W / 2;
		long t = Util.getMillis();

		// Tabs
		int tabY = panelTop + 22;
		int tabW = 40;
		int tabsLeft = width / 2 - (tabW * TABS.length) / 2;
		for (int i = 0; i < TABS.length; i++) {
			final int index = i;
			int tx = tabsLeft + i * tabW;
			tabBounds.add(new int[]{tx, tabW});
			addRenderableWidget(new MenuTextButton(tx, tabY, tabW, 14, Component.literal(TABS[i]), openedAt, () -> {
				if (lastTab != index) {
					lastTab = index;
					confirmReset = false;
					rebuildWidgets();
				}
			}));
		}
		if (tabIndicator < 0) tabIndicator = lastTab;

		// Rows for the selected tab
		int y = tabY + 22;
		int x = left;
		int w = PANEL_W;
		List<SettingRow> rows = new ArrayList<>();
		switch (lastTab) {
			case 0 -> {
				rows.add(new SettingRow(x, 0, w, ROW_H, "Accent color", () -> c.accent.label,
						() -> c.accent = cycle(LmgConfig.Accent.values(), c.accent, 1),
						() -> c.accent = cycle(LmgConfig.Accent.values(), c.accent, -1),
						() -> c.accent.rgb, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Background", () -> c.backdrop.label,
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, 1),
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, -1),
						() -> c.backdrop.top, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Animation speed", () -> LmgConfig.ANIMATION_SPEED_LABELS[c.animationSpeed],
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, 1),
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, -1),
						null, 0));
			}
			case 1 -> {
				rows.add(new SettingRow(x, 0, w, ROW_H, "Sky theme", () -> c.skyTheme.label,
						() -> c.skyTheme = cycle(LmgConfig.SkyTheme.values(), c.skyTheme, 1),
						() -> c.skyTheme = cycle(LmgConfig.SkyTheme.values(), c.skyTheme, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Time of day", () -> c.timeOfDay.label,
						() -> c.timeOfDay = cycle(LmgConfig.TimeOfDay.values(), c.timeOfDay, 1),
						() -> c.timeOfDay = cycle(LmgConfig.TimeOfDay.values(), c.timeOfDay, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Fog", () -> c.fog.label,
						() -> c.fog = cycle(LmgConfig.FogLevel.values(), c.fog, 1),
						() -> c.fog = cycle(LmgConfig.FogLevel.values(), c.fog, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Clouds", () -> c.clouds.label,
						() -> c.clouds = cycle(LmgConfig.CloudMode.values(), c.clouds, 1),
						() -> c.clouds = cycle(LmgConfig.CloudMode.values(), c.clouds, -1),
						null, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Always show stars", () -> c.alwaysStars, v -> c.alwaysStars = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Rain & snow", () -> c.showWeather, v -> c.showWeather = v, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Aurora (night sky)", () -> c.aurora.label,
						() -> c.aurora = cycle(LmgConfig.Aurora.values(), c.aurora, 1),
						() -> c.aurora = cycle(LmgConfig.Aurora.values(), c.aurora, -1),
						null, 0));
			}
			case 2 -> {
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Lmg Client cape", () -> c.cape, v -> c.cape = v, 0));
				rows.add(colorRow(x, w, "Wings", () -> c.wings, v -> c.wings = v));
				rows.add(colorRow(x, w, "Halo", () -> c.halo, v -> c.halo = v));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Crown", () -> c.crown, v -> c.crown = v, 0));
				rows.add(colorRow(x, w, "Cat ears", () -> c.catEars, v -> c.catEars = v));
				rows.add(colorRow(x, w, "China hat", () -> c.chinaHat, v -> c.chinaHat = v));
				rows.add(colorRow(x, w, "Energy shield", () -> c.shield, v -> c.shield = v));
			}
			case 3 -> {
				rows.add(new SettingRow(x, 0, w, ROW_H, "Trail", () -> c.trail.label,
						() -> c.trail = cycle(LmgConfig.Trail.values(), c.trail, 1),
						() -> c.trail = cycle(LmgConfig.Trail.values(), c.trail, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Ambient particles", () -> c.ambient.label,
						() -> c.ambient = cycle(LmgConfig.Ambient.values(), c.ambient, 1),
						() -> c.ambient = cycle(LmgConfig.Ambient.values(), c.ambient, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Ambient amount", () -> LmgConfig.AMOUNT_LABELS[c.ambientAmount],
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, 1),
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, -1),
						null, 0));
			}
			case 4 -> {
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Glowing outline on target", () -> c.targetOutline, v -> c.targetOutline = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Target info (health, gear, effects)", () -> c.targetHud, v -> c.targetHud = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Target reticle", () -> c.targetReticle, v -> c.targetReticle = v, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Hit effect", () -> c.hitEffect.label,
						() -> c.hitEffect = cycle(LmgConfig.HitEffect.values(), c.hitEffect, 1),
						() -> c.hitEffect = cycle(LmgConfig.HitEffect.values(), c.hitEffect, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Kill effect", () -> c.killEffect.label,
						() -> c.killEffect = cycle(LmgConfig.KillEffect.values(), c.killEffect, 1),
						() -> c.killEffect = cycle(LmgConfig.KillEffect.values(), c.killEffect, -1),
						null, 0));
			}
			case 5 -> {
				rows.add(new SettingRow(x, 0, w, ROW_H, "Block overlay", () -> c.blockOverlay.label,
						() -> c.blockOverlay = cycle(LmgConfig.BlockOverlay.values(), c.blockOverlay, 1),
						() -> c.blockOverlay = cycle(LmgConfig.BlockOverlay.values(), c.blockOverlay, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Overlay color", () -> c.overlayColor.label,
						() -> { do c.overlayColor = cycle(LmgConfig.CosmeticColor.values(), c.overlayColor, 1); while (c.overlayColor == LmgConfig.CosmeticColor.OFF); },
						() -> { do c.overlayColor = cycle(LmgConfig.CosmeticColor.values(), c.overlayColor, -1); while (c.overlayColor == LmgConfig.CosmeticColor.OFF); },
						() -> c.overlayColor.rgb(Util.getMillis() / 50f), 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Fullbright", () -> c.fullbright, v -> c.fullbright = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Ore edges (texture pack)", OreOutlines::isEnabled, OreOutlines::setEnabled, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Ore glow in the dark", () -> c.oreGlow, v -> c.oreGlow = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Potion effects HUD", () -> c.potionHud, v -> c.potionHud = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Low fire", () -> c.lowFire, v -> c.lowFire = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "No hurt camera shake", () -> c.noHurtCam, v -> c.noHurtCam = v, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Held item size", () -> Math.round(LmgConfig.ITEM_SCALES[c.itemScale] * 100) + "%",
						() -> c.itemScale = step(c.itemScale, LmgConfig.ITEM_SCALES.length, 1),
						() -> c.itemScale = step(c.itemScale, LmgConfig.ITEM_SCALES.length, -1),
						null, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Lower held items", () -> c.lowerItems, v -> c.lowerItems = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Lmg totem (Star of David)", () -> OreOutlines.isEnabled(OreOutlines.TOTEM),
						v -> { c.lmgTotem = v; OreOutlines.setEnabled(OreOutlines.TOTEM, v); }, 0));
			}
			case 6 -> {
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Splash screen", () -> c.showSplash, v -> c.showSplash = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Themed vanilla menus", () -> c.themedMenus, v -> c.themedMenus = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Light curtains", () -> c.curtains, v -> c.curtains = v, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Sparkles", () -> LmgConfig.SPARKLE_AMOUNT_LABELS[c.sparkles],
						() -> c.sparkles = step(c.sparkles, LmgConfig.SPARKLE_AMOUNTS.length, 1),
						() -> c.sparkles = step(c.sparkles, LmgConfig.SPARKLE_AMOUNTS.length, -1),
						null, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Spin skin preview", () -> c.skinSpin, v -> c.skinSpin = v, 0));
			}
			default -> {
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "HUD bar", () -> c.hudEnabled, v -> c.hudEnabled = v, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Position", () -> c.hudPosition.label,
						() -> c.hudPosition = cycle(LmgConfig.HudPosition.values(), c.hudPosition, 1),
						() -> c.hudPosition = cycle(LmgConfig.HudPosition.values(), c.hudPosition, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Size", () -> Math.round(LmgConfig.HUD_SCALES[c.hudScale] * 100) + "%",
						() -> c.hudScale = step(c.hudScale, LmgConfig.HUD_SCALES.length, 1),
						() -> c.hudScale = step(c.hudScale, LmgConfig.HUD_SCALES.length, -1),
						null, 0));
				rows.add(new SettingRow(x, 0, w, ROW_H, "Background opacity", () -> Math.round(LmgConfig.HUD_OPACITIES[c.hudOpacity] * 100) + "%",
						() -> c.hudOpacity = step(c.hudOpacity, LmgConfig.HUD_OPACITIES.length, 1),
						() -> c.hudOpacity = step(c.hudOpacity, LmgConfig.HUD_OPACITIES.length, -1),
						null, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Show server", () -> c.hudShowServer, v -> c.hudShowServer = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Show FPS", () -> c.hudShowFps, v -> c.hudShowFps = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Show ping", () -> c.hudShowPing, v -> c.hudShowPing = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Show clock", () -> c.hudShowClock, v -> c.hudShowClock = v, 0));
				rows.add(SettingRow.toggle(x, 0, w, ROW_H, "Show coordinates", () -> c.hudShowCoords, v -> c.hudShowCoords = v, 0));
			}
		}
		// Position rows and stagger their fade-in
		for (int i = 0; i < rows.size(); i++) {
			SettingRow row = rows.get(i);
			row.setY(y + i * ROW_GAP);
			row.setAppearAt(t + i * 35L);
			addRenderableWidget(row);
		}

		// Footer
		int footerY = panelTop + 22 + 18 + MAX_ROWS * ROW_GAP + 10;
		int half = PANEL_W / 2 - 4;
		MenuTextButton[] reset = new MenuTextButton[1];
		reset[0] = new MenuTextButton(left, footerY, half, 14, Component.literal("Reset all"), openedAt, () -> {
			if (!confirmReset) {
				confirmReset = true;
				reset[0].setMessage(Component.literal("Click again to reset"));
			} else {
				confirmReset = false;
				LmgConfig.reset();
				rebuildWidgets();
			}
		});
		addRenderableWidget(reset[0]);
		addRenderableWidget(new MenuTextButton(left + PANEL_W - half, footerY, half, 14, Component.translatable("gui.done"), openedAt, this::onClose));
	}

	/** A row that cycles through the cosmetic colors, with a little color square. */
	private SettingRow colorRow(int x, int w, String label,
								java.util.function.Supplier<LmgConfig.CosmeticColor> get,
								java.util.function.Consumer<LmgConfig.CosmeticColor> set) {
		return new SettingRow(x, 0, w, ROW_H, label, () -> get.get().label,
				() -> set.accept(cycle(LmgConfig.CosmeticColor.values(), get.get(), 1)),
				() -> set.accept(cycle(LmgConfig.CosmeticColor.values(), get.get(), -1)),
				() -> get.get() == LmgConfig.CosmeticColor.OFF ? 0x3A3A44 : get.get().rgb(Util.getMillis() / 50f), 0);
	}

	private static <E extends Enum<E>> E cycle(E[] values, E current, int dir) {
		return values[Math.floorMod(current.ordinal() + dir, values.length)];
	}

	private static int step(int index, int length, int dir) {
		return Math.floorMod(index + dir, length);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			// In a world: keep the game visible behind a blur, just darker.
			super.extractBackground(g, mouseX, mouseY, partialTick);
			g.fill(0, 0, width, height, 0x80000000);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float intro = Draw.easeOutCubic(Theme.progress(openedAt, 450f));

		// Panel
		int left = width / 2 - PANEL_W / 2 - 10;
		int right = width / 2 + PANEL_W / 2 + 10;
		int bottom = panelTop + 22 + 18 + MAX_ROWS * ROW_GAP + 10 + 16 + 8;
		Draw.roundRect(g, left, panelTop - 8, right, bottom, 8, Draw.withAlpha(0x000000, 0.45f * intro));

		// Title
		String title = LmgClient.CLIENT_NAME;
		float titleScale = 1.4f;
		float titleW = font.width(title) * titleScale;
		Draw.logo(g, width / 2f - titleW / 2f - 10, panelTop + 5, 14f, 0, intro);
		Draw.scaledText(g, font, title, width / 2f + 4, panelTop, titleScale, Draw.white(intro));

		// Sliding underline under the selected tab
		tabIndicator = Draw.approach(tabIndicator, lastTab, 14f * Theme.speed(), dt);
		if (!tabBounds.isEmpty()) {
			int i0 = (int) Math.floor(tabIndicator);
			int i1 = Math.min(tabBounds.size() - 1, i0 + 1);
			float f = tabIndicator - i0;
			float tx = Draw.lerp(tabBounds.get(i0)[0], tabBounds.get(i1)[0], f);
			int tw = tabBounds.get(0)[1];
			int lineW = 26;
			int lx = Math.round(tx + tw / 2f - lineW / 2f);
			int ly = panelTop + 22 + 15;
			g.fill(lx, ly, lx + lineW, ly + 1, Theme.accent(0.9f * intro));
		}

		super.extractRenderState(g, mouseX, mouseY, partialTick);

		// Hint at the bottom
		String hint = "Left click: next  ·  Right click: back";
		g.centeredText(font, hint, width / 2, bottom + 6, Draw.withAlpha(0x8A8A96, intro));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
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
