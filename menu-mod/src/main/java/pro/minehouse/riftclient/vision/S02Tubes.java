package pro.minehouse.riftclient.vision;

import java.util.Random;

import org.joml.Quaternionf;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 5.4–7.0: giant mouths of hollow tubes, soft focus; a fast pull-back with a clockwise roll shows a bundle of them
 * set in a field of finer hexagonal pipes, which turns out to be the crust of a rock.
 */
final class S02Tubes extends Shot {
	private final Meshes m;
	private final Instances bundle = new Instances("tube bundle");
	private final Instances field = new Instances("tube field");

	private static final Cam CAM = new Cam(40,
			new float[] { 5.4f, 1.0f, -1.6f, 3.0f, 0.2f, 0.4f, -10, 0 },
			new float[] { 5.6f, 1.0f, -2.2f, 5.0f, 0.2f, 0.4f, -10, -4 },
			new float[] { 5.9f, 0.6f, -4f, 14f, 0.1f, 0.3f, -10, -14 },
			new float[] { 6.4f, 0.5f, -12f, 32f, 0.2f, 0f, -10, -26 },
			new float[] { 7.0f, 4f, -26f, 38f, 2f, 0f, -10, -34 });

	S02Tubes(Meshes m) {
		super(5.4f, 7.0f);
		this.m = m;
		Random r = new Random(11);
		Quaternionf none = new Quaternionf();
		// the bundle: 37 thick pipes packed in a hexagon, their mouths a little uneven
		float sp = 2.1f;
		for (int q = -3; q <= 3; q++) {
			for (int k = -3; k <= 3; k++) {
				if (Math.abs(q + k) > 3) {
					continue;
				}
				float x = sp * (q + k * 0.5f), y = sp * k * 0.866f;
				bundle.add(x, y, (float) (r.nextGaussian() * 0.15), 1, none, 1, 1, 9, 0.9f + r.nextFloat() * 0.2f, q * 7 + k, 0, 0);
			}
		}
		// the field: fine pipes over a crater that rises away from the bundle, leaning outwards
		float fs = 0.62f;
		for (int q = -80; q <= 80; q++) {
			for (int k = -80; k <= 80; k++) {
				float x = fs * (q + k * 0.5f), y = fs * k * 0.866f;
				float d = (float) Math.sqrt(x * x + y * y);
				if (d < 7.0f || d > 46) {
					continue;
				}
				float bump = (float) Noise.fbm(x * 0.08, y * 0.08, 3.1, 4);
				float z = -0.3f + 0.12f * (d - 7.0f) + 9f * (bump - 0.45f) * Keys.clamp01((d - 10) / 12);
				Quaternionf lean = new Quaternionf().rotateAxis((float) Math.min(0.9, 0.012 * d + 0.25), -y / d, x / d, 0);
				field.add(x, y, z, 0.28f, lean, 1, 1, 16, 0.7f + 0.3f * (float) Noise.hash(q, k, 1), q * 3 + k, 0, 0);
			}
		}
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 400);
		f.light(-0.8f, 0.55f, 0.25f, 0.9f);
		f.background(0.02f);
		f.fog(0.06f, 0.006f);
		f.bloom(0.5f, 0.7f);
		f.exposure(1.0f);
		f.focus(dist, 6, 18);
		Material sem = Material.solid(Material.SEM).color(0.75f, 0.82f, 0.88f).p(2.2f, 1.2f, 0.4f).q(0.45f, 0, 0, 0);
		f.draw(m.pipe, bundle, sem, null);
		f.draw(m.pipeLow, field, sem.copy().p(5f, 1.4f, 0.4f).q(0.3f, 0, 0, 0), null);
	}

	@Override
	public void close() {
		bundle.close();
		field.close();
	}
}
