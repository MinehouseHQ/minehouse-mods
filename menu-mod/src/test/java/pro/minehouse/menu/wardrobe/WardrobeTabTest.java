package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

class WardrobeTabTest {
	@BeforeAll
	static void bootstrapItems() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
				.forEach(DataComponentInitializers.PendingComponents::apply);
	}

	private static WardrobeNet.Item item(String id, String slot, String kind) {
		return new WardrobeNet.Item(id, slot, id, 0, true, null, null, 1, 20, kind, Map.of(), false, false);
	}

	@Test
	void changingTabsDropsTentativeHatButKeepsEquippedCosmetics() {
		var hat = item("hat", "hat", null);
		var style = item("armor_gold_curb", "armor", null);
		var worn = Map.of("backpack", "pack", "belt", "belt");
		var state = new WardrobeNet.State(0, "", List.of(hat, style), worn, ArmorVisibility.UNAVAILABLE);
		var selection = new WardrobeScreen.Selection();
		selection.pick(state, hat, true);
		assertEquals("hat", selection.trying.get("hat"));
		selection.tab(state, WardrobeScreen.Slot.ARMOR);
		assertEquals(worn, selection.trying);
		selection.pick(state, style, true);
		assertEquals(Map.of("backpack", "pack", "belt", "belt", "armor", "armor_gold_curb"), selection.trying);
		assertEquals(worn, state.worn());
	}

	@Test
	void inspectingLockedGoldDoesNotTryItOnOverTheWornStyle() {
		var gold = item("armor_gold_curb", "armor", null);
		var worn = Map.of("armor", "armor_iron_curb", "hat", "hat");
		var state = new WardrobeNet.State(0, "", List.of(gold), worn, ArmorVisibility.UNAVAILABLE);
		var selection = new WardrobeScreen.Selection();
		selection.pick(state, gold, false);
		assertEquals(worn, selection.trying, "inspecting a locked card cannot displace a worn style");
	}

	@Test
	void removingTheSelectedStyleDoesNotTryItOnAgainOnServerUpdate() {
		var style = item("armor_leather_curb", "armor", null);
		var before = new WardrobeNet.State(0, "", List.of(style), Map.of("armor", style.id(), "hat", "real_hat"), ArmorVisibility.UNAVAILABLE);
		var after = new WardrobeNet.State(0, "", List.of(style), Map.of("hat", "real_hat"), ArmorVisibility.UNAVAILABLE);
		var selection = new WardrobeScreen.Selection();
		selection.tab(before, WardrobeScreen.Slot.ARMOR);
		selection.update(before, after);
		assertEquals(after.worn(), selection.trying);
	}

	@Test
	void eachCategoryHasItsOwnIconInOrder() {
		assertEquals(List.of(Items.LEATHER_HELMET, Items.BUNDLE, Items.LEATHER_LEGGINGS, Items.IRON_CHESTPLATE, Items.LEAD),
				Arrays.stream(WardrobeScreen.Slot.values()).map(slot -> slot.stack().getItem()).toList());
	}

	@Test
	void fiveIconsAndTheirLabelsAreReachableOnTheMinimumWidthCard() {
		// Minecraft 26.3 default bitmap font advances: the short titles fit inline beside the icon.
		var widths = Map.of("Голова", 36, "Тело", 24, "Пояс", 24, "Броня", 30, "Питомцы", 43);
		for (var active : WardrobeScreen.Slot.values()) {
			assertEquals(widths.get(active.title) <= 27, new WardrobeScreen.TabRow(8, 8, active, widths.get(active.title)).labelled(), active.title);
			var row = new WardrobeScreen.TabRow(8, 8, active, 30); // too long: icons only
			assertFalse(row.labelled(), active.title);
			int x = 18;
			for (var slot : WardrobeScreen.Slot.values()) {
				var box = row.tab(slot);
				assertEquals(x, box.x0());
				assertEquals(18, box.y0());
				assertEquals(36, box.y1());
				assertTrue(box.w() >= 18, "room for a 16px icon and its border");
				assertEquals(slot, row.hit(x + box.w() / 2, 27), slot.title);
				assertEquals(slot, row.hit(x, 18));
				assertEquals(slot, row.hit(box.x1() - 0.01, 35.99));
				assertEquals(slot.title, row.tooltip(x, 27));
				x = box.x1();
			}
			assertEquals(142, x);
			assertNull(row.hit(17, 27));
			assertNull(row.hit(x, 27));
			assertNull(row.hit(30, 17));
			assertNull(row.hit(30, 36));
			assertNull(row.tooltip(x, 27));
		}
	}

	@Test
	void shortActiveLabelIsInlineOnlyIfItFitsBesideTheIcon() {
		for (var active : WardrobeScreen.Slot.values()) {
			// 124 inside the track - four 18px icons - 16px icon, 3px gap, 6px padding = 27px.
			var row = new WardrobeScreen.TabRow(8, 8, active, 27);
			assertTrue(row.labelled());
			assertFalse(new WardrobeScreen.TabRow(8, 8, active, 28).labelled());
			for (var slot : WardrobeScreen.Slot.values()) {
				var box = row.tab(slot);
				assertEquals(slot == active ? 52 : 18, box.w());
				assertEquals(slot, row.hit(box.x0(), 27));
				assertEquals(slot == active ? null : slot.title, row.tooltip(box.x0(), 27));
			}
		}
	}

	@Test
	void primaryActionNeverWearsLockedArmorButCanRemoveItWithoutEquipment() {
		var gold = item("armor_gold_curb", "armor", null);
		assertEquals(WardrobeScreen.Action.NONE, WardrobeScreen.Action.forItem(gold, false, "Надень золотую броню", 0));
		assertEquals(WardrobeScreen.Action.WEAR, WardrobeScreen.Action.forItem(gold, false, null, 0));
		assertEquals(WardrobeScreen.Action.REMOVE, WardrobeScreen.Action.forItem(gold, true, "Надень золотую броню", 0));
		var hat = new WardrobeNet.Item("hat", "hat", "hat", 10, false, null, null, 1, 20, null, Map.of(), false, false);
		assertEquals(WardrobeScreen.Action.NONE, WardrobeScreen.Action.forItem(hat, false, null, 9));
		assertEquals(WardrobeScreen.Action.BUY, WardrobeScreen.Action.forItem(hat, false, null, 10));
	}

	@Test
	void armorHasItsOwnTabAndWornSlot() {
		for (String id : List.of("armor_gold_curb", "armor_diamond_curb", "armor_netherite_curb", "armor_iron_curb", "armor_leather_curb")) {
			var style = item(id, "armor", null);
			var tab = WardrobeScreen.Slot.of(style);
			assertEquals("Броня", tab.title);
			assertEquals("armor", tab.id);
			assertFalse(tab.lists(item("pack", "backpack", null)));
			assertFalse(tab.lists(item("wings", "backpack", "wings")));
			assertFalse(tab.lists(item("hat", "hat", null)));
			assertFalse(tab.lists(item("belt", "belt", null)));
		}
	}
}
