package pro.minehouse.menu.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers of a resolved item, for blending two frames of a cosmetic (FrameBlend). */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccess {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] minehouse$layers();

	@Accessor("activeLayerCount")
	int minehouse$activeLayerCount();
}
