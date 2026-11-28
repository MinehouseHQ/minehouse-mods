package pro.minehouse.menu.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.minehouse.menu.wardrobe.FirstPersonElementals;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsAndItemsRendererMixin {
	@Shadow @Final private Minecraft minecraft;

	// The native hand path is shared with maps, so check the rendered stacks as well as the live inventory.
	@Inject(method = "renderPlayerHand", at = @At("TAIL"))
	private void minehouse$elementalSkinArm(PoseStack pose, SubmitNodeCollector nodes, int light,
			HumanoidArm arm, PlayerRenderState player, CallbackInfo ci) {
		FirstPersonElementals.skinArm(minecraft, player, arm, pose, nodes, light);
	}
}
