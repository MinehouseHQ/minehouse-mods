package pro.minehouse.riftclient.vision;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import pro.minehouse.riftclient.cine.Film;
import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Keys;

/**
 * The vision in the limbo: how the world of the game is made, one pull-back across all its scales, drawn by the
 * client in 3D from the game's own textures. A pixel, a texture, a block in the rock, a cave and a lush cave, a
 * player of sculk veins on a beacon, a village and its chunks, the world as a ball of blocks, the Overworld, the
 * Nether and the End, and the Eye (Зеница) before the sun. 66.5 seconds, ending in the white of the limbo. The
 * camera, the rhythm, the cuts and the steel grade follow the 3 Body Problem titles (Timur 28.11: «такая же
 * катсцена, но про устройство мира Minecraft»; ref-3bp/SHOTS.md on the ref/3bp-frames branch).
 */
public final class VisionFilm implements Film {
	public static final float LENGTH = 66.5f;
	private final Meshes meshes = new Meshes();
	private final List<Shot> shots = new ArrayList<>();

	public VisionFilm() {
		shots.add(new M01Pixel(meshes));
		shots.add(new M02Block(meshes));
		shots.add(new M03Cave(meshes));
		shots.add(new M04Lush(meshes));
		shots.add(new M05Player(meshes));
		shots.add(new M06City(meshes));
		shots.add(new M07World(meshes));
		shots.add(new M08Dimensions(meshes));
		shots.add(new M09Eye(meshes));
	}

	/** Its own music (sounds/vision.ogg): in the manner of the titles' score, its own tune, on the same cuts. */
	@Override
	public Identifier music() {
		return Identifier.fromNamespaceAndPath("minehouse-rift", "vision");
	}

	@Override
	public float length() {
		return LENGTH;
	}

	@Override
	public void frame(Frame f, float t) {
		Shot shot = null;
		for (Shot s : shots) {
			if (t >= s.start && t < s.end) {
				shot = s;
			}
		}
		if (shot != null) {
			f.shotTime(t - shot.start);
			shot.frame(f, t);
		}
		cuts(f, t);
	}

	/** What happens between the shots: zooms through, flashes, dips to black, the white-out. */
	private static void cuts(Frame f, float t) {
		// T1 5.3–5.5: a zoom through, the picture pulled into the middle
		f.zoomBlur(0.35f * bump(t, 5.2f, 5.4f, 5.6f), 0.55f, 0.5f);
		// S2 pull-back: the outer pipes smear outwards
		f.zoomBlur(0.22f * bump(t, 5.6f, 6.0f, 6.6f), 0.5f, 0.5f);
		// T2 10.0–10.4: the push through the slab, a flash
		f.zoomBlur(0.3f * bump(t, 9.95f, 10.25f, 10.5f), 0.5f, 0.45f);
		f.lift(0.35f * bump(t, 10.0f, 10.2f, 10.45f));
		// T4 14.3: hard cut, a beat darker
		f.black(0.5f * bump(t, 14.25f, 14.3f, 14.6f));
		// T5 22.8–23.3: dissolve through black
		f.black(bump(t, 22.7f, 23.0f, 23.4f));
		// S10: the fast pull-back to orbit smears the lights
		f.zoomBlur(0.25f * bump(t, 35.3f, 35.9f, 36.3f), 0.5f, 0.5f);
		// the end: lift into the white of the limbo
		f.white(Keys.ramp(t, 65.0f, 65.6f));
	}

	/** 0 → 1 → 0 over a, peak, b. */
	static float bump(float t, float a, float peak, float b) {
		if (t <= a || t >= b) {
			return 0;
		}
		return t < peak ? Keys.ease((t - a) / (peak - a)) : 1 - Keys.ease((t - peak) / (b - peak));
	}

	@Override
	public void close() {
		for (Shot s : shots) {
			s.close();
		}
		meshes.close();
	}
}
