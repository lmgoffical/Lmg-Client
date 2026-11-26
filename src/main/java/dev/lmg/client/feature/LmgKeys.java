package dev.lmg.client.feature;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.screen.WorldMapScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Every Lmg key. They show up in Minecraft's Controls screen too, and in the Keys tab of the
 * Lmg menu where you can rebind them by clicking.
 */
public final class LmgKeys {
	public record Bind(KeyMapping key, String label, String hint) {}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LmgClient.id("main"));

	public static final KeyMapping OPEN_MENU = key("open_menu", InputConstants.KEY_RSHIFT);
	public static final KeyMapping ZOOM = key("zoom", InputConstants.KEY_C);
	public static final KeyMapping FULLBRIGHT = key("fullbright", InputConstants.KEY_B);
	public static final KeyMapping FREECAM = key("freecam", InputConstants.KEY_F6);
	public static final KeyMapping WORLD_MAP = key("world_map", InputConstants.KEY_M);
	public static final KeyMapping MINIMAP = key("minimap", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping WAYPOINT = key("waypoint", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping ORE_GLOW = key("ore_glow", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping COSMETICS = key("cosmetics", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping HUD_BAR = key("hud_bar", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping CINEMATIC = key("cinematic", InputConstants.KEY_F7);
	public static final KeyMapping FRIENDS = key("friends", InputConstants.KEY_O);
	public static final KeyMapping EMOTE = key("emote", InputConstants.KEY_G);
	public static final KeyMapping PHOTO = key("photo", InputConstants.KEY_F8);

	public static final List<Bind> ALL = List.of(
			new Bind(OPEN_MENU, "Open this menu", "Works anywhere in game."),
			new Bind(ZOOM, "Zoom (hold)", "Hold to zoom, scroll to zoom more."),
			new Bind(FULLBRIGHT, "Fullbright", "See in caves without torches."),
			new Bind(FREECAM, "Freecam", "Fly the camera. Your body stays put."),
			new Bind(WORLD_MAP, "World map", "Everywhere you've been, per world."),
			new Bind(MINIMAP, "Minimap on/off", "The little map in the corner."),
			new Bind(WAYPOINT, "Drop a waypoint", "Marks where you're standing."),
			new Bind(ORE_GLOW, "Ore glow on/off", "Outlines nearby ores you can already see."),
			new Bind(COSMETICS, "Hide cosmetics", "Quick way to see your plain skin."),
			new Bind(HUD_BAR, "HUD bar on/off", "The info bar at the top."),
			new Bind(CINEMATIC, "Cinematic mode", "Film bars, no HUD, smooth camera."),
			new Bind(FRIENDS, "Friends", "Who's online, and join them."),
			new Bind(EMOTE, "Emote wheel (hold)", "Hold, point, let go."),
			new Bind(PHOTO, "Photo mode", "Freeze, frame it, save it."));

	private LmgKeys() {}

	private static KeyMapping key(String name, int code) {
		return new KeyMapping("key.lmgclient." + name, InputConstants.Type.KEYBOARD, code, CATEGORY);
	}

	public static void register() {
		for (Bind b : ALL) KeyMappingHelper.registerKeyMapping(b.key());
	}

	/** Runs once per client tick. */
	public static void tick(Minecraft mc) {
		LmgConfig c = LmgConfig.get();
		while (OPEN_MENU.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new dev.lmg.client.screen.LmgSettingsScreen(null));
		}
		while (FULLBRIGHT.consumeClick()) {
			c.fullbright = !c.fullbright;
			say(c.fullbright ? "Fullbright on" : "Fullbright off");
			LmgConfig.save();
		}
		while (FREECAM.consumeClick()) Freecam.toggle();
		while (WORLD_MAP.consumeClick()) {
			if (mc.gui.screen() == null && mc.level != null) mc.gui.setScreen(new WorldMapScreen(null));
		}
		while (MINIMAP.consumeClick()) {
			c.minimap = !c.minimap;
			say(c.minimap ? "Minimap on" : "Minimap off");
			LmgConfig.save();
		}
		while (WAYPOINT.consumeClick()) {
			if (mc.player != null) {
				var w = dev.lmg.client.map.Waypoints.addHere();
				if (w != null) say("Waypoint \"" + w.name() + "\" added");
			}
		}
		while (ORE_GLOW.consumeClick()) {
			if (ServerSafe.blocks(ServerSafe.Feature.ORE_GLOW)) {
				say(ServerSafe.message(ServerSafe.Feature.ORE_GLOW));
				continue;
			}
			c.oreGlow = !c.oreGlow;
			say(c.oreGlow ? "Ore glow on" : "Ore glow off");
			LmgConfig.save();
		}
		while (COSMETICS.consumeClick()) {
			c.hideCosmetics = !c.hideCosmetics;
			say(c.hideCosmetics ? "Cosmetics hidden" : "Cosmetics showing");
			LmgConfig.save();
		}
		while (HUD_BAR.consumeClick()) {
			c.hudEnabled = !c.hudEnabled;
			LmgConfig.save();
		}
		while (FRIENDS.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new dev.lmg.client.screen.FriendsScreen(null));
		}
		while (EMOTE.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new dev.lmg.client.screen.EmoteWheelScreen());
		}
		while (PHOTO.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new dev.lmg.client.screen.PhotoModeScreen());
		}
		while (CINEMATIC.consumeClick()) {
			if (mc.player != null) Cinematic.toggle();
		}
		Freecam.tick(mc);
	}

	/** A short message above the hotbar. */
	public static void say(String text) {
		var player = Minecraft.getInstance().player;
		if (player != null) player.sendOverlayMessage(Component.literal(text));
	}
}
