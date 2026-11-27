package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.UpdateChecker;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.MenuSnow;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * The tile home Right Shift opens first (Menu > Look > Menu home switches it off): four quick buttons, then a
 * 3x3 grid of big tiles - one per group, plus Search and Minecraft's own settings. A tile opens that group in
 * the sidebar menu, and Back there comes back here. Laid out like the Minecraft settings screen.
 */
public class LmgHomeScreen extends Screen implements dev.lmg.client.render.WorldFilter.GreyBehind {
	private static final int HEADER = 24;
	/** One icon per group, in the same order as the sidebar. */
	private static final Item[] GROUP_ICONS = {Items.SUNFLOWER, Items.LEATHER_CHESTPLATE, Items.DIAMOND_SWORD, Items.FILLED_MAP,
			Items.CAKE, Items.ITEM_FRAME, Items.COMPARATOR};

	private record Tile(String title, String hint, Item icon, Supplier<Screen> open) {}

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int W, H, x0, y0, tilesY, tileW;
	private Tile[] tiles;

	public LmgHomeScreen(Screen parent) {
		super(Component.literal(LmgClient.CLIENT_NAME));
		this.parent = parent;
	}

	/** Where Right Shift (or Back) goes from here. */
	public Screen parent() {
		return parent;
	}

	@Override
	protected void init() {
		W = Math.min(420, width - 16);
		H = Math.min(262, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;

		int groups = LmgSettingsScreen.groupCount();
		tiles = new Tile[groups + 2];
		for (int i = 0; i < groups; i++) {
			final int g = i;
			tiles[i] = new Tile(LmgSettingsScreen.groupName(i), LmgSettingsScreen.groupHint(i),
					i < GROUP_ICONS.length ? GROUP_ICONS[i] : Items.PAPER, () -> LmgSettingsScreen.at(this, g, -1));
		}
		tiles[groups] = new Tile("Search", "find any setting", Items.SPYGLASS, () -> LmgSettingsScreen.searching(this, ""));
		tiles[groups + 1] = new Tile("Minecraft settings", "video, sound, controls", Items.GRASS_BLOCK, () -> new MinecraftSettingsScreen(this));

		addRenderableWidget(new SkinButton(x0 + 6, y0 + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(x0 + W - 84, y0 + 6, 78, 13, Component.literal("Sidebar menu"), openedAt,
				() -> minecraft.gui.setScreen(new LmgSettingsScreen(this))));

		// the things people open the most, as four wide buttons (like the sliders on the Minecraft settings screen)
		int colW = (W - 30) / 2, by = y0 + HEADER + 24;
		int online = dev.lmg.client.social.Social.enabled() ? dev.lmg.client.social.Social.onlineCount() : 0;
		addRenderableWidget(new PillButton(x0 + 10, by, colW, 18, Component.literal("Cosmetics"), false, openedAt,
				() -> minecraft.gui.setScreen(new CosmeticsScreen(this))));
		addRenderableWidget(new PillButton(x0 + 20 + colW, by, colW, 18, Component.literal("Pets"), false, openedAt,
				() -> minecraft.gui.setScreen(new PetsScreen(this))));
		addRenderableWidget(new PillButton(x0 + 10, by + 24, colW, 18, Component.literal(online > 0 ? "Friends (" + online + " on)" : "Friends"), false, openedAt,
				() -> minecraft.gui.setScreen(new FriendsScreen(this))));
		addRenderableWidget(new PillButton(x0 + 20 + colW, by + 24, colW, 18, Component.literal("Edit HUD"), false, openedAt,
				() -> minecraft.gui.setScreen(new HudEditorScreen(this))));

		addRenderableWidget(new SkinButton(x0 + W - 58, y0 + H - 20, 52, 13, Component.literal("Help"), openedAt,
				() -> minecraft.gui.setScreen(LmgSettingsScreen.at(this, LmgSettingsScreen.groupCount() - 2, 2))));   // Menu > Help

		tilesY = by + 52;
		tileW = (W - 20 - 12) / 3;
	}

	private int[] tileRect(int i) {
		int col = i % 3, row = i / 3;
		int tx = x0 + 10 + col * (tileW + 6), ty = tilesY + row * 38;
		return new int[]{tx, ty, tx + tileW, ty + 34};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick);
			MenuSnow.greyBackdrop(g, width, height);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
		MenuSnow.update(width, height);
		MenuSnow.drawBack(g, 1f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 300f));
		CosmeticsScreen.Pal p = CosmeticsScreen.pal();
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "", a);
		MenuSnow.panelTop(x0, y0, x0 + W);
		// title: "Lmg Client v0.35.6", the version in the accent colour like the sidebar menu
		String name = LmgClient.CLIENT_NAME + " ", ver = "v" + UpdateChecker.currentVersion();
		int tx = x0 + W / 2 - (font.width(name) + font.width(ver)) / 2;
		g.text(font, name, tx, y0 + 8, Draw.withAlpha(p.text(), a), false);
		g.text(font, ver, tx + font.width(name), y0 + 8, Draw.withAlpha(0xF2C14E, a), false);
		g.text(font, "Pick a group, or just start typing to search.", x0 + 10, y0 + HEADER + 9, Draw.withAlpha(p.dim(), a), false);

		for (int i = 0; i < tiles.length; i++) {
			int[] r = tileRect(i);
			boolean over = mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
			Draw.roundRect(g, r[0], r[1], r[2], r[3], 5, over ? Draw.withAlpha(p.accent(), a) : Draw.fade(p.border(), a));
			Draw.roundRect(g, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 4, Draw.fade(over ? p.cardHover() : p.card(), a));
			g.item(new ItemStack(tiles[i].icon()), r[0] + 7, r[1] + 9);
			String title = tiles[i].title();
			if (font.width(title) > tileW - 36) title = font.plainSubstrByWidth(title, tileW - 42) + "..";
			g.text(font, title, r[0] + 30, r[1] + 7, Draw.withAlpha(p.text(), a), false);
			String hint = tiles[i].hint();
			if (font.width(hint) > tileW - 36) hint = font.plainSubstrByWidth(hint, tileW - 42) + "..";
			g.text(font, hint, r[0] + 30, r[1] + 19, Draw.withAlpha(p.dim(), a), false);
		}
		String foot = "Prefer the old menu? Menu > Look > Menu home.";
		if (font.width(foot) > W - 20) foot = "Old menu: Menu > Look > Menu home.";
		g.text(font, foot, x0 + 10, y0 + H - 17, Draw.withAlpha(p.dim(), a), false);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		MenuSnow.drawFront(g, 1f);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
		for (int i = 0; i < tiles.length; i++) {
			int[] r = tileRect(i);
			if (event.x() >= r[0] && event.x() < r[2] && event.y() >= r[1] && event.y() < r[3]) {
				minecraft.gui.setScreen(tiles[i].open().get());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (LmgClient.OPEN_MENU_KEY.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	/** Typing a letter jumps straight into search, same as the sidebar menu. */
	@Override
	public boolean charTyped(CharacterEvent event) {
		if (Character.isLetterOrDigit(event.codepoint())) {
			minecraft.gui.setScreen(LmgSettingsScreen.searching(this, event.codepointAsString()));
			return true;
		}
		return super.charTyped(event);
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
