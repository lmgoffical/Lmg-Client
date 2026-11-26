package dev.lmg.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import dev.lmg.client.feature.PhotoMode;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Photo mode (F8). The world pauses in singleplayer. Fly the camera, pick a filter and a frame,
 * and press Enter: the buttons hide for one frame, the shot is saved to your screenshots folder,
 * and a little preview pops up in the corner.
 */
public class PhotoModeScreen extends Screen {
	private static final int PANEL_W = 150;
	private static final Identifier VIGNETTE = LmgClient.id("photo/vignette");
	private static final Identifier GRAIN = LmgClient.id("photo/grain");
	private static boolean texturesMade;

	private final long openedAt = Util.getMillis();
	private final Random random = new Random();
	private boolean panelHidden;
	/** 0 = nothing, 1 = hiding the buttons this frame, 2 = grab the finished frame now. */
	private int capture;
	private long flashAt;
	private String dragging;
	private int panelBottom;

	/** A control in the panel. */
	private record Control(String label, java.util.function.Supplier<String> value, Kind kind,
						   java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set, float min, float max,
						   Runnable next, Runnable prev, int swatch) {}

	private enum Kind { CHOICE, SLIDER, TOGGLE }

	private final List<Control> controls = new ArrayList<>();
	private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}
	private final List<Hit> hits = new ArrayList<>();

	public PhotoModeScreen() {
		super(Component.literal("Photo mode"));
	}

	@Override
	protected void init() {
		if (!PhotoMode.active()) {
			PhotoMode.start();
		}
		makeTextures();
		buildControls();
	}

	/** 0 = the look (filter, focus, frame...), 1 = the camera (zoom, timer, pose...). */
	private static int tab;
	private long countdownEnd;
	private int lastBeep = -1;

	private void buildControls() {
		controls.clear();
		if (tab == 0) {
			controls.add(new Control("Look", () -> PhotoMode.look() == null ? "Your mix" : PhotoMode.look().label, Kind.CHOICE, null, null, 0, 0,
					() -> lookStep(1), () -> lookStep(-1), 0));
			controls.add(new Control("Filter", () -> PhotoMode.filter.label, Kind.CHOICE, null, null, 0, 0,
					() -> PhotoMode.filter = cycle(PhotoMode.Filter.values(), PhotoMode.filter, 1),
					() -> PhotoMode.filter = cycle(PhotoMode.Filter.values(), PhotoMode.filter, -1), -1));
			controls.add(new Control("Focus", () -> PhotoMode.focus.label, Kind.CHOICE, null, null, 0, 0,
					() -> PhotoMode.focus = cycle(PhotoMode.Focus.values(), PhotoMode.focus, 1),
					() -> PhotoMode.focus = cycle(PhotoMode.Focus.values(), PhotoMode.focus, -1), 0));
			controls.add(new Control("Vignette", () -> Math.round(PhotoMode.vignette * 100) + "%", Kind.SLIDER,
					() -> PhotoMode.vignette, v -> PhotoMode.vignette = (float) v, 0, 1, null, null, 0));
			controls.add(new Control("Film grain", () -> "", Kind.TOGGLE, () -> PhotoMode.grain ? 1 : 0,
					v -> PhotoMode.grain = !PhotoMode.grain, 0, 0, null, null, 0));
			controls.add(new Control("Frame", () -> PhotoMode.frame.label, Kind.CHOICE, null, null, 0, 0,
					() -> PhotoMode.frame = cycle(PhotoMode.Frame.values(), PhotoMode.frame, 1),
					() -> PhotoMode.frame = cycle(PhotoMode.Frame.values(), PhotoMode.frame, -1), 0));
		} else {
			controls.add(new Control("Zoom", () -> Math.round(PhotoMode.fov) + "°", Kind.SLIDER,
					() -> 120 - PhotoMode.fov, v -> PhotoMode.fov = (float) (120 - v), 10, 110, null, null, 0));
			controls.add(new Control("Tilt", () -> Math.round(PhotoMode.roll) + "°", Kind.SLIDER,
					() -> PhotoMode.roll, v -> PhotoMode.roll = Math.abs(v) < 1.2 ? 0f : (float) v, -30, 30, null, null, 0));
			controls.add(new Control("Timer", () -> PhotoMode.TIMERS[PhotoMode.timer] == 0 ? "Off" : PhotoMode.TIMERS[PhotoMode.timer] + " s", Kind.CHOICE, null, null, 0, 0,
					() -> PhotoMode.timer = (PhotoMode.timer + 1) % PhotoMode.TIMERS.length,
					() -> PhotoMode.timer = Math.floorMod(PhotoMode.timer - 1, PhotoMode.TIMERS.length), 0));
			controls.add(new Control("Pose", () -> PhotoMode.pose == null ? "None" : PhotoMode.pose.label, Kind.CHOICE, null, null, 0, 0,
					() -> poseStep(1), () -> poseStep(-1), 0));
			controls.add(new Control("Grid", () -> "", Kind.TOGGLE, () -> PhotoMode.grid ? 1 : 0,
					v -> PhotoMode.grid = !PhotoMode.grid, 0, 0, null, null, 0));
			controls.add(new Control("Hide me", () -> "", Kind.TOGGLE, () -> PhotoMode.hideMe ? 1 : 0,
					v -> PhotoMode.hideMe = !PhotoMode.hideMe, 0, 0, null, null, 0));
		}
	}

	private static void lookStep(int dir) {
		PhotoMode.Look cur = PhotoMode.look();
		PhotoMode.Look[] all = PhotoMode.Look.values();
		int i = cur == null ? (dir > 0 ? 0 : all.length - 1) : Math.floorMod(cur.ordinal() + dir, all.length);
		all[i].apply();
	}

	/** None -> Wave -> Hug -> ... : your character strikes the pose for the shot. */
	private static void poseStep(int dir) {
		var all = dev.lmg.client.emote.Emote.values();
		int i = PhotoMode.pose == null ? -1 : PhotoMode.pose.ordinal();
		i = Math.floorMod(i + 1 + dir, all.length + 1) - 1;
		PhotoMode.pose = i < 0 ? null : all[i];
		if (PhotoMode.pose != null) dev.lmg.client.emote.Emotes.playQuiet(PhotoMode.pose);
		else dev.lmg.client.emote.Emotes.stopMine();
	}

	private static <E extends Enum<E>> E cycle(E[] values, E cur, int dir) {
		return values[Math.floorMod(cur.ordinal() + dir, values.length)];
	}

	/** A soft black vignette and a grain tile, made once. */
	private void makeTextures() {
		if (texturesMade) return;
		texturesMade = true;
		NativeImage v = new NativeImage(128, 128, false);
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				double dx = (x + 0.5) / 64.0 - 1, dy = (y + 0.5) / 64.0 - 1;
				double d = Math.sqrt(dx * dx + dy * dy) / Math.sqrt(2);
				double t = Mth.clamp((d - 0.32) / 0.68, 0, 1);
				int a = (int) Math.round(Math.pow(t * t * (3 - 2 * t), 1.25) * 255);
				v.setPixel(x, y, a << 24);
			}
		}
		minecraft.getTextureManager().register(VIGNETTE, new DynamicTexture(() -> "Lmg vignette", v));
		NativeImage g = new NativeImage(128, 128, false);
		Random r = new Random(7);
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				int l = r.nextInt(256);
				int a = 8 + r.nextInt(20);
				g.setPixel(x, y, a << 24 | l << 16 | l << 8 | l);
			}
		}
		minecraft.getTextureManager().register(GRAIN, new DynamicTexture(() -> "Lmg grain", g));
	}

	// ---- drawing ---------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// nothing: the world is the picture
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (capture == 2) {
			// the last frame had no buttons: save it
			capture = 0;
			PhotoMode.grab(() -> {});
			flashAt = Util.getMillis();
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.35f, 0.8f));
		}
		hits.clear();
		drawOverlays(g);
		if (capture == 1) {
			capture = 2;
			return;
		}

		// flash after a photo
		float flash = 1f - (Util.getMillis() - flashAt) / 260f;
		if (flash > 0) g.fill(0, 0, width, height, Draw.withAlpha(0xFFFFFF, 0.55f * flash * flash));

		// the rule-of-thirds grid (never in the photo itself)
		if (PhotoMode.grid) {
			int gc = Draw.withAlpha(0xFFFFFF, 0.28f);
			for (int i = 1; i <= 2; i++) {
				g.fill(width * i / 3, 0, width * i / 3 + 1, height, gc);
				g.fill(0, height * i / 3, width, height * i / 3 + 1, gc);
			}
		}
		// the self-timer
		if (countdownEnd != 0) {
			long left = countdownEnd - Util.getMillis();
			if (left <= 0) {
				countdownEnd = 0;
				capture = 1;
			} else {
				int secsLeft = (int) Math.ceil(left / 1000.0);
				float frac = (left % 1000) / 1000f;
				Draw.scaledText(g, font, String.valueOf(secsLeft), width / 2f, height / 2f - 12, 3f + frac,
						Draw.withAlpha(0xFFFFFF, 0.35f + 0.6f * frac));
				if (secsLeft != lastBeep) {
					lastBeep = secsLeft;
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), secsLeft <= 1 ? 1.6f : 1.2f, 0.6f));
				}
			}
		}
		if (!panelHidden && countdownEnd == 0) drawPanel(g, mouseX, mouseY);
		// how to move, in a small bar at the bottom
		String t = !panelHidden && (PhotoMode.focus == PhotoMode.Focus.SOFT || PhotoMode.focus == PhotoMode.Focus.STRONG)
				? "Focus: whatever is in the middle stays sharp  -  turn the camera to pick it"
				: panelHidden ? "H shows the controls  -  Enter takes the photo"
				: PhotoMode.orbit() ? "Drag to circle  -  Scroll to zoom  -  Ctrl+scroll: distance  -  H hides  -  Esc leaves"
				: "WASD fly  -  Space / Shift up, down  -  Drag to look  -  Scroll to zoom  -  H hides";
		int tw = font.width(t), cx = panelHidden ? width / 2 : (8 + PANEL_W + width) / 2;
		if (!panelHidden && tw > width - PANEL_W - 30) t = font.plainSubstrByWidth(t, width - PANEL_W - 40) + "..";
		tw = font.width(t);
		Draw.roundRect(g, cx - tw / 2 - 8, height - 22, cx + tw / 2 + 8, height - 8, 5, 0x990C1322);
		g.centeredText(font, t, cx, height - 18, 0xFFC9D3E3);
		drawLastPhoto(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	/** Vignette, grain and frame: these are part of the photo. */
	private void drawOverlays(GuiGraphicsExtractor g) {
		if (PhotoMode.vignette > 0.01f) {
			g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, width, height, 128, 128, 128, 128,
					Draw.withAlpha(0xFFFFFF, Math.min(1f, PhotoMode.vignette * 1.15f)));
		}
		if (PhotoMode.grain) {
			int ox = random.nextInt(128), oy = random.nextInt(128);
			for (int y = -oy; y < height; y += 128) {
				for (int x = -ox; x < width; x += 128) {
					g.blit(RenderPipelines.GUI_TEXTURED, GRAIN, x, y, 0f, 0f, 128, 128, 128, 128, 128, 128, 0xFFFFFFFF);
				}
			}
		}
		switch (PhotoMode.frame) {
			case CINEMA -> {
				int bar = Math.max(0, (height - Math.round(width / 2.39f)) / 2);
				g.fill(0, 0, width, bar, 0xFF000000);
				g.fill(0, height - bar, width, height, 0xFF000000);
			}
			case POLAROID -> {
				int side = Math.max(6, Math.round(height * 0.045f)), bottom = Math.round(height * 0.17f);
				int paper = 0xFFF5F1E8;
				g.fill(0, 0, width, side, paper);
				g.fill(0, height - bottom, width, height, paper);
				g.fill(0, side, side, height - bottom, paper);
				g.fill(width - side, side, width, height - bottom, paper);
				g.fill(side, side, width - side, side + 1, 0x22000000);
				String date = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(new Date()).toLowerCase(Locale.ROOT);
				Draw.scaledText(g, font, date, width - side - font.width(date) * 0.65f, height - bottom / 2f - 3, 1.3f, 0xFF6B6459);
			}
			case FILM -> {
				int bar = Math.max(10, Math.round(height * 0.085f));
				g.fill(0, 0, width, bar, 0xFF111111);
				g.fill(0, height - bar, width, height, 0xFF111111);
				int hole = Math.max(3, bar / 3);
				for (int x = 6; x < width; x += hole * 3) {
					Draw.roundRect(g, x, bar / 2 - hole / 2, x + hole + 1, bar / 2 + hole / 2 + 1, 1, 0xFFD9D4C7);
					Draw.roundRect(g, x, height - bar / 2 - hole / 2 - 1, x + hole + 1, height - bar / 2 + hole / 2, 1, 0xFFD9D4C7);
				}
				Draw.scaledText(g, font, "LMG 400", width - 40, height - bar - 9, 0.75f, 0xFFF2A13B);
			}
			default -> {
			}
		}
	}

	private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 260f));
		int x0 = 8, y0 = 8, x1 = x0 + PANEL_W;
		int rowH = Math.max(18, Math.min(22, (height - 16 - 112) / controls.size()));
		int y1 = Math.min(height - 8, y0 + 56 + controls.size() * rowH + 50);
		panelBottom = y1;
		int primary = Theme.primary();
		Draw.roundRect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 7, Draw.withAlpha(0x1C2943, 0.9f * a));
		Draw.roundRect(g, x0, y0, x1, y1, 6, Draw.withAlpha(0x0C1322, 0.9f * a));
		g.text(font, "Photo mode", x0 + 10, y0 + 9, Draw.white(a), false);
		String sub = PhotoMode.orbit() ? "circling you (server-safe)" : minecraft.isLocalServer() ? "the world is paused" : "the world keeps going";
		g.text(font, sub, x0 + 10, y0 + 20, Draw.withAlpha(0x7C8BA3, a), false);
		g.fill(x0 + 8, y0 + 32, x1 - 8, y0 + 33, Draw.withAlpha(0x1C2943, a));
		// two tabs: the look, and the camera
		String[] tabs = {"Look", "Camera"};
		int tw = (PANEL_W - 20) / 2;
		for (int i = 0; i < 2; i++) {
			int tx = x0 + 8 + i * (tw + 4), ty = y0 + 37;
			boolean sel = tab == i, over = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + 13;
			Draw.roundRect(g, tx, ty, tx + tw, ty + 13, 4, Draw.withAlpha(sel ? primary : 0x1C2943, (sel ? 0.9f : over ? 0.9f : 0.6f) * a));
			g.centeredText(font, tabs[i], tx + tw / 2, ty + 3, Draw.withAlpha(sel ? Theme.textOn(primary) : 0xC9D3E3, a));
			final int index = i;
			hits.add(new Hit(tx, ty, tx + tw, ty + 13, () -> {
				tab = index;
				buildControls();
			}));
		}

		int y = y0 + 56;
		for (Control c : controls) {
			drawControl(g, c, x0 + 8, y, x1 - 8, rowH, mouseX, mouseY, a, primary);
			y += rowH;
		}

		// the main button
		int by = y + 6;
		boolean overShot = mouseX >= x0 + 8 && mouseX < x1 - 8 && mouseY >= by && mouseY < by + 18;
		PillButton.draw(g, font, x0 + 8, by, PANEL_W - 16, 18, "Take photo", true, overShot ? 1f : 0f, a);
		hits.add(new Hit(x0 + 8, by, x1 - 8, by + 18, this::takePhoto));
		g.text(font, "Enter", x1 - 14 - font.width("Enter"), by + 5, Draw.withAlpha(Theme.textOn(primary), 0.55f * a), false);
		int ry = by + 23;
		boolean overReset = mouseX >= x0 + 8 && mouseX < x1 - 8 && mouseY >= ry && mouseY < ry + 14;
		PillButton.draw(g, font, x0 + 8, ry, PANEL_W - 16, 14, "Reset camera", false, overReset ? 1f : 0f, a);
		hits.add(new Hit(x0 + 8, ry, x1 - 8, ry + 14, PhotoMode::resetCamera));

	}

	private void drawControl(GuiGraphicsExtractor g, Control c, int x0, int y, int x1, int h, int mouseX, int mouseY, float a, int primary) {
		boolean over = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + h - 2;
		if (over) Draw.roundRect(g, x0 - 2, y, x1 + 2, y + h - 2, 4, Draw.white(0.04f * a));
		int ty = y + (h - 2 - 8) / 2;
		switch (c.kind) {
			case CHOICE -> {
				g.text(font, c.label, x0 + 2, ty, Draw.withAlpha(0xA3B0C4, a), false);
				String v = c.value.get();
				int right = x1 - 2;
				int nx = right - font.width(">");
				g.text(font, ">", nx, ty, Draw.withAlpha(0x7C8BA3, a), false);
				int vx = nx - 4 - font.width(v);
				g.text(font, v, vx, ty, Draw.white(a), false);
				int px = vx - 4 - font.width("<");
				g.text(font, "<", px, ty, Draw.withAlpha(0x7C8BA3, a), false);
				if (c.swatch < 0) {
					Draw.roundRect(g, px - 10, ty, px - 3, ty + 7, 2, Draw.withAlpha(PhotoMode.filter.swatch, a));
				}
				int mid = (px + vx) / 2;
				hits.add(new Hit(x0, y, mid, y + h - 2, c.prev));
				hits.add(new Hit(mid, y, x1, y + h - 2, c.next));
			}
			case TOGGLE -> {
				g.text(font, c.label, x0 + 2, ty, Draw.withAlpha(0xA3B0C4, a), false);
				boolean on = c.get.getAsDouble() > 0.5;
				int tx = x1 - 20, kyy = y + (h - 2 - 10) / 2;
				Draw.roundRect(g, tx, kyy, tx + 18, kyy + 10, 5, Draw.withAlpha(on ? primary : 0x3A3A46, a));
				int kx = on ? tx + 9 : tx + 1;
				Draw.roundRect(g, kx, kyy + 1, kx + 8, kyy + 9, 4, Draw.white(a));
				hits.add(new Hit(x0, y, x1, y + h - 2, () -> c.set.accept(0)));
			}
			case SLIDER -> {
				g.text(font, c.label, x0 + 2, ty - 3, Draw.withAlpha(0xA3B0C4, a), false);
				String v = c.value.get();
				g.text(font, v, x1 - 2 - font.width(v), ty - 3, Draw.white(a), false);
				int sx0 = x0 + 2, sx1 = x1 - 2, sy = ty + 8;
				float t = (float) ((c.get.getAsDouble() - c.min) / (c.max - c.min));
				t = Mth.clamp(t, 0f, 1f);
				Draw.roundRect(g, sx0, sy, sx1, sy + 3, 1, Draw.withAlpha(0x2A3550, a));
				int kx = sx0 + Math.round(t * (sx1 - sx0));
				Draw.roundRect(g, sx0, sy, kx, sy + 3, 1, Draw.withAlpha(primary, a));
				Draw.roundRect(g, kx - 3, sy - 2, kx + 3, sy + 5, 2, Draw.white(a));
				hits.add(new Hit(x0, y, x1, y + h - 2, () -> {
					dragging = c.label;
					setSlider(c, mouseX);
				}));
			}
		}
	}

	private void setSlider(Control c, double mouseX) {
		int sx0 = 8 + 8 + 2, sx1 = 8 + PANEL_W - 8 - 2;
		double t = Mth.clamp((mouseX - sx0) / (double) (sx1 - sx0), 0, 1);
		c.set.accept(c.min + t * (c.max - c.min));
	}

	/** The photo you just took, bottom right, for a few seconds. */
	private void drawLastPhoto(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Identifier tex = PhotoMode.lastPhoto();
		if (tex == null) return;
		float age = (System.currentTimeMillis() - PhotoMode.lastPhotoAt()) / 1000f;
		if (age > 6f) return;
		float in = Draw.easeOutCubic(Mth.clamp(age / 0.35f, 0f, 1f));
		float out = Mth.clamp((6f - age) / 0.5f, 0f, 1f);
		float a = Math.min(in, out);
		int w = Math.min(130, width / 4), h = w * PhotoMode.lastH() / Math.max(1, PhotoMode.lastW());
		int x1 = width - 12, y1 = height - 48 + Math.round((1 - in) * 30);
		int x0 = x1 - w, y0 = y1 - h;
		Draw.roundRect(g, x0 - 4, y0 - 4, x1 + 4, y1 + 16, 4, Draw.withAlpha(0xF5F1E8, a));
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y0, 0f, 0f, w, h, PhotoMode.lastW(), PhotoMode.lastH(),
				PhotoMode.lastW(), PhotoMode.lastH(), Draw.white(a));
		g.text(font, "Saved!", x0, y1 + 4, Draw.withAlpha(0x2E2A24, a), false);
		String open = "Open folder";
		int ox = x1 - font.width(open);
		boolean over = mouseX >= ox && mouseX < x1 && mouseY >= y1 + 2 && mouseY < y1 + 14;
		g.text(font, open, ox, y1 + 4, Draw.withAlpha(over ? 0x1E6FD9 : 0x3D78C9, a), false);
		hits.add(new Hit(ox, y1 + 2, x1, y1 + 14, () -> {
			try {
				java.io.File dir = new java.io.File(minecraft.gameDirectory, "screenshots");
				dir.mkdirs();
				com.mojang.blaze3d.Blaze3D.openPath(dir.toPath());
			} catch (Exception ignored) {
			}
		}));
	}

	// ---- input -----------------------------------------------------------------------------------

	public void takePhoto() {
		if (capture != 0 || countdownEnd != 0) return;
		int secs = PhotoMode.TIMERS[PhotoMode.timer];
		if (secs > 0) {
			countdownEnd = Util.getMillis() + secs * 1000L;
			// start the pose again so it's mid-move when the shutter goes
			if (PhotoMode.pose != null) dev.lmg.client.emote.Emotes.playQuiet(PhotoMode.pose);
		} else {
			capture = 1;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
			takePhoto();
			return true;
		}
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_H) {
			panelHidden = !panelHidden;
			return true;
		}
		if (setKey(event, true)) return true;
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (setKey(event, false)) return true;
		return super.keyReleased(event);
	}

	private boolean setKey(KeyEvent e, boolean down) {
		var o = minecraft.options;
		int i = o.keyUp.matches(e) ? 0 : o.keyDown.matches(e) ? 1 : o.keyLeft.matches(e) ? 2 : o.keyRight.matches(e) ? 3
				: o.keyJump.matches(e) ? 4 : o.keyShift.matches(e) ? 5 : o.keySprint.matches(e) ? 6 : -1;
		if (i < 0) return false;
		PhotoMode.keys[i] = down;
		return true;
	}

	private boolean overPanel(double x, double y) {
		return !panelHidden && x >= 8 && x < 8 + PANEL_W && y >= 8 && y < panelBottom;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (Hit h : List.copyOf(hits)) {
			if (event.x() >= h.x0 && event.x() < h.x1 && event.y() >= h.y0 && event.y() < h.y1) {
				h.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			for (Control c : controls) if (c.label.equals(dragging)) setSlider(c, event.x());
			return true;
		}
		if (!overPanel(event.x(), event.y())) {
			PhotoMode.turn(dx * 2, dy * 2);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		PhotoMode.scroll(scrollY);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public void removed() {
		if (PhotoMode.pose != null) dev.lmg.client.emote.Emotes.stopMine();
		PhotoMode.stop();
	}
}
