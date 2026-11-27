package pro.minehouse.menu.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import pro.minehouse.menu.poker.PokerCamera;

/** With the camera over a poker table the hotbar, hearts and crosshair would lie over the cards: the table screen's UI replaces them. */
@Mixin(Hud.class)
public abstract class PokerHudMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void minehouse$pokerTable(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (PokerCamera.hidesHud()) {
			ci.cancel();
		}
	}
}
