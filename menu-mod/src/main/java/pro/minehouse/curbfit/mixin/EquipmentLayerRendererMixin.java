package pro.minehouse.curbfit.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pro.minehouse.curbfit.FitSelection;

@Mixin(EquipmentLayerRenderer.class)
abstract class EquipmentLayerRendererMixin {
    @Shadow @Final private EquipmentAssetManager equipmentAssets;

    // The short overload used by HumanoidArmorLayer forwards to the full native renderer.
    // Only its model argument changes. Do not intercept or recreate any submit nodes.
    @ModifyArg(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V"), index=2, require=1)
    private Model<?> curbfit$model(LayerType type, ResourceKey<EquipmentAsset> key, Model<?> model, Object state,
            ItemStack stack, PoseStack pose, SubmitNodeCollector queue, int light, Identifier playerTexture, int outline, int order) {
        return FitSelection.choose(equipmentAssets,type,key,model,state,stack);
    }
}
