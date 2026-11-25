package dev.lmg.client.world;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Turns Lmg Client's built-in resource packs on or off (same as doing it in
 * Options -> Resource Packs, then reloading textures).
 * - "ore_outlines": ore textures with the block edge in the ore's color (textures only, no x-ray)
 * - "lmg_totem":    the Star of David totem
 */
public final class OreOutlines {
	public static final String ORES = "ore_outlines";
	public static final String TOTEM = "lmg_totem";

	private OreOutlines() {}

	private static String packId(String name) {
		for (String id : Minecraft.getInstance().getResourcePackRepository().getAvailableIds()) {
			if (id.endsWith(name)) return id;
		}
		return null;
	}

	public static boolean isEnabled(String name) {
		String id = packId(name);
		return id != null && Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(id);
	}

	public static void setEnabled(String name, boolean enabled) {
		String id = packId(name);
		if (id == null) return;
		Minecraft mc = Minecraft.getInstance();
		PackRepository repo = mc.getResourcePackRepository();
		boolean changed = enabled ? repo.addPack(id) : repo.removePack(id);
		if (changed) mc.options.updateResourcePacks(repo); // saves options.txt and reloads textures
	}

	// Ore edges (kept for the settings row)
	public static boolean isEnabled() {
		return isEnabled(ORES);
	}

	public static void setEnabled(boolean enabled) {
		setEnabled(ORES, enabled);
	}
}
