package dev.lmg.client.cosmetic;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hit and kill effects. Purely visual and only on your screen: when you hit something we
 * remember it for a few seconds; if it dies in that time we play the kill effect where it was.
 */
public final class Combat {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int REMEMBER_TICKS = 60;

	private static Entity lastTarget;
	private static Vec3 lastTargetPos = Vec3.ZERO;
	private static float lastTargetHeight = 1.8f;
	private static int ticksSinceHit = Integer.MAX_VALUE;

	private Combat() {}

	/** Called when YOU attack an entity (client side). */
	public static void onAttack(Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(target instanceof LivingEntity)) return;
		lastTarget = target;
		lastTargetPos = target.position();
		lastTargetHeight = target.getBbHeight();
		ticksSinceHit = 0;
		hitEffect(mc.level, target);
	}

	/** The entity you hit in the last few seconds (for the target reticle), or null. */
	public static Entity recentTarget() {
		return ticksSinceHit < REMEMBER_TICKS ? lastTarget : null;
	}

	public static void tick(Minecraft mc) {
		if (lastTarget == null || mc.level == null) return;
		ticksSinceHit++;
		boolean dead = lastTarget.isRemoved() || (lastTarget instanceof LivingEntity l && l.isDeadOrDying());
		if (!dead) lastTargetPos = lastTarget.position();
		if (dead && ticksSinceHit < REMEMBER_TICKS) {
			killEffect(mc.level, lastTargetPos, lastTargetHeight);
			lastTarget = null;
		} else if (ticksSinceHit >= REMEMBER_TICKS) {
			lastTarget = null;
		}
	}

	private static void hitEffect(ClientLevel level, Entity target) {
		LmgConfig cfg = LmgConfig.get();
		ParticleOptions particle = switch (cfg.hitEffect) {
			case SPARKS -> ParticleTypes.ELECTRIC_SPARK;
			case CRITS -> ParticleTypes.CRIT;
			case STARS -> ParticleTypes.END_ROD;
			case HEARTS -> ParticleTypes.HEART;
			case ACCENT -> new DustParticleOptions(cfg.accentRgb(), 1.2f);
			default -> null;
		};
		if (particle == null) return;
		int count = cfg.hitEffect == LmgConfig.HitEffect.HEARTS ? 3 : 10;
		double cy = target.getY() + target.getBbHeight() * 0.6;
		for (int i = 0; i < count; i++) {
			double speed = cfg.hitEffect == LmgConfig.HitEffect.STARS ? 0.12 : 0.3;
			level.addParticle(particle,
					target.getX() + (RANDOM.nextDouble() - 0.5) * target.getBbWidth(),
					cy + (RANDOM.nextDouble() - 0.5) * 0.5,
					target.getZ() + (RANDOM.nextDouble() - 0.5) * target.getBbWidth(),
					(RANDOM.nextDouble() - 0.5) * speed, RANDOM.nextDouble() * speed * 0.6, (RANDOM.nextDouble() - 0.5) * speed);
		}
	}

	private static void killEffect(ClientLevel level, Vec3 at, float height) {
		LmgConfig cfg = LmgConfig.get();
		double x = at.x, y = at.y, z = at.z;
		switch (cfg.killEffect) {
			case SOUL_BURST -> {
				ring(level, ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.2, z, 36, 0.25);
				column(level, ParticleTypes.SOUL, x, y, z, height, 20);
				level.playLocalSound(x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 1f, false);
			}
			case STAR_BURST -> {
				DustParticleOptions dust = new DustParticleOptions(cfg.accentRgb(), 1.6f);
				spiral(level, dust, x, y, z, height);
				sphere(level, ParticleTypes.END_ROD, x, y + height * 0.5, z, 40, 0.18);
				level.playLocalSound(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.2f, false);
			}
			case FIREWORK -> {
				sphere(level, ParticleTypes.FIREWORK, x, y + height * 0.6, z, 60, 0.3);
				level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 1f, false);
				level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1f, 1f, false);
			}
			case TOTEM -> {
				sphere(level, ParticleTypes.TOTEM_OF_UNDYING, x, y + height * 0.5, z, 60, 0.45);
				level.playLocalSound(x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.3f, false);
			}
			case HEARTS -> {
				ring(level, ParticleTypes.HEART, x, y + height, z, 12, 0.05);
				level.playLocalSound(x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 0.8f, false);
			}
			default -> {}
		}
	}

	private static void ring(ClientLevel level, ParticleOptions p, double x, double y, double z, int count, double speed) {
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count;
			level.addParticle(p, x, y, z, Math.cos(a) * speed, 0.02, Math.sin(a) * speed);
		}
	}

	private static void sphere(ClientLevel level, ParticleOptions p, double x, double y, double z, int count, double speed) {
		for (int i = 0; i < count; i++) {
			double u = RANDOM.nextDouble() * 2 - 1;
			double a = RANDOM.nextDouble() * Math.PI * 2;
			double s = Math.sqrt(1 - u * u);
			level.addParticle(p, x, y, z, s * Math.cos(a) * speed, u * speed, s * Math.sin(a) * speed);
		}
	}

	private static void column(ClientLevel level, ParticleOptions p, double x, double y, double z, float height, int count) {
		for (int i = 0; i < count; i++) {
			level.addParticle(p, x + (RANDOM.nextDouble() - 0.5) * 0.6, y + RANDOM.nextDouble() * height,
					z + (RANDOM.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
		}
	}

	private static void spiral(ClientLevel level, ParticleOptions p, double x, double y, double z, float height) {
		int count = 48;
		for (int i = 0; i < count; i++) {
			double k = i / (double) count;
			double a = k * Math.PI * 6;
			double r = 0.7 * (1 - k * 0.5);
			level.addParticle(p, x + Math.cos(a) * r, y + k * height * 1.3, z + Math.sin(a) * r, 0, 0.01, 0);
		}
	}
}
