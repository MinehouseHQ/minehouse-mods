package pro.minehouse.riftclient.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;

@Mixin(PostChain.class)
public interface PostChainAccess {
	@Accessor("passes")
	List<PostPass> minehouse$passes();
}
