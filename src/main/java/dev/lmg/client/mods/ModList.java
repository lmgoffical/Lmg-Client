package dev.lmg.client.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The mods in your mods folder: the ones running now, plus any you switched off
 * (renamed to .jar.disabled, the usual way to turn a Fabric mod off).
 *
 * Windows keeps a running mod's file locked, so switching a running mod OFF can't always happen
 * straight away. Then it's done the moment the game closes, by a tiny helper that waits for the
 * game to exit and renames the file.
 */
public final class ModList {
	public static final Path FOLDER = FabricLoader.getInstance().getGameDir().resolve("mods");
	/** Mods the game or Lmg Client can't run without. */
	private static final Set<String> REQUIRED = Set.of("lmgclient", "fabric-api", "fabricloader", "minecraft", "java");
	/** Installed by the Lmg installer. */
	private static final Set<String> LMG_PACK = Set.of("lmgclient", "fabric-api", "sodium", "lithium", "iris");

	public record Mod(String id, String name, String version, String description, String authors,
					  Path file, boolean running, boolean required, boolean lmgPack, String iconPath, List<String> neededBy) {
		public boolean on() {
			Path target = PENDING.get(file);
			if (target != null) return target.toString().endsWith(".jar");
			return file != null && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
		}
	}

	/** Renames waiting for the game to close: from -> to. */
	private static final Map<Path, Path> PENDING = new LinkedHashMap<>();
	private static final Map<String, Identifier> ICONS = new HashMap<>();
	private static boolean exitHookAdded;

	private ModList() {}

	public static List<Mod> all() {
		List<Mod> out = new ArrayList<>();
		Map<String, List<String>> neededBy = new HashMap<>();
		for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
			for (ModDependency d : c.getMetadata().getDependencies()) {
				if (d.getKind() == ModDependency.Kind.DEPENDS) {
					neededBy.computeIfAbsent(d.getModId(), k -> new ArrayList<>()).add(c.getMetadata().getName());
				}
			}
		}
		java.util.Set<Path> known = new java.util.HashSet<>();
		for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
			if (c.getContainingMod().isPresent()) continue;   // bundled inside another mod
			ModMetadata m = c.getMetadata();
			if (m.getId().equals("java") || m.getId().equals("minecraft") || m.getId().equals("fabricloader")) continue;
			Path file = c.getOrigin().getKind() == ModOrigin.Kind.PATH && !c.getOrigin().getPaths().isEmpty()
					? c.getOrigin().getPaths().get(0) : null;
			if (file != null) {
				known.add(file.toAbsolutePath().normalize());
				// already switched off this session (renamed while running)
				Path off = file.resolveSibling(file.getFileName() + ".disabled");
				if (!Files.exists(file) && Files.exists(off)) file = off;
				known.add(file.toAbsolutePath().normalize());
			}
			String authors = m.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
			out.add(new Mod(m.getId(), m.getName(), m.getVersion().getFriendlyString(), m.getDescription(), authors,
					file, true, REQUIRED.contains(m.getId()), LMG_PACK.contains(m.getId()), m.getIconPath(32).orElse(null),
					neededBy.getOrDefault(m.getId(), List.of())));
		}
		// files in the folder that aren't running: switched off, or switched on / added since the game started
		try (Stream<Path> files = Files.list(FOLDER)) {
			for (Path p : files.toList()) {
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (!(n.endsWith(".jar") || n.endsWith(".jar.disabled"))) continue;
				if (known.contains(p.toAbsolutePath().normalize())) continue;
				Mod mod = readJar(p);
				if (mod != null) out.add(mod);
			}
		} catch (Exception ignored) {
		}
		out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		return out;
	}

	private static Mod readJar(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry e = zip.getEntry("fabric.mod.json");
			if (e == null) return null;
			JsonObject j;
			try (InputStream in = zip.getInputStream(e)) {
				j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
			String id = str(j, "id", jar.getFileName().toString());
			String icon = null;
			if (j.has("icon")) {
				JsonElement ic = j.get("icon");
				if (ic.isJsonPrimitive()) icon = ic.getAsString();
				else if (ic.isJsonObject() && !ic.getAsJsonObject().entrySet().isEmpty()) icon = ic.getAsJsonObject().entrySet().iterator().next().getValue().getAsString();
			}
			return new Mod(id, str(j, "name", id), str(j, "version", "?"), str(j, "description", ""), "",
					jar, false, false, LMG_PACK.contains(id), icon, List.of());
		} catch (Exception ex) {
			return null;
		}
	}

	private static String str(JsonObject j, String k, String fallback) {
		return j.has(k) && j.get(k).isJsonPrimitive() ? j.get(k).getAsString() : fallback;
	}

	/** True if this mod's on/off state changes the next time the game starts. */
	public static boolean changesAfterRestart(Mod mod) {
		if (mod.file() == null) return false;
		if (PENDING.containsKey(mod.file())) return true;
		return mod.running() != mod.on();
	}

	/**
	 * Switches a mod on or off for the next time you start the game.
	 * @return a message to show if it couldn't be done, or null
	 */
	public static String toggle(Mod mod) {
		if (mod.file() == null) return mod.name() + " is built in, so it can't be switched off here.";
		if (mod.required() && mod.on()) return mod.name() + " is needed to run the game and Lmg Client.";
		boolean turnOn = !mod.on();
		if (!turnOn && !mod.neededBy().isEmpty()) {
			return mod.name() + " is needed by " + String.join(", ", mod.neededBy()) + ". Switch that off first.";
		}
		// a pending rename can just be cancelled
		if (PENDING.remove(mod.file()) != null) return null;
		Path from = mod.file();
		String name = from.getFileName().toString();
		Path to = turnOn ? from.resolveSibling(name.replaceFirst("(?i)\\.disabled$", "")) : from.resolveSibling(name + ".disabled");
		try {
			Files.move(from, to);
			return null;
		} catch (Exception e) {
			// Windows: the running mod's file is in use. Do it when the game closes.
			PENDING.put(from, to);
			addExitHook();
			return null;
		}
	}

	public static boolean hasPending() {
		return !PENDING.isEmpty();
	}

	private static void addExitHook() {
		if (exitHookAdded) return;
		exitHookAdded = true;
		Runtime.getRuntime().addShutdownHook(new Thread(ModList::applyPending, "Lmg mod switch"));
	}

	/** Runs as the game shuts down: starts a small PowerShell job that waits for the game to exit, then renames. */
	private static void applyPending() {
		if (PENDING.isEmpty()) return;
		if (Util.getPlatform() != Util.OS.WINDOWS) return;
		StringBuilder script = new StringBuilder("Wait-Process -Id ").append(ProcessHandle.current().pid()).append(" -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 500; ");
		for (Map.Entry<Path, Path> e : PENDING.entrySet()) {
			script.append("Move-Item -LiteralPath '").append(e.getKey().toString().replace("'", "''"))
					.append("' -Destination '").append(e.getValue().toString().replace("'", "''")).append("' -ErrorAction SilentlyContinue; ");
		}
		try {
			new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", script.toString()).start();
		} catch (Exception ex) {
			LmgClient.LOGGER.warn("Couldn't switch mods off after closing", ex);
		}
	}

	/** The mod's icon as a texture (loaded once), or null. */
	public static Identifier icon(Mod mod) {
		if (mod.iconPath() == null) return null;
		Identifier cached = ICONS.get(mod.id());
		if (cached != null || ICONS.containsKey(mod.id())) return cached;
		Identifier id = null;
		try (InputStream in = openIcon(mod)) {
			if (in != null) {
				NativeImage img = NativeImage.read(in);
				id = LmgClient.id("modicon/" + mod.id().replaceAll("[^a-z0-9_.-]", "_"));
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mod icon " + mod.id(), img));
			}
		} catch (Exception ignored) {
			id = null;
		}
		ICONS.put(mod.id(), id);
		return id;
	}

	private static InputStream openIcon(Mod mod) throws Exception {
		if (mod.running()) {
			var c = FabricLoader.getInstance().getModContainer(mod.id());
			if (c.isEmpty()) return null;
			var p = c.get().findPath(mod.iconPath());
			return p.isPresent() ? Files.newInputStream(p.get()) : null;
		}
		ZipFile zip = new ZipFile(mod.file().toFile());
		ZipEntry e = zip.getEntry(mod.iconPath());
		if (e == null) {
			zip.close();
			return null;
		}
		byte[] bytes;
		try (InputStream in = zip.getInputStream(e)) {
			bytes = in.readAllBytes();
		}
		zip.close();
		return new java.io.ByteArrayInputStream(bytes);
	}
}
