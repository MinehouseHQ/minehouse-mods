package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

class ArmorStyleTest {
	@BeforeAll
	static void loadVanillaComponentsAndTags() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
				.forEach(DataComponentInitializers.PendingComponents::apply);
		var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		try (var data = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
			TagLoader.loadTagsForExistingRegistries(data, registries).forEach(Registry.PendingTags::apply);
		}
	}

	private static ItemStack piece(Item item, EquipmentSlot slot) {
		var stack = new ItemStack(item);
		stack.set(DataComponents.EQUIPPABLE, Equippable.builder(slot).setAsset(EquipmentAssets.IRON).setSwappable(false).build());
		return stack;
	}

	private static void apply(HumanoidRenderState state, Map<String, String> worn) {
		ArmorStyle.apply(state, worn, slot -> switch (slot) {
			case HEAD -> state.headEquipment;
			case CHEST -> state.chestEquipment;
			case LEGS -> state.legsEquipment;
			case FEET -> state.feetEquipment;
			default -> throw new AssertionError(slot);
		});
	}

	@Test
	void skipsEmptyNonArmorGlidersAndWrongSlotItems() {
		var state = new HumanoidRenderState();
		var head = state.headEquipment = new ItemStack(Items.CARVED_PUMPKIN);
		var chest = state.chestEquipment = new ItemStack(Items.ELYTRA);
		var wrong = state.legsEquipment = piece(Items.IRON_HELMET, EquipmentSlot.LEGS);
		apply(state, Map.of("armor", "armor_iron_curb"));
		assertSame(head, state.headEquipment);
		assertSame(chest, state.chestEquipment);
		assertSame(wrong, state.legsEquipment);
		assertSame(ItemStack.EMPTY, state.feetEquipment);
		var glider = state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
		glider.copyFrom(DataComponents.GLIDER, chest);
		apply(state, Map.of("armor", "armor_iron_curb"));
		assertSame(glider, state.chestEquipment);
	}

	@Test
	void hatTakesPrecedenceButDoesNotDisableTheRestOfTheStyle() {
		var state = new HumanoidRenderState();
		var head = state.headEquipment = new ItemStack(Items.IRON_HELMET);
		var chest = state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
		apply(state, Map.of("hat", "hat"));
		assertSame(chest, state.chestEquipment);
		apply(state, Map.of("armor", "armor_iron_curb", "hat", "hat"));
		assertSame(head, state.headEquipment);
		assertEquals("minehouse-cosmetics:armor_iron_curb", state.chestEquipment.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().toString());
	}

	@Test
	void missingResourcePackNeverHidesWornArmor() {
		var frame = new HumanoidRenderState();
		var helmet = frame.headEquipment = new ItemStack(Items.IRON_HELMET);
		ArmorStyle.apply(frame, Map.of("armor", "armor_iron_curb"), slot -> slot == EquipmentSlot.HEAD ? helmet : ItemStack.EMPTY,
				(key, layer) -> false);
		assertSame(helmet, frame.headEquipment);
	}

	@Test
	void hintChecksActualArmorRatherThanAnythingEquippable() {
		var equipment = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
		equipment.put(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		equipment.put(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
		assertEquals("Надень золотую броню", ArmorStyle.requirement("armor_gold_curb", slot -> equipment.getOrDefault(slot, ItemStack.EMPTY)));
		equipment.put(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
		assertNull(ArmorStyle.requirement("armor_gold_curb", slot -> equipment.getOrDefault(slot, ItemStack.EMPTY)));
	}

	@Test
	void eachStyleUnlocksOnlyForItsOwnPieceInItsOwnSlot() {
		var pieces = Map.of(
				"armor_gold_curb", List.of(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS),
				"armor_diamond_curb", List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
				"armor_netherite_curb", List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS),
				"armor_iron_curb", List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
				"armor_leather_curb", List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS));
		var slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		for (var material : pieces.entrySet()) {
			for (int i = 0; i < slots.size(); i++) {
				var proper = slots.get(i);
				var stack = new ItemStack(material.getValue().get(i));
				for (var actual : EquipmentSlot.values()) {
					for (String id : pieces.keySet()) {
						String need = ArmorStyle.requirement(id, slot -> slot == actual ? stack : ItemStack.EMPTY);
						assertEquals(actual == proper && id.equals(material.getKey()), need == null, id + ": " + stack + " in " + actual);
					}
				}
			}
		}
	}

	@Test
	void reEquippingRestoresTheSavedStyleUnlessExplicitlyRemoved() {
		var helmet = new ItemStack(Items.GOLDEN_HELMET);
		var saved = Map.of("armor", "armor_gold_curb");
		for (var real : List.of(helmet, ItemStack.EMPTY, new ItemStack(Items.IRON_HELMET), helmet)) {
			var frame = new HumanoidRenderState();
			frame.headEquipment = real;
			apply(frame, saved);
			if (real == helmet) {
				assertEquals("minehouse-cosmetics:armor_gold_curb", frame.headEquipment.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier().toString());
			} else {
				assertSame(real, frame.headEquipment);
			}
		}
		var frame = new HumanoidRenderState();
		frame.headEquipment = helmet;
		apply(frame, Map.of());
		assertSame(helmet, frame.headEquipment);
		apply(frame, Map.of("armor", "other_gold_style"));
		assertSame(helmet, frame.headEquipment);
	}

	@Test
	void goldStylesOnlyTheGoldHelmetInAMixedOutfit() {
		var state = new HumanoidRenderState();
		// Both stacks advertise iron: eligibility comes from the item, not the mutable asset.
		var head = state.headEquipment = piece(Items.GOLDEN_HELMET, EquipmentSlot.HEAD);
		var chest = state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
		var feet = state.feetEquipment = new ItemStack(Items.COPPER_BOOTS);
		var name = Component.literal("mine");
		var id = Identifier.withDefaultNamespace("gold");
		var trim = new ArmorTrim(Holder.direct(new TrimMaterial(id, name)), Holder.direct(new TrimPattern(id, name, false)));
		head.set(DataComponents.TRIM, trim);
		head.set(DataComponents.CUSTOM_NAME, name);
		chest.set(DataComponents.TRIM, trim);
		apply(state, Map.of("armor", "armor_gold_curb", "backpack", "pack", "belt", "belt"));
		assertSame(chest, state.chestEquipment, "iron chest should keep its vanilla appearance and trim");
		assertSame(feet, state.feetEquipment);
		assertSame(ItemStack.EMPTY, state.legsEquipment);
		assertNotSame(head, state.headEquipment);
		assertSame(Items.GOLDEN_HELMET, state.headEquipment.getItem());
		var eq = state.headEquipment.get(DataComponents.EQUIPPABLE);
		assertEquals("minehouse-cosmetics:armor_gold_curb", eq.assetId().orElseThrow().identifier().toString());
		assertFalse(eq.swappable());
		assertEquals(name, state.headEquipment.get(DataComponents.CUSTOM_NAME));
		assertFalse(state.headEquipment.has(DataComponents.TRIM));
		assertEquals(EquipmentAssets.IRON, head.get(DataComponents.EQUIPPABLE).assetId().orElseThrow());
		assertSame(trim, head.get(DataComponents.TRIM));
	}
}
