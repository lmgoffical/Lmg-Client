package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.UpdateChecker;
import dev.lmg.client.mixin.PlayerSkinWidgetAccessor;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Replaces the vanilla title screen: text-only menu on the left,
 * your 3D skin on the right, curtains and sparkles in the background.
 */
public class LmgTitleScreen extends Screen {
	private static final int BUTTON_W = 130;
	private static final int BUTTON_H = 14;
	private static final int BUTTON_GAP = 17;
	private static final float SKIN_SPIN_DEG_PER_SEC = 16f;

	private final boolean fadeFromBlack;
	private long openedAt;
	private long lastFrame = -1;
	private PlayerSkinWidget skin;
	private int columnX;
	private int columnTop;

	public LmgTitleScreen(boolean fadeFromBlack) {
		super(Component.literal(LmgClient.CLIENT_NAME));
		this.fadeFromBlack = fadeFromBlack;
		this.openedAt = Util.getMillis();
	}

	@Override
	protected void init() {
		columnX = Math.round(width * 0.34f);
		int columnHeight = 34 + 8 + 14 + 16 + BUTTON_GAP * 6 + 8;
		columnTop = height / 2 - columnHeight / 2;

		int y = columnTop + 34 + 8 + 14 + 16;
		long t = openedAt + (fadeFromBlack ? 250 : 0);
		int x = columnX - BUTTON_W / 2;

		addRenderableWidget(new MenuTextButton(x, y, BUTTON_W, BUTTON_H, Component.translatable("menu.singleplayer"), t,
				() -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
		y += BUTTON_GAP;
		addRenderableWidget(new MenuTextButton(x, y, BUTTON_W, BUTTON_H, Component.translatable("menu.multiplayer"), t + 60,
				() -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
		y += BUTTON_GAP + 8; // small gap between "play" and "settings" groups
		addRenderableWidget(new MenuTextButton(x, y, BUTTON_W, BUTTON_H, Component.literal("Minecraft Settings"), t + 120,
				() -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))));
		y += BUTTON_GAP;
		addRenderableWidget(new MenuTextButton(x, y, BUTTON_W, BUTTON_H, Component.literal("Client"), t + 180,
				() -> minecraft.gui.setScreen(new LmgSettingsScreen(this))));
		y += BUTTON_GAP;
		addRenderableWidget(new MenuTextButton(x, y, BUTTON_W, BUTTON_H, Component.literal("Cosmetics"), t + 210,
				() -> minecraft.gui.setScreen(new CosmeticsScreen(this))));
		y += BUTTON_GAP;
		addRenderableWidget(new MenuTextButton(x, y, BUTTON_W, BUTTON_H, Component.translatable("menu.quit"), t + 270,
				() -> minecraft.stop()));

		// 3D skin preview on the right (drag it to spin it yourself).
		int skinH = Math.min(150, height - 80);
		int skinW = Math.round(skinH * 0.6f);
		skin = new PlayerSkinWidget(skinW, skinH, minecraft.getEntityModels(),
				minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false));
		skin.setPosition(Math.round(width * 0.70f) - skinW / 2, height / 2 - skinH / 2 + 8);
		addRenderableWidget(skin);

		// The first time you open the game, say plainly what this is before anything else.
		if (!LmgConfig.get().welcomeSeen) {
			minecraft.execute(() -> {
				if (minecraft.gui.screen() == this) minecraft.gui.setScreen(new WelcomeScreen(this, true));
			});
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		MenuBackground.draw(g, width, height, true);
	}

	/** "evening, Steve." - changes with your clock, so the menu feels a bit less like a machine. */
	private String greeting() {
		int hour = java.time.LocalTime.now().getHour();
		String hi = hour < 5 ? "up late" : hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening";
		return hi + ", " + minecraft.getGameProfile().name() + (hour < 5 ? "?" : ".");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float time = now / 1000f * Theme.speed();
		float intro = Draw.easeOutCubic(Theme.progress(openedAt, 700f));

		// Slowly turn the skin unless the mouse is over it.
		if (skin != null && LmgConfig.get().skinSpin && !skin.isHovered()) {
			PlayerSkinWidgetAccessor acc = (PlayerSkinWidgetAccessor) skin;
			acc.lmg$setRotationY(acc.lmg$getRotationY() + SKIN_SPIN_DEG_PER_SEC * Theme.speed() * dt);
		}

		// Logo + name at the top of the menu column.
		float bob = (float) Math.sin(time * 1.4f) * 1.5f;
		float glow = 0.85f + 0.15f * (float) Math.sin(time * 2.2f);
		Draw.logo(g, columnX, columnTop + 17 + bob, 34f, (float) Math.sin(time * 0.8f) * 0.05f, intro * glow);
		Draw.scaledText(g, font, LmgClient.CLIENT_NAME, columnX, columnTop + 34 + 8, 1.5f, Draw.white(intro));
		g.centeredText(font, greeting(), columnX, columnTop + 34 + 8 + 16, Draw.withAlpha(0x7C8BA3, intro));

		// Name tag above the skin.
		if (skin != null) {
			String name = minecraft.getGameProfile().name();
			int nx = skin.getX() + skin.getWidth() / 2;
			int ny = skin.getY() - 16;
			int w = font.width(name) + 10;
			Draw.roundRect(g, nx - w / 2, ny - 3, nx + w / 2, ny + font.lineHeight + 2, 4, Draw.withAlpha(0x000000, 0.35f * intro));
			g.centeredText(font, name, nx, ny, Draw.white(intro));
		}

		super.extractRenderState(g, mouseX, mouseY, partialTick);

		// Version in the corner, plus a notice when an update is out.
		String version = LmgClient.CLIENT_NAME + " " + UpdateChecker.currentVersion() + "  -  not made by Mojang or Microsoft";
		g.text(font, version, width - font.width(version) - 6, height - 12, Draw.withAlpha(0xF2C14E, 0.7f * intro), false);
		String update = UpdateChecker.newerVersion();
		if (update != null) {
			String msg = "Update " + update + " is out! Run Install-Lmg-Client.bat to get it.";
			float pulse = 0.75f + 0.25f * (float) Math.sin(time * 2.5f);
			g.text(font, msg, 6, height - 12, Draw.withAlpha(Theme.accentSoft(), intro * pulse), false);
		}

		if (fadeFromBlack) {
			float black = 1f - Draw.easeOutCubic(Theme.progress(openedAt, 600f));
			if (black > 0.005f) {
				g.nextStratum();
				g.fill(0, 0, width, height, Draw.withAlpha(0x000000, black));
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (LmgClient.OPEN_MENU_KEY.matches(event)) {
			minecraft.gui.setScreen(new LmgSettingsScreen(this));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
