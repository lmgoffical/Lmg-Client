package dev.lmg.client.pet;

/** One pet as saved in lmgclient.json. */
public class PetConfig {
	public static final String[] MODES = {"FOLLOW", "LEFT_SHOULDER", "RIGHT_SHOULDER", "HEAD"};
	public static final String[] MODE_LABELS = {"Follow me", "Left shoulder", "Right shoulder", "On my head"};
	public static final float[] SIZES = {0.35f, 0.55f, 0.8f, 1f};
	public static final String[] SIZE_LABELS = {"Tiny", "Small", "Medium", "Full size"};

	public boolean enabled = true;
	public String type = "CAT";
	public String name = "";
	public String mode = "FOLLOW";
	public int size = 1;
	public boolean baby = false;
	public int variant = 0;
	public int collar = 14;            // DyeColor id, cats and dogs (red)
	// mini player
	public boolean mine = true;        // true = your own skin, false = the player named below
	public String player = "";
	public boolean cosmetics = true;   // mini you wears your cosmetics
	/** Only in what's shared: the owner has "Pets climb on me" on (so others see it too). */
	public boolean climb = false;

	public PetType type() {
		return PetType.of(type);
	}

	public int modeIndex() {
		for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return i;
		return 0;
	}

	public boolean attached() {
		return !"FOLLOW".equals(mode);
	}

	/** Something that changes the entity itself (so it has to be rebuilt). */
	String identity() {
		return type + "|" + baby + "|" + variant + "|" + collar + "|" + mine + "|" + player.toLowerCase();
	}

	PetConfig sanitized() {
		if (type == null) type = "CAT";
		if (name == null) name = "";
		if (mode == null || !java.util.Arrays.asList(MODES).contains(mode)) mode = "FOLLOW";   // e.g. the old "Play around me"
		if (player == null) player = "";
		if (size < 0 || size >= SIZES.length) size = 1;
		if (collar < 0 || collar > 15) collar = 14;
		if (name.length() > 24) name = name.substring(0, 24);
		return this;
	}

	public static void sanitize(java.util.List<PetConfig> list) {
		list.removeIf(java.util.Objects::isNull);
		while (list.size() > Pets.MAX) list.remove(list.size() - 1);
		for (PetConfig p : list) p.sanitized();
	}
}
