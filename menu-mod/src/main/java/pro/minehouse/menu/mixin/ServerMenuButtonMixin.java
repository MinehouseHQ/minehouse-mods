package pro.minehouse.menu.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pro.minehouse.menu.ui.UiNet;

/**
 * «Меню сервера» in the pause screen is a vanilla dialog the server put in the registry at login
 * (MineHouse Guides). When the server draws windows through this mod, ask it for the menu instead
 * ({@code /menu}), so it opens in the MineHouse style with what is up to date now.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ServerMenuButtonMixin {
	private static final ResourceKey<Dialog> SERVER_MENU = ResourceKey.create(Registries.DIALOG,
			Identifier.fromNamespaceAndPath("minehouse-guides", "server_menu"));

	@Inject(method = "showDialog(Lnet/minecraft/core/Holder;Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), cancellable = true)
	private void minehouse$serverMenu(Holder<Dialog> dialog, Screen previous, CallbackInfo ci) {
		if (!((Object) this instanceof ClientPacketListener self)) return;
		if (dialog.is(SERVER_MENU) && UiNet.available() && self.getCommands().getRoot().getChild("menu") != null) {
			self.sendCommand("menu");
			ci.cancel();
		}
	}
}
