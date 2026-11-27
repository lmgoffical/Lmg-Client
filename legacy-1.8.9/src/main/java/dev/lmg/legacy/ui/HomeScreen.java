package dev.lmg.legacy.ui;

import dev.lmg.legacy.Colors;
import dev.lmg.legacy.Config;
import dev.lmg.legacy.LmgLegacy;
import dev.lmg.legacy.feature.Crosshair;
import dev.lmg.legacy.feature.Hud;
import dev.lmg.legacy.feature.Zoom;
import dev.lmg.legacy.render.CapeLayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Right Shift: the Lmg home. Two wide buttons, then six big tiles - one per settings page. */
public class HomeScreen extends GuiScreen {
	private static final int HEADER = 24;
	private final GuiScreen parent;
	private int W, H, x0, y0, tilesY, tileW;

	private static final String[] TILES = {"HUD", "Zoom", "Crosshair", "Cosmetics", "PvP", "Menu"};
	private static final String[] HINTS = {"FPS, CPS, keys, armour", "amount, speed, scroll", "shape, colour, size",
			"capes, halo, hat, ears", "toggle sprint, fullbright", "title screen, colours"};
	private static final Item[] ICONS = {Items.compass, Items.ender_eye, Items.bow, Items.leather_chestplate, Items.diamond_sword, Items.painting};

	public HomeScreen(GuiScreen parent) {
		this.parent = parent;
	}

	public GuiScreen parent() {
		return parent;
	}

	@Override
	public void initGui() {
		W = Math.min(360, width - 16);
		H = Math.min(230, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		tilesY = y0 + HEADER + 24 + 26;
		tileW = (W - 20 - 12) / 3;
	}

	private int[] tile(int i) {
		int col = i % 3, row = i / 3;
		int tx = x0 + 10 + col * (tileW + 6), ty = tilesY + row * 40;
		return new int[]{tx, ty, tx + tileW, ty + 36};
	}

	private int[] wide(int i) {
		int colW = (W - 30) / 2, by = y0 + HEADER + 24;
		int bx = x0 + 10 + i * (colW + 10);
		return new int[]{bx, by, bx + colW, by + 18};
	}

	@Override
	public void drawScreen(int mx, int my, float pt) {
		if (mc.theWorld == null) drawDefaultBackground();
		else drawGradientRect(0, 0, width, height, 0x90000000, 0xA0000000);
		Ui.round(x0, y0, x0 + W, y0 + H, 4, Ui.BORDER);
		Ui.round(x0 + 1, y0 + 1, x0 + W - 1, y0 + H - 1, 3, Ui.PANEL);
		drawRect(x0 + 8, y0 + HEADER, x0 + W - 8, y0 + HEADER + 1, Ui.BORDER);
		boolean overBack = Ui.inside(mx, my, x0 + 6, y0 + 6, x0 + 50, y0 + 19);
		Ui.text("< Back", x0 + 10, y0 + 9, overBack ? Ui.TEXT : Ui.DIM);
		String name = "Lmg Client ", ver = "1.8.9";
		int tx = x0 + W / 2 - (Ui.width(name) + Ui.width(ver)) / 2;
		Ui.text(name, tx, y0 + 9, Ui.TEXT);
		Ui.text(ver, tx + Ui.width(name), y0 + 9, Ui.GOLD);
		Ui.text("Pick a page. Right Shift closes this.", x0 + 10, y0 + HEADER + 9, Ui.DIM);

		String[] wideText = {"Edit HUD", "Minecraft settings"};
		for (int i = 0; i < 2; i++) {
			int[] r = wide(i);
			boolean over = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
			Ui.round(r[0], r[1] + 1, r[2], r[3] + 1, 3, 0xFF0E1628);
			Ui.round(r[0], r[1], r[2], r[3], 3, over ? 0xFF2D3F64 : 0xFF22304D);
			Ui.centered(wideText[i], (r[0] + r[2]) / 2, r[1] + 5, 0xFFE8EDF5);
		}
		for (int i = 0; i < TILES.length; i++) {
			int[] r = tile(i);
			boolean over = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
			Ui.card(r[0], r[1], r[2], r[3], over, false);
			RenderHelper.enableGUIStandardItemLighting();
			mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(ICONS[i]), r[0] + 7, r[1] + 10);
			RenderHelper.disableStandardItemLighting();
			Ui.text(TILES[i], r[0] + 30, r[1] + 8, Ui.TEXT);
			Ui.text(Ui.fit(HINTS[i], tileW - 36), r[0] + 30, r[1] + 20, Ui.DIM);
		}
		Ui.text("Keys: Options > Controls > Lmg Client.", x0 + 10, y0 + H - 15, Ui.DIM);
		super.drawScreen(mx, my, pt);
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) throws IOException {
		super.mouseClicked(mx, my, button);
		if (button != 0) return;
		if (Ui.inside(mx, my, x0 + 6, y0 + 6, x0 + 50, y0 + 19)) {
			close();
			return;
		}
		int[] a = wide(0), b = wide(1);
		if (Ui.inside(mx, my, a[0], a[1], a[2], a[3])) { mc.displayGuiScreen(new HudEditor(this)); return; }
		if (Ui.inside(mx, my, b[0], b[1], b[2], b[3])) { mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)); return; }
		for (int i = 0; i < TILES.length; i++) {
			int[] r = tile(i);
			if (Ui.inside(mx, my, r[0], r[1], r[2], r[3])) {
				mc.displayGuiScreen(page(this, i));
				return;
			}
		}
	}

	@Override
	protected void keyTyped(char c, int key) throws IOException {
		if (key == 1 || key == dev.lmg.legacy.feature.Keys.MENU.getKeyCode()) {
			close();
			return;
		}
		super.keyTyped(c, key);
	}

	private void close() {
		Config.save();
		mc.displayGuiScreen(parent);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	// ---- the pages ----------------------------------------------------------------------------

	private static Runnable cycle(final int[] ref, final int size, final int dir, final Runnable apply) {
		return new Runnable() {
			public void run() {
				ref[0] = ((ref[0] + dir) % size + size) % size;
				apply.run();
			}
		};
	}

	private static String num(double v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	/** Builds page i. */
	public static GuiScreen page(GuiScreen parent, int i) {
		final Config c = Config.get();
		List<Row> r = new ArrayList<Row>();
		switch (i) {
			case 0: {
				for (final Hud.Element e : Hud.ALL) {
					r.add(Row.toggle(e.name, new Row.Bool() { public boolean get() { return e.on(); } }, new Runnable() { public void run() { e.toggle(); } })
							.hint("Drag it where you want in Edit HUD."));
				}
				r.add(Row.slider("HUD size", () -> c.hudScale, v -> c.hudScale = (float) v, 0.5, 2, 0.05, v -> Math.round(v * 100) + "%"));
				r.add(Row.toggle("Dark boxes behind", () -> c.hudBackground, () -> c.hudBackground ^= true));
				r.add(Row.toggle("Text shadow", () -> c.hudShadow, () -> c.hudShadow ^= true));
				r.add(Row.choice("Text colour", () -> Colors.NAMES[c.hudColor],
						() -> c.hudColor = (c.hudColor + 1) % Colors.NAMES.length,
						() -> c.hudColor = (c.hudColor + Colors.NAMES.length - 1) % Colors.NAMES.length));
				r.add(Row.button("Panel spots", "Reset", () -> c.hudPos.clear()).hint("Puts every panel back where it started."));
				return new PageScreen(parent, "HUD", "What's on your screen while you play.", r, null);
			}
			case 1: {
				r.add(Row.slider("Zoom amount", () -> c.zoomAmount, v -> c.zoomAmount = (float) v, 1.5, 30, 0.5, v -> num(v) + "x closer")
						.hint("How far C zooms. Scroll while zooming to go further."));
				r.add(Row.toggle("Smooth zoom", () -> c.smoothZoom, () -> c.smoothZoom ^= true).hint("Glides in instead of snapping."));
				r.add(Row.slider("Zoom speed", () -> c.zoomSpeed, v -> c.zoomSpeed = (int) Math.round(v), 1, 10, 1, v -> Zoom.speedLabel((int) Math.round(v)))
						.hint("How fast it glides (with Smooth zoom on). Lower is slower."));
				r.add(Row.toggle("Scroll to zoom more", () -> c.zoomScroll, () -> c.zoomScroll ^= true));
				r.add(Row.toggle("Slower mouse while zoomed", () -> c.zoomLowerSensitivity, () -> c.zoomLowerSensitivity ^= true)
						.hint("So aiming doesn't feel twitchy when you're zoomed in."));
				r.add(Row.toggle("Cinematic camera", () -> c.zoomCinematic, () -> c.zoomCinematic ^= true).hint("OptiFine-style smooth camera while zoomed."));
				r.add(Row.note("Hold C to zoom. Change the key in Options > Controls."));
				return new PageScreen(parent, "Zoom", "Like OptiFine zoom, but your amount and speed.", r, null);
			}
			case 2: {
				r.add(Row.choice("Style", () -> Crosshair.STYLES[c.crosshairStyle],
						() -> c.crosshairStyle = (c.crosshairStyle + 1) % Crosshair.STYLES.length,
						() -> c.crosshairStyle = (c.crosshairStyle + Crosshair.STYLES.length - 1) % Crosshair.STYLES.length));
				r.add(Row.choice("Colour", () -> Colors.NAMES[c.crosshairColor],
						() -> c.crosshairColor = (c.crosshairColor + 1) % Colors.NAMES.length,
						() -> c.crosshairColor = (c.crosshairColor + Colors.NAMES.length - 1) % Colors.NAMES.length));
				r.add(Row.slider("Size", () -> c.crosshairSize, v -> c.crosshairSize = (int) Math.round(v), 1, 20, 1, v -> num(v)));
				r.add(Row.slider("Gap", () -> c.crosshairGap, v -> c.crosshairGap = (int) Math.round(v), 0, 10, 1, v -> num(v)));
				r.add(Row.slider("Thickness", () -> c.crosshairThickness, v -> c.crosshairThickness = (int) Math.round(v), 1, 4, 1, v -> num(v)));
				r.add(Row.toggle("Dark outline", () -> c.crosshairOutline, () -> c.crosshairOutline ^= true));
				r.add(Row.toggle("Centre dot", () -> c.crosshairDot, () -> c.crosshairDot ^= true));
				r.add(Row.slider("Spin", () -> c.crosshairSpin, v -> c.crosshairSpin = (int) Math.round(v), 0, 10, 1, v -> v == 0 ? "Off" : num(v)));
				return new PageScreen(parent, "Crosshair", "Pick a shape. The box on the right shows it.", r, new PageScreen.Preview() {
					public void draw(int x0, int y0, int x1, int y1, int mx, int my, float pt) {
						drawGradient(x0, y0, x1, y1);
						if (!Crosshair.custom()) {
							Ui.centered("Minecraft's own", (x0 + x1) / 2, (y0 + y1) / 2 - 4, Ui.DIM);
							return;
						}
						Crosshair.draw((x0 + x1) / 2f, (y0 + y1) / 2f);
					}
				});
			}
			case 3: {
				r.add(Row.choice("Cape", () -> CapeLayer.NAMES[CapeLayer.index(c.cape)],
						() -> c.cape = CapeLayer.CAPES[(CapeLayer.index(c.cape) + 1) % CapeLayer.CAPES.length],
						() -> c.cape = CapeLayer.CAPES[(CapeLayer.index(c.cape) + CapeLayer.CAPES.length - 1) % CapeLayer.CAPES.length])
						.hint("Shows if you don't already have a Minecraft cape. Only you see it."));
				r.add(Row.toggle("Halo", () -> c.halo, () -> c.halo ^= true));
				r.add(Row.toggle("China hat", () -> c.chinaHat, () -> c.chinaHat ^= true));
				r.add(Row.toggle("Cat ears", () -> c.catEars, () -> c.catEars ^= true));
				r.add(Row.choice("Colour", () -> Colors.NAMES[c.cosmeticColor],
						() -> c.cosmeticColor = (c.cosmeticColor + 1) % Colors.NAMES.length,
						() -> c.cosmeticColor = (c.cosmeticColor + Colors.NAMES.length - 1) % Colors.NAMES.length)
						.hint("For the halo, hat and ears."));
				r.add(Row.note("Only you can see these on 1.8.9."));
				return new PageScreen(parent, "Cosmetics", "Capes and things to wear.", r, new PageScreen.Preview() {
					public void draw(int x0, int y0, int x1, int y1, int mx, int my, float pt) {
						net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
						if (mc.thePlayer == null) {
							Ui.centered("Join a world", (x0 + x1) / 2, (y0 + y1) / 2 - 8, Ui.DIM);
							Ui.centered("to see yourself", (x0 + x1) / 2, (y0 + y1) / 2 + 2, Ui.DIM);
							return;
						}
						int cx = (x0 + x1) / 2, base = y1 - 14;
						// slowly turning, so the cape shows
						float turn = (System.currentTimeMillis() % 8000L) / 8000f * 360f;
						float yaw = mc.thePlayer.renderYawOffset, rot = mc.thePlayer.rotationYaw, head = mc.thePlayer.rotationYawHead;
						float pitch = mc.thePlayer.rotationPitch, prevHead = mc.thePlayer.prevRotationYawHead;
						GlStateManager.pushMatrix();
						GlStateManager.translate(cx, base, 50);
						GlStateManager.rotate(turn, 0, 1, 0);
						GlStateManager.translate(-cx, -base, -50);
						GuiInventory.drawEntityOnScreen(cx, base, Math.min(55, (y1 - y0) / 3), 0, 0, mc.thePlayer);
						GlStateManager.popMatrix();
						mc.thePlayer.renderYawOffset = yaw;
						mc.thePlayer.rotationYaw = rot;
						mc.thePlayer.rotationYawHead = head;
						mc.thePlayer.prevRotationYawHead = prevHead;
						mc.thePlayer.rotationPitch = pitch;
					}
				});
			}
			case 4: {
				r.add(Row.toggle("Toggle sprint", () -> c.toggleSprint, () -> c.toggleSprint ^= true)
						.hint("Sprints whenever you walk forward, so you don't hold Ctrl."));
				r.add(Row.toggle("Show sprint status", () -> c.hudSprint, () -> c.hudSprint ^= true).hint("A small line on the HUD while toggle sprint is on."));
				r.add(Row.toggle("Fullbright", () -> c.fullbright, () -> c.fullbright ^= true).hint("See in caves and at night. Your brightness comes back when it's off."));
				r.add(Row.note("Nothing here plays the game for you."));
				return new PageScreen(parent, "PvP", "Small things that help in a fight.", r, null);
			}
			default: {
				r.add(Row.toggle("Lmg title screen", () -> c.lmgMainMenu, () -> c.lmgMainMenu ^= true).hint("Off = Minecraft's normal title screen."));
				r.add(Row.choice("Accent colour", () -> Colors.NAMES[c.accent],
						() -> c.accent = (c.accent + 1) % Colors.NAMES.length,
						() -> c.accent = (c.accent + Colors.NAMES.length - 1) % Colors.NAMES.length));
				r.add(Row.note("Lmg Client " + LmgLegacy.VERSION + " for Minecraft 1.8.9."));
				return new PageScreen(parent, "Menu", "How Lmg's own screens look.", r, null);
			}
		}
	}

	private static void drawGradient(int x0, int y0, int x1, int y1) {
		// a sky-to-grass strip, so the crosshair preview shows on light and dark
		int mid = (y0 + y1) / 2;
		drawRect(x0, y0, x1, mid, 0xFF7FA6E0);
		drawRect(x0, mid, x1, y1, 0xFF4E7A3A);
	}
}
