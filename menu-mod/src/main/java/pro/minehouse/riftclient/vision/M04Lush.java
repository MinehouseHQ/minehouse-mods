package pro.minehouse.riftclient.vision;

import java.util.Random;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 10.4–14.3: under the bridge, through a burrow of roots into a lush cave: glow berry vines hang from a mossy roof,
 * one wipes across the lens, spore blossoms open like trumpets, azalea bushes on the moss; then a pull-back and rise
 * till the vines are a web of little lights in the dark.
 */
final class M04Lush extends Shot {
	private final Meshes m;
	private final Voxels lush = new Voxels("lush cave");

	private static final Cam CAM = new Cam(46,
			new float[] { 10.4f, 0f, 0.5f, 30f, 0.5f, 0.6f, 20f, 0 },
			new float[] { 11.0f, 0.3f, 0.4f, 23f, 0.8f, 0.4f, 14f, -8 },
			new float[] { 11.7f, -0.2f, 0.2f, 15f, 0.6f, 0f, 6f, -6 },
			new float[] { 12.4f, -0.6f, -0.4f, 8f, 0.4f, -0.6f, -2f, 4 },
			new float[] { 13.2f, 0.4f, 1.5f, 2f, 1.4f, -1f, -8f, 10 },
			new float[] { 13.8f, 1.2f, 4f, 8f, 0.8f, -2f, -12f, 12 },
			new float[] { 14.3f, 1.8f, 6.5f, 17f, 0.6f, -3f, -16f, 14 });

	M04Lush(Meshes m) {
		super(10.4f, 14.3f);
		this.m = m;
		Random r = new Random(4);
		// the burrow: a low tube of rooted dirt and moss from the bridge into the cave
		for (int z = 34; z >= 16; z--) {
			for (int x = -6; x <= 6; x++) {
				for (int y = -5; y <= 6; y++) {
					float dx = x + 0.5f, dy = (y + 0.5f) * 1.3f;
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					float rr = 2.6f + 1.2f * (float) Noise.fbm(x * 0.2, y * 0.2, z * 0.2, 2);
					if (d >= rr && d < rr + 2.5f) {
						lush.set(x, y, z, y > 0 ? (Noise.hash(x, y, z) < 0.5 ? "rooted_dirt" : "moss_block") : "moss_block");
					}
				}
			}
			for (int x = -2; x <= 2; x++) {
				if (Noise.hash(x, 1, z) < 0.35) {
					lush.plant(x, 2, z, "hanging_roots");
				}
			}
		}
		// the cave: a wide room with a mossy floor and roof, pillars of stone
		for (int x = -34; x <= 34; x++) {
			for (int z = -30; z <= 18; z++) {
				float n = (float) Noise.fbm(x * 0.07, 3, z * 0.07, 4);
				int floor = -7 + (int) (5 * n), roof = 8 + (int) (6 * (float) Noise.fbm(x * 0.09, 9, z * 0.09, 3));
				for (int y = floor - 2; y <= floor; y++) {
					lush.set(x, y, z, y == floor ? (n < 0.38 ? "clay" : "moss_block") : "stone");
				}
				for (int y = roof; y <= roof + 2; y++) {
					lush.set(x, y, z, y == roof ? "moss_block" : "stone");
				}
				// stone pillars where the roof comes down to the floor
				if (Noise.fbm(x * 0.12, 20, z * 0.12, 2) > 0.7 && Math.abs(x) > 3) {
					for (int y = floor; y <= roof; y++) {
						lush.set(x, y, z, Noise.hash(x, y, z) < 0.15 ? "mossy_cobblestone" : "stone");
					}
				}
				// the walls round the edge
				if (Math.abs(x) == 34 || z == -30) {
					for (int y = floor; y <= roof; y++) {
						lush.set(x, y, z, "stone");
					}
				}
				double h = Noise.hash(x, 7, z);
				if (h < 0.11 && lush.get(x, roof - 1, z) == null) {
					// a glow berry vine down from the roof, its berries lit here and there
					int len = 2 + r.nextInt(roof - floor - 2);
					for (int k = 1; k <= len; k++) {
						String v = k == len ? "cave_vines_lit" : r.nextFloat() < 0.3f ? "cave_vines_plant_lit" : "cave_vines_plant";
						lush.plant(x, roof - k, z, v);
					}
				} else if (h < 0.125) {
					lush.plant(x, roof - 1, z, "spore_blossom");
				} else if (h > 0.97) {
					lush.set(x, floor + 1, z, "azalea_leaves");
					lush.set(x + 1, floor + 1, z, "azalea_leaves");
					lush.set(x, floor + 2, z, "azalea_leaves");
				}
			}
		}
		// a vine right in front of the lens at 11.7 s, to wipe across it
		for (int k = 1; k <= 9; k++) {
			lush.plant(0, 7 - k, 13, k == 9 ? "cave_vines_lit" : "cave_vines_plant_lit");
		}
		lush.build();
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 300);
		f.light(-0.3f, 0.9f, 0.3f, 0.6f);
		f.background(0.0f);
		// the room darkens as the camera rises, till only the berries are left
		float rise = Keys.ramp(t, 13.0f, 14.2f);
		f.fog(0.0f, 0.05f + 0.02f * rise);
		f.bloom(0.8f, 0.5f);
		f.exposure(1.25f - 0.5f * rise);
		f.colour(0.3f);
		f.focus(dist, 3, 10);
		lush.draw(f, m, null, 0.5f, 1.6f + 2.5f * rise, 1 - 0.75f * rise);
	}

	@Override
	public void close() {
		lush.close();
	}
}
