package pro.minehouse.riftclient.cine;

/** A small value noise for building shapes on the CPU. */
public final class Noise {
	private Noise() {
	}

	public static double hash(double x, double y, double z) {
		double h = Math.sin(x * 127.1 + y * 311.7 + z * 74.7) * 43758.5453;
		return h - Math.floor(h);
	}

	public static double hash(long n) {
		return hash(n * 0.137, n * 1.731, n * 0.0313);
	}

	public static double noise(double x, double y, double z) {
		double ix = Math.floor(x), iy = Math.floor(y), iz = Math.floor(z);
		double fx = x - ix, fy = y - iy, fz = z - iz;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		double a = lerp(lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx), lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy);
		double b = lerp(lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx), lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy);
		return lerp(a, b, fz);
	}

	public static double fbm(double x, double y, double z, int octaves) {
		double s = 0, a = 0.5;
		for (int i = 0; i < octaves; i++) {
			s += a * noise(x, y, z);
			x = x * 2.03 + 1.7;
			y = y * 2.03 + 9.2;
			z = z * 2.03 + 3.1;
			a *= 0.5;
		}
		return s;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
