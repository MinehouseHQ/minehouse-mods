package pro.minehouse.riftclient.vision;

import java.util.Random;

import org.joml.Matrix4f;

import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Keys;
import pro.minehouse.riftclient.cine.Material;
import pro.minehouse.riftclient.cine.Noise;

/**
 * 45.4–51.8, the three worlds of the game in three short shots, an ender pearl drifting through each: out of the
 * white, a frozen sea of packed and blue ice from above; the Nether, falls of lava down a cliff of netherrack into a
 * lava sea, glowstone hanging from the dark; the edge of an End island on a diagonal, end stone over the void, an
 * obsidian pillar, the light of a gateway.
 */
final class M08Dimensions extends Shot {
	private final Meshes m;
	private final Voxels ice = new Voxels("frozen sea");
	private final Voxels nether = new Voxels("nether");
	private final Voxels end = new Voxels("end island");
	private final Instances frost = new Instances("ice haze");
	private final Instances embers = new Instances("embers");
	private final Instances voidDust = new Instances("void dust");

	M08Dimensions(Meshes m) {
		super(45.4f, 51.8f);
		this.m = m;
		Random r = new Random(61);
		// the frozen sea: one layer of ice blocks, icebergs of packed ice, snow on them
		for (int x = -45; x <= 45; x++) {
			for (int z = -60; z <= 30; z++) {
				double n = Noise.fbm(x * 0.06, 2, z * 0.06, 4);
				ice.set(x, 0, z, n > 0.55 ? "blue_ice" : n < 0.4 ? "water" : "packed_ice");
				double berg = Noise.fbm(x * 0.1, 9, z * 0.1, 3);
				int h = (int) ((berg - 0.6) * 18);
				for (int y = 1; y <= h; y++) {
					ice.set(x, y, z, y == h ? "snow" : "packed_ice");
				}
			}
		}
		ice.build();
		for (int i = 0; i < 160; i++) {
			frost.add((float) r.nextGaussian() * 60, 20 + (float) r.nextGaussian() * 6, (float) r.nextGaussian() * 50 - 20, 10 + r.nextFloat() * 22, 0.4f + r.nextFloat() * 0.6f, i);
		}
		// the Nether: a cliff of netherrack, basalt and magma, falls of lava down it, a lava sea, glowstone above
		for (int x = -60; x <= 60; x++) {
			for (int z = -40; z <= 40; z++) {
				nether.set(x, -1, z, z > -18 ? "lava_still" : "netherrack");
			}
			for (int z = -40; z <= -18; z++) {
				int top = 18 + (int) (14 * Noise.fbm(x * 0.07, 3, z * 0.07, 3));
				for (int y = 0; y <= top; y++) {
					if (z > -22 || y >= top - 1) {
						double h = Noise.hash(x, y, z);
						nether.set(x, y, z, h < 0.08 ? "magma" : h < 0.25 && y > top - 6 ? "basalt" : "netherrack");
					}
				}
			}
			if (Noise.hash(x, 4, 4) < 0.18) {
				// a lava fall from the top of the cliff into the sea
				int top = 18 + (int) (14 * Noise.fbm(x * 0.07, 3, -17 * 0.07, 3));
				for (int y = 0; y <= top; y++) {
					nether.set(x, y, -17, "lava_still");
				}
			}
		}
		for (int k = 0; k < 40; k++) {
			int x = r.nextInt(100) - 50, z = r.nextInt(50) - 30, y = 32 + r.nextInt(8);
			for (int d = 0; d < 3; d++) {
				nether.set(x + r.nextInt(2), y - d, z + r.nextInt(2), "glowstone");
			}
		}
		nether.build();
		for (int i = 0; i < 400; i++) {
			embers.add((float) r.nextGaussian() * 40, r.nextFloat() * 30, (float) r.nextGaussian() * 20, 0.15f + r.nextFloat() * 0.25f, 0.5f + r.nextFloat(), i);
		}
		// the End: an island of end stone falling away to the void, an obsidian pillar, a gateway's light
		for (int x = -60; x <= 60; x++) {
			for (int z = -60; z <= 60; z++) {
				float d = (float) Math.sqrt(x * x + z * z) + 12 * (float) Noise.fbm(x * 0.05, 1, z * 0.05, 3);
				if (d > 52) {
					continue;
				}
				int depth = (int) ((52 - d) * 0.5f) + 1;
				for (int y = 0; y > -depth; y--) {
					end.set(x, y, z, "end_stone");
				}
			}
		}
		for (int y = 1; y < 40; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					if (x * x + z * z <= 10) {
						end.set(x + 18, y, z - 14, "obsidian");
					}
				}
			}
		}
		end.set(18, 40, -14, "bedrock");
		end.build();
		for (int i = 0; i < 300; i++) {
			voidDust.add((float) r.nextGaussian() * 120 - 60, (float) r.nextGaussian() * 60 + 20, (float) r.nextGaussian() * 80, 0.4f + r.nextFloat() * 0.8f, 0.4f + r.nextFloat(), i);
		}
	}

	@Override
	void frame(Frame f, float t) {
		f.bloom(0.8f, 0.55f);
		if (t < 47.8f) {
			frozen(f, t);
		} else if (t < 49.8f) {
			nether(f, t);
		} else {
			end(f, t);
		}
	}

	private void pearl(Frame f, float x, float y, float z, float size) {
		f.draw(m.sphereLow, null, Material.solid(Material.SILHOUETTE).color(0.3f, 0.6f, 0.55f).p(0.4f, 0, 0), new Matrix4f().translate(x, y, z).scale(size));
	}

	/** The frozen sea from above, out of the white; the pearl drifts left. */
	private void frozen(Frame f, float t) {
		float s = t - 45.4f;
		f.camera(0, 70 - s * 4, 30, 0, 0, -12, 40);
		f.roll(-8);
		f.light(0.2f, 1f, 0.6f, 0.5f);
		f.background(0.35f);
		f.fog(0.4f, 0.004f);
		f.exposure(0.95f - 0.08f * s);
		f.colour(0.3f);
		f.white(1 - Keys.ramp(t, 45.4f, 46.2f));
		ice.draw(f, m, null, 0.8f, 0, 0.9f);
		f.draw(m.quad, frost, Material.light(Material.CLOUD).color(0.8f, 0.88f, 0.95f).p(0.3f, 0, 0).sprite(), null);
		pearl(f, 10 - s * 3, 40 - s * 1.5f, 0, 1.0f);
		f.focus(60, 2, 10);
	}

	/** Falls of lava down the netherrack, the camera sliding along the lava sea; embers rising. */
	private void nether(Frame f, float t) {
		float s = t - 47.8f;
		f.camera(-30 + s * 6, 10 - s * 1.5f, 34 - s * 3, -10 + s * 6, 12, -20, 42);
		f.roll(4);
		f.light(-0.3f, -0.6f, 0.4f, 1f);
		f.background(0.03f);
		f.fog(0.12f, 0.02f);
		f.exposure(1.05f);
		f.colour(0.45f);
		nether.draw(f, m, null, 0.5f, 1.4f, 0.9f);
		f.draw(m.quad, embers, Material.light(Material.DUST).color(1f, 0.6f, 0.3f).p(1.5f, 0, 0).sprite(), new Matrix4f().translate(-10 + s * 6, s * 3, 0));
		pearl(f, -14 + s * 5, 14, 6, 0.6f);
		f.vignette(0.9f);
		f.focus(40, 1.5f, 10);
	}

	/** The edge of the island on a diagonal: the void's light at the upper left, end stone at the lower right. */
	private void end(Frame f, float t) {
		float s = t - 49.8f;
		f.camera(-80 + s * 4, 26 + s * 3, 70, 0, 0, 0, 40);
		f.roll(-38);
		f.light(-1f, 0.6f, -0.3f, 1.2f);
		f.background(0.02f);
		f.fog(0.02f, 0.004f);
		f.exposure(1.0f + 0.1f * s);
		f.colour(0.3f);
		end.draw(f, m, null, 0.9f, 0, 0.9f);
		f.draw(m.quad, voidDust, Material.light(Material.DUST).color(0.8f, 0.7f, 1f).p(1.2f, 0, 0).sprite(), null);
		// the gateway: a bright point with a beam, far off in the void
		f.draw(m.quad, null, Material.light(Material.GLOW).color(0.85f, 0.92f, 1f).p(1.0f, 1.2f, 0.6f).sprite(), new Matrix4f().translate(-200, 120, -300).scale(300));
		pearl(f, -40 + s * 3, 30, 20, 0.5f);
		f.focus(90, 1.5f, 10);
	}

	@Override
	public void close() {
		ice.close();
		nether.close();
		end.close();
		frost.close();
		embers.close();
		voidDust.close();
	}
}
