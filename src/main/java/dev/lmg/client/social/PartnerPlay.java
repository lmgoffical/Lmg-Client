package dev.lmg.client.social;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.emote.Emote;
import dev.lmg.client.emote.Emotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/**
 * You and your partner, together: sitting on their head or shoulder, or (when you've been idle
 * a while next to them) playing around them - hopping round in circles, pulling their arm,
 * poking them, then sitting down next to them for a rest.
 *
 * Only your character's picture moves (for you and other Lmg players). Your real position on
 * the server doesn't change, so you have to stay close: it ends if you get more than a few
 * blocks apart, if you move, or the moment your partner stops allowing it.
 */
public final class PartnerPlay {
	/** Further apart than this and it ends. */
	public static final double MAX_DISTANCE = 6.0;
	/** You have to be this close to climb on. */
	public static final double START_DISTANCE = 4.0;

	// the play routine: four parts, then round again
	public static final float HOP = 5f, TUG = 5f, POKE = 4f, REST = 5f, CYCLE = HOP + TUG + POKE + REST;
	private static final float BLEND = 0.6f;

	private PartnerPlay() {}

	/** Which part of the play routine: 0 hopping round, 1 pulling their arm, 2 poking, 3 resting. */
	public static int segment(float t) {
		float c = t % CYCLE;
		if (c < HOP) return 0;
		if (c < HOP + TUG) return 1;
		if (c < HOP + TUG + POKE) return 2;
		return 3;
	}

	/** Seconds into the current part. */
	public static float segmentTime(float t) {
		float c = t % CYCLE;
		if (c < HOP) return c;
		if (c < HOP + TUG) return c - HOP;
		if (c < HOP + TUG + POKE) return c - HOP - TUG;
		return c - HOP - TUG - POKE;
	}

	private static float length(int seg) {
		return switch (seg) {
			case 0 -> HOP;
			case 1 -> TUG;
			case 2 -> POKE;
			default -> REST;
		};
	}

	/** Has your partner said yes? (The server checks too; this just saves asking.) */
	public static boolean allowedNow() {
		return testAllowed || Social.state().partnerAllowsClimb();
	}

	/** For tests only: pretend your partner said yes. */
	public static boolean testAllowed;

	// ---- where you're drawn --------------------------------------------------------------------

	/** Where to draw someone, which way they face, and how big. */
	public record Spot(double x, double y, double z, float yaw, float scale) {}

	private static Vec3 forward(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.sin(r), 0, Math.cos(r));
	}

	private static Vec3 left(float yaw) {
		// the carrier's left hand side
		double r = Math.toRadians(yaw);
		return new Vec3(Math.cos(r), 0, Math.sin(r));
	}

	private static float facing(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	/** Where someone doing {@code e} (for {@code t} seconds) is drawn, on or around {@code carrier}. */
	public static Spot spot(Emote e, float t, Player carrier, float partial) {
		Vec3 c = carrier.getPosition(partial);
		float body = Mth.rotLerp(partial, carrier.yBodyRotO, carrier.yBodyRot);
		float head = Mth.rotLerp(partial, carrier.yHeadRotO, carrier.yHeadRot);
		float h = carrier.getBbHeight();
		switch (e) {
			case PERCH_HEAD -> {
				// sitting on top of their head, turning with it
				return new Spot(c.x, c.y + h + 0.02, c.z, head, 0.5f);
			}
			case PERCH_SHOULDER -> {
				// on their left shoulder, like a parrot
				Vec3 at = c.add(left(body).scale(0.34)).add(0, h * 0.80, 0).add(forward(body).scale(-0.02));
				return new Spot(at.x, at.y, at.z, body, 0.42f);
			}
			case PLAY -> {
				int seg = segment(t);
				float st = segmentTime(t);
				Spot now = playSpot(seg, st, c, body);
				if (st < BLEND) {
					// glide over from where the last part ended
					int prev = (seg + 3) % 4;
					Spot was = playSpot(prev, length(prev), c, body);
					float k = st / BLEND;
					k = k * k * (3 - 2 * k);
					return new Spot(Mth.lerp(k, was.x, now.x), Mth.lerp(k, was.y, now.y), Mth.lerp(k, was.z, now.z),
							Mth.rotLerp(k, was.yaw, now.yaw), 1f);
				}
				return now;
			}
			default -> {
				return null;
			}
		}
	}

	private static Spot playSpot(int seg, float st, Vec3 c, float body) {
		switch (seg) {
			case 0 -> {
				// hopping round them in a circle
				double a = Math.toRadians(body) + st * 1.35;
				double r = 1.3;
				double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
				double hop = Math.abs(Math.sin(st * 7.0)) * 0.55;
				// face the way you're going round
				float yaw = (float) Math.toDegrees(Math.atan2(Math.sin(a), Math.cos(a)));
				return new Spot(x, c.y + hop, z, yaw, 1f);
			}
			case 1 -> {
				// standing at their left, pulling their arm
				Vec3 at = c.add(left(body).scale(1.05)).add(forward(body).scale(0.15));
				return new Spot(at.x, at.y, at.z, facing(at, c), 1f);
			}
			case 2 -> {
				// in front of them, bouncing and poking
				Vec3 at = c.add(forward(body).scale(0.95));
				double bounce = Math.abs(Math.sin(st * 8.0)) * 0.12;
				return new Spot(at.x, at.y + bounce, at.z, facing(at, c), 1f);
			}
			default -> {
				// a rest: sitting next to them, leaning in
				Vec3 at = c.add(left(body).scale(-0.75)).add(forward(body).scale(-0.1));
				return new Spot(at.x, at.y, at.z, body, 1f);
			}
		}
	}

	/** Moves someone's picture onto (or around) their partner. Called while their render data is filled in. */
	public static void apply(AvatarRenderState state, Player who, Emotes.Playing playing, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || playing.with() == null) return;
		Player carrier = mc.level.getPlayerByUUID(playing.with());
		if (carrier == null) return;
		Spot s = spot(playing.emote(), playing.seconds(), carrier, partial);
		if (s == null) return;
		// in the first moment, run over from where you really are
		float in = Mth.clamp(playing.seconds() / 0.5f, 0f, 1f);
		in = in * in * (3 - 2 * in);
		Vec3 real = who.getPosition(partial);
		state.x = Mth.lerp(in, real.x, s.x());
		state.y = Mth.lerp(in, real.y, s.y());
		state.z = Mth.lerp(in, real.z, s.z());
		state.bodyRot = Mth.rotLerp(in, state.bodyRot, s.yaw());
		state.yRot = 0f;
		state.xRot = 0f;
		float scale = Mth.lerp(in, 1f, s.scale());
		state.scale *= scale;
		if (playing.emote().perch()) state.shadowRadius = 0f;
		if (state.nameTagAttachment != null) state.nameTagAttachment = state.nameTagAttachment.scale(scale);
		if (playing.emote() == Emote.PLAY && segment(playing.seconds()) == 0) {
			// legs running while hopping round
			state.walkAnimationPos = playing.seconds() * 9f;
			state.walkAnimationSpeed = 0.7f;
		} else {
			state.walkAnimationSpeed = 0f;
		}
	}

	/** How hard someone is pulling this player's arm right now (0 = not at all). */
	public static float tug(UUID carrier) {
		for (Map.Entry<UUID, Emotes.Playing> en : Emotes.all().entrySet()) {
			Emotes.Playing p = en.getValue();
			if (p.emote() != Emote.PLAY || !carrier.equals(p.with())) continue;
			float t = p.seconds();
			if (segment(t) != 1) continue;
			float st = segmentTime(t);
			float in = Mth.clamp((st - BLEND) / 0.4f, 0f, 1f), out = Mth.clamp((TUG - st) / 0.4f, 0f, 1f);
			return Math.min(in, out);
		}
		return 0f;
	}

	/** Where your camera goes while you sit on your partner (null when you're not). */
	public static Vec3 myCamera(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return null;
		Emotes.Playing p = Emotes.get(mc.player.getUUID());
		if (p == null || !p.emote().perch() || p.with() == null) return null;
		Player carrier = mc.level.getPlayerByUUID(p.with());
		if (carrier == null) return null;
		Spot s = spot(p.emote(), p.seconds(), carrier, partial);
		if (s == null) return null;
		// your eyes, at the size you are up there (on a shoulder, a bit further out so their head
		// isn't right in front of your face)
		Vec3 eyes = new Vec3(s.x(), s.y() + 1.0 * s.scale(), s.z());
		if (p.emote() == Emote.PERCH_SHOULDER) {
			float body = Mth.rotLerp(partial, carrier.yBodyRotO, carrier.yBodyRot);
			eyes = eyes.add(left(body).scale(0.28)).add(0, 0.08, 0);
		}
		return eyes;
	}

	// ---- you: climbing on, getting down, and playing when idle ------------------------------------

	/** Climbs onto your partner's head or shoulder. Returns null if it worked, else why not. */
	public static String climb(Emote where) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return "Join a world first.";
		UUID partner = Social.partner();
		if (partner == null) return "Pick a partner in Friends first.";
		if (!allowedNow()) return "Your partner hasn't said yes to climbing yet.";
		Player p = Partner.inWorld(mc);
		if (p == null) return "Your partner isn't here.";
		if (p.distanceTo(mc.player) > START_DISTANCE) return "Get closer to your partner first.";
		Emotes.playWith(where, p.getUUID(), msg -> dev.lmg.client.feature.LmgKeys.say(msg));
		return null;
	}

	public static void getDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Emotes.Playing p = Emotes.get(mc.player.getUUID());
		if (p != null && p.emote().withPartner()) Emotes.stopMine();
	}

	/** What you're doing with your partner right now, or null. */
	public static Emote mine() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		Emotes.Playing p = Emotes.get(mc.player.getUUID());
		return p != null && p.emote().withPartner() ? p.emote() : null;
	}

	private static long lastActive = Util.getMillis();
	private static double lastX, lastY, lastZ;
	private static float lastYaw, lastPitch;

	/** Notices when you've gone idle next to your partner, and starts playing; any input stops it. */
	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		long now = Util.getMillis();
		var pl = mc.player;
		boolean moved = Math.abs(pl.getX() - lastX) > 0.01 || Math.abs(pl.getY() - lastY) > 0.01 || Math.abs(pl.getZ() - lastZ) > 0.01;
		boolean looked = Math.abs(pl.getYRot() - lastYaw) > 0.5f || Math.abs(pl.getXRot() - lastPitch) > 0.5f;
		var o = mc.options;
		boolean keys = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
				|| o.keyJump.isDown() || o.keyShift.isDown() || o.keyAttack.isDown() || o.keyUse.isDown();
		lastX = pl.getX();
		lastY = pl.getY();
		lastZ = pl.getZ();
		lastYaw = pl.getYRot();
		lastPitch = pl.getXRot();
		boolean active = moved || looked || keys || mc.gui.screen() != null;
		Emote doing = mine();
		if (active) {
			lastActive = now;
			// playing stops as soon as you're back (sitting on them doesn't: you can look around up there)
			if (doing == Emote.PLAY && (moved || keys || looked)) Emotes.stopMine();
			return;
		}
		LmgConfig c = LmgConfig.get();
		if (!c.partnerPlayIdle || doing != null || Emotes.get(pl.getUUID()) != null || !allowedNow()) return;
		if (now - lastActive < c.partnerIdleSeconds * 1000L) return;
		Player p = Partner.inWorld(mc);
		if (p == null || p.distanceTo(pl) > START_DISTANCE + 1) return;
		Emotes.playWith(Emote.PLAY, p.getUUID(), msg -> {});
	}
}
