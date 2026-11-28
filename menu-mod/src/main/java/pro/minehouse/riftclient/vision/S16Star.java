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
 * 51.8–66.5: out of a blur, a star; a dark lumpy body rises at the bottom, loops of plasma beside it; the star
 * falls away and the body hangs in silhouette by the loops; the star rises from below with the body in transit
 * before it; the star sets into a sea of cloud; pulling back, it all sits on a giant sphere, and it lifts to white.
 */
final class S16Star extends Shot {
	private final Meshes m;
	private final Mesh loops;
	private final Instances wisps = new Instances("star wisps");
	private final Instances sea = new Instances("cloud sea");
	private final Instances spray = new Instances("sea spray");
	private static final Vector3f STAR = new Vector3f(0, 0, -700);
	private static final float STAR_R = 110;

	private static final Cam CAM = new Cam(35,
			new float[] { 51.8f, 4f, 14f, 40f, 60f, 70f, -700f, 0, 25 },
			new float[] { 52.5f, 4f, 9f, 38f, 50f, 70f, -700f, 0, 25 },
			new float[] { 53.3f, 2f, 7f, 36f, -60f, 90f, -700f, 0, 28 },
			new float[] { 53.8f, 0f, 4f, 38f, -130f, 100f, -700f, 0, 32 },
			new float[] { 54.5f, -4f, 3f, 55f, -130f, 110f, -700f, 0, 35 },
			new float[] { 55.5f, -10f, -2f, 80f, -110f, 130f, -700f, 0, 35 },
			new float[] { 56.0f, -8f, -6f, 95f, -40f, 230f, -700f, 0, 24 },
			new float[] { 56.4f, -5f, -4f, 120f, -10f, 130f, -700f, 0, 18 },
			new float[] { 57.0f, -2f, -2f, 160f, 0f, 60f, -700f, 0, 16 },
			new float[] { 57.5f, 0f, 0f, 200f, 0f, 0f, -700f, 0, 16 },
			new float[] { 58.8f, 0f, 0f, 280f, 0f, 0f, -700f, 0, 17 },
			new float[] { 59.7f, 0f, 10f, 320f, 0f, -30f, -700f, 0, 18 },
			new float[] { 61.5f, 0f, 30f, 440f, 0f, -35f, -700f, 0, 18 },
			new float[] { 63.5f, 0f, 50f, 620f, 0f, -30f, -700f, 0, 18 },
			new float[] { 66.5f, 0f, 70f, 820f, 0f, -25f, -700f, 0, 18 });

	S16Star(Meshes m) {
		super(51.8f, 66.6f);
		this.m = m;
		Random r = new Random(71);
		Mesh.Builder b = new Mesh.Builder();
		// two bundles of loops on the star's limb: upper left and right
		loopBundle(b, r, new Vector3f(-0.75f, 0.55f, 0.35f), 70, 0.55f);
		loopBundle(b, r, new Vector3f(0.8f, 0.45f, 0.4f), 50, 0.4f);
		loops = b.upload("star loops");
		for (int i = 0; i < 160; i++) {
			float a = (float) (r.nextDouble() * Math.PI * 2);
			float rr = STAR_R * (1.02f + (float) Math.abs(r.nextGaussian()) * 0.35f);
			wisps.add(STAR.x + (float) Math.cos(a) * rr, STAR.y + (float) Math.sin(a) * rr, STAR.z + 60, 1,
					new Quaternionf().rotateZ(a - (float) Math.PI / 2 + (float) r.nextGaussian() * 0.4f), 3 + r.nextFloat() * 8, 20 + r.nextFloat() * 50, 1,
					0.3f + r.nextFloat() * 0.7f, i, 0, 0);
		}
		// the sea of cloud the star sets into: a wide flat layer of cloud at y -130
		for (int i = 0; i < 500; i++) {
			float x = (float) r.nextGaussian() * 900, z = -660 + (float) Math.abs(r.nextGaussian()) * 700;
			sea.add(x, -55 + (float) r.nextGaussian() * 8, z, 40 + r.nextFloat() * 80, 0.4f + r.nextFloat() * 0.6f, i);
		}
		for (int i = 0; i < 160; i++) {
			float x = (float) r.nextGaussian() * 260;
			spray.add(x, -50 + (float) Math.abs(r.nextGaussian()) * 50, -640 + (float) r.nextGaussian() * 60, 30 + r.nextFloat() * 50, 0.5f + r.nextFloat() * 0.5f, i);
		}
	}

	private static void loopBundle(Mesh.Builder b, Random r, Vector3f dir, int count, float scale) {
		dir.normalize();
		Vector3f foot = new Vector3f(dir).mul(STAR_R).add(STAR);
		Vector3f tan = new Vector3f(dir).cross(0, 0, 1).normalize();
		Vector3f bin = new Vector3f(dir).cross(tan).normalize();
		for (int i = 0; i < count; i++) {
			float span = (25 + r.nextFloat() * 30) * scale, h = span * (0.8f + r.nextFloat() * 0.7f);
			Vector3f off = new Vector3f(bin).mul((float) r.nextGaussian() * 10 * scale).add(new Vector3f(tan).mul((float) r.nextGaussian() * 4 * scale));
			List<Vector3f> line = new ArrayList<>();
			for (int k = 0; k <= 24; k++) {
				double a = Math.PI * k / 24;
				Vector3f p = new Vector3f(foot).add(off).add(new Vector3f(tan).mul((float) -Math.cos(a) * span)).add(new Vector3f(dir).mul((float) Math.sin(a) * h - 4));
				line.add(p);
			}
			Shapes.tube(b, line, new float[] { 0.25f + r.nextFloat() * 0.6f }, 5);
		}
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.5f, 20000);
		f.background(0.02f);
		f.bloom(0.9f, 0.6f);
		f.vignette(0.6f + 0.4f * Keys.ramp(t, 62.5f, 64.5f));
		// the light is the star: the body is lit from behind
		Vector3f eye = f.eye();
		f.light(STAR.x - eye.x, STAR.y - eye.y, STAR.z - eye.z, 1.4f);
		// out of the blur: focus racks from far off onto the star
		float rack = Keys.ramp(t, 51.8f, 52.4f);
		f.focus(t < 52.4f ? Keys.lerp(1.5f, dist, rack) : t < 56.5f ? 40 : dist, t < 52.4f ? 18 : 2, 40);
		float starBright = (0.75f - 0.25f * Keys.ramp(t, 58f, 60f)) * (1 - 0.7f * Keys.ramp(t, 53.0f, 53.8f) * (1 - Keys.ramp(t, 55.8f, 56.6f))) + 0.6f * VisionFilm.bump(t, 56.3f, 57.1f, 58.8f);
		f.draw(m.sphere, null, Material.light(Material.STAR).color(0.85f, 0.92f, 1f).p(starBright, 9, 0), new Matrix4f().translate(STAR).scale(STAR_R));
		f.draw(m.quad, null, Material.light(Material.GLOW).color(0.6f, 0.72f, 0.85f).p(4, 5f, 0.08f).sprite(), new Matrix4f().translate(STAR).scale(STAR_R * 1.8f));
		f.draw(loops, null, Material.light(Material.NERVE).color(0.7f, 0.82f, 0.95f).p(0.12f, 0, 0), null);
		f.draw(m.quad, wisps, Material.light(Material.PLASMA).color(0.55f, 0.7f, 0.85f).p(0.15f, 2, 0), null);
		// the dark body: close and big at first; from 57.5 kept on the line to the star's middle, a dot in transit
		Matrix4f body;
		float bodySize = Keys.lerp(9, 1.5f, Keys.ramp(t, 55.8f, 57.4f));
		if (t < 57.5f) {
			body = new Matrix4f().scale(bodySize);
		} else {
			Vector3f to = new Vector3f(STAR).sub(eye).normalize().mul(200).add(eye);
			body = new Matrix4f().translate(to).scale(bodySize);
		}
		f.draw(m.lump, null, Material.solid(Material.SILHOUETTE).color(0.35f, 0.45f, 0.55f).p(0.6f, 0, 0), body);
		// the sea of cloud and the mist sweeping in
		float seaIn = Keys.ramp(t, 58.9f, 60.0f);
		if (seaIn > 0) {
			f.draw(m.sphere, null, Material.solid(Material.SEM).color(0.4f, 0.5f, 0.6f).p(0.05f, 2.0f, 0.3f),
					new Matrix4f().translate(0, -8070, -700).scale(8000));
			f.draw(m.quad, sea, Material.light(Material.CLOUD).color(0.7f, 0.8f, 0.9f).p(0.1f * seaIn, 0, 0).sprite(), new Matrix4f().translate(0, -20 * (1 - seaIn), 0));
			f.draw(m.quad, spray, Material.light(Material.CLOUD).color(0.85f, 0.92f, 1f).p(0.12f * seaIn, 0, 0).sprite(), null);
			// the giant sphere the whole scene sits on: its dark curve closes in at the edges
			float big = Keys.ramp(t, 62.0f, 64.5f);
			if (big > 0) {
				f.draw(m.sphere, null, Material.solid(Material.SEM).color(0.9f * big, 1.05f * big, 1.2f * big).p(0.006f, 1.5f, 0.5f),
						new Matrix4f().translate(0, -60, -2600).scale(1250));
			}
		}
		f.draw(m.quad, null, Material.light(Material.CLOUD).color(0.8f, 0.88f, 0.95f).p(0.4f * VisionFilm.bump(t, 58.8f, 59.3f, 60.4f), 0, 0).sprite(),
				new Matrix4f().translate(eye.x - 30, eye.y - 25, eye.z - 60).scale(70));
		f.lift(0.25f * Keys.ramp(t, 64.0f, 65.0f));
	}

	@Override
	public void close() {
		loops.close();
		wisps.close();
		sea.close();
		spray.close();
	}
}
