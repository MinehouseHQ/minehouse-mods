package pro.minehouse.menu.poker;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Something on the poker table that moves: a card or a pile of chips. Position is in table units
 * (the felt's half axes are 1), so the screen can be resized; moves are queued with a start time
 * and each one starts from wherever the last one left it.
 */
final class Anim {
	/** x, y (table units), scale, rotation (rad), flip (0 face up, 1 face down), alpha. */
	float x, y, s, r, f, a;
	/** The card's code ("AS", "??" lying face down) or, for chips, unused. */
	String code = "??";
	/** Chips in the pile, for chip anims. */
	long amount;

	private record Move(long start, int dur, float x, float y, float s, float r, float f, float a, float arc, String code, long amount, boolean jump) {
	}

	private final Deque<Move> queue = new ArrayDeque<>();
	private Move current;
	private float fx, fy, fs, fr, ff, fa;

	Anim(float x, float y, float s, float r, float f, float a) {
		this.x = fx = x;
		this.y = fy = y;
		this.s = fs = s;
		this.r = fr = r;
		this.f = ff = f;
		this.a = fa = a;
	}

	/** Moves there at {@code start} (ms) over {@code dur} ms, rising by {@code arc} table units on the way. */
	void go(long start, int dur, float x, float y, float s, float r, float f, float a, float arc) {
		queue.add(new Move(start, dur, x, y, s, r, f, a, arc, null, Long.MIN_VALUE, false));
	}

	/** Like {@link #go}, but the code (or chip amount) changes when it starts. */
	void go(long start, int dur, float x, float y, float s, float r, float f, float a, float arc, String code, long amount) {
		queue.add(new Move(start, dur, x, y, s, r, f, a, arc, code, amount, false));
	}

	/** Puts it there at once at {@code start}. */
	void jump(long start, float x, float y, float s, float r, float f, float a, String code, long amount) {
		queue.add(new Move(start, 0, x, y, s, r, f, a, 0, code, amount, true));
	}

	/** Drops queued moves and stops where it is now. */
	void settle(long now) {
		update(now);
		queue.clear();
		current = null;
		fx = x;
		fy = y;
		fs = s;
		fr = r;
		ff = f;
		fa = a;
	}

	/** Where the last queued move ends (for chaining). */
	boolean busy(long now) {
		return current != null || !queue.isEmpty();
	}

	void update(long now) {
		while (true) {
			if (current != null) {
				float t = current.dur() <= 0 ? 1 : Math.min(1, (now - current.start()) / (float) current.dur());
				apply(current, t);
				if (t < 1) return;
				fx = x;
				fy = y;
				fs = s;
				fr = r;
				ff = f;
				fa = a;
				current = null;
			}
			Move next = queue.peekFirst();
			if (next == null || next.start() > now) return;
			queue.removeFirst();
			current = next;
			if (next.code() != null) code = next.code();
			if (next.amount() != Long.MIN_VALUE) amount = next.amount();
			if (next.jump()) {
				fx = next.x();
				fy = next.y();
				fs = next.s();
				fr = next.r();
				ff = next.f();
				fa = next.a();
			}
		}
	}

	private void apply(Move m, float t) {
		float e = easeOut(t);
		x = lerp(fx, m.x(), e);
		y = lerp(fy, m.y(), e) - m.arc() * (float) Math.sin(Math.PI * t);
		s = lerp(fs, m.s(), e) * (1 + 0.25F * m.arc() * (float) Math.sin(Math.PI * t) * 4);
		r = lerp(fr, m.r(), e);
		f = lerp(ff, m.f(), smooth(t));
		a = lerp(fa, m.a(), t);
	}

	static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	static float easeOut(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}

	static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}
}
