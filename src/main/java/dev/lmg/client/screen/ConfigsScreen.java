package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.config.CloudCodes;
import dev.lmg.client.config.ConfigProfiles;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuSnow;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Saved configs and sharing.
 *  - Save your current setup under a name; load / delete saved ones.
 *  - "Share" uploads a config and copies a short code like LMG-ABC234.
 *  - Type or paste a code (short or a long LMG1: code) and press Import.
 */
public class ConfigsScreen extends Screen {
	private static final int W = 300, H = 244, HEADER = 24, ROW = 17, VISIBLE = 6;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int x0, y0, scroll;
	private EditBox nameBox, codeBox;
	private String nameText = "", codeText = "";
	private String pendingDelete;
	private String status = "";
	private boolean statusOk = true;
	private long statusAt;
	private boolean busy;

	public ConfigsScreen(Screen parent) {
		super(Component.literal("Configs"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		addRenderableWidget(new SkinButton(x0 + 6, y0 + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));

		nameBox = new EditBox(font, x0 + 10, y0 + HEADER + 9, 186, 14, Component.literal("Config name"));
		nameBox.setMaxLength(24);
		nameBox.setHint(Component.literal("Name your config..."));
		nameBox.setValue(nameText);
		nameBox.setResponder(v -> nameText = v);
		addRenderableWidget(nameBox);
		addRenderableWidget(new SkinButton(x0 + 202, y0 + HEADER + 9, 88, 14, Component.literal("Save current"), openedAt, this::saveCurrent));

		List<String> names = ConfigProfiles.list();
		scroll = Math.max(0, Math.min(scroll, names.size() - VISIBLE));
		int listY = y0 + HEADER + 32;
		for (int i = 0; i < Math.min(VISIBLE, names.size() - scroll); i++) {
			String name = names.get(scroll + i);
			int ry = listY + i * ROW;
			addRenderableWidget(new SkinButton(x0 + W - 136, ry, 40, 14, Component.literal("Load"), openedAt, () -> load(name)));
			addRenderableWidget(new SkinButton(x0 + W - 92, ry, 44, 14, Component.literal("Share"), openedAt, () -> shareSaved(name)));
			boolean confirm = name.equals(pendingDelete);
			addRenderableWidget(new SkinButton(x0 + W - 44, ry, 34, 14, Component.literal(confirm ? "Sure?" : "X"), openedAt, () -> delete(name)));
		}

		// import: type/paste a code (empty box = use the clipboard)
		codeBox = new EditBox(font, x0 + 10, y0 + H - 42, 186, 14, Component.literal("Config code"));
		codeBox.setMaxLength(20000);
		codeBox.setHint(Component.literal("LMG-ABC234 (or paste)"));
		codeBox.setValue(codeText);
		codeBox.setResponder(v -> codeText = v);
		addRenderableWidget(codeBox);
		addRenderableWidget(new SkinButton(x0 + 202, y0 + H - 42, 88, 14, Component.literal("Import"), openedAt, this::importCode));

		addRenderableWidget(new SkinButton(x0 + 10, y0 + H - 22, 136, 14, Component.literal("Share current setup"), openedAt, this::shareCurrent));
		addRenderableWidget(new SkinButton(x0 + W - 146, y0 + H - 22, 136, 14, Component.literal("Copy offline code"), openedAt, this::copyOffline));
	}

	// ---- actions ---------------------------------------------------------------------------------

	private void saveCurrent() {
		try {
			String saved = ConfigProfiles.saveCurrent(nameText);
			say("Saved \"" + saved + "\"", true);
			nameText = "";
			rebuildWidgets();
		} catch (Exception e) {
			say("Couldn't save: " + e.getMessage(), false);
		}
	}

	private void load(String name) {
		try {
			ConfigProfiles.load(name);
			say("Loaded \"" + name + "\"", true);
			rebuildWidgets(); // the menu style may have changed
		} catch (Exception e) {
			say("Couldn't load \"" + name + "\"", false);
		}
	}

	private void delete(String name) {
		if (!name.equals(pendingDelete)) {
			pendingDelete = name;
			rebuildWidgets();
			return;
		}
		pendingDelete = null;
		try {
			ConfigProfiles.delete(name);
			say("Removed \"" + name + "\" from your saved configs (shared codes keep working)", true);
		} catch (Exception e) {
			say("Couldn't delete \"" + name + "\"", false);
		}
		rebuildWidgets();
	}

	private void shareSaved(String name) {
		try {
			upload(name, ConfigProfiles.codeForSaved(name));
		} catch (Exception e) {
			say("Couldn't read \"" + name + "\"", false);
		}
	}

	private void shareCurrent() {
		String name = nameText.isBlank() ? "My config" : nameText;
		upload(name, ConfigProfiles.codeForCurrent(name));
	}

	/** Uploads a config and copies the short code; falls back to the long code if offline. */
	private void upload(String name, String longCode) {
		if (busy) return;
		busy = true;
		say("Uploading...", true);
		CloudCodes.share(name, longCode).whenComplete((shortCode, error) -> minecraft.execute(() -> {
			busy = false;
			if (error == null) {
				minecraft.keyboardHandler.setClipboard(shortCode);
				say("Copied " + shortCode + " - send it to a friend!", true);
			} else {
				minecraft.keyboardHandler.setClipboard(longCode);
				say("Offline? Copied the long code instead (" + CloudCodes.messageOf(error) + ")", false);
			}
		}));
	}

	private void copyOffline() {
		String name = nameText.isBlank() ? "My config" : nameText;
		minecraft.keyboardHandler.setClipboard(ConfigProfiles.codeForCurrent(name));
		say("Long code copied (works without internet)", true);
	}

	private void importCode() {
		if (busy) return;
		String text = codeText.isBlank() ? minecraft.keyboardHandler.getClipboard() : codeText;
		if (text == null || text.isBlank()) {
			say("Type a code in the box (or copy one first)", false);
			return;
		}
		if (CloudCodes.isShortCode(text)) {
			busy = true;
			say("Downloading...", true);
			CloudCodes.fetch(text).whenComplete((profile, error) -> minecraft.execute(() -> {
				busy = false;
				if (error == null) applyImported(profile);
				else say(CloudCodes.messageOf(error), false);
			}));
			return;
		}
		try {
			applyImported(ConfigProfiles.decode(text));
		} catch (IllegalArgumentException e) {
			say(e.getMessage(), false);
		}
	}

	private void applyImported(ConfigProfiles.Profile p) {
		try {
			LmgConfig.apply(p.config());
			String name = p.name();
			List<String> existing = ConfigProfiles.list();
			for (int n = 2; existing.contains(name); n++) name = ConfigProfiles.cleanName(p.name() + " " + n);
			String saved = ConfigProfiles.saveCurrent(name);
			codeText = "";
			say("Imported \"" + saved + "\" and switched to it", true);
			rebuildWidgets();
		} catch (Exception e) {
			say("Couldn't import that config", false);
		}
	}

	private void say(String message, boolean ok) {
		status = message;
		statusOk = ok;
		statusAt = Util.getMillis();
	}

	// ---- drawing ---------------------------------------------------------------------------------

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
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "Configs", a);
		MenuSnow.panelTop(x0, y0, x0 + W);
		int[] tc = MenuSkin.textColors();

		List<String> names = ConfigProfiles.list();
		int listY = y0 + HEADER + 32;
		g.text(font, "Saved configs (" + names.size() + ")", x0 + 10, listY - 10, Draw.withAlpha(tc[1], a), false);
		if (names.isEmpty()) {
			g.text(font, "Nothing saved yet - type a name above", x0 + 10, listY + 4, Draw.withAlpha(tc[1], a), false);
			g.text(font, "and press Save current.", x0 + 10, listY + 14, Draw.withAlpha(tc[1], a), false);
		}
		for (int i = 0; i < Math.min(VISIBLE, names.size() - scroll); i++) {
			String name = names.get(scroll + i);
			int ry = listY + i * ROW;
			if (i % 2 == 0) g.fill(x0 + 8, ry - 1, x0 + W - 8, ry + 15, Draw.white(0.025f * a));
			g.text(font, name, x0 + 12, ry + 3, Draw.withAlpha(tc[0], a), false);
		}
		if (names.size() > VISIBLE) {
			String more = (scroll + 1) + "-" + Math.min(names.size(), scroll + VISIBLE) + " of " + names.size() + "  (scroll)";
			g.text(font, more, x0 + 10, listY + VISIBLE * ROW + 1, Draw.withAlpha(tc[1], a), false);
		}
		g.text(font, "Import a code", x0 + 10, y0 + H - 52, Draw.withAlpha(tc[1], a), false);

		if (!status.isEmpty()) {
			float age = (Util.getMillis() - statusAt) / 1000f;
			float sa = busy || age < 5f ? 1f : Math.max(0f, 1f - (age - 5f));
			if (sa > 0) {
				int color = statusOk ? 0x7CFF9E : 0xFF8C7C;
				String shown = font.width(status) > W - 20 ? font.plainSubstrByWidth(status, W - 26) + ".." : status;
				g.centeredText(font, shown, x0 + W / 2, y0 + H - 63, Draw.withAlpha(color, a * sa));
			}
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		MenuSnow.drawFront(g, 1f);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int before = scroll;
		scroll -= (int) Math.signum(scrollY);
		scroll = Math.max(0, Math.min(scroll, ConfigProfiles.list().size() - VISIBLE));
		if (scroll != before) rebuildWidgets();
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
