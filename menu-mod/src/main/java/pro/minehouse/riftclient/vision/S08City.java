package pro.minehouse.riftclient.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Mesh;
import pro.minehouse.riftclient.cine.Noise;
import pro.minehouse.riftclient.cine.Shapes;

/**
 * 23.0–36.2: straight down at 45° onto glass blocks with light running along their edges; a bright haze; the
 * blocks are cross-shaped buildings in a grid; the camera tilts to an aerial view of a city with a bright river,
 * low cloud passing; then straight down again, the streets a web of lights, and a fast pull-back to orbit.
 */
final class S08City extends Shot {
	private final Meshes m;
	private final Mesh river;
	private final Instances blocks = new Instances("city blocks");
	private final Instances crosses = new Instances("cross buildings");
	private final Instances roads = new Instances("roads");
	private final Instances streetLights = new Instances("street lights");
	private final Instances farLights = new Instances("far lights");
	private final Instances lowCloud = new Instances("low cloud");
	private final Instances highCloud = new Instances("high cloud");
	private final Instances wisps = new Instances("wisps");

	private static final Cam CAM = new Cam(44,
			new float[] { 23.0f, 0f, 13f, 0.1f, 0f, 0f, 0f, 45 },
			new float[] { 24.5f, 0.6f, 15f, 0.15f, 0.6f, 0f, 0f, 50 },
			new float[] { 26.0f, 2f, 17f, 0.2f, 2f, 0f, 0f, 55 },
			new float[] { 27.4f, 3f, 21f, 0.3f, 3f, 0f, 0f, 58 },
			new float[] { 28.0f, 3f, 32f, 0.5f, 3f, 0f, 0f, 60 },
			new float[] { 29.0f, 4f, 58f, 1f, 4f, 0f, 0f, 62 },
			new float[] { 29.6f, 6f, 74f, 12f, 6f, 0f, -20f, 55 },
			new float[] { 30.5f, 20f, 92f, 60f, 30f, 0f, -60f, 20 },
			new float[] { 32.0f, 30f, 140f, 85f, 40f, 0f, -130f, 10 },
			new float[] { 33.5f, 40f, 230f, 95f, 50f, 0f, -210f, 5 },
			new float[] { 34.2f, 50f, 380f, 30f, 50f, 0f, -40f, 0 },
			new float[] { 35.0f, 50f, 800f, 5f, 50f, 0f, 0f, 0 },
			new float[] { 35.6f, 50f, 2800f, 5f, 50f, 0f, 0f, 0 },
			new float[] { 36.2f, 50f, 9000f, 5f, 50f, 0f, 0f, 0 });

	S08City(Meshes m) {
		super(23.0f, 36.2f);
		this.m = m;
		Random r = new Random(41);
		Quaternionf none = new Quaternionf();
		// the river: a bright curve sweeping through the city
		List<Vector3f> line = new ArrayList<>();
		for (int k = 0; k <= 240; k++) {
			float s = k / 240f;
			float x = Keys.lerp(400, -300, s), z = (float) (-260 + 280 * Math.sin(s * 2.4) - 80 * s);
			line.add(new Vector3f(x, 0.05f, z));
		}
		float cell = 12;
		int half = 26;
		for (int i = -half; i <= half; i++) {
			for (int j = -half; j <= half; j++) {
				float cx = i * cell, cz = j * cell;
				if (near(line, cx, cz, 13)) {
					continue;
				}
				if (Math.floorMod(i, 4) == 0 && Math.floorMod(j, 3) == 0) {
					// a cross-shaped building
					float h = 6 + r.nextFloat() * 4, arm = 3.2f, w = 1.6f;
					crosses.add(cx, h / 2, cz, 1, none, w * 2, h, arm * 2 + w * 2, 1, i * 7 + j, 0, 0);
					crosses.add(cx, h / 2, cz, 1, none, arm * 2 + w * 2, h, w * 2, 1, i * 7 + j + 1, 0, 0);
					continue;
				}
				int n = 3 + r.nextInt(4);
				for (int k = 0; k < n; k++) {
					float sx = 1 + r.nextFloat() * 3, sz = 1 + r.nextFloat() * 3, h = 1 + (float) Math.pow(r.nextFloat(), 2.5) * (Math.abs(i) + Math.abs(j) < 4 ? 6 : 20);
					float ox = (r.nextFloat() - 0.5f) * (cell - sx - 2), oz = (r.nextFloat() - 0.5f) * (cell - sz - 2);
					blocks.add(cx + ox, h / 2, cz + oz, 1, none, sx, h, sz, 0.5f + r.nextFloat() * 0.6f, i * 31 + j * 7 + k, 0, 0);
				}
			}
		}
		for (int i = -half; i <= half; i++) {
			float o = (i + 0.5f) * cell;
			roads.add(o, 0.02f, 0, 1, none, 0.35f, 0.04f, half * cell * 2, 0.25f + r.nextFloat() * 0.3f, i, 0, 0);
			roads.add(0, 0.02f, o, 1, none, half * cell * 2, 0.04f, 0.35f, 0.25f + r.nextFloat() * 0.3f, i + 100, 0, 0);
		}
		Mesh.Builder b = new Mesh.Builder();
		Shapes.tube(b, line, new float[] { 3 }, 10);
		river = b.upload("river");
		// street lights: dense in patches, along a jittered road web, out to 1.6 km
		for (int k = 0; k < 160000; k++) {
			boolean alongX = r.nextBoolean();
			float lane = (Math.round(r.nextFloat() * 260 - 130) + 0.5f) * cell, pos = r.nextFloat() * 3200 - 1600;
			float x = alongX ? pos : lane, z = alongX ? lane : pos;
			x += (float) Noise.fbm(z * 0.003, 0.5, 1.5, 3) * 120;
			z += (float) Noise.fbm(x * 0.003, 2.5, 4.5, 3) * 120;
			double dense = Noise.fbm(x * 0.002, z * 0.002, 7.7, 4);
			if (r.nextFloat() > Math.pow(dense, 5) * 20) {
				continue;
			}
			streetLights.add(x, 0.3f, z, 2.5f, 0.5f + r.nextFloat(), k);
		}
		// cities seen from orbit: clusters of light over a continent
		for (int k = 0; k < 120000; k++) {
			float x = (float) r.nextGaussian() * 9000, z = (float) r.nextGaussian() * 6000;
			double dense = Noise.fbm(x * 0.0004, z * 0.0004, 3.3, 5);
			if (r.nextFloat() > Math.pow(dense, 4) * 9) {
				continue;
			}
			farLights.add(x, 0, z, 6 + r.nextFloat() * 8, 0.4f + r.nextFloat(), k);
		}
		for (int k = 0; k < 120; k++) {
			lowCloud.add(60 + (float) r.nextGaussian() * 160, 40 + r.nextFloat() * 30, -140 + (float) r.nextGaussian() * 120, 40 + r.nextFloat() * 50, 0.6f, k);
		}
		for (int k = 0; k < 140; k++) {
			highCloud.add((float) r.nextGaussian() * 5000, 900 + r.nextFloat() * 400, (float) r.nextGaussian() * 3500 - 1500, 600 + r.nextFloat() * 900, 0.6f, k);
		}
		for (int k = 0; k < 30; k++) {
			wisps.add(-6 + (float) r.nextGaussian() * 5, 4 + r.nextFloat() * 6, 4 + (float) r.nextGaussian() * 4, 4 + r.nextFloat() * 5, 0.6f, k);
		}
	}

	private static boolean near(List<Vector3f> line, float x, float z, float d) {
		for (Vector3f p : line) {
			float dx = p.x - x, dz = p.z - z;
			if (dx * dx + dz * dz < d * d) {
				return true;
			}
		}
		return false;
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.2f, 30000);
		f.background(0.015f);
		f.light(0.4f, 0.8f, -0.4f, 0.6f);
		f.bloom(0.7f, 0.55f);
		f.fog(0.05f, t < 30 ? 0.004f : 0.0015f);
		f.focus(dist, t < 29 ? 4 : 1.5f, 12);
		float city = 1 - Keys.ramp(t, 33.4f, 34.3f);
		if (city > 0) {
			// glass blocks with streaks running along their edges
			Material glass = Material.solid(Material.GLASS).color(0.6f, 0.72f, 0.82f).p(0.5f * city, 0, 0);
			f.draw(m.box, blocks, glass, null);
			f.draw(m.box, crosses, glass.copy().p((0.7f + 1.6f * VisionFilm.bump(t, 25.0f, 26.4f, 28.2f)) * city, 0, 0), null);
			f.draw(m.box, roads, Material.light(Material.EMISSIVE).color(0.55f, 0.68f, 0.8f).p(0.45f * city, 0, 0), null);
			float riv = Keys.ramp(t, 29.4f, 30.6f) * (1 - Keys.ramp(t, 33.0f, 34.2f));
			if (riv > 0) {
				f.draw(river, null, Material.light(Material.EMISSIVE).color(0.75f, 0.85f, 0.95f).p(0.7f * riv, 0, 0), new Matrix4f().scale(1, 0.01f, 1));
			}
		}
		// a bright haze flaring between the camera and the cross building
		float haze = VisionFilm.bump(t, 25.6f, 26.5f, 27.6f);
		if (haze > 0) {
			f.draw(m.quad, wisps, Material.light(Material.CLOUD).color(0.8f, 0.88f, 0.95f).p(0.9f * haze, 0, 0).sprite(), new Matrix4f().translate(0, (t - 26) * 2, 0));
		}
		float low = VisionFilm.bump(t, 30.8f, 32.4f, 34.4f);
		if (low > 0) {
			f.draw(m.quad, lowCloud, Material.light(Material.CLOUD).color(0.5f, 0.58f, 0.66f).p(0.5f * low, 0, 0).sprite(), new Matrix4f().translate(-(t - 30) * 25, 0, 0));
		}
		float web = Keys.ramp(t, 33.0f, 34.2f) * (1 - Keys.ramp(t, 35.3f, 35.9f));
		if (web > 0) {
			f.draw(m.quad, streetLights, Material.light(Material.DUST).color(0.7f, 0.82f, 0.95f).p(3f * web, 0, 0).sprite(), new Matrix4f().scale(1, 1, 1));
		}
		float orbit = Keys.ramp(t, 35.0f, 35.6f);
		if (orbit > 0) {
			f.draw(m.quad, farLights, Material.light(Material.DUST).color(0.75f, 0.85f, 0.95f).p(3f * orbit, 0, 0).sprite(), null);
			f.draw(m.quad, highCloud, Material.light(Material.CLOUD).color(0.45f, 0.52f, 0.6f).p(0.35f * orbit, 0, 0).sprite(), null);
		}
	}

	@Override
	public void close() {
		river.close();
		blocks.close();
		crosses.close();
		roads.close();
		streetLights.close();
		farLights.close();
		lowCloud.close();
		highCloud.close();
		wisps.close();
	}
}
