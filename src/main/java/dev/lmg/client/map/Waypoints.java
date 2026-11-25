package dev.lmg.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.lmg.client.LmgClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Your waypoints for the current world and dimension. */
public final class Waypoints {
	public record Waypoint(String name, int x, int y, int z, int color) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int[] COLORS = {0x3D9BFF, 0xF2C14E, 0x4FD49A, 0xFF6B78, 0xB07CFF, 0xFF8FC8};
	private static final List<Waypoint> LIST = new ArrayList<>();
	private static Path file;

	private Waypoints() {}

	public static List<Waypoint> all() {
		return LIST;
	}

	static void load(Path folder) {
		LIST.clear();
		file = folder.resolve("waypoints.json");
		try {
			if (Files.isRegularFile(file)) {
				List<Waypoint> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Waypoint>>() {}.getType());
				if (read != null) LIST.addAll(read);
			}
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not read waypoints", e);
		}
	}

	public static void save() {
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(LIST), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not save waypoints", e);
		}
	}

	public static Waypoint addHere() {
		var p = Minecraft.getInstance().player;
		if (p == null || !MapData.ready()) return null;
		return add(p.blockPosition().getX(), p.blockPosition().getY(), p.blockPosition().getZ());
	}

	public static Waypoint add(int x, int y, int z) {
		int n = 1;
		for (Waypoint w : LIST) if (w.name().startsWith("Waypoint ")) n++;
		Waypoint w = new Waypoint("Waypoint " + n, x, y, z, COLORS[LIST.size() % COLORS.length]);
		LIST.add(w);
		save();
		return w;
	}

	static void addDeath(BlockPos pos) {
		LIST.removeIf(w -> w.name().equals("Last death"));
		LIST.add(new Waypoint("Last death", pos.getX(), pos.getY(), pos.getZ(), 0xFF6B78));
		save();
	}

	public static void remove(Waypoint w) {
		LIST.remove(w);
		save();
	}
}
