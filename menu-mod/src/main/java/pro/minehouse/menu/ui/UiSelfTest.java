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
 * an update and a close, then the server and player menus (selftest/ui-menu.json, ui-player.json,
 * screenshots ui-menu.png, ui-player.png), a text field and a slider (ui-input.png), the player wheel (ui-wheel.png), and prints MINEHOUSE_UI_OK if the windows held together.
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
			// (or in a world, for a local look at the explainers with blocks: -Dminehouse.ui.world=true, see demoClient).
			boolean inWorld = client.player != null && client.gui.screen() == null && Boolean.getBoolean("minehouse.ui.world");
			if (ticks == 0 && !inWorld && (client.gui.overlay() != null || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen))) return;
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
					// «Меню сервера» and «Меню игрока» as the server sends them (MineHouse Guides MenuUi).
					UiNet.receive(client, resource("ui-menu.json"));
					check(client.gui.screen() instanceof UiScreen, "the server menu opens");
				} else if (ticks == 100) {
					Screenshot.grab(client.gameDirectory, "ui-menu.png", client.gameRenderer.mainRenderTarget(), 1,
							msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
				} else if (ticks == 110) {
					UiNet.receive(client, resource("ui-player.json"));
					check(client.gui.screen() instanceof UiScreen s && s.id().equals("selftest_player"), "a window replaces another");
				} else if (ticks == 140) {
					Screenshot.grab(client.gameDirectory, "ui-player.png", client.gameRenderer.mainRenderTarget(), 1,
							msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
				} else if (ticks == 141) {
					// A text field and a slider (client 1.49.0): the field with "focus" takes the keyboard.
					UiNet.receive(client, resource("ui-input.json"));
					if (client.gui.screen() instanceof UiScreen s) {
						s.charTyped(new net.minecraft.client.input.CharacterEvent('5'));
						s.charTyped(new net.minecraft.client.input.CharacterEvent('x'));
						check("125".equals(s.fieldText("amount")), "typing goes into the focused field, digits only");
						check("1".equals(s.sliderText("scale")), "the slider stands where the server put it");
					} else {
						check(false, "the window with a field opens");
					}
				} else if (ticks == 148) {
					Screenshot.grab(client.gameDirectory, "ui-input.png", client.gameRenderer.mainRenderTarget(), 1,
							msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
				} else if (ticks == 150) {
					// The player wheel (MineHouse Guides WheelUi), with the mouse on the right-hand tile.
					UiNet.receive(client, resource("ui-wheel.json"));
					check(client.gui.screen() instanceof UiScreen s && s.id().equals("player_wheel"), "the wheel opens");
					if (client.gui.screen() instanceof UiScreen s) s.testMouse = new int[] {s.width / 2 + 60, s.height / 2 - 18};
				} else if (ticks == 180) {
					Screenshot.grab(client.gameDirectory, "ui-wheel.png", client.gameRenderer.mainRenderTarget(), 1,
							msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
				} else if (ticks == 190) {
					// The dungeon events' explainers (MineHouse End RoomIntro), one screenshot each, or several
					// moments of each with -Dminehouse.ui.explainerAt=0.2,0.5,0.8 (shares of the scene's length).
					UiNet.receive(client, resource("ui-explainer.json").replace("SCENE", SCENES[0]));
					check(client.gui.screen() instanceof UiScreen, "the explainer opens");
					for (String scene : SCENES) check(pro.minehouse.menu.explainer.Explainer.known(scene), "the explainer knows " + scene);
				} else if (ticks > 190 && ticks < explainersEnd()) {
					int k = ticks - 190;
					int shot = k / SHOT;
					int scene = shot / AT.length;
					if (k % SHOT == 0 && shot % AT.length == 0) {
						UiNet.receive(client, resource("ui-explainer.json").replace("SCENE", SCENES[scene]).replace("\"rev\":1", "\"rev\":" + (scene + 1)));
					}
					if (k % SHOT == 1 && AT[0] >= 0) {
						pro.minehouse.menu.explainer.Explainer.pinned = AT[shot % AT.length];
					}
					if (k % SHOT == SHOT - 15) {
						String name = "ui-explainer-" + SCENES[scene] + (AT[0] >= 0 ? "-" + Math.round(AT[shot % AT.length] * 100) : "") + ".png";
						Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1,
								msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
						MinehouseMenu.LOG.info("Explainer {}: {} fps", name, client.getFps());
					}
				} else if (ticks == explainersEnd() + 60) {
					MinehouseMenu.LOG.info(problem == null ? "MINEHOUSE_UI_OK" : "MINEHOUSE_UI_FAIL: " + problem);
					client.stop();
				}
			} catch (RuntimeException e) {
				MinehouseMenu.LOG.error("MINEHOUSE_UI_FAIL", e);
				client.stop();
			}
		});
	}

	private static final String[] SCENES = {"reflection", "gravity", "darkness", "lava", "merchant", "altar", "timer",
			"ice", "meteors", "giants", "ghosts", "glass_cannon", "totems", "volatile", "storm", "frenzy", "wind", "poison", "hill", "champion"};
	/** Ticks per screenshot. */
	private static final int SHOT = 60;
	private static final float[] AT = at();

	private static float[] at() {
		String list = System.getProperty("minehouse.ui.explainerAt", "");
		if (list.isBlank()) return new float[] {-1};
		String[] parts = list.split(",");
		float[] out = new float[parts.length];
		for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i].trim());
		return out;
	}

	private static int explainersEnd() {
		return 190 + SCENES.length * AT.length * SHOT;
	}

	private static void check(boolean ok, String what) {
		if (!ok && problem == null) problem = what;
	}

	private static String resource(String name) {
		try (InputStream in = UiSelfTest.class.getResourceAsStream("/selftest/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException | NullPointerException e) {
			throw new IllegalStateException("no selftest/" + name, e);
		}
	}

	private static String window(int rev, boolean open) {
		String doc = resource("ui-window.json");
		return doc.replace("\"rev\":0", "\"rev\":" + rev).replace("\"open\":true", "\"open\":" + open);
	}
}
