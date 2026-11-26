package dev.lmg.client.pet;

import com.mojang.authlib.GameProfile;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.cosmetic.CosmeticModel;
import dev.lmg.client.cosmetic.CosmeticRegistry;
import dev.lmg.client.cosmetic.CustomSkins;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Your pets. They're real Minecraft mob models, but they only exist on your screen: nothing is
 * spawned in the world and nothing is sent to the server. Each tick they walk after you (or ride
 * on your shoulder / head), and each frame they're added to the list of things the game draws.
 */
public final class Pets {
	public static final int MAX = 3;
	/** Where follow-mode pets like to stand, relative to you: {right, forward}. */
	private static final double[][] SLOTS = {{-1.1, -1.2}, {1.1, -1.2}, {0, -2.0}};

	private static final class Live {
		final PetConfig cfg;
		final String identity;
		final Entity entity;
		final ClientLevel level;
		final boolean remote;   // belongs to another Lmg player
		int idleTicks;
		double bob;
		// playing around the owner
		int ownerStill, playTicks;
		double ownerX = Double.NaN, ownerZ;

		Live(PetConfig cfg, Entity entity, ClientLevel level) {
			this(cfg, entity, level, false);
		}

		Live(PetConfig cfg, Entity entity, ClientLevel level, boolean remote) {
			this.cfg = cfg;
			this.identity = cfg.identity();
			this.entity = entity;
			this.level = level;
			this.remote = remote;
		}
	}

	private static final List<Live> LIVE = new ArrayList<>();
	/** Pets of other Lmg players near you who shared them. */
	private static final Map<UUID, List<Live>> OTHERS = new HashMap<>();

	private Pets() {}

	// ---- per tick --------------------------------------------------------------------------------

	public static void tick(Minecraft mc) {
		LocalPlayer owner = mc.player;
		ClientLevel level = mc.level;
		if (owner == null || level == null) {
			LIVE.clear();
			return;
		}
		List<PetConfig> wanted = new ArrayList<>();
		for (PetConfig p : LmgConfig.get().pets) if (p.enabled && wanted.size() < MAX) wanted.add(p);

		// rebuild when the list, a pet's look, or the world changed
		boolean rebuild = wanted.size() != LIVE.size();
		for (int i = 0; !rebuild && i < wanted.size(); i++) {
			Live l = LIVE.get(i);
			rebuild = l.cfg != wanted.get(i) || !l.identity.equals(wanted.get(i).identity()) || l.level != level;
		}
		if (rebuild) {
			List<Live> old = new ArrayList<>(LIVE);
			LIVE.clear();
			for (int i = 0; i < wanted.size(); i++) {
				Entity e = create(wanted.get(i), level);
				if (e == null) continue;
				Live l = new Live(wanted.get(i), e, level);
				// keep a rebuilt pet where it was instead of popping back to you
				Vec3 at = i < old.size() && old.get(i).level == level ? old.get(i).entity.position() : spawnSpot(owner, i);
				e.setPos(at.x, at.y, at.z);
				e.setOldPosAndRot();
				LIVE.add(l);
			}
		}

		for (int i = 0; i < LIVE.size(); i++) move(LIVE.get(i), owner, level, i);
		tickOthers(owner, level);
	}

	private static void tickOthers(LocalPlayer me, ClientLevel level) {
		if (!LmgConfig.get().showOthers) {
			OTHERS.clear();
			return;
		}
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		for (Player p : level.players()) {
			if (p == me || p.isRemoved() || p.distanceTo(me) > LmgConfig.OTHERS_RANGES[LmgConfig.get().othersRange]) continue;
			dev.lmg.client.sync.AvatarSync.Shared shared = dev.lmg.client.sync.AvatarSync.get(p.getUUID());
			if (shared == null) continue;
			seen.add(p.getUUID());
			List<PetConfig> wanted = new ArrayList<>();
			for (PetConfig c : shared.pets()) if (c.enabled && wanted.size() < MAX) wanted.add(c);
			List<Live> lives = OTHERS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
			boolean rebuild = lives.size() != wanted.size();
			for (int i = 0; !rebuild && i < wanted.size(); i++) {
				rebuild = !lives.get(i).identity.equals(wanted.get(i).identity()) || lives.get(i).level != level
						|| !lives.get(i).cfg.mode.equals(wanted.get(i).mode) || lives.get(i).cfg.size != wanted.get(i).size
						|| !lives.get(i).cfg.name.equals(wanted.get(i).name);
			}
			if (rebuild) {
				lives.clear();
				for (int i = 0; i < wanted.size(); i++) {
					Entity e = create(wanted.get(i), level, p);
					if (e == null) continue;
					Vec3 at = spawnSpot(p, i);
					e.setPos(at.x, at.y, at.z);
					e.setOldPosAndRot();
					lives.add(new Live(wanted.get(i), e, level, true));
				}
			}
			for (int i = 0; i < lives.size(); i++) move(lives.get(i), p, level, i);
		}
		OTHERS.keySet().retainAll(seen);
	}

	private static Vec3 spawnSpot(Player owner, int slot) {
		return offset(owner, SLOTS[slot % SLOTS.length][0], SLOTS[slot % SLOTS.length][1], owner.getYRot());
	}

	/** A point next to / behind the owner, turned with them. */
	private static Vec3 offset(Entity owner, double right, double forward, float yaw) {
		double r = Math.toRadians(yaw);
		double fx = -Math.sin(r), fz = Math.cos(r);     // facing
		double rx = -Math.cos(r), rz = -Math.sin(r);    // right hand
		return new Vec3(owner.getX() + rx * right + fx * forward, owner.getY(), owner.getZ() + rz * right + fz * forward);
	}

	private static void move(Live l, Player owner, ClientLevel level, int slot) {
		Entity e = l.entity;
		e.setOldPosAndRot();
		if (e instanceof LivingEntity living) {
			living.yBodyRotO = living.yBodyRot;
			living.yHeadRotO = living.yHeadRot;
		}
		e.tickCount++;
		PetConfig cfg = l.cfg;
		PetType type = cfg.type();

		// has the owner been standing still? (then pets on Follow come and play)
		double odx = owner.getX() - l.ownerX, odz = owner.getZ() - l.ownerZ;
		if (Double.isNaN(l.ownerX) || odx * odx + odz * odz > 0.0004) l.ownerStill = 0;
		else l.ownerStill++;
		l.ownerX = owner.getX();
		l.ownerZ = owner.getZ();
		boolean idlePlay = "FOLLOW".equals(cfg.mode) && LmgConfig.get().petsPlayIdle
				&& l.ownerStill > LmgConfig.get().partnerIdleSeconds * 20;
		if ("PLAY".equals(cfg.mode) || idlePlay) {
			play(l, owner, level, slot);
			return;
		}
		l.playTicks = 0;

		if (cfg.attached()) {
			// position is worked out per frame in extract(); keep the entity near you for lighting
			e.setPos(owner.getX(), owner.getY() + owner.getBbHeight(), owner.getZ());
			e.setYRot(owner.yBodyRot);
			if (e instanceof LivingEntity living) {
				living.setYBodyRot(owner.yBodyRot);
				living.setYHeadRot(owner.yBodyRot);
				living.walkAnimation.update(0f, 0.4f, 1f);
			}
			e.setOnGround(true);
			setSitting(e, true);
			mirrorCrouch(e, owner);
			return;
		}

		double[] s = SLOTS[slot % SLOTS.length];
		Vec3 target = offset(owner, s[0], s[1], owner.yBodyRot);
		double dx = target.x - e.getX(), dz = target.z - e.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		double toOwner = e.position().distanceTo(owner.position());
		if (toOwner > 24 || e.level() != owner.level()) {
			e.setPos(target.x, owner.getY(), target.z);
			e.setOldPosAndRot();
			return;
		}

		double ownerSpeed = owner.getDeltaMovement().horizontalDistance();
		double speed = Math.max(0.16, Math.min(1.2, ownerSpeed * 1.25 + dist * 0.08));
		double step = dist > 0.35 ? Math.min(dist, speed) : 0;
		double nx = e.getX() + (dist > 0 ? dx / dist * step : 0);
		double nz = e.getZ() + (dist > 0 ? dz / dist * step : 0);
		double ny;
		if (type.flying) {
			l.bob += 0.12;
			double want = owner.getY() + 1.4 + Math.sin(l.bob) * 0.15;
			ny = e.getY() + (want - e.getY()) * 0.25;
			e.setOnGround(false);
		} else {
			double ground = groundY(level, nx, nz, e.getY(), owner.getY());
			ny = Math.abs(ground - e.getY()) > 3 ? ground : e.getY() + (ground - e.getY()) * 0.5;
			e.setOnGround(true);
		}
		e.setPos(nx, ny, nz);

		float yaw;
		if (step > 0.01) {
			yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			l.idleTicks = 0;
		} else {
			// idle: turn to look at you
			yaw = (float) (Mth.atan2(owner.getZ() - e.getZ(), owner.getX() - e.getX()) * Mth.RAD_TO_DEG) - 90f;
			l.idleTicks++;
		}
		float smooth = Mth.approachDegrees(e.getYRot(), yaw, 25f);
		e.setYRot(smooth);
		if (e instanceof LivingEntity living) {
			living.setYBodyRot(smooth);
			living.setYHeadRot(Mth.approachDegrees(living.getYHeadRot(), yaw, 35f));
			double dy = owner.getEyeY() - e.getEyeY();
			e.setXRot((float) (-Mth.atan2(dy, Math.max(0.5, toOwner)) * Mth.RAD_TO_DEG) * 0.6f);
			boolean baby = e instanceof AgeableMob m && m.isBaby();
			living.walkAnimation.update((float) Math.min(step * 4, 1.0), 0.4f, baby ? 3f : 1f);
		}
		setSitting(e, l.idleTicks > 80 && !type.flying);
		mirrorCrouch(e, owner);
	}

	/**
	 * Playing around the owner, the same routine as playing around a person: hopping round in
	 * circles, bouncing in front of them, nuzzling up close, then sitting down beside them. Each
	 * pet is a little out of step with the others.
	 */
	private static void play(Live l, Player owner, ClientLevel level, int slot) {
		Entity e = l.entity;
		PetType type = l.cfg.type();
		l.playTicks++;
		float t = l.playTicks / 20f + slot * 2.3f;
		int seg = dev.lmg.client.social.PartnerPlay.segment(t);
		float st = dev.lmg.client.social.PartnerPlay.segmentTime(t);
		double yawR = Math.toRadians(owner.yBodyRot);
		double fx = -Math.sin(yawR), fz = Math.cos(yawR);   // forward
		double rx = -Math.cos(yawR), rz = -Math.sin(yawR);  // right hand
		double side = slot % 2 == 0 ? 1 : -1;
		double tx, tz, hop;
		boolean faceOwner = true;
		switch (seg) {
			case 0 -> {
				// round and round
				double a = st * 1.5 + slot * 2.1;
				double r = 1.4 + slot * 0.35;
				tx = owner.getX() + Math.cos(a) * r;
				tz = owner.getZ() + Math.sin(a) * r;
				hop = Math.abs(Math.sin(st * 7 + slot)) * 0.5;
				faceOwner = false;
			}
			case 1 -> {
				// bouncing in front of you, excited
				tx = owner.getX() + fx * 1.1 + rx * side * 0.5 * slot;
				tz = owner.getZ() + fz * 1.1 + rz * side * 0.5 * slot;
				hop = Math.abs(Math.sin(st * 9 + slot)) * 0.7;
			}
			case 2 -> {
				// nuzzling up close
				tx = owner.getX() + rx * side * 0.6 + fx * 0.2;
				tz = owner.getZ() + rz * side * 0.6 + fz * 0.2;
				hop = Math.abs(Math.sin(st * 3)) * 0.06;
			}
			default -> {
				// a rest beside you
				tx = owner.getX() + rx * side * (0.9 + slot * 0.4) + fx * 0.2;
				tz = owner.getZ() + rz * side * (0.9 + slot * 0.4) + fz * 0.2;
				hop = 0;
			}
		}
		double nx = Mth.lerp(0.3, e.getX(), tx), nz = Mth.lerp(0.3, e.getZ(), tz);
		double base = type.flying ? owner.getY() + 1.3 + Math.sin(l.playTicks * 0.15) * 0.15
				: groundY(level, nx, nz, e.getY(), owner.getY());
		e.setPos(nx, base + hop, nz);
		e.setOnGround(hop < 0.02);
		double mx = nx - e.xo, mz = nz - e.zo;
		float yaw = faceOwner || mx * mx + mz * mz < 1e-4
				? (float) (Mth.atan2(owner.getZ() - nz, owner.getX() - nx) * Mth.RAD_TO_DEG) - 90f
				: (float) (Mth.atan2(mz, mx) * Mth.RAD_TO_DEG) - 90f;
		float smooth = Mth.approachDegrees(e.getYRot(), yaw, 30f);
		e.setYRot(smooth);
		if (e instanceof LivingEntity living) {
			living.setYBodyRot(smooth);
			living.setYHeadRot(Mth.approachDegrees(living.getYHeadRot(), yaw, 40f));
			boolean baby = e instanceof AgeableMob m && m.isBaby();
			living.walkAnimation.update((float) Math.min(Math.sqrt(mx * mx + mz * mz) * 4 + hop, 1.0), 0.4f, baby ? 3f : 1f);
		}
		setSitting(e, seg == 3 && st > 0.6f && !type.flying);
		mirrorCrouch(e, owner);
	}

	/** Lands on the floor near the pet: the first spot with something solid underneath. */
	private static double groundY(Level level, double x, double z, double petY, double ownerY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int start = (int) Math.floor(Math.max(petY, ownerY)) + 2;
		for (int y = start; y > start - 8; y--) {
			pos.set(Mth.floor(x), y - 1, Mth.floor(z));
			boolean solidBelow = !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
			pos.setY(y);
			boolean freeHere = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
			if (solidBelow && freeHere) {
				pos.setY(y - 1);
				double top = level.getBlockState(pos).getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
				return y - 1 + top;
			}
		}
		return ownerY;
	}

	private static void setSitting(Entity e, boolean sit) {
		if (e instanceof TamableAnimal t) t.setInSittingPose(sit);
		else if (e instanceof Fox f) f.setSitting(sit);
	}

	private static void mirrorCrouch(Entity e, Player owner) {
		if (e instanceof RemotePlayer p) p.setPose(owner.isCrouching() ? Pose.CROUCHING : Pose.STANDING);
	}

	// ---- creating pets ---------------------------------------------------------------------------

	private static Entity create(PetConfig cfg, ClientLevel level) {
		return create(cfg, level, Minecraft.getInstance().player);
	}

	/** Pets only exist on your screen, so they get ids the server never uses (negative ones). */
	private static int nextPetId = -1_000_000;

	private static Entity create(PetConfig cfg, ClientLevel level, Player owner) {
		try {
			PetType type = cfg.type();
			if (type == PetType.MINI_PLAYER) {
				String name = cfg.mine && owner != null ? owner.getGameProfile().name() : cfg.player;
				RemotePlayer mini = new RemotePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("lmg-pet:" + name).getBytes()), "Lmg pet"));
				mini.setId(nextPetId--);
				return mini;
			}
			Entity e = type.entity.create(level, EntitySpawnReason.LOAD);
			if (e == null) return null;
			e.setId(nextPetId--);
			if (e instanceof AgeableMob m) m.setBaby(cfg.baby);
			if (e instanceof TamableAnimal t) t.setTame(true, false);
			applyVariant(e, type, cfg.variant, level);
			DyeColor collar = DyeColor.byId(cfg.collar);
			if (type == PetType.WOLF) e.setComponent(DataComponents.WOLF_COLLAR, collar);
			if (type == PetType.CAT) e.setComponent(DataComponents.CAT_COLLAR, collar);
			e.setCustomName(null);
			return e;
		} catch (Exception ex) {
			LmgClient.LOGGER.warn("Couldn't make pet {}", cfg.type, ex);
			return null;
		}
	}

	// ---- variants --------------------------------------------------------------------------------

	private record VariantSet(DataComponentType<?> component, List<?> values) {}

	private static VariantSet variants(PetType type, Level level) {
		return switch (type) {
			case CAT -> registry(DataComponents.CAT_VARIANT, level, Registries.CAT_VARIANT);
			case WOLF -> registry(DataComponents.WOLF_VARIANT, level, Registries.WOLF_VARIANT);
			case FROG -> registry(DataComponents.FROG_VARIANT, level, Registries.FROG_VARIANT);
			case PIG -> registry(DataComponents.PIG_VARIANT, level, Registries.PIG_VARIANT);
			case COW -> registry(DataComponents.COW_VARIANT, level, Registries.COW_VARIANT);
			case CHICKEN -> registry(DataComponents.CHICKEN_VARIANT, level, Registries.CHICKEN_VARIANT);
			case PARROT -> new VariantSet(DataComponents.PARROT_VARIANT, Arrays.asList(Parrot.Variant.values()));
			case AXOLOTL -> new VariantSet(DataComponents.AXOLOTL_VARIANT, Arrays.asList(Axolotl.Variant.values()));
			case FOX -> new VariantSet(DataComponents.FOX_VARIANT, Arrays.asList(Fox.Variant.values()));
			case RABBIT -> new VariantSet(DataComponents.RABBIT_VARIANT, Arrays.asList(Rabbit.Variant.values()));
			case MOOSHROOM -> new VariantSet(DataComponents.MOOSHROOM_VARIANT, Arrays.asList(MushroomCow.Variant.values()));
			case LLAMA -> new VariantSet(DataComponents.LLAMA_VARIANT, Arrays.asList(Llama.Variant.values()));
			case SHEEP -> new VariantSet(DataComponents.SHEEP_COLOR, Arrays.asList(DyeColor.values()));
			default -> null;
		};
	}

	private static <T> VariantSet registry(DataComponentType<Holder<T>> component, Level level, ResourceKey<? extends net.minecraft.core.Registry<T>> key) {
		List<Holder<T>> list = new ArrayList<>(level.registryAccess().lookupOrThrow(key).listElements().toList());
		list.sort((a, b) -> a.unwrapKey().map(k -> k.identifier().toString()).orElse("")
				.compareTo(b.unwrapKey().map(k -> k.identifier().toString()).orElse("")));
		return new VariantSet(component, list);
	}

	@SuppressWarnings("unchecked")
	private static void applyVariant(Entity e, PetType type, int index, Level level) {
		VariantSet set = variants(type, level);
		if (set == null || set.values().isEmpty()) return;
		Object value = set.values().get(Math.floorMod(index, set.values().size()));
		e.setComponent((DataComponentType<Object>) set.component(), value);
	}

	/** How many looks this pet has (0 = just one). */
	public static int variantCount(PetType type) {
		Level level = Minecraft.getInstance().level;
		if (level == null || type == PetType.MINI_PLAYER) return 0;
		VariantSet set = variants(type, level);
		return set == null ? 0 : set.values().size();
	}

	public static String variantName(PetType type, int index) {
		Level level = Minecraft.getInstance().level;
		if (level == null) return "Default";
		VariantSet set = variants(type, level);
		if (set == null || set.values().isEmpty()) return "Default";
		Object v = set.values().get(Math.floorMod(index, set.values().size()));
		String raw = v instanceof Holder<?> h ? h.unwrapKey().map(k -> k.identifier().getPath()).orElse("?")
				: v instanceof Enum<?> en ? en.name() : String.valueOf(v);
		return pretty(raw);
	}

	static String pretty(String raw) {
		String s = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ---- drawing ---------------------------------------------------------------------------------

	/** Called at the end of world extraction: adds each pet to the list of entities to draw. */
	public static void extract(LevelExtractionContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer owner = mc.player;
		if (owner == null) return;
		boolean showMine = !owner.isInvisible() && !owner.isSpectator();
		float partial = ctx.deltaTracker().getGameTimeDeltaPartialTick(false);
		boolean firstPerson = !ctx.camera().isDetached();
		for (Live l : LIVE) {
			if (!showMine) break;
			if (l.cfg.attached() && firstPerson) continue;   // would sit right in front of your eyes
			EntityRenderState state = state(l, owner, partial);
			if (state != null) ctx.levelState().entityRenderStates.add(state);
		}
		if (OTHERS.isEmpty() || ctx.level() == null) return;
		for (Map.Entry<UUID, List<Live>> e : OTHERS.entrySet()) {
			Player other = ctx.level().getPlayerByUUID(e.getKey());
			if (other == null || other.isInvisible() || other.isSpectator()) continue;
			for (Live l : e.getValue()) {
				EntityRenderState state = state(l, other, partial);
				if (state != null) ctx.levelState().entityRenderStates.add(state);
			}
		}
	}

	/** The pet's render state for this frame (also used by the Pets screen preview). */
	private static EntityRenderState state(Live l, Player owner, float partial) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderState state;
		try {
			state = mc.getEntityRenderDispatcher().extractEntity(l.entity, partial);
		} catch (Exception ex) {
			LmgClient.LOGGER.debug("Couldn't draw pet {}", l.cfg.type, ex);
			return null;
		}
		PetConfig cfg = l.cfg;
		float size = PetConfig.SIZES[cfg.size];
		if (cfg.type() == PetType.MINI_PLAYER) size *= 0.75f;
		if (cfg.attached()) {
			// small enough to sit on you, whatever the animal
			size = Math.min(size, 0.42f / Math.max(0.3f, l.entity.getBbWidth()));
			double yaw = Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot);
			double headYaw = Mth.rotLerp(partial, owner.yHeadRotO, owner.yHeadRot);
			double x = Mth.lerp(partial, owner.xo, owner.getX()), y = Mth.lerp(partial, owner.yo, owner.getY()), z = Mth.lerp(partial, owner.zo, owner.getZ());
			double h = owner.getBbHeight();
			double r = Math.toRadians(cfg.mode.equals("HEAD") ? headYaw : yaw);
			double rx = -Math.cos(r), rz = -Math.sin(r);
			switch (cfg.mode) {
				case "LEFT_SHOULDER" -> { x -= rx * 0.36; z -= rz * 0.36; y += h * 0.78; }
				case "RIGHT_SHOULDER" -> { x += rx * 0.36; z += rz * 0.36; y += h * 0.78; }
				default -> y += h + 0.02;
			}
			state.x = x;
			state.y = y;
			state.z = z;
			if (state instanceof LivingEntityRenderState living) {
				living.bodyRot = (float) (cfg.mode.equals("HEAD") ? headYaw : yaw);
				living.yRot = 0;
			}
		}
		if (state instanceof LivingEntityRenderState living) living.scale *= size;
		state.shadowRadius *= size;
		if (cfg.attached()) state.shadowRadius = 0;
		state.isInvisible = false;

		if (cfg.name.isBlank() || (l.remote && !LmgConfig.get().showOtherPetNames)) {
			state.nameTag = null;
		} else {
			state.nameTag = Component.literal(cfg.name);
			state.nameTagAttachment = new Vec3(0, l.entity.getBbHeight() * size + 0.12, 0);
		}
		state.scoreText = null;

		if (state instanceof AvatarRenderState avatar) {
			PlayerSkin skin = !cfg.mine ? PetSkins.get(cfg.player)
					: owner == mc.player ? mySkin()
					: owner instanceof net.minecraft.client.player.AbstractClientPlayer acp ? acp.getSkin() : null;
			if (skin != null) avatar.skin = skin;
			avatar.showHat = avatar.showJacket = avatar.showLeftPants = avatar.showRightPants = true;
			avatar.showLeftSleeve = avatar.showRightSleeve = true;
			avatar.showCape = avatar.skin != null && avatar.skin.cape() != null;
			// a mini you wears your cosmetics too
			if (cfg.mine && cfg.cosmetics) avatar.id = owner.getId();
			else avatar.id = l.entity.getId();
		}
		return state;
	}

	/** Your own skin, including a custom skin and your equipped cape. */
	private static PlayerSkin mySkin() {
		Minecraft mc = Minecraft.getInstance();
		PlayerSkin base = mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
		PlayerSkin custom = CustomSkins.current();
		ClientAsset.Texture body = custom != null ? custom.body() : base.body();
		PlayerModelType model = custom != null ? custom.model() : base.model();
		ClientAsset.Texture cape = base.cape();
		CosmeticModel c = CosmeticRegistry.get(LmgConfig.get().equipped.get("capes"));
		if (c != null && c.capeTexture != null && !LmgConfig.get().hideCosmetics) {
			cape = new ClientAsset.ResourceTexture(LmgClient.id("cosmetic/" + c.capeTexture), LmgClient.id("textures/cosmetic/" + c.capeTexture));
		}
		return PlayerSkin.insecure(body, cape, base.elytra(), model);
	}

	/** A pet entity just for previews (the Pets screen), not following anyone. */
	public static EntityRenderState preview(PetConfig cfg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return null;
		String key = cfg.identity();
		Live l = PREVIEWS.get(key);
		if (l == null) {
			Entity e = create(cfg, mc.level);
			if (e == null) return null;
			l = new Live(cfg, e, mc.level);
			PREVIEWS.clear();
			PREVIEWS.put(key, l);
		}
		l.entity.tickCount++;
		PetConfig copy = new PetConfig();
		copy.type = cfg.type;
		copy.name = "";
		copy.size = 3;
		copy.mine = cfg.mine;
		copy.player = cfg.player;
		copy.cosmetics = cfg.cosmetics;
		Live shown = new Live(copy, l.entity, l.level);
		setSitting(l.entity, false);
		return state(shown, mc.player, 1f);
	}

	private static final Map<String, Live> PREVIEWS = new HashMap<>();

	// ---- skins for mini players ------------------------------------------------------------------

	/** Looks up other players' skins by name (in the background) and remembers them. */
	public static final class PetSkins {
		private static final Map<String, PlayerSkin> DONE = new ConcurrentHashMap<>();
		private static final Map<String, Boolean> STARTED = new ConcurrentHashMap<>();
		private static final Map<String, String> FAILED = new ConcurrentHashMap<>();

		private PetSkins() {}

		public static PlayerSkin get(String name) {
			String key = name.trim().toLowerCase(Locale.ROOT);
			if (key.isEmpty()) return null;
			PlayerSkin skin = DONE.get(key);
			if (skin != null) return skin;
			if (STARTED.putIfAbsent(key, true) == null) {
				Minecraft mc = Minecraft.getInstance();
				Util.nonCriticalIoPool().execute(() -> {
					try {
						Optional<GameProfile> profile = mc.services().profileResolver().fetchByName(name.trim());
						if (profile.isEmpty()) {
							FAILED.put(key, "No Minecraft player called " + name.trim());
							return;
						}
						mc.getSkinManager().get(profile.get()).thenAccept(s -> s.ifPresentOrElse(
								v -> DONE.put(key, v), () -> FAILED.put(key, "Couldn't load " + name.trim() + "'s skin")));
					} catch (Exception e) {
						FAILED.put(key, "Couldn't reach Minecraft's skin servers");
					}
				});
			}
			return null;
		}

		/** Why a skin couldn't be loaded, or null if it's fine / still loading. */
		public static String problem(String name) {
			return FAILED.get(name.trim().toLowerCase(Locale.ROOT));
		}

		public static boolean loading(String name) {
			String key = name.trim().toLowerCase(Locale.ROOT);
			return STARTED.containsKey(key) && !DONE.containsKey(key) && !FAILED.containsKey(key);
		}

		public static void retry(String name) {
			String key = name.trim().toLowerCase(Locale.ROOT);
			STARTED.remove(key);
			FAILED.remove(key);
		}
	}
}
