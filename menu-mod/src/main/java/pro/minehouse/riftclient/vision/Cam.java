package pro.minehouse.riftclient.vision;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Keys;

/** A camera move: rows of time, eye xyz, target xyz, roll in degrees. */
final class Cam {
	private final Keys keys;
	private final float fov;

	Cam(float fov, float[]... rows) {
		this.keys = new Keys(rows);
		this.fov = fov;
	}

	/** Puts the camera where it is at t, and gives back how far the target is (for the focus). */
	float apply(Frame f, float t) {
		float ex = keys.at(t, 0), ey = keys.at(t, 1), ez = keys.at(t, 2);
		float tx = keys.at(t, 3), ty = keys.at(t, 4), tz = keys.at(t, 5);
		f.camera(ex, ey, ez, tx, ty, tz, keys.width() > 7 ? keys.at(t, 7) : fov);
		f.roll(keys.at(t, 6));
		float dx = tx - ex, dy = ty - ey, dz = tz - ez;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
