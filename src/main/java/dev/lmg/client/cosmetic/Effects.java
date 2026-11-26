package dev.lmg.client.cosmetic;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side particle effects: a trail behind you and ambient particles around you.
 * Uses the game's own particles, spawned only on your screen (nothing is sent to the server).
 */
public final class Effects {
	private static final RandomSource RANDOM = RandomSource.create();
	private static int tick;

	private Effects() {}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null || mc.isPaused()) return;
		tick++;
		LmgConfig cfg = LmgConfig.get();
		// Minecraft's own Particles setting: Minimal = none of ours, Decreased = half
		ParticleStatus status = mc.options.particles().get();
		if (cfg.followParticleSetting && status == ParticleStatus.MINIMAL) return;
		if (cfg.followParticleSetting && status == ParticleStatus.DECREASED && tick % 2 == 0) return;
		// the trail is behind you, so in first person you'd never see it: don't spawn it
		boolean seeYourself = !mc.options.getCameraType().isFirstPerson() || dev.lmg.client.feature.Freecam.on();
		if (cfg.trail != LmgConfig.Trail.OFF && !player.isSpectator() && (seeYourself || cfg.trailFirstPerson)) trail(level, player, cfg);
		if (cfg.ambient != LmgConfig.Ambient.OFF) ambient(level, player, cfg);
	}

	private static void trail(ClientLevel level, LocalPlayer player, LmgConfig cfg) {
		Vec3 v = player.getDeltaMovement();
		boolean moving = v.x * v.x + v.z * v.z > 0.0009;
		if (!moving) return;
		int every = switch (cfg.trail) {
			case HEARTS, NOTES -> 5;
			default -> 1;
		};
		if (tick % every != 0) return;
		ParticleOptions particle = switch (cfg.trail) {
			case SPARKLE -> ParticleTypes.END_ROD;
			case ACCENT_DUST -> new DustParticleOptions(cfg.trailColor(), 1.0f);
			case HEARTS -> ParticleTypes.HEART;
			case NOTES -> ParticleTypes.NOTE;
			case SOUL_FLAMES -> ParticleTypes.SOUL_FIRE_FLAME;
			case SPARKS -> ParticleTypes.ELECTRIC_SPARK;
			default -> null;
		};
		if (particle == null) return;
		int count = cfg.trail == LmgConfig.Trail.ACCENT_DUST ? 2 : 1;
		for (int i = 0; i < count; i++) {
			double x = player.getX() - v.x * 2 + (RANDOM.nextDouble() - 0.5) * 0.4;
			double y = player.getY() + 0.1 + RANDOM.nextDouble() * 0.2;
			double z = player.getZ() - v.z * 2 + (RANDOM.nextDouble() - 0.5) * 0.4;
			double up = cfg.trail == LmgConfig.Trail.SPARKLE ? 0.01 : 0.0;
			level.addParticle(particle, x, y, z, 0, up, 0);
		}
	}

	private static void ambient(ClientLevel level, LocalPlayer player, LmgConfig cfg) {
		int perTick = new int[]{1, 2, 4}[cfg.ambientAmount];
		for (int i = 0; i < perTick; i++) {
			double r = 4 + RANDOM.nextDouble() * 10;
			double a = RANDOM.nextDouble() * Math.PI * 2;
			double x = player.getX() + Math.cos(a) * r;
			double z = player.getZ() + Math.sin(a) * r;
			switch (cfg.ambient) {
				case FIREFLIES -> level.addParticle(ParticleTypes.FIREFLY, x, player.getY() + 0.5 + RANDOM.nextDouble() * 3, z, 0, 0, 0);
				case PETALS -> level.addParticle(ParticleTypes.CHERRY_LEAVES, x, player.getY() + 5 + RANDOM.nextDouble() * 5, z, 0, 0, 0);
				case STARDUST -> level.addParticle(ParticleTypes.END_ROD, x, player.getY() + 1 + RANDOM.nextDouble() * 6, z,
						(RANDOM.nextDouble() - 0.5) * 0.01, -0.005, (RANDOM.nextDouble() - 0.5) * 0.01);
				case SNOW -> level.addParticle(ParticleTypes.SNOWFLAKE, x, player.getY() + 5 + RANDOM.nextDouble() * 5, z,
						0, -0.05, 0);
				case ASH -> level.addParticle(ParticleTypes.WHITE_ASH, x, player.getY() + 1 + RANDOM.nextDouble() * 6, z, 0, 0, 0);
				case SPORES -> level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, player.getY() + 1 + RANDOM.nextDouble() * 5, z, 0, 0, 0);
				default -> {}
			}
		}
	}
}
