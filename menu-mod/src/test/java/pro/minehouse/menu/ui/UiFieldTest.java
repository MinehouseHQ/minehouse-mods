package pro.minehouse.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UiFieldTest {
	@Test
	void typesAtTheCaretAndStopsAtMax() {
		UiField f = new UiField("ab", 4, false);
		assertEquals(2, f.cursor);
		f.left(false);
		f.insert("XYZ");
		assertEquals("aXYb", f.text);
		assertEquals(3, f.cursor);
	}

	@Test
	void digitsOnlyWithAMinusInFront() {
		UiField f = new UiField("", 8, true);
		f.insert("-1a2 3");
		assertEquals("-123", f.text);
		f.insert("-4");
		assertEquals("-1234", f.text);
	}

	@Test
	void lineBreaksAreLeftOut() {
		UiField f = new UiField("", 32, false);
		f.insert("one\ntwo\tthree");
		assertEquals("onetwothree", f.text);
	}

	@Test
	void backspaceAndDeleteByWord() {
		UiField f = new UiField("hello big world", 64, false);
		f.backspace(true);
		assertEquals("hello big ", f.text);
		f.home();
		f.delete(true);
		assertEquals(" big ", f.text);
		f.backspace(false);
		assertEquals(" big ", f.text);
	}

	@Test
	void scrollKeepsTheCaretInTheBox() {
		UiField f = new UiField("0123456789", 64, false);
		f.scrollTo(30, s -> s.length() * 6);
		assertEquals(10, f.cursor);
		assertEquals(6, f.scroll);
		f.home();
		f.scrollTo(30, s -> s.length() * 6);
		assertEquals(0, f.scroll);
	}

	@Test
	void clickPutsTheCaretInTheNearestGap() {
		UiField f = new UiField("abcdef", 64, false);
		f.clickAt(13, s -> s.length() * 6);
		assertEquals(2, f.cursor);
		f.clickAt(500, s -> s.length() * 6);
		assertEquals(6, f.cursor);
	}

	@Test
	void severalLinesKeepBreaksAndWrapAtSpaces() {
		UiField f = new UiField("", 100, false, true);
		f.insert("aaa bbb ccc\r\ndd");
		assertEquals("aaa bbb ccc\ndd", f.text);
		var lines = f.wrap(42, s -> s.length() * 6);
		assertEquals(3, lines.size());
		assertEquals("aaa bbb", f.text.substring(lines.get(0)[0], lines.get(0)[1]));
		assertEquals("ccc", f.text.substring(lines.get(1)[0], lines.get(1)[1]));
		assertEquals("dd", f.text.substring(lines.get(2)[0], lines.get(2)[1]));
		assertEquals(2, UiField.lineOf(lines, f.cursor));
		f.vertical(-1, lines, s -> s.length() * 6);
		assertEquals(10, f.cursor);
		f.vertical(-1, lines, s -> s.length() * 6);
		assertEquals(2, f.cursor);
	}

	@Test
	void aLongWordBreaksAnywhere() {
		UiField f = new UiField("abcdefghij", 100, false, true);
		var lines = f.wrap(24, s -> s.length() * 6);
		assertEquals(3, lines.size());
		assertEquals(4, lines.get(0)[1]);
	}

	@Test
	void oneLineFieldsDropLineBreaks() {
		UiField f = new UiField("", 100, false, false);
		f.insert("a\nb");
		assertEquals("ab", f.text);
	}
}
