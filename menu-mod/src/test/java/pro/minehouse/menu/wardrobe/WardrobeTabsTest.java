package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pro.minehouse.menu.wardrobe.WardrobeScreen.Slot;

class WardrobeTabsTest {
	private static WardrobeNet.Item cosmetic(String id, String slot, String kind) {
		return new WardrobeNet.Item(id, slot, id, 100, false, null, null, 1, 20, kind, Map.of(), false, false);
	}

	@Test void clickingEachTabShowsOnlyItsOwnGridInAMixedCatalog() {
		var items = List.of(cosmetic("armor_gold_curb", "armor", null), cosmetic("karat", "hat", null),
				cosmetic("armor_diamond_curb", "armor", null), cosmetic("shorokh", "pet", null),
				cosmetic("armor_netherite_curb", "armor", null), cosmetic("angel_wings", "backpack", "wings"),
				cosmetic("armor_iron_curb", "armor", null), cosmetic("shoulder_cat_mokko", "shoulder", null),
				cosmetic("armor_leather_curb", "armor", null), cosmetic("spider_legs", "belt", null));
		var expected = Map.of(Slot.ARMOR, List.of("armor_gold_curb", "armor_diamond_curb", "armor_netherite_curb", "armor_iron_curb", "armor_leather_curb"),
				Slot.PET, List.of("shorokh"), Slot.HAT, List.of("karat"),
				Slot.SHOULDER, List.of("shoulder_cat_mokko"), Slot.BACKPACK, List.of("angel_wings"), Slot.BELT, List.of("spider_legs"));
		Slot active = Slot.HAT;
		for (Slot target : List.of(Slot.ARMOR, Slot.PET, Slot.ARMOR, Slot.SHOULDER, Slot.BACKPACK, Slot.BELT, Slot.HAT)) {
			var row = new WardrobeScreen.TabRow(8, 8, active, 30); // icon-only minimum-width row
			var box = row.tab(target);
			active = row.hit(box.x0() + box.w() / 2, (box.y0() + box.y1()) / 2);
			assertEquals(target, active);
			assertEquals(expected.get(target), active.grid(items).stream().map(WardrobeNet.Item::id).toList());
		}
	}

	@Test void armorVisibilityControlOnlyAppearsOnItsOwnTab() {
		for (var visibility : ArmorVisibility.values()) {
			var active = Slot.HAT;
			for (var target : List.of(Slot.ARMOR, Slot.PET, Slot.ARMOR, Slot.SHOULDER, Slot.BACKPACK, Slot.BELT, Slot.HAT)) {
				var row = new WardrobeScreen.TabRow(8, 8, active, 30);
				var box = row.tab(target);
				active = row.hit(box.x0() + box.w() / 2, (box.y0() + box.y1()) / 2);
				assertEquals(target, active);
				assertEquals(target == Slot.ARMOR && visibility != ArmorVisibility.UNAVAILABLE,
						WardrobeScreen.showsArmorToggle(active, visibility), target + " " + visibility);
			}
		}
	}

	@Test void switchingFromAPetPreviewToLockedArmorRestoresTheWornOutfit() {
		var pet = cosmetic("shorokh", "pet", null);
		var gold = cosmetic("armor_gold_curb", "armor", null);
		var iron = cosmetic("armor_iron_curb", "armor", null);
		var worn = Map.of("armor", iron.id(), "shoulder", "shoulder_cat_mokko");
		var state = new WardrobeNet.State(0, "", List.of(pet, gold, iron), worn, ArmorVisibility.UNAVAILABLE);
		var selection = new WardrobeScreen.Selection();
		selection.pick(state, pet, true);
		assertEquals(pet.id(), selection.petPreviewId(state));
		assertEquals(iron.id(), selection.trying.get("armor"));
		selection.tab(state, Slot.ARMOR);
		assertNull(selection.petPreviewId(state), "the server must fall back to the equipped pet");
		assertEquals(iron.id(), selection.selected);
		selection.pick(state, gold, false);
		assertEquals(gold.id(), selection.selected, "locked armor can still be inspected");
		assertEquals(worn, selection.trying);
		var equipped = new WardrobeNet.State(0, "", state.items(), Map.of("armor", iron.id(), "pet", pet.id()), ArmorVisibility.UNAVAILABLE);
		selection.tab(equipped, Slot.PET);
		assertEquals(pet.id(), selection.selected);
		assertNull(selection.petPreviewId(equipped), "an equipped pet needs no override");
	}

	private static List<String> titles(String slot, String kind) {
		var item = cosmetic("item", slot, kind);
		return Arrays.stream(WardrobeScreen.Slot.values()).filter(s -> s.lists(item)).map(s -> s.title).toList();
	}

	@Test void groundPetHasExactlyOneTabAndShoulderItemsHaveTheirOwn() {
		assertEquals(List.of("Питомцы"), titles("pet", null));
		assertEquals(List.of("Тело"), titles("backpack", null));
		assertEquals(List.of("Тело"), titles("backpack", "wings"));
		assertEquals(List.of("Тело"), titles("backpack", "cape"));
		assertEquals(List.of("Голова"), titles("hat", null));
		assertEquals(List.of("Плечо"), titles("shoulder", null));
		assertEquals(List.of("Пояс"), titles("belt", null));
		assertTrue(titles("future-slot", null).isEmpty());
	}
}
