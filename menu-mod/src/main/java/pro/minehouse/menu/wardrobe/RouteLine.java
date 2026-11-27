package pro.minehouse.menu.wardrobe;

import java.util.ArrayList;
import java.util.List;

/**
 * The route to the fitting room as a smooth line: block cells from the server, rounded off
 * (Chaikin) so steps become slopes and corners curves, measured by length so arrows can slide
 * along it. No Minecraft classes: unit-tested.
 */
final class RouteLine {
	/** A point on the line and the way forward there (unit vector). */
	record At(double x, double y, double z, double dx, double dy, double dz) {
	}

	final double[] xs;
	final double[] ys;
	final double[] zs;
	/** Length from the start to each point. */
	final double[] along;

	private RouteLine(List<double[]> pts) {
		int n = pts.size();
		xs = new double[n];
		ys = new double[n];
		zs = new double[n];
		along = new double[n];
		for (int i = 0; i < n; i++) {
			xs[i] = pts.get(i)[0];
			ys[i] = pts.get(i)[1];
			zs[i] = pts.get(i)[2];
			if (i > 0) along[i] = along[i - 1] + Math.sqrt(sq(xs[i] - xs[i - 1]) + sq(ys[i] - ys[i - 1]) + sq(zs[i] - zs[i - 1]));
		}
	}

	/** From feet cells (block x, y, z): the middle of each cell's floor, rounded twice. */
	static RouteLine of(List<int[]> cells) {
		List<double[]> pts = new ArrayList<>(cells.size());
		for (int[] c : cells) pts.add(new double[] {c[0] + .5, c[1], c[2] + .5});
		for (int k = 0; k < 2 && pts.size() > 2; k++) pts = chaikin(pts);
		return new RouteLine(pts);
	}

	/** Corner cutting: each segment gives its 1/4 and 3/4 points; the ends stay. */
	static List<double[]> chaikin(List<double[]> in) {
		List<double[]> out = new ArrayList<>(in.size() * 2);
		out.add(in.get(0));
		for (int i = 0; i + 1 < in.size(); i++) {
			double[] a = in.get(i), b = in.get(i + 1);
			out.add(lerp(a, b, .25));
			out.add(lerp(a, b, .75));
		}
		out.add(in.get(in.size() - 1));
		return out;
	}

	private static double[] lerp(double[] a, double[] b, double t) {
		return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
	}

	private static double sq(double v) {
		return v * v;
	}

	int size() {
		return xs.length;
	}

	double length() {
		return xs.length == 0 ? 0 : along[xs.length - 1];
	}

	/** How far along the line the point nearest to (x, y, z) is. */
	double project(double x, double y, double z) {
		double best = Double.MAX_VALUE, at = 0;
		for (int i = 0; i + 1 < xs.length; i++) {
			double vx = xs[i + 1] - xs[i], vy = ys[i + 1] - ys[i], vz = zs[i + 1] - zs[i];
			double len2 = vx * vx + vy * vy + vz * vz;
			double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - xs[i]) * vx + (y - ys[i]) * vy + (z - zs[i]) * vz) / len2));
			double d = sq(xs[i] + vx * t - x) + sq(ys[i] + vy * t - y) + sq(zs[i] + vz * t - z);
			if (d < best) {
				best = d;
				at = along[i] + Math.sqrt(len2) * t;
			}
		}
		return at;
	}

	/** The point {@code s} along the line (clamped) and the direction there. */
	At at(double s) {
		int n = xs.length;
		if (n == 1) return new At(xs[0], ys[0], zs[0], 1, 0, 0);
		s = Math.max(0, Math.min(length(), s));
		int lo = 0, hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (along[mid] <= s) lo = mid;
			else hi = mid;
		}
		double seg = along[hi] - along[lo];
		double t = seg < 1e-9 ? 0 : (s - along[lo]) / seg;
		double vx = xs[hi] - xs[lo], vy = ys[hi] - ys[lo], vz = zs[hi] - zs[lo];
		double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (len < 1e-9) {
			vx = 1;
			len = 1;
		}
		return new At(xs[lo] + vx * t, ys[lo] + vy * t, zs[lo] + vz * t, vx / len, vy / len, vz / len);
	}
}
