package pro.minehouse.riftclient;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * One broken rift ({@link Shards}) seen from one eye this frame, camera-relative: where every piece
 * is, and the place behind the rift cut to what each piece lets through.
 *
 * <p>The same trick as the old oval window (see {@link PortalWindows}), once per piece: each face of
 * the room is clipped on the CPU to the far side of a plane through the rift's middle that faces the
 * eye (so you look INTO the place from whichever side you stand), to the far side of the piece's
 * own plane, and to the cone from the eye through one triangle of the piece; then it is squeezed
 * along the rays from the eye into a thin slab just behind the piece. So every piece is a little
 * window with its own parallax, pieces nearer the eye hide those behind, and the world behind the
 * rift does not show through. Only the game's own entity shaders are used (they work with Sodium).
 */
final class ShardCut {
	/** Floats per vertex while clipping: x, y, z, u, v (as in PortalWindows). */
	static final int F = 5;
	private static final int FULL = LightCoordsUtil.FULL_BRIGHT;
	/** Glass colours: pale rim, the violet of the edges, the deep violet inside. */
	static final int RIM = 0xFFF1E4FF;
	private static final int EDGE_LIGHT = 0xFFC9A2FF;
	private static final int EDGE_DARK = 0xFF8A5AD8;
	private static final int VIOLET = 0xA25CFF;

	/** The rift's middle, camera-relative, and its radius in blocks. */
	final float cx, cy, cz, radius;
	/** The plane through the middle facing the eye (flat), and the eye's distance to it. */
	final float nx, nz, d0;
	/** False: the eye is in the rift's middle, nothing is seen through it. */
	final boolean through;
	final int count;
	/** Per piece: its corners (camera-relative, x y z) then its centre. */
	final float[][] corners;
	final int[] n;
	/** Per piece: its plane's normal away from the eye, the eye's distance to the plane, the slab. */
	final float[] mx, my, mz, e0, alpha, beta, slab;
	final boolean[] shown;
	/** Per piece and triangle (centre, k, k + 1): the three planes through the eye, inward, and its box on the view. */
	final float[][] sides;
	final float[][] box;
	final float[] shardBox;
	final float thickness;
	/** For the flash: seconds since it opened, and now. */
	final float open, time;
	private float boxX0 = Float.MAX_VALUE, boxX1 = -Float.MAX_VALUE, boxY0 = Float.MAX_VALUE, boxY1 = -Float.MAX_VALUE;
	private final float[] bufA, bufB, bufC, bufD;

	/**
	 * @param ccx the rift's middle, camera-relative
	 * @param open seconds since it opened, as a share of {@link Shards#OPEN_SECONDS}
	 */
	ShardCut(List<Shards.Shard> shards, float ccx, float ccy, float ccz, float radius, float open, float time, boolean still) {
		this.cx = ccx;
		this.cy = ccy;
		this.cz = ccz;
		this.radius = radius;
		this.open = open;
		this.time = time;
		float flat = (float) Math.sqrt(ccx * ccx + ccz * ccz);
		this.through = flat > 0.35f;
		this.nx = flat > 1e-4f ? ccx / flat : 0;
		this.nz = flat > 1e-4f ? ccz / flat : 1;
		this.d0 = flat;
		this.thickness = Math.max(0.035f, radius * 0.025f);
		this.count = shards.size();
		corners = new float[count][];
		n = new int[count];
		mx = new float[count];
		my = new float[count];
		mz = new float[count];
		e0 = new float[count];
		alpha = new float[count];
		beta = new float[count];
		slab = new float[count];
		shown = new boolean[count];
		sides = new float[count][];
		box = new float[count][];
		shardBox = new float[count * 4];
		int maxN = Shards.maxCorners(shards);
		float[] tmp = new float[(maxN + 1) * 3];
		for (int i = 0; i < count; i++) {
			Shards.Shard s = shards.get(i);
			if (!Shards.place(s, open, time, still, tmp)) {
				continue;
			}
			int c = s.corners();
			float[] p = new float[(c + 1) * 3];
			for (int k = 0; k <= c; k++) {
				p[k * 3] = ccx + tmp[k * 3] * radius;
				p[k * 3 + 1] = ccy + tmp[k * 3 + 1] * radius;
				p[k * 3 + 2] = ccz + tmp[k * 3 + 2] * radius;
			}
			corners[i] = p;
			n[i] = c;
			shown[i] = true;
			// The plane: normal from two edges out of the centre (Newell would do too; these are flat).
			float ax = p[0] - p[c * 3], ay = p[1] - p[c * 3 + 1], az = p[2] - p[c * 3 + 2];
			int h = c / 2;
			float bx = p[h * 3] - p[c * 3], by = p[h * 3 + 1] - p[c * 3 + 1], bz = p[h * 3 + 2] - p[c * 3 + 2];
			float qx = ay * bz - az * by, qy = az * bx - ax * bz, qz = ax * by - ay * bx;
			float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
			if (ql < 1e-6f) {
				shown[i] = false;
				continue;
			}
			qx /= ql;
			qy /= ql;
			qz /= ql;
			float d = qx * p[c * 3] + qy * p[c * 3 + 1] + qz * p[c * 3 + 2];
			if (d < 0) {
				qx = -qx;
				qy = -qy;
				qz = -qz;
				d = -d;
			}
			mx[i] = qx;
			my[i] = qy;
			mz[i] = qz;
			e0[i] = d;
			slab[i] = 0.03f + 0.01f * d;
			alpha[i] = 1f / (d + slab[i]);
			beta[i] = slab[i] / (d + slab[i]);
			// Triangles of the fan, for the cut: only when the piece is not seen edge-on from right by it.
			if (!through || d < 0.03f) {
				continue;
			}
			float[] sd = new float[c * 9];
			float[] bx4 = new float[c * 4];
			float sx0 = Float.MAX_VALUE, sx1 = -Float.MAX_VALUE, sy0 = Float.MAX_VALUE, sy1 = -Float.MAX_VALUE;
			for (int k = 0; k < c; k++) {
				int j = (k + 1) % c;
				int o = c * 3, a = k * 3, b = j * 3;
				side(sd, k * 9, p, o, p, a, p, b);
				side(sd, k * 9 + 3, p, a, p, b, p, o);
				side(sd, k * 9 + 6, p, b, p, o, p, a);
				float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
				boolean ok = true;
				for (int q : new int[] { o, a, b }) {
					float df = p[q] * nx + p[q + 2] * nz;
					if (df < 0.05f) {
						ok = false;
						break;
					}
					float u = (p[q] * -nz + p[q + 2] * nx) / df, v = p[q + 1] / df;
					x0 = Math.min(x0, u);
					x1 = Math.max(x1, u);
					y0 = Math.min(y0, v);
					y1 = Math.max(y1, v);
				}
				if (!ok) {
					x0 = y0 = -Float.MAX_VALUE;
					x1 = y1 = Float.MAX_VALUE;
				}
				bx4[k * 4] = x0;
				bx4[k * 4 + 1] = x1;
				bx4[k * 4 + 2] = y0;
				bx4[k * 4 + 3] = y1;
				sx0 = Math.min(sx0, x0);
				sx1 = Math.max(sx1, x1);
				sy0 = Math.min(sy0, y0);
				sy1 = Math.max(sy1, y1);
			}
			sides[i] = sd;
			box[i] = bx4;
			shardBox[i * 4] = sx0;
			shardBox[i * 4 + 1] = sx1;
			shardBox[i * 4 + 2] = sy0;
			shardBox[i * 4 + 3] = sy1;
			boxX0 = Math.min(boxX0, sx0);
			boxX1 = Math.max(boxX1, sx1);
			boxY0 = Math.min(boxY0, sy0);
			boxY1 = Math.max(boxY1, sy1);
		}
		int cap = (maxN + 16) * 2 * F + 64 * F;
		bufA = new float[cap];
		bufB = new float[cap];
		bufC = new float[cap];
		bufD = new float[cap];
	}

	/** The plane through the eye and the points at a and b, facing the point at c (normal into out at o). */
	private static void side(float[] out, int o, float[] pa, int a, float[] pb, int b, float[] pc, int c) {
		float ax = pa[a], ay = pa[a + 1], az = pa[a + 2];
		float bx = pb[b], by = pb[b + 1], bz = pb[b + 2];
		float x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
		if (x * pc[c] + y * pc[c + 1] + z * pc[c + 2] < 0) {
			x = -x;
			y = -y;
			z = -z;
		}
		out[o] = x;
		out[o + 1] = y;
		out[o + 2] = z;
	}

	// ------------------------------------------------------------------ the place behind

	/**
	 * The faces of the room that the pieces let through, each squeezed behind its piece.
	 * {@code ox, oy, oz}: from the room's coordinates to camera-relative ones.
	 */
	void room(PoseStack.Pose p, VertexConsumer vc, List<PortalWindows.Quad> quads, float ox, float oy, float oz) {
		if (!through || boxX0 > boxX1) {
			return;
		}
		float rx = -nz, rz = nx;
		for (PortalWindows.Quad q : quads) {
			float[] src = q.xyzuv();
			for (int i = 0; i < 4; i++) {
				bufA[i * F] = src[i * F] + ox;
				bufA[i * F + 1] = src[i * F + 1] + oy;
				bufA[i * F + 2] = src[i * F + 2] + oz;
				bufA[i * F + 3] = src[i * F + 3];
				bufA[i * F + 4] = src[i * F + 4];
			}
			int m = clip(bufA, 4, bufB, nx, 0, nz, d0);
			if (m < 3) {
				continue;
			}
			float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
			for (int i = 0; i < m; i++) {
				float x = bufB[i * F], y = bufB[i * F + 1], z = bufB[i * F + 2];
				float df = Math.max(x * nx + z * nz, 1e-4f);
				float u = (x * rx + z * rz) / df, v = y / df;
				x0 = Math.min(x0, u);
				x1 = Math.max(x1, u);
				y0 = Math.min(y0, v);
				y1 = Math.max(y1, v);
			}
			if (x1 < boxX0 || x0 > boxX1 || y1 < boxY0 || y0 > boxY1) {
				continue;
			}
			for (int s = 0; s < count; s++) {
				float[] sd = sides[s];
				if (sd == null || x1 < shardBox[s * 4] || x0 > shardBox[s * 4 + 1] || y1 < shardBox[s * 4 + 2] || y0 > shardBox[s * 4 + 3]) {
					continue;
				}
				float[] bx = box[s];
				for (int k = 0; k < n[s]; k++) {
					if (x1 < bx[k * 4] || x0 > bx[k * 4 + 1] || y1 < bx[k * 4 + 2] || y0 > bx[k * 4 + 3]) {
						continue;
					}
					int c = clip(bufB, m, bufC, mx[s], my[s], mz[s], e0[s]);
					float[] cur = bufC, other = bufD;
					for (int t = 0; t < 3 && c >= 3; t++) {
						c = clip(cur, c, other, sd[k * 9 + t * 3], sd[k * 9 + t * 3 + 1], sd[k * 9 + t * 3 + 2], 0);
						float[] tt = cur;
						cur = other;
						other = tt;
					}
					if (c >= 3) {
						squeezeAndEmit(p, vc, cur, c, s, q);
					}
				}
			}
		}
	}

	private void squeezeAndEmit(PoseStack.Pose p, VertexConsumer vc, float[] cur, int c, int s, PortalWindows.Quad q) {
		for (int i = 0; i < c; i++) {
			float d = cur[i * F] * mx[s] + cur[i * F + 1] * my[s] + cur[i * F + 2] * mz[s];
			float k = 1f / (alpha[s] * d + beta[s]);
			cur[i * F] *= k;
			cur[i * F + 1] *= k;
			cur[i * F + 2] *= k;
		}
		for (int i = 1; i + 1 < c; i++) {
			emit(p, vc, cur, 0, q);
			emit(p, vc, cur, i, q);
			emit(p, vc, cur, i + 1, q);
			emit(p, vc, cur, i + 1, q);
		}
	}

	private static void emit(PoseStack.Pose p, VertexConsumer vc, float[] buf, int i, PortalWindows.Quad q) {
		vertex(p, vc, buf[i * F], buf[i * F + 1], buf[i * F + 2], buf[i * F + 3], buf[i * F + 4], q.color(), q.light());
	}

	/** Sutherland-Hodgman: keeps the part of the polygon where (x, y, z) . (px, py, pz) >= d. */
	static int clip(float[] in, int n, float[] out, float px, float py, float pz, float d) {
		int m = 0;
		int max = out.length / F;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float si = in[i * F] * px + in[i * F + 1] * py + in[i * F + 2] * pz - d;
			float sj = in[j * F] * px + in[j * F + 1] * py + in[j * F + 2] * pz - d;
			if (si >= 0 && m < max) {
				System.arraycopy(in, i * F, out, m * F, F);
				m++;
			}
			if ((si >= 0) != (sj >= 0) && m < max) {
				float t = si / (si - sj);
				for (int c = 0; c < F; c++) {
					out[m * F + c] = in[i * F + c] + (in[j * F + c] - in[i * F + c]) * t;
				}
				m++;
			}
		}
		return m;
	}

	// ------------------------------------------------------------------ the glass

	/**
	 * What is seen through a piece where the place has nothing (or, for a rift that leads nowhere
	 * this client knows, everything seen through it): at the back of the slab, deep violet, lighter
	 * towards the rift's middle when {@code glowing}.
	 */
	void back(PoseStack.Pose p, VertexConsumer vc, float u, float v, int color, boolean glowing) {
		for (int s = 0; s < count; s++) {
			if (!shown[s] || e0[s] < 0.03f) {
				continue;
			}
			float[] c = corners[s];
			int k0 = n[s];
			float t = (e0[s] + slab[s] * 0.97f) / e0[s];
			for (int k = 0; k < k0; k++) {
				int j = (k + 1) % k0;
				int ca = glowing ? glow(color, c, k0) : color, cb = glowing ? glow(color, c, k) : color, cc = glowing ? glow(color, c, j) : color;
				tri(p, vc, u, v, c, k0, ca, c, k, cb, c, j, cc, t, true);
			}
		}
	}

	private int glow(int base, float[] c, int k) {
		float dx = c[k * 3] - cx, dy = c[k * 3 + 1] - cy, dz = c[k * 3 + 2] - cz;
		float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;
		float g = Math.max(0, 1 - r / 0.9f);
		return mix(base, 0xFF6A2FC0, g * 0.8f);
	}

	/** The thickness of every piece: its edges as walls, lit a little by which way they face. Solid. */
	void edges(PoseStack.Pose p, VertexConsumer vc, float u, float v) {
		float th = thickness;
		for (int s = 0; s < count; s++) {
			if (!shown[s]) {
				continue;
			}
			float[] c = corners[s];
			int k0 = n[s];
			float ox = mx[s] * th, oy = my[s] * th, oz = mz[s] * th;
			for (int k = 0; k < k0; k++) {
				int j = (k + 1) % k0;
				float ex = c[j * 3] - c[k * 3], ez = c[j * 3 + 2] - c[k * 3 + 2];
				int col = (Math.abs(ex) > Math.abs(ez)) == (k % 2 == 0) ? EDGE_LIGHT : EDGE_DARK;
				float[] q = { c[k * 3], c[k * 3 + 1], c[k * 3 + 2], c[j * 3], c[j * 3 + 1], c[j * 3 + 2],
						c[j * 3] + ox, c[j * 3 + 1] + oy, c[j * 3 + 2] + oz, c[k * 3] + ox, c[k * 3 + 1] + oy, c[k * 3 + 2] + oz };
				quad(p, vc, u, v, q, col);
			}
		}
	}

	/**
	 * Over the room: a violet sheen on every piece (stronger near the middle and where it is seen
	 * at a slant), its pale rim, a few cracks, then the flash and its rays. Translucent.
	 */
	void sheen(PoseStack.Pose p, VertexConsumer vc, float u, float v) {
		float w = Math.max(0.02f, radius * 0.012f);
		for (int s = 0; s < count; s++) {
			if (!shown[s] || e0[s] < 0.03f) {
				continue;
			}
			float[] c = corners[s];
			int k0 = n[s];
			float t = (e0[s] - 0.004f) / e0[s];
			float len = (float) Math.sqrt(c[k0 * 3] * c[k0 * 3] + c[k0 * 3 + 1] * c[k0 * 3 + 1] + c[k0 * 3 + 2] * c[k0 * 3 + 2]);
			float slant = 1 - Math.min(1, e0[s] / Math.max(len, 1e-3f));
			for (int k = 0; k < k0; k++) {
				int j = (k + 1) % k0;
				tri(p, vc, u, v, c, k0, sheenColor(c, k0, slant), c, k, sheenColor(c, k, slant), c, j, sheenColor(c, j, slant), t, false);
			}
			float t2 = (e0[s] - 0.006f) / e0[s];
			// The rim: a strip inside each edge, towards the centre.
			for (int k = 0; k < k0; k++) {
				int j = (k + 1) % k0;
				float[] a = inset(c, k, k0, w), b = inset(c, j, k0, w);
				float[] q = { c[k * 3] * t2, c[k * 3 + 1] * t2, c[k * 3 + 2] * t2, c[j * 3] * t2, c[j * 3 + 1] * t2, c[j * 3 + 2] * t2,
						b[0] * t2, b[1] * t2, b[2] * t2, a[0] * t2, a[1] * t2, a[2] * t2 };
				quad(p, vc, u, v, q, RIM);
			}
			// Cracks: from the centre to every third corner, thin and pale.
			// A layer nearer than the rim, so where they cross they do not fight.
			float t3 = (e0[s] - 0.008f) / e0[s];
			for (int k = 1; k < k0; k += 3) {
				float[] q = strip(c[k0 * 3], c[k0 * 3 + 1], c[k0 * 3 + 2], c[k * 3], c[k * 3 + 1], c[k * 3 + 2], w * 0.45f, mx[s], my[s], mz[s]);
				for (int i = 0; i < 12; i++) {
					q[i] *= t3;
				}
				quad(p, vc, u, v, q, 0x99F1E4FF);
			}
		}
		flash(p, vc, u, v);
	}

	private int sheenColor(float[] c, int k, float slant) {
		float dx = c[k * 3] - cx, dy = c[k * 3 + 1] - cy, dz = c[k * 3 + 2] - cz;
		float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;
		float a = 0.10f + 0.45f * Math.max(0, 1 - r / 0.55f) + 0.35f * slant * slant;
		return (int) (Math.min(0.85f, a) * 255) << 24 | VIOLET;
	}

	private static float[] inset(float[] c, int k, int centre, float w) {
		float dx = c[centre * 3] - c[k * 3], dy = c[centre * 3 + 1] - c[k * 3 + 1], dz = c[centre * 3 + 2] - c[k * 3 + 2];
		float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float f = l < 1e-4f ? 0 : Math.min(0.45f, w / l);
		return new float[] { c[k * 3] + dx * f, c[k * 3 + 1] + dy * f, c[k * 3 + 2] + dz * f };
	}

	/** A strip of width w from a to b, lying in the plane with normal m. */
	private static float[] strip(float ax, float ay, float az, float bx, float by, float bz, float w, float mx, float my, float mz) {
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
		float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (l < 1e-6f) {
			l = 1;
		}
		sx *= w / 2 / l;
		sy *= w / 2 / l;
		sz *= w / 2 / l;
		return new float[] { ax - sx, ay - sy, az - sz, bx - sx, by - sy, bz - sz, bx + sx, by + sy, bz + sz, ax + sx, ay + sy, az + sz };
	}

	/**
	 * The flash in the middle, white fading to violet, and its rays, facing the eye. Drawn nearer
	 * the eye than it is (same size on the screen), so the pieces in front of the middle do not hide
	 * it, as the picture has it over everything.
	 */
	private void flash(PoseStack.Pose p, VertexConsumer vc, float u, float v) {
		float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		if (dist < 0.5f) {
			return;
		}
		float pull = Math.max(0.3f, (dist - radius * 0.9f) / dist);
		float fx = cx / dist, fy = cy / dist, fz = cz / dist;
		// Across the view: right = f x up, up = right x f.
		float rx = -fz, rz = fx, rl = (float) Math.sqrt(rx * rx + rz * rz);
		if (rl < 1e-3f) {
			rx = 1;
			rz = 0;
			rl = 1;
		}
		rx /= rl;
		rz /= rl;
		float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
		float burst = (float) Math.exp(-open * 5) * 2.2f;
		float grow = Math.min(1, open * 4);
		float pulse = 1 + 0.06f * (float) Math.sin(time * 3.1);
		float size = radius * (0.32f + burst * 0.35f) * pulse * grow;
		float ox = cx * pull, oy = cy * pull, oz = cz * pull;
		size *= pull;
		int segs = 24;
		for (int i = 0; i < segs; i++) {
			double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			// Two rings: a hot white core, then violet fading out. Each on its own layer, nearest last.
			ring(p, vc, u, v, ox, oy, oz, rx, rz, ux, uy, uz, c0, s0, c1, s1, size * 0.35f, size, 0xC0E9D4FF, 0x00A25CFF, layer(23));
			ring(p, vc, u, v, ox, oy, oz, rx, rz, ux, uy, uz, c0, s0, c1, s1, 0, size * 0.35f, 0xF0FFFFFF, 0xC0E9D4FF, layer(24));
		}
		// Rays, turning slowly.
		java.util.Random r = new java.util.Random(11);
		float spin = time * 0.15f;
		for (int i = 0; i < 22; i++) {
			double a = i * Math.PI * 2 / 22 + (r.nextDouble() - 0.5) * 0.2 + spin;
			float len = radius * pull * (0.55f + r.nextFloat() * 0.55f) * grow * (1 + burst * 0.5f), w = radius * pull * (0.008f + r.nextFloat() * 0.018f);
			float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
			float px = -dy, py = dx;
			float[] q = {
					ox + (rx * px + 0) * w + ux * py * w, oy + uy * py * w, oz + (rz * px) * w + uz * py * w,
					ox + rx * dx * len + ux * dy * len, oy + uy * dy * len, oz + rz * dx * len + uz * dy * len,
					ox + rx * dx * len + ux * dy * len, oy + uy * dy * len, oz + rz * dx * len + uz * dy * len,
					ox - rx * px * w - ux * py * w, oy - uy * py * w, oz - rz * px * w - uz * py * w };
			int[] col = { 0xD0F0DCFF, 0x00A25CFF, 0x00A25CFF, 0xD0F0DCFF };
			float lk = layer(i);
			for (int k = 0; k < 12; k++) {
				q[k] *= lk;
			}
			for (int k = 0; k < 4; k++) {
				vertex(p, vc, q[k * 3], q[k * 3 + 1], q[k * 3 + 2], u, v, col[k], FULL);
			}
			for (int k = 3; k >= 0; k--) {
				vertex(p, vc, q[k * 3], q[k * 3 + 1], q[k * 3 + 2], u, v, col[k], FULL);
			}
		}
	}

	/**
	 * The flash is many flat pieces in one plane facing the eye; drawn all at the same depth they
	 * fight over who is in front (z-fighting in the middle, Timur 28.11). So each ray and ring gets
	 * its own layer a little nearer the eye: scaled towards the eye, it covers the same pixels.
	 */
	private static float layer(int i) {
		return 1 - 0.0015f * (i + 1);
	}

	private static void ring(PoseStack.Pose p, VertexConsumer vc, float u, float v, float ox, float oy, float oz, float rx, float rz, float ux,
			float uy, float uz, float c0, float s0, float c1, float s1, float in, float out, int inner, int outer, float lk) {
		ox *= lk;
		oy *= lk;
		oz *= lk;
		in *= lk;
		out *= lk;
		float[][] pts = {
				{ ox + (rx * c0 + 0) * in + ux * s0 * in, oy + uy * s0 * in, oz + rz * c0 * in + uz * s0 * in },
				{ ox + rx * c0 * out + ux * s0 * out, oy + uy * s0 * out, oz + rz * c0 * out + uz * s0 * out },
				{ ox + rx * c1 * out + ux * s1 * out, oy + uy * s1 * out, oz + rz * c1 * out + uz * s1 * out },
				{ ox + rx * c1 * in + ux * s1 * in, oy + uy * s1 * in, oz + rz * c1 * in + uz * s1 * in } };
		int[] col = { inner, outer, outer, inner };
		for (int k = 0; k < 4; k++) {
			vertex(p, vc, pts[k][0], pts[k][1], pts[k][2], u, v, col[k], FULL);
		}
		for (int k = 3; k >= 0; k--) {
			vertex(p, vc, pts[k][0], pts[k][1], pts[k][2], u, v, col[k], FULL);
		}
	}

	/** A triangle (as a quad with the last corner twice), scaled along the rays by t, seen from both sides when asked. */
	private static void tri(PoseStack.Pose p, VertexConsumer vc, float u, float v, float[] pa, int a, int ca, float[] pb, int b, int cb, float[] pc,
			int c, int cc, float t, boolean both) {
		vertex(p, vc, pa[a * 3] * t, pa[a * 3 + 1] * t, pa[a * 3 + 2] * t, u, v, ca, FULL);
		vertex(p, vc, pb[b * 3] * t, pb[b * 3 + 1] * t, pb[b * 3 + 2] * t, u, v, cb, FULL);
		vertex(p, vc, pc[c * 3] * t, pc[c * 3 + 1] * t, pc[c * 3 + 2] * t, u, v, cc, FULL);
		vertex(p, vc, pc[c * 3] * t, pc[c * 3 + 1] * t, pc[c * 3 + 2] * t, u, v, cc, FULL);
		if (both) {
			vertex(p, vc, pc[c * 3] * t, pc[c * 3 + 1] * t, pc[c * 3 + 2] * t, u, v, cc, FULL);
			vertex(p, vc, pc[c * 3] * t, pc[c * 3 + 1] * t, pc[c * 3 + 2] * t, u, v, cc, FULL);
			vertex(p, vc, pb[b * 3] * t, pb[b * 3 + 1] * t, pb[b * 3 + 2] * t, u, v, cb, FULL);
			vertex(p, vc, pa[a * 3] * t, pa[a * 3 + 1] * t, pa[a * 3 + 2] * t, u, v, ca, FULL);
		} else {
			vertex(p, vc, pc[c * 3] * t, pc[c * 3 + 1] * t, pc[c * 3 + 2] * t, u, v, cc, FULL);
			vertex(p, vc, pc[c * 3] * t, pc[c * 3 + 1] * t, pc[c * 3 + 2] * t, u, v, cc, FULL);
			vertex(p, vc, pb[b * 3] * t, pb[b * 3 + 1] * t, pb[b * 3 + 2] * t, u, v, cb, FULL);
			vertex(p, vc, pa[a * 3] * t, pa[a * 3 + 1] * t, pa[a * 3 + 2] * t, u, v, ca, FULL);
		}
	}

	/** A flat quad of four corners (x y z each), seen from either side. */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, float u, float v, float[] q, int color) {
		for (int k = 0; k < 4; k++) {
			vertex(p, vc, q[k * 3], q[k * 3 + 1], q[k * 3 + 2], u, v, color, FULL);
		}
		for (int k = 3; k >= 0; k--) {
			vertex(p, vc, q[k * 3], q[k * 3 + 1], q[k * 3 + 2], u, v, color, FULL);
		}
	}

	static int mix(int a, int b, float t) {
		int r = 0;
		for (int sh = 0; sh <= 24; sh += 8) {
			int x = a >>> sh & 255, y = b >>> sh & 255;
			r |= Math.round(x + (y - x) * t) << sh;
		}
		return r;
	}

	static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int color, int light) {
		vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
	}
}
