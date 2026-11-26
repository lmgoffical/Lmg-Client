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
public class LmgSettingsScreen extends Screen implements dev.lmg.client.render.WorldFilter.GreyBehind {
	private record Page(String name, String about) {}

	private record Category(String name, Page... pages) {}

	/** The sidebar: a few groups, each with its own pages along the top. */
	private static final Category[] CATS = {
			new Category("Visuals", new Page("Sky", "sky, time, fog and clouds"), new Page("Weather", "rain, snow and winter"), new Page("Effects", "crosshair, damage, blur, colours"), new Page("Cinematic", "shaders, film mode and photos")),
			new Category("You", new Page("Outfit", "cosmetics, pets and you"), new Page("Trails", "particles around you"), new Page("Items", "what's in your hand"), new Page("Sharing", "what other Lmg players see")),
			new Category("Combat", new Page("Target", "whatever you're aiming at"), new Page("Hits", "when you land a hit"), new Page("Clarity", "seeing clearly in a fight")),
			new Category("HUD", new Page("Bar", "the bar at the top"), new Page("Maps", "minimap, map and waypoints")),
			new Category("Social", new Page("Friends", "who's online and joining them"), new Page("Emotes", "the emote wheel"), new Page("UID", "your Lmg number")),
			new Category("Menu", new Page("Look", "how this menu looks"), new Page("Main menu", "the title screen"), new Page("Help", "the tour, safety and mods")),
			new Category("Game", new Page("Keys", "click a key, then press the new one"), new Page("Tools", "zoom, freecam and server-safe"), new Page("FPS", "make the game run smoother"), new Page("Configs", "save setups, share them with a code"))
	};

	private static final String[] TIPS = {"drag the top bar to move this menu", "double-click the top bar to re-center it",
			"right-click a setting to go back one", "M opens the world map", "hold C to zoom, scroll to zoom more",
			"every key can be changed in Keys", "Right Shift closes this"};
	private static final int PANEL_W = 300;
	private static final int PANEL_W_SKEET = 380; // skeet uses two columns of group boxes
	private static final int SIDEBAR_W = 74;
	private static final int HEADER_H = 24;
	private static final int ROW_H = 14;
	private static final int ROW_GAP = 16;
	private static final int MAX_ROWS = 10;
	private static final int FOOTER_H = 18;

	private static int lastCat = 0;                              // Visuals
	private static final int[] lastPage = new int[CATS.length];  // the page you were on in each group
	private static int rowScroll;                                // for pages with more rows than fit
	private static long pageShownAt;
	private static final int TAB_GAP = 16;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private final MenuAvatar avatar = new MenuAvatar();
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
		pageShownAt = Util.getMillis();   // say what the page is for when the menu opens
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
		// with you standing next to it, centre the two together (unless you've moved the panel yourself)
		int stage = Math.min(150, width - panelW() - 32);
		if (c.menuAvatar && minecraft != null && minecraft.player != null && c.menuOffsetX == 0 && stage >= 86) {
			panelX = (width - (panelW() + 8 + stage)) / 2;
		}
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
		int online = dev.lmg.client.social.Social.enabled() ? dev.lmg.client.social.Social.onlineCount() : 0;
		String friends = online > 0 ? "Friends (" + online + ")" : "Friends";
		int fw = font.width(friends) + 14;
		int friendsX = nl ? backX - fw - 4 : panelX + panelW() - 62 - fw - 4;
		addRenderableWidget(new SkinButton(friendsX, panelY + 6, fw, 13, Component.literal(friends), openedAt,
				() -> minecraft.gui.setScreen(new FriendsScreen(this))));
		friendsRect = new int[]{friendsX, panelY + 6, friendsX + fw, panelY + 19};
		hudRect = new int[]{panelX + panelW() - 62, panelY + 6, panelX + panelW() - 6, panelY + 19};
		// the first time this menu opens: a quick tour
		if (!LmgConfig.get().tourDone && tourStep < 0 && !tourOffered) {
			tourOffered = true;
			tourStep = 0;
		}

		// Sidebar: the groups
		int sy = panelY + HEADER_H + 6;
		for (int i = 0; i < CATS.length; i++) {
			final int index = i;
			addRenderableWidget(new SideTab(panelX + 6, sy + i * TAB_GAP, SIDEBAR_W - 10, 13, CATS[i].name(),
					() -> lastCat == index, () -> showPage(index, lastPage[index])));
		}

		// the group's pages, along the top of the content
		Category cat = CATS[lastCat];
		int px = pagesX(), py = panelY + HEADER_H + (MenuSkin.style() == LmgConfig.MenuStyle.NEVERLOSE ? 5 : 6);
		for (int i = 0; i < cat.pages().length; i++) {
			final int index = i;
			String name = cat.pages()[i].name();
			int w = font.width(name) + 12;
			addRenderableWidget(new PageTab(px, py, w, 12, name, () -> lastPage[lastCat] == index, () -> showPage(lastCat, index)));
			px += w + 3;
		}

		// Settings for the selected page
		rows.clear();
		rows.addAll(rowsFor(page().name(), c));
		rowScroll = Math.max(0, Math.min(rowScroll, rows.size() - MAX_ROWS));
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

	// ---- the tour --------------------------------------------------------------------------------

	private record Step(String title, String text, Runnable before, java.util.function.Supplier<int[]> target) {}

	private int tourStep = -1;
	private static boolean tourOffered;
	private int[] friendsRect = new int[4], hudRect = new int[4];
	private final List<int[]> tourHits = new ArrayList<>();
	private long tourStepAt;

	void startTour() {
		tourStep = 0;
		tourStepAt = Util.getMillis();
		showPage(0, 0);
	}

	private List<Step> steps() {
		int cx0 = panelX + SIDEBAR_W, cx1 = panelX + panelW();
		int rowsTop = panelY + HEADER_H + 6 + MenuSkin.contentTopPad() - 2;
		List<Step> s = new ArrayList<>();
		s.add(new Step("Welcome to Lmg", "This is the Lmg menu. Right Shift opens and closes it, anywhere in game. Here's a quick look at where everything is.",
				() -> showPage(0, 0), null));
		s.add(new Step("Groups", "Everything is grouped on the left: Visuals, You, Combat, HUD, Social, Menu and Game.",
				null, () -> new int[]{panelX + 2, panelY + HEADER_H + 2, panelX + SIDEBAR_W - 2, panelY + HEADER_H + 8 + CATS.length * TAB_GAP}));
		s.add(new Step("Pages", "Each group has pages along the top. Visuals has Sky, Weather, Effects and Cinematic.",
				null, () -> new int[]{cx0 + 2, panelY + HEADER_H + 3, cx1 - 4, panelY + HEADER_H + 20}));
		s.add(new Step("Settings", "Click a setting to change it. Point at one and the line at the bottom says what it does. Colours open a picker: drag the dot and the slider.",
				null, () -> new int[]{cx0 + 2, rowsTop, cx1 - 4, rowsTop + 4 * ROW_GAP + 4}));
		s.add(new Step("New visuals", "Your own crosshair, damage numbers, motion blur and colour filters are in Visuals > Effects.",
				() -> showPage(0, 2), () -> new int[]{cx0 + 2, rowsTop, cx1 - 4, rowsTop + 5 * ROW_GAP + 4}));
		s.add(new Step("Friends", "See who's online, where they're playing, and join them. Key: O.",
				null, () -> friendsRect));
		s.add(new Step("Your UID", "Your Lmg number. The lower it is, the rarer: 1 to 10 are Founders. Grab yours here.",
				() -> showPage(4, 2), () -> new int[]{cx0 + 2, rowsTop, cx1 - 4, rowsTop + 2 * ROW_GAP + 4}));
		s.add(new Step("Edit HUD", "Move the bar, the minimap and the panels wherever you like.",
				null, () -> hudRect));
		s.add(new Step("Keys worth knowing", "G emote wheel, F8 photo mode, M world map, C zoom, F7 film mode, O friends. Change any of them in Game > Keys.",
				() -> showPage(6, 0), () -> new int[]{cx0 + 2, rowsTop, cx1 - 4, rowsTop + 4 * ROW_GAP + 4}));
		s.add(new Step("That's it!", "Have fun. You can take this tour again any time from Menu > Help.",
				() -> showPage(0, 0), null));
		return s;
	}

	private void tourNext(boolean skip) {
		List<Step> all = steps();
		if (skip || tourStep >= all.size() - 1) {
			tourStep = -1;
			LmgConfig.get().tourDone = true;
			LmgConfig.save();
			return;
		}
		tourStep++;
		tourStepAt = Util.getMillis();
		Step st = all.get(tourStep);
		if (st.before() != null) st.before().run();
	}

	/** The dimmed screen with a hole where the step is, and a card that explains it. */
	private void drawTour(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		tourHits.clear();
		List<Step> all = steps();
		if (tourStep >= all.size()) tourStep = all.size() - 1;
		Step st = all.get(tourStep);
		float a = Draw.easeOutCubic(Draw.clamp01((Util.getMillis() - tourStepAt) / 220f));
		int dim = Draw.withAlpha(0x05070D, 0.72f);
		int[] r = st.target() == null ? null : st.target().get();
		if (r == null) {
			g.fill(0, 0, width, height, dim);
		} else {
			int pad = 3;
			int x0 = r[0] - pad, y0 = r[1] - pad, x1 = r[2] + pad, y1 = r[3] + pad;
			g.fill(0, 0, width, y0, dim);
			g.fill(0, y1, width, height, dim);
			g.fill(0, y0, x0, y1, dim);
			g.fill(x1, y0, width, y1, dim);
			// a soft pulsing outline round the spot
			float pulse = 0.55f + 0.45f * (float) Math.sin(Util.getMillis() / 260.0);
			int col = Draw.withAlpha(dev.lmg.client.render.Theme.primary(), pulse * a);
			g.fill(x0, y0, x1, y0 + 1, col);
			g.fill(x0, y1 - 1, x1, y1, col);
			g.fill(x0, y0, x0 + 1, y1, col);
			g.fill(x1 - 1, y0, x1, y1, col);
		}

		// beside the spot if there's room (a bit narrower if needed), else under / over it, else in the middle
		int roomRight = r == null ? 0 : width - r[2] - 16, roomLeft = r == null ? 0 : r[0] - 16;
		int cw = r != null && Math.max(roomRight, roomLeft) >= 104 ? Math.min(170, Math.max(roomRight, roomLeft)) : 170;
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(st.text()), cw - 16);
		int ch = 30 + lines.size() * 10 + 22;
		int cx, cy;
		if (r == null) {
			cx = width / 2 - cw / 2;
			cy = height / 2 - ch / 2;
		} else if (roomRight >= cw) {
			cx = r[2] + 10;
			cy = Math.max(6, Math.min(height - ch - 6, (r[1] + r[3]) / 2 - ch / 2));
		} else if (roomLeft >= cw) {
			cx = r[0] - 10 - cw;
			cy = Math.max(6, Math.min(height - ch - 6, (r[1] + r[3]) / 2 - ch / 2));
		} else if (r[3] + 8 + ch < height) {
			cx = Math.max(6, Math.min(width - cw - 6, (r[0] + r[2]) / 2 - cw / 2));
			cy = r[3] + 8;
		} else if (r[1] - 8 - ch > 0) {
			cx = Math.max(6, Math.min(width - cw - 6, (r[0] + r[2]) / 2 - cw / 2));
			cy = r[1] - 8 - ch;
		} else {
			cx = width / 2 - cw / 2;
			cy = height / 2 - ch / 2;
		}
		cy += Math.round((1 - a) * 6);
		Draw.roundRect(g, cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, 7, Draw.withAlpha(0x2A3B5E, a));
		Draw.roundRect(g, cx, cy, cx + cw, cy + ch, 6, Draw.withAlpha(0x0C1322, 0.98f * a));
		g.text(font, st.title(), cx + 8, cy + 8, Draw.white(a), false);
		String count = (tourStep + 1) + " / " + all.size();
		g.text(font, count, cx + cw - 8 - font.width(count), cy + 8, Draw.withAlpha(0x7C8BA3, a), false);
		int ty = cy + 22;
		for (var line : lines) {
			g.text(font, line, cx + 8, ty, Draw.withAlpha(0xC9D3E3, a), false);
			ty += 10;
		}
		boolean last = tourStep == all.size() - 1;
		String next = last ? "Done" : "Next";
		int bw = font.width(next) + 16, bx = cx + cw - 8 - bw, by = cy + ch - 20;
		boolean overNext = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 14;
		PillButton.draw(g, font, bx, by, bw, 14, next, true, overNext ? 1f : 0f, a);
		tourHits.add(new int[]{bx, by, bx + bw, by + 14, 0});
		if (!last) {
			String skip = "Skip tour";
			int sx = cx + 8;
			boolean overSkip = mouseX >= sx && mouseX < sx + font.width(skip) && mouseY >= by && mouseY < by + 14;
			g.text(font, skip, sx, by + 3, Draw.withAlpha(overSkip ? 0xE8EDF5 : 0x7C8BA3, a), false);
			tourHits.add(new int[]{sx, by, sx + font.width(skip), by + 14, 1});
		}
	}

	private static Page page() {
		Page[] pages = CATS[lastCat].pages();
		return pages[Math.max(0, Math.min(lastPage[lastCat], pages.length - 1))];
	}

	/** Goes to a group and page (and remembers it for next time). */
	void showPage(int cat, int pageIndex) {
		if (cat == lastCat && pageIndex == lastPage[lastCat]) return;
		lastCat = cat;
		lastPage[cat] = pageIndex;
		rowScroll = 0;
		pageShownAt = Util.getMillis();
		confirmReset = false;
		rebuildWidgets();
	}

	/** Where the page tabs start (Neverlose shows the group's name first). */
	private int pagesX() {
		int contentX = panelX + SIDEBAR_W + 2 + MenuSkin.contentInset();
		return contentX + 4;
	}

	private void nextFilter(int dir) {
		LmgConfig c = LmgConfig.get();
		var all = dev.lmg.client.feature.PhotoMode.Filter.values();
		// "Off" sits where Natural is in the photo filters
		int i = c.worldFilter == null ? 0 : c.worldFilter.ordinal();
		i = Math.floorMod(i + dir, all.length);
		c.worldFilter = i == 0 ? null : all[i];
	}

	private void layoutSingle(List<SettingRow> rows, int sy, long t) {
		int inset = MenuSkin.contentInset();
		int rx = panelX + SIDEBAR_W + 2 + inset;
		int rw = panelW() - SIDEBAR_W - 8 - inset * 2;
		int ry = sy + MenuSkin.contentTopPad();
		int shown = Math.min(MAX_ROWS, rows.size() - rowScroll);
		for (int i = 0; i < shown; i++) {
			int idx = rowScroll + i;
			addRenderableWidget(rows.get(idx).at(rx, ry + i * ROW_GAP, rw - (rows.size() > MAX_ROWS ? 4 : 0), ROW_H, t + i * 25L).onOpen(row -> dropdown = row));
		}

	}

	/** Skeet: two titled group boxes side by side; rows are shared out so both columns are about even. */
	private void layoutSkeet(List<SettingRow> rows, long t) {
		int areaX0 = panelX + SIDEBAR_W + 6, areaX1 = panelX + panelW() - 6;
		int gap = 8;
		int colW = (areaX1 - areaX0 - gap) / 2;
		int boxTop = panelY + HEADER_H + 22;   // under the page tabs
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
		groups.add(new MenuSkin.Group(areaX0, boxTop, col1X1, boxBottom, page().name()));
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

	private List<SettingRow> rowsFor(String page, LmgConfig c) {
		List<SettingRow> r = new ArrayList<>();
		switch (page) {
			case "Sky" -> {
				r.add(enumRow("Sky theme", LmgConfig.SkyTheme.values(), () -> c.skyTheme, v -> c.skyTheme = v, v -> v.label)
						.withHint("Recolours the sky, just for you."));
				r.add(SettingRow.color("Sky colour", () -> c.skyRgb, null, () -> picker("Sky colour", c.skyRgb,
						v -> { c.skyRgb = v; c.skyTheme = LmgConfig.SkyTheme.CUSTOM; }, () -> c.skyRgb = 0x7FA8FF, null, null))
						.withHint("Picking one switches the sky to Your colours."));
				r.add(SettingRow.color("Horizon colour", () -> c.horizonRgb, null, () -> picker("Horizon colour", c.horizonRgb,
						v -> { c.horizonRgb = v; c.skyTheme = LmgConfig.SkyTheme.CUSTOM; }, () -> c.horizonRgb = 0xFFC7A0, null, null))
						.withHint("The edge of the sky, the fog and the sunset."));
				r.add(enumRow("Time of day", LmgConfig.TimeOfDay.values(), () -> c.timeOfDay, v -> c.timeOfDay = v, v -> v.label)
						.withHint("Lock it to sunset, if you like."));
				r.add(enumRow("Fog", LmgConfig.FogLevel.values(), () -> c.fog, v -> c.fog = v, v -> v.label)
						.withHint("Less fog = you see further."));
				r.add(enumRow("Clouds", LmgConfig.CloudMode.values(), () -> c.clouds, v -> c.clouds = v, v -> v.label));
				r.add(enumRow("Aurora", LmgConfig.Aurora.values(), () -> c.aurora, v -> c.aurora = v, v -> v.label)
						.withHint("Northern lights at night."));
				r.add(SettingRow.color("Aurora colour", () -> c.auroraRgb, null, () -> picker("Aurora colour", c.auroraRgb,
						v -> { c.auroraRgb = v; c.aurora = LmgConfig.Aurora.CUSTOM; }, () -> c.auroraRgb = 0x3CFFA0, null, null))
						.withHint("Picking one switches the aurora to Your colour."));
				r.add(SettingRow.toggle("Always show stars", () -> c.alwaysStars, v -> c.alwaysStars = v)
						.withHint("Stars stay out during the day too."));
			}
			case "Weather" -> {
				r.add(SettingRow.toggle("Rain & snow", () -> c.showWeather, v -> c.showWeather = v)
						.withHint("Off hides falling rain and snow."));
				r.add(SettingRow.choice("Winter", () -> {
							var m = c.winter == null ? dev.lmg.client.world.Winter.Mode.SEASONAL : c.winter;
							return m == dev.lmg.client.world.Winter.Mode.SEASONAL ? "Seasonal" + (dev.lmg.client.world.Winter.snowing(minecraft) || minecraft.level == null ? "" : " (not yet)") : m.label;
						},
						() -> c.winter = cycle(dev.lmg.client.world.Winter.Mode.values(), c.winter == null ? dev.lmg.client.world.Winter.Mode.SEASONAL : c.winter, 1),
						() -> c.winter = cycle(dev.lmg.client.world.Winter.Mode.values(), c.winter == null ? dev.lmg.client.world.Winter.Mode.SEASONAL : c.winter, -1))
						.withHint("Snow falls everywhere - only on your screen."));
				r.add(SettingRow.toggle("Snow settles", () -> c.winterSettles, v -> c.winterSettles = v)
						.withHint("A layer of snow builds up around you."));
			}
			case "Effects" -> {
				r.add(enumRow("Crosshair", LmgConfig.Crosshair.values(), () -> c.crosshair, v -> c.crosshair = v, v -> v.label)
						.withHint("Your own crosshair shape."));
				r.add(SettingRow.color("Crosshair colour", () -> c.crosshairRgb, null, () -> picker("Crosshair colour", c.crosshairRgb,
						v -> { c.crosshairRgb = v; if (c.crosshair == LmgConfig.Crosshair.VANILLA) c.crosshair = LmgConfig.Crosshair.CROSS_DOT; },
						() -> c.crosshairRgb = 0xFFFFFF, null, null)).withHint("Any colour. Vanilla's can't be recoloured."));
				r.add(SettingRow.choice("Crosshair size", () -> LmgConfig.CROSSHAIR_SIZE_LABELS[c.crosshairSize],
						() -> c.crosshairSize = step(c.crosshairSize, LmgConfig.CROSSHAIR_SIZES.length, 1),
						() -> c.crosshairSize = step(c.crosshairSize, LmgConfig.CROSSHAIR_SIZES.length, -1)));
				r.add(SettingRow.toggle("Crosshair shows reach", () -> c.crosshairTargetColor, v -> c.crosshairTargetColor = v)
						.withHint("Turns your in-reach colour when you can hit."));
				r.add(SettingRow.toggle("Damage numbers", () -> c.damageNumbers, v -> c.damageNumbers = v)
						.withHint("How much each of your hits did, floating up."));
				r.add(SettingRow.color("Damage colour", () -> c.damageRgb, null, () -> picker("Damage colour", c.damageRgb,
						v -> c.damageRgb = v, () -> c.damageRgb = 0xFF5A6E, null, null)));
				r.add(SettingRow.color("Hurt flash colour", () -> c.hurtRgb, null, () -> picker("Hurt flash colour", c.hurtRgb,
						v -> { c.hurtRgb = v; dev.lmg.client.feature.Visuals.applyHurtColor(); },
						() -> { c.hurtRgb = 0xFF0000; dev.lmg.client.feature.Visuals.applyHurtColor(); }, null, null))
						.withHint("The flash when anything gets hit (red normally)."));
				r.add(SettingRow.choice("Motion blur", () -> LmgConfig.MOTION_BLUR_LABELS[c.motionBlur],
						() -> c.motionBlur = step(c.motionBlur, LmgConfig.MOTION_BLUR_LABELS.length, 1),
						() -> c.motionBlur = step(c.motionBlur, LmgConfig.MOTION_BLUR_LABELS.length, -1))
						.withHint("Smears fast movement. Feels smoother."));
				r.add(SettingRow.choice("Colour filter", () -> c.worldFilter == null ? "Off" : c.worldFilter.label, () -> nextFilter(1), () -> nextFilter(-1),
						() -> c.worldFilter == null ? 0x3A3A44 : c.worldFilter.swatch)
						.withHint("A colour grade over the whole world."));
				r.add(SettingRow.toggle("My name tag (F5)", () -> c.ownNameTag, v -> c.ownNameTag = v)
						.withHint("See your own name over your head (F5)."));
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
				r.add(SettingRow.button("Photo mode", "Open", () -> {
					if (minecraft.player != null) minecraft.gui.setScreen(new PhotoModeScreen());
					else toast("Join a world first", "Then press F8.");
				}).withHint("Freeze, frame it, save it. Key: F8"));
			}
			case "Outfit" -> {
				r.add(SettingRow.button("Cosmetics & skins", "Open", () -> minecraft.gui.setScreen(new CosmeticsScreen(this)))
						.withHint("Capes, wings, hats and your own skins."));
				r.add(SettingRow.button("Pets", "Open", () -> minecraft.gui.setScreen(new PetsScreen(this)))
						.withHint("Animals or a mini player that follow you."));
				r.add(SettingRow.toggle("You next to this menu", () -> c.menuAvatar, v -> c.menuAvatar = v)
						.withHint("You and your pet, beside the menu. Drag to spin."));
				r.add(SettingRow.toggle("Hide cosmetics", () -> c.hideCosmetics, v -> c.hideCosmetics = v)
						.withHint("Your plain skin, nothing else."));
			}
			case "Trails" -> {
				r.add(enumRow("Trail", LmgConfig.Trail.values(), () -> c.trail, v -> c.trail = v, v -> v.label)
						.withHint("Particles behind you as you walk."));
				r.add(SettingRow.color("Trail colour", c::trailColor, null, () -> picker("Trail colour", c.trailColor(),
						v -> { c.trailRgb = v; if (c.trail == LmgConfig.Trail.OFF) c.trail = LmgConfig.Trail.ACCENT_DUST; }, () -> c.trailRgb = -1, null, null))
						.withHint("For the Coloured dust trail. Default: accent."));
				r.add(enumRow("Ambient particles", LmgConfig.Ambient.values(), () -> c.ambient, v -> c.ambient = v, v -> v.label)
						.withHint("Things floating around you."));
				r.add(SettingRow.choice("Ambient amount", () -> LmgConfig.AMOUNT_LABELS[c.ambientAmount],
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, 1),
						() -> c.ambientAmount = step(c.ambientAmount, LmgConfig.AMOUNT_LABELS.length, -1)));
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
			case "Sharing" -> {
				r.add(SettingRow.toggle("Share pets & cosmetics", () -> c.shareAvatar, v -> c.shareAvatar = v)
						.withHint("Other Lmg players see them. Off by default."));
				r.add(SettingRow.toggle("See others' pets & cosmetics", () -> c.showOthers, v -> {
					c.showOthers = v;
					if (!v) dev.lmg.client.sync.AvatarSync.clearRemote();
				}).withHint("From Lmg players near you who share theirs."));
				r.add(SettingRow.toggle("Show their pet names", () -> c.showOtherPetNames, v -> c.showOtherPetNames = v)
						.withHint("Turn off to hide names other people chose."));
				r.add(SettingRow.choice("Others' pets & cosmetics", () -> "within " + LmgConfig.OTHERS_RANGES[c.othersRange],
						() -> c.othersRange = step(c.othersRange, LmgConfig.OTHERS_RANGES.length, 1),
						() -> c.othersRange = step(c.othersRange, LmgConfig.OTHERS_RANGES.length, -1))
						.withHint("Further players are drawn plain."));
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
			}
			case "Hits" -> {
				r.add(enumRow("Hit effect", LmgConfig.HitEffect.values(), () -> c.hitEffect, v -> c.hitEffect = v, v -> v.label)
						.withHint("What you see when you land a hit."));
				r.add(SettingRow.color("Hit colour", c::hitColor, null, () -> picker("Hit colour", c.hitColor(),
						v -> c.hitRgb = v, () -> c.hitRgb = -1, null, null))
						.withHint("For the Coloured burst. Default: accent."));
				r.add(enumRow("Kill effect", LmgConfig.KillEffect.values(), () -> c.killEffect, v -> c.killEffect = v, v -> v.label));
			}
			case "Clarity" -> {
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
			case "Bar" -> {
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
				r.add(SettingRow.toggle("Way back after dying", () -> c.deathTrail, v -> c.deathTrail = v)
						.withHint("A trail, a beam and a compass to where you died."));
			}
			case "Friends" -> {
				r.add(SettingRow.button("Friends list", "Open", () -> minecraft.gui.setScreen(new FriendsScreen(this)))
						.withHint("Who's online, and join them. Key: O"));
				r.add(SettingRow.toggle("Show my server", () -> c.friendsShowServer, v -> c.friendsShowServer = v)
						.withHint("Friends see (and can join) where you play."));
				r.add(SettingRow.toggle("Friend pop-ups", () -> c.friendsToasts, v -> c.friendsToasts = v)
						.withHint("\"Mia is online\" in the corner."));
				r.add(SettingRow.toggle("Appear offline", () -> c.friendsAppearOffline, v -> c.friendsAppearOffline = v)
						.withHint("Nobody sees you online."));
				r.add(SettingRow.toggle("Partner hearts", () -> c.partnerHearts, v -> c.partnerHearts = v)
						.withHint("A heart on their name, hearts between you."));
				r.add(SettingRow.toggle("Match my partner's outfit", () -> c.matchPartner, v -> {
					c.matchPartner = v;
					if (v) toast("Matching", dev.lmg.client.social.Partner.matchOutfit(true));
				}).withHint("Their items, in your colours."));
			}
			case "Emotes" -> {
				r.add(SettingRow.button("Emote wheel", "Open", () -> {
					if (minecraft.player != null) minecraft.gui.setScreen(new EmoteWheelScreen());
					else toast("Join a world first", "Then hold G.");
				}).withHint("Hold G in game, point, let go."));
				r.add(SettingRow.toggle("Watch myself emote", () -> c.emoteCamera, v -> c.emoteCamera = v)
						.withHint("Turns the camera round while you emote."));
			}
			case "UID" -> {
				r.add(SettingRow.button("Your UID", () -> dev.lmg.client.social.Uids.myLabel(), () -> dev.lmg.client.social.Uids.claim(msg -> toast("UID", msg)))
						.withHint("Your Lmg number. Lower = earlier = rarer."));
				r.add(SettingRow.toggle("UIDs on name tags", () -> c.uidOnNameTags, v -> c.uidOnNameTags = v)
						.withHint("Other Lmg players' numbers next to their names."));
			}
			case "Look" -> {
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
				r.add(SettingRow.choice("Menu weather", () -> c.weather() == MenuSnow.Weather.AUTO
								? "Seasonal (" + MenuSnow.Weather.AUTO.resolved().label.toLowerCase(java.util.Locale.ROOT) + ")" : c.weather().label,
						() -> c.menuWeather = cycle(MenuSnow.Weather.values(), c.weather(), 1),
						() -> c.menuWeather = cycle(MenuSnow.Weather.values(), c.weather(), -1))
						.withHint("Snow, leaves, petals or fireflies in the menus."));
				r.add(SettingRow.choice("Animation speed", () -> LmgConfig.ANIMATION_SPEED_LABELS[c.animationSpeed],
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, 1),
						() -> c.animationSpeed = step(c.animationSpeed, LmgConfig.ANIMATION_SPEEDS.length, -1))
						.withHint("How fast menus slide and fade."));
			}
			case "Main menu" -> {
				r.add(SettingRow.choice("Menu background", () -> c.backdrop.label,
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, 1),
						() -> c.backdrop = cycle(LmgConfig.Backdrop.values(), c.backdrop, -1), () -> c.backdrop.top)
						.withHint("Behind the main menu."));
				r.add(SettingRow.color("Background colour", () -> c.menuBgRgb < 0 ? c.backdrop.top : c.menuBgRgb, null,
						() -> picker("Menu background", c.menuBgRgb < 0 ? 0x2A3A66 : c.menuBgRgb, v -> c.menuBgRgb = v, () -> c.menuBgRgb = -1, null, null))
						.withHint("Any colour behind the main menu. Default: the preset."));
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
			case "Help" -> {
				r.add(SettingRow.button("Show me around", "Start", () -> startTour())
						.withHint("The quick tour of this menu again."));
				r.add(SettingRow.button("About & safety", "Open", () -> minecraft.gui.setScreen(new WelcomeScreen(this, false)))
						.withHint("What this mod does, and what it sends."));
				r.add(SettingRow.button("Mods", "Open", () -> minecraft.gui.setScreen(new ModsScreen(this)))
						.withHint("See your mods, switch them on or off."));
			}
			case "Keys" -> {
				for (dev.lmg.client.feature.LmgKeys.Bind b : dev.lmg.client.feature.LmgKeys.ALL) {
					r.add(SettingRow.key(b.label(), () -> keyName(b.key()), () -> waitingKey == b.key(), () -> {
						waitingKey = waitingKey == b.key() ? null : b.key();
					}).withHint(waitingKey == b.key() ? "Press a key. Esc clears it." : b.hint()));
				}
			}
			case "Tools" -> {
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
			default -> {
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

		// (Neverlose would print the group's name where the page tabs go; the sidebar already shows it)
		MenuSkin.panel(g, font, x0, y0, x1, y1, HEADER_H, SIDEBAR_W, FOOTER_H,
				MenuSkin.style() == LmgConfig.MenuStyle.NEVERLOSE ? "" : CATS[lastCat].name(), intro);
		MenuSnow.panelTop(x0, y0, x1);

		LmgConfig.MenuStyle style = MenuSkin.style();
		int[] tc = MenuSkin.textColors();
		int faint = style == LmgConfig.MenuStyle.LMG ? 0x7C8BA3 : tc[1];

		// a scrollbar when a page has more rows than fit (Keys)
		if (rows.size() > MAX_ROWS && style != LmgConfig.MenuStyle.SKEET) {
			int top = y0 + HEADER_H + 6 + MenuSkin.contentTopPad(), h = MAX_ROWS * ROW_GAP;
			int barH = Math.max(14, h * MAX_ROWS / rows.size());
			int by = top + (h - barH) * rowScroll / Math.max(1, rows.size() - MAX_ROWS);
			Draw.roundRect(g, x1 - 6, by, x1 - 4, by + barH, 1, Draw.withAlpha(dev.lmg.client.render.Theme.primary(), 0.6f * intro));
		}

		// footer: what the setting under the mouse does, or a small tip
		String hint = null;
		if (waitingKey != null) hint = "Press the new key. Esc clears it.";
		else for (SettingRow r : rows) if (r.hovered() && !r.hint().isEmpty()) hint = r.hint();
		boolean isTip = hint == null;
		// just after switching page: what the page is for; then little tips
		if (isTip) hint = Util.getMillis() - pageShownAt < 5000 ? page().name() + ": " + page().about()
				: "tip: " + TIPS[(int) ((Util.getMillis() / 7000) % TIPS.length)];
		int hx0 = x0 + SIDEBAR_W + 6, hx1 = x1 - 10;
		if (font.width(hint) > hx1 - hx0) hint = font.plainSubstrByWidth(hint, hx1 - hx0 - 8) + "..";
		int hintColor = isTip ? (style == LmgConfig.MenuStyle.LMG ? 0x55637A : (style == LmgConfig.MenuStyle.SKEET ? 0x4A4A4A : 0x3E4766))
				: (style == LmgConfig.MenuStyle.LMG ? 0xF2C14E : tc[0]);
		g.text(font, hint, hx0, y1 - FOOTER_H + 6, Draw.withAlpha(hintColor, intro), false);

		// you and your pet, next to the panel (if there's room)
		if (avatar.layout(width, height, x0, y0, x1, y1)) avatar.draw(g, font, mouseX, mouseY, intro);
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		MenuSnow.drawFront(g, 1f);
		if (dropdown != null) drawDropdown(g, mouseX, mouseY);
		// your UID, at the bottom of the sidebar
		int myUid = LmgConfig.get().myUid;
		if (myUid > 0) {
			String u = "UID #" + myUid;
			g.text(font, u, x0 + SIDEBAR_W / 2 - font.width(u) / 2, y1 - FOOTER_H - 12,
					Draw.withAlpha(dev.lmg.client.social.Uids.color(myUid), intro), false);
		}
		if (tourStep >= 0) drawTour(g, mouseX, mouseY);
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
		if (tourStep >= 0) {
			for (int[] h : List.copyOf(tourHits)) {
				if (event.x() >= h[0] && event.x() < h[2] && event.y() >= h[1] && event.y() < h[3]) tourNext(h[4] == 1);
			}
			return true;   // the menu waits while the tour is up
		}
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
		if (event.button() == 0 && avatar.mouseClicked(event.x(), event.y(), () -> minecraft.gui.setScreen(new CosmeticsScreen(this)))) return true;
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
		if (avatar.mouseDragged(dx)) return true;
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
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// long pages (Keys) scroll
		if (rows.size() > MAX_ROWS && mouseX > panelX + SIDEBAR_W && mouseX < panelX + panelW() && mouseY > panelY && mouseY < panelY + panelH()) {
			int next = Math.max(0, Math.min(rows.size() - MAX_ROWS, rowScroll - (int) Math.signum(scrollY)));
			if (next != rowScroll) {
				rowScroll = next;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (avatar.mouseReleased()) return true;
		if (dragging) {
			dragging = false;
			LmgConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (tourStep >= 0) {
			int k = event.key();
			if (event.isEscape()) tourNext(true);
			else if (k == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || k == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE
					|| k == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) tourNext(false);
			return true;
		}
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
