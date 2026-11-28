package pro.minehouse.riftclient.vision;

import pro.minehouse.riftclient.cine.Noise;

/** Small helpers for placing things. */
final class Pt {
	private Pt() {
	}

	/** An angle into 0..2π. */
	static double wrap(double a) {
		double p = Math.PI * 2;
		return ((a % p) + p) % p;
	}

	/** A smooth noise along one line, 0..1. */
	static double noise1(double x) {
		return Noise.fbm(x, 0.37, 0.71, 3) * 1.6 - 0.3;
	}
}
