package pro.minehouse.menu.wardrobe;


import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Your own cosmetics in third person and in the inventory. The server dresses you up only in the
 * packets other players get (your client draws you from your own inventory), so the menu mod puts
 * what you wear onto your own render state: the hat in the helmet's place, the backpack as a layer
 * placed like the server's item display (at the neck, following the body), the belt at the hips.
 * Your own animations blend and react to what you do ({@link CosmeticMotion}); other players'
 * cosmetics use the server's latest confirmed pose.
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
	static final RenderStateDataKey<List<ItemStackRenderState>> EXTRA_HATS = RenderStateDataKey.create(() -> "minehouse:extra_hats");
	static final RenderStateDataKey<List<ItemStackRenderState>> EXTRA_BACKPACKS = RenderStateDataKey.create(() -> "minehouse:extra_backpacks");
	static final RenderStateDataKey<List<ItemStackRenderState>> EXTRA_BELTS = RenderStateDataKey.create(() -> "minehouse:extra_belts");
	static final RenderStateDataKey<List<ItemStackRenderState>> EXTRA_SHOULDERS = RenderStateDataKey.create(() -> "minehouse:extra_shoulders");

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
				helper.register(new HatLayer(avatar));
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
		// On removal, also suppress the vanilla renderer's cached copy until it catches up.
		var cached = display.itemRenderState();
		return isCosmeticStack(passengerStack(display)) || cached != null && isCosmeticStack(cached.itemStack());
	}

	/**
	 * While the wardrobe's fitting scene is open: other players, whatever rides them, and loose
	 * cosmetic displays (their pets) are not drawn. Your own passengers and your fitting pet
	 * (custom_model_data string 2 "fitting", Cosmetics 0.27.0+) stay.
	 */
	public static boolean hiddenInFitting(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || entity == mc.player || !(mc.gui.screen() instanceof Fitting f) || !f.fittingScene()) return false;
		Entity root = entity.getRootVehicle();
		if (root == mc.player) return false;
		if (root instanceof net.minecraft.world.entity.player.Player) return true;
		if (entity instanceof Display.ItemDisplay display && entity.getVehicle() == null) {
			ItemStack stack = passengerStack(display);
			if (!isCosmeticStack(stack)) return false;
			CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
			return cmd == null || !"fitting".equals(cmd.getString(2));
		}
		return false;
	}

	private static boolean isCosmeticStack(ItemStack stack) {
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		return model != null && (WardrobeNet.NS.equals(model.getNamespace()) || KARAT_DISPLAY.equals(model));
	}

	/** Equipment and this tracked data are updated by the packets, before the display's next render tick. */
	static ItemStack passengerStack(Display.ItemDisplay display) {
		return display.getSlot(0).get();
	}

	/** Called at the end of AvatarRenderer.extractRenderState. */
	public static void extract(Avatar avatar, AvatarRenderState state) {
		state.setData(SHOULDER, null);
		state.setData(BACKPACK, null);
		state.setData(BACK_KIND, null);
		state.setData(PREVIEW_YAW, null);
		state.setData(BELT, null);
		state.setData(HIDE_LEGS, null);
		state.setData(EXTRA_HATS, new ArrayList<>());
		state.setData(EXTRA_BACKPACKS, new ArrayList<>());
		state.setData(EXTRA_BELTS, new ArrayList<>());
		state.setData(EXTRA_SHOULDERS, new ArrayList<>());
		Minecraft mc = Minecraft.getInstance();
		if (suppress || mc.player == null) {
			return;
		}
		if (avatar != mc.player) {
			extractObserved(avatar.getItemBySlot(EquipmentSlot.HEAD), avatar.getPassengers(), state,
					(out, stack) -> mc.getItemModelResolver().updateForTopItem(out, stack, ItemDisplayContext.HEAD, mc.level, null, 0));
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
		if (!fittingRoom && (state.isInvisible || state.isSpectator)) return;
		var assets = ((pro.minehouse.menu.mixin.EquipmentAssetsAccess) mc.getEntityRenderDispatcher()).minehouse$equipmentAssets();
		ArmorStyle.apply(state, worn, mc.player::getItemBySlot, (key, layer) -> !assets.get(key).getLayers(layer).isEmpty());
		// Extra layers (no server frames for them) only idle in the fitting room, like previews.
		boolean idleOnly = fittingRoom;
		String hat = worn.get("hat");
		CosmeticMotion.Pick pick = CosmeticMotion.worn(avatar.getId(), net.item(hat), fittingRoom);
		if (hat != null && CosmeticMotion.draw(pick)) {
			int frame = pick == null ? -1 : pick.a();
			ItemStack stack = WardrobeScreen.stack(hat, frame);
			if (KaratMount.onShoulder(hat, frame)) {
				// Keep vanilla's helmet, skull or other head item.
				setShoulder(state, out -> FrameBlend.resolve(out, stack, pick));
			} else {
				FrameBlend.resolve(state.headItem, stack, pick);
				state.wornHeadType = null;
				state.headEquipment = stack;
			}
		}
		for (int i = 2; i <= 3; i++) {
			String id = worn.get(WardrobeNet.wearKey("hat", i));
			if (id == null) continue;
			CosmeticMotion.Pick layerPick = CosmeticMotion.pick(net.item(id), idleOnly);
			ItemStack stack = WardrobeScreen.stack(id, layerPick == null ? -1 : layerPick.a());
			addModel(state, KaratMount.onShoulder(id, layerPick == null ? -1 : layerPick.a()) ? EXTRA_SHOULDERS : EXTRA_HATS, stack, layerPick);
		}
		// A shoulder companion (Cosmetics 0.27.0) is drawn like an extra back layer: its model sits on the shoulder.
		String shoulder = worn.get("shoulder");
		if (shoulder != null && visible(state)) {
			addModel(state, EXTRA_BACKPACKS, WardrobeScreen.stack(shoulder), CosmeticMotion.pick(net.item(shoulder), idleOnly));
		}
		String back = worn.get("backpack");
		String kind = back == null ? null : kindOf(back);
		CosmeticMotion.Pick backPick = CosmeticMotion.worn(avatar.getId(), net.item(back), fittingRoom);
		if (back != null && CosmeticMotion.draw(backPick) && visible(state) && !(kind != null && underGlider(state))) {
			ItemStackRenderState model = new ItemStackRenderState();
			FrameBlend.resolve(model, WardrobeScreen.stack(back, backPick == null ? -1 : backPick.a()), backPick);
			state.setData(BACKPACK, model);
			state.setData(BACK_KIND, kind);
		}
		for (int i = 2; i <= 3 && visible(state); i++) {
			String id = worn.get(WardrobeNet.wearKey("backpack", i));
			String k = id == null ? null : kindOf(id);
			if (id != null && !(k != null && underGlider(state))) addModel(state, EXTRA_BACKPACKS, WardrobeScreen.stack(id), CosmeticMotion.pick(net.item(id), idleOnly));
		}
		String belt = worn.get("belt");
		CosmeticMotion.Pick beltPick = CosmeticMotion.worn(avatar.getId(), net.item(belt), fittingRoom);
		if (belt != null && CosmeticMotion.draw(beltPick) && visible(state)) {
			ItemStackRenderState model = new ItemStackRenderState();
			FrameBlend.resolve(model, WardrobeScreen.stack(belt, beltPick == null ? -1 : beltPick.a()), beltPick);
			state.setData(BELT, model);
			WardrobeNet.Item item = net.item(belt);
			if (item != null && item.hideLegs()) {
				hideLegs(state);
			}
		}
		for (int i = 2; i <= 3 && visible(state); i++) {
			String id = worn.get(WardrobeNet.wearKey("belt", i));
			if (id != null) {
				addModel(state, EXTRA_BELTS, WardrobeScreen.stack(id), CosmeticMotion.pick(net.item(id), idleOnly));
				WardrobeNet.Item item = net.item(id); if (item != null && item.hideLegs()) hideLegs(state);
			}
		}
	}

	private static void addModel(AvatarRenderState state, RenderStateDataKey<List<ItemStackRenderState>> key, ItemStack stack, CosmeticMotion.Pick pick) {
		ItemStackRenderState model = new ItemStackRenderState();
		FrameBlend.resolve(model, stack, pick);
		state.getData(key).add(model);
	}

	private static void addModel(AvatarRenderState state, RenderStateDataKey<List<ItemStackRenderState>> key, Consumer<ItemStackRenderState> bake) {
		ItemStackRenderState model = new ItemStackRenderState();
		bake.accept(model);
		extraList(state, key).add(model);
	}

	/** Tests build a bare render state; extract() normally sets these lists first. */
	private static List<ItemStackRenderState> extraList(AvatarRenderState state, RenderStateDataKey<List<ItemStackRenderState>> key) {
		var models = state.getData(key);
		if (models == null) state.setData(key, models = new ArrayList<>());
		return models;
	}

	/** Resolve the received stack as-is: a server may hold a frame or jump to another clip. */
	static void extractObserved(ItemStack head, List<Entity> passengers, AvatarRenderState state,
			BiConsumer<ItemStackRenderState, ItemStack> bake) {
		// Vanilla already resolved the received HEAD stack. Only migrate old-server shoulder poses.
		Identifier headModel = head.get(DataComponents.ITEM_MODEL);
		boolean cosmeticHead = !state.headItem.isEmpty() && headModel != null && WardrobeNet.NS.equals(headModel.getNamespace());
		if (cosmeticHead && KaratMount.onShoulder(headModel.getPath(), FrameBlend.frame(head))) {
			setShoulder(state, out -> bake.accept(out, head));
			state.headItem.clear();
		}
		for (Entity passenger : passengers) {
			if (!(passenger instanceof Display.ItemDisplay display) || !(display.getVehicle() instanceof Avatar)) continue;
			// Metadata updates the tracked slot immediately; Display.tick refreshes the render cache later.
			ItemStack stack = display.getSlot(0).get();
			if (!isCosmeticStack(stack)) continue;
			// custom_model_data: "f<n>", then the slot (Cosmetics 0.12.0+; older ones: the back);
			// flag 0: it hides the legs.
			CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
			String slot = cmd == null ? null : cmd.getString(1);
			// Extra layers (Cosmetics 0.22+, flag "layer"): drawn beside the primary one.
			boolean layer = cmd != null && "layer".equals(cmd.getString(2));
			if ("hat".equals(slot)) {
				boolean shoulder = KaratMount.onShoulder(stack.get(DataComponents.ITEM_MODEL).getPath(), FrameBlend.frame(stack));
				// Equipment wins if the two packets briefly overlap at a perch change.
				if (!layer && !cosmeticHead && shoulder) {
					setShoulder(state, out -> bake.accept(out, stack));
				} else if (layer) {
					addModel(state, shoulder ? EXTRA_SHOULDERS : EXTRA_HATS, out -> bake.accept(out, stack));
				}
				continue;
			}
			if (!visible(state)) {
				continue;
			}
			boolean belt = "belt".equals(slot);
			ItemStackRenderState model = new ItemStackRenderState();
			bake.accept(model, stack);
			if ("shoulder".equals(slot)) {
				// Its own slot since Cosmetics 0.27.0, never the primary back item (that one names the kind).
				extraList(state, EXTRA_BACKPACKS).add(model);
			} else if (belt) {
				if (!layer && state.getData(BELT) == null) state.setData(BELT, model); else extraList(state, EXTRA_BELTS).add(model);
				if (Boolean.TRUE.equals(cmd.getBoolean(0))) {
					hideLegs(state);
				}
			} else if (!layer && state.getData(BACKPACK) == null) {
				state.setData(BACKPACK, model);
				// The model is named after the cosmetic, which the wardrobe state describes.
				state.setData(BACK_KIND, kindOf(stack.get(DataComponents.ITEM_MODEL).getPath()));
			} else {
				extraList(state, EXTRA_BACKPACKS).add(model);
			}
		}
	}

	private static void setShoulder(AvatarRenderState state, Consumer<ItemStackRenderState> bake) {
		if (!state.isInvisible && !state.isSpectator && (state.hasPose(Pose.STANDING) || state.hasPose(Pose.CROUCHING))) {
			ItemStackRenderState model = new ItemStackRenderState();
			bake.accept(model);
			state.setData(SHOULDER, model);
		}
	}

	private static List<ItemStackRenderState> extraModels(AvatarRenderState state, RenderStateDataKey<List<ItemStackRenderState>> key) {
		var models = state.getData(key);
		return models == null ? List.of() : models;
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
	 * Belts, at the hips: placed like the backpack (the neck's frame, the item 4 px up at 10/16), on
	 * the bottom of the body. They go with the body, never with a leg: animation mods (Punchy! Moves
	 * and the like) swing the legs on their own, and the belt stays on the waist.
	 */
	static final class BeltLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		BeltLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			ItemStackRenderState model = state.getData(BELT);
			var extras = extraModels(state, EXTRA_BELTS);
			if ((model == null || model.isEmpty()) && extras.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			beltFrame(getParentModel(), poseStack);
			if (model != null && !model.isEmpty()) model.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			for (ItemStackRenderState extra : extras) extra.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}

	/** The belt's frame: the body's (root, then body: moves, twists and leans with it), the item 4 px up. */
	static void beltFrame(PlayerModel player, PoseStack poseStack) {
		player.root().translateAndRotate(poseStack);
		player.body.translateAndRotate(poseStack);
		poseStack.translate(0, -0.25f, 0);
		poseStack.rotateDegrees(Axis.YP, 180);
		poseStack.scale(0.625f, -0.625f, -0.625f);
	}

	static final class BackpackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		BackpackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			submitBodyItem(state.getData(BACKPACK), "cape".equals(state.getData(BACK_KIND)), poseStack, collector, light, state);
			submitBodyItem(state.getData(SHOULDER), false, poseStack, collector, light, state);
			for (ItemStackRenderState extra : extraModels(state, EXTRA_BACKPACKS)) submitBodyItem(extra, false, poseStack, collector, light, state);
			for (ItemStackRenderState extra : extraModels(state, EXTRA_SHOULDERS)) submitBodyItem(extra, false, poseStack, collector, light, state);
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

	static final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		HatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }
		@Override public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			for (ItemStackRenderState model : extraModels(state, EXTRA_HATS)) {
				poseStack.pushPose();
				PlayerModel player = getParentModel();
				player.root().translateAndRotate(poseStack);
				player.head.translateAndRotate(poseStack);
				CustomHeadLayer.translateToHead(poseStack, CustomHeadLayer.Transforms.DEFAULT);
				model.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				poseStack.popPose();
			}
		}
	}
}
