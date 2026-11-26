package pro.minehouse.menu.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The quads of one layer of a resolved item, for blending two frames of a cosmetic (FrameBlend). */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccess {
	@Accessor("quads")
	ItemQuads minehouse$quads();
}
