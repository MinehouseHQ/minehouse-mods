package pro.minehouse.riftclient.cine;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import pro.minehouse.riftclient.MinehouseRiftClient;

/**
 * Plays one client-side film over everything on the screen (the world, the HUD, chat). A film that fails to draw is
 * stopped and not tried again until the game restarts, so a broken driver never breaks the game.
 */
public final class CinePlayer {
	private static CineRenderer renderer;
	private static Film film;
	private static long start;
	private static float still = -1;
	private static boolean broken;
	private static SoundInstance music;

	private CinePlayer() {
	}

	/** Starts a film now, or from a later moment of it. */
	public static void play(Supplier<Film> make, float from) {
		if (broken) {
			return;
		}
		stop();
		try {
			film = make.get();
		} catch (RuntimeException e) {
			fail(e);
			return;
		}
		// the scenes are built: the film and its music start together from here
		start = System.nanoTime() - (long) (from * 1e9);
		still = -1;
		Identifier id = film.music();
		if (id != null && from < 0.5f) {
			try {
				Minecraft mc = mc();
				mc.getMusicManager().stopPlaying();
				music = new SimpleSoundInstance(id, SoundSource.MASTER, 1f, 1f, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
				mc.getSoundManager().play(music);
			} catch (RuntimeException e) {
				MinehouseRiftClient.LOG.warn("Film music off", e);
				music = null;
			}
		}
	}

	/** Holds the film on one moment (for looking at it frame by frame). */
	public static void hold(float seconds) {
		still = seconds;
	}

	public static void stop() {
		if (music != null) {
			mc().getSoundManager().stop(music);
			music = null;
		}
		if (film != null) {
			film.close();
			film = null;
		}
	}

	public static boolean playing() {
		return film != null;
	}

	public static float seconds() {
		return still >= 0 ? still : (System.nanoTime() - start) / 1e9f;
	}

	/** Called once a frame, after the game drew its picture and its interface. */
	public static void renderFrame() {
		if (film == null) {
			return;
		}
		float t = seconds();
		if (t > film.length()) {
			stop();
			return;
		}
		try {
			if (renderer == null) {
				renderer = new CineRenderer();
			}
			renderer.render(film, t);
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private static void fail(RuntimeException e) {
		MinehouseRiftClient.LOG.error("Film off", e);
		broken = true;
		stop();
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
