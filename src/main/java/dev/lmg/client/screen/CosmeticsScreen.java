package dev.lmg.client.screen;

import com.mojang.blaze3d.Blaze3D;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.cosmetic.CosmeticModel;
import dev.lmg.client.cosmetic.CosmeticRegistry;
import dev.lmg.client.cosmetic.CustomModels;
import dev.lmg.client.cosmetic.CustomSkins;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuSnow;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cosmetics wardrobe: category tabs on the left, a grid of item cards with a "+" to wear them,
 * and a live preview of you on the right (drag it to spin). Follows the selected menu style.
 */
public class CosmeticsScreen extends Screen implements dev.lmg.client.render.WorldFilter.GreyBehind {
	private static final String SKINS = "skins";
	private static final String[] TABS;
	static {
		TABS = new String[CosmeticRegistry.CATEGORIES.length + 1];
		System.arraycopy(CosmeticRegistry.CATEGORIES, 0, TABS, 0, CosmeticRegistry.CATEGORIES.length);
		TABS[TABS.length - 1] = SKINS;
	}
	private static final int HEADER = 24, SIDEBAR = 92, PREVIEW_W = 124, MIN_CARD_W = 58, CARD_H = 68, GAP = 6;
	private int CARD_W = MIN_CARD_W;

	private static String lastTab = "capes";

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int W, H, x0, y0, gridX, gridY, gridW, gridH, cols, scroll;
	private String tab = lastTab;
	private String selected;
	private float spin = 0f, spinVel = 0f;
	private boolean draggingModel;
	private final Map<String, Float> hover = new HashMap<>();
	private long lastFrame = -1;
	private PlayerSkinWidget menuPreview;

	public CosmeticsScreen(Screen parent) {
		super(Component.literal("Cosmetics"));
		this.parent = parent;
	}

	// ---- layout ----------------------------------------------------------------------------------

	@Override
	protected void init() {
		W = Math.min(440, width - 16);
		H = Math.min(268, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		gridX = x0 + SIDEBAR + 10;
		gridY = y0 + HEADER + 22;
		gridW = W - SIDEBAR - PREVIEW_W - 22;
		gridH = H - HEADER - 30;
		cols = Math.max(1, (gridW + GAP) / (MIN_CARD_W + GAP));
		CARD_W = (gridW - (cols - 1) * GAP) / cols;
		addRenderableWidget(new SkinButton(x0 + 6, y0 + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(x0 + 54, y0 + 6, 40, 13, Component.literal("Pets"), openedAt,
				() -> minecraft.gui.setScreen(new PetsScreen(this))));

		int px = x0 + W - PREVIEW_W - 6;
		java.util.UUID partner = dev.lmg.client.social.Social.partner();
		if (partner != null && !tab.equals("custom") && !tab.equals(SKINS)) {
			var f = dev.lmg.client.social.Social.state().friend(partner);
			String who = f != null ? f.name() : "partner";
			boolean on = LmgConfig.get().matchPartner;
			String label = (on ? "Matching " : "Match ") + who + " \u2764";
			int bw = font.width(label) + 14;
			addRenderableWidget(new PillButton(x0 + W - bw - 6, y0 + 5, bw, 14, Component.literal(label), on, openedAt, () -> {
				LmgConfig c = LmgConfig.get();
				c.matchPartner = !c.matchPartner;
				LmgConfig.save();
				if (c.matchPartner) toast(dev.lmg.client.social.Partner.matchOutfit(true));
				else toast("Your outfit is yours again. Your items stay on.");
				rebuildWidgets();
			}));
		}
		if (tab.equals("custom")) {
			addRenderableWidget(new SkinButton(x0 + W - 150, y0 + 6, 70, 13, Component.literal("Open folder"), openedAt, () -> open(CustomModels.DIR)));
			addRenderableWidget(new SkinButton(x0 + W - 76, y0 + 6, 70, 13, Component.literal("Reload"), openedAt, () -> {
				CustomModels.reload();
				toast(CustomModels.lastMessage);
			}));
		} else if (tab.equals(SKINS)) {
			addRenderableWidget(new SkinButton(x0 + W - 150, y0 + 6, 70, 13, Component.literal("Open folder"), openedAt, () -> open(CustomSkins.DIR)));
			addRenderableWidget(new SkinButton(x0 + W - 76, y0 + 6, 70, 13, Component.literal("Reload"), openedAt, () -> {
				CustomSkins.forgetAll();
				toast(CustomSkins.list().size() + " skins found");
			}));
			if (LmgConfig.get().customSkin != null) {
				addRenderableWidget(new SkinButton(px + 6, y0 + H - 22, PREVIEW_W - 12, 14,
						Component.literal(LmgConfig.get().customSkinSlim ? "Arms: Slim (Alex)" : "Arms: Wide (Steve)"), openedAt, () -> {
					LmgConfig.get().customSkinSlim = !LmgConfig.get().customSkinSlim;
					LmgConfig.save();
					rebuildWidgets();
				}));
			}
		}

		if (minecraft.player == null) {
			// Outside a world there is no "you" to draw, so show the skin and cape with the vanilla widget.
			int ph = 108, pw = Math.round(ph * 0.6f);
			menuPreview = new PlayerSkinWidget(pw, ph, minecraft.getEntityModels(), this::menuSkin);
			menuPreview.setPosition(px + PREVIEW_W / 2 - pw / 2, y0 + HEADER + 10);
			addRenderableWidget(menuPreview);
		}
		if (selected == null || !items().contains(selected)) selected = equippedInTab();
	}

	private PlayerSkin menuSkin() {
		PlayerSkin base = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false).get();
		PlayerSkin custom = CustomSkins.current();
		ClientAsset.Texture body = custom != null ? custom.body() : base.body();
		PlayerModelType model = custom != null ? custom.model() : base.model();
		CosmeticModel cape = CosmeticRegistry.get(LmgConfig.get().equipped.get("capes"));
		ClientAsset.Texture capeTex = base.cape();
		if (cape != null && cape.capeTexture != null) {
			capeTex = new ClientAsset.ResourceTexture(LmgClient.id("cosmetic/" + cape.capeTexture), LmgClient.id("textures/cosmetic/" + cape.capeTexture));
		}
		return PlayerSkin.insecure(body, capeTex, base.elytra(), model);
	}

	/** Item ids shown in the current tab ("" stands for "your normal skin" in the Skins tab). */
	private List<String> items() {
		List<String> out = new ArrayList<>();
		if (tab.equals(SKINS)) {
			out.add("");
			out.addAll(CustomSkins.list());
		} else {
			for (CosmeticModel m : CosmeticRegistry.inCategory(tab)) out.add(m.id);
		}
		return out;
	}

	private String equippedInTab() {
		if (tab.equals(SKINS)) return LmgConfig.get().customSkin == null ? "" : LmgConfig.get().customSkin;
		String id = LmgConfig.get().equipped.get(tab);
		if (id != null) return id;
		List<String> list = items();
		return list.isEmpty() ? null : list.get(0);
	}

	private boolean isEquipped(String id) {
		if (tab.equals(SKINS)) {
			String cur = LmgConfig.get().customSkin;
			return id.isEmpty() ? cur == null : id.equals(cur);
		}
		return id.equals(LmgConfig.get().equipped.get(tab));
	}

	private void toggle(String id) {
		LmgConfig c = LmgConfig.get();
		if (tab.equals(SKINS)) {
			if (id.isEmpty() || id.equals(c.customSkin)) {
				c.customSkin = null;
			} else {
				c.customSkin = id;
				c.customSkinSlim = CustomSkins.looksSlim(id);
			}
		} else if (id.equals(c.equipped.get(tab))) {
			c.equipped.remove(tab);
		} else {
			c.equipped.put(tab, id);
		}
		selected = id;
		LmgConfig.save();
		rebuildWidgets();
	}

	private void open(java.nio.file.Path dir) {
		try {
			java.nio.file.Files.createDirectories(dir);
			Blaze3D.openPath(dir);
		} catch (Exception e) {
			toast("Couldn't open " + dir);
		}
	}

	private String toast = "";
	private long toastAt;

	private void toast(String text) {
		toast = text;
		toastAt = Util.getMillis();
	}

	// ---- palette ---------------------------------------------------------------------------------

	record Pal(int sidebar, int card, int cardHover, int border, int text, int dim, int accent, int field) {}

	static Pal pal() {
		return switch (MenuSkin.style()) {
			case NEVERLOSE -> new Pal(MenuSkin.NL_SIDEBAR, MenuSkin.NL_CARD, 0xFF06182F, MenuSkin.NL_BORDER,
					MenuSkin.NL_TEXT, 0x6F8091, MenuSkin.accent(), MenuSkin.NL_FIELD);
			case SKEET -> new Pal(0xFF0C0C0C, 0xFF191919, 0xFF202020, 0xFF2B2B2B, MenuSkin.SK_TEXT, 0x7A7A7A, MenuSkin.accent(), 0xFF262626);
			default -> new Pal(0xFF0A101D, 0xFF111A2E, 0xFF16213A, 0xFF1C2943, 0xE8EDF5, 0x7C8BA3, Theme.primary(), 0xFF1C2943);
		};
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
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 300f));
		Pal p = pal();
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "Cosmetics", a);
		MenuSnow.panelTop(x0, y0, x0 + W);

		// sidebar: one tab per category, with how many items it has
		int sx = x0 + 6, sy = y0 + HEADER + 8;
		g.fill(x0 + SIDEBAR + 2, y0 + HEADER + 6, x0 + SIDEBAR + 3, y0 + H - 8, Draw.fade(p.border, a));
		for (String t : TABS) {
			if (t.equals(SKINS)) {
				sy += 6;
				g.text(font, "YOU", sx + 4, sy, Draw.withAlpha(p.dim, a), false);
				sy += 12;
			}
			boolean active = t.equals(tab);
			boolean over = mouseX >= sx && mouseX < x0 + SIDEBAR && mouseY >= sy && mouseY < sy + 16;
			float h = anim("tab:" + t, over ? 1 : 0, dt);
			MenuSkin.tab(g, font, sx, sy, SIDEBAR - 8, 16, Component.literal(tabName(t)), active ? 1f : 0f, h);
			String count = String.valueOf(t.equals(SKINS) ? CustomSkins.list().size() : CosmeticRegistry.inCategory(t).size());
			g.text(font, count, x0 + SIDEBAR - 6 - font.width(count), sy + 5, Draw.withAlpha(active ? p.accent : p.dim, a), false);
			sy += 18;
		}

		// grid header
		String head = tabName(tab);
		g.text(font, head, gridX, y0 + HEADER + 8, Draw.withAlpha(p.text, a), false);
		String sub = switch (tab) {
			case "custom" -> "Blockbench models from config/lmgclient-cosmetics";
			case SKINS -> "PNG skins from config/lmgclient-skins";
			default -> "Press + to wear. Drag yourself to spin.";
		};
		int subRoom = gridW - font.width(head) - 8;
		if (font.width(sub) > subRoom) sub = font.plainSubstrByWidth(sub, subRoom - 6) + "..";
		g.text(font, sub, gridX + font.width(head) + 6, y0 + HEADER + 8, Draw.withAlpha(p.dim, a), false);

		// cards
		List<String> list = items();
		int rows = (list.size() + cols - 1) / cols;
		int visibleRows = Math.max(1, (gridH + GAP) / (CARD_H + GAP));
		scroll = Math.max(0, Math.min(scroll, rows - visibleRows));
		g.enableScissor(gridX - 2, gridY - 2, gridX + gridW + 2, gridY + gridH);
		for (int i = 0; i < list.size(); i++) {
			int r = i / cols - scroll, c = i % cols;
			if (r < 0 || r >= visibleRows + 1) continue;
			int cx = gridX + c * (CARD_W + GAP), cy = gridY + r * (CARD_H + GAP);
			drawCard(g, list.get(i), cx, cy, mouseX, mouseY, dt, a, p);
		}
		g.disableScissor();
		if (list.isEmpty() || (tab.equals(SKINS) && list.size() == 1)) emptyHint(g, p, a);
		if (rows > visibleRows) {
			int barH = Math.max(12, gridH * visibleRows / rows);
			int by = gridY + (gridH - barH) * scroll / Math.max(1, rows - visibleRows);
			Draw.roundRect(g, gridX + gridW + 3, by, gridX + gridW + 5, by + barH, 1, Draw.withAlpha(p.accent, 0.6f * a));
		}

		drawPreview(g, mouseX, mouseY, dt, a, p);

		if (!toast.isEmpty()) {
			float age = (now - toastAt) / 1000f;
			float ta = age < 3f ? 1f : Math.max(0f, 1f - (age - 3f));
			if (ta > 0) g.centeredText(font, toast, x0 + W / 2, y0 + H + 6, Draw.withAlpha(0xFFFFFF, ta * a));
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		MenuSnow.drawFront(g, 1f);
	}

	private void drawCard(GuiGraphicsExtractor g, String id, int x, int y, int mx, int my, float dt, float a, Pal p) {
		boolean over = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H && my >= gridY && my < gridY + gridH;
		float h = anim("card:" + id, over ? 1 : 0, dt);
		boolean equipped = isEquipped(id);
		boolean sel = id.equals(selected);
		int border = sel ? (0xFF000000 | p.accent) : Draw.lerpRgb(p.border & 0xFFFFFF, p.field & 0xFFFFFF, h) | (p.border & 0xFF000000);
		if (MenuSkin.style() == LmgConfig.MenuStyle.SKEET) {
			g.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, Draw.fade(0xFF000000, a));
			g.fill(x, y, x + CARD_W, y + CARD_H, Draw.fade(border, a));
			g.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, Draw.fade(h > 0.5f ? p.cardHover : p.card, a));
		} else {
			Draw.roundRect(g, x, y, x + CARD_W, y + CARD_H, 5, Draw.fade(border, a));
			Draw.roundRect(g, x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, 4, Draw.fade(h > 0.5f ? p.cardHover : p.card, a));
		}
		if (equipped) Draw.roundRect(g, x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, 4, Draw.withAlpha(p.accent, 0.10f * a));

		// thumbnail (lifts a little on hover)
		float icx = x + CARD_W / 2f, icy = y + 25 - 2 * h;
		if (tab.equals(SKINS)) {
			drawSkinFace(g, id, icx, icy, 30, a);
		} else {
			CosmeticModel m = CosmeticRegistry.get(id);
			if (m != null && !m.custom) {
				Draw.texture(g, LmgClient.id("textures/cosmetic/icons/" + id + ".png"), 128, icx, icy, 46 + 4 * h, 0, Draw.white(a));
			} else {
				Draw.roundRect(g, (int) icx - 13, (int) icy - 13, (int) icx + 13, (int) icy + 13, 4, Draw.withAlpha(p.accent, 0.18f * a));
				g.centeredText(font, "3D", (int) icx, (int) icy - 4, Draw.withAlpha(p.accent, a));
			}
		}

		String name = displayName(id);
		if (font.width(name) > CARD_W - 6) name = name.replaceFirst(" (Wings|Ears|Glasses|Hat|Cape)$", "");
		if (font.width(name) > CARD_W - 6) name = font.plainSubstrByWidth(name, CARD_W - 10) + "..";
		g.centeredText(font, name, x + CARD_W / 2, y + CARD_H - 13, Draw.withAlpha(equipped ? p.text : Draw.lerpRgb(p.dim, p.text, Math.max(h, sel ? 1 : 0)), a));

		// the "+" / tick button in the corner
		int bx = x + CARD_W - 13, by = y + 3;
		boolean overBtn = mx >= bx - 1 && mx < bx + 11 && my >= by - 1 && my < by + 11;
		float bh = anim("btn:" + id, overBtn ? 1 : 0, dt);
		int btnBg = equipped ? (0xFF000000 | p.accent) : (Draw.lerpRgb(p.field & 0xFFFFFF, p.accent, bh * 0.5f) | 0xFF000000);
		Draw.roundRect(g, bx, by, bx + 10, by + 10, 5, Draw.fade(btnBg, a));
		int glyph = Draw.withAlpha(equipped ? 0xFFFFFF : Draw.lerpRgb(p.accent, 0xFFFFFF, bh), a);
		if (equipped && overBtn) {            // hovering a worn item shows "-" (take it off)
			g.fill(bx + 3, by + 4, bx + 7, by + 6, glyph);
		} else if (equipped) {                // tick
			g.fill(bx + 2, by + 5, bx + 3, by + 6, glyph);
			g.fill(bx + 3, by + 6, bx + 4, by + 7, glyph);
			g.fill(bx + 4, by + 5, bx + 5, by + 6, glyph);
			g.fill(bx + 5, by + 4, bx + 6, by + 5, glyph);
			g.fill(bx + 6, by + 3, bx + 7, by + 4, glyph);
			g.fill(bx + 4, by + 6, bx + 5, by + 7, glyph);
		} else {                              // plus
			g.fill(bx + 2, by + 4, bx + 8, by + 6, glyph);
			g.fill(bx + 4, by + 2, bx + 6, by + 8, glyph);
		}
	}

	private void drawSkinFace(GuiGraphicsExtractor g, String file, float cx, float cy, int size, float a) {
		Identifier tex = file.isEmpty() ? null : CustomSkins.texture(file);
		if (tex == null) {
			if (file.isEmpty()) tex = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false).get().body().texturePath();
			else {
				g.centeredText(font, "?", (int) cx, (int) cy - 4, Draw.white(a));
				return;
			}
		}
		int x = Math.round(cx - size / 2f), y = Math.round(cy - size / 2f);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 8f, 8f, size, size, 8, 8, 64, 64, Draw.white(a));
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 40f, 8f, size, size, 8, 8, 64, 64, Draw.white(a));
	}

	private void emptyHint(GuiGraphicsExtractor g, Pal p, float a) {
		int y = gridY + (tab.equals(SKINS) ? CARD_H + 14 : 20);
		String[] lines = tab.equals(SKINS)
				? new String[]{"Put skin PNGs (64x64) in", ".minecraft/config/lmgclient-skins", "then press Reload."}
				: new String[]{"Export from Blockbench as \"Java Block/Item\".", "Put model.json + model.png in", ".minecraft/config/lmgclient-cosmetics", "Build it around the head: (4,0,4) to (12,8,12).", "Name it body_<name> to wear it on your body."};
		for (String l : lines) {
			g.text(font, l, gridX, y, Draw.withAlpha(p.dim, a), false);
			y += 11;
		}
	}

	private void drawPreview(GuiGraphicsExtractor g, int mx, int my, float dt, float a, Pal p) {
		int px = x0 + W - PREVIEW_W - 6, py = y0 + HEADER + 6, pw = PREVIEW_W, ph = 122;
		Draw.roundRect(g, px, py, px + pw, py + ph, 5, Draw.fade(p.sidebar, a));
		// soft floor glow under the model
		for (int i = 0; i < 6; i++) {
			int rw = 34 - i * 5;
			g.fill(px + pw / 2 - rw, py + ph - 12 + i / 2, px + pw / 2 + rw, py + ph - 11 + i / 2, Draw.withAlpha(p.accent, 0.05f * a));
		}
		if (minecraft.player != null) {
			if (!draggingModel) {
				spin += spinVel * dt;
				spinVel *= (float) Math.pow(0.05, dt);
			}
			drawPlayer(g, px + 2, py + 2, px + pw - 2, py + ph - 2, spin);
		}

		// details of the selected item
		int ty = py + ph + 8;
		if (selected == null) return;
		CosmeticModel m = tab.equals(SKINS) ? null : CosmeticRegistry.get(selected);
		String title = displayName(selected);
		g.text(font, title, px + 2, ty, Draw.withAlpha(p.text, a), false);
		ty += 11;
		String desc = m != null ? m.description : (selected.isEmpty() ? "The skin from your Minecraft account." : "Only you see this skin.");
		for (var line : font.split(Component.literal(desc), pw - 4)) {
			if (ty > y0 + H - (m != null && m.tintable ? 62 : 32)) break;
			g.text(font, line, px + 2, ty, Draw.withAlpha(p.dim, a), false);
			ty += 10;
		}
		if (m == null || !m.tintable) return;

		// colour: three quick picks, then two sliders (colour, and light to dark) that recolour you live;
		// click the big swatch for the full picker with the box
		ty = y0 + H - 40;
		g.text(font, "Colour", px + 2, ty - 11, Draw.withAlpha(p.dim, a), false);
		String cur = LmgConfig.get().cosmeticColors.get(m.id);
		int sx = px + 2;
		for (int i = 0; i < 3; i++) {
			int bx = sx + i * 18, by = ty;
			String value = swatchValue(i);
			boolean on = value == null ? (cur == null) : value.equals(cur);
			Draw.roundRect(g, bx - 1, by - 1, bx + 15, by + 13, 3, on ? Draw.withAlpha(p.accent, a) : Draw.fade(p.border, a));
			switch (i) {
				case 0 -> {
					Draw.roundRect(g, bx, by, bx + 14, by + 12, 2, Draw.withAlpha(m.defaultRgb(), a));
					g.centeredText(font, "D", bx + 7, by + 2, Draw.withAlpha(0x000000, 0.6f * a));
				}
				case 1 -> Draw.roundRect(g, bx, by, bx + 14, by + 12, 2, Draw.withAlpha(Theme.accent() & 0xFFFFFF, a));
				default -> {
					for (int k = 0; k < 14; k++) {
						int rgb = java.awt.Color.HSBtoRGB(k / 14f, 0.6f, 1f) & 0xFFFFFF;
						g.fill(bx + k, by + 1, bx + k + 1, by + 11, Draw.withAlpha(rgb, a));
					}
				}
			}
		}
		// the colour you have now (click for the full picker)
		int nowRgb = CosmeticRegistry.colorFor(m, cur, 0) & 0xFFFFFF;
		int swx0 = sx + 56, swx1 = px + PREVIEW_W - 6;
		boolean custom = cur != null && cur.startsWith("#");
		Draw.roundRect(g, swx0 - 1, ty - 1, swx1 + 1, ty + 13, 3, custom ? Draw.withAlpha(p.accent, a) : Draw.fade(p.border, a));
		Draw.roundRect(g, swx0, ty, swx1, ty + 12, 2, Draw.withAlpha(nowRgb, a));
		String hexText = String.format(java.util.Locale.ROOT, "#%06X", nowRgb);
		g.centeredText(font, hexText, (swx0 + swx1) / 2, ty + 2, Draw.withAlpha(Theme.textOn(nowRgb), 0.85f * a));
		// hue slider
		int sl0 = sx, sl1 = px + PREVIEW_W - 6, hy = ty + 17;
		for (int k = sl0; k < sl1; k++) {
			int rgb = java.awt.Color.HSBtoRGB((k - sl0) / (float) (sl1 - sl0), 0.85f, 1f) & 0xFFFFFF;
			g.fill(k, hy, k + 1, hy + 6, Draw.withAlpha(rgb, a));
		}
		float[] ht = hueTone(nowRgb);
		knob(g, sl0 + Math.round(ht[0] * (sl1 - sl0)), hy, a);
		// light -> colour -> dark slider, in this hue
		int ly = ty + 27;
		for (int k = sl0; k < sl1; k++) {
			int rgb = fromHueTone(ht[0], (k - sl0) / (float) (sl1 - sl0));
			g.fill(k, ly, k + 1, ly + 6, Draw.withAlpha(rgb, a));
		}
		knob(g, sl0 + Math.round(ht[1] * (sl1 - sl0)), ly, a);
	}

	private void knob(GuiGraphicsExtractor g, int x, int y, float a) {
		Draw.roundRect(g, x - 2, y - 2, x + 3, y + 8, 2, Draw.withAlpha(0x000000, 0.7f * a));
		Draw.roundRect(g, x - 1, y - 1, x + 2, y + 7, 1, Draw.white(a));
	}

	/** {hue, tone}: tone 0 = white, 0.5 = the full colour, 1 = black. */
	static float[] hueTone(int rgb) {
		float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
		float tone = hsb[2] < 0.999f ? 0.5f + (1f - hsb[2]) * 0.5f : hsb[1] * 0.5f;
		return new float[]{hsb[0], Math.max(0f, Math.min(1f, tone))};
	}

	static int fromHueTone(float hue, float tone) {
		float sat = tone < 0.5f ? tone * 2f : 1f;
		float bri = tone < 0.5f ? 1f : 1f - (tone - 0.5f) * 2f * 0.92f;
		return java.awt.Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
	}

	/** 0 = nothing, 1 = dragging the colour slider, 2 = the light/dark slider. */
	private int draggingColour;

	private void setColourFrom(double mx, int which) {
		CosmeticModel m = selected == null || tab.equals(SKINS) ? null : CosmeticRegistry.get(selected);
		if (m == null || !m.tintable) return;
		int px = x0 + W - PREVIEW_W - 6, sl0 = px + 2, sl1 = px + PREVIEW_W - 6;
		float t = (float) Math.max(0, Math.min(1, (mx - sl0) / (double) (sl1 - sl0)));
		float[] ht = hueTone(CosmeticRegistry.colorFor(m, LmgConfig.get().cosmeticColors.get(m.id), 0) & 0xFFFFFF);
		// starting from white/black/grey: give the colour slider something to show
		if (which == 1 && (ht[1] < 0.05f || ht[1] > 0.95f)) ht[1] = 0.5f;
		int rgb = which == 1 ? fromHueTone(t, ht[1]) : fromHueTone(ht[0], t);
		LmgConfig.get().cosmeticColors.put(m.id, String.format(java.util.Locale.ROOT, "#%06X", rgb));
	}

	/** Draws you, spun by {@code yaw} degrees, like the inventory does but with free rotation. */
	private void drawPlayer(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float yaw) {
		var player = minecraft.player;
		EntityRenderState state = minecraft.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, 1f);
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180f + yaw;
			living.yRot = 0f;
			living.xRot = 0f;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1f;
		}
		state.shadowPieces.clear();
		Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(-8));
		Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
		Vector3f offset = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.06f, 0f);
		g.entity(state, 44f, offset, rot, tilt, x1, y1, x2, y2);
	}

	private String swatchValue(int i) {
		return switch (i) {
			case 0 -> null;
			case 1 -> "accent";
			case 2 -> "rainbow";
			default -> null;
		};
	}

	private float anim(String key, float target, float dt) {
		float v = Draw.approach(hover.getOrDefault(key, 0f), target, 14f * Theme.speed(), dt);
		hover.put(key, v);
		return v;
	}

	private String displayName(String id) {
		if (tab.equals(SKINS)) return id.isEmpty() ? "Your skin" : id.replaceFirst("(?i)\\.png$", "");
		CosmeticModel m = CosmeticRegistry.get(id);
		return m == null ? id : m.name;
	}

	private static String tabName(String t) {
		return t.equals(SKINS) ? "Skins" : CosmeticRegistry.CATEGORY_NAMES.getOrDefault(t, t);
	}

	// ---- input -----------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		if (event.button() != 0) return false;

		// tabs
		int sx = x0 + 6, sy = y0 + HEADER + 8;
		for (String t : TABS) {
			if (t.equals(SKINS)) sy += 18;
			if (mx >= sx && mx < x0 + SIDEBAR && my >= sy && my < sy + 16) {
				if (!t.equals(tab)) {
					tab = lastTab = t;
					scroll = 0;
					selected = null;
					if (t.equals("custom")) CustomModels.reload();
					rebuildWidgets();
				}
				return true;
			}
			sy += 18;
		}

		// cards
		if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
			List<String> list = items();
			for (int i = 0; i < list.size(); i++) {
				int r = i / cols - scroll, c = i % cols;
				int cx = gridX + c * (CARD_W + GAP), cy = gridY + r * (CARD_H + GAP);
				if (mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H) {
					String id = list.get(i);
					boolean onButton = mx >= cx + CARD_W - 14 && my < cy + 14;
					if (onButton || doubleClick || id.equals(selected)) toggle(id);
					else selected = id;
					return true;
				}
			}
		}

		// preview: start spinning / colour swatches
		int px = x0 + W - PREVIEW_W - 6, py = y0 + HEADER + 6;
		if (minecraft.player != null && mx >= px && mx < px + PREVIEW_W && my >= py && my < py + 122) {
			draggingModel = true;
			return true;
		}
		CosmeticModel m = selected == null || tab.equals(SKINS) ? null : CosmeticRegistry.get(selected);
		if (m != null && m.tintable) {
			int ty = y0 + H - 40, cx0 = px + 2;
			for (int i = 0; i < 3; i++) {
				int bx = cx0 + i * 18;
				if (mx >= bx - 1 && mx < bx + 15 && my >= ty - 1 && my < ty + 13) {
					String v = swatchValue(i);
					if (v == null) LmgConfig.get().cosmeticColors.remove(m.id);
					else LmgConfig.get().cosmeticColors.put(m.id, v);
					LmgConfig.save();
					return true;
				}
			}
			int swx0 = cx0 + 56, swx1 = px + PREVIEW_W - 6;
			if (mx >= swx0 && mx < swx1 && my >= ty - 1 && my < ty + 13) {
				String id = m.id;
				minecraft.gui.setScreen(new ColorPickerScreen(this, m.name + " colour", CosmeticRegistry.colorFor(m, 0),
						v -> LmgConfig.get().cosmeticColors.put(id, String.format(java.util.Locale.ROOT, "#%06X", v & 0xFFFFFF)),
						() -> LmgConfig.get().cosmeticColors.remove(id)));
				return true;
			}
			int sl1 = px + PREVIEW_W - 6;
			if (mx >= cx0 - 2 && mx < sl1 + 2 && my >= ty + 15 && my < ty + 25) {
				draggingColour = 1;
				setColourFrom(mx, 1);
				return true;
			}
			if (mx >= cx0 - 2 && mx < sl1 + 2 && my >= ty + 25 && my < ty + 35) {
				draggingColour = 2;
				setColourFrom(mx, 2);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingColour != 0) {
			setColourFrom(event.x(), draggingColour);
			return true;
		}
		if (draggingModel) {
			spin += (float) dx * 2.2f;
			spinVel = (float) dx * 60f;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingColour != 0) LmgConfig.save();
		draggingColour = 0;
		draggingModel = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= gridX && mouseX < gridX + gridW + 8) {
			scroll -= (int) Math.signum(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
