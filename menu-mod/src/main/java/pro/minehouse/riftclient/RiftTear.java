package pro.minehouse.riftclient;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import pro.minehouse.riftclient.mixin.PostChainAccess;
import pro.minehouse.riftclient.mixin.PostPassAccess;

/**
 * The rift itself, drawn by the mode's screen shader: a bright jagged tear in the air that bends
 * the picture of the world around it. The server marks where a rift hangs with a flat picture (an
 * item display with the model {@code minehouse-rift:rift}); this client finds those pictures, does
 * not draw them, and tells the shader where on the screen each one is.
 */
public final class RiftTear {
	static final Identifier MODEL = Identifier.fromNamespaceAndPath("minehouse-rift", "rift");
	/** The uniform block of the shader: for each tear two vec4. */
	static final String BLOCK = "RiftTears";
	static final int MAX = 4;
	static final int BYTES = MAX * 2 * 16 + 16;
	/** How far a tear is drawn from, in blocks. */
	static final double RANGE = 96;
	/** Part of the picture's side that the tear in it takes. */
	static final float TEAR_SHARE = 0.68f;

	record Tear(Vec3 bottom, Vec3 top, float seed) {
	}

	private static List<Tear> tears = List.of();
	private static GpuBuffer buffer;
	/** When the shader last got the tears: while it does, the flat pictures are not drawn. */
	private static long uploadedAt;

	private RiftTear() {
	}

	static boolean picture(Entity e) {
		if (!(e instanceof Display.ItemDisplay d)) {
			return false;
		}
		return MODEL.equals(d.getItemStack().get(DataComponents.ITEM_MODEL));
	}

	/** Every client tick: which rifts are near. */
	static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			tears = List.of();
			return;
		}
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		List<Tear> found = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!picture(e) || e.position().distanceToSqr(eye) > RANGE * RANGE) {
				continue;
			}
			float side = ((Display) e).getHeight();
			float half = (side > 0 ? side : 6.4f) * TEAR_SHARE / 2;
			Vec3 c = e.position();
			found.add(new Tear(c.add(0, -half, 0), c.add(0, half, 0), (float) ((c.x * 0.37 + c.z * 0.61 + c.y * 0.13) % 17.0)));
		}
		found.sort(Comparator.comparingDouble(t -> t.bottom().distanceToSqr(eye)));
		tears = found.size() > MAX ? List.copyOf(found.subList(0, MAX)) : found;
	}

	/** Whether a rift is near enough to be drawn. */
	static boolean any() {
		return !tears.isEmpty() || RiftRestart.any();
	}

	/** The flat picture gives way to the shader while the shader works. */
	public static boolean hidden(Entity e) {
		return System.currentTimeMillis() - uploadedAt < 500 && picture(e);
	}

	/** Each tear as the shader wants it: both ends on the screen (0..1), how open it is, its seed. */
	static float[] project(List<Tear> list, Matrix4f viewProjection, Vec3 camera) {
		float[] out = new float[MAX * 8];
		int n = 0;
		Vector4f v = new Vector4f();
		for (Tear t : list) {
			if (n >= MAX) {
				break;
			}
			// Only the middle is put on the screen, and the size follows from how far it is. Putting
			// both ends there went wrong as soon as one of them came beside or behind the viewer.
			Vec3 middle = t.bottom().add(t.top()).scale(0.5).subtract(camera);
			v.set((float) middle.x, (float) middle.y, (float) middle.z, 1).mul(viewProjection);
			if (v.w < 0.3f) {
				continue;
			}
			float zoom = (float) Math.sqrt(viewProjection.m01() * viewProjection.m01() + viewProjection.m11() * viewProjection.m11()
					+ viewProjection.m21() * viewProjection.m21());
			float half = (float) (t.top().distanceTo(t.bottom()) * 0.5) * zoom / v.w * 0.5f;
			float x = v.x / v.w * 0.5f + 0.5f;
			float y = v.y / v.w * 0.5f + 0.5f;
			float[] ends = {x, y - half, x, y + half};
			float depth = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? v.z / v.w : v.z / v.w * 0.5f + 0.5f;
			float[] depths = {depth, depth};
			double far = middle.length();
			// It fades when the viewer stands in it, and far away.
			float open = (float) Math.max(0, Math.min(1, Math.min((RANGE - far) / 12, (far - 1.0) / 2.5)));
			System.arraycopy(ends, 0, out, n * 8, 4);
			out[n * 8 + 4] = open;
			out[n * 8 + 5] = t.seed();
			out[n * 8 + 6] = depths[0];
			out[n * 8 + 7] = depths[1];
			n++;
		}
		return out;
	}

	/** Before the screen effects of a frame are applied: ours learn where the tears are. */
	public static void upload(Camera camera, List<PostChain> applied) {
		boolean ours = false;
		for (PostChain chain : applied) {
			ours |= chain.id().getNamespace().equals(MODEL.getNamespace());
		}
		if (!ours) {
			return;
		}
		float[] data = project(tears, camera.getViewRotationProjectionMatrix(new Matrix4f()), camera.position());
		if (buffer == null || buffer.isClosed()) {
			buffer = RenderSystem.getDevice().createBuffer(() -> "MineHouse rift tears", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BYTES);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer bytes = stack.malloc(BYTES);
			for (float f : data) {
				bytes.putFloat(f);
			}
			// After the tears, the numbers of the whole screen: how much of it a restart has taken.
			bytes.putFloat(RiftRestart.field()).putFloat(0).putFloat(0).putFloat(0);
			bytes.flip();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), bytes);
		}
		for (PostChain chain : applied) {
			if (!chain.id().getNamespace().equals(MODEL.getNamespace())) {
				continue;
			}
			for (PostPass pass : ((PostChainAccess) chain).minehouse$passes()) {
				Map<String, GpuBuffer> uniforms = ((PostPassAccess) pass).minehouse$uniforms();
				GpuBuffer old = uniforms.get(BLOCK);
				if (old != null && old != buffer) {
					uniforms.put(BLOCK, buffer);
					if (!old.isClosed()) {
						old.close();
					}
				}
			}
		}
		uploadedAt = System.currentTimeMillis();
	}
}
