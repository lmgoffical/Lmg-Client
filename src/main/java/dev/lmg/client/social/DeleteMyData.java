package dev.lmg.client.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.config.CloudCodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.util.Util;

import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Delete all my Lmg data". Removes everything the Lmg server holds about you:
 *  - friends: your account, sessions, online status, friend links, partner and invites
 *  - your UID
 *  - the pets and cosmetics you shared
 *  - the config codes you shared from this computer
 * Proving the account is yours uses the same Mojang check as signing in (only Mojang sees
 * your login token). Then the copies on this computer go too: the friends session, your UID,
 * nicknames, and sharing is switched off. Your settings, maps and screenshots never left this
 * computer, so they stay unless you delete them yourself.
 */
public final class DeleteMyData {
	private static volatile boolean busy;

	private DeleteMyData() {}

	public static boolean busy() {
		return busy;
	}

	/** Runs it all; {@code done} gets one line saying what was deleted (or what couldn't be). */
	public static void run(Consumer<String> done) {
		if (busy) return;
		busy = true;
		Minecraft mc = Minecraft.getInstance();
		Util.nonCriticalIoPool().execute(() -> {
			List<String> gone = new ArrayList<>(), problems = new ArrayList<>();
			// 1. config codes you shared
			try {
				int n = CloudCodes.deleteMine().join();
				if (n > 0) gone.add(n + (n == 1 ? " shared config" : " shared configs"));
			} catch (Exception e) {
				problems.add("shared configs (couldn't reach the server)");
			}
			// 2. everything tied to your Minecraft account
			User user = mc.getUser();
			if (user == null || user.getAccessToken() == null || user.getAccessToken().length() < 10) {
				problems.add("account data (needs a real Minecraft account to prove it's you)");
			} else {
				try {
					byte[] b = new byte[20];
					new SecureRandom().nextBytes(b);
					String serverId = HexFormat.of().formatHex(b);
					mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
					JsonObject body = new JsonObject();
					body.addProperty("action", "forget");
					body.addProperty("username", user.getName());
					body.addProperty("serverId", serverId);
					HttpResponse<String> res = Social.http().send(Social.function(body).build(), HttpResponse.BodyHandlers.ofString());
					if (res.statusCode() == 200) {
						JsonObject removed = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("removed");
						if (removed.get("friends").getAsInt() > 0) gone.add("friends");
						if (removed.get("uid").getAsInt() > 0) gone.add("your UID");
						if (removed.get("avatar").getAsInt() > 0) gone.add("shared pets & cosmetics");
					} else {
						problems.add("account data (server said " + res.statusCode() + ")");
					}
				} catch (Exception e) {
					LmgClient.LOGGER.debug("Delete my data failed", e);
					problems.add("account data (couldn't reach Mojang or the Lmg server)");
				}
			}
			mc.execute(() -> {
				// 3. this computer: friends session, UID, nicknames, sharing off
				Social.wipeLocal();
				LmgConfig c = LmgConfig.get();
				c.shareAvatar = false;
				c.myUid = 0;
				c.friendNicknames.clear();
				LmgConfig.save();
				dev.lmg.client.sync.AvatarSync.clearRemote();
				busy = false;
				String what = gone.isEmpty() ? "There was nothing on the Lmg server to delete." : "Deleted: " + String.join(", ", gone) + ".";
				done.accept(problems.isEmpty() ? what : what + " Not done: " + String.join("; ", problems) + " - try again.");
			});
		});
	}
}
