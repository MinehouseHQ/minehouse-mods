package pro.minehouse.riftclient.cine;

import java.util.List;

import org.joml.Vector3f;

/** The shapes films are made of. Every shape is centred on zero and about one block across, unless said. */
public final class Shapes {
	private Shapes() {
	}

	/** A ball of radius 1. */
	public static void sphere(Mesh.Builder b, int rings, int segments) {
		int base = b.vertices();
		for (int r = 0; r <= rings; r++) {
			double phi = Math.PI * r / rings;
			for (int s = 0; s <= segments; s++) {
				double th = 2 * Math.PI * s / segments;
				float x = (float) (Math.sin(phi) * Math.cos(th)), y = (float) Math.cos(phi), z = (float) (Math.sin(phi) * Math.sin(th));
				b.vertex(x, y, z, x, y, z, s / (float) segments, r / (float) rings);
			}
		}
		for (int r = 0; r < rings; r++) {
			for (int s = 0; s < segments; s++) {
				int a = base + r * (segments + 1) + s, c = a + segments + 1;
				b.quad(a, c, c + 1, a + 1);
			}
		}
	}

	/** A lumpy ball: the radius changes with a noise, for rocks and asteroids. */
	public static void lump(Mesh.Builder b, int rings, int segments, float amount, long seed) {
		int base = b.vertices();
		Vector3f[] pts = new Vector3f[(rings + 1) * (segments + 1)];
		for (int r = 0; r <= rings; r++) {
			double phi = Math.PI * r / rings;
			for (int s = 0; s <= segments; s++) {
				double th = 2 * Math.PI * (s % segments) / segments;
				float x = (float) (Math.sin(phi) * Math.cos(th)), y = (float) Math.cos(phi), z = (float) (Math.sin(phi) * Math.sin(th));
				float k = 1 + amount * (float) (Noise.fbm(x * 1.3 + seed, y * 1.3, z * 1.3, 4) - 0.5) * 2;
				pts[r * (segments + 1) + s] = new Vector3f(x * k, y * k * 0.85f, z * k);
			}
		}
		for (int r = 0; r <= rings; r++) {
			for (int s = 0; s <= segments; s++) {
				Vector3f p = pts[r * (segments + 1) + s];
				Vector3f n = new Vector3f(p).normalize();
				b.vertex(p, n, s / (float) segments, r / (float) rings);
			}
		}
		for (int r = 0; r < rings; r++) {
			for (int s = 0; s < segments; s++) {
				int a = base + r * (segments + 1) + s, c = a + segments + 1;
				b.quad(a, c, c + 1, a + 1);
			}
		}
	}

	/** A box from -0.5 to 0.5. */
	public static void box(Mesh.Builder b) {
		float[][] n = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
		for (float[] f : n) {
			Vector3f nn = new Vector3f(f[0], f[1], f[2]);
			Vector3f u = Math.abs(nn.y) > 0.5f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
			Vector3f w = new Vector3f(nn).cross(u);
			int[] idx = new int[4];
			for (int k = 0; k < 4; k++) {
				float su = (k == 0 || k == 3) ? -0.5f : 0.5f, sw = (k < 2) ? -0.5f : 0.5f;
				Vector3f p = new Vector3f(nn).mul(0.5f).add(new Vector3f(u).mul(su)).add(new Vector3f(w).mul(sw));
				idx[k] = b.vertex(p, nn, su + 0.5f, sw + 0.5f);
			}
			b.quad(idx[0], idx[1], idx[2], idx[3]);
		}
	}

	/** A flat square from -1 to 1 facing +z: sprites. */
	public static void quad(Mesh.Builder b) {
		int a = b.vertex(-1, -1, 0, 0, 0, 1, 0, 0);
		int c = b.vertex(1, -1, 0, 0, 0, 1, 1, 0);
		int d = b.vertex(1, 1, 0, 0, 0, 1, 1, 1);
		int e = b.vertex(-1, 1, 0, 0, 0, 1, 0, 1);
		b.quad(a, c, d, e);
	}

	/**
	 * A tube along a line of points, its radius changing along it. The u of the surface runs along the tube (0..1),
	 * the v round it.
	 */
	public static void tube(Mesh.Builder b, List<Vector3f> line, float[] radius, int sides) {
		int n = line.size();
		if (n < 2) {
			return;
		}
		int base = b.vertices();
		Vector3f prevNormal = null;
		for (int k = 0; k < n; k++) {
			Vector3f p = line.get(k);
			Vector3f dir = new Vector3f(line.get(Math.min(k + 1, n - 1))).sub(line.get(Math.max(k - 1, 0))).normalize();
			Vector3f nrm;
			if (prevNormal == null) {
				Vector3f up = Math.abs(dir.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
				nrm = new Vector3f(dir).cross(up).normalize();
			} else {
				// carry the frame along the tube so it does not twist
				nrm = new Vector3f(prevNormal).sub(new Vector3f(dir).mul(prevNormal.dot(dir))).normalize();
			}
			prevNormal = nrm;
			Vector3f bin = new Vector3f(dir).cross(nrm).normalize();
			float r = radius[Math.min(k, radius.length - 1)];
			for (int s = 0; s <= sides; s++) {
				double a = 2 * Math.PI * s / sides;
				Vector3f off = new Vector3f(nrm).mul((float) Math.cos(a)).add(new Vector3f(bin).mul((float) Math.sin(a)));
				b.vertex(new Vector3f(p).add(new Vector3f(off).mul(r)), off, k / (float) (n - 1), s / (float) sides);
			}
		}
		for (int k = 0; k < n - 1; k++) {
			for (int s = 0; s < sides; s++) {
				int a = base + k * (sides + 1) + s, c = a + sides + 1;
				b.quad(a, c, c + 1, a + 1);
			}
		}
	}

	/**
	 * An open pipe along -z from its mouth at z = 0 to z = -1: an outer wall of radius 1, an inner wall of radius
	 * {@code inner}, and the lip between them facing +z.
	 */
	public static void hollowTube(Mesh.Builder b, int sides, float inner) {
		for (int wall = 0; wall < 2; wall++) {
			float r = wall == 0 ? 1 : inner, sign = wall == 0 ? 1 : -1;
			int base = b.vertices();
			for (int k = 0; k < 2; k++) {
				for (int s = 0; s <= sides; s++) {
					double a = 2 * Math.PI * s / sides;
					float c = (float) Math.cos(a), sn = (float) Math.sin(a);
					b.vertex(c * r, sn * r, -k, c * sign, sn * sign, 0, s / (float) sides, k);
				}
			}
			for (int s = 0; s < sides; s++) {
				b.quad(base + s, base + sides + 1 + s, base + sides + 2 + s, base + s + 1);
			}
		}
		int base = b.vertices();
		for (int s = 0; s <= sides; s++) {
			double a = 2 * Math.PI * s / sides;
			float c = (float) Math.cos(a), sn = (float) Math.sin(a);
			b.vertex(c, sn, 0, 0, 0, 1, s / (float) sides, 0);
			b.vertex(c * inner, sn * inner, 0, 0, 0, 1, s / (float) sides, 1);
		}
		for (int s = 0; s < sides; s++) {
			b.quad(base + 2 * s, base + 2 * s + 1, base + 2 * s + 3, base + 2 * s + 2);
		}
	}

	/** A field of heights over x and z from -0.5 to 0.5: the height is given for each point. */
	public static void grid(Mesh.Builder b, int nx, int nz, HeightField h) {
		int base = b.vertices();
		float e = 0.5f / Math.max(nx, nz);
		for (int j = 0; j <= nz; j++) {
			for (int i = 0; i <= nx; i++) {
				float x = i / (float) nx - 0.5f, z = j / (float) nz - 0.5f;
				float y = h.at(x, z);
				Vector3f n = new Vector3f(h.at(x - e, z) - h.at(x + e, z), 2 * e, h.at(x, z - e) - h.at(x, z + e)).normalize();
				b.vertex(x, y, z, n.x, n.y, n.z, i / (float) nx, j / (float) nz);
			}
		}
		for (int j = 0; j < nz; j++) {
			for (int i = 0; i < nx; i++) {
				int a = base + j * (nx + 1) + i, c = a + nx + 1;
				b.quad(a, c, c + 1, a + 1);
			}
		}
	}

	@FunctionalInterface
	public interface HeightField {
		float at(float x, float z);
	}
}
