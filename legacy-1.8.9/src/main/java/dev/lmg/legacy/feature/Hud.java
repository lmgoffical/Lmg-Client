package dev.lmg.legacy.feature;

import dev.lmg.legacy.Colors;
import dev.lmg.legacy.Config;
import dev.lmg.legacy.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/** The HUD panels. Each has a default spot; drag them anywhere in Edit HUD. */
public final class Hud {
	private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

	/** One panel on the HUD. Sizes are in unscaled GUI pixels (before the HUD size setting). */
	public abstract static class Element {
		public final String id, name;
		final float defX, defY;
		public int w = 40, h = 12;

		Element(String id, String name, float defX, float defY) {
			this.id = id;
			this.name = name;
			this.defX = defX;
			this.defY = defY;
		}

		public abstract boolean on();

		public abstract void toggle();

		/** Works out w/h, then draws at (0,0) (the caller translates and scales). */
		abstract void draw(boolean preview);

		public float[] pos() {
			float[] p = Config.get().hudPos.get(id);
			return p != null && p.length == 2 ? p : new float[]{defX, defY};
		}
	}

	public static final List<Element> ALL = new ArrayList<Element>();

	static {
		ALL.add(new Line("fps", "FPS", 0.005f, 0.01f) {
			public boolean on() { return Config.get().hudFps; }
			public void toggle() { Config.get().hudFps ^= true; }
			String text() { return "FPS " + Minecraft.getDebugFPS(); }
		});
		ALL.add(new Line("cps", "CPS", 0.005f, 0.06f) {
			public boolean on() { return Config.get().hudCps; }
			public void toggle() { Config.get().hudCps ^= true; }
			String text() { return "CPS " + Cps.left() + " | " + Cps.right(); }
		});
		ALL.add(new Line("ping", "Ping", 0.005f, 0.11f) {
			public boolean on() { return Config.get().hudPing; }
			public void toggle() { Config.get().hudPing ^= true; }
			String text() {
				Minecraft mc = Minecraft.getMinecraft();
				if (mc.isSingleplayer() || mc.getNetHandler() == null || mc.thePlayer == null) return "Ping 0ms";
				NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
				return "Ping " + (info == null ? "?" : String.valueOf(info.getResponseTime())) + "ms";
			}
		});
		ALL.add(new Line("coords", "Coordinates", 0.005f, 0.16f) {
			public boolean on() { return Config.get().hudCoords; }
			public void toggle() { Config.get().hudCoords ^= true; }
			String text() {
				EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
				if (p == null) return "X 0  Y 64  Z 0  N";
				String f = p.getHorizontalFacing().getName();
				return "X " + (int) Math.floor(p.posX) + "  Y " + (int) Math.floor(p.posY) + "  Z " + (int) Math.floor(p.posZ)
						+ "  " + Character.toUpperCase(f.charAt(0));
			}
		});
		ALL.add(new Line("clock", "Clock", 0.005f, 0.21f) {
			private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
			public boolean on() { return Config.get().hudClock; }
			public void toggle() { Config.get().hudClock ^= true; }
			String text() { return fmt.format(new Date()); }
		});
		ALL.add(new Line("sprint", "Sprint status", 0.005f, 0.95f) {
			public boolean on() { return Config.get().hudSprint && Config.get().toggleSprint; }
			public void toggle() { Config.get().hudSprint ^= true; }
			String text() { return PvpTweaks.sprinting() ? "Sprinting (toggled)" : "Sprint ready (toggled)"; }
		});
		ALL.add(new Keystrokes());
		ALL.add(new Armour());
		ALL.add(new Potions());
	}

	public static Element byId(String id) {
		for (Element e : ALL) if (e.id.equals(id)) return e;
		return null;
	}

	/** Draws every switched-on panel (preview = in the HUD editor, shows example values). */
	public static void render(ScaledResolution sr, boolean preview) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!preview && (mc.gameSettings.showDebugInfo || mc.thePlayer == null)) return;
		float scale = Config.get().hudScale;
		for (Element e : ALL) {
			if (!e.on() && !preview) continue;
			float[] p = e.pos();
			int x = Math.round(p[0] * sr.getScaledWidth()), y = Math.round(p[1] * sr.getScaledHeight());
			GlStateManager.pushMatrix();
			GlStateManager.translate(x, y, 0);
			GlStateManager.scale(scale, scale, 1);
			if (preview && !e.on()) GlStateManager.color(1f, 1f, 1f, 0.35f);
			e.draw(preview);
			GlStateManager.popMatrix();
			// keep it on screen after the size changes
			clampOnScreen(e, sr);
		}
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	static void clampOnScreen(Element e, ScaledResolution sr) {
		float[] p = e.pos();
		float scale = Config.get().hudScale;
		float maxX = 1f - e.w * scale / (float) sr.getScaledWidth(), maxY = 1f - e.h * scale / (float) sr.getScaledHeight();
		float nx = Math.max(0, Math.min(maxX, p[0])), ny = Math.max(0, Math.min(maxY, p[1]));
		if (nx != p[0] || ny != p[1]) {
			if (Config.get().hudPos.containsKey(e.id)) Config.get().hudPos.put(e.id, new float[]{nx, ny});
		}
	}

	static int textColor() {
		return 0xFF000000 | Colors.rgb(Config.get().hudColor);
	}

	static void label(String s, int x, int y, int color) {
		if (Config.get().hudShadow) Ui.shadow(s, x, y, color);
		else Ui.text(s, x, y, color);
	}

	static void box(int w, int h) {
		if (Config.get().hudBackground) Ui.round(0, 0, w, h, 2, 0x8C0D1424);
	}

	/** A one-line text panel. */
	abstract static class Line extends Element {
		Line(String id, String name, float x, float y) {
			super(id, name, x, y);
		}

		abstract String text();

		void draw(boolean preview) {
			String s = text();
			boolean bg = Config.get().hudBackground;
			w = Ui.width(s) + (bg ? 8 : 0);
			h = bg ? 13 : 9;
			box(w, h);
			label(s, bg ? 4 : 0, bg ? 3 : 0, textColor());
		}
	}

	/** W A S D, the mouse buttons with clicks per second, and the space bar. */
	static final class Keystrokes extends Element {
		Keystrokes() {
			super("keys", "Keystrokes", 0.005f, 0.30f);
		}

		public boolean on() { return Config.get().hudKeys; }

		public void toggle() { Config.get().hudKeys ^= true; }

		void draw(boolean preview) {
			Minecraft mc = Minecraft.getMinecraft();
			int k = 22, g = 2;
			w = k * 3 + g * 2;
			h = k * 2 + g + 2 + 22 + 2 + 10;
			key("W", k + g, 0, k, k, mc.gameSettings.keyBindForward.isKeyDown());
			key("A", 0, k + g, k, k, mc.gameSettings.keyBindLeft.isKeyDown());
			key("S", k + g, k + g, k, k, mc.gameSettings.keyBindBack.isKeyDown());
			key("D", (k + g) * 2, k + g, k, k, mc.gameSettings.keyBindRight.isKeyDown());
			int my = (k + g) * 2, mw = (w - g) / 2;
			boolean inGame = mc.currentScreen == null;
			key("LMB", 0, my, mw, 22, inGame && Mouse.isButtonDown(0), Cps.left() + " CPS");
			key("RMB", mw + g, my, w - mw - g, 22, inGame && Mouse.isButtonDown(1), Cps.right() + " CPS");
			key("", 0, my + 22 + g, w, 10, mc.gameSettings.keyBindJump.isKeyDown());
			// the space bar's little line
			Gui.drawRect(w / 2 - 10, my + 22 + g + 4, w / 2 + 10, my + 22 + g + 5, mc.gameSettings.keyBindJump.isKeyDown() ? 0xFF111111 : textColor());
		}

		private void key(String name, int x, int y, int kw, int kh, boolean down) {
			key(name, x, y, kw, kh, down, null);
		}

		private void key(String name, int x, int y, int kw, int kh, boolean down, String sub) {
			Ui.round(x, y, x + kw, y + kh, 2, down ? 0xE6FFFFFF : 0x8C0D1424);
			int c = down ? 0xFF111111 : textColor();
			if (name.isEmpty()) return;
			if (sub == null) {
				Ui.centered(name, x + kw / 2, y + (kh - 8) / 2, c);
			} else {
				Ui.centered(name, x + kw / 2, y + 3, c);
				GlStateManager.pushMatrix();
				GlStateManager.translate(x + kw / 2f, y + 13, 0);
				GlStateManager.scale(0.7f, 0.7f, 1);
				Ui.centered(sub, 0, 0, c);
				GlStateManager.popMatrix();
			}
		}
	}

	/** Your armour (and what you're holding), with how much durability is left. */
	static final class Armour extends Element {
		Armour() {
			super("armour", "Armour", 0.90f, 0.62f);
		}

		public boolean on() { return Config.get().hudArmour; }

		public void toggle() { Config.get().hudArmour ^= true; }

		void draw(boolean preview) {
			Minecraft mc = Minecraft.getMinecraft();
			List<ItemStack> items = new ArrayList<ItemStack>();
			if (mc.thePlayer != null) {
				for (int i = 3; i >= 0; i--) if (mc.thePlayer.inventory.armorInventory[i] != null) items.add(mc.thePlayer.inventory.armorInventory[i]);
				if (mc.thePlayer.getHeldItem() != null) items.add(mc.thePlayer.getHeldItem());
			}
			if (items.isEmpty() && preview) {
				items.add(new ItemStack(net.minecraft.init.Items.diamond_helmet));
				items.add(new ItemStack(net.minecraft.init.Items.diamond_chestplate));
				items.add(new ItemStack(net.minecraft.init.Items.diamond_leggings));
				items.add(new ItemStack(net.minecraft.init.Items.diamond_boots));
			}
			w = 46;
			h = Math.max(16, items.size() * 17);
			if (items.isEmpty()) return;
			if (Config.get().hudBackground) Ui.round(0, 0, w, h, 2, 0x8C0D1424);
			int y = 0;
			for (ItemStack s : items) {
				RenderHelper.enableGUIStandardItemLighting();
				mc.getRenderItem().renderItemAndEffectIntoGUI(s, 1, y);
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableLighting();
				GlStateManager.enableBlend();
				String t;
				int col = textColor();
				if (s.isItemStackDamageable()) {
					int left = s.getMaxDamage() - s.getItemDamage();
					t = String.valueOf(left);
					float frac = left / (float) s.getMaxDamage();
					if (frac < 0.25f) col = 0xFFFF5A5A;
					else if (frac < 0.5f) col = 0xFFFFD36A;
				} else {
					t = s.stackSize > 1 ? "x" + s.stackSize : "";
				}
				label(t, 19, y + 5, col);
				y += 17;
			}
		}
	}

	/** Potion effects with their icons and time left. */
	static final class Potions extends Element {
		Potions() {
			super("potions", "Potion effects", 0.80f, 0.20f);
		}

		public boolean on() { return Config.get().hudPotions; }

		public void toggle() { Config.get().hudPotions ^= true; }

		void draw(boolean preview) {
			Minecraft mc = Minecraft.getMinecraft();
			Collection<PotionEffect> effects = mc.thePlayer == null ? new ArrayList<PotionEffect>() : mc.thePlayer.getActivePotionEffects();
			if (effects.isEmpty() && preview) {
				effects = new ArrayList<PotionEffect>();
				effects.add(new PotionEffect(Potion.moveSpeed.id, 2400, 1));
				effects.add(new PotionEffect(Potion.damageBoost.id, 1800, 0));
			}
			w = 110;
			h = Math.max(12, effects.size() * 22);
			int y = 0;
			for (PotionEffect e : effects) {
				Potion potion = Potion.potionTypes[e.getPotionID()];
				if (potion == null) continue;
				if (Config.get().hudBackground) Ui.round(0, y, w, y + 20, 2, 0x8C0D1424);
				if (potion.hasStatusIcon()) {
					int idx = potion.getStatusIconIndex();
					GlStateManager.disableDepth();
					GlStateManager.disableLighting();
					GlStateManager.enableTexture2D();
					GlStateManager.enableBlend();
					GlStateManager.enableAlpha();
					GlStateManager.color(1f, 1f, 1f, 1f);
					mc.getTextureManager().bindTexture(INVENTORY);
					mc.ingameGUI.drawTexturedModalRect(2, y + 1, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18);
					GlStateManager.enableDepth();
				}
				String name = I18n.format(potion.getName());
				if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
				label(Ui.fit(name, w - 26), 23, y + 2, textColor());
				label(Potion.getDurationString(e), 23, y + 11, 0xFF9DAAC0);
				y += 22;
			}
		}
	}
}
