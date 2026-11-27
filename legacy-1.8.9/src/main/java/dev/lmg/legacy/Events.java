package dev.lmg.legacy;

import dev.lmg.legacy.feature.Cps;
import dev.lmg.legacy.feature.Crosshair;
import dev.lmg.legacy.feature.Hud;
import dev.lmg.legacy.feature.Keys;
import dev.lmg.legacy.feature.PvpTweaks;
import dev.lmg.legacy.feature.Zoom;
import dev.lmg.legacy.ui.HomeScreen;
import dev.lmg.legacy.ui.HudEditor;
import dev.lmg.legacy.ui.MainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/** Everything Lmg listens to. */
public class Events {
	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent e) {
		if (e.phase != TickEvent.Phase.START) return;
		Minecraft mc = Minecraft.getMinecraft();
		PvpTweaks.tick();
		dev.lmg.legacy.render.AnimatedCape.tick();
		while (Keys.MENU.isPressed()) if (mc.currentScreen == null) mc.displayGuiScreen(new HomeScreen(null));
		while (Keys.HUD_EDITOR.isPressed()) if (mc.currentScreen == null) mc.displayGuiScreen(new HudEditor(null));
		TestDriver.tick();
	}

	@SubscribeEvent
	public void onMouseInput(InputEvent.MouseInputEvent e) {
		if (Mouse.getEventButtonState()) Cps.click(Mouse.getEventButton());
	}

	/** Scroll while zooming = zoom further (and don't switch hotbar slots). */
	@SubscribeEvent
	public void onMouse(MouseEvent e) {
		if (e.dwheel != 0 && Zoom.held() && Config.get().zoomScroll) {
			Zoom.scroll(e.dwheel);
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onFov(EntityViewRenderEvent.FOVModifier e) {
		float f = Zoom.factor();
		if (f != 1f) e.setFOV(e.getFOV() / f);
	}

	@SubscribeEvent
	public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
		if (e.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && Crosshair.custom()) {
			e.setCanceled(true);
			Crosshair.draw(e.resolution.getScaledWidth() / 2f, e.resolution.getScaledHeight() / 2f);
		}
	}

	@SubscribeEvent
	public void onOverlayPost(RenderGameOverlayEvent.Post e) {
		if (e.type == RenderGameOverlayEvent.ElementType.ALL && !(Minecraft.getMinecraft().currentScreen instanceof HudEditor)) {
			Hud.render(e.resolution, false);
		}
	}

	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent e) {
		if (e.gui != null) Zoom.restore();
		if (e.gui instanceof GuiMainMenu && Config.get().lmgMainMenu) e.gui = new MainMenu();
	}
}
