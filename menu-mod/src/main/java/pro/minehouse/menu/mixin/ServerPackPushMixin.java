package pro.minehouse.menu.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.minehouse.menu.ServerPack;

/** Answers the MineHouse pack push from the pack preloaded at game start, see {@link ServerPack}. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ServerPackPushMixin {
	@Shadow
	@Final
	protected Minecraft minecraft;

	@Shadow
	@Final
	protected Connection connection;

	@Inject(
		method = "handleResourcePackPush",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void minehouse$preloadedPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		try {
			if (ServerPack.onPush(this.minecraft, this.connection, packet.id(), packet.url(), packet.hash())) {
				ci.cancel();
			}
		} catch (Throwable t) {
			// Never lose the pack because of us: vanilla takes it from here.
		}
	}
}
