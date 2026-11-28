package pro.minehouse.riftclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The glass of a rift, broken (Timur, 28.11: «все разломы как рифт в миниигру, только фиолетовые и
 * объёмные», «не в одной плоскости, а в трёх сразу, осколки разлетаются во все стороны»).
 *
 * <p>Pure geometry, no game classes: the rift is a unit sphere round its middle. A pane is broken
 * into wedges round a small hole (where the flash is) in each of three crossed planes, and thin
 * slivers fly off with any orientation. As the rift opens, each piece swings out of the middle on its
 * own delay, overshoots a little and settles; then they all bob and the whole star turns slowly about
 * the upright axis. Prototype: https://claude.ai/artifact/Rf14a1vGkmBAcfiXrQRdpi
 *
 * <p>Every piece is a polygon star-shaped about its centre, so it is drawn and cut as a fan of
 * triangles from there; each triangle is convex, which is what the cut in {@link ShardCut} needs.
 */
final class Shards {
	/** Seconds the opening burst takes. */
	static final float OPEN_SECONDS = 1.6f;

	/** One piece: its outline in its own plane, and how it moves. */
	record Shard(float[] poly, float cx, float cy, float[] basis, float z, float tx, float ty, float tz, float outward, float delay,
			float phase, boolean sliver) {
		int corners() {
			return poly.length / 2;
		}
	}

	private Shards() {
	}

	/** The three planes as columns u, v, n (row-major 3x3). */
	static float[] euler(double x, double y, double z) {
		double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
		// R = Rx * Ry * Rz (three.js order XYZ)
		return new float[] {
				(float) (cy * cz), (float) (-cy * sz), (float) sy,
				(float) (cx * sz + sx * sy * cz), (float) (cx * cz - sx * sy * sz), (float) (-sx * cy),
				(float) (sx * sz - cx * sy * cz), (float) (sx * cz + cx * sy * sz), (float) (cx * cy) };
	}

	static List<Shard> generate(long seed) {
		Random r = new Random(seed);
		List<Shard> out = new ArrayList<>();
		pane(out, r, euler(0, 0, 0), 8, 2, 5);
		pane(out, r, euler(Math.PI / 2, 0, 0.4), 7, 1, 4);
		pane(out, r, euler(0, Math.PI / 2, 0.9), 7, 3, 6);
		for (int i = 0; i < 16; i++) {
			// A random plane: rotate a basis by three random angles.
			float[] b = euler(r.nextDouble() * Math.PI * 2, r.nextDouble() * Math.PI * 2, r.nextDouble() * Math.PI * 2);
			float len = 0.15f + r.nextFloat() * 0.3f, w = 0.015f + r.nextFloat() * 0.022f, at = 0.3f + r.nextFloat() * 0.55f;
			float[] poly = { at - len / 2, w, at + len / 2, 0, at - len / 2, -w };
			out.add(new Shard(poly, at - len / 6, 0, b, 0, (r.nextFloat() - 0.5f) * 2, (r.nextFloat() - 0.5f) * 2, (r.nextFloat() - 0.5f) * 2,
					0.35f + r.nextFloat() * 0.3f, r.nextFloat() * 0.3f, r.nextFloat() * 6.28f, true));
		}
		return out;
	}

	/** Wedges round the hole in one plane, two of them missing (gaps, as in the picture). */
	private static void pane(List<Shard> out, Random r, float[] basis, int n, int skipA, int skipB) {
		for (int k = 0; k < n; k++) {
			if (k == skipA || k == skipB) {
				continue;
			}
			double a0 = k * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.2;
			double a1 = (k + 1) * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.2 - 0.07;
			float r0 = 0.1f + r.nextFloat() * 0.06f, r1 = 0.6f + r.nextFloat() * 0.4f;
			float z = (r.nextFloat() - 0.5f) * 0.55f;
			float tx = (r.nextFloat() - 0.5f) * 0.9f, ty = (r.nextFloat() - 0.5f) * 0.9f, tz = (r.nextFloat() - 0.5f) * 0.35f;
			if (r.nextFloat() < 0.4f) {
				float rm = r0 + (r1 - r0) * (0.4f + r.nextFloat() * 0.2f);
				add(out, wedge(r, a0, a1, r0, rm, false), basis, z * 0.5f, tx * 0.6f, ty * 0.6f, tz * 0.6f, 0.25f, r);
				add(out, wedge(r, a0, a1, rm * 1.04f, r1, true), basis, z, tx, ty, tz, 0.6f, r);
			} else {
				add(out, wedge(r, a0, a1, r0, r1, false), basis, z, tx, ty, tz, 0.45f, r);
			}
		}
	}

	private static void add(List<Shard> out, float[] poly, float[] basis, float z, float tx, float ty, float tz, float outward, Random r) {
		float cx = 0, cy = 0;
		int n = poly.length / 2;
		for (int i = 0; i < n; i++) {
			cx += poly[i * 2];
			cy += poly[i * 2 + 1];
		}
		out.add(new Shard(poly, cx / n, cy / n, basis, z, tx, ty, tz, outward, r.nextFloat() * 0.3f, r.nextFloat() * 6.28f, false));
	}

	/**
	 * A wedge from angle a0 to a1, radius ri to ro: the inner edge straight (or jagged, when it is
	 * the outer half of a split wedge), the outer edge jagged, the sides bent once.
	 */
	private static float[] wedge(Random r, double a0, double a1, float ri, float ro, boolean jagIn) {
		List<float[]> pts = new ArrayList<>();
		if (jagIn) {
			jagged(pts, r, ri, a0, a1, 0.88f, 0.12f);
		} else {
			pts.add(polar(a0, ri));
			pts.add(polar(a1, ri));
		}
		pts.add(polar(a1 + (r.nextDouble() - 0.5) * 0.1, (ri + ro) / 2));
		jagged(pts, r, ro, a1, a0, 0.74f, 0.32f);
		pts.add(polar(a0 + (r.nextDouble() - 0.5) * 0.1, (ri + ro) / 2));
		float[] poly = new float[pts.size() * 2];
		for (int i = 0; i < pts.size(); i++) {
			poly[i * 2] = pts.get(i)[0];
			poly[i * 2 + 1] = pts.get(i)[1];
		}
		return poly;
	}

	private static void jagged(List<float[]> pts, Random r, float rad, double from, double to, float lo, float span) {
		int s = 4 + r.nextInt(3);
		for (int i = 0; i <= s; i++) {
			double a = from + (to - from) * i / s;
			pts.add(polar(a, rad * (lo + r.nextFloat() * span)));
		}
	}

	private static float[] polar(double a, float rad) {
		return new float[] { (float) Math.cos(a) * rad, (float) Math.sin(a) * rad };
	}

	/** Overshoots and settles: 0 at 0, 1 at 1. */
	static float easeOutBack(float t) {
		float c1 = 1.70158f, c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}

	/**
	 * Where the corners of {@code s} are now, in the rift's unit space: x, y, z per corner into
	 * {@code out}, then its centre (3 more). {@code open}: seconds since it opened, over
	 * OPEN_SECONDS; {@code time}: seconds, for the bobbing and the slow turn. False when it has not
	 * come out yet.
	 */
	static boolean place(Shard s, float open, float time, boolean still, float[] out) {
		float k = Math.max(0, Math.min(1, (open - s.delay()) / 0.75f));
		if (k <= 0) {
			return false;
		}
		float e = easeOutBack(k);
		float sp = 1 + s.outward();
		float bob = still ? 0 : (float) Math.sin(time * 0.7 + s.phase()) * 0.02f;
		float wob = still ? 0 : 0.05f;
		float px = s.cx() * sp * e, py = s.cy() * sp * e, pz = (s.z() + bob) * e;
		float[] t = euler(s.tx() + wob * Math.sin(time * 0.5 + s.phase()), s.ty() + wob * Math.cos(time * 0.45 + s.phase()), s.tz());
		float spin = still ? 0 : time * 0.07f;
		float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
		float[] b = s.basis();
		int n = s.corners();
		for (int i = 0; i <= n; i++) {
			float lx, ly;
			if (i < n) {
				lx = (s.poly()[i * 2] - s.cx()) * e;
				ly = (s.poly()[i * 2 + 1] - s.cy()) * e;
			} else {
				lx = 0;
				ly = 0;
			}
			// Tilt about its own centre, then into its plane, then the slow turn about y.
			float qx = t[0] * lx + t[1] * ly + px, qy = t[3] * lx + t[4] * ly + py, qz = t[6] * lx + t[7] * ly + pz;
			float wx = b[0] * qx + b[1] * qy + b[2] * qz;
			float wy = b[3] * qx + b[4] * qy + b[5] * qz;
			float wz = b[6] * qx + b[7] * qy + b[8] * qz;
			out[i * 3] = cs * wx + sn * wz;
			out[i * 3 + 1] = wy;
			out[i * 3 + 2] = -sn * wx + cs * wz;
		}
		return true;
	}

	static int maxCorners(List<Shard> shards) {
		int m = 0;
		for (Shard s : shards) {
			m = Math.max(m, s.corners());
		}
		return m;
	}
}
