package dev.lmg.legacy.feature;

import dev.lmg.legacy.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;

/** Toggle sprint (always sprint when you walk forward) and fullbright. Both just press keys / set brightness for you. */
public final class PvpTweaks {
	private PvpTweaks() {}

	public static void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		Config c = Config.get();
		EntityPlayerSP p = mc.thePlayer;
		if (p != null && c.toggleSprint && mc.currentScreen == null) {
			boolean canSprint = p.movementInput != null && p.movementInput.moveForward > 0 && !p.isSneaking()
					&& !p.isCollidedHorizontally && (p.getFoodStats().getFoodLevel() > 6 || p.capabilities.allowFlying);
			if (canSprint) KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
		}
		// fullbright: remember your brightness, turn it way up, put it back when switched off
		if (c.fullbright) {
			if (c.savedGamma < 0) c.savedGamma = mc.gameSettings.gammaSetting;
			mc.gameSettings.gammaSetting = 16f;
		} else if (c.savedGamma >= 0) {
			mc.gameSettings.gammaSetting = c.savedGamma;
			c.savedGamma = -1f;
		}
	}

	public static boolean sprinting() {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		return p != null && p.isSprinting();
	}
}
