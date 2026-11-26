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
				try {
					var f = LmgSettingsScreen.class.getDeclaredField("lastTab");
					f.setAccessible(true);
					f.setInt(null, 0);   // the Theme tab, where the weather setting is
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
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
				"fill -14 -60 2 14 -60 26 minecraft:pink_petals[flower_amount=3] replace minecraft:air",
				"setblock 3 -60 6 minecraft:lantern",
				"time set 12400"};
		for (String c : cmds) {
			System.out.println("[scene] " + c);
			srv.runCommand(c);
		}
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
		try {
			var f = LmgSettingsScreen.class.getDeclaredField("lastTab");
			f.setAccessible(true);
			f.setInt(null, 4);   // the Avatar tab
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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
