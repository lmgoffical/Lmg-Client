package dev.lmg.client.emote;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is doing which emote right now. Yours start the moment you pick one; other Lmg players'
 * arrive through {@link EmoteNet}. An emote stops when it's over or when that player moves.
 */
public final class Emotes {
	/** One playing emote. {@code where} is where the player stood when it started. */
	public record Playing(Emote emote, long startMs, Vec3 where, UUID with) {
		public float seconds() {
			return (clock() - startMs) / 1000f;
		}
	}

	private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();
	private static CameraType cameraBefore;
	private static boolean cameraChanged;

	/** For the wheel's preview: drawn on your player in the wheel only. */
	public static volatile Emote preview;
	public static volatile long previewStart;

	private Emotes() {}

	// a clock that stops while the game is paused (so emotes freeze in photo mode too)
	private static long pausedTotal, pausedSince;

	public static long clock() {
		long now = Util.getMillis();
		return now - pausedTotal - (pausedSince != 0 ? now - pausedSince : 0);
	}

	public static Playing get(UUID uuid) {
		return PLAYING.get(uuid);
	}

	// ---- yours -----------------------------------------------------------------------------------

	public static void play(Emote e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (mc.player.isPassenger() || mc.player.isFallFlying() || mc.player.isSwimming() || mc.player.isSleeping()) {
			dev.lmg.client.feature.LmgKeys.say("Can't do that right now.");
			return;
		}
		PLAYING.put(mc.player.getUUID(), new Playing(e, clock(), mc.player.position(), null));
		// see yourself do it (and go back to how you were looking after)
		if (LmgConfig.get().emoteCamera && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			cameraBefore = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			cameraChanged = true;
		}
		EmoteNet.send(e);
	}

	/** Plays an emote without turning the camera (photo mode has its own camera). */
	public static void playQuiet(Emote e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		PLAYING.put(mc.player.getUUID(), new Playing(e, clock(), mc.player.position(), null));
		EmoteNet.send(e);
	}

	/**
	 * Sitting on, or playing around, your partner. Shows straight away; if the server says your
	 * partner hasn't allowed it, it stops again and {@code said} hears why.
	 */
	public static void playWith(Emote e, UUID partner, java.util.function.Consumer<String> said) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || partner == null) return;
		PLAYING.put(mc.player.getUUID(), new Playing(e, clock(), mc.player.position(), partner));
		EmoteNet.sendWith(e, problem -> mc.execute(() -> {
			Playing now = PLAYING.get(mc.player == null ? null : mc.player.getUUID());
			if (now != null && now.emote() == e) PLAYING.remove(mc.player.getUUID());
			if (said != null) said.accept(problem);
		}));
	}

	/** Everyone doing something right now (to find who's playing around whom). */
	public static Map<UUID, Playing> all() {
		return PLAYING;
	}

	public static void stopMine() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Playing was = PLAYING.remove(mc.player.getUUID());
		if (was != null && was.emote().lasting()) EmoteNet.send(null);
		restoreCamera(mc);
	}

	private static void restoreCamera(Minecraft mc) {
		if (cameraChanged && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT && cameraBefore != null) {
			mc.options.setCameraType(cameraBefore);
		}
		cameraChanged = false;
	}

	// ---- other players ---------------------------------------------------------------------------

	/** Another player started an emote ({@code null} = they stopped). */
	public static void remote(UUID uuid, Emote e, long startMs) {
		remote(uuid, e, startMs, null);
	}

	public static void remote(UUID uuid, Emote e, long startMs, UUID with) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null && uuid.equals(mc.player.getUUID())) return;  // your own echo
			if (e == null) {
				PLAYING.remove(uuid);
				return;
			}
			Player p = mc.level == null ? null : mc.level.getPlayerByUUID(uuid);
			if (p == null) return;
			long elapsed = Math.max(0, Math.min(15_000, System.currentTimeMillis() - startMs));
			long start = clock() - elapsed;
			PLAYING.put(uuid, new Playing(e, start, p.position(), with));
		});
	}

	// ---- every tick ------------------------------------------------------------------------------

	public static void tick(Minecraft mc) {
		long now = Util.getMillis();
		if (mc.isPaused() && pausedSince == 0) pausedSince = now;
		else if (!mc.isPaused() && pausedSince != 0) {
			pausedTotal += now - pausedSince;
			pausedSince = 0;
		}
		if (mc.isPaused()) return;
		ClientLevel level = mc.level;
		if (level == null) {
			PLAYING.clear();
			return;
		}
		UUID me = mc.player != null ? mc.player.getUUID() : null;
		for (Map.Entry<UUID, Playing> en : PLAYING.entrySet()) {
			Playing pl = en.getValue();
			Player p = level.getPlayerByUUID(en.getKey());
			boolean mine = en.getKey().equals(me);
			boolean over = !pl.emote().loops() && pl.seconds() > pl.emote().seconds;
			boolean gone = p == null || p.isRemoved();
			boolean moved = p != null && horizontalDistance(p.position(), pl.where()) > 0.12;
			boolean busy = p != null && (p.isPassenger() || p.isFallFlying() || p.isSwimming() || p.isSwinging());
			// partner emotes end when the partner leaves or gets too far away (or, for you, stops allowing it)
			if (pl.emote().withPartner()) {
				Player with = pl.with() == null ? null : level.getPlayerByUUID(pl.with());
				if (with == null || p == null || with.distanceTo(p) > dev.lmg.client.social.PartnerPlay.MAX_DISTANCE) gone = true;
				if (mine && !dev.lmg.client.social.PartnerPlay.allowedNow()) gone = true;
			}
			if (mine && mc.player != null && (mc.options.keyJump.isDown() || mc.options.keyShift.isDown())) moved = true;
			if (over || gone || moved || busy) {
				PLAYING.remove(en.getKey());
				if (mine) {
					if (pl.emote().lasting() && !over) EmoteNet.send(null);
					restoreCamera(mc);
				}
			}
		}
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
