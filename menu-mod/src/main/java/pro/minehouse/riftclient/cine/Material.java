package pro.minehouse.riftclient.cine;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** How a surface looks (see core/cine.fsh). The numbers mean different things for each kind. */
public final class Material {
	public static final int LIT = 0, EMISSIVE = 1, SEM = 2, GLOW = 3, PLANET = 4, STAR = 5, NERVE = 6, GLASS = 7, CLOUD = 8,
			ATMOS = 9, DUST = 10, PLASMA = 11, SILHOUETTE = 12, FOAM = 13, BUBBLE = 14,
			/** A block of the game: its texture rectangle in the instance (see {@link BlockTex}). */
			BLOCK = 15,
			/** A cube of one colour (rgb in the instance, a = how much it glows): a pixel of a texture. */
			PIXEL = 16;

	final Matrix4f params = new Matrix4f().zero();
	final Vector4f color = new Vector4f(1, 1, 1, 1);
	final boolean light;

	private Material(int kind, boolean light) {
		this.light = light;
		params.m00(kind);
		if (light) {
			params.m32(1);
		}
	}

	public static Material solid(int kind) {
		return new Material(kind, false);
	}

	/** Light that adds up and is drawn after the solid things. */
	public static Material light(int kind) {
		return new Material(kind, true);
	}

	public Material color(float r, float g, float b) {
		color.set(r, g, b, 1);
		return this;
	}

	/** Darker or brighter by this much, keeping the colour. */
	public Material scale(float v) {
		color.mul(v, v, v, 1);
		return this;
	}

	public Material grey(float v) {
		return color(v, v, v);
	}

	/** The three numbers after the kind: [0].yzw. */
	public Material p(float a, float b, float c) {
		params.m01(a).m02(b).m03(c);
		return this;
	}

	/** The next four numbers: [1]. */
	public Material q(float a, float b, float c, float d) {
		params.m10(a).m11(b).m12(c).m13(d);
		return this;
	}

	/** The four numbers after those: [2]. */
	public Material r(float a, float b, float c, float d) {
		params.m20(a).m21(b).m22(c).m23(d);
		return this;
	}

	/** A block with another texture on top (logs, grass): [1] is the top, [2] the bottom (or the top again). */
	public Material faces(float[] top, float[] bottom) {
		q(top[0], top[1], top[2], top[3]);
		float[] b = bottom == null ? top : bottom;
		return r(b[0], b[1], b[2], b[3]);
	}

	/** A {@link #BLOCK} box this many units per block: its texture repeats along it (one box for a whole slab). */
	public Material tile(float unitsPerBlock) {
		params.m30(unitsPerBlock);
		return this;
	}

	/** Always faces the camera: the mesh should be {@link Shapes#quad}. */
	public Material sprite() {
		params.m33(1);
		return this;
	}

	public Material copy() {
		Material m = new Material(0, light);
		m.params.set(params);
		m.color.set(color);
		return m;
	}
}
