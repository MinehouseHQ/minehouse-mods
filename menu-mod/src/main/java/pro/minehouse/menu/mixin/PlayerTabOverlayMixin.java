package pro.minehouse.menu.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pro.minehouse.menu.tab.TabOverlay;

/** On MineHouse the tab list is ours ({@link TabOverlay}); elsewhere vanilla's. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void minehouse$draw(GuiGraphicsExtractor g, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
		if (TabOverlay.draw(g, width)) ci.cancel();
	}

	@Inject(method = "setVisible", at = @At("HEAD"))
	private void minehouse$visible(boolean visible, CallbackInfo ci) {
		TabOverlay.setVisible(visible);
	}
}
