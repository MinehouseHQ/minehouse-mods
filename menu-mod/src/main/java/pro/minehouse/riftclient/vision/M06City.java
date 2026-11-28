package pro.minehouse.riftclient.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import pro.minehouse.riftclient.cine.BlockTex;
import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 23.0–36.2: straight down onto the glass roof of a cross-shaped church, lanterns along its edges; a bright haze;
 * it stands in a village of block houses on a grid of paths; the camera tilts to an aerial view with a river
 * through the fields, low square clouds passing; dusk falls and the torches light; straight down again, the
 * borders of the chunks show, the villages are a web of lights, and a fast pull-back to orbit.
 */
final class M06City extends Shot {
	private final Meshes m;
	private final Voxels town = new Voxels("town");
	private final Instances[] chunks = { new Instances("grass chunks"), new Instances("water chunks"), new Instances("forest chunks"),
			new Instances("sand chunks"), new Instances("snow chunks") };
	private static final String[] CHUNK = { "grass_block", "water", "oak_leaves", "sand", "snowy_grass" };
	private final Instances borders = new Instances("chunk borders");
	private final Instances lights = new Instances("village lights");
	private final Instances farLights = new Instances("far lights");
	private final Instances clouds = new Instances("clouds");
	private final Instances wisps = new Instances("haze");
	private final List<float[]> river = new ArrayList<>();
	private static final int NEAR = 80;

	private static final Cam CAM = new Cam(44,
			new float[] { 23.0f, 0f, 16f, 0.1f, 0f, 0f, 0f, 45 },
			new float[] { 24.5f, 0.6f, 19f, 0.15f, 0.6f, 0f, 0f, 50 },
			new float[] { 26.0f, 2f, 22f, 0.2f, 2f, 0f, 0f, 55 },
			new float[] { 27.4f, 3f, 27f, 0.3f, 3f, 0f, 0f, 58 },
			new float[] { 28.0f, 3f, 36f, 0.5f, 3f, 0f, 0f, 60 },
			new float[] { 29.0f, 4f, 58f, 1f, 4f, 0f, 0f, 62 },
			new float[] { 29.6f, 6f, 74f, 12f, 6f, 0f, -20f, 55 },
			new float[] { 30.5f, 20f, 92f, 60f, 30f, 0f, -60f, 20 },
			new float[] { 32.0f, 30f, 140f, 85f, 40f, 0f, -130f, 10 },
			new float[] { 33.5f, 40f, 230f, 95f, 50f, 0f, -210f, 5 },
			new float[] { 34.2f, 50f, 380f, 30f, 50f, 0f, -40f, 0 },
			new float[] { 35.0f, 50f, 800f, 5f, 50f, 0f, 0f, 0 },
			new float[] { 35.6f, 50f, 2800f, 5f, 50f, 0f, 0f, 0 },
			new float[] { 36.2f, 50f, 9000f, 5f, 50f, 0f, 0f, 0 });

	M06City(Meshes m) {
		super(23.0f, 36.2f);
		this.m = m;
		Random r = new Random(41);
		for (int k = 0; k <= 400; k++) {
			float s = k / 400f;
			river.add(new float[] { Keys.lerp(900, -900, s), (float) (-260 + 280 * Math.sin((s * 2.57f - 0.6f) * 2.4) - 80 * s) });
		}
		// the ground round the village, block by block
		for (int x = -NEAR; x < NEAR; x++) {
			for (int z = -NEAR; z < NEAR; z++) {
				float w = riverDist(x + 0.5f, z + 0.5f);
				if (w < 5) {
					town.set(x, -1, z, "water");
					town.set(x, -2, z, "sand");
				} else if (w < 7) {
					town.set(x, -1, z, "sand");
				} else {
					town.set(x, -1, z, "grass_block");
				}
				town.set(x, -2, z, town.get(x, -2, z) == null ? "dirt" : town.get(x, -2, z));
			}
		}
		// paths on a grid of 12, houses, fields and trees in the cells between
		for (int i = -6; i <= 6; i++) {
			for (int j = -6; j <= 6; j++) {
				int cx = i * 12, cz = j * 12;
				if (riverDist(cx + 6, cz + 6) < 12) {
					continue;
				}
				for (int k = 0; k < 12; k++) {
					for (int q = 0; q < 2; q++) {
						if (riverDist(cx + k, cz + q) > 7) {
							town.set(cx + k, -1, cz + q, "dirt_path");
						}
						if (riverDist(cx + q, cz + k) > 7) {
							town.set(cx + q, -1, cz + k, "dirt_path");
						}
					}
				}
				if (i == 0 && j == 0) {
					church(cx + 2, cz + 2);
					continue;
				}
				double h = Noise.hash(i, j, 3);
				if (h < 0.55) {
					house(cx + 3 + r.nextInt(2), cz + 3 + r.nextInt(2), 5 + r.nextInt(3), 5 + r.nextInt(2), r);
				} else if (h < 0.8) {
					field(cx + 3, cz + 3);
				} else {
					tree(cx + 6, cz + 6, r);
				}
				town.plant(cx + 2, 0, cz + 2, "torch");
			}
		}
		// a few trees round the edge of the village
		for (int k = 0; k < 70; k++) {
			int x = r.nextInt(2 * NEAR - 8) - NEAR + 4, z = r.nextInt(2 * NEAR - 8) - NEAR + 4;
			if (Math.max(Math.abs(x), Math.abs(z)) > 74 && riverDist(x, z) > 8) {
				tree(x, z, r);
			}
		}
		town.build();
		// the land beyond, a chunk at a time
		Quaternionf none = new Quaternionf();
		for (int i = -110; i < 110; i++) {
			for (int j = -110; j < 110; j++) {
				float x = i * 16 + 8, z = j * 16 + 8;
				if (Math.abs(x) < NEAR && Math.abs(z) < NEAR) {
					continue;
				}
				double n = Noise.fbm(x * 0.0015, 4.2, z * 0.0015, 4);
				int kind;
				if (riverDist(x, z) < 10 || n < 0.3) {
					kind = 1;
				} else if (n < 0.315) {
					kind = 3;
				} else if (z < -1300 + 300 * Noise.fbm(x * 0.002, 1, 1, 2)) {
					kind = 4;
				} else if (Noise.fbm(x * 0.006, 7.7, z * 0.006, 3) > 0.6) {
					kind = 2;
				} else {
					kind = 0;
				}
				float top = kind == 1 ? -1 : kind == 2 ? 3 : (float) Math.floor(4 * (n - 0.34));
				float[] rect = BlockTex.rect(Voxels.kindOf(CHUNK[kind]).side());
				chunks[kind].add(x, top - 8, z, 1, none, 16, 16, 16, rect[0], rect[1], rect[2], rect[3]);
			}
		}
		// the borders of the chunks, as the game draws them
		for (int k = -40; k <= 40; k++) {
			borders.add(k * 16, 0.3f, 0, 1, none, 0.25f, 0.06f, 1280, 1, 0, 0, 0);
			borders.add(0, 0.3f, k * 16, 1, none, 1280, 0.06f, 0.25f, 1, 0, 0, 0);
		}
		// villages far and near at night: clusters of torches on little grids
		for (int v = 0; v < 260; v++) {
			float vx = (float) r.nextGaussian() * 1500, vz = (float) r.nextGaussian() * 1200;
			if (riverDist(vx, vz) < 30 || Noise.fbm(vx * 0.0015, 4.2, vz * 0.0015, 4) < 0.34) {
				continue;
			}
			int n = 20 + r.nextInt(60);
			for (int k = 0; k < n; k++) {
				float x = vx + 12 * Math.round((float) r.nextGaussian() * 3), z = vz + 12 * Math.round((float) r.nextGaussian() * 3);
				lights.add(x + r.nextFloat() * 2, 1, z + r.nextFloat() * 2, 7f + r.nextFloat() * 5, 0.5f + r.nextFloat(), k);
			}
		}
		for (int k = 0; k < 14000; k++) {
			float x = (float) r.nextGaussian() * 9000, z = (float) r.nextGaussian() * 6000;
			if (Noise.fbm(x * 0.0004, z * 0.0004, 3.3, 5) < 0.5) {
				continue;
			}
			farLights.add(x, 0, z, 70 + r.nextFloat() * 70, 0.4f + r.nextFloat(), k);
		}
		// the game's clouds: flat blocks of 12, laid out from its cloud picture, 128 blocks up
		BlockTex.Image sky = BlockTex.image("textures/environment/clouds.png");
		for (int y = 0; y < sky.height(); y += 1) {
			for (int x = 0; x < sky.width(); x += 1) {
				if ((sky.at(x, y) >>> 24) > 128) {
					float wx = (x - sky.width() / 2f) * 12 + 60, wz = (y - sky.height() / 2f) * 12 - 140;
					if (Math.abs(wx) < 2400 && Math.abs(wz) < 2400) {
						clouds.add(wx, 128, wz, 1, none, 12, 4, 12, 1, 0, 0, 0);
					}
				}
			}
		}
		for (int k = 0; k < 30; k++) {
			wisps.add(-6 + (float) r.nextGaussian() * 5, 6 + r.nextFloat() * 8, 4 + (float) r.nextGaussian() * 4, 4 + r.nextFloat() * 5, 0.6f, k);
		}
	}

	private float riverDist(float x, float z) {
		float best = 1e9f;
		for (float[] p : river) {
			float dx = p[0] - x, dz = p[1] - z;
			best = Math.min(best, dx * dx + dz * dz);
		}
		return (float) Math.sqrt(best);
	}

	/** The church in the middle: a cross of stone bricks with a glass roof, lanterns along its edges. */
	private void church(int x0, int z0) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean arm = (x >= 3 && x <= 5) || (z >= 3 && z <= 5);
				if (!arm) {
					continue;
				}
				boolean edge = x == 0 || z == 0 || x == 8 || z == 8 || ((x < 3 || x > 5) && (z == 2 || z == 6)) || ((z < 3 || z > 5) && (x == 2 || x == 6));
				for (int y = 0; y < 5; y++) {
					if (edge) {
						town.set(x0 + x, y, z0 + z, y == 2 ? "glass" : "stone_bricks");
					}
				}
				town.set(x0 + x, 5, z0 + z, edge ? "stone_bricks" : "glass");
				if (edge && (x + z) % 2 == 0) {
					town.plant(x0 + x, 6, z0 + z, "lantern");
				}
			}
		}
		for (int y = 5; y < 9; y++) {
			town.set(x0 + 4, y, z0 + 4, y == 8 ? "glowstone" : "stone_bricks");
		}
	}

	private void house(int x0, int z0, int w, int d, Random r) {
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				boolean wall = x == 0 || z == 0 || x == w - 1 || z == d - 1;
				boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
				town.set(x0 + x, 0, z0 + z, wall ? "cobblestone" : "oak_planks");
				for (int y = 1; y < 4; y++) {
					if (corner) {
						town.set(x0 + x, y, z0 + z, "oak_log");
					} else if (wall) {
						town.set(x0 + x, y, z0 + z, y == 2 && (x + z) % 2 == 1 ? "glass" : "oak_planks");
					}
				}
			}
		}
		// the roof steps up to a ridge
		for (int k = 0; k <= Math.min(w, d) / 2; k++) {
			for (int x = k - 1; x <= w - k; x++) {
				for (int z = k - 1; z <= d - k; z++) {
					town.set(x0 + x, 4 + k, z0 + z, "spruce_planks");
				}
			}
		}
		town.plant(x0 - 1, 1, z0 + d / 2, r.nextBoolean() ? "torch" : "lantern");
	}

	private void field(int x0, int z0) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				if (x == 4) {
					town.set(x0 + x, -1, z0 + z, "water");
					continue;
				}
				town.set(x0 + x, -1, z0 + z, "farmland_moist");
				town.plant(x0 + x, 0, z0 + z, "wheat_stage7");
			}
		}
	}

	private void tree(int x0, int z0, Random r) {
		int h = 4 + r.nextInt(3);
		for (int y = 0; y < h; y++) {
			town.set(x0, y, z0, "oak_log");
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = h - 2; y <= h + 1; y++) {
					int rr = y > h - 1 ? 1 : 2;
					if (Math.abs(x) <= rr && Math.abs(z) <= rr && !(x == 0 && z == 0 && y < h) && !(Math.abs(x) == 2 && Math.abs(z) == 2 && r.nextBoolean())) {
						town.set(x0 + x, y, z0 + z, "oak_leaves");
					}
				}
			}
		}
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.2f, 30000);
		// dusk falls from 30.5 s: the land darkens, the torches and the lanterns light the night
		float dusk = Keys.ramp(t, 30.5f, 33.8f);
		f.background(0.01f + 0.05f * (1 - dusk));
		f.light(0.4f, 0.8f, -0.4f, 0.6f);
		f.bloom(0.75f, 0.5f);
		f.fog(0.05f * (1 - dusk), t < 30 ? 0.004f : 0.0012f);
		f.exposure(1.15f);
		f.colour(0.35f);
		f.focus(dist, t < 29 ? 4 : 1.5f, 12);
		float land = 1 - 0.85f * dusk;
		town.draw(f, m, null, 0.45f, 1 + 3 * dusk, land);
		for (int k = 0; k < chunks.length; k++) {
			Material mat = Voxels.material(CHUNK[k], 0.45f, 0).tile(1);
			f.draw(m.box, chunks[k], mat.scale(land * 0.9f), null);
		}
		// a bright haze between the camera and the church
		float haze = VisionFilm.bump(t, 25.6f, 26.5f, 27.6f);
		if (haze > 0) {
			f.draw(m.quad, wisps, Material.light(Material.CLOUD).color(0.8f, 0.88f, 0.95f).p(0.9f * haze, 0, 0).sprite(), new Matrix4f().translate(0, (t - 26) * 2, 0));
		}
		// the game's clouds, lit by the day, then dark against the night
		float cl = Keys.ramp(t, 29.5f, 30.5f);
		if (cl > 0) {
			f.draw(m.box, clouds, Material.solid(Material.LIT).grey((0.95f - 0.75f * dusk) * cl).p(0.2f, 0, 0), new Matrix4f().translate((t - 30) * 4, 0, 0));
		}
		float grid = Keys.ramp(t, 33.6f, 34.4f) * (1 - Keys.ramp(t, 35.2f, 35.8f));
		if (grid > 0) {
			f.draw(m.box, borders, Material.light(Material.EMISSIVE).color(0.6f, 0.8f, 1f).p(0.35f * grid, 0, 0), null);
		}
		float web = Keys.ramp(t, 32.5f, 34.0f) * (1 - Keys.ramp(t, 35.4f, 36.0f));
		if (web > 0) {
			f.draw(m.quad, lights, Material.light(Material.DUST).color(1f, 0.8f, 0.5f).p(3f * web, 0, 0).sprite(), null);
		}
		float orbit = Keys.ramp(t, 35.0f, 35.6f);
		if (orbit > 0) {
			f.draw(m.quad, farLights, Material.light(Material.DUST).color(1f, 0.82f, 0.55f).p(3f * orbit, 0, 0).sprite(), null);
		}
	}

	@Override
	public void close() {
		town.close();
		for (Instances i : chunks) {
			i.close();
		}
		borders.close();
		lights.close();
		farLights.close();
		clouds.close();
		wisps.close();
	}
}
