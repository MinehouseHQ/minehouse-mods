package pro.minehouse.menu;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Checks for a pack update before Minecraft loads, see {@link PackUpdater#preLaunch}, and starts
 * fetching the server resource pack ({@link ServerPack#startPrefetch}).
 */
public final class PreLaunchUpdate implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		try {
			MenuConfig.load();
			// Runs in the background while the update check and the game load: see ServerPack.
			ServerPack.startPrefetch();
			PackUpdater.preLaunch();
		} catch (Throwable t) {
			MinehouseMenu.LOG.warn("Pre-launch update check failed", t);
		}
	}
}
