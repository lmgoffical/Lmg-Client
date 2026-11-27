package dev.lmg.legacy;

import dev.lmg.legacy.feature.Cps;
import dev.lmg.legacy.feature.Keys;
import dev.lmg.legacy.ui.HomeScreen;
import dev.lmg.legacy.ui.HudEditor;
import dev.lmg.legacy.ui.MainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Only for development: with -Dlmg.test=1 the game walks through every Lmg screen and feature, saves screenshots
 * (screenshots/lmg-*.png) and closes. Does nothing in normal play.
 */
final class TestDriver {
	private static final boolean ON = !System.getProperty("lmg.test", "").isEmpty();
	private static int t, step, waitUntil, sub;

	private TestDriver() {}

	private static void shot(String name) {
		Minecraft mc = Minecraft.getMinecraft();
		ScreenShotHelper.saveScreenshot(mc.mcDataDir, "lmg-" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		LmgLegacy.LOGGER.info("LMGTEST shot {}", name);
	}

	private static void waitTicks(int n) {
		waitUntil = t + n;
	}

	static void tick() {
		if (!ON) return;
		Minecraft mc = Minecraft.getMinecraft();
		t++;
		if (t < waitUntil) return;
		Config c = Config.get();
		switch (step) {
			case 0:
				mc.gameSettings.pauseOnLostFocus = false;   // the virtual screen never has focus
				if (!(mc.currentScreen instanceof MainMenu)) return;
				waitTicks(40);
				break;
			case 1:
				shot("title");
				mc.displayGuiScreen(new HomeScreen(mc.currentScreen));
				waitTicks(15);
				break;
			case 2:
				shot("home");
				mc.displayGuiScreen(HomeScreen.page(mc.currentScreen, 0));
				waitTicks(10);
				break;
			case 3:
				shot("page-hud");
				mc.displayGuiScreen(HomeScreen.page(new HomeScreen(null), 1));
				waitTicks(10);
				break;
			case 4:
				shot("page-zoom");
				c.crosshairStyle = 1;
				mc.displayGuiScreen(HomeScreen.page(new HomeScreen(null), 2));
				waitTicks(10);
				break;
			case 5:
				shot("page-crosshair");
				// a world with everything switched on
				c.hudFps = c.hudCps = c.hudKeys = c.hudArmour = c.hudPotions = c.hudCoords = c.hudPing = c.hudClock = true;
				c.toggleSprint = true;
				c.cape = "cape_cherry";
				c.halo = true;
				c.cosmeticColor = 5;
				c.crosshairStyle = 1;
				c.crosshairColor = 1;
				Config.save();
				mc.displayGuiScreen(null);
				WorldSettings ws = new WorldSettings(1234L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT);
				ws.enableCommands();
				mc.launchIntegratedServer("lmgtest", "lmgtest", ws);
				waitTicks(40);
				break;
			case 6:
				if (mc.currentScreen instanceof net.minecraft.client.gui.GuiIngameMenu) mc.displayGuiScreen(null);
				if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;
				final MinecraftServer srv = MinecraftServer.getServer();
				srv.addScheduledTask(new Runnable() {
					public void run() {
						EntityPlayerMP p = srv.getConfigurationManager().getPlayerList().get(0);
						ItemStack[] armour = {new ItemStack(Items.diamond_boots), new ItemStack(Items.diamond_leggings),
								new ItemStack(Items.diamond_chestplate), new ItemStack(Items.diamond_helmet)};
						armour[2].setItemDamage(400);
						armour[3].setItemDamage(120);
						for (int i = 0; i < 4; i++) p.inventory.armorInventory[i] = armour[i];
						p.inventory.setInventorySlotContents(0, new ItemStack(Items.diamond_sword));
						p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 6000, 1));
						p.addPotionEffect(new PotionEffect(Potion.damageBoost.id, 3000, 0));
						srv.worldServers[0].setWorldTime(6000);
					}
				});
				waitTicks(80);
				break;
			case 7:
				mc.thePlayer.rotationPitch = 8f;
				for (int i = 0; i < 6; i++) Cps.click(0);
				for (int i = 0; i < 2; i++) Cps.click(1);
				KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
				waitTicks(6);
				break;
			case 8:
				shot("hud");
				KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
				mc.gameSettings.thirdPersonView = 2;
				waitTicks(30);
				break;
			case 9:
				shot("front");
				mc.gameSettings.thirdPersonView = 1;
				waitTicks(30);
				break;
			case 10:
				shot("back");
				mc.gameSettings.thirdPersonView = 0;
				c.zoomAmount = 6f;
				KeyBinding.setKeyBindState(Keys.ZOOM.getKeyCode(), true);
				waitTicks(30);
				break;
			case 11:
				shot("zoom");
				KeyBinding.setKeyBindState(Keys.ZOOM.getKeyCode(), false);
				sub = 1;
				waitTicks(20);
				break;
			case 12:
				// every crosshair style, one shot each
				if (sub > 1) shot("crosshair-" + (sub - 1));
				if (sub < dev.lmg.legacy.feature.Crosshair.STYLES.length) {
					c.crosshairStyle = sub++;
					waitTicks(4);
					return;
				}
				c.crosshairStyle = 1;
				mc.displayGuiScreen(new HomeScreen(null));
				waitTicks(15);
				break;
			case 13:
				shot("home-ingame");
				mc.displayGuiScreen(HomeScreen.page(mc.currentScreen, 3));
				waitTicks(30);
				break;
			case 14:
				shot("page-cosmetics");
				mc.displayGuiScreen(new HudEditor(null));
				waitTicks(15);
				break;
			case 15:
				shot("hud-editor");
				mc.displayGuiScreen(null);
				LmgLegacy.LOGGER.info("LMGTEST done");
				mc.shutdown();
				break;
			default:
				return;
		}
		step++;
	}
}
