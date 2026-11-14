package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import pro.minehouse.riftclient.RiftRestart;

/** In the limbo the mod shows its own screen: the limbo's titles and its line above the hotbar would lie over it. */
@Mixin(Hud.class)
public abstract class LimboHudMixin {
	@Inject(method = {"extractTitle", "extractOverlayMessage"}, at = @At("HEAD"), cancellable = true)
	private void minehouse$limboScreen(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (RiftRestart.inLimbo()) {
			ci.cancel();
		}
	}
}
