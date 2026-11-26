package pro.minehouse.menu.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.minehouse.menu.skin.SkinPartState;
import pro.minehouse.menu.skin.SkinParts;

/** Player body parts built of item displays, drawn as the player model (see SkinParts). */
@Mixin(DisplayRenderer.ItemDisplayRenderer.class)
public abstract class SkinPartDisplayMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$ItemDisplay;Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V", at = @At("TAIL"))
	private void minehouse$skinPart(Display.ItemDisplay display, ItemDisplayEntityRenderState state, float partialTick, CallbackInfo ci) {
		var item = display.itemRenderState();
		ItemStack stack = item == null ? ItemStack.EMPTY : item.itemStack();
		int part = SkinParts.partOf(stack);
		ResolvableProfile profile = part < 0 ? null : stack.get(DataComponents.PROFILE);
		((SkinPartState) state).minehouse$set(profile == null ? -1 : part, profile);
	}

	@Inject(method = "submitInner(Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V", at = @At("HEAD"), cancellable = true)
	private void minehouse$drawSkinPart(ItemDisplayEntityRenderState state, PoseStack pose, SubmitNodeCollector nodes, int light, float partialTick, CallbackInfo ci) {
		SkinPartState skin = (SkinPartState) state;
		if (skin.minehouse$part() < 0 || state.renderState == null || state.item.isEmpty()) {
			return;
		}
		SkinParts.submit(skin.minehouse$part(), skin.minehouse$profile(), state.renderState.transformation().get(partialTick),
				pose, nodes, light, state.outlineColor);
		if (!SkinParts.debug()) {
			ci.cancel();
		}
	}
}
