package dev.lmg.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings saved to .minecraft/config/lmgclient.json. Add a field here to add a setting. */
public final class LmgConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(LmgClient.MOD_ID + ".json");
	private static LmgConfig instance = new LmgConfig();

	public boolean showSplash = true;
	public boolean themedMenus = true;
	public boolean menuEffects = true;
	public boolean hudEnabled = true;
	public boolean hudShowServer = true;
	public boolean hudShowFps = true;
	public boolean hudShowClock = true;

	public static LmgConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				LmgConfig loaded = GSON.fromJson(reader, LmgConfig.class);
				if (loaded != null) instance = loaded;
			} catch (Exception e) {
				LmgClient.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LmgClient.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
