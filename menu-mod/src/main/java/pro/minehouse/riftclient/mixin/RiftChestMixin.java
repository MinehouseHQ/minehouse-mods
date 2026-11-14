package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import pro.minehouse.riftclient.RiftBagScreen;
import pro.minehouse.riftclient.TopDown;

/** In a run of the Rift a chest opens in the mode's own window, not in the usual one. */
@Mixin(MenuScreens.class)
public abstract class RiftChestMixin {
	@Inject(method = "create", at = @At("HEAD"), cancellable = true)
	private static <T extends AbstractContainerMenu> void minehouse$riftChest(MenuType<T> type, Minecraft minecraft, int containerId, Component title,
			CallbackInfo ci) {
		if (!TopDown.active() || minecraft.player == null) {
			return;
		}
		if (type.create(containerId, minecraft.player.getInventory()) instanceof ChestMenu menu) {
			minecraft.player.containerMenu = menu;
			minecraft.gui.setScreen(RiftBagScreen.chest(menu, minecraft.player.getInventory()));
			ci.cancel();
		}
	}
}
