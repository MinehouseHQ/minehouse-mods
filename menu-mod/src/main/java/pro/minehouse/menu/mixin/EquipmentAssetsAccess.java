package pro.minehouse.menu.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Use the same reloaded equipment definitions as the live renderer. */
@Mixin(EntityRenderDispatcher.class)
public interface EquipmentAssetsAccess {
	@Accessor("equipmentAssets") EquipmentAssetManager minehouse$equipmentAssets();
}
