package pro.minehouse.riftclient.vision;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;

/**
 * 0.0–5.4: black, a point of light; a ring of frosted beads grows round it and turns; a foam of bubbles blooms
 * out of the middle and fills the left; the camera pushes in faster and faster and flies through a bright
 * crevice in the foam.
 */
final class S01Spore extends Shot {
	private final Meshes m;
	private final Instances ring = new Instances("spore ring");
	private final Instances ringOuter = new Instances("spore ring outer");
	private final Instances bubbles = new Instances("foam");
	private final Instances haze = new Instances("foam haze");
	private final Instances glow = new Instances("spore light");
	private final Instances slit = new Instances("crevice light");
	private final float[] bubbleDist;

	private static final Keys CAM_Z = new Keys(0f, 9f, 2.5f, 8.6f, 3.6f, 7.4f, 4.5f, 4.2f, 5.4f, -1.5f);
	private static final Keys CAM_X = new Keys(0f, 0f, 2.5f, 0f, 3.6f, 0.4f, 4.5f, 0.9f, 5.4f, 1.1f);

	S01Spore(Meshes m) {
		super(0, 5.4f);
		this.m = m;
		Random r = new Random(7);
		// rings of fluffy beads: a full inner one, broken arcs further out, clumps of light along them
		float[] radii = { 0.95f, 1.2f, 1.45f, 1.75f, 2.0f };
		float[] cover = { 1f, 0.85f, 0.7f, 0.55f, 0.4f };
		for (int i = 0; i < 9000; i++) {
			int ri = Math.min(4, (int) (Math.pow(r.nextDouble(), 1.3) * 5));
			double a = r.nextDouble() * Math.PI * 2;
			double arc = Pt.wrap(a + ri * 1.7) / (Math.PI * 2);
			if (arc > cover[ri]) {
				continue;
			}
			double clump = Math.pow(Pt.noise1(a * 3 + ri * 5), 2.2) * 1.8;
			double rr = radii[ri] + r.nextGaussian() * 0.06 * (1 + clump);
			float b = (float) ((0.15 + clump) * (0.4 + 0.6 * r.nextDouble()) * (ri == 0 ? 1.3 : 1));
			Instances dst = ri < 3 ? ring : ringOuter;
			dst.add((float) (Math.cos(a) * rr), (float) (Math.sin(a) * rr), (float) (r.nextGaussian() * 0.06), 0.015f + r.nextFloat() * 0.03f, b, i);
		}
		for (int i = 0; i < 1500; i++) {
			double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 0.9;
			ring.add((float) (Math.cos(a) * rr), (float) (Math.sin(a) * rr), 0, 0.02f + r.nextFloat() * 0.03f, (float) (0.05 + 0.25 * Math.pow(Pt.noise1(a * 2 + rr * 4), 2)), i);
		}
		// the foam: a blob that leans left, with a crevice down its middle-right the camera flies through
		int n = 3200;
		bubbleDist = new float[n];
		int k = 0;
		while (k < n) {
			float x = (float) (r.nextGaussian() * 1.5 - 1.2), y = (float) (r.nextGaussian() * 1.5), z = (float) (r.nextDouble() * -7 + 1.2);
			float crevice = Math.abs(x - 0.9f + 0.15f * y);
			if (crevice < 0.35f && z < 0.5f || x > 1.4f + 0.3f * y) {
				continue;
			}
			float size = 0.05f + (float) Math.pow(r.nextDouble(), 2.5) * 0.22f;
			bubbleDist[k] = (float) Math.sqrt(x * x + y * y + z * z * 0.2f);
			bubbles.add(x, y, z, size, 1, k);
			k++;
		}
		for (int i = 0; i < 90; i++) {
			haze.add((float) (r.nextGaussian() * 1.4 - 1.2), (float) (r.nextGaussian() * 1.5), (float) (r.nextDouble() * -8), 1.2f + r.nextFloat() * 1.5f, 0.25f, i);
		}
		glow.add(0, 0, 0, 1, 1, 0);
		slit.add(0, 0, 0, 1, new Quaternionf(), 0.22f, 1.6f, 1, 1, 0, 0, 0);
	}

	@Override
	void frame(Frame f, float t) {
		float cz = CAM_Z.at(t), cx = CAM_X.at(t);
		f.camera(cx, 0, cz, cx * 0.6f, 0, cz - 10, 28);
		f.roll(t * 1.5f);
		f.light(-0.3f, 0.4f, -1f, 1.2f);
		f.bloom(0.8f, 0.5f);
		f.focus(Math.max(0.5f, 9 - Math.max(0, t - 2.5f) * 2.4f), 7, 14);
		// the point of light
		float point = Keys.ramp(t, 0.35f, 0.5f) * (1 - Keys.ramp(t, 2.8f, 3.5f));
		f.draw(m.quad, glow, Material.light(Material.GLOW).grey(1).p(40, 9, 0.35f).sprite(), new Matrix4f().scale(0.16f * point));
		// the ring: grows out of the point, turns, fades as the foam comes
		float grow = Keys.ramp(t, 0.45f, 0.95f);
		float fade = 1 - Keys.ramp(t, 2.4f, 3.6f);
		if (grow > 0 && fade > 0) {
			Matrix4f rm = new Matrix4f().rotateX(0.25f).rotateZ((float) Math.toRadians(-10 * t)).scale((0.2f + 0.8f * grow) * 0.8f * (1 - 0.35f * Keys.ramp(t, 1.5f, 2.6f)));
			f.draw(m.quad, ring, Material.light(Material.DUST).grey(1).p(0.9f * fade * grow, 0, 0).sprite(), rm);
			f.draw(m.quad, ringOuter, Material.light(Material.DUST).grey(1).p(0.6f * fade * grow, 0, 0).sprite(), new Matrix4f(rm).rotateZ(0.4f).scale(1.05f));
		}
		// the foam blooms out of the middle
		float bloom = Keys.ramp(t, 1.0f, 3.0f);
		if (bloom > 0) {
			Matrix4f fm = new Matrix4f().scale(0.45f + 0.55f * Keys.ease(bloom));
			f.draw(m.sphereLow, bubbles, Material.light(Material.BUBBLE).grey(1).p(0.06f * bloom + 0.05f * Keys.ramp(t, 3.6f, 5f), 1.8f, 0), fm);
			f.draw(m.quad, haze, Material.light(Material.CLOUD).grey(1).p(0.04f * bloom + 0.22f * Keys.ramp(t, 3.6f, 5.0f), 0, 0).sprite(), fm);
		}
		// light from behind the crevice: a tall soft slit, not a ball
		float back = Keys.ramp(t, 3.2f, 5.2f);
		if (back > 0) {
			f.draw(m.quad, slit, Material.light(Material.GLOW).grey(0.9f * back).p(4, 3.5f, 0.35f).sprite(), new Matrix4f().translate(1.0f, 0, -8).scale(6));
		}
	}

	@Override
	public void close() {
		ring.close();
		ringOuter.close();
		bubbles.close();
		haze.close();
		glow.close();
		slit.close();
	}
}
