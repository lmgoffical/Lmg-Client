package dev.lmg.client.screen;

import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuSnow;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Minecraft's own settings, laid out the Lmg way: the four sliders people change most at the
 * top, then a card for each settings page. The pages themselves are vanilla (restyled).
 * "Classic options" opens the original screen (it has a few extras, like world difficulty).
 */
public class MinecraftSettingsScreen extends Screen {
	/** Set for a moment while opening the original Options screen, so it isn't swapped out. */
	public static boolean classic;

	private static final int HEADER = 24;

	private record Card(String title, String hint, Item icon, Supplier<Screen> open) {}

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int W, H, x0, y0, cardsY, cardW;
	private Card[] cards;

	public MinecraftSettingsScreen(Screen parent) {
		super(Component.literal("Minecraft settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		var options = minecraft.options;
		W = Math.min(420, width - 16);
		H = Math.min(262, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;

		cards = new Card[]{
				new Card("Video", "graphics, FPS, GUI size", Items.PAINTING, () -> new VideoSettingsScreen(this, minecraft, options)),
				new Card("Music & sounds", "every volume slider", Items.NOTE_BLOCK, () -> new SoundOptionsScreen(this, options)),
				new Card("Controls", "keys, mouse, sensitivity", Items.LEVER, () -> new ControlsScreen(this, options)),
				new Card("Skin", "cape and skin layers", Items.LEATHER_CHESTPLATE, () -> new SkinCustomizationScreen(this, options)),
				new Card("Resource packs", "texture packs", Items.BOOKSHELF, () -> new PackSelectionScreen(minecraft.getResourcePackRepository(),
						repo -> { options.updateResourcePacks(repo); minecraft.gui.setScreen(this); },
						minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title"))),
				new Card("Chat", "size, colours, filters", Items.OAK_SIGN, () -> new ChatOptionsScreen(this, options)),
				new Card("Language", "the game's language", Items.BOOK, () -> new LanguageSelectScreen(this, options, minecraft.getLanguageManager())),
				new Card("Accessibility", "subtitles, motion, text", Items.ENDER_EYE, () -> new AccessibilityOptionsScreen(this, options)),
				new Card("Online", "realms and server options", Items.COMPASS, () -> new OnlineOptionsScreen(this, options)),
		};

		addRenderableWidget(new SkinButton(x0 + 6, y0 + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(x0 + W - 98, y0 + 6, 92, 13, Component.literal("Classic options"), openedAt, () -> {
			classic = true;
			try {
				minecraft.gui.setScreen(new OptionsScreen(this, options));
			} finally {
				classic = false;
			}
		}));

		// the sliders people change the most (vanilla widgets, drawn in the Lmg style)
		int colW = (W - 30) / 2;
		int sy = y0 + HEADER + 24;
		addRenderableWidget(options.fov().createButton(options, x0 + 10, sy, colW));
		addRenderableWidget(options.renderDistance().createButton(options, x0 + 20 + colW, sy, colW));
		addRenderableWidget(options.gamma().createButton(options, x0 + 10, sy + 24, colW));
		addRenderableWidget(options.getSoundSourceOptionInstance(SoundSource.MASTER).createButton(options, x0 + 20 + colW, sy + 24, colW));

		cardsY = sy + 56;
		cardW = (W - 20 - 12) / 3;
		addRenderableWidget(new SkinButton(x0 + W - 70, y0 + H - 20, 64, 13, Component.literal("Credits"), openedAt,
				() -> minecraft.gui.setScreen(new CreditsAndAttributionScreen(this))));
	}

	private int[] cardRect(int i) {
		int col = i % 3, row = i / 3;
		int cx = x0 + 10 + col * (cardW + 6), cy = cardsY + row * 38;
		return new int[]{cx, cy, cx + cardW, cy + 34};
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
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "Minecraft settings", a);
		MenuSnow.panelTop(x0, y0, x0 + W);
		g.text(font, "The game's own settings. Changes save when you go back.", x0 + 10, y0 + HEADER + 9, Draw.withAlpha(p.dim(), a), false);

		for (int i = 0; i < cards.length; i++) {
			int[] r = cardRect(i);
			boolean over = mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
			Draw.roundRect(g, r[0], r[1], r[2], r[3], 5, over ? Draw.withAlpha(p.accent(), a) : Draw.fade(p.border(), a));
			Draw.roundRect(g, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 4, Draw.fade(over ? p.cardHover() : p.card(), a));
			g.item(new ItemStack(cards[i].icon()), r[0] + 7, r[1] + 9);
			g.text(font, cards[i].title(), r[0] + 30, r[1] + 7, Draw.withAlpha(p.text(), a), false);
			String hint = cards[i].hint();
			if (font.width(hint) > cardW - 36) hint = font.plainSubstrByWidth(hint, cardW - 42) + "..";
			g.text(font, hint, r[0] + 30, r[1] + 19, Draw.withAlpha(p.dim(), a), false);
		}
		g.text(font, "Classic options has world difficulty and a few extras.", x0 + 10, y0 + H - 17, Draw.withAlpha(p.dim(), a), false);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		MenuSnow.drawFront(g, 1f);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		for (int i = 0; i < cards.length; i++) {
			int[] r = cardRect(i);
			if (event.x() >= r[0] && event.x() < r[2] && event.y() >= r[1] && event.y() < r[3]) {
				minecraft.gui.setScreen(cards[i].open().get());
				return true;
			}
		}
		return false;
	}

	@Override
	public void removed() {
		minecraft.options.save();
	}

	@Override
	public void onClose() {
		minecraft.options.save();
		minecraft.gui.setScreen(parent);
	}
}
