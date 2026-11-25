package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Freecam: your body stays where it is while the camera flies around (WASD, jump = up,
 * sneak = down, sprint = faster). Only on your screen - nothing is sent to the server.
 */
public final class Freecam {
	private static boolean on;
	private static double x, y, z, px, py, pz;
	private static float yaw, pitch;

	private Freecam() {}

	public static boolean on() {
		return on;
	}

	public static void toggle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (!on && !mc.isLocalServer() && !LmgConfig.get().freecamOnServers) {
			LmgKeys.say("Freecam is off on servers - some ban it. Turn it on in Keys if this one allows it.");
			return;
		}
		on = !on;
		if (on) {
			Vec3 eye = mc.player.getEyePosition();
			x = px = eye.x;
			y = py = eye.y;
			z = pz = eye.z;
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
		}
		LmgKeys.say(on ? "Freecam on" : "Freecam off");
	}

	public static void disable() {
		on = false;
	}

	/** Mouse look while flying. */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
	}

	public static void tick(Minecraft mc) {
		if (!on) return;
		if (mc.player == null || mc.level == null) {
			on = false;
			return;
		}
		px = x;
		py = y;
		pz = z;
		if (mc.gui.screen() != null) return;
		var o = mc.options;
		float fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
		float side = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
		float up = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
		double speed = LmgConfig.FREECAM_SPEEDS[LmgConfig.get().freecamSpeed] * (o.keySprint.isDown() ? 2.5 : 1.0);
		double rad = Math.toRadians(yaw);
		double sin = Math.sin(rad), cos = Math.cos(rad);
		double mx = (-sin * fwd + cos * side), mz = (cos * fwd + sin * side);
		double len = Math.sqrt(mx * mx + mz * mz + up * up);
		if (len > 0) {
			x += mx / len * speed;
			y += up / len * speed;
			z += mz / len * speed;
		}
	}

	public static Vec3 position(float partial) {
		return new Vec3(Mth.lerp(partial, px, x), Mth.lerp(partial, py, y), Mth.lerp(partial, pz, z));
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}
}
