package dev.lmg.client.social;

import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;

/** "Mia is online" - a small card with their face, in the corner. */
public final class FriendToast implements Toast {
	private static final long SHOW_MS = 5000;

	private final UUID uuid;
	private final String name, line;
	private Visibility visibility = Visibility.SHOW;
	private final int width;

	private FriendToast(UUID uuid, String name, String line) {
		this.uuid = uuid;
		this.name = name;
		this.line = line;
		Font font = Minecraft.getInstance().font;
		this.width = Math.max(150, Math.max(font.width(name), font.width(line)) + 44);
	}

	public static void show(UUID uuid, String name, String line) {
		Minecraft mc = Minecraft.getInstance();
		String nick = Social.nickname(uuid);
		mc.gui.toastManager().addToast(new FriendToast(uuid, nick.isBlank() ? name : nick, line));
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		if (fullyVisibleForMs >= SHOW_MS * manager.getNotificationDisplayTimeMultiplier()) visibility = Visibility.HIDE;
	}

	@Override
	public SoundEvent getSoundEvent() {
		return SoundEvents.UI_TOAST_IN;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long fullyVisibleForMs) {
		int w = width(), h = height();
		Draw.roundRect(g, 0, 0, w, h, 6, 0xFF1C2943);
		Draw.roundRect(g, 1, 1, w - 1, h - 1, 5, 0xF00C1322);
		// accent stripe on the left
		g.fill(1, 6, 3, h - 6, 0xFF000000 | Theme.accent());
		PlayerFaceExtractor.extractRenderState(g, ResolvableProfile.createUnresolved(uuid), 9, 8, 16);
		g.text(font, name, 32, 7, 0xFFFFFFFF, false);
		g.text(font, line, 32, 18, Draw.withAlpha(0x9DAAC0, 1f), false);
	}
}
