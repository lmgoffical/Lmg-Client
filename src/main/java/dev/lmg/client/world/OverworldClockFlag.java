package dev.lmg.client.world;

/** Added to the client's clock instances so we know which one is the overworld's day/night clock. */
public interface OverworldClockFlag {
	void lmg$setOverworld(boolean overworld);
}
