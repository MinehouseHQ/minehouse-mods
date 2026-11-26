package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

class WardrobeObserverTest {
	private static ClientLevel level;
	private static RemotePlayer wearer;

	@BeforeAll
	static void bootstrap() throws ReflectiveOperationException {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
				.forEach(DataComponentInitializers.PendingComponents::apply);
		// Display needs a client level to tick and an avatar to ride, but neither needs a client window.
		level = withoutConstructor(ClientLevel.class);
		var clientSide = Level.class.getDeclaredField("isClientSide");
		clientSide.setAccessible(true);
		clientSide.setBoolean(level, true);
		wearer = withoutConstructor(RemotePlayer.class);
	}

	@Test
	void usesUpdatedTrackedFrameBeforeDisplayTick() {
		var display = passenger();
		var source = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		receiveItemData(source, display, stack("minehouse-cosmetics:pack", 8, "backpack"));
		display.tick();
		receiveItemData(source, display, stack("minehouse-cosmetics:pack", 17, "backpack"));
		assertEquals(8, FrameBlend.frame(display.itemRenderState().itemStack()));
		assertEquals(17, FrameBlend.frame(extract(ItemStack.EMPTY, display).stack(WardrobeSelf.BACKPACK)));
		display.tick();
		assertEquals(17, FrameBlend.frame(extract(ItemStack.EMPTY, display).stack(WardrobeSelf.BACKPACK)));
	}

	@Test
	void drawsFirstShoulderMetadataBeforeCacheExists() {
		var display = passenger();
		var incoming = stack("minehouse-cosmetics-display:karat", 800, "hat");
		display.getSlot(0).set(incoming);
		assertNull(display.itemRenderState());
		assertAll(
				() -> assertSame(incoming, extract(ItemStack.EMPTY, display).stack(WardrobeSelf.SHOULDER)),
				() -> assertTrue(WardrobeSelf.isCosmeticDisplay(display), "suppress vanilla duplicate"));
	}

	@Test
	void clearsRemovedBeltBeforeTickButSuppressesTheCachedCopy() {
		var display = passenger();
		var source = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		receiveItemData(source, display, stack("minehouse-cosmetics:belt", 0, "belt"));
		display.tick();
		assertTrue(WardrobeSelf.hidesLegs(extract(ItemStack.EMPTY, display)));
		receiveItemData(source, display, ItemStack.EMPTY);
		var render = extract(ItemStack.EMPTY, display);
		assertAll(
				() -> assertNull(render.stack(WardrobeSelf.BELT)),
				() -> assertFalse(WardrobeSelf.hidesLegs(render)),
				() -> assertTrue(WardrobeSelf.isCosmeticDisplay(display), "suppress stale vanilla copy"));
		display.tick();
		assertFalse(WardrobeSelf.isCosmeticDisplay(display));
	}

	@Test
	void equipmentWinsAnOverlappingPerchChangeWithoutHidingTheBackpack() {
		var shoulder = passenger();
		var perched = stack("minehouse-cosmetics-display:karat", 800, "hat");
		shoulder.getSlot(0).set(perched);
		var backpack = passenger();
		var pack = stack("minehouse-cosmetics:pack", 8, "backpack");
		backpack.getSlot(0).set(pack);

		var helmet = new ItemStack(Items.IRON_HELMET);
		var wornOnShoulder = extract(helmet, shoulder, backpack);
		assertSame(helmet, wornOnShoulder.headEquipment);
		assertSame(perched, wornOnShoulder.stack(WardrobeSelf.SHOULDER));
		assertSame(pack, wornOnShoulder.stack(WardrobeSelf.BACKPACK));

		var crown = stack("minehouse-cosmetics:karat", 450, "hat");
		var overlap = extract(crown, backpack, shoulder);
		assertSame(crown, overlap.stacks.get(overlap.headItem));
		assertNull(overlap.stack(WardrobeSelf.SHOULDER));
		assertSame(pack, overlap.stack(WardrobeSelf.BACKPACK));
	}

	@Test
	void movesOlderServerShoulderEquipmentAndIgnoresNonWardrobeDisplays() {
		var other = passenger();
		other.getSlot(0).set(stack("minehouse-cosmetics-display:unrelated", 8, "backpack"));
		other.tick();
		var head = stack("minehouse-cosmetics:karat", 800, "hat");
		var render = extract(head, other);
		assertTrue(render.headItem.isEmpty());
		assertSame(head, render.stack(WardrobeSelf.SHOULDER));
		assertNull(render.stack(WardrobeSelf.BACKPACK));
		assertFalse(WardrobeSelf.isCosmeticDisplay(other));
	}

	@Test
	void holdsConfirmedFramesUntilAnotherEquipmentOrMetadataUpdate() throws InterruptedException {
		var previous = WardrobeNet.state;
		var idle = new WardrobeNet.Clip(0, 10, true);
		var walk = new WardrobeNet.Clip(10, 20, true);
		var catalogue = List.of(animated("fixture_hat", "hat", idle, walk),
				animated("fixture_pack", "backpack", idle, walk), animated("fixture_belt", "belt", idle, walk));
		WardrobeNet.state = new WardrobeNet.State(0, "", catalogue, Map.of(), ArmorVisibility.VISIBLE);
		try {
			var hat = stack("minehouse-cosmetics:fixture_hat", 9, "hat");
			var backpack = passenger();
			backpack.getSlot(0).set(stack("minehouse-cosmetics:fixture_pack", 9, "backpack"));
			var belt = passenger();
			belt.getSlot(0).set(stack("minehouse-cosmetics:fixture_belt", 9, "belt"));
			extract(hat, backpack, belt); // The first samples start the old observer clock.
			Thread.sleep(100); // Two 20 fps frame periods, with no new equipment or metadata.
			var held = extract(hat, backpack, belt);
			assertAll("f0 (the next idle frame) was never sent",
					() -> assertEquals(9, FrameBlend.frame(held.stacks.get(held.headItem))),
					() -> assertEquals(9, FrameBlend.frame(held.stack(WardrobeSelf.BACKPACK))),
					() -> assertEquals(9, FrameBlend.frame(held.stack(WardrobeSelf.BELT))));

			// The next samples can jump to another clip rather than advance the old loop.
			backpack.getSlot(0).set(stack("minehouse-cosmetics:fixture_pack", 17, "backpack"));
			var updated = extract(stack("minehouse-cosmetics:fixture_hat", 12, "hat"), belt, backpack);
			assertEquals(12, FrameBlend.frame(updated.stacks.get(updated.headItem)));
			assertEquals(17, FrameBlend.frame(updated.stack(WardrobeSelf.BACKPACK)));
			assertEquals(9, FrameBlend.frame(updated.stack(WardrobeSelf.BELT)));
		} finally {
			WardrobeNet.state = previous;
		}
	}

	private static WardrobeNet.Item animated(String id, String slot, WardrobeNet.Clip idle, WardrobeNet.Clip walk) {
		return new WardrobeNet.Item(id, slot, id, 0, true, null, null, 30, 20, null,
				Map.of("idle", idle, "walk", walk), false, false);
	}

	private static Display.ItemDisplay passenger() {
		return new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level) {
			@Override public Entity getVehicle() { return wearer; }
		};
	}

	private static void receiveItemData(Display.ItemDisplay source, Display.ItemDisplay display, ItemStack stack) {
		source.getSlot(0).set(stack);
		// Same application path as ClientPacketListener.handleSetEntityData.
		display.getEntityData().assignValues(source.getEntityData().packDirty());
	}

	private static <T> T withoutConstructor(Class<T> type) throws ReflectiveOperationException {
		var factory = ReflectionFactory.getReflectionFactory();
		return type.cast(factory.newConstructorForSerialization(type, Object.class.getConstructor()).newInstance());
	}

	private static ItemStack stack(String model, int frame, String slot) {
		var stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of("belt".equals(slot)),
				List.of("f" + frame, slot), List.of()));
		return stack;
	}

	private static State extract(ItemStack head, Entity... passengers) {
		var state = new State();
		state.pose = Pose.STANDING;
		if (head.is(Items.IRON_HELMET)) {
			state.headEquipment = head;
		} else if (!head.isEmpty()) {
			state.bake(state.headItem, head);
		}
		WardrobeSelf.extractObserved(head, List.of(passengers), state, state::bake);
		return state;
	}

	// Plain JUnit does not install Fabric's render-state mixin or load baked item models.
	private static class State extends AvatarRenderState {
		private final Map<RenderStateDataKey<?>, Object> data = new HashMap<>();
		private final Map<ItemStackRenderState, ItemStack> stacks = new IdentityHashMap<>();

		@Override public <T> void setData(RenderStateDataKey<T> key, T value) { data.put(key, value); }
		@Override @SuppressWarnings("unchecked")
		public <T> T getData(RenderStateDataKey<T> key) { return (T) data.get(key); }

		void bake(ItemStackRenderState out, ItemStack stack) {
			out.clear();
			out.newLayer();
			stacks.put(out, stack);
		}

		ItemStack stack(RenderStateDataKey<ItemStackRenderState> key) {
			return stacks.get(getData(key));
		}
	}
}
