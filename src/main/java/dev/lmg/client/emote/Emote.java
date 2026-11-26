package dev.lmg.client.emote;

/** The emotes on the wheel. {@code seconds <= 0} means it plays until you move. */
public enum Emote {
	WAVE("wave", "Wave", 2.4f),
	HUG("hug", "Hug", 8f),
	CHEER("cheer", "Cheer", 2.6f),
	DANCE("dance", "Dance", 0f),
	CLAP("clap", "Clap", 2.8f),
	SIT("sit", "Sit", 0f),
	POINT("point", "Point", 2.4f),
	BOW("bow", "Bow", 2.2f);

	public final String id, label;
	public final float seconds;

	Emote(String id, String label, float seconds) {
		this.id = id;
		this.label = label;
		this.seconds = seconds;
	}

	public boolean loops() {
		return seconds <= 0;
	}

	/** Emotes that keep going until you move (and that someone walking up later should still see). */
	public boolean lasting() {
		return loops() || this == HUG;
	}

	public static Emote byId(String id) {
		for (Emote e : values()) if (e.id.equals(id)) return e;
		return null;
	}
}
