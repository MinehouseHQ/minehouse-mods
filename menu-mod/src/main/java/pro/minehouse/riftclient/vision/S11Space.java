package pro.minehouse.riftclient.vision;

import java.util.Random;

import org.joml.Matrix4f;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;

/**
 * 36.2–45.4: the planet sweeps in crescent-lit with its night lights, a small moon, a big moon passing close; the
 * planet shrinks before the band of the galaxy and goes dark against its core; debris drifts across the core,
 * the camera pushes in through the dust faster and faster until it is all white.
 */
final class S11Space extends Shot {
	private final Meshes m;
	private final Instances stars = new Instances("stars");
	private final Instances band = new Instances("galaxy band");
	private final Instances dust = new Instances("core dust");
	private final Instances debris = new Instances("debris");
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

	S11Space(Meshes m) {
		super(36.2f, 45.4f);
		this.m = m;
		Random r = new Random(53);
		for (int i = 0; i < 9000; i++) {
			double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
			stars.add((float) (s * Math.cos(a)) * 200000, (float) u * 200000, (float) (s * Math.sin(a)) * 200000, 150 + r.nextFloat() * 250, (float) Math.pow(r.nextFloat(), 3), i);
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
		f.draw(m.quad, stars, Material.light(Material.DUST).grey(1).p(0.9f, 0, 0).sprite(), null);
		float core = 0.3f + 0.7f * Keys.ramp(t, 37.6f, 40.6f) + 3f * Keys.ramp(t, 41.0f, 44.0f);
		f.draw(m.quad, band, Material.light(Material.CLOUD).color(0.55f, 0.66f, 0.76f).p(0.035f * core, 0, 0).sprite(), null);
		f.draw(m.quad, null, Material.light(Material.GLOW).color(0.1f, 0.11f, 0.13f).p(5, 3.5f, 0.3f).sprite(),
				new Matrix4f().translate(0, 0, CORE_Z).scale((9000 + 6000 * Keys.ramp(t, 38.0f, 41.0f)) * (0.5f + 0.5f * core)));
		if (t > 39.5f) {
			f.draw(m.quad, dust, Material.light(Material.DUST).color(0.8f, 0.88f, 0.95f).p(0.25f * Keys.ramp(t, 39.5f, 41.5f) * (1 + 3 * Keys.ramp(t, 42.5f, 44.5f)), 0, 0).sprite(), null);
			f.draw(m.sphereLow, debris, Material.solid(Material.SILHOUETTE).grey(0.4f).p(0.2f, 0, 0), new Matrix4f().translate((t - 40) * 2500, 0, 0));
		}
		if (t < 40.3f) {
			// the planet with its night lights, its air, and the moons
			f.draw(m.sphere, null, Material.solid(Material.PLANET).grey(2.4f).p(1.2f, 0.6f, 0).q(1.7f, 0, 0, 0), new Matrix4f().scale(100));
			f.draw(m.sphere, null, Material.light(Material.ATMOS).color(0.5f, 0.65f, 0.85f).p(0.9f, 3f, 0), new Matrix4f().scale(103));
			float ma = (t - 36.2f) * 0.25f;
			f.draw(m.sphere, null, Material.solid(Material.PLANET).grey(0.8f).p(0, 0.2f, 0).q(7.3f, 0, 0, 0),
					new Matrix4f().translate(150 - 300 * ma, -70 + 40 * ma, 60).scale(9));
			float pass = Keys.ramp(t, 37.4f, 38.1f);
			if (pass > 0 && pass < 1) {
				// the big moon sweeping past close to the lens, right to left
				Matrix4f near = new Matrix4f().translate(Keys.lerp(90, -90, pass), f.eye().y - 34, f.eye().z - 110).scale(22);
				f.draw(m.sphere, null, Material.solid(Material.PLANET).grey(0.7f).p(0, 0.2f, 0).q(3.1f, 0, 0, 0), near);
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
	}
}
