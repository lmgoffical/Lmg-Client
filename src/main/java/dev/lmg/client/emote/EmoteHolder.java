package dev.lmg.client.emote;

/** Added to the player's render data: which emote to show, and how far into it. */
public interface EmoteHolder {
	Emote lmg$emote();

	float lmg$emoteTime();

	void lmg$setEmote(Emote emote, float seconds);
}
