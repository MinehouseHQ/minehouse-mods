package pro.minehouse.riftclient.cine;

/** A film drawn by the client: for each moment it places the camera and the things in front of it. */
public interface Film extends AutoCloseable {
	/** How long it runs, in seconds. */
	float length();

	/** Builds the frame at this moment. */
	void frame(Frame f, float seconds);

	@Override
	default void close() {
	}
}
