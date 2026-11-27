package pro.minehouse.menu.ui;

import java.util.function.ToIntFunction;

/**
 * What is typed into one text field of a server window (node "input"): the text, the caret and how
 * far the text is scrolled in its box. No Minecraft classes, so it is unit tested.
 */
final class UiField {
	/** The value the server sent last: a different one replaces what was typed. */
	final String server;
	String text;
	int cursor;
	/** The first character shown in the box. */
	int scroll;
	int max;
	final boolean digits;

	UiField(String value, int max, boolean digits) {
		this.max = Math.max(1, Math.min(256, max));
		this.digits = digits;
		this.server = value;
		this.text = "";
		insert(value);
		this.cursor = text.length();
	}

	/** Puts text in at the caret: line breaks and, for digits, everything but digits (and a leading minus) left out; cut at max. */
	void insert(String s) {
		if (s == null) return;
		StringBuilder add = new StringBuilder();
		s.codePoints().forEach(c -> {
			if (c == '\n' || c == '\r' || c == '\t' || c < 32 || c == 127) return;
			if (digits && !Character.isDigit(c) && !(c == '-' && cursor == 0 && add.isEmpty() && !text.startsWith("-"))) return;
			add.appendCodePoint(c);
		});
		int room = max - text.length();
		if (room <= 0 || add.isEmpty()) return;
		String in = add.length() > room ? add.substring(0, room) : add.toString();
		if (Character.isHighSurrogate(in.charAt(in.length() - 1))) in = in.substring(0, in.length() - 1);
		text = text.substring(0, cursor) + in + text.substring(cursor);
		cursor += in.length();
	}

	void clear() {
		text = "";
		cursor = 0;
		scroll = 0;
	}

	void backspace(boolean word) {
		int from = word ? wordLeft() : prev(cursor);
		if (from == cursor) return;
		text = text.substring(0, from) + text.substring(cursor);
		cursor = from;
	}

	void delete(boolean word) {
		int to = word ? wordRight() : next(cursor);
		if (to == cursor) return;
		text = text.substring(0, cursor) + text.substring(to);
	}

	void left(boolean word) {
		cursor = word ? wordLeft() : prev(cursor);
	}

	void right(boolean word) {
		cursor = word ? wordRight() : next(cursor);
	}

	void home() {
		cursor = 0;
	}

	void end() {
		cursor = text.length();
	}

	private int prev(int i) {
		return i <= 0 ? 0 : Character.isLowSurrogate(text.charAt(i - 1)) && i >= 2 ? i - 2 : i - 1;
	}

	private int next(int i) {
		return i >= text.length() ? text.length() : Character.isHighSurrogate(text.charAt(i)) && i + 2 <= text.length() ? i + 2 : i + 1;
	}

	private int wordLeft() {
		int i = cursor;
		while (i > 0 && text.charAt(i - 1) == ' ') i--;
		while (i > 0 && text.charAt(i - 1) != ' ') i--;
		return i;
	}

	private int wordRight() {
		int i = cursor;
		while (i < text.length() && text.charAt(i) == ' ') i++;
		while (i < text.length() && text.charAt(i) != ' ') i++;
		return i;
	}

	/** Moves the scroll so the caret is inside a box {@code room} pixels wide. */
	void scrollTo(int room, ToIntFunction<String> width) {
		cursor = Math.max(0, Math.min(cursor, text.length()));
		scroll = Math.max(0, Math.min(scroll, cursor));
		while (scroll < cursor && width.applyAsInt(text.substring(scroll, cursor)) > room - 1) scroll = next(scroll);
	}

	/** A click {@code dx} pixels from the start of the shown text: the caret goes to the nearest gap. */
	void clickAt(double dx, ToIntFunction<String> width) {
		int best = scroll;
		double bestD = Double.MAX_VALUE;
		for (int i = scroll; i <= text.length(); i = i == text.length() ? i + 1 : next(i)) {
			double d = Math.abs(width.applyAsInt(text.substring(scroll, Math.min(i, text.length()))) - dx);
			if (d < bestD) {
				bestD = d;
				best = Math.min(i, text.length());
			}
		}
		cursor = best;
	}
}
