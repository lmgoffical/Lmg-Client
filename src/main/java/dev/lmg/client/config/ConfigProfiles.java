package dev.lmg.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Saved configs ("profiles") and share codes.
 *
 * Profiles live in .minecraft/config/lmgclient-profiles/<name>.json.
 * A share code is "LMG1:" + base64url( deflate( {"n": name, "c": {settings}} ) ),
 * so it works anywhere text can be pasted - no server needed.
 */
public final class ConfigProfiles {
	public static final String CODE_PREFIX = "LMG1:";
	private static final int MAX_CODE_LENGTH = 20_000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson COMPACT = new Gson();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(LmgClient.MOD_ID + "-profiles");

	/** A named set of settings (from a file or a code). */
	public record Profile(String name, LmgConfig config) {}

	private ConfigProfiles() {}

	// ---------------------------------------------------------------------------------------------
	// Saved configs
	// ---------------------------------------------------------------------------------------------

	public static List<String> list() {
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(DIR)) return names;
		try (Stream<Path> files = Files.list(DIR)) {
			files.filter(f -> f.getFileName().toString().endsWith(".json"))
					.forEach(f -> {
						String file = f.getFileName().toString();
						names.add(file.substring(0, file.length() - 5));
					});
		} catch (IOException e) {
			LmgClient.LOGGER.warn("Could not list configs", e);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Saves the current settings under {@code name}. Returns the cleaned-up name actually used. */
	public static String saveCurrent(String name) throws IOException {
		String clean = cleanName(name);
		Files.createDirectories(DIR);
		JsonObject root = new JsonObject();
		root.addProperty("n", clean);
		root.add("c", LmgConfig.toJsonTree());
		Files.writeString(DIR.resolve(clean + ".json"), GSON.toJson(root), StandardCharsets.UTF_8);
		return clean;
	}

	public static void load(String name) throws IOException {
		String text = Files.readString(DIR.resolve(cleanName(name) + ".json"), StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		LmgConfig.apply(LmgConfig.fromJsonTree(root.get("c")));
	}

	public static void delete(String name) throws IOException {
		Files.deleteIfExists(DIR.resolve(cleanName(name) + ".json"));
	}

	/** Letters, numbers, spaces, - and _ only; 1-24 characters. */
	public static String cleanName(String name) {
		String clean = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
		if (clean.length() > 24) clean = clean.substring(0, 24).trim();
		return clean.isEmpty() ? "My config" : clean;
	}

	// ---------------------------------------------------------------------------------------------
	// Share codes
	// ---------------------------------------------------------------------------------------------

	/** A code for the current settings (without your personal settings - see LmgConfig.PERSONAL_FIELDS). */
	public static String codeForCurrent(String name) {
		JsonObject root = new JsonObject();
		root.addProperty("n", cleanName(name));
		root.add("c", LmgConfig.toShareableJsonTree());
		return encode(root);
	}

	/** A code for a saved config. Saved files keep personal settings; the code doesn't. */
	public static String codeForSaved(String name) throws IOException {
		String text = Files.readString(DIR.resolve(cleanName(name) + ".json"), StandardCharsets.UTF_8);
		return encode(JsonParser.parseString(text).getAsJsonObject());
	}

	/**
	 * Re-encodes a long code with personal settings taken out. Codes made by this version are
	 * already clean; this also cleans ones made by older versions before they get uploaded.
	 */
	public static String scrub(String code) {
		return encode(parse(code));
	}

	/** Reads a share code. Throws IllegalArgumentException with a readable message if it's not valid. */
	public static Profile decode(String code) {
		JsonObject root = parse(code);
		try {
			String name = root.has("n") ? cleanName(root.get("n").getAsString()) : "Imported";
			return new Profile(name, LmgConfig.fromJsonTree(root.get("c")));
		} catch (Exception e) {
			throw new IllegalArgumentException("That code is broken or incomplete");
		}
	}

	/** Builds a code from {"n": name, "c": settings}, always taking personal settings out of "c". */
	private static String encode(JsonObject root) {
		JsonObject out = root.deepCopy();
		if (out.has("c") && out.get("c").isJsonObject()) LmgConfig.stripPersonal(out.getAsJsonObject("c"));
		byte[] json = COMPACT.toJson(out).getBytes(StandardCharsets.UTF_8);
		return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(json));
	}

	/** The {"n", "c"} JSON inside a long code. Throws IllegalArgumentException with a readable message. */
	private static JsonObject parse(String code) {
		if (code == null) throw new IllegalArgumentException("Clipboard is empty");
		String trimmed = code.trim().replaceAll("\\s+", "");
		int at = trimmed.indexOf(CODE_PREFIX);
		if (at < 0) throw new IllegalArgumentException("That's not an Lmg config code");
		trimmed = trimmed.substring(at + CODE_PREFIX.length());
		if (trimmed.length() > MAX_CODE_LENGTH) throw new IllegalArgumentException("Code is too long");
		try {
			byte[] json = inflate(Base64.getUrlDecoder().decode(trimmed));
			return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalArgumentException("That code is broken or incomplete");
		}
	}

	private static byte[] deflate(byte[] data) {
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		deflater.setInput(data);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
		deflater.end();
		return out.toByteArray();
	}

	private static byte[] inflate(byte[] data) throws DataFormatException {
		Inflater inflater = new Inflater();
		inflater.setInput(data);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while (!inflater.finished()) {
			int n = inflater.inflate(buf);
			if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
			out.write(buf, 0, n);
			if (out.size() > 200_000) throw new DataFormatException("too big"); // no zip bombs
		}
		inflater.end();
		return out.toByteArray();
	}
}
