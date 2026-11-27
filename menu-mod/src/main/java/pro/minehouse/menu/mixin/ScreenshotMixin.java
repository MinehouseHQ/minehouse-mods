package pro.minehouse.menu.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pro.minehouse.menu.feed.FeedShots;

/** F2 (the screenshot key): offer the new picture to the site feed, see {@link FeedShots}. */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	@Inject(method = "grab(Lnet/minecraft/client/Minecraft;Z)V", at = @At("TAIL"))
	private static void minehouse$feed(Minecraft minecraft, boolean panorama, CallbackInfo ci) {
		FeedShots.taken(minecraft.gameDirectory);
	}
}
