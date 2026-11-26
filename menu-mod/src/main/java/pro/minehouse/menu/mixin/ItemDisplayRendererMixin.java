package pro.minehouse.menu.mixin;

import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.minehouse.menu.wardrobe.FrameBlend;

/** A pet's frames from the server, drawn blended like the rest of the cosmetics (see FrameBlend.pet). */
@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$ItemDisplayRenderer")
public abstract class ItemDisplayRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$ItemDisplay;Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V",
			at = @At("TAIL"))
	private void minehouse$blendPet(Display.ItemDisplay display, ItemDisplayEntityRenderState state, float partialTick, CallbackInfo ci) {
		FrameBlend.pet(display, state.item);
	}
}
