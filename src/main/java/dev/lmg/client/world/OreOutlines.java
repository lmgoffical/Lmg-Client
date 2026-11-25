package dev.lmg.client.world;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Turns the built-in "Lmg Ore Outlines" resource pack on or off (same as doing it in
 * Options -> Resource Packs, then reloading). It only recolors ore textures; it does not
 * show ores through walls.
 */
public final class OreOutlines {
	private OreOutlines() {}

	private static String packId() {
		PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
		for (String id : repo.getAvailableIds()) {
			if (id.endsWith("ore_outlines")) return id;
		}
		return null;
	}

	public static boolean isEnabled() {
		String id = packId();
		return id != null && Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(id);
	}

	public static void setEnabled(boolean enabled) {
		String id = packId();
		if (id == null) return;
		Minecraft mc = Minecraft.getInstance();
		PackRepository repo = mc.getResourcePackRepository();
		boolean changed = enabled ? repo.addPack(id) : repo.removePack(id);
		if (changed) mc.options.updateResourcePacks(repo); // saves options.txt and reloads textures
	}
}
