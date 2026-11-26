package dev.lmg.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Quads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Winter, only on your screen: snow falls in every overworld biome (not deserts, savannas or
 * badlands), and a thin layer of snow settles on the ground and treetops around you - patchy at
 * first, then everywhere. Switch it off and it melts away. Nothing in the real world changes, so
 * it's fine on any server and never touches your singleplayer worlds.
 */
public final class Winter {
	public enum Mode {
		OFF("Off"), SEASONAL("Seasonal"), ALWAYS("On");
		public final String label;

		Mode(String label) {
			this.label = label;
		}
	}

	private static final int RADIUS = 40;
	private static final Identifier SNOW_TEXTURE = Identifier.withDefaultNamespace("textures/block/snow.png");
	/** How settled the snow is: 0 = none, 1 = everywhere (takes about a minute and a half). */
	private static float cover;
	private static int ticks;
	private static BlockPos lastCenter;
	/** {x, top (in 1/16ths), z, light, sides to draw as bits, which of the 4x4 patches are covered} per snowy column. */
	private static List<int[]> columns = new ArrayList<>();

	private Winter() {}

	/** Is it winter right now, here? */
	public static boolean snowing(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || level.dimension() != Level.OVERWORLD) return false;
		Mode m = LmgConfig.get().winter;
		if (m == Mode.OFF || m == null) return false;
		if (m == Mode.SEASONAL) {
			LocalDate d = LocalDate.now();
			int mo = d.getMonthValue(), day = d.getDayOfMonth();
			if (!(mo == 12 || mo <= 2 || (mo == 11 && day >= 20) || (mo == 3 && day <= 5))) return false;
		}
		return true;
	}

	/** Dry places stay dry (deserts, savannas, badlands). */
	public static boolean snowsAt(ClientLevel level, BlockPos pos) {
		Biome biome = level.getBiome(pos).value();
		return biome.hasPrecipitation();
	}

	/** How hard it snows (0..1) on top of whatever the real weather is doing. */
	public static float intensity() {
		return 0.55f + 0.45f * Math.min(1f, cover * 1.5f);
	}

	public static void tick(Minecraft mc) {
		boolean on = snowing(mc) && mc.player != null;
		boolean here = on && snowsAt(mc.level, mc.player.blockPosition());
		// settles over ~90 seconds, melts in ~30
		cover = Math.max(0f, Math.min(1f, cover + (here ? 1f / 1800f : -1f / 600f)));
		if (!LmgConfig.get().winterSettles || cover <= 0f || mc.player == null || mc.level == null) {
			columns = new ArrayList<>();
			lastCenter = null;
			return;
		}
		ticks++;
		BlockPos center = mc.player.blockPosition();
		boolean moved = lastCenter == null || lastCenter.distManhattan(center) >= 3;
		if (!moved && ticks % 20 != 0) return;
		lastCenter = center;
		columns = scan(mc.level, center);
	}

	private static List<int[]> scan(ClientLevel level, BlockPos center) {
		List<int[]> out = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[][] tops = new int[RADIUS * 2 + 3][RADIUS * 2 + 3];
		// first: the height of the snow surface in every column (in 1/16ths of a block), or MIN if none
		for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
			for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
				tops[dx + RADIUS + 1][dz + RADIUS + 1] = surface(level, pos, center.getX() + dx, center.getZ() + dz);
			}
		}
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > RADIUS * RADIUS) continue;
				int x = center.getX() + dx, z = center.getZ() + dz;
				int top = tops[dx + RADIUS + 1][dz + RADIUS + 1];
				if (top == Integer.MIN_VALUE) continue;
				// patchy at first: each spot (and each quarter-of-a-quarter of it) has its own turn to get
				// snow, so it starts as speckles and grows together; it also thins out at the edge
				float edge = 1f - Math.max(0f, (float) Math.sqrt(d2) / RADIUS - 0.7f) / 0.3f;
				float c = cover * edge;
				float need = hash(x, z) * 0.7f;
				if (need > c) continue;
				int mask = 0xFFFF;
				if (need + 0.3f > c) {
					mask = 0;
					for (int i = 0; i < 16; i++) if (need + hash(x * 4 + (i & 3), z * 4 + (i >> 2)) * 0.3f <= c) mask |= 1 << i;
					if (mask == 0) continue;
				}
				pos.set(x, Math.floorDiv(top, 16), z);
				int light = LightCoordsUtil.getLightCoords(level, pos.above());
				// sides: only where the neighbour's snow is lower (a step), so you see the layer's edge
				int sides = 0;
				int[][] n = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
				for (int i = 0; i < 4; i++) {
					int nt = tops[dx + n[i][0] + RADIUS + 1][dz + n[i][1] + RADIUS + 1];
					if (nt == Integer.MIN_VALUE || nt < top) sides |= 1 << i;
				}
				if (mask != 0xFFFF) sides = 0;
				out.add(new int[]{x, top, z, light, sides, mask});
			}
		}
		return out;
	}

	/** Where snow would rest in this column, in 1/16ths of a block, or MIN_VALUE if it wouldn't. */
	private static int surface(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
		if (y < level.getMinY()) return Integer.MIN_VALUE;
		pos.set(x, y, z);
		if (!snowsAt(level, pos)) return Integer.MIN_VALUE;
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().isEmpty()) return Integer.MIN_VALUE;          // not on water or lava
		if (state.is(net.minecraft.world.level.block.Blocks.SNOW) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
				|| state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return Integer.MIN_VALUE;   // real snow already
		// only on a full, flat top (ground, leaves, roofs) - not lanterns, fences, flowers or half-slabs
		if (!state.isFaceSturdy(level, pos, Direction.UP)) return Integer.MIN_VALUE;
		return (y + 1) * 16;
	}

	private static float hash(int x, int z) {
		int h = x * 73856093 ^ z * 19349663;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return (h & 0xFFFF) / 65535f;
	}

	public static void render(LevelRenderContext ctx) {
		List<int[]> list = columns;
		if (list.isEmpty()) return;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		// a layer about 1.5 pixels thick (2 when it's fully settled)
		float thick = (1f + Math.min(1f, cover) * 1.2f) / 16f;
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityCutout(SNOW_TEXTURE), (p, vc) -> {
			for (int[] c : list) {
				float x0 = (float) (c[0] - cam.x), z0 = (float) (c[2] - cam.z);
				float y0 = (float) (c[1] / 16.0 - cam.y) + 0.002f, y1 = y0 + thick;
				float x1 = x0 + 1, z1 = z0 + 1;
				// each block's snow is turned and shaded a little differently, so there's no grid
				int h = (int) (hash(c[0] * 31 + 7, c[2] * 17 + 3) * 65535);
				int rot = h & 3;
				boolean flip = (h & 4) != 0;
				float shade = 0.93f + ((h >> 3) & 15) / 15f * 0.07f;
				int sc = Math.round(255 * shade);
				int light = c[3], argb = 0xFF000000 | sc << 16 | sc << 8 | Math.min(255, sc + 4), sideArgb = 0xFFE4ECF4;
				float v1 = thick;   // texture rows for the thin sides
				int mask = c[5];
				if (mask == 0xFFFF) {
					top(vc, p, x0, y1, z0, 1f, 0f, 0f, 1f, rot, flip, argb, light);
				} else {
					// still settling: only the covered patches, a little thinner
					float ty = y0 + thick * 0.6f;
					for (int i = 0; i < 16; i++) {
						if ((mask & 1 << i) == 0) continue;
						float px0 = x0 + (i & 3) * 0.25f, pz0 = z0 + (i >> 2) * 0.25f, px1 = px0 + 0.25f, pz1 = pz0 + 0.25f;
						float u0 = (i & 3) * 0.25f, v0 = (i >> 2) * 0.25f;
						top(vc, p, px0, ty, pz0, 0.25f, u0, v0, 0.25f, rot, flip, argb, light);
					}
				}
				int s = c[4];
				if ((s & 1) != 0) Quads.quad(vc, p, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, 0, 0, 1, v1, sideArgb, light, 0, 0, -1);
				if ((s & 2) != 0) Quads.quad(vc, p, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, 0, 0, 1, v1, sideArgb, light, 0, 0, 1);
				if ((s & 4) != 0) Quads.quad(vc, p, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, 0, 0, 1, v1, sideArgb, light, -1, 0, 0);
				if ((s & 8) != 0) Quads.quad(vc, p, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, 0, 0, 1, v1, sideArgb, light, 1, 0, 0);
			}
		});
	}

	/** A flat square of snow, its texture turned by {@code rot} quarter turns (and maybe mirrored). */
	private static void top(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float size,
							float u, float v, float uvSize, int rot, boolean flip, int argb, int light) {
		float[][] uv = {{u, v}, {u + uvSize, v}, {u + uvSize, v + uvSize}, {u, v + uvSize}};
		float[][] at = {{x, z + size}, {x + size, z + size}, {x + size, z}, {x, z}};
		for (int k = 0; k < 4; k++) {
			int i = flip ? (3 - k + rot) & 3 : (k + rot) & 3;
			Quads.vertex(vc, p, at[k][0], y, at[k][1], uv[i][0], uv[i][1], argb, light, 0, 1, 0);
		}
	}

	/** For the in-game tests: skip the wait. */
	public static void settleNow() {
		cover = 1f;
		lastCenter = null;
	}
}
