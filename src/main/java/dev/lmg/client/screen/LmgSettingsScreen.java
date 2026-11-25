package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** The "Client" menu: on/off toggles for every Lmg Client feature. */
public class LmgSettingsScreen extends Screen {
	private static final int BUTTON_W = 170;
	private static final int BUTTON_H = 14;
	private static final int GAP = 17;

	private final Screen parent;
	private final long openedAt = Util.getMillis();

	public LmgSettingsScreen(Screen parent) {
		super(Component.literal(LmgClient.CLIENT_NAME + " Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LmgConfig c = LmgConfig.get();
		int x = width / 2 - BUTTON_W / 2;
		int y = height / 2 - (GAP * 9) / 2 + 10;
		long t = openedAt;

		y = toggle(x, y, t, "Splash screen", () -> c.showSplash, v -> c.showSplash = v);
		y = toggle(x, y, t += 40, "Themed menus", () -> c.themedMenus, v -> c.themedMenus = v);
		y = toggle(x, y, t += 40, "Menu sparkles & curtains", () -> c.menuEffects, v -> c.menuEffects = v);
		y = toggle(x, y, t += 40, "HUD bar", () -> c.hudEnabled, v -> c.hudEnabled = v);
		y = toggle(x, y, t += 40, "HUD: server", () -> c.hudShowServer, v -> c.hudShowServer = v);
		y = toggle(x, y, t += 40, "HUD: FPS", () -> c.hudShowFps, v -> c.hudShowFps = v);
		y = toggle(x, y, t += 40, "HUD: clock", () -> c.hudShowClock, v -> c.hudShowClock = v);

		addRenderableWidget(new MenuTextButton(x, y + 8, BUTTON_W, BUTTON_H, Component.translatable("gui.done"), t + 40, this::onClose));
	}

	private int toggle(int x, int y, long appearAt, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		MenuTextButton[] self = new MenuTextButton[1];
		self[0] = new MenuTextButton(x, y, BUTTON_W, BUTTON_H, label(label, get.get()), appearAt, () -> {
			set.accept(!get.get());
			LmgConfig.save();
			self[0].setMessage(label(label, get.get()));
		});
		addRenderableWidget(self[0]);
		return y + GAP;
	}

	private static Component label(String name, boolean on) {
		return Component.literal(name + ":  " + (on ? "§fOn" : "§8Off"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		MenuBackground.draw(g, width, height, true);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float intro = Draw.easeOutCubic((Util.getMillis() - openedAt) / 500f);
		int titleY = height / 2 - (GAP * 9) / 2 - 18;
		Draw.logo(g, width / 2f - font.width(title) * 0.75f - 14, titleY + 5, 16f, 0, intro);
		Draw.scaledText(g, font, title.getString(), width / 2f, titleY, 1.5f, Draw.white(intro));
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		LmgConfig.save();
		minecraft.gui.setScreen(parent);
	}
}
