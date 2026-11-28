package pro.minehouse.riftclient.cine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Draws a client-side film over the whole screen: its own 3D picture (with depth) in bright colours, then the bloom
 * and the grade onto the game's picture. Nothing of the world is used, so a film can show anything at any scale.
 */
public final class CineRenderer implements AutoCloseable {
	private static final int INFO_BYTES = 128;
	private static final int POST_BYTES = 80;

	private final Frame frame = new Frame();
	private final Projection projection = new Projection();
	private ProjectionMatrixBuffer projectionBuffer;
	private GpuBuffer info;
	private final GpuBuffer[] post = new GpuBuffer[5];
	private Instances single;
	private GpuTexture hdr, depth, sharp, bloomA, bloomB;
	private GpuTextureView hdrView, depthView, sharpView, bloomAView, bloomBView;
	private int width, height;

	/** Draws the film at this moment onto the game's picture. */
	public void render(Film film, float seconds) {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		ensure(main.width, main.height);
		frame.reset(seconds);
		film.frame(frame, seconds);

		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder enc = device.createCommandEncoder();
		frame.view.identity().lookAt(frame.eye, frame.target, frame.up);
		writeInfo(enc);

		// what to draw: the solid things first, then the light on top of them
		List<Frame.Draw> solid = new ArrayList<>(), light = new ArrayList<>();
		for (Frame.Draw d : frame.draws) {
			(d.material().light ? light : solid).add(d);
		}
		List<GpuBufferSlice> transforms = new ArrayList<>();
		List<GpuBuffer> instanceBuffers = new ArrayList<>();
		List<Frame.Draw> order = new ArrayList<>(solid);
		order.addAll(light);
		for (Frame.Draw d : order) {
			Matrix4f mv = new Matrix4f(frame.view).mul(d.model());
			transforms.add(RenderSystem.getDynamicUniforms().writeTransform(mv, new Vector4f(d.material().color), new Vector3f(), new Matrix4f(d.material().params)));
			instanceBuffers.add((d.instances() == null ? single : d.instances()).upload());
		}

		projection.setupPerspective(frame.near, frame.far, frame.fov, width, height);
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
		AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
		try (RenderPass pass = enc.createRenderPass(() -> "MineHouse film", hdrView, Optional.of(frame.clear), depthView, OptionalDouble.of(RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE))) {
			RenderPipeline current = null;
			for (int k = 0; k < order.size(); k++) {
				Frame.Draw d = order.get(k);
				RenderPipeline p = d.material().light ? CinePipelines.LIGHT : CinePipelines.SOLID;
				if (p != current) {
					pass.setPipeline(RenderSystem.getCompiledPipeline(p));
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("CineInfo", info);
					pass.setUniform("Atlas", atlas.getTextureView(), atlas.getSampler());
					current = p;
				}
				Instances inst = d.instances() == null ? single : d.instances();
				pass.setVertexBuffer(0, d.mesh().vertices.slice());
				pass.setVertexBuffer(1, instanceBuffers.get(k).slice());
				pass.setIndexBuffer(d.mesh().indices, IndexType.INT);
				pass.setUniform("DynamicTransforms", transforms.get(k));
				pass.drawIndexed(d.mesh().count, Math.max(1, inst.size()), 0, 0, 0);
			}
		}
		RenderSystem.restoreProjectionMatrix();

		// bloom: the bright parts, small, blurred both ways
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		float px = height / 1080f;
		writePost(enc, 0, 0, 0);
		writePost(enc, 1, 1.5f, 0);
		writePost(enc, 2, 0, 1.5f);
		writePost(enc, 3, 0, 0);
		writePost(enc, 4, frame.focus.z * px, frame.focus.y * px);
		GpuTextureView picture = hdrView;
		if (frame.focus.x > 0) {
			// the lens: the frame goes through the depth of field first (the sharp copy is the soft one here)
			blit(enc, CinePipelines.DOF, sharpView, post[4], hdrView, null, linear);
			picture = sharpView;
		}
		blit(enc, CinePipelines.BRIGHT, bloomAView, post[0], picture, null, linear);
		blit(enc, CinePipelines.BLUR, bloomBView, post[1], bloomAView, null, linear);
		blit(enc, CinePipelines.BLUR, bloomAView, post[2], bloomBView, null, linear);
		blit(enc, CinePipelines.FINAL, main.getColorTextureView(), post[3], picture, bloomAView, linear);
	}

	private void blit(CommandEncoder enc, RenderPipeline pipeline, GpuTextureView out, GpuBuffer uniforms, GpuTextureView scene, GpuTextureView bloom, GpuSampler sampler) {
		try (RenderPass pass = enc.createRenderPass(() -> "MineHouse film post", out, Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("CinePost", uniforms);
			pass.setUniform("SceneSampler", scene, sampler);
			if (bloom != null) {
				pass.setUniform("BloomSampler", bloom, sampler);
			}
			pass.draw(3, 1, 0, 0);
		}
	}

	private void writeInfo(CommandEncoder enc) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer b = stack.calloc(INFO_BYTES);
			frame.view.get(0, b);
			b.position(64);
			b.putFloat(frame.eye.x).putFloat(frame.eye.y).putFloat(frame.eye.z).putFloat(frame.shotSeconds);
			Vector3f l = frame.view.transformDirection(new Vector3f(frame.lightDir)).normalize();
			b.putFloat(l.x).putFloat(l.y).putFloat(l.z).putFloat(frame.backLight);
			b.putFloat(frame.fog.x).putFloat(frame.fog.y).putFloat(frame.fog.z).putFloat(frame.fog.w);
			b.putFloat(frame.filmSeconds).putFloat(0).putFloat(0).putFloat(0);
			b.flip();
			enc.writeToBuffer(info.slice(), b);
		}
	}

	private void writePost(CommandEncoder enc, int k, float stepX, float stepY) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer b = stack.calloc(POST_BYTES);
			b.putFloat(frame.look.x).putFloat(frame.look.y).putFloat(frame.look.z).putFloat(frame.look.w);
			b.putFloat(stepX).putFloat(stepY).putFloat(frame.filmSeconds).putFloat(frame.white);
			b.putFloat(frame.framing.x).putFloat(frame.framing.y).putFloat(frame.framing.z).putFloat(frame.framing.w);
			b.putFloat(frame.aim.x).putFloat(frame.aim.y).putFloat(frame.aim.z).putFloat(frame.focus.x);
			b.putFloat(frame.colour).putFloat(0).putFloat(0).putFloat(0);
			b.flip();
			enc.writeToBuffer(post[k].slice(), b);
		}
	}

	private void ensure(int w, int h) {
		GpuDevice device = RenderSystem.getDevice();
		if (info == null) {
			info = device.createBuffer(() -> "MineHouse film info", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_BYTES);
			for (int k = 0; k < post.length; k++) {
				int kk = k;
				post[k] = device.createBuffer(() -> "MineHouse film post " + kk, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, POST_BYTES);
			}
			projectionBuffer = new ProjectionMatrixBuffer("MineHouse film");
			single = new Instances("single").add(0, 0, 0, 1, 1, 0);
		}
		if (hdr != null && w == width && h == height) {
			return;
		}
		closeTargets();
		width = Math.max(1, w);
		height = Math.max(1, h);
		int target = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
		hdr = device.createTexture(() -> "MineHouse film", target, CinePipelines.HDR, width, height, 1, 1);
		sharp = device.createTexture(() -> "MineHouse film lens", target, CinePipelines.HDR, width, height, 1, 1);
		depth = device.createTexture(() -> "MineHouse film depth", GpuTexture.USAGE_RENDER_ATTACHMENT, CinePipelines.DEPTH, width, height, 1, 1);
		int bw = Math.max(1, width / 4), bh = Math.max(1, height / 4);
		bloomA = device.createTexture(() -> "MineHouse film bloom", target, CinePipelines.HDR, bw, bh, 1, 1);
		bloomB = device.createTexture(() -> "MineHouse film bloom 2", target, CinePipelines.HDR, bw, bh, 1, 1);
		hdrView = device.createTextureView(hdr);
		sharpView = device.createTextureView(sharp);
		depthView = device.createTextureView(depth);
		bloomAView = device.createTextureView(bloomA);
		bloomBView = device.createTextureView(bloomB);
	}

	private void closeTargets() {
		for (GpuTextureView v : new GpuTextureView[] { hdrView, depthView, sharpView, bloomAView, bloomBView }) {
			if (v != null) {
				v.close();
			}
		}
		for (GpuTexture t : new GpuTexture[] { hdr, depth, sharp, bloomA, bloomB }) {
			if (t != null) {
				t.close();
			}
		}
		hdr = depth = sharp = bloomA = bloomB = null;
		hdrView = depthView = sharpView = bloomAView = bloomBView = null;
	}

	@Override
	public void close() {
		closeTargets();
		if (info != null) {
			info.close();
			for (GpuBuffer b : post) {
				b.close();
			}
			projectionBuffer.close();
			single.close();
			info = null;
		}
	}
}
