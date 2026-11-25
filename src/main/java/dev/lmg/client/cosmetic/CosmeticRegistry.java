package dev.lmg.client.cosmetic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lmg.client.LmgClient;
import dev.lmg.client.LmgConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All cosmetics: the built-in catalog (assets/lmgclient/cosmetics) plus imported custom models. */
public final class CosmeticRegistry {
	/** Categories in the order the Cosmetics screen shows them. */
	public static final String[] CATEGORIES = {"capes", "wings", "hats", "glasses", "ears", "auras", "custom"};
	public static final Map<String, String> CATEGORY_NAMES = Map.of(
			"capes", "Capes", "wings", "Wings", "hats", "Hats", "glasses", "Glasses",
			"ears", "Ears", "auras", "Auras", "custom", "Custom");

	private static final Gson GSON = new Gson();
	private static final Map<String, CosmeticModel> MODELS = new LinkedHashMap<>();

	private CosmeticRegistry() {}

	public static void loadBuiltIn() {
		MODELS.values().removeIf(m -> !m.custom);
		JsonElement index = readJson("/assets/lmgclient/cosmetics/index.json");
		if (index == null) {
			LmgClient.LOGGER.warn("No cosmetic catalog found");
			return;
		}
		JsonArray items = index.getAsJsonObject().getAsJsonArray("items");
		for (JsonElement e : items) {
			String id = e.getAsJsonObject().get("id").getAsString();
			JsonElement json = readJson("/assets/lmgclient/cosmetics/" + id + ".json");
			if (json == null) continue;
			try {
				MODELS.put(id, GSON.fromJson(json, CosmeticModel.class).prepare());
			} catch (Exception ex) {
				LmgClient.LOGGER.warn("Bad cosmetic {}", id, ex);
			}
		}
		LmgClient.LOGGER.info("Loaded {} cosmetics", MODELS.size());
	}

	public static void putCustom(CosmeticModel model) {
		model.custom = true;
		model.category = "custom";
		MODELS.put(model.id, model.prepare());
	}

	public static void clearCustom() {
		MODELS.values().removeIf(m -> m.custom);
	}

	public static CosmeticModel get(String id) {
		return id == null ? null : MODELS.get(id);
	}

	public static List<CosmeticModel> inCategory(String category) {
		List<CosmeticModel> list = new ArrayList<>();
		for (CosmeticModel m : MODELS.values()) if (category.equals(m.category)) list.add(m);
		return list;
	}

	/** The colour to draw an item with, from the config (hex / "accent" / "rainbow") or its default. */
	public static int colorFor(CosmeticModel model, float ticks) {
		String c = LmgConfig.get().cosmeticColors.get(model.id);
		if (c == null || c.isBlank()) return model.defaultRgb();
		if (c.equals("accent")) return LmgConfig.get().accent.rgb;
		if (c.equals("rainbow")) return java.awt.Color.HSBtoRGB((ticks / 120f) % 1f, 0.55f, 1f) & 0xFFFFFF;
		try {
			return Integer.parseInt(c.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return model.defaultRgb();
		}
	}

	private static JsonElement readJson(String path) {
		try (InputStream in = CosmeticRegistry.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (Exception e) {
			LmgClient.LOGGER.warn("Could not read {}", path, e);
			return null;
		}
	}

	/** Moves the old one-field-per-cosmetic settings (before v0.17) onto the new catalog. */
	public static void migrate(LmgConfig c, JsonObject raw) {
		if (raw == null || raw.has("equipped")) return;
		Map<String, String> eq = c.equipped;
		String wings = str(raw, "wings"), halo = str(raw, "halo"), ears = str(raw, "catEars"), hat = str(raw, "chinaHat"), shield = str(raw, "shield");
		if (on(wings)) { eq.put("wings", "wings_angel"); colorFrom(c, "wings_angel", wings); }
		if (on(halo)) { eq.put("auras", "aura_halo"); colorFrom(c, "aura_halo", halo); }
		else if (on(shield)) { eq.put("auras", "aura_shield"); colorFrom(c, "aura_shield", shield); }
		if (on(ears)) { eq.put("ears", "ears_cat"); colorFrom(c, "ears_cat", ears); }
		if (on(hat)) { eq.put("hats", "hat_china"); colorFrom(c, "hat_china", hat); }
		else if (raw.has("crown") && raw.get("crown").getAsBoolean()) eq.put("hats", "hat_crown");
		if (!raw.has("cape") || raw.get("cape").getAsBoolean()) eq.put("capes", "cape_lmg");
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
	}

	private static boolean on(String v) {
		return v != null && !v.equals("OFF");
	}

	private static void colorFrom(LmgConfig c, String id, String old) {
		String v = switch (old) {
			case "WHITE" -> "#F6F6FF";
			case "BLACK" -> "#24222B";
			case "GOLD" -> "#FFD36A";
			case "PINK" -> "#FFB3CF";
			case "ACCENT" -> "accent";
			case "RAINBOW" -> "rainbow";
			default -> null;
		};
		if (v != null) c.cosmeticColors.put(id, v);
	}
}
