package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import pro.minehouse.riftclient.TopDown;

/**
 * Small things (an item on the floor above all) are drawn only close to the camera, and the camera
 * of the Rift hangs farther away than that: there everything near enough to be on the screen is drawn.
 */
@Mixin(Entity.class)
public abstract class RiftItemSeenMixin {
	@Inject(method = "shouldRenderAtSqrDistance(D)Z", at = @At("HEAD"), cancellable = true)
	private void minehouse$riftSeen(double distance, CallbackInfoReturnable<Boolean> cir) {
		if (TopDown.active() && distance < TopDown.SEEN * TopDown.SEEN) {
			cir.setReturnValue(true);
		}
	}
}
