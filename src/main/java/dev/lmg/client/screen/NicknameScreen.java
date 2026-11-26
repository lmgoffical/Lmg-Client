package dev.lmg.client.screen;

import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import dev.lmg.client.social.Social;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.UUID;

/** Your own name for a friend. Only you see it; it's kept on this computer. */
public class NicknameScreen extends Screen {
	private static final int W = 220, H = 84;
	private final Screen parent;
	private final UUID uuid;
	private final String realName;
	private final long openedAt = Util.getMillis();
	private EditBox box;
	private int x0, y0;

	public NicknameScreen(Screen parent, UUID uuid, String realName) {
		super(Component.literal("Nickname"));
		this.parent = parent;
		this.uuid = uuid;
		this.realName = realName;
	}

	@Override
	protected void init() {
		if (parent != null) parent.init(width, height);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		box = new EditBox(font, x0 + 16, y0 + 34, W - 32, 10, Component.literal("Nickname"));
		box.setBordered(false);
		box.setMaxLength(20);
		box.setTextColor(0xFFFFFFFF);
		box.setHint(Component.literal("What you call " + realName).withStyle(st -> st.withColor(0x56637A)));
		box.setValue(Social.nickname(uuid));
		addRenderableWidget(box);
		setInitialFocus(box);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (parent != null) parent.extractBackground(g, -1, -1, partialTick);
		else super.extractBackground(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (parent != null) parent.extractRenderState(g, -1, -1, partialTick);
		g.nextStratum();
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 200f));
		g.fill(0, 0, width, height, Draw.withAlpha(0x05070D, 0.55f * a));
		Draw.roundRect(g, x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 8, Draw.withAlpha(0x2A3B5E, a));
		Draw.roundRect(g, x0, y0, x0 + W, y0 + H, 7, Draw.withAlpha(0x0C1322, 0.98f * a));
		g.text(font, "Nickname for " + realName, x0 + 12, y0 + 10, Draw.white(a), false);
		g.text(font, "Only you see it", x0 + 12, y0 + 20, Draw.withAlpha(0x7C8BA3, a), false);
		Draw.roundRect(g, x0 + 10, y0 + 30, x0 + W - 10, y0 + 46, 4, Draw.withAlpha(Theme.primary(), 0.7f * a));
		Draw.roundRect(g, x0 + 11, y0 + 31, x0 + W - 11, y0 + 45, 3, Draw.withAlpha(0x0A101D, a));
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int by = y0 + H - 22;
		boolean overClear = mouseX >= x0 + 10 && mouseX < x0 + 70 && mouseY >= by && mouseY < by + 14;
		PillButton.draw(g, font, x0 + 10, by, 60, 14, "Remove", false, overClear ? 1f : 0f, a);
		boolean overSave = mouseX >= x0 + W - 70 && mouseX < x0 + W - 10 && mouseY >= by && mouseY < by + 14;
		PillButton.draw(g, font, x0 + W - 70, by, 60, 14, "Save", true, overSave ? 1f : 0f, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int by = y0 + H - 22;
		if (event.y() >= by && event.y() < by + 14) {
			if (event.x() >= x0 + 10 && event.x() < x0 + 70) {
				Social.setNickname(uuid, "");
				onClose();
				return true;
			}
			if (event.x() >= x0 + W - 70 && event.x() < x0 + W - 10) {
				save();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	private void save() {
		Social.setNickname(uuid, box.getValue());
		onClose();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
