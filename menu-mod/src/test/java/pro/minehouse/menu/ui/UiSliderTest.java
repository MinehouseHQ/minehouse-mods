package pro.minehouse.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiSliderTest {
	@Test
	void snapsToStepsInRange() {
		UiSlider s = new UiSlider(0.8, 1.2, 0.05, 1.03);
		assertEquals(1.05, s.value);
		assertEquals("1.05", s.text());
		assertTrue(s.setFraction(1.5));
		assertEquals(1.2, s.value);
		assertEquals("1.20", s.text());
		assertFalse(s.nudge(1));
		assertTrue(s.nudge(-1));
		assertEquals(1.15, s.value);
	}

	@Test
	void wholeNumbersHaveNoPoint() {
		UiSlider s = new UiSlider(0, 100, 10, 47);
		assertEquals(50, s.value);
		assertEquals("50", s.text());
		assertEquals(0.5, s.fraction());
	}

	@Test
	void noStepMovesFreely() {
		UiSlider s = new UiSlider(0, 1, 0, 2);
		assertEquals(1, s.value);
		s.setFraction(0.25);
		assertEquals("0.25", s.text());
	}
}
