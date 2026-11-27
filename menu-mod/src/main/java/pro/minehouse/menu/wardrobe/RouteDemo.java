package pro.minehouse.menu.wardrobe;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;

/**
 * Development only (-Dminehouse.route.demo=true, as an op on a local server with MineHouse
 * Cosmetics): sets a fitting room 30 blocks away on the surface, walks back, presses «O», saves
 * screenshots of the route from the eyes and from behind, then quits.
 */
final class RouteDemo {
	private static int ticks;
	private static int x, y, z;

	private RouteDemo() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) return;
			ticks++;
			var c = client.player.connection;
			switch (ticks) {
				case 40 -> {
					x = client.player.getBlockX();
					y = client.player.getBlockY();
					z = client.player.getBlockZ();
					c.sendCommand("time set day");
					c.sendCommand("execute positioned " + (x + 22) + " " + y + " " + (z + 18) + " positioned over motion_blocking_no_leaves run tp @s ~ ~ ~");
				}
				case 70 -> c.sendCommand("cosmetics room add demo");
				case 80 -> c.sendCommand("tp @s " + x + " " + y + " " + z + " -50 35");
				case 120 -> WardrobeNet.send("open", null, null);
				case 220 -> Screenshot.grab(client, false);
				case 225 -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				case 240 -> client.player.setXRot(55);
				case 280 -> client.player.setYRot(client.player.getYRot() + 25);
				case 300 -> client.player.setXRot(10);
				case 270, 330 -> Screenshot.grab(client, false);
				case 340 -> client.stop();
				default -> {
				}
			}
		});
	}
}
