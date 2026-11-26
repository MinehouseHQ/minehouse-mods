package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class FrameBlendTest {
	@Test
	void waitForTheMountChangeRatherThanBlendIntoAPoseOnAnotherBone() {
		for (int f : new int[]{286, 651, 975, 1370}) {
			var pick = new CosmeticMotion.Pick(f, f + 1, 1f); // e.g. late equipment/display packet
			assertEquals(CosmeticMotion.Pick.of(f), pick.forMount("karat"));
			assertSame(pick, pick.forMount("ordinary_hat"));
		}
	}

	@Test
	void blendNormallyOnTheSamePerchIncludingTheLoopSeam() {
		for (int f : new int[]{0, 287, 652, 976, 1377}) {
			var pick = new CosmeticMotion.Pick(f, (f + 1) % 1378, .6f);
			assertSame(pick, pick.forMount("karat"));
		}
	}
}
