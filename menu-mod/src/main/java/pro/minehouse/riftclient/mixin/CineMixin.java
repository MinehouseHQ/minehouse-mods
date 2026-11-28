package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;
import pro.minehouse.riftclient.cine.CinePlayer;

/** A client-side film is drawn over the finished frame, interface and all. */
@Mixin(GameRenderer.class)
public abstract class CineMixin {
	@Inject(method = "render()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER))
	private void minehouse$film(CallbackInfo ci) {
		CinePlayer.renderFrame();
	}
}
