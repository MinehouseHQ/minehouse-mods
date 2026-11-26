package pro.minehouse.menu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import pro.minehouse.menu.wardrobe.WardrobeSelf;

/**
 * Turns you in the fitting room: the whole drawn model, around the feet, by the dragged angle.
 * Outside everything else the renderer does, so it works whoever poses the body (vanilla bodyRot or
 * an animation mod like Punchy! Moves). Pushed at the start, popped at every return.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererSubmitMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
	private void minehouse$previewTurn(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		poseStack.pushPose();
		if (state instanceof AvatarRenderState avatar) {
			float yaw = WardrobeSelf.previewYaw(avatar);
			if (yaw != 0) {
				// setupRotations turns by 180 - bodyRot, so a larger bodyRot is a turn the other way.
				poseStack.rotateDegrees(Axis.YP, -yaw);
			}
		}
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
	private void minehouse$previewTurnEnd(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		poseStack.popPose();
	}
}
