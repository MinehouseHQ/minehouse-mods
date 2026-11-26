package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class WardrobeLayersTest {
	private static WardrobeNet.State read(JsonObject fields) {
		fields.addProperty("coins", 0);
		fields.addProperty("store", "");
		fields.add("items", new com.google.gson.JsonArray());
		return WardrobeNet.parseState(fields);
	}

	private static WardrobeNet.State read(String json) {
		return read(JsonParser.parseString(json).getAsJsonObject());
	}

	@Test
	void onlyTheFirstOfOldLayersIsKeptOnePerSlot() {
		var state = read("""
				{"worn":{"hat":"legacy_hat","armor":"gold","pet":"dog"},
				 "wornLayers":{"hat":["first","second","third"],"pet":[]}}
				""");
		assertEquals(Map.of("hat", "first", "armor", "gold"), state.worn());
		assertEquals(List.of("hat", "armor"), state.wornSlots());
		assertEquals(1, state.wornPosition("hat", "first"));
		assertEquals(0, state.wornPosition("hat", "second"));
		assertEquals(0, state.wornPosition("hat", "legacy_hat"));
	}

	@Test
	void removeUsesIdOnlyWhenServerAdvertisesLayersEvenIfEmpty() {
		var legacy = read("""
				{"worn":{"hat":"first"}}
				""");
		var layered = read("""
				{"worn":{"hat":"first"},"wornLayers":{}}
				""");
		assertEquals(Map.of("hat", "first"), legacy.worn());
		assertFalse(legacy.multiWear());
		assertTrue(layered.multiWear());
		assertEquals(JsonParser.parseString("""
				{"action":"remove","slot":"hat"}
				"""), WardrobeNet.removeAction(legacy, "hat", "first"));
		assertEquals(JsonParser.parseString("""
				{"action":"remove","slot":"hat","id":"first"}
				"""), WardrobeNet.removeAction(layered, "hat", "first"));
	}

	private static WardrobeNet.Item item(String id, String slot, String kind, boolean owned) {
		return new WardrobeNet.Item(id, slot, id, 2, owned, null, null, 1, 20, kind, Map.of(), false, false);
	}

	private static WardrobeNet.State state(Map<String, String> worn, WardrobeNet.Item... items) {
		return new WardrobeNet.State(10, "", List.of(items), worn, ArmorVisibility.UNAVAILABLE, true);
	}

	@Test
	void tryingAHatReplacesTheWornOneAndChangingTabsResetsThePreview() {
		var other = item("other", "hat", null, false);
		var state = state(Map.of("hat", "first", "armor", "gold"), other);
		var selection = new WardrobeScreen.Selection();
		selection.pick(state, other, true);
		assertEquals(Map.of("hat", "other", "armor", "gold"), selection.trying);
		selection.update(state, state); // a dye/coin state refresh should not discard the preview
		assertEquals("other", selection.trying.get("hat"));
		selection.tab(state, WardrobeScreen.Slot.ARMOR);
		assertEquals(state.worn(), selection.trying);
	}

	@Test
	void aSecondBeltReplacesTheFirst() {
		var next = item("next", "belt", null, true);
		var state = state(Map.of("belt", "first"), next);
		var selection = new WardrobeScreen.Selection();
		selection.pick(state, next, true);
		assertEquals(Map.of("belt", "next"), selection.trying);
		assertNotEquals(WardrobeScreen.Action.NONE, WardrobeScreen.Action.forItem(state, next, null));
	}
}
