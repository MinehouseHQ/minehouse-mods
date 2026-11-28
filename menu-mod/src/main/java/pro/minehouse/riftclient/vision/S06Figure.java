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
import pro.minehouse.riftclient.cine.Shapes;

/**
 * 14.3–23.0: glowing veins on black with a hot light at the upper right; pulling back they gather into a head in
 * profile, the neck, a shoulder; the chest lights from inside; the figure recedes to a speck under a beam of light.
 */
final class S06Figure extends Shot {
	private final Meshes m;
	private final Mesh veins;
	private final Instances haze = new Instances("figure haze");
	private final Instances beam = new Instances("beam");

	private static final Cam CAM = new Cam(40,
			new float[] { 14.3f, 1.4f, 17.6f, 3.2f, 0.9f, 17.2f, 0f, 0 },
			new float[] { 15.0f, 1.0f, 17.2f, 5.2f, 0.5f, 16.9f, 0f, -2 },
			new float[] { 15.8f, 0.3f, 16.5f, 6f, 0f, 16.2f, 0f, -4 },
			new float[] { 16.5f, 0.4f, 15.4f, 6.5f, 0f, 15.1f, 0f, -3 },
			new float[] { 17.2f, 1.6f, 14.3f, 6.5f, 0.6f, 14.2f, 0f, 0 },
			new float[] { 17.9f, 2f, 13.7f, 7f, 1f, 13.6f, 0f, 2 },
			new float[] { 18.6f, 1f, 13.6f, 9f, 0.4f, 13.5f, 0f, 3 },
			new float[] { 19.5f, 0f, 13.2f, 13f, 0f, 13.2f, 0f, 2 },
			new float[] { 20.5f, -1f, 13f, 19f, -2.5f, 13.6f, 0f, 0 },
			new float[] { 21.5f, 0f, 12f, 30f, 2f, 9f, 0f, 0 },
			new float[] { 22.5f, 0f, 11f, 60f, 3f, 6f, 0f, 0 },
			new float[] { 23.0f, 0f, 11f, 120f, 3f, 6f, 0f, 0 });

	/** The figure turned three-quarters to the camera. */
	private static final Matrix4f BODY = new Matrix4f().rotateY((float) Math.toRadians(-55));

	S06Figure(Meshes m) {
		super(14.3f, 23.0f);
		this.m = m;
		Random r = new Random(31);
		Mesh.Builder b = new Mesh.Builder();
		for (int i = 0; i < 650; i++) {
			Vector3f p = new Vector3f((float) r.nextGaussian() * 1.2f, 9 + r.nextFloat() * 8.6f, (float) r.nextGaussian() * 1.6f);
			float depth = -r.nextFloat() * r.nextFloat() * 0.6f;
			Body.project(p, depth);
			Vector3f dir = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
			List<Vector3f> line = new ArrayList<>();
			int steps = 25 + r.nextInt(45);
			for (int k = 0; k < steps; k++) {
				line.add(new Vector3f(p));
				dir.add((float) r.nextGaussian() * 0.35f, (float) r.nextGaussian() * 0.35f, (float) r.nextGaussian() * 0.35f).normalize();
				p.add(new Vector3f(dir).mul(0.13f));
				Body.project(p, depth);
			}
			float rad = 0.006f + (float) Math.pow(r.nextFloat(), 4) * 0.05f;
			Shapes.tube(b, line, new float[] { rad }, 5);
		}
		veins = b.upload("veins");
		// smoke inside the figure
		int n = 0;
		while (n < 500) {
			Vector3f p = new Vector3f((float) r.nextGaussian() * 1.4f, 8.5f + r.nextFloat() * 9, (float) r.nextGaussian() * 2.2f);
			if (Body.sdf(p) < 0) {
				haze.add(p.x, p.y, p.z, 0.5f + r.nextFloat() * 0.9f, 0.05f + r.nextFloat() * 0.1f, n++);
			}
		}
		beam.add(0, 0, 0, 1, new Quaternionf(), 0.06f, 1.6f, 1, 1, 0, 0, 0);
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 600);
		f.background(0.012f);
		f.bloom(0.9f, 0.45f);
		f.vignette(0.8f);
		f.focus(dist, 5, 16);
		float vein = 0.9f + 0.6f * Keys.ramp(t, 17.6f, 18.6f) - 0.5f * Keys.ramp(t, 19.0f, 20.5f);
		f.draw(veins, null, Material.light(Material.NERVE).color(0.62f, 0.78f, 0.95f).p(vein * 0.12f, 0, 0), BODY);
		f.draw(m.quad, haze, Material.light(Material.CLOUD).color(0.5f, 0.62f, 0.75f).p(0.04f + 0.05f * Keys.ramp(t, 17.5f, 18.5f), 0, 0).sprite(), BODY);
		// the hot light at the back of the head, then the core in the chest
		float head = Keys.ramp(t, 14.3f, 14.9f) * (1 - Keys.ramp(t, 16.0f, 17.0f));
		float core = Keys.ramp(t, 17.4f, 18.4f) * (1 - 0.6f * Keys.ramp(t, 19.0f, 20.0f));
		f.draw(m.quad, null, Material.light(Material.GLOW).color(0.8f, 0.9f, 1f).p(9, 4, 0.3f).sprite(),
				new Matrix4f(BODY).translate(0.7f, 17.1f, 0.4f).scale(0.8f * head + 0.001f));
		f.draw(m.quad, null, Material.light(Material.GLOW).color(0.8f, 0.9f, 1f).p(7, 3, 0.35f).sprite(),
				new Matrix4f(BODY).translate(-0.2f, 13.4f, 0.6f).scale(1.7f * core + 0.001f));
		// at the end: a beam from above, and the figure is a speck of light
		float b = VisionFilm.bump(t, 21.9f, 22.5f, 23.0f);
		if (b > 0) {
			f.draw(m.quad, beam, Material.light(Material.GLOW).color(0.7f, 0.8f, 0.9f).p(3, 2, 0.3f).sprite(),
					new Matrix4f().translate(0.5f, 30, 0).scale(30 * b));
		}
		float speck = Keys.ramp(t, 22.0f, 22.9f);
		if (speck > 0) {
			f.draw(m.quad, null, Material.light(Material.GLOW).grey(1).p(40, 10, 0.4f).sprite(), new Matrix4f().translate(0, 13, 0).scale(3 * speck));
		}
	}

	@Override
	public void close() {
		veins.close();
		haze.close();
		beam.close();
	}
}
