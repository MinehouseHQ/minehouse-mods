package pro.minehouse.riftclient.vision;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 5.4–7.0: the pixels were the face of a block. Right up against the diamond ore, soft; a fast pull-back with a
 * roll shows it set in a wall of stone with veins of ore, the rough wall of a cave.
 */
final class M02Block extends Shot {
	private final Meshes m;
	private final Voxels wall = new Voxels("ore wall");

	private static final Cam CAM = new Cam(40,
			new float[] { 5.4f, 0.55f, 0.45f, 1.5f, 0.5f, 0.5f, -4f, 0 },
			new float[] { 5.6f, 0.5f, 0.4f, 2.4f, 0.5f, 0.5f, -4f, -4 },
			new float[] { 5.9f, 0.2f, 0.2f, 6f, 0.5f, 0.4f, -4f, -14 },
			new float[] { 6.4f, -1f, -1f, 16f, 0.5f, 0f, -4f, -26 },
			new float[] { 7.0f, -3f, -5f, 30f, 1f, -1f, -4f, -34 });

	M02Block(Meshes m) {
		super(5.4f, 7.0f);
		this.m = m;
		for (int x = -45; x <= 45; x++) {
			for (int y = -28; y <= 28; y++) {
				double d = Math.sqrt(x * x + y * y);
				// the wall bulges and dips; the ore block stands out a little in the middle
				// flat round the ore, so it sits flush in the wall
				double rough = Math.min(1, Math.max(0, (d - 2.5) / 6));
				int h = (int) Math.floor(rough * (4.5 * Noise.fbm(x * 0.09, y * 0.09, 1.7, 4) - 2.2 + 0.02 * d));
				for (int z = h - 3; z <= h; z++) {
					wall.set(x, y, z, block(x, y, z));
				}
			}
		}
		wall.set(0, 0, 0, "diamond_ore");
		wall.set(0, 0, 1, null);
		wall.build();
	}

	/** What the rock is at a place: stone with patches of andesite, diorite, granite, tuff, and veins of ore. */
	static String block(int x, int y, int z) {
		double a = Noise.fbm(x * 0.12, y * 0.12, z * 0.12 + 5, 3);
		double b = Noise.fbm(x * 0.2 + 40, y * 0.2, z * 0.2, 2);
		double ore = Noise.hash(x, y, z * 31 + 3);
		if (y < -16 + (int) (4 * a)) {
			return ore < 0.02 ? "deepslate_diamond_ore" : ore < 0.06 ? "deepslate_iron_ore" : "deepslate";
		}
		if (ore < 0.012 && b > 0.5) {
			return "diamond_ore";
		}
		if (b > 0.66) {
			return ore < 0.5 ? "coal_ore" : "stone";
		}
		if (b < 0.3 && ore < 0.35) {
			return "iron_ore";
		}
		if (a > 0.66) {
			return "andesite";
		}
		if (a < 0.28) {
			return "diorite";
		}
		if (a > 0.6 && b < 0.4) {
			return "granite";
		}
		if (a < 0.33 && b > 0.55) {
			return "tuff";
		}
		return ore > 0.985 ? "copper_ore" : "stone";
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 400);
		f.light(-0.6f, 0.5f, 0.6f, 0.6f);
		f.background(0.0f);
		f.fog(0.0f, 0.012f);
		f.bloom(0.55f, 0.7f);
		f.exposure(1.15f);
		f.colour(0.3f);
		f.focus(dist, 5, 16);
		wall.draw(f, m, null, 0.7f, 1, 1);
	}

	@Override
	public void close() {
		wall.close();
	}
}
