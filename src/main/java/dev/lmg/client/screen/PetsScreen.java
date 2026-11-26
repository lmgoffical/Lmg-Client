package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.pet.PetConfig;
import dev.lmg.client.pet.PetType;
import dev.lmg.client.pet.Pets;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Pets: up to three, each one a Minecraft animal or a mini player. Pick the kind, name it,
 * choose where it goes (follow you, left/right shoulder, on your head), and how big it is.
 */
public class PetsScreen extends Screen {
	private static final int HEADER = 24, LEFT_W = 112, PREVIEW_W = 124, ICON = 20;
	private static int selected = 0;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int W, H, x0, y0, midX, midW;
	private EditBox nameBox, playerBox;
	private float spin;
	private String typedPlayer;   // waits until you stop typing before looking the skin up
	private long typedAt;
	private long lastFrame = -1;

	public PetsScreen(Screen parent) {
		super(Component.literal("Pets"));
		this.parent = parent;
	}

	private static List<PetConfig> pets() {
		return LmgConfig.get().pets;
	}

	private PetConfig current() {
		List<PetConfig> list = pets();
		if (list.isEmpty()) return null;
		selected = Math.max(0, Math.min(selected, list.size() - 1));
		return list.get(selected);
	}

	@Override
	protected void init() {
		W = Math.min(440, width - 16);
		H = Math.min(268, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		midX = x0 + LEFT_W + 14;
		midW = W - LEFT_W - PREVIEW_W - 30;
		addRenderableWidget(new SkinButton(x0 + 6, y0 + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));

		// left: your pets
		int ly = y0 + HEADER + 22 + pets().size() * 40;
		if (pets().size() < Pets.MAX) {
			addRenderableWidget(new SkinButton(x0 + 8, ly, LEFT_W - 8, 14, Component.literal("+ Add a pet"), openedAt, () -> {
				PetConfig p = new PetConfig();
				p.name = "";
				pets().add(p);
				selected = pets().size() - 1;
				LmgConfig.save();
				rebuildWidgets();
			}));
		}

		PetConfig pet = current();
		if (pet == null) return;
		PetType type = pet.type();

		// middle: settings for the selected pet (under the type grid)
		int rows = (PetType.values().length + perRow() - 1) / perRow();
		int y = y0 + HEADER + 18 + rows * ICON + 8;
		nameBox = new EditBox(font, midX, y, midW, 14, Component.literal("Name"));
		nameBox.setMaxLength(24);
		nameBox.setHint(Component.literal("Name it (shows above it)"));
		nameBox.setValue(pet.name);
		nameBox.setResponder(v -> { pet.name = v; LmgConfig.save(); });
		addRenderableWidget(nameBox);
		y += 18;

		long t = openedAt;
		y = row(SettingRow.choice("Where", () -> PetConfig.MODE_LABELS[pet.modeIndex()],
				() -> { pet.mode = PetConfig.MODES[(pet.modeIndex() + 1) % PetConfig.MODES.length]; LmgConfig.save(); },
				() -> { pet.mode = PetConfig.MODES[Math.floorMod(pet.modeIndex() - 1, PetConfig.MODES.length)]; LmgConfig.save(); }), y, t);
		y = row(SettingRow.choice("Size", () -> PetConfig.SIZE_LABELS[pet.size],
				() -> { pet.size = (pet.size + 1) % PetConfig.SIZES.length; LmgConfig.save(); },
				() -> { pet.size = Math.floorMod(pet.size - 1, PetConfig.SIZES.length); LmgConfig.save(); }), y, t);

		if (type == PetType.MINI_PLAYER) {
			y = row(SettingRow.choice("Whose skin", () -> pet.mine ? "Mine" : "Someone else",
					() -> { pet.mine = !pet.mine; LmgConfig.save(); rebuildWidgets(); },
					() -> { pet.mine = !pet.mine; LmgConfig.save(); rebuildWidgets(); }), y, t);
			if (pet.mine) {
				y = row(SettingRow.toggle("Wear my cosmetics", () -> pet.cosmetics, v -> pet.cosmetics = v), y, t);
			} else {
				playerBox = new EditBox(font, midX, y, midW, 14, Component.literal("Player name"));
				playerBox.setMaxLength(16);
				playerBox.setHint(Component.literal("Their Minecraft username"));
				playerBox.setValue(pet.player);
				playerBox.setResponder(v -> {
					typedPlayer = v.trim();
					typedAt = Util.getMillis();
				});
				addRenderableWidget(playerBox);
				y += 30;
			}
		} else {
			y = row(SettingRow.toggle("Baby", () -> pet.baby, v -> pet.baby = v), y, t);
			int count = Pets.variantCount(type);
			if (count > 1) {
				y = row(SettingRow.choice("Look", () -> Pets.variantName(type, pet.variant),
						() -> { pet.variant = Math.floorMod(pet.variant + 1, count); LmgConfig.save(); },
						() -> { pet.variant = Math.floorMod(pet.variant - 1, count); LmgConfig.save(); }), y, t);
			}
			if (type == PetType.CAT || type == PetType.WOLF) {
				y = row(SettingRow.choice("Collar", () -> pretty(DyeColor.byId(pet.collar).getName()),
						() -> { pet.collar = (pet.collar + 1) % 16; LmgConfig.save(); },
						() -> { pet.collar = Math.floorMod(pet.collar - 1, 16); LmgConfig.save(); },
						() -> DyeColor.byId(pet.collar).getTextureDiffuseColor() & 0xFFFFFF), y, t);
			}
		}

		// right: on/off and remove
		int px = x0 + W - PREVIEW_W - 6;
		int by = y0 + H - 40;
		addRenderableWidget(new SkinButton(px, by, PREVIEW_W, 14, Component.literal(pet.enabled ? "Showing - hide it" : "Hidden - show it"), openedAt, () -> {
			pet.enabled = !pet.enabled;
			LmgConfig.save();
			rebuildWidgets();
		}));
		addRenderableWidget(new SkinButton(px, by + 18, PREVIEW_W, 14, Component.literal("Remove this pet"), openedAt, () -> {
			pets().remove(pet);
			selected = Math.max(0, selected - 1);
			LmgConfig.save();
			rebuildWidgets();
		}));
	}

	private int row(SettingRow r, int y, long t) {
		addRenderableWidget(r.at(midX - 4, y, midW + 8, 14, t));
		return y + 16;
	}

	private int perRow() {
		return Math.max(1, midW / ICON);
	}

	private static String pretty(String s) {
		s = s.replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ---- drawing ---------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick);
			g.fill(0, 0, width, height, 0x66000000);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		spin += dt * 30f;
		PetConfig editing = current();
		if (typedPlayer != null && now - typedAt > 900 && editing != null) {
			editing.player = typedPlayer;
			typedPlayer = null;
			LmgConfig.save();
		}
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 300f));
		CosmeticsScreen.Pal p = CosmeticsScreen.pal();
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "Pets", a);

		// left column: your pets
		g.text(font, "Your pets (" + pets().size() + "/" + Pets.MAX + ")", x0 + 10, y0 + HEADER + 8, Draw.withAlpha(p.dim(), a), false);
		for (int i = 0; i < pets().size(); i++) {
			PetConfig pet = pets().get(i);
			int cx = x0 + 8, cy = y0 + HEADER + 20 + i * 40, cw = LEFT_W - 8, ch = 36;
			boolean sel = i == selected;
			boolean over = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
			Draw.roundRect(g, cx, cy, cx + cw, cy + ch, 5, sel ? Draw.withAlpha(p.accent(), a) : Draw.fade(p.border(), a));
			Draw.roundRect(g, cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 4, Draw.fade(over ? p.cardHover() : p.card(), a));
			g.item(new ItemStack(pet.type().icon), cx + 6, cy + 10);
			String title = pet.name.isBlank() ? pet.type().label : pet.name;
			if (font.width(title) > cw - 34) title = font.plainSubstrByWidth(title, cw - 40) + "..";
			g.text(font, title, cx + 28, cy + 7, Draw.withAlpha(pet.enabled ? p.text() : p.dim(), a), false);
			String where = pet.enabled ? PetConfig.MODE_LABELS[pet.modeIndex()] : "hidden";
			g.text(font, where, cx + 28, cy + 19, Draw.withAlpha(p.dim(), a), false);
		}
		if (pets().isEmpty()) {
			int yy = y0 + HEADER + 40;
			for (String line : new String[]{"No pets yet.", "Press + Add a pet.", "", "They follow you, ride", "on your shoulder or", "sit on your head."}) {
				g.text(font, line, x0 + 12, yy, Draw.withAlpha(p.dim(), a), false);
				yy += 10;
			}
		}
		g.fill(x0 + LEFT_W + 6, y0 + HEADER + 6, x0 + LEFT_W + 7, y0 + H - 8, Draw.fade(p.border(), a));

		PetConfig pet = current();
		if (pet != null) {
			// middle: what kind of pet
			g.text(font, "Pick a pet", midX, y0 + HEADER + 8, Draw.withAlpha(p.text(), a), false);
			PetType[] types = PetType.values();
			PetType hovered = null;
			for (int i = 0; i < types.length; i++) {
				int ix = midX + (i % perRow()) * ICON, iy = y0 + HEADER + 18 + (i / perRow()) * ICON;
				boolean sel = types[i] == pet.type();
				boolean over = mouseX >= ix && mouseX < ix + ICON - 2 && mouseY >= iy && mouseY < iy + ICON - 2;
				if (over) hovered = types[i];
				if (sel || over) Draw.roundRect(g, ix - 1, iy - 1, ix + ICON - 1, iy + ICON - 1, 3,
						sel ? Draw.withAlpha(p.accent(), 0.35f * a) : Draw.fade(p.cardHover(), a));
				g.item(new ItemStack(types[i].icon), ix + 1, iy + 1);
			}
			String label = (hovered != null ? hovered : pet.type()).label;
			g.text(font, label, midX + midW - font.width(label), y0 + HEADER + 8, Draw.withAlpha(p.accent(), a), false);

			if (pet.type() == PetType.MINI_PLAYER && !pet.mine && playerBox != null) {
				String status;
				int color = p.dim();
				if (typedPlayer != null) status = "...";
				else if (pet.player.isBlank()) status = "Type a username, e.g. your friend's.";
				else if (Pets.PetSkins.problem(pet.player) != null) {
					status = Pets.PetSkins.problem(pet.player);
					color = 0xFF8C7C;
				} else if (Pets.PetSkins.get(pet.player) != null) {
					status = "Got " + pet.player + "'s skin.";
					color = 0x7CFF9E;
				} else status = "Looking up " + pet.player + "...";
				g.text(font, status, midX, playerBox.getY() + 17, Draw.withAlpha(color, a), false);
			}
		}

		// right: preview
		int px = x0 + W - PREVIEW_W - 6, py = y0 + HEADER + 6, pw = PREVIEW_W, ph = 130;
		Draw.roundRect(g, px, py, px + pw, py + ph, 5, Draw.fade(p.sidebar(), a));
		if (pet != null && minecraft.level != null) {
			drawPet(g, pet, px + 2, py + 2, px + pw - 2, py + ph - 2);
		} else {
			g.centeredText(font, pet == null ? "Add a pet to see it" : "Join a world", px + pw / 2, py + ph / 2 - 8, Draw.withAlpha(p.dim(), a));
			if (pet != null) g.centeredText(font, "to see a preview", px + pw / 2, py + ph / 2 + 3, Draw.withAlpha(p.dim(), a));
		}
		g.text(font, "Only you can see", px + 2, py + ph + 6, Draw.withAlpha(p.dim(), a), false);
		g.text(font, "your pets.", px + 2, py + ph + 16, Draw.withAlpha(p.dim(), a), false);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void drawPet(GuiGraphicsExtractor g, PetConfig pet, int x1, int y1, int x2, int y2) {
		EntityRenderState state = Pets.preview(pet);
		if (state == null) return;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180f + (float) Math.sin(Math.toRadians(spin)) * 35f + 25f;
			living.yRot = 0f;
			living.xRot = 0f;
			living.scale = 1f;
		}
		state.shadowPieces.clear();
		state.nameTag = null;
		float h = Math.max(0.3f, state.boundingBoxHeight), w = Math.max(0.3f, state.boundingBoxWidth);
		float pixels = Math.min(80f / h, 80f / w);
		Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(-8));
		Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
		g.entity(state, pixels, new Vector3f(0f, h / 2f, 0f), rot, tilt, x1, y1, x2, y2);
	}

	// ---- input -----------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		for (int i = 0; i < pets().size(); i++) {
			int cx = x0 + 8, cy = y0 + HEADER + 20 + i * 40;
			if (mx >= cx && mx < cx + LEFT_W - 8 && my >= cy && my < cy + 36) {
				selected = i;
				rebuildWidgets();
				return true;
			}
		}
		PetConfig pet = current();
		if (pet != null) {
			PetType[] types = PetType.values();
			for (int i = 0; i < types.length; i++) {
				int ix = midX + (i % perRow()) * ICON, iy = y0 + HEADER + 18 + (i / perRow()) * ICON;
				if (mx >= ix && mx < ix + ICON - 2 && my >= iy && my < iy + ICON - 2) {
					if (pet.type() != types[i]) {
						pet.type = types[i].name();
						pet.variant = 0;
						LmgConfig.save();
						rebuildWidgets();
					}
					return true;
				}
			}
		}
		return false;
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
