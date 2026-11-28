package pro.minehouse.menu.wardrobe;

import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** World-space companion to the elemental limb overlay. No entities, packets, or hand-render hooks. */
final class ElementalTrailRenderer {
	private static final double RANGE_SQUARED = 48 * 48;
	private static final ElementalFootfalls<AbstractClientPlayer> TRAIL = new ElementalFootfalls<>();
	// Resource reloads replace model identities; don't pin the old atlas or its bakes.
	private static final WeakHashMap<CuboidItemModelWrapper, ItemStackRenderState> BAKES = new WeakHashMap<>();

	private ElementalTrailRenderer() {
	}

	static void init() {
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> TRAIL.clear());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TRAIL.clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRAIL.clear());
		ClientTickEvents.END_CLIENT_TICK.register(ElementalTrailRenderer::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(ElementalTrailRenderer::submit);
	}

	private static boolean inFitting(Minecraft mc) {
		return mc.gui.screen() instanceof Fitting fitting && fitting.fittingScene();
	}

	private static boolean visible(AbstractClientPlayer player) {
		return !player.isRemoved() && player.isAlive() && !player.isInvisible() && !player.isSpectator();
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || inFitting(mc)) {
			TRAIL.clear();
			return;
		}
		if (mc.isPaused()) return;
		var camera = mc.getCameraEntity();
		Vec3 origin = camera == null ? mc.player.position() : camera.position();
		TRAIL.begin(level);
		for (var player : level.players()) {
			if (!visible(player) || player.distanceToSqr(origin) > RANGE_SQUARED) continue;
			String kind = equipped(mc, player);
			if (kind == null) continue;
			TRAIL.sample(player, kind, player.position(), groundedTravel(player), foot -> groundHeight(level, player, foot));
		}
		TRAIL.end();
	}

	private static boolean groundedTravel(AbstractClientPlayer player) {
		if (!player.onGround() || player.isPassenger() || player.isFallFlying() || player.isInWater()
				|| player.isInLava() || player.onClimbable()) return false;
		Pose pose = player.getPose();
		if (pose != Pose.STANDING && pose != Pose.CROUCHING) return false;
		if (player instanceof LocalPlayer local) {
			// Actual displacement is checked by the gait as well. Input alone can push into a wall.
			return !local.getAbilities().flying && local.input.getMoveVector().lengthSquared() > 0;
		}
		// Remote peers have no local input. Reject long corrections even when vanilla smooths them.
		var interpolation = player.getInterpolation();
		return !interpolation.hasActiveInterpolation()
				|| player.distanceToSqr(interpolation.target().position()) <= 4;
	}

	private static String equipped(Minecraft mc, AbstractClientPlayer player) {
		if (player == mc.player) {
			var state = WardrobeNet.state;
			return state == null ? null : ElementalLimbLayer.kind(state.worn().get("belt"));
		}
		// Read this player's live tracked item, never the local outfit or its cached display render.
		for (var passenger : player.getPassengers()) {
			if (!(passenger instanceof Display.ItemDisplay display) || display.getVehicle() != player) continue;
			ItemStack item = WardrobeSelf.passengerStack(display);
			var id = item.get(DataComponents.ITEM_MODEL);
			if (id == null || !WardrobeNet.NS.equals(id.getNamespace())) continue;
			String kind = ElementalLimbLayer.kind(id.getPath());
			if (kind == null) continue;
			var cmd = item.get(DataComponents.CUSTOM_MODEL_DATA);
			if (cmd != null && "belt".equals(cmd.getString(1)) && "limbs".equals(cmd.getString(2))) return kind;
		}
		return null;
	}

	private static double groundHeight(ClientLevel level, AbstractClientPlayer player, Vec3 foot) {
		if (!loaded(level, foot)) return Double.NaN;
		var hit = level.clip(new ClipContext(foot.add(0, 0.51, 0), foot.add(0, -0.62, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
				? hit.getLocation().y : Double.NaN;
	}

	private static boolean loaded(ClientLevel level, Vec3 point) {
		BlockPos pos = BlockPos.containing(point);
		return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
	}

	static Identifier modelId(String kind, int frame) {
		return Identifier.fromNamespaceAndPath("minehouse-menu", "elemental/" + kind + "/step/f" + frame);
	}

	private static ItemStackRenderState frame(Minecraft mc, Identifier id) {
		// Ignore deliberately empty poses and missing/overridden assets, not a substitute vanilla item.
		if (!(mc.getModelManager().getItemModel(id) instanceof CuboidItemModelWrapper model)) return null;
		return BAKES.computeIfAbsent(model, ignored -> {
			var out = new ItemStackRenderState();
			var item = new ItemStack(Items.PAPER);
			item.set(DataComponents.ITEM_MODEL, id);
			mc.getItemModelResolver().updateForTopItem(out, item, ItemDisplayContext.FIXED, mc.level, null, 0);
			return out;
		});
	}

	private static void submit(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || inFitting(mc)) return;
		var camera = context.levelState().cameraRenderState;
		if (!camera.initialized) return;
		PoseStack poses = context.poseStack();
		for (var stamp : TRAIL.stamps()) {
			Vec3 at = stamp.position();
			if (stamp.wearer().level() != level || !visible(stamp.wearer()) || camera.pos.distanceToSqr(at) > RANGE_SQUARED
					|| !loaded(level, at)) continue;
			if (camera.cullFrustum != null && !camera.cullFrustum.isVisible(AABB.ofSize(at, 2, 2, 2))) continue;
			ItemStackRenderState model = frame(mc, modelId(stamp.kind(), TRAIL.frame(stamp)));
			if (model == null || model.isEmpty()) continue;
			poses.pushPose();
			try {
				applyWorldPose(poses, at, camera.pos, stamp.yaw());
				model.submit(poses, context.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			} finally {
				poses.popPose();
			}
		}
	}

	static void applyWorldPose(PoseStack poses, Vec3 at, Vec3 camera, float yaw) {
		// The baked FIXED origin is already the sole, with positive Y pointing up.
		poses.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		poses.last().rotateDegrees(Axis.YP, yaw);
	}
}
