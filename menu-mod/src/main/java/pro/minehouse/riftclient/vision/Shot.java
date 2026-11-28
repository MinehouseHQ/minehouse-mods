package pro.minehouse.riftclient.vision;

import pro.minehouse.riftclient.cine.Frame;

/** One shot of the vision: its own little world, shown from its start to its end (seconds of the film). */
abstract class Shot implements AutoCloseable {
	final float start, end;

	Shot(float start, float end) {
		this.start = start;
		this.end = end;
	}

	/** Puts together the frame at t seconds of the film (start <= t < end). */
	abstract void frame(Frame f, float t);

	@Override
	public void close() {
	}
}
