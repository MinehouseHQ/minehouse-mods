package pro.minehouse.riftclient.vision;

import java.util.ArrayList;
import java.util.List;

import pro.minehouse.riftclient.cine.Film;
import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Keys;

/**
 * The vision in the limbo (Timur 28.11: «почти один в один» with the 3 Body Problem titles): one pull-back across
 * all scales, from a spore to the stars, drawn by the client in 3D. 66.5 seconds, ending in the white of the limbo.
 * The shots and their cuts follow the reference frame by frame (ref-3bp/SHOTS.md on the ref/3bp-frames branch).
 */
public final class VisionFilm implements Film {
	public static final float LENGTH = 66.5f;
	private final Meshes meshes = new Meshes();
	private final List<Shot> shots = new ArrayList<>();

	public VisionFilm() {
		shots.add(new S01Spore(meshes));
		shots.add(new S02Tubes(meshes));
		shots.add(new S03Mineral(meshes));
		shots.add(new S04Tangle());
		shots.add(new S06Figure(meshes));
		shots.add(new S08City(meshes));
		shots.add(new S11Space(meshes));
		shots.add(new S13Ice(meshes));
		shots.add(new S16Star(meshes));
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
