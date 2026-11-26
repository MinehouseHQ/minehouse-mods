package pro.minehouse.curbfit;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;

public final class FitSelection {
    static boolean playerPass(Object state, LayerType type) {
        return state instanceof AvatarRenderState a && !a.isBaby && a.entityType == EntityTypes.PLAYER
            && (type == LayerType.HUMANOID || type == LayerType.HUMANOID_LEGGINGS);
    }
    public static Model<?> choose(EquipmentAssetManager assets, LayerType type, ResourceKey<EquipmentAsset> key,
            Model<?> original, Object state, ItemStack stack) {
        if (!(original instanceof PlayerModel) || !playerPass(state,type)
                || !Style.matches(key.identifier(),assets.get(key).getLayers(type))) return original;
        var equipment = stack.get(DataComponents.EQUIPPABLE);
        if(equipment == null || equipment.assetId().orElse(null) != key) return original;
        var slot = equipment.slot();
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return original;
        if ((slot == EquipmentSlot.LEGS) != (type == LayerType.HUMANOID_LEGGINGS)) return original;
        var a = (AvatarRenderState)state;
        if (a.skin == null) return original;
        return SkinFits.model(a.skin.body().texturePath(),a.skin.model()==PlayerModelType.SLIM,slot);
    }
    private FitSelection() {}
}
