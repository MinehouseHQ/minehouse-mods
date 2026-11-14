package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import pro.minehouse.riftclient.RiftNotice;

/** In the Rift mode the vanilla boss bars are not drawn: the mode shows them as its own notices. */
@Mixin(BossHealthOverlay.class)
public abstract class RiftBossBarMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void minehouse$riftNotices(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (RiftNotice.on()) {
			ci.cancel();
		}
	}
}
