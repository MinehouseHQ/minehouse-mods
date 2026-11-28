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
 * 0.0–5.4, the smallest thing in the game: black, one glowing pixel; the pixels of the diamond ore texture come
 * up round it ring by ring, each a little cube, then the stone round the ore; the camera pulls back over the
 * texture, then pushes in faster and faster into a gap between the pixels.
 */
final class M01Pixel extends Shot {
	private final Meshes m;
	private final Instances pixels = new Instances("pixels");
	private final Instances glow = new Instances("first pixel glow").add(0, 0, 0.6f, 1, 1, 0);
	private final List<float[]> px = new ArrayList<>();

	/** Where the push ends: a gap between four pixels near the middle. */
	private static final float GX = 3, GY = -2;
	private static final Cam CAM = new Cam(36,
			new float[] { 0.0f, 0.2f, 0.1f, 4.5f, 0f, 0f, 0f, 0 },
			new float[] { 1.2f, 0.6f, 0.4f, 9f, 0f, 0f, 0f, -2 },
			new float[] { 2.6f, 2.5f, 1.8f, 22f, 0.6f, 0.2f, 0f, -5 },
			new float[] { 3.8f, 5f, 3f, 40f, 1.5f, 0.4f, 0f, -8 },
			new float[] { 4.4f, 4.5f, 1f, 30f, GX, GY, 0f, -6 },
			new float[] { 4.9f, GX + 0.4f, GY + 0.2f, 9f, GX, GY, 0f, -2 },
			new float[] { 5.25f, GX, GY, 1.6f, GX, GY, -2f, 0 },
			new float[] { 5.4f, GX, GY, 0.4f, GX, GY, -3f, 0 });

	M01Pixel(Meshes m) {
		super(0f, 5.4f);
		this.m = m;
		// the ore in the middle, stone and a little coal round it
		String[][] grid = { { "stone", "coal_ore", "stone" }, { "stone", "diamond_ore", "stone" }, { "andesite", "stone", "iron_ore" } };
		int[] ore = BlockTex.pixels("diamond_ore");
		// the first pixel: the brightest blue one nearest the middle
		int best = 0;
		float bestScore = -1e9f;
		for (int k = 0; k < 256; k++) {
			int c = ore[k];
			float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
			float dx = k % 16 - 7.5f, dy = k / 16 - 7.5f;
			float score = (b - r) * 2 + g - 0.05f * (dx * dx + dy * dy);
			if (score > bestScore) {
				bestScore = score;
				best = k;
			}
		}
		float ox = best % 16, oy = best / 16;
		for (int by = 0; by < 3; by++) {
			for (int bx = 0; bx < 3; bx++) {
				int[] tex = BlockTex.pixels(grid[by][bx]);
				for (int k = 0; k < 256; k++) {
					int c = tex[k];
					float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
					// the middle block's pixel at (ox, oy) sits at the origin; y goes up the screen
					// a thin seam between the blocks, so they read as blocks when the camera is back
					float x = (bx - 1) * 16.8f + k % 16 - ox, y = -((by - 1) * 16.8f + k / 16 - oy);
					float d = (float) Math.sqrt(x * x + y * y);
					float lum = 0.3f * r + 0.6f * g + 0.1f * b;
					// a little more contrast than the texture has, so the stone's speckle carries in grey
					r = Math.max(0, lum + (r - lum) * 1.2f + (lum - 0.45f) * 0.7f);
					g = Math.max(0, lum + (g - lum) * 1.2f + (lum - 0.45f) * 0.7f);
					b = Math.max(0, lum + (b - lum) * 1.2f + (lum - 0.45f) * 0.7f);
					boolean gem = bx == 1 && by == 1 && b > r + 0.15f;
					float seed = (float) Noise.hash(k, bx * 3 + by, 7);
					// x, y, relief, when it comes, r, g, b, glow, seed
					px.add(new float[] { x, y, lum * 0.8f, 0.3f + 0.16f * d + 0.25f * seed, r, g, b, d < 0.5f ? 1.5f : gem ? 0.35f : 0, seed });
				}
			}
		}
	}

	@Override
	void frame(Frame f, float t) {
		float dist = CAM.apply(f, t);
		f.clip(0.05f, 400);
		f.light(-0.5f, 0.6f, 0.65f, 0.8f);
		f.background(0);
		f.bloom(0.7f, 0.55f);
		f.exposure(1.1f);
		f.colour(0.12f + 0.25f * Keys.ramp(t, 1.0f, 3.5f));
		f.focus(dist, 5, 14);
		pixels.clear();
		Quaternionf none = new Quaternionf();
		for (float[] p : px) {
			float a = Keys.ease(Keys.clamp01((t - p[3]) / 0.45f));
			if (a <= 0) {
				continue;
			}
			// each pixel rises out of the dark, a cube that settles into the flat of the texture
			float z = p[2] - 7 * (1 - a) * (0.6f + 0.8f * p[8]);
			float s = 0.9f * (0.3f + 0.7f * a);
			pixels.add(p[0], p[1], z, s, none, 1, 1, 0.9f + p[2], p[4], p[5], p[6], p[7] * (0.6f + 0.4f * a));
		}
		f.draw(m.box, pixels, Material.solid(Material.PIXEL).grey(1).p(1.6f, 0.7f, 0), null);
		// the first pixel's light
		float point = Keys.ramp(t, 0.1f, 0.4f) * (1 - 0.7f * Keys.ramp(t, 1.5f, 3.0f));
		f.draw(m.quad, glow, Material.light(Material.GLOW).color(0.7f, 0.95f, 1f).p(30, 6, 0.4f).sprite(), new Matrix4f().scale(2.2f * point));
	}

	@Override
	public void close() {
		pixels.close();
		glow.close();
	}
}
