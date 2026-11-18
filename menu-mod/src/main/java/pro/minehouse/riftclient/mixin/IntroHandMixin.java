package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;
import pro.minehouse.riftclient.Intro;

/** No hand or held item in front of the Chapter 2 opening. */
@Mixin(GameRenderer.class)
public abstract class IntroHandMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void minehouse$hideHand(CallbackInfo ci) {
		if (Intro.playing()) {
			ci.cancel();
		}
	}
}
