package dev.lmg.client.emote;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * How each emote moves the player model. Runs after the normal animation, so it works on the
 * body, the armour and the cape at once (they all use the same model shape).
 *
 * Model space: y goes down, the ground is at y = 24, forward is -z. A negative arm xRot lifts the
 * arm forward; a positive zRot swings the right arm out (negative for the left arm).
 */
public final class EmotePoses {
	private static final float PI = (float) Math.PI;

	private EmotePoses() {}

	/** {@code t} = seconds since the emote started. */
	public static void apply(HumanoidModel<?> m, Emote e, float t) {
		// ease in over a quarter second, and out at the end of timed emotes
		float in = ease(Mth.clamp(t / 0.25f, 0f, 1f));
		float out = e.loops() ? 1f : ease(Mth.clamp((e.seconds - t) / 0.3f, 0f, 1f));
		float k = Math.min(in, out);
		if (k <= 0f) return;
		switch (e) {
			case WAVE -> wave(m, t, k);
			case HUG -> hug(m, t, k);
			case CHEER -> cheer(m, t, k);
			case DANCE -> dance(m, t, k);
			case CLAP -> clap(m, t, k);
			case SIT -> sit(m, t, k);
			case POINT -> point(m, t, k);
			case BOW -> bow(m, t, k);
		}
	}

	private static float ease(float x) {
		return x * x * (3f - 2f * x);
	}

	private static void to(ModelPart p, float xRot, float yRot, float zRot, float k) {
		p.xRot = Mth.lerp(k, p.xRot, xRot);
		p.yRot = Mth.lerp(k, p.yRot, yRot);
		p.zRot = Mth.lerp(k, p.zRot, zRot);
	}

	/** Moves the whole body down (positive) or up (negative), in model pixels. */
	private static void lift(HumanoidModel<?> m, float dy) {
		m.head.y += dy;
		m.body.y += dy;
		m.rightArm.y += dy;
		m.leftArm.y += dy;
		m.rightLeg.y += dy;
		m.leftLeg.y += dy;
	}

	private static void wave(HumanoidModel<?> m, float t, float k) {
		float swing = Mth.sin(t * 11f) * 0.38f;
		to(m.rightArm, -0.25f, 0.15f, 2.55f + swing, k);
		m.head.zRot += Mth.sin(t * 5.5f) * 0.06f * k;
		m.body.zRot += Mth.sin(t * 5.5f) * 0.025f * k;
	}

	private static void hug(HumanoidModel<?> m, float t, float k) {
		float squeeze = 0.06f * Mth.sin(t * 2.2f);
		to(m.rightArm, -1.35f, -0.62f - squeeze, 0.08f, k);
		to(m.leftArm, -1.35f, 0.62f + squeeze, -0.08f, k);
		m.body.zRot += Mth.sin(t * 1.6f) * 0.045f * k;
		m.head.zRot += Mth.sin(t * 1.6f) * 0.08f * k;
		m.head.xRot = Mth.lerp(k, m.head.xRot, 0.12f);
	}

	private static void cheer(HumanoidModel<?> m, float t, float k) {
		float pump = Mth.sin(t * 13f) * 0.18f;
		to(m.rightArm, -0.1f, 0f, PI - 0.45f + pump, k);
		to(m.leftArm, -0.1f, 0f, -(PI - 0.45f) - pump, k);
		float hop = Math.abs(Mth.sin(t * 6.5f)) * 2.2f;
		lift(m, -hop * k);
		m.head.xRot = Mth.lerp(k, m.head.xRot, -0.25f);
	}

	private static void dance(HumanoidModel<?> m, float t, float k) {
		float w = t * PI * 2f * 1.8f;        // about 108 bpm
		float beat = Mth.sin(w);
		to(m.rightArm, -0.35f + 0.25f * beat, 0f, 0.85f + 0.65f * beat, k);
		to(m.leftArm, -0.35f - 0.25f * beat, 0f, -(0.85f - 0.65f * beat), k);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, 0.3f * beat);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, -0.3f * beat);
		float twist = Mth.sin(w * 0.5f) * 0.38f;
		m.body.yRot += twist * k;
		m.rightArm.yRot += twist * k;
		m.leftArm.yRot += twist * k;
		m.head.yRot += twist * 0.6f * k;
		m.head.zRot += Mth.sin(w) * 0.1f * k;
		lift(m, -Math.abs(Mth.cos(w)) * 1.3f * k);
	}

	private static void clap(HumanoidModel<?> m, float t, float k) {
		float c = 0.5f + 0.5f * Mth.cos(t * 15f);  // 1 = apart, 0 = together
		to(m.rightArm, -1.25f, -0.42f + 0.32f * c, 0.05f, k);
		to(m.leftArm, -1.25f, 0.42f - 0.32f * c, -0.05f, k);
		m.head.xRot = Mth.lerp(k, m.head.xRot, 0.1f);
	}

	private static void sit(HumanoidModel<?> m, float t, float k) {
		// legs out in front, everything lowered so the hips rest on the ground
		to(m.rightLeg, -1.5f, 0.14f, 0.04f, k);
		to(m.leftLeg, -1.5f, -0.14f, -0.04f, k);
		to(m.rightArm, -0.62f, -0.12f, 0.12f, k);
		to(m.leftArm, -0.62f, 0.12f, -0.12f, k);
		m.body.xRot = Mth.lerp(k, m.body.xRot, -0.05f);
		lift(m, 10f * k);
		// a slow breath
		m.head.xRot += Mth.sin(t * 1.4f) * 0.02f * k;
	}

	private static void point(HumanoidModel<?> m, float t, float k) {
		// point where you're looking
		to(m.rightArm, m.head.xRot - PI / 2f, m.head.yRot, 0f, k);
		to(m.leftArm, 0.05f, 0f, -0.32f, k);
		m.rightArm.xRot += Mth.sin(t * 9f) * 0.03f * k;
	}

	private static void bow(HumanoidModel<?> m, float t, float k) {
		float d = k * Mth.clamp(Mth.sin(Mth.clamp(t / 2.0f, 0f, 1f) * PI) * 1.4f, 0f, 1f);
		// like crouching, but deeper and with the head down
		m.body.xRot += 0.62f * d;
		m.head.xRot = Mth.lerp(d, m.head.xRot, 0.55f);
		m.head.y += 4.6f * d;
		m.head.z -= 1.5f * d;
		m.body.y += 3.4f * d;
		m.rightArm.y += 3.4f * d;
		m.leftArm.y += 3.4f * d;
		m.rightLeg.z += 4.2f * d;
		m.leftLeg.z += 4.2f * d;
		to(m.rightArm, -0.9f, -0.55f, 0.1f, k);   // hand to the chest
		m.leftArm.xRot = Mth.lerp(d, m.leftArm.xRot, 0.62f);
	}
}
