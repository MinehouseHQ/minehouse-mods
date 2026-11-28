package pro.minehouse.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiCheckTest {
	@Test
	void standsWhereTheServerPutIt() {
		assertEquals(1f, new UiCheck(true).position(0));
		assertEquals(0f, new UiCheck(false).position(0));
		assertEquals("true", new UiCheck(true).text());
	}

	@Test
	void knobSlidesOver() {
		UiCheck c = new UiCheck(false);
		c.flip(1000);
		assertEquals("true", c.text());
		assertEquals(0f, c.position(1000));
		float mid = c.position(1000 + UiCheck.SLIDE_MS / 2);
		assertTrue(mid > 0.5f && mid < 1f, "eased: past half way at half time, " + mid);
		assertEquals(1f, c.position(1000 + UiCheck.SLIDE_MS));
	}

	@Test
	void flippedBackMidSlideGoesOnFromWhereItIs() {
		UiCheck c = new UiCheck(false);
		c.flip(0);
		float at = c.position(40);
		c.flip(40);
		assertEquals("false", c.text());
		assertEquals(at, c.position(40), 1e-4f);
		assertEquals(0f, c.position(40 + UiCheck.SLIDE_MS));
	}
}
