package dev.lmg.client.social;

import dev.lmg.client.emote.Emote;
import dev.lmg.client.emote.EmoteNet;
import dev.lmg.client.emote.Emotes;
import dev.lmg.client.render.Draw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asking to climb on someone who isn't your partner, and answering when someone asks you.
 *
 * When someone asks, a small card shows at the top of your screen for 20 seconds:
 * "Mia_Builds wants to sit on your head - Y yes - U always - N no". You can also answer in
 * the menu (Social > Climbing). "Yes" lasts 2 hours, "always" until you take it back.
 */
public final class ClimbRequests {
	/** Someone asking you. */
	public record Ask(UUID from, String name, Emote kind, long at) {}

	private static final long SHOW_MS = 20_000;
	private static final List<Ask> asks = new ArrayList<>();
	/** Yeses that arrived just now (before the next friends sync brings them in properly). */
	private static final Map<UUID, Long> freshYes = new ConcurrentHashMap<>();
	/** What you asked for, so it starts as soon as they say yes. */
	private static UUID askedWho;
	private static Emote askedFor;
	private static long askedAt;

	private ClimbRequests() {}

	// ---- asking ------------------------------------------------------------------------------------

	/** Asks {@code who} if you can do {@code kind} with them. */
	public static void ask(Player who, Emote kind) {
		askedWho = who.getUUID();
		askedFor = kind;
		askedAt = Util.getMillis();
		EmoteNet.askClimb(who.getUUID(), kind, result -> Minecraft.getInstance().execute(() -> {
			switch (result) {
				case "allowed" -> start(who.getUUID(), kind);
				case "asked" -> dev.lmg.client.feature.LmgKeys.say("Asked " + who.getGameProfile().name() + ". Waiting for a yes...");
				case "asks_off" -> dev.lmg.client.feature.LmgKeys.say(who.getGameProfile().name() + " isn't taking climbing requests.");
				case "not_lmg" -> dev.lmg.client.feature.LmgKeys.say(who.getGameProfile().name() + " needs Lmg Client with Friends on.");
				case "too_fast" -> dev.lmg.client.feature.LmgKeys.say("You just asked - give them a moment.");
				case "no_room" -> dev.lmg.client.feature.LmgKeys.say("Climbing works on servers and LAN worlds, with Friends on.");
				default -> dev.lmg.client.feature.LmgKeys.say("Couldn't ask right now.");
			}
		}));
	}

	private static void start(UUID who, Emote kind) {
		askedWho = null;
		String why = PartnerPlay.climbOn(who, kind);
		if (why != null) dev.lmg.client.feature.LmgKeys.say(why);
	}

	/** A live answer to your ask arrived. */
	public static void answered(UUID owner, String answer) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			Player p = mc.level == null ? null : mc.level.getPlayerByUUID(owner);
			String name = p != null ? p.getGameProfile().name() : "They";
			if ("no".equals(answer)) {
				freshYes.remove(owner);
				// get down if you were on them
				if (mc.player != null) {
					Emotes.Playing mine = Emotes.get(mc.player.getUUID());
					if (mine != null && owner.equals(mine.with())) Emotes.stopMine();
				}
				if (owner.equals(askedWho)) {
					askedWho = null;
					dev.lmg.client.feature.LmgKeys.say(name + " said no.");
				}
				return;
			}
			freshYes.put(owner, answer.equals("always") ? Long.MAX_VALUE : System.currentTimeMillis() + 2 * 3600_000L);
			Social.refreshSoon();
			if (owner.equals(askedWho) && askedFor != null && Util.getMillis() - askedAt < 60_000) {
				dev.lmg.client.feature.LmgKeys.say(name + " said yes!");
				start(owner, askedFor);
			}
		});
	}

	/** Has {@code owner} said yes to you (partner, the last sync, or an answer that just came in)? */
	public static boolean mayClimbOn(UUID owner) {
		if (owner == null) return false;
		if (PartnerPlay.testAllowed) return true;
		if (owner.equals(Social.partner()) && Social.state().partnerAllowsClimb()) return true;
		if (Social.state().mayClimbOn(owner)) return true;
		Long until = freshYes.get(owner);
		return until != null && until > System.currentTimeMillis();
	}

	// ---- being asked -------------------------------------------------------------------------------

	/** Someone asked to climb on you. */
	public static void asked(UUID from, String name, Emote kind) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			synchronized (asks) {
				asks.removeIf(a -> a.from().equals(from));
				asks.add(new Ask(from, name, kind, Util.getMillis()));
			}
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.7f));
		});
	}

	/** The ask on screen right now (the newest one still waiting), or null. */
	public static Ask current() {
		long now = Util.getMillis();
		synchronized (asks) {
			asks.removeIf(a -> now - a.at() > SHOW_MS);
			return asks.isEmpty() ? null : asks.get(asks.size() - 1);
		}
	}

	public static List<Ask> waiting() {
		current();
		synchronized (asks) {
			return List.copyOf(asks);
		}
	}

	/** Your answer: "once", "always" or "no". */
	public static void answer(Ask a, String answer) {
		synchronized (asks) {
			asks.remove(a);
		}
		EmoteNet.answerClimb(a.from(), answer, ok -> Minecraft.getInstance().execute(() -> {
			if (!ok) dev.lmg.client.feature.LmgKeys.say("Couldn't send your answer - try again in the menu.");
			else Social.refreshSoon();
		}));
		dev.lmg.client.feature.LmgKeys.say(switch (answer) {
			case "once" -> a.name() + " can climb on you for 2 hours.";
			case "always" -> a.name() + " can climb on you any time (take it back in Social > Climbing).";
			default -> "Told " + a.name() + " no.";
		});
	}

	/** The answer keys (Y / U / N unless you changed them) only do anything while a question is up. */
	public static void tick(Minecraft mc) {
		boolean yes = false, always = false, no = false;
		while (dev.lmg.client.feature.LmgKeys.CLIMB_YES.consumeClick()) yes = true;
		while (dev.lmg.client.feature.LmgKeys.CLIMB_ALWAYS.consumeClick()) always = true;
		while (dev.lmg.client.feature.LmgKeys.CLIMB_NO.consumeClick()) no = true;
		Ask a = current();
		if (a == null || mc.gui.screen() != null) return;
		if (yes) answer(a, "once");
		else if (always) answer(a, "always");
		else if (no) answer(a, "no");
	}

	private static String keyName(net.minecraft.client.KeyMapping key) {
		return key.isUnbound() ? "?" : key.getTranslatedKeyMessage().getString();
	}

	/** The card at the top of the screen. */
	public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		Ask a = current();
		Minecraft mc = Minecraft.getInstance();
		if (a == null || mc.gui.hud.isHidden()) return;
		var font = mc.font;
		String what = switch (a.kind()) {
			case PERCH_HEAD -> "wants to sit on your head";
			case PERCH_SHOULDER -> "wants to sit on your shoulder";
			default -> "wants to play around you";
		};
		String line1 = a.name() + " " + what;
		String line2 = keyName(dev.lmg.client.feature.LmgKeys.CLIMB_YES) + "  yes      " + keyName(dev.lmg.client.feature.LmgKeys.CLIMB_ALWAYS)
				+ "  always      " + keyName(dev.lmg.client.feature.LmgKeys.CLIMB_NO) + "  no";
		int w = Math.max(font.width(line1), font.width(line2)) + 24, h = 32;
		int x = g.guiWidth() / 2 - w / 2, y = 28;
		float age = (Util.getMillis() - a.at()) / 1000f;
		float alpha = Math.min(1f, Math.min(age / 0.25f, (SHOW_MS / 1000f - age) / 0.6f));
		int pink = Partner.PINK;
		Draw.roundRect(g, x - 1, y - 1, x + w + 1, y + h + 1, 7, Draw.withAlpha(pink, 0.8f * alpha));
		Draw.roundRect(g, x, y, x + w, y + h, 6, Draw.withAlpha(0x0C1322, 0.94f * alpha));
		g.centeredText(font, line1, x + w / 2, y + 6, Draw.withAlpha(0xFFFFFF, alpha));
		g.centeredText(font, line2, x + w / 2, y + 19, Draw.withAlpha(0xF2C14E, alpha));
		// time left
		float left = 1f - age * 1000f / SHOW_MS;
		g.fill(x + 6, y + h - 2, x + 6 + Math.round((w - 12) * Math.max(0f, left)), y + h - 1, Draw.withAlpha(pink, 0.7f * alpha));
	}
}
