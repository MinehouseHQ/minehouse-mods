package pro.minehouse.menu.wardrobe;

import java.util.Map;

/**
 * A screen that shows the player in the fitting room: the camera in front of them
 * ({@link WardrobeCamera}), standing still, turned by dragging, wearing what is tried on
 * ({@link WardrobeSelf}). The client's own wardrobe screen, or a server window with scene "fitting".
 */
public interface Fitting {
	/** Whether the fitting-room view is on now. */
	default boolean fittingScene() {
		return true;
	}

	/** Slot → cosmetic id drawn on the player. */
	Map<String, String> trying();

	/** The drag turn, degrees. */
	float previewYaw();

	/** A colour being tried on this cosmetic, or null. */
	Integer tint(String id);
}
