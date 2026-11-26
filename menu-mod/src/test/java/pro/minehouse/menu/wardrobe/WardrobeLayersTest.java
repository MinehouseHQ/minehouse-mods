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
	void orderedLayersBecomeFlatFittingKeysAndAllOffUsesOnlyBaseSlots() {
		var state = read("""
				{"worn":{"hat":"legacy_hat","armor":"gold","pet":"dog"},
				 "wornLayers":{"hat":["first","second","third"],"pet":[]}}
				""");
		assertEquals(Map.of("hat", "first", "hat:2", "second", "hat:3", "third", "armor", "gold"), state.worn());
		assertEquals(List.of("hat", "armor"), state.wornSlots());
		assertEquals(2, state.wornPosition("hat", "second"));
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
	void tryingAThirdHatKeepsOthersAndChangingTabsResetsThePreview() {
		var third = item("third", "hat", null, false);
		var state = state(Map.of("hat", "first", "hat:2", "second", "armor", "gold"), third);
		var selection = new WardrobeScreen.Selection();
		selection.pick(state, third, true);
		assertEquals(Map.of("hat", "first", "hat:2", "second", "hat:3", "third", "armor", "gold"), selection.trying);
		selection.update(state, state); // a dye/coin state refresh should not discard a tentative layer
		assertEquals("third", selection.trying.get("hat:3"));
		selection.tab(state, WardrobeScreen.Slot.ARMOR);
		assertEquals(state.worn(), selection.trying);
	}

	@Test
	void aFourthBeltCannotBePreviewedWornOrBought() {
		var fourth = item("fourth", "belt", null, false);
		var owned = item("another", "belt", null, true);
		var state = state(Map.of("belt", "first", "belt:2", "second", "belt:3", "third"), fourth, owned);
		var selection = new WardrobeScreen.Selection();
		selection.pick(state, fourth, true);
		assertEquals(state.worn(), selection.trying);
		assertEquals(WardrobeScreen.Action.NONE, WardrobeScreen.Action.forItem(state, fourth, null));
		assertEquals(WardrobeScreen.Action.NONE, WardrobeScreen.Action.forItem(state, owned, null));
	}

	@Test
	void coveringBackSwitchKeepsTheTwoPlainBackpacksAndSelectsNonPrimaryWornItems() {
		var wings = item("wings", "backpack", "wings", true);
		var cape = item("cape", "backpack", "cape", true);
		var state = state(Map.of("backpack", "first", "backpack:2", "wings", "backpack:3", "third"), wings, cape);
		var selection = new WardrobeScreen.Selection();
		selection.tab(state, WardrobeScreen.Slot.CAPE);
		assertEquals("wings", selection.selected);
		assertEquals(WardrobeScreen.Action.REMOVE, WardrobeScreen.Action.forItem(state, wings, null));
		selection.pick(state, cape, true);
		assertEquals(Map.of("backpack", "first", "backpack:2", "cape", "backpack:3", "third"), selection.trying);
		selection.pick(state, wings, true);
		var after = state(Map.of("backpack", "first", "backpack:2", "third"), wings, cape);
		selection.update(state, after);
		assertNull(selection.selected);
		assertEquals(after.worn(), selection.trying);
	}
}
