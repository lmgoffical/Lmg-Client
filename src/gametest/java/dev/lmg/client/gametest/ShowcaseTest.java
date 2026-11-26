package dev.lmg.client.gametest;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.screen.FriendsScreen;
import dev.lmg.client.screen.LmgSettingsScreen;
import dev.lmg.client.social.FriendToast;
import dev.lmg.client.social.Social;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Plays the game for real and takes screenshots of each feature.
 * Run: SDL_VIDEO_DRIVER=offscreen LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runClientGameTest
 * Pick what to run with -Dlmg.test=friends,menu (default: everything).
 */
public class ShowcaseTest implements FabricClientGameTest {
	private static final String ONLY = System.getProperty("lmg.test", System.getenv().getOrDefault("LMG_TEST", "all"));

	private static boolean want(String name) {
		return ONLY.equals("all") || ("," + ONLY + ",").contains("," + name + ",");
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.welcomeSeen = true;
			c.showSplash = false;
			c.tourDone = true;
		});
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(5);

		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 1000");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			ctx.waitTicks(20);

			if (want("menu")) {
				scene(world);
				world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
				ctx.waitTicks(10);
				world.getConnection().waitForChunksRender();
				page(5, 0);   // Menu > Look, where the weather setting is
				for (var w : new dev.lmg.client.render.MenuSnow.Weather[]{dev.lmg.client.render.MenuSnow.Weather.SNOW,
						dev.lmg.client.render.MenuSnow.Weather.LEAVES, dev.lmg.client.render.MenuSnow.Weather.PETALS,
						dev.lmg.client.render.MenuSnow.Weather.FIREFLIES}) {
					ctx.runOnClient(mc -> LmgConfig.get().menuWeather = w);
					if (w == dev.lmg.client.render.MenuSnow.Weather.FIREFLIES) world.getServer().runCommand("time set 14500");
					ctx.setScreen(() -> new LmgSettingsScreen(null));
					ctx.waitTicks(w == dev.lmg.client.render.MenuSnow.Weather.FIREFLIES ? 40 : 240);
					ctx.takeScreenshot("menu-" + w.name().toLowerCase());
					ctx.setScreen(() -> null);
					ctx.waitTicks(40);   // long enough that the weather starts fresh next time
				}
				ctx.runOnClient(mc -> LmgConfig.get().menuWeather = dev.lmg.client.render.MenuSnow.Weather.AUTO);
				world.getServer().runCommand("time set 1000");
			}
			if (want("friends") || want("partner")) friends(ctx);
			if (want("partner") || want("emotes")) partner(ctx, world);
			if (want("emotes")) emotes(ctx);
			if (want("photo")) photo(ctx, world);
			if (want("avatar")) avatar(ctx, world);
			if (want("winter")) winter(ctx, world);
			if (want("death")) death(ctx, world);
			if (want("newmenu")) newMenu(ctx, world);
			if (want("tour")) tour(ctx);
			if (want("effects")) effects(ctx, world);
			if (want("items")) items(ctx, world);
			if (want("xhair")) xhair(ctx, world);
			if (want("tourskip")) tourSkip(ctx);
			if (want("v29menu")) v29menu(ctx, world);
			if (want("v29hud")) v29hud(ctx, world);
			if (want("v29world")) v29world(ctx, world);
			if (want("v29photo")) v29photo(ctx, world);
			if (want("cosmetics")) cosmetics(ctx);
		}
		ctx.runOnClient(mc -> LmgConfig.get().welcomeSeen = true);
	}

	private void friends(ClientGameTestContext ctx) {
		// the intro, before friends are switched on
		ctx.runOnClient(mc -> LmgConfig.get().friendsEnabled = false);
		ctx.setScreen(() -> new FriendsScreen(null));
		ctx.waitTicks(30);
		ctx.takeScreenshot("friends-intro");

		// signed in with a test account (Mojang sign-in can't run here)
		String token = testToken();
		if (token == null) return;
		ctx.runOnClient(mc -> {
			LmgConfig.get().friendsEnabled = true;
			try {
				Method m = Social.class.getDeclaredMethod("testSession", UUID.class, String.class);
				m.setAccessible(true);
				m.invoke(null, UUID.fromString("11111111-1111-4111-8111-111111111111"), token);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		ctx.setScreen(() -> new FriendsScreen(null));
		ctx.waitFor(mc -> !Social.state().friends().isEmpty(), 400);
		ctx.waitTicks(40); // faces load
		ctx.takeScreenshot("friends-list");
		// hover the partner row to show its buttons
		ctx.getInput().setCursorPos(640, 462);
		ctx.waitTicks(10);
		ctx.takeScreenshot("friends-hover");
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> FriendToast.show(UUID.fromString("22222222-2222-4222-8222-222222222222"), "Mia_Builds", "is online"));
		ctx.waitTicks(25);
		ctx.takeScreenshot("friends-toast");
		ctx.waitTicks(140);
	}

	/** Opens the menu on a group and page next time (0 Visuals, 1 You, 2 Combat, 3 HUD, 4 Social, 5 Menu, 6 Game). */
	static void page(int cat, int page) {
		try {
			var c = LmgSettingsScreen.class.getDeclaredField("lastCat");
			c.setAccessible(true);
			c.setInt(null, cat);
			var p = LmgSettingsScreen.class.getDeclaredField("lastPage");
			p.setAccessible(true);
			((int[]) p.get(null))[cat] = page;
			var q = LmgSettingsScreen.class.getDeclaredField("search");
			q.setAccessible(true);
			q.set(null, null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static final UUID MIA = UUID.fromString("22222222-2222-4222-8222-222222222222");

	/** Your partner standing next to you: heart on her name tag, hearts between you, matching outfits. */
	private void partner(ClientGameTestContext ctx, TestSingleplayerContext world) {
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.showOthers = true;
			c.matchPartner = true;
			c.partnerHearts = true;
			c.cosmeticColors.put("wings_butterfly", "#5DA9FF");
			c.cosmeticColors.put("ears_cat", "#5DA9FF");
		});
		world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			var p = mc.player;
			var mia = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(MIA, "Mia_Builds"));
			mia.setId(-424242);
			mia.snapTo(p.getX() + 1.3, p.getY(), p.getZ(), 0f, 0f);
			mia.setYHeadRot(-12f);
			mia.setYBodyRot(0f);
			mc.level.addEntity(mia);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitFor(mc -> dev.lmg.client.sync.AvatarSync.getAlways(MIA) != null, 600);
		ctx.waitTicks(80);
		ctx.takeScreenshot("partner-front");
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(30);
		ctx.takeScreenshot("partner-back");
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
	}

	private void emotes(ClientGameTestContext ctx) {
		// the wheel, pointing at Dance
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		ctx.setScreen(dev.lmg.client.screen.EmoteWheelScreen::new);
		ctx.getInput().setCursorPos(795, 492);
		ctx.waitTicks(20);
		ctx.takeScreenshot("emote-wheel");
		ctx.setScreen(() -> null);
		ctx.waitTicks(5);
		for (dev.lmg.client.emote.Emote e : dev.lmg.client.emote.Emote.values()) {
			ctx.runOnClient(mc -> {
				dev.lmg.client.emote.Emotes.play(e);
				dev.lmg.client.emote.Emotes.remote(MIA, e, System.currentTimeMillis());
			});
			ctx.waitTicks(e == dev.lmg.client.emote.Emote.BOW ? 18 : 14);
			ctx.takeScreenshot("emote-" + e.id);
			ctx.runOnClient(mc -> {
				dev.lmg.client.emote.Emotes.stopMine();
				dev.lmg.client.emote.Emotes.remote(MIA, null, System.currentTimeMillis());
			});
			ctx.waitTicks(4);
		}
	}

	/** A small cherry grove with a pond, so the photos have something to look at. */
	static void scene(TestSingleplayerContext world) {
		var srv = world.getServer();
		String[] cmds = {
				"place feature minecraft:cherry 6 -60 14",
				"place feature minecraft:cherry -7 -60 16",
				"place feature minecraft:cherry 1 -60 22",
				"place feature minecraft:cherry -13 -60 10",
				"place feature minecraft:cherry 13 -60 24",
				"fill -4 -61 8 2 -61 11 minecraft:water",
				"setblock 3 -60 6 minecraft:lantern",
				"time set 12400"};
		for (String c : cmds) {
			System.out.println("[scene] " + c);
			srv.runCommand(c);
		}
		// a natural meadow: petals in patches (different amounts and directions), grass, a few flowers
		srv.runOnServer(server -> {
			var level = server.overworld();
			var rnd = new java.util.Random(42);
			String[] facing = {"north", "south", "east", "west"};
			String[] flowers = {"dandelion", "poppy", "cornflower", "azure_bluet", "oxeye_daisy", "lily_of_the_valley"};
			for (int x = -16; x <= 16; x++) {
				for (int z = 0; z <= 28; z++) {
					var pos = new net.minecraft.core.BlockPos(x, -60, z);
					if (!level.getBlockState(pos).isAir()) continue;
					// patches: petals are thick near the trees and thin out in between
					double patch = Math.sin(x * 0.45 + z * 0.2) * Math.cos(z * 0.38 - x * 0.15);
					double r = rnd.nextDouble();
					String block = null;
					if (patch > 0.15 && r < 0.75) block = "pink_petals[flower_amount=" + (1 + rnd.nextInt(4)) + ",facing=" + facing[rnd.nextInt(4)] + "]";
					else if (r < 0.22) block = "short_grass";
					else if (r < 0.26) block = flowers[rnd.nextInt(flowers.length)];
					if (block == null) continue;
					String cmd = "setblock " + x + " -60 " + z + " minecraft:" + block;
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
				}
			}
		});
	}

	private void photo(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("tp @a 0.5 -60 5.5 180 0");
		ctx.waitTicks(10);
		world.getConnection().waitForChunksRender();
		ctx.runOnClient(mc -> {
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			dev.lmg.client.emote.Emotes.play(dev.lmg.client.emote.Emote.WAVE);
		});
		ctx.waitTicks(12);
		ctx.setScreen(dev.lmg.client.screen.PhotoModeScreen::new);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			var p = mc.player;
			// in front of you and a bit to the side, looking back at you and the trees
			dev.lmg.client.feature.PhotoMode.place(p.getX() - 1.6, p.getY() + 1.75, p.getZ() - 3.6, -22f, 9f);
			dev.lmg.client.feature.PhotoMode.filter = dev.lmg.client.feature.PhotoMode.Filter.WARM;
			dev.lmg.client.feature.PhotoMode.vignette = 0.45f;
			dev.lmg.client.feature.PhotoMode.fov = 62f;
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("photo-mode");
		// the photo itself, with a polaroid frame
		ctx.runOnClient(mc -> {
			dev.lmg.client.feature.PhotoMode.frame = dev.lmg.client.feature.PhotoMode.Frame.POLAROID;
			((dev.lmg.client.screen.PhotoModeScreen) mc.gui.screen()).takePhoto();
		});
		ctx.waitTicks(15);
		ctx.takeScreenshot("photo-saved");
		// black and white, film strip, a bit of tilt, panel hidden
		ctx.runOnClient(mc -> {
			dev.lmg.client.feature.PhotoMode.filter = dev.lmg.client.feature.PhotoMode.Filter.MONO;
			dev.lmg.client.feature.PhotoMode.frame = dev.lmg.client.feature.PhotoMode.Frame.FILM;
			dev.lmg.client.feature.PhotoMode.grain = true;
			dev.lmg.client.feature.PhotoMode.roll = 6f;
		});
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_H);
		ctx.waitTicks(160);
		ctx.takeScreenshot("photo-mono");
		ctx.runOnClient(mc -> {
			dev.lmg.client.feature.PhotoMode.filter = dev.lmg.client.feature.PhotoMode.Filter.DREAM;
			dev.lmg.client.feature.PhotoMode.frame = dev.lmg.client.feature.PhotoMode.Frame.CINEMA;
			dev.lmg.client.feature.PhotoMode.grain = false;
			dev.lmg.client.feature.PhotoMode.roll = 0f;
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("photo-dreamy");
		ctx.setScreen(() -> null);
		ctx.waitTicks(5);
	}

	/** You (and a pet) next to the Right Shift menu, on a 1080p screen. */
	private void avatar(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
		ctx.runOnClient(mc -> {
			var c = LmgConfig.get();
			c.menuAvatar = true;
			c.menuOffsetX = 0;
			c.equipped.put("wings", "wings_angel");
			c.equipped.put("hats", "hat_crown");
			c.pets.clear();
			var pet = new dev.lmg.client.pet.PetConfig();
			pet.type = "FOX";
			pet.name = "Ember";
			c.pets.add(pet);
		});
		ctx.getInput().resizeWindow(1920, 1080);
		ctx.waitTicks(10);
		world.getConnection().waitForChunksRender();
		page(1, 0);   // You > Outfit
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.getInput().setCursorPos(1500, 300);
		ctx.waitTicks(120);
		ctx.takeScreenshot("menu-avatar");
		// and the fox following you in the world
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(60);
		ctx.takeScreenshot("pet-world");
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		ctx.setScreen(() -> null);
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(10);
	}

	/** Snow falling and settling on the grove (only on your screen). */
	private void winter(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("time set 2000");
		world.getServer().runCommand("tp @a -2.5 -60 -3.5 -20 12");
		ctx.runOnClient(mc -> {
			LmgConfig.get().winter = dev.lmg.client.world.Winter.Mode.ALWAYS;
			LmgConfig.get().winterSettles = true;
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});
		ctx.waitTicks(10);
		world.getConnection().waitForChunksRender();
		ctx.runOnClient(mc -> LmgConfig.get().winter = dev.lmg.client.world.Winter.Mode.OFF);
		world.getServer().runCommand("weather rain");
		ctx.waitTicks(80);
		ctx.takeScreenshot("winter-vanilla-rain");
		world.getServer().runCommand("weather clear");
		ctx.runOnClient(mc -> LmgConfig.get().winter = dev.lmg.client.world.Winter.Mode.ALWAYS);
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> {
			try {
				var f = dev.lmg.client.world.Winter.class.getDeclaredField("cover");
				f.setAccessible(true);
				f.setFloat(null, 0.45f);   // halfway: speckled
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("winter-falling");
		ctx.runOnClient(mc -> dev.lmg.client.world.Winter.settleNow());
		ctx.waitTicks(40);
		ctx.takeScreenshot("winter-settled");
		// from above, to see the snow on the treetops
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
		world.getServer().runCommand("tp @a -2.5 -50 -6.5 -10 35");
		ctx.waitTicks(40);
		ctx.takeScreenshot("winter-trees");
		ctx.runOnClient(mc -> {
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			LmgConfig.get().winter = dev.lmg.client.world.Winter.Mode.SEASONAL;
		});
	}

	/** Walk somewhere, die, respawn: the trail, the beam, the compass and the maps. */
	private void death(ClientGameTestContext ctx, TestSingleplayerContext world) {
		world.getServer().runCommand("gamerule doImmediateRespawn false");
		world.getServer().runCommand("time set 13500");
		world.getServer().runCommand("tp @a 0.5 -60 0.5");
		ctx.waitTicks(5);
		// a winding walk out to the east
		for (int i = 1; i <= 40; i++) {
			double x = 0.5 + i * 1.2, z = 0.5 + Math.sin(i / 6.0) * 9 + i * 0.4;
			world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f -60 %.2f", x, z));
			ctx.waitTicks(1);
		}
		world.getServer().runCommand("kill @a");
		ctx.waitForScreen(net.minecraft.client.gui.screens.DeathScreen.class);
		ctx.waitTicks(30);
		ctx.clickScreenButton("deathScreen.respawn");
		ctx.waitTicks(20);
		world.getServer().runCommand("tp @a -3.5 -59 -2.5 facing 14 -60 8");
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		ctx.takeScreenshot("death-trail");
		ctx.setScreen(() -> new dev.lmg.client.screen.WorldMapScreen(null));
		ctx.waitTicks(30);
		ctx.takeScreenshot("death-map");
		ctx.setScreen(() -> null);
	}

	/** The regrouped menu: a few pages, a long page that scrolls, and the other two styles. */
	private void newMenu(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
		ctx.runOnClient(mc -> LmgConfig.get().myUid = 7);
		int[][] pages = {{0, 0}, {0, 2}, {4, 2}, {6, 0}, {1, 1}};
		for (int[] pg : pages) {
			page(pg[0], pg[1]);
			ctx.setScreen(() -> new LmgSettingsScreen(null));
			ctx.waitTicks(25);
			ctx.takeScreenshot("newmenu-" + pg[0] + "-" + pg[1]);
		}
		for (var style : new LmgConfig.MenuStyle[]{LmgConfig.MenuStyle.SKEET, LmgConfig.MenuStyle.NEVERLOSE}) {
			ctx.runOnClient(mc -> LmgConfig.get().menuStyle = style);
			page(0, 2);
			ctx.setScreen(() -> new LmgSettingsScreen(null));
			ctx.waitTicks(25);
			ctx.takeScreenshot("newmenu-" + style.name().toLowerCase());
		}
		ctx.runOnClient(mc -> LmgConfig.get().menuStyle = LmgConfig.MenuStyle.LMG);
		ctx.setScreen(() -> null);
	}

	/** The first-time tour, a few of its steps. */
	private void tour(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> LmgConfig.get().tourDone = false);
		page(0, 0);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(20);
		for (int step = 1; step <= 12; step++) {
			if (step == 1 || step == 2 || step == 5 || step == 6 || step == 7 || step == 10) ctx.takeScreenshot("tour-" + step);
			ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
			ctx.waitTicks(12);
		}
		ctx.runOnClient(mc -> System.out.println("[tour] done=" + LmgConfig.get().tourDone));
		ctx.setScreen(() -> null);
	}

	/** The held item presets, and the screen for moving it. */
	private void items(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("time set 1000");
		world.getServer().runCommand("tp @a 0.5 -60 0.5 30 -4");
		world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword[enchantments={sharpness:5}]");
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		ctx.waitTicks(30);
		world.getConnection().waitForChunksRender();
		for (dev.lmg.client.feature.ItemLook.Preset p : dev.lmg.client.feature.ItemLook.Preset.values()) {
			ctx.runOnClient(mc -> p.apply(LmgConfig.get()));
			ctx.waitTicks(8);
			ctx.takeScreenshot("items-" + p.name().toLowerCase());
		}
		// LMG_ITEM_TRY="x,y,z,turn,tilt,roll,size;..." tries other looks (for tuning the presets)
		String tries = System.getenv().getOrDefault("LMG_ITEM_TRY", "");
		int n = 0;
		for (String t : tries.split(";")) {
			if (t.isBlank()) continue;
			String[] v = t.split(",");
			ctx.runOnClient(mc -> {
				LmgConfig c = LmgConfig.get();
				c.itemX = Float.parseFloat(v[0]);
				c.itemY = Float.parseFloat(v[1]);
				c.itemZ = Float.parseFloat(v[2]);
				c.itemTurn = Float.parseFloat(v[3]);
				c.itemTilt = Float.parseFloat(v[4]);
				c.itemRoll = Float.parseFloat(v[5]);
				c.itemSize = Float.parseFloat(v[6]);
			});
			ctx.waitTicks(6);
			ctx.takeScreenshot("items-try" + (n++));
		}
		ctx.runOnClient(mc -> dev.lmg.client.feature.ItemLook.Preset.BIG_LOW.apply(LmgConfig.get()));
		ctx.setScreen(() -> new dev.lmg.client.screen.ItemLookScreen(null));
		ctx.waitTicks(15);
		ctx.takeScreenshot("items-screen");
		ctx.runOnClient(mc -> System.out.println("[items] " + dev.lmg.client.screen.ItemLookScreen.describe()));
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> dev.lmg.client.feature.ItemLook.Preset.VANILLA.apply(LmgConfig.get()));
	}

	/** The crosshair editor, the logo crosshair spinning, and a drawing in game. */
	private void xhair(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("time set 1000");
		world.getServer().runCommand("tp @a 0.5 -60 0.5 30 -4");
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.crosshairRgb = 0xFF8FC8;
			c.crosshairSpin = 1;
			c.crosshairSize = 2;
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});
		ctx.setScreen(() -> new dev.lmg.client.screen.CrosshairEditorScreen(null));
		ctx.waitTicks(20);
		ctx.takeScreenshot("xhair-editor");
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> LmgConfig.get().crosshair = LmgConfig.Crosshair.LOGO);
		ctx.waitTicks(10);
		ctx.takeScreenshot("xhair-logo");
		ctx.runOnClient(mc -> LmgConfig.get().crosshair = LmgConfig.Crosshair.DRAWN);
		ctx.waitTicks(10);
		ctx.takeScreenshot("xhair-drawn");
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.crosshair = LmgConfig.Crosshair.VANILLA;
			c.crosshairSpin = 0;
			c.crosshairSize = 1;
		});
	}

	/** Opens the "more options" card of a row on the open menu (what clicking its gear does). */
	private static void openMore(net.minecraft.client.Minecraft mc, String label) {
		try {
			var screen = mc.gui.screen();
			var f = LmgSettingsScreen.class.getDeclaredField("rows");
			f.setAccessible(true);
			for (Object o : (java.util.List<?>) f.get(screen)) {
				var row = (dev.lmg.client.screen.SettingRow) o;
				if (row.getMessage().getString().equals(label)) {
					var m = dev.lmg.client.screen.SettingRow.class.getDeclaredField("more");
					m.setAccessible(true);
					((Runnable) m.get(row)).run();
					return;
				}
			}
			throw new IllegalStateException("no row " + label);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** v0.29 menu: search, the new pages, a gear card, low-end mode. */
	private void v29menu(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
		ctx.runOnClient(mc -> LmgConfig.get().myUid = 7);
		page(2, 1);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(25);
		ctx.takeScreenshot("v29-crosshair-page");
		ctx.runOnClient(mc -> openMore(mc, "Crosshair"));
		ctx.waitTicks(15);
		ctx.takeScreenshot("v29-crosshair-more");
		ctx.setScreen(() -> null);
		page(2, 2);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(20);
		ctx.takeScreenshot("v29-hits-page");
		ctx.runOnClient(mc -> openMore(mc, "Damage numbers"));
		ctx.waitTicks(15);
		ctx.takeScreenshot("v29-damage-more");
		ctx.setScreen(() -> null);
		page(3, 1);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(20);
		ctx.takeScreenshot("v29-panels-page");
		page(6, 2);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(20);
		ctx.takeScreenshot("v29-fps-page");
		page(0, 2);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(15);
		ctx.getInput().typeChars("colour");
		ctx.waitTicks(15);
		ctx.takeScreenshot("v29-search");
		ctx.runOnClient(mc -> System.out.println("[v29] search rows=" + rowsOf(mc)));
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> System.out.println("[v29] after esc screen=" + mc.gui.screen()));
		ctx.runOnClient(mc -> LmgConfig.get().menuStyle = LmgConfig.MenuStyle.SKEET);
		page(2, 1);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(20);
		ctx.takeScreenshot("v29-skeet-crosshair");
		ctx.runOnClient(mc -> LmgConfig.get().menuStyle = LmgConfig.MenuStyle.NEVERLOSE);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(20);
		ctx.takeScreenshot("v29-nl-crosshair");
		ctx.runOnClient(mc -> openMore(mc, "Crosshair"));
		ctx.waitTicks(15);
		ctx.takeScreenshot("v29-nl-more");
		ctx.runOnClient(mc -> LmgConfig.get().menuStyle = LmgConfig.MenuStyle.LMG);
		// low-end mode on and back off puts things back
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.aurora = LmgConfig.Aurora.PURPLE;
			c.ambient = LmgConfig.Ambient.PETALS;
			dev.lmg.client.feature.Looks.setLowEnd(true);
			String during = c.aurora + "/" + c.ambient;
			dev.lmg.client.feature.Looks.setLowEnd(false);
			System.out.println("[v29] lowend during=" + during + " after=" + c.aurora + "/" + c.ambient);
		});
		ctx.setScreen(() -> null);
	}

	private static String rowsOf(net.minecraft.client.Minecraft mc) {
		try {
			var f = LmgSettingsScreen.class.getDeclaredField("rows");
			f.setAccessible(true);
			StringBuilder b = new StringBuilder();
			for (Object o : (java.util.List<?>) f.get(mc.gui.screen())) b.append(((dev.lmg.client.screen.SettingRow) o).getMessage().getString()).append(", ");
			return b.toString();
		} catch (Exception e) {
			return e.toString();
		}
	}

	/** v0.29 HUD: PvP stats, armour, hotbar style, name tags, hit sound, inventory colour. */
	private void v29hud(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("time set 1000");
		world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
		world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[damage=300]");
		world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
		world.getServer().runCommand("item replace entity @a armor.legs with minecraft:iron_leggings[damage=200]");
		world.getServer().runCommand("item replace entity @a armor.feet with minecraft:diamond_boots[damage=420]");
		world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:golden_apple 12");
		world.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:ender_pearl 16");
		world.getServer().runCommand("summon minecraft:zombie 0.5 -60 3.0 {NoAI:1b,Rotation:[180f,0f],Health:200f,CustomName:'Training dummy',CustomNameVisible:1b,Attributes:[{id:\"minecraft:max_health\",base:200}]}");
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.pvpHud = true;
			c.armourHud = true;
			c.armourNumbers = true;
			c.hotbarStyle = LmgConfig.HotbarStyle.LMG;
			c.nameTagBg = LmgConfig.NameTagBg.ACCENT;
			c.crosshair = LmgConfig.Crosshair.CROSS_DOT;
			c.crosshairGap = 3;
			c.crosshairLength = 5;
			c.damageNumbers = true;
			c.damageStyle = LmgConfig.DamageStyle.BUBBLE;
			c.hitSound = LmgConfig.HitSound.DING;
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});
		ctx.waitTicks(30);
		world.getConnection().waitForChunksRender();
		ctx.getInput().lookAt(new net.minecraft.core.BlockPos(0, -59, 3));
		ctx.waitTicks(10);
		for (int i = 0; i < 2; i++) {
			ctx.runOnClient(mc -> {
				dev.lmg.client.feature.Visuals.onClick(0);
				var z = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
				if (z != null) {
					dev.lmg.client.feature.Visuals.onAttack(z);
					mc.gameMode.attack(mc.player, z);
				}
			});
			ctx.waitTicks(12);
		}
		ctx.runOnClient(mc -> System.out.println("[v29] combo=" + dev.lmg.client.feature.Visuals.combo() + " reach=" + dev.lmg.client.feature.Visuals.lastReach()));
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTicks(3);
		ctx.takeScreenshot("v29-hud");
		ctx.runOnClient(mc -> {
			LmgConfig.get().hotbarStyle = LmgConfig.HotbarStyle.OUTLINE;
			LmgConfig.get().inventoryTint = 0xB45CFF;
			LmgConfig.get().inventoryTintStrength = 0.3f;
		});
		ctx.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		ctx.waitTicks(15);
		ctx.takeScreenshot("v29-inventory");
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.pvpHud = false;
			c.armourHud = false;
			c.hotbarStyle = LmgConfig.HotbarStyle.VANILLA;
			c.nameTagBg = LmgConfig.NameTagBg.VANILLA;
			c.inventoryTint = -1;
			c.crosshair = LmgConfig.Crosshair.VANILLA;
			c.damageNumbers = false;
			c.hitSound = LmgConfig.HitSound.OFF;
		});
		world.getServer().runCommand("kill @e[type=minecraft:zombie]");
	}

	/** v0.29 world: new skies, mist, leaves and the lens effects. */
	private void v29world(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("tp @a 0.5 -60 0.5 30 -4");
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		String[][] shots = {{"OCEAN", "1000", "OFF"}, {"GOLDEN", "12000", "LEAVES"}, {"LAVENDER", "23000", "MIST"}};
		for (String[] sh : shots) {
			world.getServer().runCommand("time set " + sh[1]);
			ctx.runOnClient(mc -> {
				LmgConfig c = LmgConfig.get();
				c.skyTheme = LmgConfig.SkyTheme.valueOf(sh[0]);
				c.ambient = LmgConfig.Ambient.valueOf(sh[2]);
				c.ambientAmount = 2;
			});
			ctx.waitTicks(sh[2].equals("OFF") ? 20 : 140);
			ctx.takeScreenshot("v29-sky-" + sh[0].toLowerCase());
		}
		world.getServer().runCommand("time set 1000");
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.skyTheme = LmgConfig.SkyTheme.VANILLA;
			c.ambient = LmgConfig.Ambient.OFF;
			c.lensVignette = 2;
			c.lensGrain = 2;
			c.lensChroma = true;
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("v29-lens");
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.lensVignette = 0;
			c.lensGrain = 0;
			c.lensChroma = false;
			System.out.println("[v29] applied=" + mc.gameRenderer.getAppliedPostEffects());
		});
	}

	/** v0.29 photo mode: tabs, looks, focus, grid. */
	private void v29photo(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("time set 1000");
		world.getServer().runCommand("tp @a 0.5 -60 0.5 30 -4");
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> dev.lmg.client.feature.PhotoMode.Look.GOLDEN.apply());
		ctx.setScreen(dev.lmg.client.screen.PhotoModeScreen::new);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			var p = mc.player;
			// stand back a little, looking at you with the trees behind
			dev.lmg.client.feature.PhotoMode.place(p.getX() - 2.2, p.getY() + 1.6, p.getZ() + 3.2, -153f, 9f);
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("v29-photo-look");
		ctx.runOnClient(mc -> {
			dev.lmg.client.feature.PhotoMode.grid = true;
			dev.lmg.client.feature.PhotoMode.Look.MINIATURE.apply();
		});
		ctx.waitTicks(15);
		ctx.takeScreenshot("v29-photo-miniature");
		ctx.runOnClient(mc -> {
			dev.lmg.client.feature.PhotoMode.grid = false;
			dev.lmg.client.feature.PhotoMode.Look.CLEAN.apply();
			dev.lmg.client.feature.PhotoMode.focus = dev.lmg.client.feature.PhotoMode.Focus.STRONG;
			System.out.println("[v29] photo applied=" + mc.gameRenderer.getAppliedPostEffects());
		});
		ctx.waitTicks(15);
		ctx.takeScreenshot("v29-photo-focus");
		ctx.runOnClient(mc -> System.out.println("[v29] photo applied2=" + mc.gameRenderer.getAppliedPostEffects()));
		ctx.runOnClient(mc -> dev.lmg.client.feature.PhotoMode.focus = dev.lmg.client.feature.PhotoMode.Focus.OFF);
		ctx.setScreen(() -> null);
	}

	/** The first tour card, with its "No thanks" button. */
	private void tourSkip(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> LmgConfig.get().tourDone = false);
		page(0, 0);
		ctx.setScreen(() -> new LmgSettingsScreen(null));
		ctx.waitTicks(20);
		ctx.takeScreenshot("tourskip-card");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> System.out.println("[tourskip] done=" + LmgConfig.get().tourDone + " screen=" + mc.gui.screen()));
		ctx.setScreen(() -> null);
	}

	/** Crosshair, damage numbers, the hurt colour and a colour filter, with a real hit on a mob. */
	private void effects(ClientGameTestContext ctx, TestSingleplayerContext world) {
		scene(world);
		world.getServer().runCommand("time set 1000");
		world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
		world.getServer().runCommand("summon minecraft:zombie 0.5 -60 3.0 {NoAI:1b,Rotation:[180f,0f],Health:200f,Attributes:[{id:\"minecraft:max_health\",base:200}]}");
		ctx.runOnClient(mc -> {
			LmgConfig c = LmgConfig.get();
			c.crosshair = LmgConfig.Crosshair.CROSS_DOT;
			c.crosshairRgb = 0x7CF2FF;
			c.damageNumbers = true;
			c.damageRgb = 0xFFD24A;
			c.hurtRgb = 0x3D7BFF;
			dev.lmg.client.feature.Visuals.applyHurtColor();
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});
		ctx.waitTicks(30);
		world.getConnection().waitForChunksRender();
		ctx.getInput().lookAt(new net.minecraft.core.BlockPos(0, -59, 3));
		ctx.waitTicks(10);
		for (int i = 0; i < 3; i++) {
			ctx.runOnClient(mc -> {
				var z = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
				if (z != null) {
					dev.lmg.client.feature.Visuals.onAttack(z);
					mc.gameMode.attack(mc.player, z);
				}
			});
			ctx.waitTicks(i < 2 ? 12 : 3);
		}
		ctx.takeScreenshot("effects-hit");
		ctx.runOnClient(mc -> LmgConfig.get().worldFilter = dev.lmg.client.feature.PhotoMode.Filter.VIVID);
		ctx.waitTicks(20);
		ctx.takeScreenshot("effects-vivid");
		ctx.runOnClient(mc -> {
			LmgConfig.get().worldFilter = null;
			LmgConfig.get().motionBlur = 3;
		});
		for (int i = 0; i < 10; i++) {
			ctx.getInput().moveCursor(60, 0);
			ctx.waitTicks(1);
		}
		ctx.takeScreenshot("effects-blur");
		ctx.runOnClient(mc -> {
			LmgConfig.get().motionBlur = 0;
			System.out.println("[effects] applied=" + mc.gameRenderer.getAppliedPostEffects());
		});
	}

	/** The colour sliders in Cosmetics, with wings on. */
	private void cosmetics(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			LmgConfig.get().equipped.put("wings", "wings_butterfly");
			LmgConfig.get().cosmeticColors.put("wings_butterfly", "#8A5BFF");
		});
		ctx.setScreen(() -> new dev.lmg.client.screen.CosmeticsScreen(null));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			try {
				var cs = mc.gui.screen();
				var tab = cs.getClass().getDeclaredField("tab");
				tab.setAccessible(true);
				tab.set(cs, "wings");
				var sel = cs.getClass().getDeclaredField("selected");
				sel.setAccessible(true);
				sel.set(cs, "wings_butterfly");
				var rb = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod("rebuildWidgets");
				rb.setAccessible(true);
				rb.invoke(cs);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("cosmetics-colour");
		ctx.setScreen(() -> null);
	}

	private static String testToken() {
		try {
			Path p = Path.of(System.getenv().getOrDefault("LMG_TEST_TOKENS", ""));
			if (!Files.exists(p)) return null;
			for (String line : Files.readAllLines(p)) {
				String[] parts = line.trim().split("\\s+");
				if (parts.length >= 2 && parts[0].equals("A")) return parts[1];
			}
		} catch (Exception ignored) {
		}
		return null;
	}
}
