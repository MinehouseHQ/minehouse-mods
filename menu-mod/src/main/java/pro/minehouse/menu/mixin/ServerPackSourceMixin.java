package pro.minehouse.menu.mixin;

import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.minehouse.menu.ServerPack;

/** Puts the preloaded MineHouse server pack next to the packs vanilla downloads from servers. */
@Mixin(DownloadedPackSource.class)
public abstract class ServerPackSourceMixin {
	@Inject(method = "createRepositorySource", at = @At("RETURN"), cancellable = true)
	private void minehouse$addPreloaded(CallbackInfoReturnable<RepositorySource> cir) {
		cir.setReturnValue(ServerPack.source(cir.getReturnValue()));
	}

	/** A reload failed (broken pack): the recovery reload goes without ours. */
	@Inject(method = "onRecovery", at = @At("HEAD"))
	private void minehouse$dropOnRecovery(CallbackInfo ci) {
		ServerPack.onRecovery();
	}
}
