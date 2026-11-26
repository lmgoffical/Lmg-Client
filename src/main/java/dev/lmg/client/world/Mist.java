package dev.lmg.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Quads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Low mist: soft white puffs that hang just above the ground around you and drift slowly.
 * Thicker in the morning and at night. Only on your screen.
 */
public final class Mist {
	private static final Identifier TEXTURE = LmgClient.id("textures/cosmetic/mist.png");
	private static final Random RANDOM = new Random();

	private record Puff(double x, double y, double z, double vx, double vz, float size, int born, int life, float spin) {}

	private static final List<Puff> puffs = new ArrayList<>();
	private static int tick;

	private Mist() {}

	public static boolean on() {
		return LmgConfig.get().ambient == LmgConfig.Ambient.MIST;
	}

	public static void tick(Minecraft mc) {
		if (!on() || mc.level == null || mc.player == null) {
			puffs.clear();
			return;
		}
		if (mc.isPaused()) return;
		tick++;
		int want = new int[]{40, 70, 110}[LmgConfig.get().ambientAmount];
		puffs.removeIf(p -> tick - p.born > p.life || mc.player.distanceToSqr(p.x, mc.player.getY(), p.z) > 40 * 40);
		int add = Math.min(3, want - puffs.size());
		for (int i = 0; i < add; i++) {
			double r = 5 + RANDOM.nextDouble() * 26;
			double a = RANDOM.nextDouble() * Math.PI * 2;
			double x = mc.player.getX() + Math.cos(a) * r, z = mc.player.getZ() + Math.sin(a) * r;
			int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
			if (ground <= mc.level.getMinY()) continue;
			double y = ground + 0.4 + RANDOM.nextDouble() * 1.4;
			puffs.add(new Puff(x, y, z, (RANDOM.nextDouble() - 0.5) * 0.02, (RANDOM.nextDouble() - 0.5) * 0.02,
					3.5f + RANDOM.nextFloat() * 4f, tick, 500 + RANDOM.nextInt(500), RANDOM.nextFloat() * 6.28f));
		}
	}

	public static void render(LevelRenderContext ctx) {
		if (puffs.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		var camState = ctx.levelState().cameraRenderState;
		Vec3 cam = camState.pos;
		Quaternionf rot = new Quaternionf(camState.orientation);
		Vector3f right = rot.transform(new Vector3f(1, 0, 0)), up = rot.transform(new Vector3f(0, 1, 0));
		float day = WorldVisuals.daylight();
		// thickest at dawn, dusk and night; thin in bright daylight
		float thickness = 0.55f + 0.45f * (1f - day);
		int shade = Math.round(255 * (0.55f + 0.45f * day));
		int rgb = (shade << 16) | (Math.min(255, shade + 8) << 8) | Math.min(255, shade + 18);
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		PoseStack pose = ctx.poseStack();
		List<Puff> list = List.copyOf(puffs);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (p, vc) -> {
			for (Puff m : list) {
				float age = tick - m.born + partial;
				float fade = Math.min(1f, Math.min(age / 80f, (m.life - age) / 80f));
				if (fade <= 0) continue;
				double px = m.x + m.vx * age - cam.x, py = m.y - cam.y, pz = m.z + m.vz * age - cam.z;
				double dist = Math.sqrt(px * px + py * py + pz * pz);
				// fade out when you're inside it, and far away
				float near = (float) Math.min(1.0, Math.max(0.0, (dist - 2.0) / 4.0));
				float far = (float) Math.min(1.0, Math.max(0.0, (34.0 - dist) / 8.0));
				float alpha = 0.34f * thickness * fade * near * far;
				if (alpha < 0.01f) continue;
				int argb = (Math.round(alpha * 255) << 24) | rgb;
				float s = m.size;
				float c = (float) Math.cos(m.spin), sn = (float) Math.sin(m.spin);
				// a slowly turned square facing you
				float rx = (right.x * c + up.x * sn) * s, ry = (right.y * c + up.y * sn) * s * 0.55f, rz = (right.z * c + up.z * sn) * s;
				float ux = (up.x * c - right.x * sn) * s, uy = (up.y * c - right.y * sn) * s * 0.55f, uz = (up.z * c - right.z * sn) * s;
				float x = (float) px, y = (float) py, z = (float) pz;
				Quads.vertex(vc, p, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, argb, Quads.FULL_BRIGHT, 0, 1, 0);
				Quads.vertex(vc, p, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, argb, Quads.FULL_BRIGHT, 0, 1, 0);
				Quads.vertex(vc, p, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, argb, Quads.FULL_BRIGHT, 0, 1, 0);
				Quads.vertex(vc, p, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, argb, Quads.FULL_BRIGHT, 0, 1, 0);
			}
		});
	}
}
