package pro.minehouse.menu.ui;

/** A checkbox or switch (node "check"): what the server said, what the player set, when it flipped (for the knob sliding over). */
public final class UiCheck {
	/** How long the knob takes to slide over, ms. */
	public static final long SLIDE_MS = 140;

	public final boolean server;
	public boolean value;
	/** When the player last flipped it; long ago at first, so it stands still. */
	public long flippedAt = Long.MIN_VALUE / 2;
	/** Where the knob was when it was flipped. */
	private float from;

	public UiCheck(boolean server) {
		this.server = server;
		this.value = server;
		this.from = server ? 1 : 0;
	}

	public void flip(long now) {
		// Flipped back mid-slide: go on from where the knob is, not from the far end.
		from = position(now);
		value = !value;
		flippedAt = now;
	}

	/** Where the knob is, 0 (off) to 1 (on), eased. */
	public float position(long now) {
		float t = Math.max(0, Math.min(1, (now - flippedAt) / (float) SLIDE_MS));
		float eased = 1 - (1 - t) * (1 - t) * (1 - t);
		return from + ((value ? 1 : 0) - from) * eased;
	}

	public String text() {
		return value ? "true" : "false";
	}
}
