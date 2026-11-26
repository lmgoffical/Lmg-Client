package dev.lmg.client.feature;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Photo mode: a free camera (or, on servers with server-safe mode, a camera that circles you and
 * can't go through walls), colour filters, a vignette, grain, frames - and a button that saves
 * the shot to your screenshots folder.
 *
 * The camera moves every frame by real time, so it stays smooth while singleplayer is paused.
 */
public final class PhotoMode {
	public enum Filter {
		NATURAL("Natural", null, 0xD9DEE6), WARM("Warm", "warm", 0xF2B36B), COOL("Cool", "cool", 0x6FA8E8),
		MONO("Black & white", "mono", 0xB4B4B4), NOIR("Noir", "noir", 0x5A5A5A), VINTAGE("Vintage", "vintage", 0xC9A27A),
		VIVID("Vivid", "vivid", 0x5BD45B), DREAM("Dreamy", "dream", 0xD7A8F0);

		public final String label;
		public final Identifier effect;
		public final int swatch;

		Filter(String label, String effect, int swatch) {
			this.label = label;
			this.effect = effect == null ? null : LmgClient.id(effect);
			this.swatch = swatch;
		}
	}

	public enum Frame { NONE("None"), CINEMA("Cinema bars"), POLAROID("Polaroid"), FILM("Film strip");
		public final String label;

		Frame(String label) {
			this.label = label;
		}
	}

	/** Depth of field: keeps what's in the middle sharp, softens the rest. */
	public enum Focus {
		OFF("Off", null), SOFT("Soft", "dof_soft"), STRONG("Strong", "dof_strong"), MINIATURE("Miniature", "tilt_shift");

		public final String label;
		public final Identifier effect;

		Focus(String label, String effect) {
			this.label = label;
			this.effect = effect == null ? null : LmgClient.id(effect);
		}
	}

	/** Ready-made looks: a filter, frame, vignette, grain and focus that go together. */
	public enum Look {
		GOLDEN("Golden hour", Filter.WARM, Frame.POLAROID, 0.45f, false, Focus.SOFT),
		NOIR("Noir", Filter.NOIR, Frame.CINEMA, 0.6f, true, Focus.OFF),
		DREAMY("Dreamy", Filter.DREAM, Frame.NONE, 0.3f, false, Focus.SOFT),
		FILM("Old film", Filter.VINTAGE, Frame.FILM, 0.5f, true, Focus.OFF),
		MINIATURE("Miniature", Filter.VIVID, Frame.NONE, 0.2f, false, Focus.MINIATURE),
		CLEAN("Clean", Filter.NATURAL, Frame.NONE, 0f, false, Focus.OFF);

		public final String label;
		final Filter f;
		final Frame fr;
		final float v;
		final boolean g;
		final Focus fo;

		Look(String label, Filter f, Frame fr, float v, boolean g, Focus fo) {
			this.label = label;
			this.f = f;
			this.fr = fr;
			this.v = v;
			this.g = g;
			this.fo = fo;
		}

		public void apply() {
			filter = f;
			frame = fr;
			vignette = v;
			grain = g;
			focus = fo;
		}

		boolean matches() {
			return filter == f && frame == fr && Math.abs(vignette - v) < 0.01f && grain == g && focus == fo;
		}
	}

	/** The look you're on, or null if you've mixed your own. */
	public static Look look() {
		for (Look l : Look.values()) if (l.matches()) return l;
		return null;
	}

	public static final int[] TIMERS = {0, 3, 10};

	private static boolean active, orbit;
	public static Focus focus = Focus.OFF;
	public static int timer = 0;            // index into TIMERS
	public static boolean grid = false;
	public static dev.lmg.client.emote.Emote pose = null;
	public static Filter filter = Filter.NATURAL;
	public static Frame frame = Frame.NONE;
	public static float fov = 70f, roll = 0f, vignette = 0.35f;
	public static boolean grain = false, hideMe = false;

	// camera
	private static double x, y, z, vx, vy, vz;
	private static float yaw, pitch, orbitDist = 3.5f;
	private static long lastFrameNs;
	/** Held movement keys, set by the photo screen: forward, back, left, right, up, down, fast. */
	public static final boolean[] keys = new boolean[7];

	// the last photo, as a texture for the little preview
	private static Identifier lastPhoto;
	private static int lastW, lastH;
	private static long lastPhotoAt;
	private static String lastFile = "";

	private PhotoMode() {}

	public static boolean active() {
		return active;
	}

	public static boolean orbit() {
		return orbit;
	}

	public static void start() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		active = true;
		// servers with server-safe on: circle around yourself instead of flying anywhere
		orbit = ServerSafe.blocks(ServerSafe.Feature.FREECAM);
		Vec3 eye = mc.player.getEyePosition();
		x = eye.x;
		y = eye.y;
		z = eye.z;
		vx = vy = vz = 0;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		fov = mc.options.fov().get();
		roll = 0;
		lastFrameNs = 0;
		java.util.Arrays.fill(keys, false);
		if (Freecam.on()) Freecam.disable();
	}

	public static void stop() {
		active = false;
		java.util.Arrays.fill(keys, false);
	}

	public static void resetCamera() {
		boolean was = active;
		float f = fov;
		start();
		active = was;
		fov = f;
	}

	/** Puts the camera somewhere exact (used by "reset" and the in-game tests). */
	public static void place(double cx, double cy, double cz, float cyaw, float cpitch) {
		x = cx;
		y = cy;
		z = cz;
		vx = vy = vz = 0;
		yaw = cyaw;
		pitch = Mth.clamp(cpitch, -90f, 90f);
	}

	/** Mouse drag: look around (or circle around you). */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.25f;
		pitch = Mth.clamp(pitch + (float) dy * 0.25f, -90f, 90f);
	}

	/** Scroll: zoom (or, circling, move closer / further). */
	public static void scroll(double amount) {
		if (orbit && keys[6]) orbitDist = Mth.clamp(orbitDist - (float) amount * 0.4f, 1.2f, 8f);
		else fov = Mth.clamp(fov - (float) amount * 3f, 10f, 110f);
	}

	// ---- the camera, every frame ---------------------------------------------------------------

	public static Vec3 position(Minecraft mc, float partial) {
		long now = System.nanoTime();
		float dt = lastFrameNs == 0 ? 0f : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
		lastFrameNs = now;
		if (orbit) return orbitPosition(mc, partial);

		float fwd = (keys[0] ? 1 : 0) - (keys[1] ? 1 : 0);
		float side = (keys[2] ? 1 : 0) - (keys[3] ? 1 : 0);
		float up = (keys[4] ? 1 : 0) - (keys[5] ? 1 : 0);
		double speed = keys[6] ? 12.0 : 4.0;   // blocks per second
		double rad = Math.toRadians(yaw), prad = Math.toRadians(pitch);
		double sin = Math.sin(rad), cos = Math.cos(rad);
		// forward follows where you look (up and down too), like a drone
		double fx = -sin * Math.cos(prad), fy = -Math.sin(prad), fz = cos * Math.cos(prad);
		double tx = fx * fwd + cos * side, ty = fy * fwd + up, tz = fz * fwd + sin * side;
		double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
		if (len > 0) {
			tx = tx / len * speed;
			ty = ty / len * speed;
			tz = tz / len * speed;
		}
		// glide: ease towards the wanted speed
		double k = 1 - Math.exp(-dt * 7.0);
		vx += (tx - vx) * k;
		vy += (ty - vy) * k;
		vz += (tz - vz) * k;
		x += vx * dt;
		y += vy * dt;
		z += vz * dt;
		return new Vec3(x, y, z);
	}

	private static Vec3 orbitPosition(Minecraft mc, float partial) {
		Vec3 eye = mc.player.getEyePosition(partial);
		double rad = Math.toRadians(yaw), prad = Math.toRadians(pitch);
		Vec3 back = new Vec3(Math.sin(rad) * Math.cos(prad), Math.sin(prad), -Math.cos(rad) * Math.cos(prad));
		Vec3 want = eye.add(back.scale(orbitDist));
		// don't go through walls
		var hit = mc.level.clip(new ClipContext(eye, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() != HitResult.Type.MISS) want = hit.getLocation().add(back.scale(-0.25));
		return want;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	// ---- taking the photo ------------------------------------------------------------------------

	/** Grabs the last finished frame (the screen hides its buttons for that frame) and saves it. */
	public static void grab(Runnable done) {
		Minecraft mc = Minecraft.getInstance();
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
			File dir = new File(mc.gameDirectory, "screenshots");
			dir.mkdirs();
			String name = "lmg-photo_" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
			File file = new File(dir, name + ".png");
			for (int i = 2; file.exists(); i++) file = new File(dir, name + "_" + i + ".png");
			File out = file;
			// a small copy for the preview card
			NativeImage thumb = scaled(image, 192);
			net.minecraft.util.Util.ioPool().execute(() -> {
				try (image) {
					image.writeToFile(out);
				} catch (Exception e) {
					LmgClient.LOGGER.warn("Couldn't save the photo", e);
				}
			});
			mc.execute(() -> {
				Identifier id = LmgClient.id("photo/last");
				if (lastPhoto != null) mc.getTextureManager().release(lastPhoto);
				mc.getTextureManager().register(id, new DynamicTexture(() -> "Lmg photo", thumb));
				lastPhoto = id;
				lastW = thumb.getWidth();
				lastH = thumb.getHeight();
				lastPhotoAt = System.currentTimeMillis();
				lastFile = out.getName();
				done.run();
			});
		});
	}

	private static NativeImage scaled(NativeImage src, int width) {
		int w = Math.min(width, src.getWidth());
		int h = Math.max(1, src.getHeight() * w / src.getWidth());
		NativeImage out = new NativeImage(w, h, false);
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				out.setPixel(px, py, src.getPixel(px * src.getWidth() / w, py * src.getHeight() / h));
			}
		}
		return out;
	}

	public static Identifier lastPhoto() {
		return lastPhoto;
	}

	public static int lastW() {
		return lastW;
	}

	public static int lastH() {
		return lastH;
	}

	public static long lastPhotoAt() {
		return lastPhotoAt;
	}

	public static String lastFile() {
		return lastFile;
	}
}
