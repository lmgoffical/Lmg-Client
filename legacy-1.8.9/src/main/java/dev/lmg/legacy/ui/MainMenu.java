package dev.lmg.legacy.ui;

import dev.lmg.legacy.LmgLegacy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;

import java.io.IOException;
import java.util.Random;

/** The Lmg title screen: dark sky, slow stars, the logo, and a column of buttons. */
public class MainMenu extends GuiScreen {
	private static final ResourceLocation LOGO = new ResourceLocation("lmgclient", "textures/gui/logo.png");
	private final long openedAt = System.currentTimeMillis();
	private final float[][] stars = new float[90][3];

	public MainMenu() {
		Random r = new Random(7);
		for (float[] s : stars) {
			s[0] = r.nextFloat();
			s[1] = r.nextFloat();
			s[2] = 0.3f + r.nextFloat() * 0.7f;
		}
	}

	/** A flat Lmg-style button: dark until you hover, then the accent colour. */
	static class Button extends GuiButton {
		Button(int id, int x, int y, int w, int h, String text) {
			super(id, x, y, w, h, text);
		}

		@Override
		public void drawButton(Minecraft mc, int mx, int my) {
			if (!visible) return;
			hovered = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
			Ui.round(xPosition, yPosition + 1, xPosition + width, yPosition + height + 1, 3, 0x80000000);
			Ui.round(xPosition, yPosition, xPosition + width, yPosition + height, 3, hovered ? Ui.accent() : 0xCC16213A);
			Ui.centered(displayString, xPosition + width / 2, yPosition + (height - 8) / 2, hovered ? 0xFFFFFFFF : Ui.TEXT);
		}
	}

	@Override
	public void initGui() {
		int bw = 160, bh = 20, x = width / 2 - bw / 2, y = height / 2 - 6;
		buttonList.clear();
		buttonList.add(new Button(1, x, y, bw, bh, "Singleplayer"));
		buttonList.add(new Button(2, x, y + 24, bw, bh, "Multiplayer"));
		buttonList.add(new Button(3, x, y + 48, bw, bh, "Lmg Client"));
		buttonList.add(new Button(4, x, y + 72, bw / 2 - 2, bh, "Options"));
		buttonList.add(new Button(5, x + bw / 2 + 2, y + 72, bw / 2 - 2, bh, "Mods"));
		buttonList.add(new Button(6, x, y + 96, bw, bh, "Quit"));
	}

	@Override
	protected void actionPerformed(GuiButton b) throws IOException {
		switch (b.id) {
			case 1: mc.displayGuiScreen(new GuiSelectWorld(this)); break;
			case 2: mc.displayGuiScreen(new GuiMultiplayer(this)); break;
			case 3: mc.displayGuiScreen(new HomeScreen(this)); break;
			case 4: mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)); break;
			case 5: mc.displayGuiScreen(new GuiModList(this)); break;
			case 6: mc.shutdown(); break;
			default: break;
		}
	}

	@Override
	public void drawScreen(int mx, int my, float pt) {
		drawGradientRect(0, 0, width, height, 0xFF0B1022, 0xFF1B1035);
		long t = System.currentTimeMillis() - openedAt;
		for (float[] s : stars) {
			float tw = 0.5f + 0.5f * (float) Math.sin(t / 900.0 * s[2] + s[0] * 20);
			int a = (int) (40 + 160 * s[2] * tw);
			int x = (int) (s[0] * width), y = (int) (((s[1] + t / 400000f * s[2]) % 1f) * height);
			drawRect(x, y, x + 1, y + 1, (a << 24) | 0xFFFFFF);
		}
		// logo + name
		int ls = 64, lx = width / 2 - ls / 2, ly = height / 2 - 6 - 24 - ls;
		mc.getTextureManager().bindTexture(LOGO);
		GlStateManager.enableBlend();
		GlStateManager.color(1f, 1f, 1f, 1f);
		Gui.drawModalRectWithCustomSizedTexture(lx, ly, 0, 0, ls, ls, ls, ls);
		GlStateManager.disableBlend();
		GlStateManager.pushMatrix();
		GlStateManager.translate(width / 2f, ly + ls + 3, 0);
		GlStateManager.scale(1.5f, 1.5f, 1);
		Ui.centered("Lmg Client", 0, 0, 0xFFFFFFFF);
		GlStateManager.popMatrix();
		Ui.text("Minecraft 1.8.9 - Forge", 4, height - 12, Ui.DIM);
		String v = "Lmg Client " + LmgLegacy.VERSION + " (1.8.9 PvP edition)";
		Ui.text(v, width - Ui.width(v) - 4, height - 12, Ui.DIM);
		super.drawScreen(mx, my, pt);
	}
}
