package pro.minehouse.riftclient.cine;

/**
 * Values over time, smoothly through key moments: {@code new Keys(0, 1, 2, 5)} goes 1 at 0 s to 5 at 2 s. Rows of
 * several values (a camera: eye, target, fov, roll) go through each key together.
 */
public final class Keys {
	private final float[] times;
	private final float[][] values;

	/** time, value, time, value... */
	public Keys(float... pairs) {
		int n = pairs.length / 2;
		times = new float[n];
		values = new float[n][1];
		for (int i = 0; i < n; i++) {
			times[i] = pairs[i * 2];
			values[i][0] = pairs[i * 2 + 1];
		}
	}

	/** Each row: time, then the values. */
	public Keys(float[]... rows) {
		times = new float[rows.length];
		values = new float[rows.length][];
		for (int i = 0; i < rows.length; i++) {
			times[i] = rows[i][0];
			values[i] = java.util.Arrays.copyOfRange(rows[i], 1, rows[i].length);
		}
	}

	/** How many values each key holds. */
	public int width() {
		return values[0].length;
	}

	public float at(float t) {
		return at(t, 0);
	}

	/** Catmull-Rom through the keys, held flat before the first and after the last. */
	public float at(float t, int k) {
		int n = times.length;
		if (t <= times[0]) {
			return values[0][k];
		}
		if (t >= times[n - 1]) {
			return values[n - 1][k];
		}
		int i = 0;
		while (i < n - 2 && t > times[i + 1]) {
			i++;
		}
		float u = (t - times[i]) / (times[i + 1] - times[i]);
		float p1 = values[i][k], p2 = values[i + 1][k];
		float p0 = i > 0 ? values[i - 1][k] : p1 - (p2 - p1);
		float p3 = i + 2 < n ? values[i + 2][k] : p2 + (p2 - p1);
		// keep the speed even when the keys are not evenly spaced
		float d1 = times[i + 1] - times[i];
		float d0 = i > 0 ? times[i] - times[i - 1] : d1;
		float d2 = i + 2 < n ? times[i + 2] - times[i + 1] : d1;
		float m1 = (p2 - p0) / (d0 + d1) * d1;
		float m2 = (p3 - p1) / (d1 + d2) * d1;
		float u2 = u * u, u3 = u2 * u;
		return (2 * u3 - 3 * u2 + 1) * p1 + (u3 - 2 * u2 + u) * m1 + (-2 * u3 + 3 * u2) * p2 + (u3 - u2) * m2;
	}

	public static float ease(float x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}

	public static float clamp01(float x) {
		return Math.max(0, Math.min(1, x));
	}

	/** 0 before a, 1 after b, smooth between. */
	public static float ramp(float t, float a, float b) {
		return ease((t - a) / (b - a));
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
