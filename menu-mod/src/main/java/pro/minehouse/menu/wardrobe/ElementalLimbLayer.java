package pro.minehouse.menu.wardrobe;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pro.minehouse.menu.mixin.ItemStackRenderStateAccess;
import pro.minehouse.menu.mixin.LayerRenderStateAccess;

/** Elemental belts dress the avatar's four posed limbs, not the waist. */
final class ElementalLimbLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	static final RenderStateDataKey<List<ItemStackRenderState>> MODELS = RenderStateDataKey.create(() -> "minehouse:elemental_limbs");
	private static final int FRAMES = 96;
	// Plain baked cuboid models are immutable and their identities are replaced on pack reload.
	private static final WeakHashMap<CuboidItemModelWrapper, ItemStackRenderState> reflected = new WeakHashMap<>();

	ElementalLimbLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	static String kind(String id) {
		return switch (id) {
			case "elemental_ember" -> "ember";
			case "elemental_rime" -> "rime";
			case "elemental_end" -> "end";
			case null, default -> null;
		};
	}

	/** The local baked loop has one pose per game tick (20 fps). */
	static int loopFrame(long gameTime) {
		return Math.floorMod(gameTime, FRAMES);
	}

	static List<ItemStackRenderState> resolve(String kind, boolean slim, int frame,
			Function<Identifier, ItemStackRenderState> bake) {
		if (frame < 0 || frame >= FRAMES) return null;
		var models = new ArrayList<ItemStackRenderState>(Limb.ALL.length);
		for (Limb limb : Limb.ALL) {
			var out = bake.apply(limb.itemId(kind, slim, frame));
			if (out == null || out.isEmpty()) return null;
			models.add(out);
		}
		return models;
	}

	static void extract(AvatarRenderState state, String kind, int frame) {
		if (!visible(state) || state.skin == null) return;
		Minecraft mc = Minecraft.getInstance();
		List<ItemStackRenderState> models = resolve(kind, state.skin.model() == PlayerModelType.SLIM, frame, id -> bake(mc, id));
		if (models != null) state.setData(MODELS, models);
	}

	private static ItemStackRenderState bake(Minecraft mc, Identifier id) {
		// Missing models and non-cuboid item overrides are not valid elemental geometry.
		if (!(mc.getModelManager().getItemModel(id) instanceof CuboidItemModelWrapper model)) return null;
		return reflected.computeIfAbsent(model, ignored -> {
			var out = new ItemStackRenderState();
			var stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, id);
			mc.getItemModelResolver().updateForTopItem(out, stack, ItemDisplayContext.FIXED, mc.level, null, 0);
			var layers = (ItemStackRenderStateAccess) out;
			for (int i = 0; i < layers.minehouse$activeLayerCount(); i++) {
				var layer = layers.minehouse$layers()[i];
				var quads = ((LayerRenderStateAccess) layer).minehouse$quads();
				layer.setQuads(ItemQuads.split(quads.all().stream().map(ElementalLimbLayer::reverseWinding).toList()));
			}
			return out;
		});
	}

	/** The Y reflection changes handedness. Keep outward faces front-facing in vanilla's culled item passes. */
	static BakedQuad reverseWinding(BakedQuad q) {
		return new BakedQuad(q.position0(), q.position3(), q.position2(), q.position1(),
				q.packedUV0(), q.packedUV3(), q.packedUV2(), q.packedUV1(), q.direction(), q.materialInfo());
	}

	private static boolean visible(AvatarRenderState state) {
		return !state.isInvisible && !state.isSpectator;
	}

	@Override
	public void submit(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		List<ItemStackRenderState> models = state.getData(MODELS);
		if (models == null || !visible(state)) return;
		PlayerModel model = getParentModel();
		for (Limb limb : Limb.ALL) {
			ModelPart part = limb.part(model);
			if (!model.root().visible || !part.visible || part.skipDraw) continue;
			poses.pushPose();
			limbFrame(model, part, poses);
			models.get(limb.ordinal()).submit(poses, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poses.popPose();
		}
	}

	/** Identity FIXED items: (8,8,8) is the pivot, native X/Z, item +Y points opposite model +Y. */
	static void limbFrame(PlayerModel model, ModelPart part, PoseStack poses) {
		model.root().translateAndRotate(poses);
		part.translateAndRotate(poses);
		poses.scale(1, -1, 1);
	}

	enum Limb {
		RIGHT_ARM("right_arm", true), LEFT_ARM("left_arm", true), RIGHT_LEG("right_leg", false), LEFT_LEG("left_leg", false);

		private static final Limb[] ALL = values();
		private final String path;
		private final boolean arm;

		Limb(String path, boolean arm) {
			this.path = path;
			this.arm = arm;
		}

		Identifier itemId(String kind, boolean slim, int frame) {
			return Identifier.fromNamespaceAndPath("minehouse-menu", "elemental/" + kind + "/" + path
					+ (arm && !slim ? "_wide" : "") + "/f" + frame);
		}

		ModelPart part(PlayerModel model) {
			return switch (this) {
				case RIGHT_ARM -> model.rightArm;
				case LEFT_ARM -> model.leftArm;
				case RIGHT_LEG -> model.rightLeg;
				case LEFT_LEG -> model.leftLeg;
			};
		}
	}
}
