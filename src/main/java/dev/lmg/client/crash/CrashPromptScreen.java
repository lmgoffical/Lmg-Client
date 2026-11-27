package dev.lmg.client.crash;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import dev.lmg.client.screen.MenuSkin;
import dev.lmg.client.screen.SkinButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** "The game crashed last time. Send the report?" - Send / Always send / Don't send. */
public class CrashPromptScreen extends Screen {
	private static final int W = 300, H = 132, HEADER = 24;
	private final Screen parent;
	private final int count;
	private final long openedAt = Util.getMillis();
	private String message;
	private boolean boxes;

	public CrashPromptScreen(Screen parent, int count) {
		super(Component.literal("Crash report"));
		this.parent = parent;
		this.count = count;
	}

	@Override
	protected void init() {
		int x0 = width / 2 - W / 2, y0 = height / 2 - H / 2, by = y0 + H - 24;
		boxes = message == null || !message.equals("Sending...");
		if (message != null) {
			if (!message.equals("Sending...")) addRenderableWidget(new SkinButton(width / 2 - 40, by, 80, 16, Component.literal("OK"), openedAt, this::onClose));
			return;
		}
		int gap = 6, bw = (W - 24 - gap * 2) / 3;
		addRenderableWidget(new SkinButton(x0 + 12, by, bw, 16, Component.literal("Send"), openedAt, () -> send(false)));
		boxes = true;
		addRenderableWidget(new SkinButton(x0 + 12 + bw + gap, by, bw, 16, Component.literal("Always send"), openedAt, () -> send(true)));
		addRenderableWidget(new SkinButton(x0 + 12 + (bw + gap) * 2, by, bw, 16, Component.literal("Don't send"), openedAt, () -> {
			CrashReports.markChecked();
			onClose();
		}));
	}

	private void send(boolean always) {
		if (always) {
			LmgConfig.get().crashReports = CrashReports.Mode.ALWAYS;
			LmgConfig.save();
		}
		message = "Sending...";
		rebuildWidgets();
		CrashReports.sendAll(text -> {
			message = text + (always ? " Next time it'll send by itself (Menu > Help to change)." : "");
			rebuildWidgets();
		});
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		MenuBackground.draw(g, width, height, true);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 300f));
		int x0 = width / 2 - W / 2, y0 = height / 2 - H / 2;
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "Lmg Client crashed", a);
		int y = y0 + HEADER + 10;
		String head = count > 1 ? "The game crashed " + count + " times since you last played." : "The game crashed last time you played.";
		for (var line : font.split(Component.literal(message != null ? message : head + " Send the crash report so it can be fixed?"), W - 24)) {
			g.text(font, line, x0 + 12, y, Draw.withAlpha(0xE8EDF5, a), false);
			y += 11;
		}
		if (message == null) {
			y += 6;
			String what = "It says what went wrong, your Lmg and Minecraft versions, your mods and your PC's specs. "
					+ "Your name, account ID, folder names and IP are taken out first.";
			for (var line : font.split(Component.literal(what), W - 24)) {
				g.text(font, line, x0 + 12, y, Draw.withAlpha(0x7C8BA3, a), false);
				y += 10;
			}
		}
		// proper boxes behind the buttons ("Send" filled, the rest outlined)
		if (boxes) {
			boolean first = true;
			for (var c : children()) {
				if (!(c instanceof SkinButton b)) continue;
				int bx = b.getX(), by = b.getY(), bx1 = bx + b.getWidth(), by1 = by + b.getHeight();
				float h = b.isHoveredOrFocused() ? 1f : 0f;
				Draw.roundRect(g, bx, by, bx1, by1, 4, Theme.accent((first ? 0.8f : 0.45f + 0.3f * h) * a));
				Draw.roundRect(g, bx + 1, by + 1, bx1 - 1, by1 - 1, 3, Draw.withAlpha(0x0D1424, a));
				if (first) Draw.roundRect(g, bx + 1, by + 1, bx1 - 1, by1 - 1, 3, Theme.accent((0.32f + 0.18f * h) * a));
				first = false;
			}
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return message != null;
	}

	@Override
	public void onClose() {
		if (message == null) CrashReports.markChecked();
		minecraft.gui.setScreen(parent);
	}
}
