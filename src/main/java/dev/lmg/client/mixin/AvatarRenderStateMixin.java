package dev.lmg.client.mixin;

import dev.lmg.client.emote.Emote;
import dev.lmg.client.emote.EmoteHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Lets a player's render data carry the emote they're doing. */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements EmoteHolder {
	@Unique
	private Emote lmg$emote;
	@Unique
	private float lmg$emoteTime;

	@Unique
	private float lmg$tug;

	@Override
	public float lmg$tug() {
		return lmg$tug;
	}

	@Override
	public void lmg$setTug(float amount) {
		lmg$tug = amount;
	}

	@Override
	public Emote lmg$emote() {
		return lmg$emote;
	}

	@Override
	public float lmg$emoteTime() {
		return lmg$emoteTime;
	}

	@Override
	public void lmg$setEmote(Emote emote, float seconds) {
		lmg$emote = emote;
		lmg$emoteTime = seconds;
	}
}
