package pro.minehouse.riftclient.vision;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import pro.minehouse.riftclient.cine.BlockTex;
import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 14.3–23.0: glowing sculk veins on black; pulling back they gather into the head of a player in profile, the
 * neck, a shoulder; a sculk heart lights the chest from inside and the light runs out along the veins; the player
 * recedes to a speck standing in the beam of a beacon.
 */
final class M05Player extends Shot {
	private final Meshes m;
	private final Instances skin = new Instances("player skin");
	private final Instances heart = new Instances("player heart").add(0, 10.2f, 1.4f, 1, 1, 0);
	private final Instances beam = new Instances("beacon beam");
	private final Voxels pyramid = new Voxels("beacon");
	private static final float BLOCK = 8.9f;
	/** x, y, z, r, g, b, vein (0..1), distance from the heart. */
	private final List<float[]> vox = new ArrayList<>();
	private static final float V = 0.25f; // one voxel: half a pixel of the skin

	private static final Cam CAM = new Cam(40,
			new float[] { 14.3f, 3.3f, 14.7f, 0.5f, 2.0f, 14.4f, 0.1f, 0 },
			new float[] { 15.0f, 4.4f, 14.5f, 1.4f, 1.9f, 14.1f, 0f, -2 },
			new float[] { 15.8f, 6.2f, 14.2f, 3.2f, 1.0f, 13.6f, 0f, -4 },
			new float[] { 16.5f, 7.2f, 13.2f, 4.6f, 0.5f, 12.4f, 0f, -3 },
			new float[] { 17.2f, 7.8f, 12.0f, 7.5f, 0f, 11.2f, 0f, 0 },
			new float[] { 17.9f, 8.6f, 11.5f, 9.5f, 0f, 10.5f, 0f, 2 },
			new float[] { 18.6f, 11f, 10.5f, 12f, 0f, 9.4f, 0f, 3 },
			new float[] { 19.5f, 13f, 9f, 20f, 0f, 8.2f, 0f, 2 },
			new float[] { 20.5f, 14f, 9f, 32f, 0f, 6.5f, 0f, 0 },
			new float[] { 21.5f, 22f, 20f, 48f, 0f, 3f, 0f, 0 },
			new float[] { 22.5f, 40f, 50f, 80f, 0f, 0f, 0f, 0 },
			new float[] { 23.0f, 55f, 80f, 110f, 0f, -4f, 0f, 0 });

	M05Player(Meshes m) {
		super(14.3f, 23.0f);
		this.m = m;
		// the parts of the player, in pixels of the skin: x0, y0, z0, width, height, depth
		int[][] parts = { { -4, 24, -4, 8, 8, 8 }, { -4, 12, -2, 8, 12, 4 }, { -8, 12, -2, 4, 12, 4 }, { 4, 12, -2, 4, 12, 4 }, { -4, 0, -2, 4, 12, 4 },
				{ 0, 0, -2, 4, 12, 4 } };
		int[] sculk = BlockTex.pixels("sculk");
		for (int[] p : parts) {
			int nx = p[3] * 2, ny = p[4] * 2, nz = p[5] * 2;
			for (int i = 0; i < nx; i++) {
				for (int j = 0; j < ny; j++) {
					for (int k = 0; k < nz; k++) {
						if (i > 0 && j > 0 && k > 0 && i < nx - 1 && j < ny - 1 && k < nz - 1) {
							continue;
						}
						float x = (p[0] * 2 + i + 0.5f) * V, y = (p[1] * 2 + j + 0.5f) * V, z = (p[2] * 2 + k + 0.5f) * V;
						int c = sculk[Math.floorMod(j, 16) * 16 + Math.floorMod(i + k, 16)];
						float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
						// veins: thin ridges of a noise over the body, and the sculk's own bright specks
						double n = Noise.fbm(x * 0.55, y * 0.55, z * 0.55, 4);
						double n2 = Noise.fbm(x * 1.3 + 9, y * 1.3, z * 1.3, 3);
						float vein = (float) Math.max(smooth(0.03, 0.0, Math.abs(n - 0.5)), 0.55 * smooth(0.02, 0.0, Math.abs(n2 - 0.5)));
						float dh = (float) Math.sqrt(x * x + (y - 10.2f) * (y - 10.2f) + (z - 0.5f) * (z - 0.5f));
						// the body itself: the dark of sculk, enough to see the shape of the player against the black
						vox.add(new float[] { x, y, z, 0.05f + r * 0.12f, 0.08f + g * 0.12f, 0.09f + b * 0.12f, vein, dh });
					}
				}
			}
		}
		// the player stands on a beacon on a step of iron blocks (a block is 1.8 / 16 of the player: 8.9 units)
		beam.add(0, 216.5f, 0, 1, new Quaternionf(), 3.4f, 400, 3.4f, 1, 0, 0, 0);
		pyramid.set(0, -1, 0, "beacon");
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				pyramid.set(x, -2, z, "iron_block");
			}
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				pyramid.set(x, -3, z, "iron_block");
			}
		}
		pyramid.build();
	}

	private static float smooth(double a, double b, double x) {
		double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return (float) (t * t * (3 - 2 * t));
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 2000);
		f.light(0.7f, 0.6f, 0.4f, 0.8f);
		f.background(0.0f);
		f.bloom(0.9f, 0.45f);
		f.exposure(1.2f);
		f.colour(0.35f);
		f.focus(dist, 4, 14);
		// the heart wakes at 16.8 s and its light runs out along the veins
		float wake = Keys.ramp(t, 16.6f, 17.6f);
		float front = (t - 16.8f) * 9;
		skin.clear();
		Quaternionf none = new Quaternionf();
		for (float[] v : vox) {
			float run = Keys.clamp01((front - v[7]) / 3 + 1) * Keys.clamp01(1 - (front - v[7]) / 30);
			float glow = v[6] * (0.55f + 0.35f * (float) Math.sin(t * 2.5f + v[7] * 0.8f) + 2.2f * run * wake);
			skin.add(v[0], v[1], v[2], V * 0.97f, none, 1, 1, 1, v[3] + 0.3f * v[6], v[4] + 0.85f * v[6], v[5] + 0.95f * v[6], glow);
		}
		f.draw(m.box, skin, Material.solid(Material.PIXEL).grey(1).p(1.3f, 0.6f, 0), null);
		f.draw(m.quad, heart, Material.light(Material.GLOW).color(0.4f, 0.9f, 1f).p(18, 5, 0.3f).sprite(), new Matrix4f().scale(3.5f * wake * (0.8f + 0.2f * (float) Math.sin(t * 5))));
		// the beacon: its beam lights up as the camera leaves
		float b = Keys.ramp(t, 19.6f, 21.0f);
		if (b > 0) {
			f.draw(m.box, beam, Material.light(Material.EMISSIVE).color(0.75f, 0.9f, 1f).p(0.6f * b, 0, 0), null);
		}
		float p = Keys.ramp(t, 19.0f, 20.5f);
		if (p > 0) {
			pyramid.draw(f, m, new Matrix4f().translate(-BLOCK * 0.5f, 0, -BLOCK * 0.5f).scale(BLOCK), 0.6f, 1.2f, p);
		}
	}

	@Override
	public void close() {
		skin.close();
		heart.close();
		beam.close();
		pyramid.close();
	}
}
