package dev.lmg.client.feature;

import dev.lmg.client.LmgConfig;
import net.minecraft.client.Minecraft;

/**
 * Server-safe mode: on a multiplayer server, the features that look most like cheating are
 * switched off so people don't get banned for using a visual mod. You can turn it off, but
 * it's on by default and the menu says so.
 */
public final class ServerSafe {
	private ServerSafe() {}

	/** True on a multiplayer server (not your own singleplayer world or a LAN game you host). */
	public static boolean onMultiplayer() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && !mc.isLocalServer();
	}

	/** Whether a feature must stay off right now. */
	public static boolean blocks(Feature feature) {
		return LmgConfig.get().serverSafe && onMultiplayer() && feature.onServers;
	}

	/** Shown when someone presses a key that's blocked, so it's clear why nothing happened. */
	public static String message(Feature feature) {
		return feature.label + " is off on servers while server-safe mode is on (Tools tab).";
	}

	public enum Feature {
		FREECAM("Freecam", true),
		ORE_GLOW("Ore glow", true),
		FULLBRIGHT("Fullbright", false);

		public final String label;
		public final boolean onServers;

		Feature(String label, boolean onServers) {
			this.label = label;
			this.onServers = onServers;
		}
	}
}
