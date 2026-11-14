package pro.minehouse.riftclient.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.renderpearl.api.buffers.GpuBuffer;

import net.minecraft.client.renderer.PostPass;

@Mixin(PostPass.class)
public interface PostPassAccess {
	@Accessor("customUniforms")
	Map<String, GpuBuffer> minehouse$uniforms();
}
