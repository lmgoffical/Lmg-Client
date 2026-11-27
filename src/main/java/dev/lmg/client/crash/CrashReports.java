package dev.lmg.client.crash;

import com.google.gson.JsonObject;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import dev.lmg.client.UpdateChecker;
import dev.lmg.client.social.Social;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Crash reports. When the game starts after a crash it finds the new file(s) in crash-reports/ and, depending on
 * Help > Crash reports: asks first (default), sends on its own, or never sends. A report is cleaned before it
 * leaves the computer (your name, account ID, folder names, IPs and emails are replaced) and goes to the "crash"
 * edge function, which tells the developer on Discord. Crashes before Lmg loads can only be sent next time.
 */
public final class CrashReports {
	public enum Mode { ASK, ALWAYS, NEVER }

	public static final String[] MODE_LABELS = {"Ask me first", "Always send", "Never send"};
	private static final int MAX_BYTES = 200_000, MAX_SEND = 3;

	private static List<Path> pending = List.of();
	private static boolean handled;

	private CrashReports() {}

	/** At startup: which crash reports are new since we last looked. */
	public static void scan() {
		LmgConfig c = LmgConfig.get();
		Path dir = FabricLoader.getInstance().getGameDir().resolve("crash-reports");
		if (c.crashesCheckedUpTo <= 0) {
			// first run with crash reports: don't dig up old crashes from before
			c.crashesCheckedUpTo = System.currentTimeMillis();
			LmgConfig.save();
			return;
		}
		if (!Files.isDirectory(dir)) return;
		List<Path> found = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir)) {
			s.filter(p -> p.getFileName().toString().endsWith(".txt"))
					.filter(p -> modified(p) > c.crashesCheckedUpTo)
					.sorted(Comparator.comparingLong(CrashReports::modified))
					.forEach(found::add);
		} catch (Exception e) {
			LmgClient.LOGGER.debug("Couldn't look at crash-reports", e);
		}
		pending = found;
		if (!found.isEmpty()) LmgClient.LOGGER.info("Found {} new crash report(s)", found.size());
	}

	public static List<Path> pending() {
		return pending;
	}

	/** Called every tick; once the title screen is up, deals with any new crash reports. */
	public static void tick(Minecraft mc) {
		if (handled || pending.isEmpty()) return;
		var screen = mc.gui.screen();
		if (!(screen instanceof dev.lmg.client.screen.LmgTitleScreen) && !(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
		handled = true;
		switch (LmgConfig.get().crashReports) {
			case ALWAYS -> sendAll(null);
			case NEVER -> markChecked();
			default -> mc.gui.setScreen(new CrashPromptScreen(screen, pending.size()));
		}
	}

	/** Sends the newest few reports in the background; `done` (if any) gets a short message for the screen. */
	public static void sendAll(java.util.function.Consumer<String> done) {
		List<Path> list = new ArrayList<>(pending);
		markChecked();
		if (list.size() > MAX_SEND) list = list.subList(list.size() - MAX_SEND, list.size());
		final List<Path> toSend = list;
		Util.nonCriticalIoPool().execute(() -> {
			int ok = 0;
			for (Path p : toSend) if (send(p)) ok++;
			int sent = ok;
			LmgClient.LOGGER.info("Sent {} of {} crash report(s)", sent, toSend.size());
			if (done != null) Minecraft.getInstance().execute(() -> done.accept(sent > 0
					? "Sent - thank you! It helps get this fixed." : "Couldn't send it right now. It'll stay in your crash-reports folder."));
		});
	}

	/** Don't ask about these crashes again. */
	public static void markChecked() {
		LmgConfig c = LmgConfig.get();
		long newest = c.crashesCheckedUpTo;
		for (Path p : pending) newest = Math.max(newest, modified(p));
		c.crashesCheckedUpTo = Math.max(newest, System.currentTimeMillis() - 1000);
		LmgConfig.save();
		pending = List.of();
	}

	private static boolean send(Path p) {
		try {
			byte[] bytes = Files.readAllBytes(p);
			String text = new String(bytes, 0, Math.min(bytes.length, MAX_BYTES), StandardCharsets.UTF_8);
			JsonObject body = new JsonObject();
			body.addProperty("installId", installId());
			body.addProperty("version", UpdateChecker.currentVersion());
			body.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
			body.addProperty("edition", "main");
			body.addProperty("report", clean(text));
			HttpRequest req = HttpRequest.newBuilder(URI.create("https://fieakrewvbyubtyxpqhr.supabase.co/functions/v1/crash"))
					.header("apikey", Social.publishableKey()).header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(20))
					.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
			HttpResponse<String> res = Social.http().send(req, HttpResponse.BodyHandlers.ofString());
			return res.statusCode() == 200;
		} catch (Exception e) {
			LmgClient.LOGGER.debug("Couldn't send crash report {}", p, e);
			return false;
		}
	}

	private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?\\b");
	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern WIN_USER = Pattern.compile("(?i)([A-Z]:[\\\\/]+(?:Users|Documents and Settings)[\\\\/]+)[^\\\\/\\s\"']+");
	private static final Pattern NIX_USER = Pattern.compile("(/(?:home|Users)/)[^/\\s\"']+");
	private static final Pattern TOKEN = Pattern.compile("(?i)(--accessToken\\s+|token[\"'=:\\s]+)[A-Za-z0-9._-]{12,}");

	/** Takes out anything that points at a person: their name and account ID, folder names, IPs, emails, tokens. */
	public static String clean(String text) {
		Minecraft mc = Minecraft.getInstance();
		String out = text;
		String home = System.getProperty("user.home", "");
		if (home.length() > 3) out = out.replace(home, "~").replace(home.replace('\\', '/'), "~");
		String user = System.getProperty("user.name", "");
		if (mc != null && mc.getUser() != null) {
			String name = mc.getUser().getName();
			if (name != null && name.length() >= 3) out = out.replaceAll("(?i)\\b" + Pattern.quote(name) + "\\b", "<player>");
			UUID id = mc.getUser().getProfileId();
			if (id != null) {
				out = out.replace(id.toString(), "<player-id>").replace(id.toString().replace("-", ""), "<player-id>");
			}
		}
		if (user.length() >= 3) out = out.replaceAll("(?i)\\b" + Pattern.quote(user) + "\\b", "<user>");
		out = WIN_USER.matcher(out).replaceAll("$1<user>");
		out = NIX_USER.matcher(out).replaceAll("$1<user>");
		out = IP.matcher(out).replaceAll("<ip>");
		out = EMAIL.matcher(out).replaceAll("<email>");
		out = TOKEN.matcher(out).replaceAll("$1<hidden>");
		return out;
	}

	/**
	 * A random ID for this computer's install, kept in its own file (not in the settings, so it never ends up in a
	 * shared config code). It only lets the server tell "one player crashing a lot" from "many players".
	 */
	static String installId() {
		Path f = FabricLoader.getInstance().getConfigDir().resolve("lmgclient-install-id.txt");
		try {
			if (Files.exists(f)) {
				String s = Files.readString(f).trim();
				if (s.matches("[0-9a-f-]{36}")) return s;
			}
			String id = UUID.randomUUID().toString();
			Files.writeString(f, id);
			return id;
		} catch (Exception e) {
			return UUID.randomUUID().toString();
		}
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (Exception e) {
			return 0;
		}
	}
}
