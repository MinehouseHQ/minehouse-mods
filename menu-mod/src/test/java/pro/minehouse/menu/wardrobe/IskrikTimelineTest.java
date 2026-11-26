package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pro.minehouse.wardrobe.IskrikTimeline;

class IskrikTimelineTest {
	private static final WardrobeNet.Item PET = item("iskrik", 448, 20);
	private static final UUID A = new UUID(1, 2), B = new UUID(1, 3);

	private static WardrobeNet.Item item(String id, int frames, int fps) {
		return new WardrobeNet.Item(id, "backpack", id, 100, true, null, null, frames, fps, null, java.util.Map.of(), false, false);
	}

	private static int pose(UUID wearer, long tick) {
		return PET.frameAt(wearer, tick, 123456L);
	}

	@Test
	void seekingAndRenderingAgainDoesNotReroll() {
		long start = 1_234_567_890L;
		int[] owner = new int[1200];
		for (int i = 0; i < owner.length; i++) owner[i] = pose(A, start + i);
		for (int i = owner.length - 1; i >= 0; i--) {
			pose(B, start + i); // An unrelated wearer and a different wall clock.
			assertEquals(owner[i], PET.frameAt(A, start + i, 987654321L));
			assertEquals(IskrikTimeline.frame(A, start + i), owner[i], "owner/preview disagrees with server timeline");
		}
	}

	@Test
	void wearerChangesDecisionsNotJustPhase() {
		String a = visits(A), b = visits(B);
		// A phase shift of one common sequence would share long contiguous runs of decisions.
		assertFalse(a.contains(b.substring(8, 32)));
		assertNotEquals(a.substring(0, 24), a.substring(24, 48));
	}

	/** Inspect full visits between landings, including transitions across schedule boundaries. */
	private static String visits(UUID wearer) {
		StringBuilder decisions = new StringBuilder();
		Set<Integer> directions = new HashSet<>(), actions = new HashSet<>(), durations = new HashSet<>();
		Set<Integer> rests = new HashSet<>();
		int t = 1;
		while (pose(wearer, t - 1) < 210 || pose(wearer, t) >= 72) {
			assertTrue(++t < 700, "never landed");
		}
		int start = t, lastFire = -1;
		for (int visit = 0; visit < 160; visit++) {
			int landing = t;
			while (pose(wearer, t) < 72) {
				assertTrue(t - landing < 200, "stuck at home");
				assertEquals((t - landing) % 72, pose(wearer, t++));
			}
			int action = pose(wearer, t);
			if (action < 210) {
				between(t - landing, 56, 104);
				int end = switch (action) { case 72 -> 112; case 113 -> 163; case 164 -> 209;
					default -> throw new AssertionError("partial gesture at " + t); };
				for (int frame = action; frame <= end; frame++) assertEquals(frame, pose(wearer, t++));
				int ready = t;
				while (pose(wearer, t) < 72) {
					assertTrue(t - ready < 65, "stuck after gesture");
					assertEquals(t - ready, pose(wearer, t++));
				}
				between(t - ready, 34, 64);
				rests.add(t - ready);
			} else {
				between(t - landing, 56 + 26 + 34, 104 + 26 + 64); // extra 1.3 s nap
				action = 0;
			}
			actions.add(action);
			if (action == 164) {
				if (lastFire >= 0) between(visit - lastFire, 2, 5);
				lastFire = visit;
			}
			assertTrue(visit - lastFire <= 5, "volley overdue");
			int orbit = pose(wearer, t);
			assertTrue(orbit == 210 || orbit == 329, "partial flight");
			directions.add(orbit);
			decisions.append(switch (action) { case 0 -> 'n'; case 72 -> 'w'; case 113 -> 'y'; default -> 's'; });
			for (int frame = orbit; frame < orbit + 119; frame++) assertEquals(frame, pose(wearer, t++));
			assertEquals(0, pose(wearer, t), "must land after one orbit");
			durations.add(t - landing);
		}
		assertEquals(Set.of(0, 72, 113, 164), actions);
		assertEquals(Set.of(210, 329), directions);
		assertTrue(durations.size() > 1 && rests.size() > 1);
		assertTrue(160 * 119.0 / (t - start) < 0.41, "too much time airborne");
		return decisions.toString();
	}

	private static void between(int value, int min, int max) {
		assertTrue(value >= min && value <= max, () -> value + " outside " + min + ".." + max);
	}

	@Test
	void otherItemsStillFollowOnlyTheOriginalWallClock() {
		assertEquals(0, item("still", 1, 20).frameAt(A, 55, 875));
		assertEquals(4, item("karat", 1378, 5).frameAt(B, 999, 875));
		assertEquals(0, item("karat", 1378, 5).frameAt(A, 0, 275600));
	}
}
