package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.pet.PetConfig;
import dev.lmg.client.pet.Pets;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * You (with your cosmetics) and your first pet, standing next to the Right Shift menu when
 * there's room. Drag to spin; let go and it keeps turning a little, then settles. Your head
 * follows the mouse.
 */
final class MenuAvatar {
	private float yaw = 22f, spinVel, idle;
	private boolean dragging;
	private long lastFrame = -1;
	private int x0, y0, x1, y1;
	private boolean shown;
	private int buttonX0, buttonY0, buttonX1, buttonY1;

	/** Picks the side of the panel with more room. Returns false if there isn't enough anywhere. */
	boolean layout(int screenW, int screenH, int panelX0, int panelY0, int panelX1, int panelY1) {
		Minecraft mc = Minecraft.getInstance();
		int left = panelX0 - 8, right = screenW - panelX1 - 8;
		int room = Math.max(left, right);
		shown = mc.player != null && room >= 86 && LmgConfig.get().menuAvatar;
		if (!shown) return false;
		int w = Math.min(150, room - 8);
		int h = Math.min(panelY1 - panelY0, 230);
		boolean onRight = right >= left;
		x0 = onRight ? panelX1 + 8 + (room - w) / 2 : (panelX0 - 8 - room) + (room - w) / 2;
		x0 = Math.max(4, x0);
		x1 = x0 + w;
		y0 = panelY0 + (panelY1 - panelY0 - h) / 2;
		y1 = y0 + h;
		return true;
	}

	void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float appear) {
		if (!shown) return;
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		if (!dragging) {
			// coast, then drift back to a three-quarter view and sway a little
			yaw += spinVel * dt;
			spinVel *= (float) Math.pow(0.05, dt);
			idle += dt;
			if (Math.abs(spinVel) < 20f) {
				float rest = 22f + (float) Math.sin(idle * 0.6f) * 10f;
				float diff = Mth.wrapDegrees(rest - yaw);
				yaw += diff * Math.min(1f, dt * 1.4f);
			}
		}
		int primary = Theme.primary();
		int cx = (x0 + x1) / 2;
		int floorY = y1 - 40;

		// a soft light behind you and a shadow on the floor
		for (int i = 0; i < 6; i++) {
			int r = 18 + i * 9;
			Draw.roundRect(g, cx - r, floorY - 80 - r / 2, cx + r, floorY - 80 + r * 2, r, Draw.withAlpha(primary, 0.025f * appear));
		}
		for (int i = 0; i < 4; i++) {
			int rw = 34 - i * 6, rh = 6 - i;
			Draw.roundRect(g, cx - rw, floorY - rh / 2, cx + rw, floorY + rh / 2 + 1, rh, Draw.withAlpha(0x000000, 0.18f * appear));
		}

		// you (the box is as wide as the stage so wings and capes aren't cut off)
		int tall = Math.min(118, floorY - y0 - 14);
		EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 1f);
		// the box grows the same amount up and down, so hats and halos fit without moving you
		drawEntity(g, state, x0, floorY - tall - 16, x1, floorY + 4 + 16, tall, yaw, mouseX, mouseY, appear);

		// your pet, in front of you and to the side, at the size you picked for it
		PetConfig pet = firstPet();
		if (pet != null) {
			EntityRenderState ps = Pets.preview(pet);
			if (ps != null) {
				float perBlock = tall / 1.8f;
				float size = PetConfig.SIZES[Math.max(0, Math.min(PetConfig.SIZES.length - 1, pet.size))];
				int ph = Math.max(14, Math.round(Math.max(0.3f, ps.boundingBoxHeight / Math.max(0.01f, ps instanceof LivingEntityRenderState l ? l.scale : 1f)) * perBlock * size));
				drawEntity(g, ps, cx - 70, floorY - ph - 6, cx - 4, floorY + 6, ph, yaw - 40f, mouseX, mouseY, appear);
			}
		}

		// name, and a way to change your look
		String name = mc.player.getGameProfile().name();
		String line = pet != null && pet.name != null && !pet.name.isBlank() ? name + " & " + pet.name : name;
		if (font.width(line) > x1 - x0) line = font.plainSubstrByWidth(line, x1 - x0 - 6) + "..";
		g.centeredText(font, line, cx, floorY + 8, Draw.withAlpha(0xE8EDF5, appear));
		String label = "Change outfit";
		int bw = font.width(label) + 16;
		buttonX0 = cx - bw / 2;
		buttonX1 = cx + bw / 2;
		buttonY0 = floorY + 20;
		buttonY1 = buttonY0 + 14;
		boolean over = mouseX >= buttonX0 && mouseX < buttonX1 && mouseY >= buttonY0 && mouseY < buttonY1;
		PillButton.draw(g, font, buttonX0, buttonY0, bw, 14, label, false, over ? 1f : 0f, appear);
		if (!dragging && over(mouseX, mouseY) && !over && mouseY < floorY) {
			g.centeredText(font, "drag to spin", cx, buttonY1 + 5, Draw.withAlpha(0x7C8BA3, 0.8f * appear));
		}
	}

	private static PetConfig firstPet() {
		for (PetConfig p : LmgConfig.get().pets) if (p.enabled) return p;
		return null;
	}

	/** Like the inventory's player view, but turned by {@code turn} degrees, with the head looking at the mouse. */
	private void drawEntity(GuiGraphicsExtractor g, EntityRenderState state, int bx0, int by0, int bx1, int by1, int tallPx,
							float turn, int mouseX, int mouseY, float appear) {
		float headCx = (bx0 + bx1) / 2f, headCy = by0 + (by1 - by0) * 0.18f;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180f + turn;
			float look = (float) Math.atan((headCx - mouseX) / 60f) * 30f;
			living.yRot = Mth.clamp(-look, -40f, 40f);
			living.xRot = Mth.clamp((float) Math.atan((headCy - mouseY) / 60f) * -20f, -25f, 25f);
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1f;
		}
		state.shadowPieces.clear();
		state.nameTag = null;
		float h = Math.max(0.3f, state.boundingBoxHeight);
		// size by height: the tallest part of you fills the space
		float pixels = Math.min(tallPx / h, (bx1 - bx0) / Math.max(0.3f, state.boundingBoxWidth)) * 0.92f * (0.92f + 0.08f * appear);
		Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(-6));
		Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
		g.entity(state, pixels, new Vector3f(0f, h / 2f, 0f), rot, tilt, bx0, by0, bx1, by1);
	}

	boolean over(double mx, double my) {
		return shown && mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	/** Returns true if the click was ours. */
	boolean mouseClicked(double mx, double my, Runnable openCosmetics) {
		if (!shown) return false;
		if (mx >= buttonX0 && mx < buttonX1 && my >= buttonY0 && my < buttonY1) {
			openCosmetics.run();
			return true;
		}
		if (over(mx, my)) {
			dragging = true;
			spinVel = 0;
			return true;
		}
		return false;
	}

	boolean mouseDragged(double dx) {
		if (!dragging) return false;
		yaw += (float) dx * 2.4f;
		spinVel = (float) dx * 2.4f * 30f;
		idle = 0;
		return true;
	}

	boolean mouseReleased() {
		if (!dragging) return false;
		dragging = false;
		return true;
	}
}
