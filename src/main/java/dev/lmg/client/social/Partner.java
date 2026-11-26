package dev.lmg.client.social;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.cosmetic.CosmeticModel;
import dev.lmg.client.cosmetic.CosmeticRegistry;
import dev.lmg.client.sync.AvatarSync;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Partners: a heart on their name tag, little hearts floating up between you when you stand
 * together, and (if you switch it on) the same outfit as them - each of you in your own colours.
 */
public final class Partner {
	public static final int PINK = 0xFF7EB3;
	private static int ticks;
	private static UUID watching;

	private Partner() {}

	/** Your partner's player in the world, if they're here. */
	public static Player inWorld(Minecraft mc) {
		UUID id = Social.partner();
		if (id == null || mc.level == null) return null;
		Player p = mc.level.getPlayerByUUID(id);
		if (p != null) return p;
		// offline-mode servers give players other ids: fall back to the name
		Social.Friend f = Social.state().friend(id);
		if (f == null) return null;
		for (Player q : mc.level.players()) if (q.getGameProfile().name().equalsIgnoreCase(f.name())) return q;
		return null;
	}

	public static boolean isPartner(Player p) {
		UUID id = Social.partner();
		if (id == null || p == null) return false;
		if (id.equals(p.getUUID())) return true;
		Social.Friend f = Social.state().friend(id);
		return f != null && p.getGameProfile().name().equalsIgnoreCase(f.name());
	}

	/** "Mia_Builds" -> "Mia_Builds ❤" for your partner's name tag. */
	public static Component withHeart(Component name) {
		MutableComponent out = name.copy();
		out.append(Component.literal(" ❤").withStyle(Style.EMPTY.withColor(PINK)));
		return out;
	}

	public static void tick(Minecraft mc) {
		ticks++;
		UUID id = Social.partner();
		// keep your partner's shared look fresh while you match them
		boolean match = id != null && LmgConfig.get().matchPartner;
		if (!java.util.Objects.equals(watching, match ? id : null)) {
			AvatarSync.watch(watching, false);
			watching = match ? id : null;
			AvatarSync.watch(watching, true);
		}
		if (match && ticks % 40 == 0) matchOutfit(false);
		if (id == null || mc.player == null || !LmgConfig.get().partnerHearts) return;

		// hearts between you when you're close
		Player them = inWorld(mc);
		if (them == null || them.isInvisible() || mc.player.isSpectator()) return;
		double d = them.distanceTo(mc.player);
		if (d > 4.5 || ticks % 26 != 0) return;
		Vec3 a = mc.player.getEyePosition(), b = them.getEyePosition();
		Vec3 mid = a.add(b).scale(0.5).add(0, 0.45, 0);
		var r = mc.level.getRandom();
		mc.level.addParticle(ParticleTypes.HEART, mid.x + (r.nextDouble() - 0.5) * 0.3, mid.y, mid.z + (r.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
	}

	/**
	 * Copies your partner's outfit onto you (only the built-in items; your colour choices stay).
	 * Returns a message for the screen, or null if nothing changed.
	 */
	public static String matchOutfit(boolean manual) {
		UUID id = Social.partner();
		if (id == null) return manual ? "You don't have a partner yet." : null;
		AvatarSync.Shared theirs = AvatarSync.getAlways(id);
		if (theirs == null) {
			AvatarSync.watch(id, true);
			return manual ? "Waiting for their outfit - they need \"Share my pets & cosmetics\" on." : null;
		}
		LmgConfig c = LmgConfig.get();
		Map<String, String> next = new HashMap<>();
		// keep anything of yours that isn't shareable (your own imported models)
		for (Map.Entry<String, String> e : c.equipped.entrySet()) {
			CosmeticModel m = CosmeticRegistry.get(e.getValue());
			if (m != null && m.custom) next.put(e.getKey(), e.getValue());
		}
		for (Map.Entry<String, String> e : theirs.equipped().entrySet()) {
			if (CosmeticRegistry.get(e.getValue()) != null) next.put(e.getKey(), e.getValue());
		}
		if (next.equals(c.equipped)) return manual ? "You're already matching." : null;
		c.equipped.clear();
		c.equipped.putAll(next);
		LmgConfig.save();
		Social.Friend f = Social.state().friend(id);
		String name = f != null ? f.name() : "your partner";
		if (!manual) dev.lmg.client.feature.LmgKeys.say("Matched " + name + "'s new outfit ❤");
		return "Matching " + name + " ❤";
	}
}
