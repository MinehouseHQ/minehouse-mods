package pro.minehouse.riftclient.cine;

import java.util.Optional;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * How the film is drawn: meshes into a bright (HDR) picture of its own, then the bloom and the grade onto the screen.
 * Registered with the game's other pipelines, so their shaders compile with the game's.
 */
public final class CinePipelines {
	/** A mesh: where, which way the surface faces, and a place on it. */
	public static final VertexFormat MESH = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Normal", GpuFormat.RGB32_FLOAT)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.build();
	/** One copy of a mesh: where, turned how, stretched how, and four numbers for its material. */
	public static final VertexFormat INSTANCE = VertexFormat.builder(1)
			.addAttribute("IPos", GpuFormat.RGBA32_FLOAT)
			.addAttribute("IRot", GpuFormat.RGBA32_FLOAT)
			.addAttribute("IScale", GpuFormat.RGBA32_FLOAT)
			.addAttribute("IData", GpuFormat.RGBA32_FLOAT)
			.build();
	public static final GpuFormat HDR = GpuFormat.RGBA16_FLOAT;
	public static final GpuFormat DEPTH = GpuFormat.D32_FLOAT;

	static final BindGroupLayout CINE = BindGroupLayout.builder().withUniform("CineInfo", UniformType.UNIFORM_BUFFER).build();
	static final BindGroupLayout POST = BindGroupLayout.builder().withUniform("CinePost", UniformType.UNIFORM_BUFFER).build();
	static final BindGroupLayout SCENE_SAMPLER = BindGroupLayout.builder().withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER).build();
	static final BindGroupLayout SCENE_BLOOM_SAMPLERS = BindGroupLayout.builder()
			.withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER)
			.withUniform("BloomSampler", UniformType.COMBINED_IMAGE_SAMPLER)
			.build();

	public static final RenderPipeline SOLID = RenderPipelines.register(mesh("cine_solid")
			.withColorTargetState(new ColorTargetState(Optional.empty(), HDR, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
			.build());
	/** Light that adds up: glows, nerves, dust, plasma. It is hidden behind solid things but hides nothing. */
	public static final RenderPipeline LIGHT = RenderPipelines.register(mesh("cine_light")
			.withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), HDR, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build());

	public static final RenderPipeline DOF = RenderPipelines.register(post("cine_dof", "cine_dof", SCENE_SAMPLER, HDR));
	public static final RenderPipeline BRIGHT = RenderPipelines.register(post("cine_bright", "cine_bright", SCENE_SAMPLER, HDR));
	public static final RenderPipeline BLUR = RenderPipelines.register(post("cine_blur", "cine_blur", SCENE_SAMPLER, HDR));
	public static final RenderPipeline FINAL = RenderPipelines.register(post("cine_final", "cine_final", SCENE_BLOOM_SAMPLERS, null));

	private CinePipelines() {
	}

	/** Loads the class, so that the pipelines are registered before the shaders are first compiled. */
	public static void init() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("minehouse-rift", path);
	}

	private static RenderPipeline.Builder mesh(String name) {
		return RenderPipeline.builder()
				.withLocation(id("pipeline/" + name))
				.withVertexShader(id("core/cine"))
				.withFragmentShader(id("core/cine"))
				.withBindGroupLayout(BindGroupLayouts.PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
				.withBindGroupLayout(CINE)
				.withVertexBinding(0, MESH)
				.withVertexBinding(1, INSTANCE)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false);
	}

	private static RenderPipeline post(String name, String fragment, BindGroupLayout samplers, GpuFormat format) {
		RenderPipeline.Builder b = RenderPipeline.builder()
				.withLocation(id("pipeline/" + name))
				.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
				.withFragmentShader(id("core/" + fragment))
				.withBindGroupLayout(POST)
				.withBindGroupLayout(samplers)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false);
		b.withColorTargetState(format == null ? ColorTargetState.DEFAULT : new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL));
		return b.build();
	}
}
