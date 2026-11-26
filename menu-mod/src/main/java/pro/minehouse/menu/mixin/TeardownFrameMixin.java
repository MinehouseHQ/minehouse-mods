package pro.minehouse.menu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import pro.minehouse.menu.MinehouseMenu;

/**
 * When the server switches the client back to configuration (a restart or a move to another
 * server behind the proxy), the game drops the game mode, draws one frame of the new screen and
 * only then drops the level. Some mods (Spatial GUI) draw the world in that frame; the camera is
 * already reset, so the frame throws, the level is never dropped, and the next tick crashes on
 * the missing game mode. The frame is only for show: if it fails, the teardown goes on.
 */
@Mixin(Minecraft.class)
public abstract class TeardownFrameMixin {
	@WrapOperation(
		method = "clearClientLevel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V")
	)
	private void minehouse$teardownFrame(Minecraft minecraft, Screen screen, Operation<Void> original) {
		try {
			original.call(minecraft, screen);
		} catch (RuntimeException e) {
			MinehouseMenu.LOG.warn("A frame drawn while leaving the level failed, leaving anyway", e);
		}
	}
}
