package pro.minehouse.menu.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.minehouse.menu.wardrobe.PetPat;
import pro.minehouse.menu.wardrobe.WardrobeSelf;

/**
 * Belts that are legs themselves (spider legs) take the wearer's legs away: not out of the skin,
 * they are just not drawn while it is on. Set every time, since the model is shared by all players.
 */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void minehouse$hideLegs(AvatarRenderState state, CallbackInfo ci) {
		boolean show = !WardrobeSelf.hidesLegs(state);
		PlayerModel model = (PlayerModel) (Object) this;
		model.leftLeg.visible = show;
		model.rightLeg.visible = show;
	}

	/** A player stroking a pet: the hand on its head (see PetPat). */
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void minehouse$petPat(AvatarRenderState state, CallbackInfo ci) {
		PetPat.pose(state, (PlayerModel) (Object) this);
	}
}
