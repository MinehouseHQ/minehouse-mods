package pro.minehouse.riftclient.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import pro.minehouse.riftclient.RiftFx;
import pro.minehouse.riftclient.RiftTear;

/** The Rift mode asks the game for its screen effect the same way a player's own effects are asked for. */
@Mixin(GameRenderer.class)
public abstract class RiftFxMixin {
	@Shadow
	@Final
	private List<Identifier> requestedPostEffects;

	@Shadow
	@Final
	private List<PostChain> appliedPostEffects;

	@Shadow
	@Final
	private Camera mainCamera;

	@Shadow
	@Final
	private GameRenderState gameRenderState;

	@Shadow
	private void bobHurt(CameraRenderState cameraState, PoseStack poseStack) {
	}

	@Shadow
	private void bobView(CameraRenderState cameraState, PoseStack poseStack) {
	}

	@Inject(method = "applyPostEffects", at = @At("HEAD"))
	private void minehouse$riftTears(CallbackInfo ci) {
		// The same view the world was drawn with: the sway of walking and of being hurt is part of it.
		CameraRenderState state = gameRenderState.levelRenderState.cameraRenderState;
		PoseStack sway = new PoseStack();
		bobHurt(state, sway);
		if (gameRenderState.optionsRenderState.bobView) {
			bobView(state, sway);
		}
		Matrix4f view = new Matrix4f(state.projectionMatrix).mul(sway.last().pose()).mul(state.viewRotationMatrix);
		RiftTear.upload(view, mainCamera.position(), appliedPostEffects);
	}

	@Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void minehouse$riftFx(DeltaTracker deltaTracker, CallbackInfo ci) {
		Identifier fx = RiftFx.current();
		if (fx != null) {
			requestedPostEffects.add(fx);
		}
	}
}
