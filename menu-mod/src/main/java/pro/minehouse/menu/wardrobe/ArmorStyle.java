package pro.minehouse.menu.wardrobe;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.BiPredicate;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import pro.minehouse.menu.Tr;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/** The catalog IDs supported by the client. An unknown armor style never unlocks or renders. */
enum ArmorStyle {
	GOLD("armor_gold_curb", EquipmentAssets.GOLD, "Надень золотую броню", "Wear gold armor"),
	PIVOZAVR("pivozavr_latun", EquipmentAssets.GOLD, "Надень золотую броню", "Wear gold armor"),
	DIAMOND("armor_diamond_curb", EquipmentAssets.DIAMOND, "Надень алмазную броню", "Wear diamond armor"),
	NETHERITE("armor_netherite_curb", EquipmentAssets.NETHERITE, "Надень незеритовую броню", "Wear netherite armor"),
	IRON("armor_iron_curb", EquipmentAssets.IRON, "Надень железную броню", "Wear iron armor"),
	LEATHER("armor_leather_curb", EquipmentAssets.LEATHER, "Надень кожаную броню", "Wear leather armor");

	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	private static final Map<String, ArmorStyle> BY_ID = java.util.Arrays.stream(values())
			.collect(java.util.stream.Collectors.toUnmodifiableMap(style -> style.styleAsset.identifier().getPath(), Function.identity()));
	private final ResourceKey<EquipmentAsset> vanillaAsset;
	private final ResourceKey<EquipmentAsset> styleAsset;
	private final String hint;
	private final String englishHint;

	ArmorStyle(String id, ResourceKey<EquipmentAsset> vanillaAsset, String hint, String englishHint) {
		this.vanillaAsset = vanillaAsset;
		this.styleAsset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(WardrobeNet.NS, id));
		this.hint = hint;
		this.englishHint = englishHint;
	}

	private static ArmorStyle find(String id) {
		return id == null ? null : BY_ID.get(id);
	}

	private boolean matches(ItemStack real, EquipmentSlot slot) {
		if (real.isEmpty() || real.has(DataComponents.GLIDER)) return false;
		Equippable equipped = real.get(DataComponents.EQUIPPABLE);
		// A stack can override its asset and preferred slot. Neither changes the underlying armor.
		Equippable base = real.getItem().components().get(DataComponents.EQUIPPABLE);
		return equipped != null && equipped.slot() == slot && base != null && base.slot() == slot
				&& base.assetId().filter(vanillaAsset::equals).isPresent();
	}

	static String requirement(String id, Function<EquipmentSlot, ItemStack> equipment) {
		ArmorStyle style = find(id);
		if (style == null) return Tr.s("Недоступный стиль", "Unavailable style");
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (style.matches(equipment.apply(slot), slot)) return null;
		}
		return Tr.s(style.hint, style.englishHint);
	}

	static void apply(HumanoidRenderState state, Map<String, String> worn, Function<EquipmentSlot, ItemStack> equipment) {
		apply(state, worn, equipment, (key, layer) -> true);
	}

	static void apply(HumanoidRenderState state, Map<String, String> worn, Function<EquipmentSlot, ItemStack> equipment,
			BiPredicate<ResourceKey<EquipmentAsset>, LayerType> available) {
		ArmorStyle style = find(worn.get("armor"));
		if (style == null) return;
		boolean outer = available.test(style.styleAsset, LayerType.HUMANOID);
		if (outer && !worn.containsKey("hat")) {
			state.headEquipment = style.display(equipment.apply(EquipmentSlot.HEAD), EquipmentSlot.HEAD, state.headEquipment);
		}
		if (outer) {
			state.chestEquipment = style.display(equipment.apply(EquipmentSlot.CHEST), EquipmentSlot.CHEST, state.chestEquipment);
			state.feetEquipment = style.display(equipment.apply(EquipmentSlot.FEET), EquipmentSlot.FEET, state.feetEquipment);
		}
		if (available.test(style.styleAsset, LayerType.HUMANOID_LEGGINGS)) {
			state.legsEquipment = style.display(equipment.apply(EquipmentSlot.LEGS), EquipmentSlot.LEGS, state.legsEquipment);
		}
	}

	private ItemStack display(ItemStack real, EquipmentSlot slot, ItemStack vanillaDisplay) {
		if (!matches(real, slot)) return vanillaDisplay;
		Equippable e = real.get(DataComponents.EQUIPPABLE);
		ItemStack copy = real.copy();
		copy.set(DataComponents.EQUIPPABLE, new Equippable(e.slot(), e.equipSound(), Optional.of(styleAsset), e.cameraOverlay(),
				e.allowedEntities(), e.dispensable(), e.swappable(), e.damageOnHurt(), e.equipOnInteract(), e.canBeSheared(), e.shearingSound()));
		copy.remove(DataComponents.TRIM);
		return copy;
	}
}
