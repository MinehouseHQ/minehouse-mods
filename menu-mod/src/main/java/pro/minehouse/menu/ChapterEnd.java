package pro.minehouse.menu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Util;

/**
 * The end of Chapter 1. The server says so right before it kicks everyone and stops; from then on
 * the main menu has no button to join MineHouse, only the black dot the world fell into, and the
 * kick goes straight to that menu. Kept on disk (config/minehouse-chapter-end.txt) so a restart of
 * the game doesn't bring the button back. It comes back once the server answers again, a while
 * after the end: that is the next chapter.
 */
public final class ChapterEnd {
	/** Pings in the first minutes after the end may still reach the old server going down. */
	static final long QUIET_MS = 5 * 60_000L;

	private static Boolean ended;
	private static long since;
	private static boolean justKicked;

	private ChapterEnd() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("minehouse-chapter-end.txt");
	}

	/** Is Chapter 1 over (and the next not here yet)? */
	public static boolean active() {
		if (ended == null) {
			ended = false;
			try {
				Path f = file();
				if (Files.exists(f)) {
					since = Long.parseLong(Files.readString(f).trim());
					ended = true;
				}
			} catch (IOException | NumberFormatException e) {
				ended = false;
			}
		}
		return ended;
	}

	/** The server's word: the chapter is over. */
	public static void mark() {
		ended = true;
		since = System.currentTimeMillis();
		justKicked = true;
		try {
			Files.writeString(file(), Long.toString(since));
		} catch (IOException e) {
			MinehouseMenu.LOG.warn("Could not keep the end of the chapter: {}", e.toString());
		}
	}

	static void clear() {
		ended = false;
		justKicked = false;
		try {
			Files.deleteIfExists(file());
		} catch (IOException e) {
			MinehouseMenu.LOG.warn("Could not forget the end of the chapter: {}", e.toString());
		}
	}

	/** True when the server's last answer (wall clock, 0 for none) means the next chapter has begun. */
	static boolean over(long answeredAt, long since) {
		return answeredAt - since >= QUIET_MS;
	}

	/** Every client tick: the kick lands in the main menu, and the menu comes back with the server. */
	public static void tick(Minecraft mc) {
		if (!active()) {
			return;
		}
		if (justKicked && mc.gui.screen() instanceof DisconnectedScreen) {
			justKicked = false;
			mc.gui.setScreen(new TitleScreen());
		}
		if (mc.level == null && over(ServerStatus.answeredAt(), since)) {
			MinehouseMenu.LOG.info("MineHouse answers again: the next chapter is here");
			clear();
		}
	}

	/** Seconds since the end, for the dot's slow pulse. */
	public static float seconds() {
		return (Util.getMillis() % 3_600_000L) / 1000f;
	}
}
