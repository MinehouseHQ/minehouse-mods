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
	/** Several lines: Enter breaks the line (Ctrl+Enter sends), long lines wrap. */
	final boolean multiline;
	/** Multiline: the first wrapped line shown in the box. */
	int topLine;

	/** The longest text a field takes (the server cuts at the same). */
	static final int MAX = 2000;

	UiField(String value, int max, boolean digits) {
		this(value, max, digits, false);
	}

	UiField(String value, int max, boolean digits, boolean multiline) {
		this.max = Math.max(1, Math.min(MAX, max));
		this.digits = digits;
		this.multiline = multiline && !digits;
		this.server = value;
		this.text = "";
		insert(value);
		this.cursor = text.length();
	}

	/** Puts text in at the caret: line breaks and, for digits, everything but digits (and a leading minus) left out; cut at max. */
	void insert(String s) {
		if (s == null) return;
		StringBuilder add = new StringBuilder();
		if (multiline) s = s.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
		s.codePoints().forEach(c -> {
			if (c == '\n' && multiline) {
				add.append('\n');
				return;
			}
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

	// ------------------------------------------------------------------ several lines

	/**
	 * The text broken into lines that fit {@code room} pixels: [start, end) of each, at line breaks
	 * and, when a line is too long, after its last space (or anywhere if a word alone is too long).
	 * The break itself (newline or space) belongs to no line.
	 */
	java.util.List<int[]> wrap(int room, ToIntFunction<String> width) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		int s = 0;
		int n = text.length();
		while (true) {
			int hard = text.indexOf('\n', s);
			int lineEnd = hard < 0 ? n : hard;
			int start = s;
			while (true) {
				int end = start;
				while (end < lineEnd && width.applyAsInt(text.substring(start, next(end))) <= room) end = next(end);
				if (end >= lineEnd) {
					out.add(new int[] {start, lineEnd});
					break;
				}
				int space = text.lastIndexOf(' ', end);
				if (space > start) {
					out.add(new int[] {start, space});
					start = space + 1;
				} else {
					if (end == start) end = next(start);
					out.add(new int[] {start, end});
					start = end;
				}
			}
			if (hard < 0) break;
			s = hard + 1;
		}
		return out;
	}

	/** The line the caret is on. */
	static int lineOf(java.util.List<int[]> lines, int cursor) {
		for (int i = 0; i < lines.size(); i++) {
			if (cursor <= lines.get(i)[1]) return i;
		}
		return lines.size() - 1;
	}

	/** Up (-1) or down (+1) a line, keeping as near the same x as there is. */
	void vertical(int dir, java.util.List<int[]> lines, ToIntFunction<String> width) {
		int line = lineOf(lines, cursor);
		int target = line + dir;
		if (target < 0) {
			cursor = 0;
			return;
		}
		if (target >= lines.size()) {
			cursor = text.length();
			return;
		}
		int[] from = lines.get(line);
		int x = width.applyAsInt(text.substring(from[0], Math.max(from[0], Math.min(cursor, from[1]))));
		cursor = nearest(lines.get(target), x, width);
	}

	/** A click in line {@code line} (counted in the whole text), {@code dx} pixels from its start. */
	void clickAt(java.util.List<int[]> lines, int line, double dx, ToIntFunction<String> width) {
		line = Math.max(0, Math.min(lines.size() - 1, line));
		cursor = nearest(lines.get(line), dx, width);
	}

	private int nearest(int[] range, double x, ToIntFunction<String> width) {
		int best = range[0];
		double bestD = Double.MAX_VALUE;
		for (int i = range[0]; i <= range[1]; i = i == range[1] ? i + 1 : next(i)) {
			int at = Math.min(i, range[1]);
			double d = Math.abs(width.applyAsInt(text.substring(range[0], at)) - x);
			if (d < bestD) {
				bestD = d;
				best = at;
			}
		}
		return best;
	}

	/** Scrolls so the caret's line is among the {@code rows} shown. */
	void scrollLinesTo(java.util.List<int[]> lines, int rows) {
		int line = lineOf(lines, cursor);
		if (line < topLine) topLine = line;
		if (line >= topLine + rows) topLine = line - rows + 1;
		topLine = Math.max(0, Math.min(topLine, Math.max(0, lines.size() - rows)));
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
