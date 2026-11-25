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
			return;
		}
		if (tick++ % 10 != 0) return; // rescan twice a second
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		BlockPos center = player.blockPosition();
		List<long[]> result = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
		for (int dy = -RADIUS; dy <= RADIUS; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					int rgb = colorOf(level.getBlockState(pos).getBlock());
					if (rgb == 0) continue;
					boolean exposed = false;
					for (Direction d : Direction.values()) {
						side.setWithOffset(pos, d);
						if (!level.getBlockState(side).canOcclude()) {
							exposed = true;
							break;
						}
					}
					if (exposed) result.add(new long[]{pos.asLong(), rgb, (long) (dx * dx + dy * dy + dz * dz)});
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
