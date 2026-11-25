package dev.lmg.client.hud;

import dev.lmg.client.cosmetic.Combat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Works out which mob/player you're aiming at. Vanilla only knows about entities in melee
 * reach, so we also look further (up to 32 blocks) - but only along a clear line of sight.
 */
public final class Targeting {
	private static final double RANGE = 32.0;
	private static LivingEntity cached;
	private static int cachedTick = -1;

	private Targeting() {}

	/** Recomputed once per tick from the client tick handler. */
	public static void tick(Minecraft mc) {
		cachedTick++;
		cached = compute(mc);
	}

	/** The living thing you're aiming at, or the one you hit in the last few seconds, or null. */
	public static LivingEntity current() {
		return cached != null && cached.isAlive() && !cached.isRemoved() ? cached : null;
	}

	private static LivingEntity compute(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return null;
		if (mc.crosshairPickEntity instanceof LivingEntity l && l != player && l.isAlive()) return l;

		Vec3 eye = player.getEyePosition(1f);
		Vec3 end = eye.add(player.getViewVector(1f).scale(RANGE));
		BlockHitResult wall = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation(); // never look through blocks
		AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player,
				eye.distanceToSqr(end));
		if (hit != null && hit.getEntity() instanceof LivingEntity l) return l;

		Entity recent = Combat.recentTarget();
		return recent instanceof LivingEntity l && l.isAlive() ? l : null;
	}
}
