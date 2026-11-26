package pro.minehouse.menu.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;

import pro.minehouse.menu.MinehouseMenu;

/**
 * CI check (xvfb-run ./gradlew runSelftestClient -Pselftest=ui): feeds the client a made-up server
 * window (selftest/ui-window.json, the wardrobe's layout with vanilla models), screenshots it, sends
 * an update and a close, and prints MINEHOUSE_UI_OK if the window held together.
 */
final class UiSelfTest {
	private static int ticks;
	private static String problem;
	private static UiScreen opened;

	private UiSelfTest() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Wait for the title screen: item components are bound only after the resources load.
			if (ticks == 0 && (client.gui.overlay() != null || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen))) return;
			ticks++;
			try {
				if (ticks == 20) {
					UiNet.receive(client, window(1, true));
					check(client.gui.screen() instanceof UiScreen, "the window opens");
					opened = client.gui.screen() instanceof UiScreen s ? s : null;
				} else if (ticks == 50) {
					Screenshot.grab(client.gameDirectory, "ui-window.png", client.gameRenderer.mainRenderTarget(), 1,
							msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
				} else if (ticks == 60) {
					UiNet.receive(client, window(2, false));
					check(client.gui.screen() == opened, "an update keeps the same screen");
					UiNet.receive(client, window(3, false).replace("\"id\":\"selftest\"", "\"id\":\"other\""));
					check(client.gui.screen() == opened, "an update of another window that is not open is ignored");
				} else if (ticks == 70) {
					opened.closeByServer();
					check(!(client.gui.screen() instanceof UiScreen), "the server closes it");
					MinehouseMenu.LOG.info(problem == null ? "MINEHOUSE_UI_OK" : "MINEHOUSE_UI_FAIL: " + problem);
					client.stop();
				}
			} catch (RuntimeException e) {
				MinehouseMenu.LOG.error("MINEHOUSE_UI_FAIL", e);
				client.stop();
			}
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok && problem == null) problem = what;
	}

	private static String window(int rev, boolean open) {
		String doc;
		try (InputStream in = UiSelfTest.class.getResourceAsStream("/selftest/ui-window.json")) {
			doc = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException | NullPointerException e) {
			throw new IllegalStateException("no selftest/ui-window.json", e);
		}
		return doc.replace("\"rev\":0", "\"rev\":" + rev).replace("\"open\":true", "\"open\":" + open);
	}
}
