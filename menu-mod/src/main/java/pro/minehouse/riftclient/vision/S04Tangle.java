package pro.minehouse.riftclient.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Mesh;
import pro.minehouse.riftclient.cine.Shapes;

/**
 * 10.4–14.3: under the slab into a burrow of hairy tubes, a bright opening ahead; a furry tube wipes across the
 * lens; a tangle of hyphae, some ending in trumpet mouths, a thick one passing close; then a pull-back and rise
 * till the tangle is a web of fine lines.
 */
final class S04Tangle extends Shot {
	private final Mesh thick, fine, near;

	private static final Cam CAM = new Cam(46,
			new float[] { 10.4f, 0f, 0.3f, 8f, 0.5f, 0.8f, -4f, 0 },
			new float[] { 11.0f, 0.3f, 0.2f, 4.5f, 0.8f, 0.6f, -6f, -8 },
			new float[] { 11.7f, -0.2f, 0f, 1f, 0.6f, 0.2f, -9f, -6 },
			new float[] { 12.4f, -0.6f, -0.5f, -2.5f, 0.4f, -0.4f, -12f, 4 },
			new float[] { 13.2f, 0.4f, 0.4f, -5f, 1.4f, -0.4f, -14f, 10 },
			new float[] { 13.8f, 1.2f, 3.5f, -2f, 0.8f, -0.6f, -14f, 12 },
			new float[] { 14.3f, 1.8f, 7f, 2f, 0.6f, -1f, -14f, 14 });

	S04Tangle() {
		super(10.4f, 14.3f);
		Random r = new Random(23);
		Mesh.Builder thickB = new Mesh.Builder(), fineB = new Mesh.Builder(), nearB = new Mesh.Builder();
		for (int i = 0; i < 520; i++) {
			boolean big = i < 140;
			Vector3f p = new Vector3f((float) r.nextGaussian() * 5, (float) r.nextGaussian() * 2.6f, 6 - r.nextFloat() * 34);
			// keep a channel open along the camera's way
			if (big && Math.abs(p.x) < 1.2f && Math.abs(p.y) < 1.0f) {
				p.x += Math.signum(p.x + 0.01f) * 1.6f;
			}
			Vector3f dir = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian() * 0.5f, (float) r.nextGaussian()).normalize();
			int steps = big ? 22 : 30;
			float step = big ? 0.45f : 0.35f;
			float rad = big ? 0.12f + r.nextFloat() * 0.28f : 0.025f + r.nextFloat() * 0.05f;
			boolean trumpet = big && r.nextFloat() < 0.3f;
			List<Vector3f> line = new ArrayList<>();
			float[] radius = new float[steps];
			for (int k = 0; k < steps; k++) {
				line.add(new Vector3f(p));
				float s = k / (float) (steps - 1);
				radius[k] = rad * (trumpet && s > 0.85f ? 1 + (s - 0.85f) * 14 : 1);
				dir.add((float) r.nextGaussian() * 0.25f, (float) r.nextGaussian() * 0.25f, (float) r.nextGaussian() * 0.25f).normalize();
				p.add(new Vector3f(dir).mul(step));
			}
			Shapes.tube(big ? thickB : fineB, line, radius, big ? 14 : 6);
		}
		// the furry tube that wipes across the lens at 11.5, and the thick one passing close at 12.3
		Shapes.tube(nearB, List.of(new Vector3f(3, -1.6f, 1.6f), new Vector3f(0.6f, -0.3f, 1.4f), new Vector3f(-1.4f, 1.4f, 1.0f), new Vector3f(-3, 3, 0.6f)),
				new float[] { 0.55f }, 20);
		Shapes.tube(nearB, List.of(new Vector3f(-4, 2.2f, -4.2f), new Vector3f(-1.2f, 0.6f, -4.3f), new Vector3f(1.0f, -0.6f, -4.0f), new Vector3f(4, -2.6f, -3.6f)),
				new float[] { 0.38f }, 20);
		thick = thickB.upload("hyphae");
		fine = fineB.upload("fine hyphae");
		near = nearB.upload("near hyphae");
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.03f, 120);
		// the bright opening at the start goes dark as we get in among the tubes
		float bright = 1 - Keys.ramp(t, 10.6f, 11.8f);
		float sky = 0.03f + 0.4f * bright;
		f.background(sky);
		f.fog(sky, 0.05f + 0.04f * bright);
		f.light(0.5f, 0.7f, 0.5f, 1.1f);
		f.bloom(0.55f, 0.7f);
		f.focus(t < 12.9f ? Math.min(dist, 2.6f) : dist * 0.6f, 6, 18);
		Material hair = Material.solid(Material.SEM).color(0.72f, 0.8f, 0.86f).p(6f, 2.2f, 0.55f);
		f.draw(thick, null, hair, null);
		f.draw(fine, null, hair.copy().p(14f, 1.6f, 0.5f), null);
		f.draw(near, null, hair.copy().p(5f, 2.6f, 0.6f), null);
	}

	@Override
	public void close() {
		thick.close();
		fine.close();
		near.close();
	}
}
