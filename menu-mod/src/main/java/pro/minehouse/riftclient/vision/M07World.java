package pro.minehouse.riftclient.vision;

import java.util.Random;

import org.joml.Matrix4f;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.BlockTex;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 36.2–45.4: the world as a ball of blocks (grass, sea, sand, snow, stone) sweeps in crescent-lit, a small square
 * moon, the big square moon of the game passing close; the world shrinks before a band of square stars and goes
 * dark against its glow; bits of obsidian drift across, the camera pushes in through the dust until all is white.
 */
final class M07World extends Shot {
	private final Meshes m;
	private final Instances stars = new Instances("stars");
	private final Instances band = new Instances("galaxy band");
	private final Instances dust = new Instances("core dust");
	private final Instances debris = new Instances("debris");
	private final Voxels world = new Voxels("world");
	private final Instances moon = new Instances("moon");
	private static final float CELL = 4, R = 100;
	private static final float CORE_Z = -40000;

	private static final Cam CAM = new Cam(35,
			new float[] { 36.2f, 0f, 15f, 175f, -95f, 5f, 0f, -6 },
			new float[] { 36.8f, 0f, 12f, 330f, -70f, 0f, 0f, -4 },
			new float[] { 37.3f, 0f, 10f, 380f, -12f, 0f, 0f, -2 },
			new float[] { 38.3f, 0f, 8f, 800f, 0f, 0f, 0f, 0 },
			new float[] { 39.3f, 0f, 6f, 1400f, 0f, 0f, -1000f, 0 },
			new float[] { 39.9f, 0f, 5f, 2400f, 0f, 0f, -3000f, 0 },
			new float[] { 41.0f, 0f, 0f, -2000f, 0f, 0f, CORE_Z, 0 },
			new float[] { 42.5f, 0f, 0f, -14000f, 0f, 0f, CORE_Z, 0 },
			new float[] { 43.5f, 0f, 0f, -28000f, 0f, 0f, CORE_Z - 10000, 0 },
			new float[] { 45.4f, 0f, 0f, -52000f, 0f, 0f, CORE_Z - 30000, 0 });

	M07World(Meshes m) {
		super(36.2f, 45.4f);
		this.m = m;
		Random r = new Random(53);
		for (int i = 0; i < 9000; i++) {
			double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
			stars.add((float) (s * Math.cos(a)) * 200000, (float) u * 200000, (float) (s * Math.sin(a)) * 200000, 250 + r.nextFloat() * 350, (float) Math.pow(r.nextFloat(), 3) * 3, i);
		}
		// the band of the galaxy: a flat lane of glowing cloud with dark lanes between, and the core in its middle
		for (int i = 0; i < 900; i++) {
			float x = (float) r.nextGaussian() * 30000, y = (float) r.nextGaussian() * 1600 * (1 + Math.abs(x) / 40000);
			float b = (float) (0.25 + 0.75 * Math.exp(-x * x / 2e8));
			band.add(x, y, CORE_Z + (float) r.nextGaussian() * 3000, 3000 + r.nextFloat() * 6000, b, i);
		}
		for (int i = 0; i < 26000; i++) {
			float x = (float) r.nextGaussian() * 9000, y = (float) r.nextGaussian() * 2000, z = CORE_Z + (float) r.nextGaussian() * 14000;
			dust.add(x, y, z, 8 + r.nextFloat() * 25, 0.3f + r.nextFloat(), i);
		}
		// the world: a ball of blocks; sea where the land is low, sand on its shores, snow at the poles, stone peaks
		int n = (int) (R / CELL) + 2;
		for (int i = -n; i <= n; i++) {
			for (int j = -n; j <= n; j++) {
				for (int k = -n; k <= n; k++) {
					float x = i * CELL, y = j * CELL, z = k * CELL;
					float d = (float) Math.sqrt(x * x + y * y + z * z);
					if (d < 1) {
						continue;
					}
					float h = (float) Noise.fbm(x / d * 1.6 + 3, y / d * 1.6, z / d * 1.6, 5);
					float top = R + (h > 0.47f ? (h - 0.47f) * 40 : 0);
					if (d > top || d < top - CELL * 2.2f) {
						continue;
					}
					float lat = Math.abs(y / d);
					String b;
					if (h < 0.47f) {
						b = lat > 0.82f ? "packed_ice" : "water";
					} else if (lat > 0.78f || h > 0.66f && lat > 0.5f) {
						b = "snow";
					} else if (h < 0.49f) {
						b = "sand";
					} else if (h > 0.64f) {
						b = "stone";
					} else if (Noise.fbm(x / d * 5, y / d * 5 + 4, z / d * 5, 3) > 0.58) {
						b = "oak_leaves";
					} else {
						b = "grass_all";
					}
					world.set(i, j, k, b);
				}
			}
		}
		world.build();
		// the moon of the game: a flat square of its pixels
		BlockTex.Image mi = BlockTex.image("textures/environment/celestial/moon/full_moon.png");
		for (int y = 0; y < mi.height(); y++) {
			for (int x = 0; x < mi.width(); x++) {
				int c = mi.at(x, y);
				float rr = (c >> 16 & 255) / 255f, gg = (c >> 8 & 255) / 255f, bb = (c & 255) / 255f;
				// the game adds the moon onto the sky, so its black frame is not part of it
				if ((c >>> 24) < 40 || rr + gg + bb < 0.3f) {
					continue;
				}
				moon.add((x + 0.5f) / mi.width() - 0.5f, 0.5f - (y + 0.5f) / mi.height(), 0, 1f / mi.width(), new org.joml.Quaternionf(), 1, 1, 0.3f, rr, gg, bb, 0.9f);
			}
		}
		for (int i = 0; i < 40; i++) {
			debris.add(-3000 + (float) r.nextGaussian() * 1500, (float) r.nextGaussian() * 300, -16000 + (float) r.nextGaussian() * 2000, 6 + r.nextFloat() * 30, 1, i);
		}
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(2f, 400000);
		f.background(0.01f);
		f.bloom(0.8f, 0.5f);
		f.vignette(0.7f);
		// the sun swings from the right to behind the planet: crescent, then eclipse
		float ecl = Keys.ramp(t, 38.4f, 39.3f);
		f.light(Keys.lerp(1f, 0.05f, ecl), 0.2f, Keys.lerp(0.8f, -1f, ecl), 0.6f);
		f.draw(m.box, stars, Material.light(Material.EMISSIVE).grey(1).p(0.9f, 0, 0), null);
		float core = 0.3f + 0.7f * Keys.ramp(t, 37.6f, 40.6f) + 3f * Keys.ramp(t, 41.0f, 44.0f);
		f.draw(m.quad, band, Material.light(Material.CLOUD).color(0.55f, 0.66f, 0.76f).p(0.035f * core, 0, 0).sprite(), null);
		f.draw(m.quad, null, Material.light(Material.GLOW).color(0.1f, 0.11f, 0.13f).p(5, 3.5f, 0.3f).sprite(),
				new Matrix4f().translate(0, 0, CORE_Z).scale((9000 + 6000 * Keys.ramp(t, 38.0f, 41.0f)) * (0.5f + 0.5f * core)));
		if (t > 39.5f) {
			f.draw(m.quad, dust, Material.light(Material.DUST).color(0.8f, 0.88f, 0.95f).p(0.25f * Keys.ramp(t, 39.5f, 41.5f) * (1 + 3 * Keys.ramp(t, 42.5f, 44.5f)), 0, 0).sprite(), null);
			f.draw(m.box, debris, Material.solid(Material.SILHOUETTE).grey(0.4f).p(0.2f, 0, 0), new Matrix4f().translate((t - 40) * 2500, 0, 0));
		}
		if (t < 40.3f) {
			// the planet with its night lights, its air, and the moons
			// the world of blocks, turning slowly, in its thin air
			world.draw(f, m, new Matrix4f().rotateY((t - 36.2f) * 0.03f).rotateX(0.35f).scale(CELL).translate(-0.5f, -0.5f, -0.5f), 1.0f, 0, 1.6f);
			f.draw(m.sphere, null, Material.light(Material.ATMOS).color(0.5f, 0.65f, 0.85f).p(0.6f, 3f, 0), new Matrix4f().scale(R + 14));
			float ma = (t - 36.2f) * 0.25f;
			f.draw(m.box, moon, Material.solid(Material.PIXEL).grey(1.3f).p(1, 0.8f, 0),
					new Matrix4f().translate(150 - 300 * ma, -70 + 40 * ma, 60).rotateY(0.3f).scale(18));
			float pass = Keys.ramp(t, 37.4f, 38.1f);
			if (pass > 0 && pass < 1) {
				// the big moon sweeping past close to the lens, right to left
				Matrix4f near = new Matrix4f().translate(Keys.lerp(90, -90, pass), f.eye().y - 34, f.eye().z - 110).rotateZ(0.2f).scale(48);
				f.draw(m.box, moon, Material.solid(Material.PIXEL).grey(1.2f).p(1, 0.8f, 0), near);
			}
		}
		f.focus(t < 38.2f ? dist : 100000, t < 38.2f ? 3 : 0.5f, 14);
		// past the core it all goes white
		f.lift(0.6f * Keys.ramp(t, 42.8f, 44.4f));
		f.white(Keys.ramp(t, 43.6f, 44.8f) * 0.95f);
	}

	@Override
	public void close() {
		stars.close();
		band.close();
		dust.close();
		debris.close();
		world.close();
		moon.close();
	}
}
