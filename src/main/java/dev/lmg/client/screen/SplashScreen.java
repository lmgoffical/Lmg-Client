package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.Sparkles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * "Press to enter" screen shown once per launch. Clicking spins the star,
 * throws out sparkles, fades to black, then opens the main menu.
 */
public class SplashScreen extends Screen {
	private static final float INTRO_MS = 900f;
	private static final float ENTER_MS = 1100f;

	private final long openedAt = Util.getMillis();
	private final Sparkles burst = new Sparkles();
	private long enterAt = -1;

	public SplashScreen() {
		super(Component.literal(LmgClient.CLIENT_NAME));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		MenuBackground.draw(g, width, height, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		long now = Util.getMillis();
		float time = now / 1000f;
		float intro = Draw.easeOutCubic((now - openedAt) / INTRO_MS);
		float enter = enterAt < 0 ? 0f : Draw.clamp01((now - enterAt) / ENTER_MS);

		float cx = width / 2f;
		float cy = height / 2f - 14f;
		float bob = (float) Math.sin(time * 1.6f) * 2f * (1 - enter);

		// Logo: gentle sway while waiting; full spin and grow once you click.
		float size = 58f * (0.85f + 0.15f * intro) * (1f + 0.55f * Draw.easeInCubic(enter));
		float sway = (float) Math.sin(time * 0.9f) * 0.06f;
		float spin = Draw.easeInOutCubic(enter) * (float) (Math.PI * 2);
		float glowPulse = 0.85f + 0.15f * (float) Math.sin(time * 2.2f);
		float logoAlpha = intro * (1f - Draw.easeInCubic(Draw.clamp01((enter - 0.55f) / 0.45f)));
		Draw.logo(g, cx, cy + bob, size, sway * (1 - enter) + spin, logoAlpha * glowPulse);

		// Name and prompt fade out quickly when entering.
		float textAlpha = intro * (1f - Draw.clamp01(enter / 0.3f));
		Draw.scaledText(g, font, LmgClient.CLIENT_NAME, cx, cy + 38f + bob, 1.6f, Draw.white(textAlpha));
		float pulse = 0.45f + 0.35f * (float) Math.sin(time * 3f);
		float promptIn = Draw.clamp01((now - openedAt - 500) / 600f);
		Draw.scaledText(g, font, "Press to enter", cx, cy + 58f + bob, 0.85f, Draw.withAlpha(0xC8C8D2, textAlpha * pulse * promptIn));

		burst.update();
		burst.draw(g, 1f);

		// Fade to black at the end of the transition (drawn on top of everything).
		float black = Draw.clamp01((enter - 0.65f) / 0.35f);
		if (black > 0) {
			g.nextStratum();
			g.fill(0, 0, width, height, Draw.withAlpha(0x000000, black));
		}
	}

	@Override
	public void tick() {
		if (enterAt >= 0 && Util.getMillis() - enterAt >= ENTER_MS) {
			minecraft.gui.setScreen(new LmgTitleScreen(true));
		}
	}

	private void enter() {
		if (enterAt >= 0) return;
		enterAt = Util.getMillis();
		burst.burst(width / 2f, height / 2f - 14f, 46, 170f);
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		enter();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		enter();
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
