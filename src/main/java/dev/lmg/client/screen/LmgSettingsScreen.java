package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
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
	private static final String[] TABS = {"Theme", "World", "Avatar", "Effects", "Target", "PvP", "Items", "Menus", "HUD"};
	private static final int PANEL_W = 300;
	private static final int SIDEBAR_W = 74;
	private static final int HEADER_H = 24;
	private static final int ROW_H = 14;
	private static final int ROW_GAP = 16;
	private static final int MAX_ROWS = 9;
	private static final int FOOTER_H = 18;

	private static int lastTab = 1;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int panelX, panelY;
	private boolean dragging;
	private double dragStartX, dragStartY;
	private int dragOrigOffX, dragOrigOffY;
	private boolean confirmReset;

	/** @param parent screen to go back to, or {@code null} to return to the game */
	public LmgSettingsScreen(Screen parent) {
		super(Component.literal(LmgClient.CLIENT_NAME));
		this.parent = parent;
	}

	private static int panelH() {
		return HEADER_H + 6 + MenuSkin.contentTopPad() + MAX_ROWS * ROW_GAP + FOOTER_H;
	}

	private void computePanel() {
		LmgConfig c = LmgConfig.get();
		panelX = width / 2 - PANEL_W / 2 + c.menuOffsetX;
		panelY = height / 2 - panelH() / 2 + c.menuOffsetY;
		panelX = Math.max(4, Math.min(panelX, width - PANEL_W - 4));
		panelY = Math.max(4, Math.min(panelY, height - panelH() - 4));
	}

	@Override
	protected void init() {
		computePanel();
		LmgConfig c = LmgConfig.get();
		long t = Util.getMillis();

		// Header buttons (Neverlose keeps its logo top-left, so Back moves to the right there)
		boolean nl = MenuSkin.style() == LmgConfig.MenuStyle.NEVERLOSE;
		int backX = nl ? panelX + PANEL_W - 64 - 48 : panelX + 6;
		addRenderableWidget(new SkinButton(backX, panelY + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(panelX + PANEL_W - 62, panelY + 6, 56, 13, Component.literal("Edit HUD"), openedAt,
				() -> minecraft.gui.setScreen(new HudEditorScreen(this))));

		// Sidebar
		int sy = panelY + HEADER_H + 6;
		for (int i = 0; i < TABS.length; i++) {
			final int index = i;
			addRenderableWidget(new SideTab(panelX + 6, sy + i * 15, SIDEBAR_W - 10, 14, TABS[i],
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
		int inset = MenuSkin.contentInset();
		int rx = panelX + SIDEBAR_W + 2 + inset;
		int rw = PANEL_W - SIDEBAR_W - 8 - inset * 2;
		int ry = sy + MenuSkin.contentTopPad();
		for (int i = 0; i < rows.size(); i++) {
			addRenderableWidget(rows.get(i).at(rx, ry + i * ROW_GAP, rw, ROW_H, t + i * 25L));
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

	private List<SettingRow> rowsFor(int tab, LmgConfig c) {
		List<SettingRow> r = new ArrayList<>();
		switch (tab) {
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
				r.add(SettingRow.choice("Menu style", () -> c.menuStyle.label,
						() -> { c.menuStyle = cycle(LmgConfig.MenuStyle.values(), c.menuStyle, 1); rebuildWidgets(); },
						() -> { c.menuStyle = cycle(LmgConfig.MenuStyle.values(), c.menuStyle, -1); rebuildWidgets(); }));
				r.add(SettingRow.toggle("Use my accent in menu", () -> c.menuUseAccent, v -> c.menuUseAccent = v));
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
		int x0 = panelX, y0 = panelY, x1 = panelX + PANEL_W, y1 = panelY + panelH();

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
	}

	// ---------------------------------------------------------------------------------------------
	// Dragging the panel by its header
	// ---------------------------------------------------------------------------------------------

	private boolean inHeader(double mx, double my) {
		return mx >= panelX && mx < panelX + PANEL_W && my >= panelY && my < panelY + HEADER_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
