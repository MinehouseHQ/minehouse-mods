package pro.minehouse.riftclient.vision;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;

/**
 * 45.4–51.8, three short shots over the faces of a world: pale marbled ice fading up out of the white with a
 * dark sphere drifting left; icy-blue feathers of plasma by a bright limb; a bright limb on a diagonal over a dark
 * mottled surface. The little dark sphere is in all three.
 */
final class S13Ice extends Shot {
	private final Meshes m;
	private final Instances frost = new Instances("ice haze");
	private final Instances feathers = new Instances("plasma feathers");
	private final Instances crust = new Instances("dark crust");

	S13Ice(Meshes m) {
		super(45.4f, 51.8f);
		this.m = m;
		Random r = new Random(61);
		for (int i = 0; i < 260; i++) {
			frost.add((float) r.nextGaussian() * 60, (float) r.nextGaussian() * 3, -10 - r.nextFloat() * 140, 8 + r.nextFloat() * 22, 0.4f + r.nextFloat() * 0.6f, i);
		}
		// feathers: long strips fanning out from the limb
		for (int i = 0; i < 520; i++) {
			float a = (float) (r.nextGaussian() * 0.35 + 1.95);
			float rr = 1000 + (float) Math.abs(r.nextGaussian()) * 420;
			float x = (float) Math.cos(a) * rr, y = (float) Math.sin(a) * rr;
			Quaternionf q = new Quaternionf().rotateZ(a + (float) r.nextGaussian() * 0.5f - (float) Math.PI / 2).rotateX((float) r.nextGaussian() * 0.6f);
			feathers.add(x, y, (float) r.nextGaussian() * 80, 1, q, 12 + r.nextFloat() * 30, 90 + r.nextFloat() * 220, 1, 0.3f + r.nextFloat() * 0.7f, i, 0, 0);
		}
		for (int i = 0; i < 400; i++) {
			crust.add((float) r.nextGaussian() * 80, (float) r.nextGaussian() * 80, 0, 5 + r.nextFloat() * 14, 0.3f + r.nextFloat() * 0.7f, i);
		}
	}

	@Override
	void frame(Frame f, float t) {
		f.background(0.01f);
		f.bloom(0.8f, 0.6f);
		if (t < 47.8f) {
			ice(f, t);
		} else if (t < 49.8f) {
			plasma(f, t);
		} else {
			limb(f, t);
		}
	}

	/** Pale marbled ice from just above, out of the white; the dark sphere drifts left. */
	private void ice(Frame f, float t) {
		float s = t - 45.4f;
		f.camera(0, 260 - s * 8, 40, 0, 0, -30, 40);
		f.roll(-8);
		f.light(0.2f, 1f, 0.6f, 0.5f);
		f.background(0.6f);
		f.exposure(1.3f - 0.12f * s);
		f.white(1 - Keys.ramp(t, 45.4f, 46.2f));
		f.draw(m.sphere, null, Material.solid(Material.PLANET).color(2.4f, 2.6f, 2.8f).p(0, 0.4f, 0).q(11.3f, 0, 0, 0),
				new Matrix4f().translate(0, -2000, -30).scale(2000));
		f.draw(m.quad, frost, Material.light(Material.CLOUD).color(0.8f, 0.88f, 0.95f).p(0.35f, 0, 0).sprite(), null);
		f.draw(m.sphere, null, Material.solid(Material.SILHOUETTE).grey(0.6f).p(0.3f, 0, 0), new Matrix4f().translate(36 - s * 7, 120 - s * 3, -10).scale(4.5f));
		f.focus(240, 2, 10);
	}

	/** Blue feathers of plasma by a bright limb at the lower right, black space at the upper left. */
	private void plasma(Frame f, float t) {
		float s = t - 47.8f;
		f.camera(-340 + s * 25, 980 - s * 10, 1400 - s * 60, -420, 1000, 0, 40);
		f.light(-0.6f, 0.6f, 0.5f, 1f);
		f.exposure(1.1f - 0.15f * s);
		f.draw(m.sphere, null, Material.solid(Material.PLANET).color(0.6f, 0.75f, 0.9f).p(0, 1.2f, 0).q(5.5f, 0, 0, 0), new Matrix4f().scale(1000));
		f.draw(m.sphere, null, Material.light(Material.ATMOS).color(0.6f, 0.8f, 1f).p(1.6f + 0.5f * s, 2.5f, 0), new Matrix4f().scale(1030));
		f.draw(m.quad, feathers, Material.light(Material.PLASMA).color(0.55f, 0.78f, 1f).p(0.25f, 3, 0), null);
		f.draw(m.sphere, null, Material.solid(Material.SILHOUETTE).grey(0.5f).p(0.1f, 0, 0), new Matrix4f().translate(-420, 1000, 600).scale(2.2f));
		f.vignette(0.9f);
	}

	/** A bright limb on a diagonal: white air at the upper left, dark mottled ground at the lower right. */
	private void limb(Frame f, float t) {
		float s = t - 49.8f;
		f.camera(-60 + s * 4, 40 + s * 3, 160, 0, 0, 0, 40);
		f.roll(-38);
		f.light(-1f, 0.6f, -0.3f, 1.2f);
		f.exposure(1.0f + 0.12f * s);
		f.draw(m.sphere, null, Material.solid(Material.SEM).color(0.4f, 0.52f, 0.65f).p(0.3f, 1.2f, 0.35f), new Matrix4f().translate(400, -400, -100).scale(560));
		f.draw(m.quad, crust, Material.light(Material.CLOUD).color(0.5f, 0.65f, 0.8f).p(0.2f, 0, 0).sprite(), new Matrix4f().translate(40, -40, 20));
		f.draw(m.quad, null, Material.light(Material.GLOW).color(0.85f, 0.92f, 1f).p(1.0f, 1.2f, 0.6f).sprite(), new Matrix4f().translate(-160, 160, -300).scale(520));
		f.draw(m.sphere, null, Material.solid(Material.SILHOUETTE).grey(0.4f).p(0.1f, 0, 0), new Matrix4f().translate(-10, 26, 60).scale(0.5f));
		f.focus(170, 1.5f, 10);
	}

	@Override
	public void close() {
		frost.close();
		feathers.close();
		crust.close();
	}
}
