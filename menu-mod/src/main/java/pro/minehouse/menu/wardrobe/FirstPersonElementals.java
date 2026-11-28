package pro.minehouse.menu.wardrobe;

import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import pro.minehouse.menu.mixin.ItemStackRenderStateAccess;
import pro.minehouse.menu.mixin.LayerRenderStateAccess;

/** Independent viewmodel overlay; it never resolves from an item in the player's inventory. */
public final class FirstPersonElementals {
	private static final int FRAMES = 96;
	// Model identities change on a resource reload. Old bakes can be collected without a reload hook.
	private static final WeakHashMap<ItemModel, ItemStackRenderState> reflectedFrames = new WeakHashMap<>();

	private FirstPersonElementals() {}

	static String equipped(WardrobeNet.State state) {
		if (state == null) return null;
		return switch (state.worn().get("belt")) {
			case "elemental_ember" -> "ember";
			case "elemental_rime" -> "rime";
			case "elemental_end" -> "end";
			case null, default -> null;
		};
	}

	static Identifier modelId(String kind, HumanoidArm arm, PlayerModelType width, long gameTick) {
		String part = arm == HumanoidArm.RIGHT ? "right_arm" : "left_arm";
		String suffix = width == PlayerModelType.WIDE ? "_wide" : "";
		return Identifier.fromNamespaceAndPath("minehouse-menu", "elemental/" + kind + "/" + part + suffix + "/f" + Math.floorMod(gameTick, FRAMES));
	}

	/** Overlay only on the vanilla empty hand. Check both snapshots to cover the swap animation. */
	public static void skinArm(Minecraft mc, PlayerRenderState player, HumanoidArm arm,
			PoseStack pose, SubmitNodeCollector nodes, int light) {
		var hands = player.firstPersonHandsAndItems;
		if (mc.player == null || !mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty()
				|| hands.mainHandItem == null || !hands.mainHandItem.isEmpty()
				|| hands.offHandItem == null || !hands.offHandItem.isEmpty()) return;
		AvatarRenderState avatar = player.avatarRenderState;
		if (avatar == null || avatar.id != mc.player.getId() || avatar.isInvisible || avatar.isSpectator || avatar.skin == null) return;
		PlayerModel model = mc.getEntityRenderDispatcher().getRenderer(avatar).getModel();
		ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		draw(mc, mc.player, arm, avatar.skin.model(), part, pose, nodes, light, OverlayTexture.NO_OVERLAY);
	}

	/** Draw a limb-local cosmetic on an already posed, visible vanilla or Punchy arm. */
	static void draw(Minecraft mc, LocalPlayer wearer, HumanoidArm arm, PlayerModelType width, ModelPart part,
			PoseStack pose, SubmitNodeCollector nodes, int light, int overlay) {
		if (mc.player != wearer || mc.getCameraEntity() != wearer || !mc.options.getCameraType().isFirstPerson()
				|| wearer.isSpectator() || wearer.isInvisible() || !part.visible || part.skipDraw) return;
		String kind = equipped(WardrobeNet.state);
		if (kind == null) return;
		ItemStackRenderState frame = frame(mc, modelId(kind, arm, width, wearer.level().getGameTime()));
		if (frame == null) return;
		pose.pushPose();
		try {
			// ModelPart is the renderer's source of truth; Punchy applies extra animated
			// matrices here that are not present in the plain PartPose fields.
			part.translateAndRotate(pose);
			pose.scale(1, -1, 1);
			frame.submit(pose, nodes, light, overlay, 0);
		} finally {
			pose.popPose();
		}
	}

	static void applyArmPose(PoseStack pose, PartPose part) {
		pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
		pose.last().rotate(new Quaternionf().rotationZYX(part.zRot(), part.yRot(), part.xRot()));
		pose.scale(part.xScale(), -part.yScale(), part.zScale());
	}

	private static ItemStackRenderState frame(Minecraft mc, Identifier id) {
		// Only plain cuboid assets are supported. Missing models and deliberate empty frames draw nothing.
		if (!(mc.getModelManager().getItemModel(id) instanceof CuboidItemModelWrapper model)) return null;
		return reflectedFrames.computeIfAbsent(model, key -> {
			var out = new ItemStackRenderState();
			var access = (ItemStackRenderStateAccess) out;
			access.minehouse$displayContext(ItemDisplayContext.FIXED);
			key.update(out, ItemStack.EMPTY, mc.getItemModelResolver(), ItemDisplayContext.FIXED, mc.level, null, 0);
			for (int i = 0; i < access.minehouse$activeLayerCount(); i++) {
				var layer = access.minehouse$layers()[i];
				var quads = ((LayerRenderStateAccess) layer).minehouse$quads();
				layer.setQuads(ItemQuads.split(quads.all().stream().map(FirstPersonElementals::reverseWinding).toList()));
			}
			return out;
		});
	}

	static BakedQuad reverseWinding(BakedQuad quad) {
		// A local Y reflection flips front/back in the culled item pipeline. Preserve the original
		// normal (transformed by PoseStack) and move each UV with its vertex.
		return new BakedQuad(quad.position0(), quad.position3(), quad.position2(), quad.position1(),
				quad.packedUV0(), quad.packedUV3(), quad.packedUV2(), quad.packedUV1(), quad.direction(), quad.materialInfo());
	}
}
