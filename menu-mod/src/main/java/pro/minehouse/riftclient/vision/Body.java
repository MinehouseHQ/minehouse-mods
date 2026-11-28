package pro.minehouse.riftclient.vision;

import org.joml.Vector3f;

/**
 * A rough human figure as a distance field (ten units to a metre): head and face to -x (seen in profile from +z),
 * one arm raised and bent toward the camera, the other down. Veins and haze are grown on it.
 */
final class Body {
	private Body() {
	}

	private static final float[][] ELLIPSOIDS = {
			{ 0, 16.6f, 0, 1.0f, 1.2f, 0.85f }, // skull
			{ -0.55f, 16.0f, 0, 0.6f, 0.75f, 0.6f }, // face and jaw
			{ 0.1f, 12.7f, 0, 1.25f, 2.5f, 1.8f }, // chest
			{ 0.1f, 10.1f, 0, 1.15f, 1.3f, 1.6f }, // belly and hips
	};
	private static final float[][] CAPSULES = {
			{ 0.1f, 15.0f, 0, 0.05f, 15.9f, 0, 0.5f }, // neck
			{ 0.1f, 14.5f, -1.8f, 0.1f, 14.5f, 1.8f, 0.65f }, // shoulders
			{ 0.1f, 14.5f, 1.8f, -0.3f, 16.4f, 2.6f, 0.42f }, // upper arm, raised
			{ -0.3f, 16.4f, 2.6f, -1.2f, 15.2f, 2.3f, 0.36f }, // forearm
			{ 0.1f, 14.5f, -1.8f, 0.3f, 11.7f, -2.2f, 0.42f }, // other arm, down
			{ 0.3f, 11.7f, -2.2f, 0.1f, 9.4f, -2.0f, 0.36f },
	};

	static float sdf(Vector3f p) {
		float d = 1e9f;
		for (float[] e : ELLIPSOIDS) {
			float qx = (p.x - e[0]) / e[3], qy = (p.y - e[1]) / e[4], qz = (p.z - e[2]) / e[5];
			float k = ((float) Math.sqrt(qx * qx + qy * qy + qz * qz) - 1) * Math.min(e[3], Math.min(e[4], e[5]));
			d = smin(d, k, 0.5f);
		}
		for (float[] c : CAPSULES) {
			float bx = c[3] - c[0], by = c[4] - c[1], bz = c[5] - c[2];
			float px = p.x - c[0], py = p.y - c[1], pz = p.z - c[2];
			float h = Math.max(0, Math.min(1, (px * bx + py * by + pz * bz) / (bx * bx + by * by + bz * bz)));
			float dx = px - bx * h, dy = py - by * h, dz = pz - bz * h;
			d = smin(d, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - c[6], 0.5f);
		}
		return d;
	}

	static Vector3f normal(Vector3f p) {
		float e = 0.02f;
		return new Vector3f(sdf(new Vector3f(p).add(e, 0, 0)) - sdf(new Vector3f(p).sub(e, 0, 0)),
				sdf(new Vector3f(p).add(0, e, 0)) - sdf(new Vector3f(p).sub(0, e, 0)),
				sdf(new Vector3f(p).add(0, 0, e)) - sdf(new Vector3f(p).sub(0, 0, e))).normalize();
	}

	/** Moves p onto the level set at depth (negative = inside). */
	static void project(Vector3f p, float depth) {
		for (int i = 0; i < 3; i++) {
			float d = sdf(p) - depth;
			p.sub(normal(p).mul(d));
		}
	}

	private static float smin(float a, float b, float k) {
		float h = Math.max(k - Math.abs(a - b), 0) / k;
		return Math.min(a, b) - h * h * k * 0.25f;
	}
}
