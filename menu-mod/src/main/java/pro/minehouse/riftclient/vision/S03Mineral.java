package pro.minehouse.riftclient.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Mesh;
import pro.minehouse.riftclient.cine.Noise;
import pro.minehouse.riftclient.cine.Shapes;

/**
 * 7.0–10.4: over crumpled mineral, a bright glass bar slants across; dark pillars sweep past close by; then a
 * frosted slab on legs with a row of windows and roots hanging to a pale floor, and the push toward it speeds up.
 */
final class S03Mineral extends Shot {
	private final Meshes m;
	private final Mesh ground, roots;
	private final Instances pillars = new Instances("colonnade");
	private final Instances legs = new Instances("slab legs");

	private static final Cam CAM = new Cam(42,
			new float[] { 7.0f, -4f, 2.4f, 6f, 0f, 0.3f, -2f, 0 },
			new float[] { 7.5f, -2.5f, 2.1f, 5.6f, 2f, 0.6f, -2f, 4 },
			new float[] { 8.0f, 0f, 1.4f, 5.6f, 4f, 0.8f, -2f, 10 },
			new float[] { 8.5f, 3f, 1.1f, 5.9f, 8f, 0.8f, -2f, 6 },
			new float[] { 9.0f, 8f, 1.1f, 6.3f, 13f, 1f, -2f, 3 },
			new float[] { 9.5f, 16f, 2.0f, 5.5f, 20f, 1.3f, -3f, 0 },
			new float[] { 10.0f, 19.5f, 1.7f, 3.0f, 21f, 1.2f, -3f, 0 },
			new float[] { 10.4f, 20.6f, 0.7f, -0.8f, 21f, 0.9f, -6f, 0 });

	S03Mineral(Meshes m) {
		super(7.0f, 10.4f);
		this.m = m;
		Mesh.Builder b = new Mesh.Builder();
		Shapes.grid(b, 400, 400, (x, z) -> height(x * 60 + 8, z * 60) / 60);
		ground = b.upload("mineral ground");
		// the colonnade the camera trucks past
		for (int i = 0; i < 6; i++) {
			pillars.add(2.5f + i * 2.6f, -1, 4.4f + (i % 2) * 0.3f, 1, new org.joml.Quaternionf().rotateX((float) (Math.PI / 2)), 0.3f, 0.3f, 8, 1, i, 0, 0);
		}
		// legs under the slab: the gaps between them are its windows
		for (int i = 0; i < 8; i++) {
			legs.add(15.2f + i * 1.65f, -0.2f, -2.1f, 1, new org.joml.Quaternionf().rotateX((float) (Math.PI / 2)), 0.22f, 0.22f, 2.2f, 1, i, 0, 0);
		}
		// roots hanging from the slab and spreading on the floor
		Random r = new Random(5);
		b = new Mesh.Builder();
		for (int i = 0; i < 14; i++) {
			float x0 = 23.5f + r.nextFloat() * 3, z0 = -1.9f + r.nextFloat() * 0.4f;
			float x1 = x0 + (float) r.nextGaussian() * 0.9f, z1 = z0 + 0.3f + r.nextFloat() * 1.4f;
			List<Vector3f> line = new ArrayList<>();
			float[] rad = new float[12];
			for (int k = 0; k < 12; k++) {
				float s = k / 11f;
				float sag = (float) Math.sin(s * Math.PI) * 0.25f;
				line.add(new Vector3f(Keys.lerp(x0, x1, s * s), Keys.lerp(1.75f, 0.02f, s) - sag * 0.2f, Keys.lerp(z0, z1, s * s) + sag));
				rad[k] = 0.035f + 0.05f * s * s;
			}
			Shapes.tube(b, line, rad, 8);
		}
		roots = b.upload("roots");
	}

	/** Crumpled crust on the left, flattening into a pale floor under the slab; a ridge wall at the back. */
	static float height(float x, float z) {
		float rough = 1 - Keys.ramp(x, 12, 17) * 0.95f;
		x -= 8;
		double n1 = Noise.fbm(x * 0.45, z * 0.45, 0.5, 5);
		double ridge = Math.pow(1 - Math.abs(2 * n1 - 1), 3);
		double n2 = Noise.fbm(x * 1.3 + 4, z * 1.3, 2.5, 4);
		double fine = Math.pow(1 - Math.abs(2 * n2 - 1), 4);
		x += 8;
		float wall = Math.max(0, -3.5f - z) * 0.7f;
		return (float) ((ridge * 1.1 + fine * 0.3 - 0.5) * rough + wall * rough);
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 200);
		f.light(0.6f, 0.8f, 0.3f, 0.8f);
		f.background(0.03f);
		f.fog(0.1f, 0.02f);
		f.bloom(0.6f, 0.65f);
		f.focus(dist, 4, 16);
		Material crust = Material.solid(Material.SEM).color(0.7f, 0.78f, 0.84f).p(1.4f, 1.2f, 0.6f);
		f.draw(ground, null, crust, new Matrix4f().translate(8, 0, 0).scale(60));
		// the glass bar, slanting from the upper right across the crust
		Matrix4f bar = new Matrix4f().translate(2f, 2.4f, 0.5f).rotateY((float) Math.toRadians(58)).rotateX((float) Math.toRadians(-16)).scale(0.55f, 0.55f, 30);
		f.draw(m.box, null, Material.light(Material.GLASS).color(0.85f, 0.92f, 1f).p(1.6f, 0, 0), bar);
		f.draw(m.pipeLow, pillars, Material.solid(Material.SEM).color(0.35f, 0.4f, 0.44f).p(1.5f, 0.6f, 0.25f), null);
		// the slab with its windows
		Material frost = Material.solid(Material.SEM).color(0.78f, 0.85f, 0.9f).p(2.5f, 1.6f, 0.7f);
		f.draw(m.sphere, null, frost, new Matrix4f().translate(21, 2.0f, -2.4f).scale(7.5f, 0.6f, 1.4f));
		f.draw(m.pipeLow, legs, frost, null);
		f.draw(roots, null, frost.copy().p(6, 1.8f, 0.6f), null);
	}

	@Override
	public void close() {
		ground.close();
		roots.close();
		pillars.close();
		legs.close();
	}
}
