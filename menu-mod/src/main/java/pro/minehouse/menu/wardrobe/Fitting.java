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

	/** Whether the pet is being looked at (the «Питомцы» tab): the camera flies over to it. */
	default boolean petFocus() {
		return false;
	}

	/** The drag turn of the fitting pet, degrees (dragging turns the pet while it is looked at). */
	default float petYaw() {
		return 0;
	}

	/** Slot → cosmetic id drawn on the player. */
	Map<String, String> trying();

	/** The drag turn, degrees. */
	float previewYaw();

	/** A colour being tried on this cosmetic, or null. */
	Integer tint(String id);
}
