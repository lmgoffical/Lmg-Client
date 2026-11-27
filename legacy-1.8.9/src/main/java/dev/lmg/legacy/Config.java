package dev.lmg.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Every setting, saved as config/lmgclient-1.8.9.json. */
public final class Config {
	// ---- HUD ----
	public boolean hudFps = true, hudCps = true, hudKeys = true, hudArmour = true, hudPotions = true;
	public boolean hudCoords = false, hudPing = false, hudClock = false, hudSprint = true;
	public boolean hudBackground = true, hudShadow = true;
	public float hudScale = 1f;
	public int hudColor = 0;                       // index into Colors
	/** Where each panel sits: id -> {x, y} as a fraction of the screen (top-left corner). */
	public Map<String, float[]> hudPos = new HashMap<String, float[]>();

	// ---- zoom ----
	public float zoomAmount = 4f;                  // how many times closer
	public int zoomSpeed = 3;                      // 1 very slow .. 10 very fast
	public boolean smoothZoom = true, zoomScroll = true, zoomLowerSensitivity = true, zoomCinematic = false;

	// ---- crosshair ----
	public int crosshairStyle = 1;                 // see Crosshair.STYLES
	public int crosshairColor = 0;
	public int crosshairSize = 5, crosshairGap = 2, crosshairThickness = 1, crosshairSpin = 0;
	public boolean crosshairOutline = true, crosshairDot = false;

	// ---- PvP ----
	public boolean toggleSprint = false, fullbright = false;
	public float savedGamma = -1f;

	// ---- cosmetics (only on your own player) ----
	public String cape = "cape";                   // file in textures/capes, "" = none
	public boolean halo = false, chinaHat = false, catEars = false;
	public boolean animatedCape = true;
	public int cosmeticColor = 5;

	// ---- menu ----
	public boolean lmgMainMenu = true;
	public int accent = 1;
	public boolean seenWelcome = false;

	// --------------------------------------------------------------------------------------------
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	public static Config get() {
		return instance;
	}

	private static File file() {
		return new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "lmgclient-1.8.9.json");
	}

	public static void load() {
		File f = file();
		if (f.exists()) {
			Reader r = null;
			try {
				r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
				Config c = GSON.fromJson(r, Config.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				LmgLegacy.LOGGER.warn("Couldn't read {}, using defaults", f, e);
			} finally {
				try { if (r != null) r.close(); } catch (Exception ignored) {}
			}
		}
		instance.clamp();
	}

	public static void save() {
		File f = file();
		Writer w = null;
		try {
			f.getParentFile().mkdirs();
			w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
			GSON.toJson(instance, w);
		} catch (Exception e) {
			LmgLegacy.LOGGER.warn("Couldn't save {}", f, e);
		} finally {
			try { if (w != null) w.close(); } catch (Exception ignored) {}
		}
	}

	private void clamp() {
		if (hudPos == null) hudPos = new HashMap<String, float[]>();
		if (cape == null) cape = "";
		hudScale = Math.max(0.5f, Math.min(2f, hudScale));
		zoomAmount = Math.max(1.5f, Math.min(30f, zoomAmount));
		zoomSpeed = Math.max(1, Math.min(10, zoomSpeed));
		crosshairStyle = Math.max(0, Math.min(dev.lmg.legacy.feature.Crosshair.STYLES.length - 1, crosshairStyle));
		crosshairSize = Math.max(1, Math.min(20, crosshairSize));
		crosshairGap = Math.max(0, Math.min(10, crosshairGap));
		crosshairThickness = Math.max(1, Math.min(4, crosshairThickness));
		hudColor = Colors.clamp(hudColor);
		crosshairColor = Colors.clamp(crosshairColor);
		cosmeticColor = Colors.clamp(cosmeticColor);
		accent = Colors.clamp(accent);
	}
}
