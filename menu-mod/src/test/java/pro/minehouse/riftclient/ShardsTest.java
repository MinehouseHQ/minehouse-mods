package pro.minehouse.riftclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ShardsTest {
	@Test
	void threePanesAndSliversSameEveryTime() {
		List<Shards.Shard> a = Shards.generate(42), b = Shards.generate(42);
		assertEquals(a.size(), b.size());
		long slivers = a.stream().filter(Shards.Shard::sliver).count();
		assertEquals(16, slivers);
		// 8 + 7 + 7 wedges, two missing in each pane, some split in two.
		assertTrue(a.size() - slivers >= 16 && a.size() - slivers <= 32, "pieces " + a.size());
		for (int i = 0; i < a.size(); i++) {
			assertTrue(java.util.Arrays.equals(a.get(i).poly(), b.get(i).poly()));
		}
	}

	@Test
	void piecesFlyOutAndStayRoundTheMiddle() {
		float[] out = new float[(Shards.maxCorners(Shards.generate(7)) + 1) * 3];
		for (Shards.Shard s : Shards.generate(7)) {
			assertFalse(Shards.place(s, 0, 0, true, out), "nothing out before it opens");
			assertTrue(Shards.place(s, 1, 0, true, out));
			for (int k = 0; k <= s.corners(); k++) {
				double r = Math.sqrt(out[k * 3] * out[k * 3] + out[k * 3 + 1] * out[k * 3 + 1] + out[k * 3 + 2] * out[k * 3 + 2]);
				assertTrue(r < 2.2, "corner at " + r);
			}
		}
	}

	@Test
	void eachPieceStaysFlat() {
		float[] out = new float[(Shards.maxCorners(Shards.generate(3)) + 1) * 3];
		for (Shards.Shard s : Shards.generate(3)) {
			Shards.place(s, 1, 12.5f, false, out);
			int c = s.corners();
			// Normal from the centre and two corners; every corner lies in that plane.
			float ax = out[0] - out[c * 3], ay = out[1] - out[c * 3 + 1], az = out[2] - out[c * 3 + 2];
			int h = c / 2;
			float bx = out[h * 3] - out[c * 3], by = out[h * 3 + 1] - out[c * 3 + 1], bz = out[h * 3 + 2] - out[c * 3 + 2];
			float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
			float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			for (int k = 0; k < c; k++) {
				float d = ((out[k * 3] - out[c * 3]) * nx + (out[k * 3 + 1] - out[c * 3 + 1]) * ny + (out[k * 3 + 2] - out[c * 3 + 2]) * nz) / l;
				assertEquals(0, d, 1e-4);
			}
		}
	}

	@Test
	void openingOvershootsThenSettles() {
		assertEquals(0, Shards.easeOutBack(0), 1e-6);
		assertEquals(1, Shards.easeOutBack(1), 1e-6);
		assertTrue(Shards.easeOutBack(0.7f) > 1);
	}
}
