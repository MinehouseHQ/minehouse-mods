package pro.minehouse.menu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pro.minehouse.menu.wardrobe.PetPat;
import pro.minehouse.menu.wardrobe.WardrobeCamera;

/** While the wardrobe is open the camera stands in front of the player in the world; at poker, over the table; stroking a pet, over the shoulder. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;
	@Shadow
	private Level level;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
	private void minehouse$wardrobeCamera(float partialTicks, CallbackInfo ci) {
		WardrobeCamera.Shot shot = WardrobeCamera.shot(entity, level, partialTicks);
		if (shot != null) {
			detached = true;
			setRotation(shot.yRot(), shot.xRot());
			setPosition(shot.position());
			return;
		}
		pro.minehouse.menu.poker.PokerCamera.Shot poker = pro.minehouse.menu.poker.PokerCamera.shot(entity, level, partialTicks);
		if (poker != null) {
			// Not detached while right over your seat: your own head is then not drawn in the way.
			detached = pro.minehouse.menu.poker.PokerCamera.showsSelf();
			setRotation(poker.yRot(), poker.xRot());
			setPosition(poker.position());
			return;
		}
		PetPat.Shot pat = PetPat.shot(entity, level, partialTicks);
		if (pat != null) {
			detached = true;
			setRotation(pat.yRot(), pat.xRot());
			setPosition(pat.position());
		}
	}
}
