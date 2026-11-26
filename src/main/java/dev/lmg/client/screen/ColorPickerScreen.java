package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.awt.Color;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Pick any colour: drag the dot in the box for how strong / bright it is, and the slider on the
 * right for the colour itself. Changes show up straight away.
 */
public class ColorPickerScreen extends Screen {
	private static final int W = 250, H = 214, HEADER = 24, BOX_W = 150, BOX_H = 110, HUE_W = 12;
	private static final int[] PRESETS = {0xFFFFFF, 0x9FD8FF, 0x3D9BFF, 0xB79CFF, 0xFF9CD6, 0xFF4D5E, 0xF2C14E, 0x4FD49A, 0xFF8A3D, 0x24222B};

	private final Screen parent;
	private final String title;
	private final IntConsumer onChange;
	private final Runnable onDefault;
	private final BooleanSupplier rainbowGet;
	private final Consumer<Boolean> rainbowSet;
	private final long openedAt = Util.getMillis();
	private float hue, sat, val;
	private int x0, y0;
	private int dragging;   // 0 none, 1 box, 2 hue
	private EditBox hex;
	private boolean settingHex;

	public ColorPickerScreen(Screen parent, String title, int initialRgb, IntConsumer onChange,
							 Runnable onDefault, BooleanSupplier rainbowGet, Consumer<Boolean> rainbowSet) {
		super(Component.literal(title));
		this.parent = parent;
		this.title = title;
		this.onChange = onChange;
		this.onDefault = onDefault;
		this.rainbowGet = rainbowGet;
		this.rainbowSet = rainbowSet;
		setFrom(initialRgb);
	}

	/** Without rainbow. */
	public ColorPickerScreen(Screen parent, String title, int initialRgb, IntConsumer onChange, Runnable onDefault) {
		this(parent, title, initialRgb, onChange, onDefault, null, null);
	}

	private void setFrom(int rgb) {
		float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
		if (hsb[1] > 0.01f && hsb[2] > 0.01f) hue = hsb[0];   // keep the hue for greys/black
		sat = hsb[1];
		val = hsb[2];
	}

	private int rgb() {
		return Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
	}

	private int boxX() { return x0 + 10; }
	private int boxY() { return y0 + HEADER + 10; }
	private int hueX() { return boxX() + BOX_W + 8; }

	@Override
	protected void init() {
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		int rx = hueX() + HUE_W + 10, rw = x0 + W - 10 - rx;
		hex = new EditBox(font, rx, boxY() + 40, rw, 14, Component.literal("Hex"));
		hex.setMaxLength(7);
		hex.setValue(hexOf(rgb()));
		hex.setResponder(v -> {
			if (settingHex) return;
			String s = v.trim().replace("#", "");
			if (s.matches("[0-9a-fA-F]{6}")) {
				setFrom(Integer.parseInt(s, 16));
				changed(false);
			}
		});
		addRenderableWidget(hex);

		int by = y0 + H - 22;
		addRenderableWidget(new SkinButton(x0 + W - 70, by, 60, 14, Component.literal("Done"), openedAt, this::onClose));
		int bx = x0 + 10;
		if (onDefault != null) {
			addRenderableWidget(new SkinButton(bx, by, 60, 14, Component.literal("Default"), openedAt, () -> {
				onDefault.run();
				if (rainbowSet != null) rainbowSet.accept(false);
				LmgConfig.save();
				onClose();
			}));
			bx += 66;
		}
		if (rainbowSet != null) {
			addRenderableWidget(new SkinButton(bx, by, 70, 14,
					Component.literal(rainbowGet.getAsBoolean() ? "Rainbow: on" : "Rainbow: off"), openedAt, () -> {
				rainbowSet.accept(!rainbowGet.getAsBoolean());
				LmgConfig.save();
				rebuildWidgets();
			}));
		}
	}

	private static String hexOf(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	/** Tell the setting, and keep the hex box in step. */
	private void changed(boolean updateHex) {
		if (rainbowSet != null && rainbowGet.getAsBoolean()) rainbowSet.accept(false);
		onChange.accept(rgb());
		if (updateHex && hex != null) {
			settingHex = true;
			hex.setValue(hexOf(rgb()));
			settingHex = false;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick);
			g.fill(0, 0, width, height, 0x55000000);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 220f));
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, title, a);

		// the box: left to right = how strong, top to bottom = how bright
		int bx = boxX(), by = boxY();
		int pure = Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;
		for (int i = 0; i < BOX_W; i++) {
			float s = i / (float) (BOX_W - 1);
			int top = Draw.lerpRgb(0xFFFFFF, pure, s);
			g.fillGradient(bx + i, by, bx + i + 1, by + BOX_H, Draw.withAlpha(top, a), Draw.withAlpha(0x000000, a));
		}
		int dx = bx + Math.round(sat * (BOX_W - 1)), dy = by + Math.round((1 - val) * (BOX_H - 1));
		ring(g, dx, dy, val > 0.6f && sat < 0.4f ? 0xFF000000 : 0xFFFFFFFF);

		// the hue slider
		int hx = hueX();
		for (int j = 0; j < BOX_H; j++) {
			int c = Color.HSBtoRGB(j / (float) (BOX_H - 1), 1f, 1f) & 0xFFFFFF;
			g.fill(hx, by + j, hx + HUE_W, by + j + 1, Draw.withAlpha(c, a));
		}
		int hy = by + Math.round(hue * (BOX_H - 1));
		g.fill(hx - 2, hy - 1, hx + HUE_W + 2, hy + 2, 0xFF000000);
		g.fill(hx - 1, hy, hx + HUE_W + 1, hy + 1, 0xFFFFFFFF);

		// preview and presets
		int rx = hx + HUE_W + 10, rw = x0 + W - 10 - rx;
		boolean rainbow = rainbowGet != null && rainbowGet.getAsBoolean();
		if (rainbow) {
			for (int i = 0; i < rw; i++) {
				int c = Color.HSBtoRGB((i / (float) rw + Util.getMillis() / 4000f) % 1f, 0.6f, 1f) & 0xFFFFFF;
				g.fill(rx + i, by, rx + i + 1, by + 32, Draw.withAlpha(c, a));
			}
		} else {
			Draw.roundRect(g, rx, by, rx + rw, by + 32, 4, Draw.withAlpha(rgb(), a));
		}
		int py = by + BOX_H + 10;
		g.text(font, "Quick picks", bx, py, Draw.withAlpha(0x7C8BA3, a), false);
		for (int i = 0; i < PRESETS.length; i++) {
			int sx = bx + i * 18, sy = py + 11;
			boolean on = !rainbow && (rgb() & 0xF0F0F0) == (PRESETS[i] & 0xF0F0F0);
			Draw.roundRect(g, sx - 1, sy - 1, sx + 15, sy + 15, 3, on ? Draw.withAlpha(MenuSkin.accent(), a) : Draw.withAlpha(0x1C2943, a));
			Draw.roundRect(g, sx, sy, sx + 14, sy + 14, 2, Draw.withAlpha(PRESETS[i], a));
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private static void ring(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x - 3, y - 1, x - 2, y + 2, color);
		g.fill(x + 2, y - 1, x + 3, y + 2, color);
		g.fill(x - 1, y - 3, x + 2, y - 2, color);
		g.fill(x - 1, y + 2, x + 2, y + 3, color);
		g.fill(x - 2, y - 2, x - 1, y - 1, color);
		g.fill(x + 1, y - 2, x + 2, y - 1, color);
		g.fill(x - 2, y + 1, x - 1, y + 2, color);
		g.fill(x + 1, y + 1, x + 2, y + 2, color);
	}

	private void dragTo(double mx, double my) {
		if (dragging == 1) {
			sat = (float) Math.max(0, Math.min(1, (mx - boxX()) / (BOX_W - 1)));
			val = (float) Math.max(0, Math.min(1, 1 - (my - boxY()) / (BOX_H - 1)));
		} else if (dragging == 2) {
			hue = (float) Math.max(0, Math.min(0.999, (my - boxY()) / (BOX_H - 1)));
			if (sat < 0.05f) sat = 1f;      // picking a hue on grey: make it colourful
			if (val < 0.05f) val = 1f;
		}
		changed(true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		int bx = boxX(), by = boxY();
		if (mx >= bx && mx < bx + BOX_W && my >= by && my < by + BOX_H) {
			dragging = 1;
			dragTo(mx, my);
			return true;
		}
		int hx = hueX();
		if (mx >= hx - 2 && mx < hx + HUE_W + 2 && my >= by && my < by + BOX_H) {
			dragging = 2;
			dragTo(mx, my);
			return true;
		}
		int py = by + BOX_H + 21;
		for (int i = 0; i < PRESETS.length; i++) {
			int sx = bx + i * 18;
			if (mx >= sx && mx < sx + 14 && my >= py && my < py + 14) {
				setFrom(PRESETS[i]);
				changed(true);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != 0) {
			dragTo(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != 0) LmgConfig.save();
		dragging = 0;
		return super.mouseReleased(event);
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
