package pro.minehouse.riftclient.vision;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 7.0–10.4: inside the rock, a cave of stone over deepslate. A bright bar of sea lanterns behind glass slants
 * across the roof; the log posts of an old mineshaft sweep past close by, torches on them; the cave opens into a
 * chasm with a stone-brick bridge on log legs, glass along its sides and roots hanging under it to a pale floor,
 * and the push towards it speeds up.
 */
final class M03Cave extends Shot {
	private final Meshes m;
	private final Voxels cave = new Voxels("cave");

	static float cx(float z) {
		return (float) (3 * Math.sin(z * 0.06));
	}

	private static final float B = -50; // the bridge, along z
	private static final Cam CAM = new Cam(42,
			new float[] { 7.0f, cx(4) - 0.5f, 1.8f, 4f, cx(-8), 2.5f, -8f, 0 },
			new float[] { 7.5f, cx(0), 1.4f, 0f, cx(-10), 2.2f, -10f, 4 },
			new float[] { 8.0f, cx(-6) + 0.8f, 1.0f, -6f, cx(-16) + 0.5f, 1f, -16f, 10 },
			new float[] { 8.5f, cx(-16) + 1.1f, 0.8f, -16f, cx(-26) + 1f, 0.8f, -26f, 6 },
			new float[] { 9.0f, cx(-28) + 1.0f, 1.0f, -28f, cx(-38), 0.5f, -38f, 3 },
			new float[] { 9.5f, cx(-36), 1.5f, -36f, cx(B), 0.5f, B, 0 },
			new float[] { 10.0f, cx(-44), -1.5f, -44f, cx(B), -1.5f, B, 0 },
			new float[] { 10.4f, cx(-48.5f), -2.6f, -48.5f, cx(B), -2.2f, B - 2, 0 });

	M03Cave(Meshes m) {
		super(7.0f, 10.4f);
		this.m = m;
		for (int z = 10; z >= -66; z--) {
			float c = cx(z);
			boolean chasm = z < -40 && z > -62;
			for (int x = (int) c - 16; x <= (int) c + 16; x++) {
				for (int y = -14; y <= 14; y++) {
					float dx = x + 0.5f - c, dy = y + 0.5f - 0.8f;
					float wob = (float) Noise.fbm(x * 0.15, y * 0.15, z * 0.15, 3);
					float r;
					if (chasm) {
						// a tall wide room: floor far below, roof high
						dy = (y + 0.5f - (dy > 0 ? 2 : -1)) * (dy > 0 ? 0.9f : 0.75f);
						r = 11 + 3 * wob;
					} else {
						dy *= dy > 0 ? 1.2f : 1.6f;
						r = 3.6f + 2.2f * wob;
					}
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					if (d < r || d > r + 3) {
						continue;
					}
					cave.set(x, y, z, y < -2 + (int) (3 * wob) ? deep(x, y, z) : M02Block.block(x, y, z));
				}
			}
		}
		// the bright bar: sea lanterns behind a slanting row of glass across the roof
		for (int k = -6; k <= 6; k++) {
			int x = Math.round(cx(-5) + k), y = 3 + Math.round(k * 0.35f) + 1, z = -5 + Math.round(k * 0.4f);
			cave.set(x, y + 1, z, "sea_lantern");
			cave.set(x, y, z, "glass");
		}
		// the mineshaft: log posts each side, a plank beam over them, a torch on every other post
		for (int z = -12; z >= -36; z -= 6) {
			int c = Math.round(cx(z));
			for (int y = -1; y <= 2; y++) {
				cave.set(c - 2, y, z, "oak_log");
				cave.set(c + 2, y, z, "oak_log");
			}
			for (int x = c - 2; x <= c + 2; x++) {
				cave.set(x, 3, z, "oak_planks");
			}
			if ((z / 6) % 2 == 0) {
				cave.plant(c + 1, 1, z, "torch");
			} else {
				cave.plant(c - 1, 1, z, "torch");
			}
			for (int x = c - 2; x <= c + 2; x++) {
				for (int zz = z; zz > z - 6; zz--) {
					cave.set(x, -2, zz, "oak_planks");
				}
			}
		}
		// the bridge over the chasm: stone bricks on log legs, glass along both sides, roots under it
		int c = Math.round(cx(B));
		for (int x = c - 12; x <= c + 12; x++) {
			for (int z = (int) B - 1; z <= (int) B + 1; z++) {
				cave.set(x, 1, z, "stone_bricks");
			}
			cave.set(x, 2, (int) B - 1, (x & 1) == 0 ? "glass" : "stone_bricks");
			cave.set(x, 2, (int) B + 1, (x & 1) == 0 ? "glass" : "stone_bricks");
			if (Noise.hash(x, 5, 9) < 0.6) {
				int len = 1 + (int) (Noise.hash(x, 2, 3) * 4);
				for (int k = 0; k < len; k++) {
					cave.plant(x, -k, (int) B + (x % 3 == 0 ? -1 : 0), "hanging_roots");
				}
			}
		}
		for (int x = c - 12; x <= c + 12; x += 6) {
			for (int y = -12; y <= 0; y++) {
				cave.set(x, y, (int) B, "oak_log");
			}
			cave.plant(x, -1, (int) B + 1, "lantern");
		}
		// the pale floor of the chasm
		for (int x = c - 14; x <= c + 14; x++) {
			for (int z = -60; z <= -42; z++) {
				int y = -13 + (int) (2 * Noise.fbm(x * 0.2, 0, z * 0.2, 2));
				cave.set(x, y, z, Noise.hash(x, y, z) < 0.7 ? "calcite" : "diorite");
			}
		}
		cave.build();
	}

	static String deep(int x, int y, int z) {
		double ore = Noise.hash(x, y, z * 17 + 1);
		return ore < 0.012 ? "deepslate_diamond_ore" : ore < 0.04 ? "deepslate_redstone_ore" : ore < 0.07 ? "deepslate_iron_ore" : ore < 0.3 ? "cobbled_deepslate" : "deepslate";
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 300);
		f.light(-0.4f, 0.8f, 0.4f, 0.6f);
		f.background(0.0f);
		f.fog(0.0f, 0.06f);
		f.bloom(0.7f, 0.6f);
		f.exposure(1.25f);
		f.colour(0.3f);
		f.focus(dist, 3, 10);
		cave.draw(f, m, null, 0.5f, 1.4f, 1);
	}

	@Override
	public void close() {
		cave.close();
	}
}
