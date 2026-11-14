package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import pro.minehouse.riftclient.RiftTear;

/** The flat picture of a rift is not drawn while the shader draws the rift itself. */
@Mixin(EntityRenderer.class)
public abstract class RiftPictureMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void minehouse$riftPicture(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		if (RiftTear.hidden(entity)) {
			cir.setReturnValue(false);
		}
	}
}
