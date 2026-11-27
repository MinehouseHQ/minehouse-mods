package pro.minehouse.menu.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.net.URLConnection;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pro.minehouse.menu.ServerPack;

/**
 * Timeouts for vanilla's file downloads (server resource packs among them).
 *
 * <p>Vanilla opens the connection with no connect or read timeout, so a download whose body stops
 * coming (Polymer rewrites the pack in place right after a server restart) hung the "Download"
 * thread forever and the player sat on the loading screen. With a read timeout the download
 * fails, the client reports FAILED_DOWNLOAD and the next join gets the new pack.
 */
@Mixin(HttpUtil.class)
public abstract class HttpUtilMixin {
	@ModifyExpressionValue(
		method = "downloadFile",
		at = @At(value = "INVOKE", target = "Ljava/net/URL;openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;")
	)
	private static URLConnection minehouse$timeouts(URLConnection connection) {
		connection.setConnectTimeout(ServerPack.CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(ServerPack.READ_TIMEOUT_MS);
		return connection;
	}
}
