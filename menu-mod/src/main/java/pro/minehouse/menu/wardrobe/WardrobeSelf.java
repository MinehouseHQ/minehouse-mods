package pro.minehouse.menu.wardrobe;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Your own cosmetics in third person and in the inventory. The server dresses you up only in the
 * packets other players get (your client draws you from your own inventory), so the menu mod puts
 * what you wear onto your own render state: the hat in the helmet's place, the backpack as a layer
 * placed like the server's item display (at the neck, following the body), the belt at the hips.
 * Animated ones are drawn between frames ({@link FrameBlend}), and yours react to what you do
 * ({@link CosmeticMotion}).
 */
public final class WardrobeSelf {
	static final RenderStateDataKey<ItemStackRenderState> BACKPACK = RenderStateDataKey.create(() -> "minehouse:backpack");
	/** A HAT pose riding the torso; independent of both real head equipment and the back slot. */
	static final RenderStateDataKey<ItemStackRenderState> SHOULDER = RenderStateDataKey.create(() -> "minehouse:shoulder");
	/** In the fitting room: how far the dragged preview turns you, applied around the feet when drawn. */
	/** The kind of what is on the back: "cape" (swings as the vanilla cape), "wings", or null. */
	static final RenderStateDataKey<String> BACK_KIND = RenderStateDataKey.create(() -> "minehouse:back_kind");
	static final RenderStateDataKey<Float> PREVIEW_YAW = RenderStateDataKey.create(() -> "minehouse:preview_yaw");
	static final RenderStateDataKey<ItemStackRenderState> BELT = RenderStateDataKey.create(() -> "minehouse:belt");
	/** The belt takes the legs away (spider legs): see PlayerModelMixin. */
	static final RenderStateDataKey<Boolean> HIDE_LEGS = RenderStateDataKey.create(() -> "minehouse:hide_legs");

	// A distinct wire ID lets pre-shoulder clients leave this passenger to the vanilla renderer.
	private static final Identifier KARAT_DISPLAY = Identifier.fromNamespaceAndPath("minehouse-cosmetics-display", "karat");

	/** Set while the wardrobe builds its preview, which dresses the player itself. */
	static boolean suppress;

	private WardrobeSelf() {
	}

	static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new BackpackLayer(avatar));
				helper.register(new BeltLayer(avatar));
			}
		});
	}

	/**
	 * The server's backpack for other players: an item display with one of our models riding them.
	 * Drawn by the layer instead, glued to the body (the display itself turns with the body yaw the
	 * server sends, which lags behind the walking animation, so it slid off to the side).
	 */
	public static boolean isCosmeticDisplay(Entity entity) {
		if (!(entity instanceof Display.ItemDisplay display) || !(entity.getVehicle() instanceof Avatar)) {
			return false;
		}
		Display.ItemDisplay.ItemRenderState item = display.itemRenderState();
		if (item == null) {
			return false;
		}
		Identifier model = item.itemStack().get(DataComponents.ITEM_MODEL);
		return model != null && (WardrobeNet.NS.equals(model.getNamespace()) || KARAT_DISPLAY.equals(model));
	}

	/** Called at the end of AvatarRenderer.extractRenderState. */
	public static void extract(Avatar avatar, AvatarRenderState state) {
		state.setData(SHOULDER, null);
		state.setData(BACKPACK, null);
		state.setData(BACK_KIND, null);
		state.setData(PREVIEW_YAW, null);
		state.setData(BELT, null);
		state.setData(HIDE_LEGS, null);
		Minecraft mc = Minecraft.getInstance();
		if (suppress || mc.player == null) {
			return;
		}
		if (avatar != mc.player) {
			// Their hat (the server put it in the helmet slot): the same model, between frames.
			// headEquipment is armor-only in vanilla: paper hats are in the avatar's HEAD slot.
			ItemStack head = avatar.getItemBySlot(EquipmentSlot.HEAD);
			Identifier headModel = head.get(DataComponents.ITEM_MODEL);
			boolean cosmeticHead = !state.headItem.isEmpty() && headModel != null && WardrobeNet.NS.equals(headModel.getNamespace());
			if (cosmeticHead) {
				CosmeticMotion.Pick pick = FrameBlend.observed(avatar.getId(), "hat", head);
				if (KaratMount.onShoulder(headModel.getPath(), FrameBlend.frame(head))) {
					// Older server: shoulder frames still arrive as head equipment. Move, don't duplicate.
					setShoulder(state, head, pick);
					state.headItem.clear();
				} else if (pick != null && pick.a() != pick.b()) {
					FrameBlend.resolve(state.headItem, head, pick);
				}
			}
			for (Entity passenger : avatar.getPassengers()) {
				if (isCosmeticDisplay(passenger)) {
					ItemStack stack = ((Display.ItemDisplay) passenger).itemRenderState().itemStack();
					// custom_model_data: "f<n>", then the slot (Cosmetics 0.12.0+; older ones: the back);
					// flag 0: it hides the legs.
					CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
					String slot = cmd == null ? null : cmd.getString(1);
					if ("hat".equals(slot)) {
						// Equipment wins if the two packets briefly overlap at a perch change.
						if (!cosmeticHead && KaratMount.onShoulder(stack.get(DataComponents.ITEM_MODEL).getPath(), FrameBlend.frame(stack))) {
							setShoulder(state, stack, FrameBlend.observed(avatar.getId(), "hat", stack));
						}
						continue;
					}
					if (!visible(state)) {
						continue;
					}
					boolean belt = "belt".equals(slot);
					ItemStackRenderState model = new ItemStackRenderState();
					FrameBlend.resolve(model, stack, FrameBlend.observed(avatar.getId(), belt ? "belt" : "backpack", stack));
					if (belt) {
						state.setData(BELT, model);
						if (Boolean.TRUE.equals(cmd.getBoolean(0))) {
							hideLegs(state);
						}
					} else if (state.getData(BACKPACK) == null) {
						state.setData(BACKPACK, model);
						// The model is named after the cosmetic, which the wardrobe state describes.
						state.setData(BACK_KIND, kindOf(stack.get(DataComponents.ITEM_MODEL).getPath()));
					}
				}
			}
			return;
		}
		WardrobeNet.State net = WardrobeNet.state;
		if (net == null) {
			return;
		}
		if (net.armorVisibility() == ArmorVisibility.HIDDEN) {
			hideWornArmor(state);
		}
		Map<String, String> worn = net.worn();
		boolean fittingRoom = mc.gui.screen() instanceof Fitting f && f.fittingScene();
		if (fittingRoom && mc.gui.screen() instanceof Fitting screen) {
			// In the fitting room: what you try on, turned by dragging, standing still.
			worn = screen.trying();
			// The drag turns the whole drawn model (see previewYaw) rather than bodyRot: animation mods
			// such as Punchy! Moves pose the body themselves and ignored a bodyRot set here.
			state.bodyRot = avatar.getYRot();
			state.setData(PREVIEW_YAW, screen.previewYaw());
			state.yRot = 0;
			state.xRot = 0;
			state.walkAnimationSpeed = 0;
		}
		boolean idleOnly = fittingRoom;
		var assets = ((pro.minehouse.menu.mixin.EquipmentAssetsAccess) mc.getEntityRenderDispatcher()).minehouse$equipmentAssets();
		ArmorStyle.apply(state, worn, mc.player::getItemBySlot, (key, layer) -> !assets.get(key).getLayers(layer).isEmpty());
		String hat = worn.get("hat");
		if (hat != null) {
			CosmeticMotion.Pick pick = CosmeticMotion.pick(net.item(hat), idleOnly);
			int frame = pick == null ? -1 : pick.a();
			ItemStack stack = WardrobeScreen.stack(hat, frame);
			if (KaratMount.onShoulder(hat, frame)) {
				setShoulder(state, stack, pick); // keep vanilla's helmet, skull or other head item
			} else {
				FrameBlend.resolve(state.headItem, stack, pick);
				state.wornHeadType = null;
				state.headEquipment = stack;
			}
		}
		String back = worn.get("backpack");
		String kind = back == null ? null : kindOf(back);
		if (back != null && visible(state) && !(kind != null && underGlider(state))) {
			ItemStackRenderState model = new ItemStackRenderState();
			FrameBlend.resolve(model, WardrobeScreen.stack(back), CosmeticMotion.pick(net.item(back), idleOnly));
			state.setData(BACKPACK, model);
			state.setData(BACK_KIND, kind);
		}
		String belt = worn.get("belt");
		if (belt != null && visible(state)) {
			ItemStackRenderState model = new ItemStackRenderState();
			FrameBlend.resolve(model, WardrobeScreen.stack(belt), CosmeticMotion.pick(net.item(belt), idleOnly));
			state.setData(BELT, model);
			WardrobeNet.Item item = net.item(belt);
			if (item != null && item.hideLegs()) {
				hideLegs(state);
			}
		}
	}

	private static void setShoulder(AvatarRenderState state, ItemStack stack, CosmeticMotion.Pick pick) {
		if (!state.isInvisible && !state.isSpectator && (state.hasPose(Pose.STANDING) || state.hasPose(Pose.CROUCHING))) {
			ItemStackRenderState model = new ItemStackRenderState();
			FrameBlend.resolve(model, stack, pick);
			state.setData(SHOULDER, model);
		}
	}

	/** Vanilla extracted these display stacks from the player. Remove the whole armor-layer input
	 * (base, trim and glint), before applying hats/belts. Leave gliders and non-armor head items alone. */
	private static void hideWornArmor(AvatarRenderState state) {
		if (state.headEquipment.is(ItemTags.HEAD_ARMOR)) state.headEquipment = ItemStack.EMPTY;
		if (!underGlider(state) && state.chestEquipment.is(ItemTags.CHEST_ARMOR)) state.chestEquipment = ItemStack.EMPTY;
		if (state.legsEquipment.is(ItemTags.LEG_ARMOR)) state.legsEquipment = ItemStack.EMPTY;
		if (state.feetEquipment.is(ItemTags.FOOT_ARMOR)) state.feetEquipment = ItemStack.EMPTY;
	}

	/** No legs, no leggings or boots on them. */
	private static void hideLegs(AvatarRenderState state) {
		state.setData(HIDE_LEGS, true);
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
	}

	/** Whether this player's legs are hidden by their belt. */
	public static boolean hidesLegs(AvatarRenderState state) {
		return Boolean.TRUE.equals(state.getData(HIDE_LEGS));
	}

	/** The fitting room's turn for this render state, or 0. Called around LivingEntityRenderer.submit. */
	public static float previewYaw(AvatarRenderState state) {
		Float yaw = state.getData(PREVIEW_YAW);
		return yaw == null ? 0 : yaw;
	}

	private static String kindOf(String id) {
		WardrobeNet.State net = WardrobeNet.state;
		WardrobeNet.Item item = net == null ? null : net.item(id);
		return item == null ? null : item.kind();
	}

	/** Like the server: wings and capes go under an elytra (anything gliding worn on the chest). */
	private static boolean underGlider(AvatarRenderState state) {
		return state.chestEquipment != null && state.chestEquipment.has(DataComponents.GLIDER);
	}

	/** Like the server: no backpack while swimming, gliding, sleeping or invisible. */
	private static boolean visible(AvatarRenderState state) {
		return !state.isInvisible && !state.hasPose(Pose.SWIMMING) && !state.hasPose(Pose.FALL_FLYING) && !state.hasPose(Pose.SLEEPING);
	}

	/** The top of the back, 2 px behind the body's middle (the model faces -z). */
	private static final float CAPE_PIVOT_Z = 2 / 16f;

	/**
	 * Belts, at the hips: placed like the backpack (the neck's frame, the item 4 px up at 10/16), but
	 * following the legs instead of the body, which leans when sneaking while the legs only go back.
	 */
	static final class BeltLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		BeltLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			ItemStackRenderState model = state.getData(BELT);
			if (model == null || model.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			PlayerModel player = getParentModel();
			player.root().translateAndRotate(poseStack);
			// Standing the legs hang from 12 px below the neck; sneaking they go back and a bit down.
			poseStack.translate(0, (player.rightLeg.y - 12) / 16f, player.rightLeg.z / 16f);
			poseStack.translate(0, -0.25f, 0);
			poseStack.rotateDegrees(Axis.YP, 180);
			poseStack.scale(0.625f, -0.625f, -0.625f);
			model.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}

	static final class BackpackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		BackpackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			submitBodyItem(state.getData(BACKPACK), "cape".equals(state.getData(BACK_KIND)), poseStack, collector, light, state);
			submitBodyItem(state.getData(SHOULDER), false, poseStack, collector, light, state);
		}

		private void submitBodyItem(ItemStackRenderState model, boolean cape, PoseStack poseStack, SubmitNodeCollector collector,
				int light, AvatarRenderState state) {
			if (model == null || model.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			// The body's pivot is the neck, like the head's: CustomHeadLayer.translateToHead, but
			// turning and leaning with the body instead of the head. The root first: vanilla leaves it
			// still, but animation mods (Punchy! Moves and the like) move the whole model through it,
			// and the body is its child, so without it the backpack stays behind when the body moves.
			PlayerModel player = getParentModel();
			player.root().translateAndRotate(poseStack);
			player.body.translateAndRotate(poseStack);
			if (cape) {
				// Swings from the top of the back like the vanilla cape (PlayerCapeModel), from the
				// same numbers the game keeps for it. The server does the same for vanilla clients.
				poseStack.translate(0, 0, CAPE_PIVOT_Z);
				poseStack.rotateDegrees(Axis.XP, 6 + state.capeLean / 2 + state.capeFlap);
				poseStack.translate(0, 0, -CAPE_PIVOT_Z);
			}
			CustomHeadLayer.translateToHead(poseStack, CustomHeadLayer.Transforms.DEFAULT);
			model.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
