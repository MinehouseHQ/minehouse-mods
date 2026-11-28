package pro.minehouse.riftclient.cine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import pro.minehouse.riftclient.MinehouseRiftClient;

/**
 * Frames of a film without a server (xvfb-run ./gradlew runSelftestClient -Pselftest=film): on the title screen it
 * holds the film at each moment of -Dminehouse.filmTimes (seconds, comma separated) and saves film-NNN.png
 * (NNN = tenths of a second) into screenshots/. Prints MINEHOUSE_FILM_OK when every frame was drawn.
 */
public final class FilmSelfTest {
	private static int ticks;
	private static int index;
	private static final List<Float> TIMES = new ArrayList<>();

	private FilmSelfTest() {
	}

	public static void register(Supplier<Film> film) {
		for (String s : System.getProperty("minehouse.filmTimes", "0.5,2,5").split(",")) {
			if (!s.isBlank()) {
				TIMES.add(Float.parseFloat(s.trim()));
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticks == 0 && (client.gui.overlay() != null || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen))) {
				return;
			}
			ticks++;
			if (ticks == 5) {
				CinePlayer.play(film, 0);
			}
			if (ticks < 40) {
				return;
			}
			int step = (ticks - 40) % 8;
			if (index >= TIMES.size()) {
				if (step == 7) {
					MinehouseRiftClient.LOG.info(CinePlayer.playing() ? "MINEHOUSE_FILM_OK" : "MINEHOUSE_FILM_FAIL");
					client.stop();
				}
				return;
			}
			float t = TIMES.get(index);
			if (step == 0) {
				CinePlayer.hold(t);
			}
			if (step == 6) {
				String name = String.format("film-%04d.png", Math.round(t * 10));
				Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1,
						msg -> MinehouseRiftClient.LOG.info("Film frame {}: {}", name, msg.getString()));
				index++;
			}
		});
	}
}
