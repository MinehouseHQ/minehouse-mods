package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import pro.minehouse.riftclient.PortalWindows;

/**
 * Walking through a see-through rift window moves you into another dimension, and the game shows
 * its loading screen for that. Not after a window: the room is already drawn where you arrive
 * (PortalWindows), so the screen is skipped and you just walk on.
 */
@Mixin(ClientPacketListener.class)
public abstract class WindowLoadingScreenMixin {
	@Redirect(method = "startWaitingForNewLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void minehouse$noLoadingScreenThroughWindow(Minecraft mc, Screen screen) {
		if (!PortalWindows.walkedThrough(mc.level)) {
			mc.setScreenAndShow(screen);
		}
	}
}
