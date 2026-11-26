package pro.minehouse.menu.wardrobe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import pro.minehouse.menu.mixin.ItemStackRenderStateAccess;
import pro.minehouse.menu.mixin.LayerRenderStateAccess;

/**
 * Smooth animated cosmetics. The server bakes a Blockbench animation into one item model per frame,
 * at most 20 a second, and picks the frame (custom_model_data "f&lt;n&gt;"). Drawn as they are, they
 * move in steps. What the client mod draws itself (your own cosmetics, other players' backpacks,
 * belts and hats) is drawn between two frames instead: both are resolved and every vertex is moved
 * part of the way from one to the next, at the render frame rate. Baked quads normally match in
 * order, but a prop can add or remove quads. In that case, switch between whole poses.
 */
public final class FrameBlend {
	private FrameBlend() {
	}

	/** Resolves a cosmetic between two of its frames into {@code out}. */
	static void resolve(ItemStackRenderState out, ItemStack stack, CosmeticMotion.Pick pick) {
		resolve(out, stack, pick, ItemDisplayContext.HEAD);
	}

	static void resolve(ItemStackRenderState out, ItemStack stack, CosmeticMotion.Pick pick, ItemDisplayContext context) {
		Minecraft mc = Minecraft.getInstance();
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (pick != null && model != null) {
			pick = pick.forMount(model.getPath());
		}
		if (pick == null) {
			mc.getItemModelResolver().updateForTopItem(out, stack, context, mc.level, null, 0);
			return;
		}
		resolve(out, pick, ItemStackRenderState::new, (state, frame) ->
				mc.getItemModelResolver().updateForTopItem(state, at(stack, frame), context, mc.level, null, 0));
	}

	static void resolve(ItemStackRenderState out, CosmeticMotion.Pick pick, Supplier<ItemStackRenderState> scratch,
			ObjIntConsumer<ItemStackRenderState> bake) {
		// A completed transition must use the actual target model, not the source's layers/materials.
		if (pick.t() >= 1f) {
			bake.accept(out, pick.b());
			return;
		}
		bake.accept(out, pick.a());
		if (pick.a() == pick.b() || pick.t() <= 0.001f) {
			return;
		}
		ItemStackRenderState next = scratch.get();
		bake.accept(next, pick.b());
		if (!tryBlend(out, next, pick.t()) && pick.t() >= 0.5f) {
			// Re-resolve the whole pose, including transforms and layers, rather than mixing them.
			bake.accept(out, pick.b());
		}
	}

	/** The stack showing frame {@code frame}, the rest of its custom_model_data kept. */
	static ItemStack at(ItemStack stack, int frame) {
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		List<String> strings = new ArrayList<>(cmd == null ? List.of() : cmd.strings());
		if (strings.isEmpty()) {
			strings.add("f" + frame);
		} else {
			strings.set(0, "f" + frame);
		}
		ItemStack out = stack.copy();
		out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(cmd == null ? List.of() : cmd.floats(),
				cmd == null ? List.of() : cmd.flags(), List.copyOf(strings), cmd == null ? List.of() : cmd.colors()));
		return out;
	}

	/** Moves {@code a}'s vertices towards {@code b}'s, or leaves {@code a} intact if the counts don't match. */
	private static boolean tryBlend(ItemStackRenderState a, ItemStackRenderState b, float t) {
		ItemStackRenderStateAccess sa = (ItemStackRenderStateAccess) a;
		ItemStackRenderStateAccess sb = (ItemStackRenderStateAccess) b;
		int n = sa.minehouse$activeLayerCount();
		if (n != sb.minehouse$activeLayerCount()) {
			return false;
		}
		var layersA = sa.minehouse$layers();
		var layersB = sb.minehouse$layers();
		for (int i = 0; i < n; i++) {
			ItemQuads qa = ((LayerRenderStateAccess) layersA[i]).minehouse$quads();
			ItemQuads qb = ((LayerRenderStateAccess) layersB[i]).minehouse$quads();
			if (qa == null || qb == null || qa.all().size() != qb.all().size()) {
				return false;
			}
		}
		for (int i = 0; i < n; i++) {
			ItemStackRenderState.LayerRenderState la = layersA[i];
			ItemQuads qa = ((LayerRenderStateAccess) la).minehouse$quads();
			ItemQuads qb = ((LayerRenderStateAccess) layersB[i]).minehouse$quads();
			List<BakedQuad> out = new ArrayList<>(qa.all().size());
			for (int q = 0; q < qa.all().size(); q++) {
				BakedQuad x = qa.all().get(q);
				BakedQuad y = qb.all().get(q);
				out.add(new BakedQuad(lerp(x.position0(), y.position0(), t), lerp(x.position1(), y.position1(), t),
						lerp(x.position2(), y.position2(), t), lerp(x.position3(), y.position3(), t),
						x.packedUV0(), x.packedUV1(), x.packedUV2(), x.packedUV3(), x.direction(), x.materialInfo()));
			}
			la.setQuads(ItemQuads.split(out));
		}
		return true;
	}

	private static Vector3fc lerp(Vector3fc a, Vector3fc b, float t) {
		return new Vector3f(a).lerp(b, t);
	}

	// ------------------------------------------------------------------ other players

	/** Frame actually carried by the stack, even before the observer has the wardrobe catalogue. */
	static int frame(ItemStack stack) {
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		String f = cmd == null ? null : cmd.getString(0);
		if (f == null || !f.startsWith("f")) {
			return -1;
		}
		try {
			return Integer.parseInt(f.substring(1));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private record Seen(int frame, long at) {
	}

	/** Entity id and slot → the frame the server last sent and when it came. */
	private static final Map<String, Seen> seen = new HashMap<>();

	/**
	 * Where another player's cosmetic is: the frame the server sent, on its way to the next one in
	 * its clip, by the time since it came. When the server's next frame comes, it is where the blend
	 * got to, so it goes on without a jump; if it is late, the blend waits on the next frame.
	 */
	static CosmeticMotion.Pick observed(int entityId, String slot, ItemStack stack) {
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		WardrobeNet.State st = WardrobeNet.state;
		WardrobeNet.Item item = st == null || model == null ? null : st.item(model.getPath());
		int frame = frame(stack);
		if (frame < 0 || item == null) {
			return null;
		}
		long now = System.currentTimeMillis();
		String key = entityId + "/" + slot;
		Seen last = seen.get(key);
		if (last == null || last.frame() != frame) {
			if (seen.size() > 512) {
				seen.clear();
			}
			seen.put(key, last = new Seen(frame, now));
		}
		WardrobeNet.Clip clip = item.reacts() ? item.clipOf(frame) : new WardrobeNet.Clip(0, Math.max(item.frames(), frame + 1), true);
		int next = clip.next(frame);
		float t = Math.min(1f, (now - last.at()) * item.fps() / 1000f);
		return new CosmeticMotion.Pick(frame, next, t);
	}

	// ------------------------------------------------------------------ pets

	/** A pet's last two frames from the server, when the newer one came and how long the one before it lasted. */
	private record Trail(int from, int to, long at, long gap) {
	}

	private static final Map<Integer, Trail> trails = new HashMap<>();

	/**
	 * A pet (Shorokh and the like) walks, runs, sniffs and greets in clips the server picks from its
	 * own state, at 10 or 12.5 frames a second. Instead of guessing the next frame, this draws the pose
	 * one frame behind: from the frame before the newest to the newest, over the time the server took
	 * between them. A change of clip blends smoothly when the poses have matching topology.
	 */
	public static void pet(Display.ItemDisplay display, ItemStackRenderState out) {
		Display.ItemDisplay.ItemRenderState item = display.itemRenderState();
		if (item == null || display.getVehicle() != null) {
			return;
		}
		ItemStack stack = item.itemStack();
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		WardrobeNet.State st = WardrobeNet.state;
		if (model == null || !WardrobeNet.NS.equals(model.getNamespace()) || cmd == null || st == null) {
			return;
		}
		WardrobeNet.Item pet = st.item(model.getPath());
		String f = cmd.getString(0);
		if (pet == null || !"pet".equals(pet.slot()) || f == null || !f.startsWith("f")) {
			return;
		}
		int frame;
		try {
			frame = Integer.parseInt(f.substring(1));
		} catch (NumberFormatException e) {
			return;
		}
		long now = System.currentTimeMillis();
		long period = 1000L / Math.max(1, pet.fps());
		Trail trail = trails.get(display.getId());
		if (trail == null) {
			if (trails.size() > 256) {
				trails.clear();
			}
			trail = new Trail(frame, frame, now, period);
			trails.put(display.getId(), trail);
		} else if (trail.to() != frame) {
			// Measure the server's pace, but don't let a stall or a burst of packets stretch the blend.
			long gap = Math.clamp(now - trail.at(), period / 2, period * 2);
			trail = new Trail(trail.to(), frame, now, gap);
			trails.put(display.getId(), trail);
		}
		float t = Math.min(1f, (now - trail.at()) / (float) trail.gap());
		resolve(out, stack, new CosmeticMotion.Pick(trail.from(), trail.to(), t), item.itemTransform());
	}
}
