package dev.lmg.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Quads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Ore glow": draws glowing edges, in the ore's color, on ore blocks near you that are
 * actually exposed (touching air, water, etc). The edges are depth-tested like normal
 * blocks, so ores hidden behind stone are NOT shown - it just makes visible ores easy to
 * spot in dark caves.
 */
public final class OreGlow {
	private static final int RADIUS = 14;
	private static final int MAX_BLOCKS = 350;
	private static final Map<Block, Integer> COLORS = new HashMap<>();
	private static List<long[]> found = new ArrayList<>(); // {packed pos, rgb}
	private static int tick;
	private static BlockPos lastCenter;
	private static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> ORE = st -> colorOf(st.getBlock()) != 0;

	private OreGlow() {}

	private static int colorOf(Block block) {
		return COLORS.computeIfAbsent(block, b -> {
			String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (id.equals("ancient_debris")) return 0xC878FF;
			if (!id.endsWith("_ore")) return 0;
			if (id.contains("diamond")) return 0x3CF0F0;
			if (id.contains("emerald")) return 0x1EF05A;
			if (id.contains("gold")) return 0xFFDC28;
			if (id.contains("redstone")) return 0xFF2020;
			if (id.contains("lapis")) return 0x2860FF;
			if (id.contains("copper")) return 0xF07838;
			if (id.contains("iron")) return 0xECB28C;
			if (id.contains("quartz")) return 0xFFFFFF;
			if (id.contains("coal")) return 0x707070;
			return 0xFFFFFF;
		});
	}

	public static void tick(Minecraft mc) {
		if (!LmgConfig.get().oreGlow || mc.player == null || mc.level == null
				|| dev.lmg.client.feature.ServerSafe.blocks(dev.lmg.client.feature.ServerSafe.Feature.ORE_GLOW)) {
			found = new ArrayList<>();
			lastCenter = null;
			return;
		}
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		BlockPos center = player.blockPosition();
		// Ores don't move: rescan when you've moved a couple of blocks, or every 2 seconds.
		tick++;
		boolean moved = lastCenter == null || lastCenter.distManhattan(center) >= 2;
		if (!moved && tick % 40 != 0) return;
		lastCenter = center;
		List<long[]> result = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
		int minX = center.getX() - RADIUS, maxX = center.getX() + RADIUS;
		int minY = Math.max(level.getMinY(), center.getY() - RADIUS), maxY = Math.min(level.getMaxY(), center.getY() + RADIUS);
		int minZ = center.getZ() - RADIUS, maxZ = center.getZ() + RADIUS;
		// Go a chunk section (16x16x16) at a time and skip sections with no ore in them at all.
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
				if (chunk == null) continue;
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSections().length) continue;
					LevelChunkSection section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(ORE)) continue;
					int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
					int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
					int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
					for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
						int rgb = colorOf(section.getBlockState(x & 15, y & 15, z & 15).getBlock());
						if (rgb == 0) continue;
						pos.set(x, y, z);
						boolean exposed = false;
						for (Direction d : Direction.values()) {
							side.setWithOffset(pos, d);
							if (!level.getBlockState(side).canOcclude()) {
								exposed = true;
								break;
							}
						}
						int dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
						if (exposed) result.add(new long[]{pos.asLong(), rgb, (long) (dx * dx + dy * dy + dz * dz)});
					}
				}
			}
		}
		result.sort((a, b) -> Long.compare(a[2], b[2]));
		found = result.size() > MAX_BLOCKS ? new ArrayList<>(result.subList(0, MAX_BLOCKS)) : result;
	}

	public static void render(LevelRenderContext ctx) {
		List<long[]> blocks = found;
		if (blocks.isEmpty()) return;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		ctx.submitNodeCollector().submitCustomGeometry(pose,
				RenderTypes.entityTranslucentEmissive(LmgClient.id("textures/cosmetic/white.png")), (p, vc) -> {
					for (long[] b : blocks) {
						BlockPos bp = BlockPos.of(b[0]);
						int argb = 0xE6000000 | (int) b[1];
						float x = (float) (bp.getX() - cam.x), y = (float) (bp.getY() - cam.y), z = (float) (bp.getZ() - cam.z);
						Quads.boxEdges(vc, p, x, y, z, x + 1, y + 1, z + 1, 0.035f, argb, Quads.FULL_BRIGHT);
					}
				});
	}
}
