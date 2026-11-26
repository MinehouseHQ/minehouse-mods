package pro.minehouse.menu.wardrobe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A server snapshot, never a locally toggled preference. Old servers omit the field. */
enum ArmorVisibility {
	UNAVAILABLE, VISIBLE, HIDDEN;

	static ArmorVisibility read(JsonObject state) {
		JsonElement field = state.get("armorHidden");
		if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isBoolean()) {
			return UNAVAILABLE;
		}
		return field.getAsBoolean() ? HIDDEN : VISIBLE;
	}
}
