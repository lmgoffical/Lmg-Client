package dev.lmg.legacy.feature;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

/** Lmg's keys. They show up in Options > Controls under "Lmg Client" and can be changed there. */
public final class Keys {
	public static final KeyBinding MENU = new KeyBinding("Open Lmg menu", Keyboard.KEY_RSHIFT, "Lmg Client");
	public static final KeyBinding ZOOM = new KeyBinding("Zoom", Keyboard.KEY_C, "Lmg Client");
	public static final KeyBinding HUD_EDITOR = new KeyBinding("Edit HUD", Keyboard.KEY_NONE, "Lmg Client");

	private Keys() {}

	public static void register() {
		ClientRegistry.registerKeyBinding(MENU);
		ClientRegistry.registerKeyBinding(ZOOM);
		ClientRegistry.registerKeyBinding(HUD_EDITOR);
	}
}
