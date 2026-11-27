package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class RouteLineTest {
	@Test
	void straightLineKeepsItsLengthAndDirection() {
		RouteLine line = RouteLine.of(List.of(new int[] {0, 64, 0}, new int[] {1, 64, 0}, new int[] {2, 64, 0}, new int[] {3, 64, 0}));
		assertEquals(3, line.length(), 1e-9);
		RouteLine.At mid = line.at(1.5);
		assertEquals(2.0, mid.x(), 1e-9);
		assertEquals(64, mid.y(), 1e-9);
		assertEquals(1, mid.dx(), 1e-9);
		assertEquals(1.5, line.project(2.0, 65, 0.9), 1e-9);
	}

	@Test
	void stepsBecomeSlopesAndEndsStay() {
		RouteLine line = RouteLine.of(List.of(new int[] {0, 64, 0}, new int[] {1, 64, 0}, new int[] {2, 65, 0}, new int[] {3, 65, 0}));
		RouteLine.At start = line.at(0), end = line.at(line.length());
		assertEquals(0.5, start.x(), 1e-9);
		assertEquals(3.5, end.x(), 1e-9);
		assertEquals(65, end.y(), 1e-9);
		// Nowhere a vertical jump: y changes no faster than the slope of one step.
		for (int i = 1; i < line.size(); i++) {
			double dy = Math.abs(line.ys[i] - line.ys[i - 1]);
			double run = Math.abs(line.xs[i] - line.xs[i - 1]);
			assertTrue(dy <= run + 1e-9, "slope at " + i);
		}
	}

	@Test
	void ladderPointsUp() {
		RouteLine line = RouteLine.of(List.of(new int[] {0, 64, 0}, new int[] {0, 65, 0}, new int[] {0, 66, 0}));
		assertEquals(1, line.at(1).dy(), 1e-9);
	}

	@Test
	void packetParsesAndClears() {
		var shown = WardrobeRoute.parse("{\"room\":[10.5,64,3.5],\"name\":\"1\",\"complete\":true,\"points\":[[0,64,0],[1,64,0]]}", null);
		assertNotNull(shown);
		assertTrue(shown.complete());
		assertEquals(10.5, shown.room().x, 1e-9);
		assertEquals(2, shown.line().size());
		var again = WardrobeRoute.parse("{\"room\":[10.5,64,3.5],\"complete\":false,\"points\":[]}", shown);
		assertNull(again.line());
		assertEquals(shown.since(), again.since());
		assertNull(WardrobeRoute.parse("{}", shown));
	}

	@Test
	void fadeAndColours() {
		assertEquals(0, WardrobeRoute.fade(0, 10), 1e-6);
		assertEquals(1, WardrobeRoute.fade(5, 10), 1e-6);
		assertEquals(0, WardrobeRoute.fade(5, 0), 1e-6);
		assertEquals(0xFF000000 | 0x123456, WardrobeRoute.argb(1, 0x123456));
		assertEquals(0x808080, WardrobeRoute.mix(0x000000, 0xFFFFFF, 0.5f) & 0xFEFEFE, 0x010101);
	}
}
