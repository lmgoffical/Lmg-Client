package dev.lmg.client.mixin;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The box behind name tags: the game's, none, a darker one, or one in your colour. */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagMixin {
	@ModifyArg(method = "submitNameTag",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;"),
			index = 6)
	private int lmg$tagBox(int background) {
		if (background == 0) return 0;   // the bright see-through copy has no box anyway
		return switch (LmgConfig.get().nameTagBg) {
			case NONE -> 0;
			case DARK -> 0xB0000000;
			case ACCENT -> Draw.withAlpha(LmgConfig.get().accentRgb(), 0.45f);
			default -> background;
		};
	}
}
