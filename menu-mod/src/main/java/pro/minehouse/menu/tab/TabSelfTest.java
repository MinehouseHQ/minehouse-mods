package pro.minehouse.menu.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

import pro.minehouse.menu.MinehouseMenu;

/**
 * CI check (xvfb-run ./gradlew runSelftestClient -Pselftest=tab): draws the tab list for made-up
 * players over the title screen (a few, then a full server in columns), screenshots tab.png and
 * tab-full.png, and prints MINEHOUSE_TAB_OK.
 */
final class TabSelfTest {
	private static int ticks;

	private TabSelfTest() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticks == 0 && (client.gui.overlay() != null || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen))) return;
			ticks++;
			try {
				if (ticks == 20) {
					client.gui.setScreen(new Preview(8));
				} else if (ticks == 50) {
					shot(client, "tab.png");
				} else if (ticks == 60) {
					client.gui.setScreen(new Preview(46));
				} else if (ticks == 90) {
					shot(client, "tab-full.png");
				} else if (ticks == 100) {
					MinehouseMenu.LOG.info("MINEHOUSE_TAB_OK");
					client.stop();
				}
			} catch (RuntimeException e) {
				MinehouseMenu.LOG.error("MINEHOUSE_TAB_FAIL", e);
				client.stop();
			}
		});
	}

	private static void shot(net.minecraft.client.Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1,
				msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
	}

	private static final String[] NAMES = {"P5ina", "Morok_", "zenitsa", "HouseClaw", "Sift", "kotik2007", "Svetets", "nyashka",
			"Dimon4ik", "TwitchGuy", "Ponchik", "oslik", "AlexCraft", "steve_irl", "Kirill", "MashaMine", "Arthas", "creeperboom",
			"Vasya_Pro", "lunatic", "Mimik", "Shorokh", "Iskrik", "bebra", "Notch2", "zxc_ghoul", "Tolik", "Gleb", "Nastya",
			"Lera", "Egor", "sanya", "Krot", "Pchela", "Burger", "kekw", "Fox", "Wolfie", "Raven", "Okunь", "Zeka", "dendy",
			"Pixel", "Bloky", "Grib", "Ezhik"};

	/** Made-up list: roles, Nitro colours, a stream, a spectator, pings from good to bad. */
	static String json(int n) {
		StringBuilder players = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if (i > 0) players.append(',');
			players.append('"').append(id(i)).append("\":{");
			List<String> f = new ArrayList<>();
			if (i == 0) f.add("\"r\":\"admin\"");
			if (i == 1) f.add("\"r\":\"moderator\"");
			if (i == 2) f.add("\"r\":\"helper\"");
			if (i == 4) f.add("\"r\":\"bot\"");
			if (i == 0 || i == 5 || i == 9 || i == 13) f.add("\"c\":\"" + new String[] {"#a855f7", "#f472b6", "#34d399", "#fbbf24"}[(i / 4) % 4] + "\"");
			if (i == 9 || i == 1) f.add("\"l\":true");
			players.append(String.join(",", f)).append('}');
		}
		return "{\"v\":1,\"online\":" + n + ",\"max\":100,\"tps\":19.8,\"players\":{" + players + "}}";
	}

	static UUID id(int i) {
		return new UUID(0x4d48L, i + 1);
	}

	static List<TabOverlay.Line> lines(int n) {
		int[] pings = {12, 34, 56, 0, 18, 95, 140, 210, 41, 320, 64, 700, 22, -1};
		List<TabOverlay.Line> lines = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			UUID id = id(i);
			lines.add(new TabOverlay.Line(id, NAMES[i % NAMES.length], DefaultPlayerSkin.get(id), pings[i % pings.length], i == 7));
		}
		return lines;
	}

	private static final class Preview extends Screen {
		private final TabData data;
		private final List<TabOverlay.Line> lines;
		private final long opened = net.minecraft.util.Util.getMillis();

		Preview(int n) {
			super(Component.literal("tab"));
			data = TabData.parse(json(n));
			lines = lines(n);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			super.extractRenderState(g, mouseX, mouseY, a);
			TabOverlay.draw(g, font, width, data, lines, 42, net.minecraft.util.Util.getMillis() - opened);
		}
	}
}
