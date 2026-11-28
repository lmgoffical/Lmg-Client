package dev.lmg.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Remembers what the world looks like from above, a chunk at a time, as you explore.
 * Only uses chunks your game already has loaded (nothing extra is asked from the server).
 * Saved to .minecraft/config/lmgclient-maps/<world>/<dimension>/.
 */
public final class MapData {
	public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("lmgclient-maps");
	private static final int CHUNKS_PER_TICK = 10;

	private static final Map<Long, MapRegion> REGIONS = new HashMap<>();
	private static String worldKey;          // which world + dimension the regions belong to
	private static Path folder;
	private static int[][] spiral = new int[0][];
	private static int spiralRadius = -1, cursor, ticks;
	private static boolean wasDead;

	private MapData() {}

	// ---- per tick --------------------------------------------------------------------------------

	/** Far chunks the sweep mapped, and when (in ticks), so it doesn't keep redoing them. */
	private static final java.util.Map<Long, Integer> SWEPT = new java.util.HashMap<>();

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			if (worldKey != null) unload();
			return;
		}
		String key = keyFor(mc, level);
		if (!key.equals(worldKey)) {
			unload();
			worldKey = key;
			folder = ROOT.resolve(key);
			Waypoints.load(folder);
		}
		deathWaypoint(mc);

		int radius = Math.min(mc.options.getEffectiveRenderDistance(), 16);
		if (radius != spiralRadius) buildSpiral(radius);
		int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
		// The 25 chunks right around you are re-mapped every few ticks (spread out), the rest in a slow sweep.
		LmgConfig cfg = LmgConfig.get();
		int nearEvery = LmgConfig.SCAN_NEAR_EVERY[cfg.mapScan];
		int near = Math.min(spiral.length, 25);
		for (int i = ticks % nearEvery; i < near; i += nearEvery) {
			int[] o = spiral[i];
			scanChunk(level, pcx + o[0], pcz + o[1]);
		}
		// the sweep skips far chunks mapped in the last 30 seconds (they rarely change), so once the area
		// around you is mapped it costs almost nothing; a handful of skips per tick keeps it moving
		int budget = LmgConfig.SCAN_SWEEP[cfg.mapScan];
		for (int tries = 0; budget > 0 && tries < budget * 8; tries++) {
			int[] o = spiral[cursor];
			cursor = (cursor + 1) % spiral.length;
			long chunkKey = (((long) (pcx + o[0])) << 32) ^ ((pcz + o[1]) & 0xFFFFFFFFL);
			Integer last = SWEPT.get(chunkKey);
			if (last != null && ticks - last < 600) continue;
			SWEPT.put(chunkKey, ticks);
			scanChunk(level, pcx + o[0], pcz + o[1]);
			budget--;
		}
		if (ticks % 1200 == 0) SWEPT.values().removeIf(t -> ticks - t > 1200);
		ticks++;
		if (ticks % 4 == 0) for (MapRegion r : REGIONS.values()) r.upload();
		if (ticks % 1200 == 0) saveAll();
		if (ticks % 600 == 0 && !(mc.gui.screen() instanceof dev.lmg.client.screen.WorldMapScreen)) unloadFar(pcx >> 5, pcz >> 5);
	}

	/** Saves and forgets regions far from you so memory doesn't keep growing while you explore. */
	private static void unloadFar(int prx, int prz) {
		saveAll();
		var it = REGIONS.values().iterator();
		while (it.hasNext()) {
			MapRegion r = it.next();
			if (Math.abs(r.rx - prx) > 3 || Math.abs(r.rz - prz) > 3) {
				if (r == lastRegion) lastRegion = null;
				r.close();
				it.remove();
			}
		}
	}

	private static void deathWaypoint(Minecraft mc) {
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead && LmgConfig.get().deathWaypoint) Waypoints.addDeath(mc.player.blockPosition());
		wasDead = dead;
	}

	private static void buildSpiral(int r) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) list.add(new int[]{dx, dz});
		list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		spiral = list.toArray(new int[0][]);
		spiralRadius = r;
		cursor = 0;
	}

	// ---- scanning --------------------------------------------------------------------------------

	private static void scanChunk(ClientLevel level, int cx, int cz) {
		if (!level.hasChunk(cx, cz)) return;
		boolean ceiling = level.dimensionType().hasCeiling();
		int playerY = Minecraft.getInstance().player.blockPosition().getY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x0 = cx << 4, z0 = cz << 4;
		// heights of the row just north of this chunk, for the "lit from the north-west" shading
		int[] northHeight = new int[16];
		boolean northLoaded = level.hasChunk(cx, cz - 1);
		for (int i = 0; i < 16; i++) northHeight[i] = northLoaded ? surfaceY(level, x0 + i, z0 - 1, ceiling, playerY, pos) : Integer.MIN_VALUE;
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = x0 + dx, z = z0 + dz;
				int y = surfaceY(level, x, z, ceiling, playerY, pos);
				int color = 0;
				if (y > level.getMinY()) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					FluidState fluid = state.getFluidState();
					MapColor mc = state.getMapColor(level, pos);
					MapColor.Brightness b;
					if (!fluid.isEmpty() && mc == MapColor.WATER) {
						int depth = 0;
						while (depth < 12 && level.getBlockState(pos.move(0, -1, 0)).getFluidState().isEmpty() == false) depth++;
						b = depth < 2 ? MapColor.Brightness.HIGH : depth < 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
					} else {
						int n = northHeight[dx] == Integer.MIN_VALUE ? y : northHeight[dx];
						b = y > n ? MapColor.Brightness.HIGH : y < n ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
					}
					if (mc != MapColor.NONE) color = mc.calculateARGBColor(b) | 0xFF000000;
				}
				northHeight[dx] = y;
				region(x >> 9, z >> 9, true).set(x & 511, z & 511, color);
			}
		}
	}

	/** Y of the block you'd see from above (or, under a ceiling like the Nether's, the floor you're on). */
	private static int surfaceY(ClientLevel level, int x, int z, boolean ceiling, int playerY, BlockPos.MutableBlockPos pos) {
		int y;
		if (ceiling) {
			y = Math.min(playerY + 1, level.getMaxY());
			pos.set(x, y, z);
			while (y > level.getMinY() && !level.getBlockState(pos).isAir()) pos.setY(--y);   // inside a wall: find air
			while (y > level.getMinY() && level.getBlockState(pos).isAir()) pos.setY(--y);    // then fall to the floor
		} else {
			y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			pos.set(x, y, z);
		}
		while (y > level.getMinY() && level.getBlockState(pos).getMapColor(level, pos) == MapColor.NONE) pos.setY(--y);
		return y;
	}

	// ---- regions ---------------------------------------------------------------------------------

	public static MapRegion region(int rx, int rz, boolean create) {
		long k = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
		MapRegion r = REGIONS.get(k);
		if (r == null && create) {
			r = new MapRegion(rx, rz, loadImage(rx, rz));
			REGIONS.put(k, r);
		}
		return r;
	}

	/** Colour of the block at x, z (ARGB), or 0 if it isn't mapped. */
	public static int colorAt(int x, int z) {
		// the minimap asks for thousands of neighbouring pixels: remember the last region
		long key = ((long) (x >> 9) << 32) ^ ((z >> 9) & 0xFFFFFFFFL);
		MapRegion last = lastRegion;
		if (last != null && key == lastKey) return last.get(x & 511, z & 511);
		MapRegion r = region(x >> 9, z >> 9, false);
		if (r != null) {
			lastKey = key;
			lastRegion = r;
		}
		if (r == null) {
			if (!hasFile(x >> 9, z >> 9)) return 0;
			r = region(x >> 9, z >> 9, true);
		}
		return r.get(x & 511, z & 511);
	}

	private static final Map<Long, Boolean> FILE_CACHE = new HashMap<>();
	private static long lastKey;
	private static MapRegion lastRegion;

	private static boolean hasFile(int rx, int rz) {
		if (folder == null) return false;
		long k = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
		return FILE_CACHE.computeIfAbsent(k, kk -> Files.isRegularFile(file(rx, rz)));
	}

	/** Makes sure every saved region in this rectangle (in region coordinates) is loaded. */
	public static void loadArea(int rx0, int rz0, int rx1, int rz1) {
		for (int rx = rx0; rx <= rx1; rx++) for (int rz = rz0; rz <= rz1; rz++) {
			if (region(rx, rz, false) == null && hasFile(rx, rz)) region(rx, rz, true);
		}
	}

	public static Iterable<MapRegion> regions() {
		return REGIONS.values();
	}

	private static Path file(int rx, int rz) {
		return folder.resolve(rx + "_" + rz + ".png");
	}

	private static NativeImage loadImage(int rx, int rz) {
		if (folder == null) return null;
		Path f = file(rx, rz);
		if (!Files.isRegularFile(f)) return null;
		try (InputStream in = Files.newInputStream(f)) {
			NativeImage img = NativeImage.read(in);
			if (img.getWidth() == MapRegion.SIZE && img.getHeight() == MapRegion.SIZE) return img;
			img.close();
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not read map region {}", f, e);
		}
		return null;
	}

	public static void saveAll() {
		if (folder == null) return;
		try {
			Files.createDirectories(folder);
		} catch (Exception e) {
			return;
		}
		for (MapRegion r : REGIONS.values()) {
			if (!r.needsSave) continue;
			try {
				r.image().writeToFile(file(r.rx, r.rz));
				r.needsSave = false;
				FILE_CACHE.put(((long) r.rx << 32) ^ (r.rz & 0xFFFFFFFFL), true);
			} catch (Exception e) {
				LmgClient.LOGGER.warn("Could not save map region {},{}", r.rx, r.rz, e);
			}
		}
	}

	private static void unload() {
		saveAll();
		Waypoints.save();
		for (MapRegion r : REGIONS.values()) r.close();
		REGIONS.clear();
		SWEPT.clear();
		lastRegion = null;
		FILE_CACHE.clear();
		worldKey = null;
		folder = null;
	}

	// ---- naming ----------------------------------------------------------------------------------

	private static String keyFor(Minecraft mc, ClientLevel level) {
		String world;
		if (mc.getSingleplayerServer() != null) world = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
		else if (mc.getCurrentServer() != null) world = "mp_" + mc.getCurrentServer().ip;
		else world = "other";
		String dim = level.dimension().identifier().toString();
		return clean(world) + "/" + clean(dim);
	}

	private static String clean(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
	}

	public static boolean ready() {
		return worldKey != null;
	}
}
