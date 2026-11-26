package pro.minehouse.menu.wardrobe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import pro.minehouse.menu.MinehouseMenu;
import pro.minehouse.wardrobe.AnimationFrames;
import pro.minehouse.wardrobe.IskrikTimeline;

/**
 * The wardrobe channel of the MineHouse Cosmetics server mod. One JSON string each way; the
 * format is documented in cosmetics-mod {@code WardrobeNet}.
 */
public final class WardrobeNet {
	static final String NS = "minehouse-cosmetics";
	private static final List<String> SLOTS = List.of("hat", "shoulder", "backpack", "belt", "armor", "pet");

	/** Only views owned by the fitting session may be closed by its end packet. */
	interface FittingView {
		void expire();
	}

	public record StatePayload(String json) implements CustomPacketPayload {
		static final Type<StatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "state"));
		static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = ByteBufCodecs.stringUtf8(1 << 20).map(StatePayload::new, StatePayload::json).cast();

		@Override
		public Type<StatePayload> type() {
			return TYPE;
		}
	}

	/** Optional clientbound control, separate from state so it can never open the GUI. */
	public record FittingEnd() implements CustomPacketPayload {
		static final FittingEnd INSTANCE = new FittingEnd();
		static final Type<FittingEnd> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "fitting_end"));
		static final StreamCodec<FriendlyByteBuf, FittingEnd> CODEC = StreamCodec.unit(INSTANCE);

		void apply(net.minecraft.client.gui.screens.Screen current) {
			if (current instanceof FittingView view) view.expire();
		}

		@Override public Type<FittingEnd> type() { return TYPE; }
	}

	public record ActionPayload(String json) implements CustomPacketPayload {
		static final Type<ActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "action"));
		static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = ByteBufCodecs.stringUtf8(4096).map(ActionPayload::new, ActionPayload::json).cast();

		@Override
		public Type<ActionPayload> type() {
			return TYPE;
		}
	}

	/** Receiver registration advertises support for the server's extra display passengers. */
	public record MultiWear() implements CustomPacketPayload {
		static final Type<MultiWear> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "multi_wear"));
		static final StreamCodec<FriendlyByteBuf, MultiWear> CODEC = StreamCodec.unit(new MultiWear());

		@Override public Type<MultiWear> type() { return TYPE; }
	}

	/**
	 * color: what the player dyed it (or the default), null if it can't be dyed. frames/fps: an
	 * animated cosmetic (Cosmetics 0.9.0+): one model per frame, picked by custom_model_data "f<n>";
	 * frames 1 = still. For Iskrik, use the wearer's UUID and level time to choose a pose.
	 * kind: "cape" or "wings" (worn on the back, listed on the «Тело» tab with backpacks); otherwise null.
	 */
	public record Item(String id, String slot, String name, int price, boolean owned, Integer color, Integer defaultColor,
			int frames, int fps, String kind, Map<String, Clip> clips, boolean hideLegs, boolean flatIcon) {
		/** Whether it reacts to what the wearer does (Cosmetics 0.12.0+: clips other than idle). */
		boolean reacts() {
			return clips.size() > (clips.containsKey("idle") ? 1 : 0);
		}

		/** The idle loop: frames f0.. as many as {@code frames}. */
		Clip idle() {
			Clip idle = clips.get("idle");
			return idle != null ? idle : new Clip(0, frames, true);
		}


		boolean cape() {
			return "cape".equals(kind);
		}

		boolean dyeable() {
			return color != null;
		}

		boolean specialTimeline() {
			return IskrikTimeline.matches(id, frames, fps) || minerGlints();
		}

		private boolean minerGlints() {
			return id.equals("field_belt") && frames == 36 && fps == 20 && clips.isEmpty();
		}

		/** The owner, F5 layer and wardrobe cards use the same local player's game tick. */
		int frame() {
			var player = Minecraft.getInstance().player;
			return frameAt(player == null ? null : player.getUUID(), player == null ? 0 : player.level().getGameTime(),
					System.currentTimeMillis());
		}

		int frameAt(UUID wearer, long gameTick, long millis) {
			if (IskrikTimeline.matches(id, frames, fps)) return wearer == null ? 0 : IskrikTimeline.frame(wearer, gameTick);
			if (minerGlints()) return 0; // unlit card; the world display receives the exact server pose
			return frames > 1 ? (int) Math.floorMod(millis * fps / 1000, (long) frames) : 0;
		}
	}

	/** Frames start..start+frames-1 of an animation by event (see cosmetics-mod Motion). */
	public record Clip(int start, int frames, boolean loop) {
		/** The frame after {@code frame}: round to the start of a loop, a clip played once stays on its last. */
		int next(int frame) {
			int n = frame + 1;
			return n < start + frames ? n : loop ? start : frame;
		}
	}

	/** Worn outfits and scripted fitting previews share flat keys (hat, hat:2, hat:3). */
	static String wearKey(String slot, int position) {
		return position == 1 ? slot : slot + ":" + position;
	}

	/** How many items a slot holds: one each since Cosmetics 0.30.0 (hats, backpacks and belts held 3). */
	static int layeredCapacity(String slot) {
		return 1;
	}

	public record State(int coins, String store, List<Item> items, Map<String, String> worn, ArmorVisibility armorVisibility,
			boolean multiWear) {
		public State(int coins, String store, List<Item> items, Map<String, String> worn, ArmorVisibility armorVisibility) {
			this(coins, store, items, worn, armorVisibility, false);
		}

		List<String> wornSlots() {
			return SLOTS.stream().filter(worn::containsKey).toList();
		}

		int wornPosition(String slot, String id) {
			for (int i = 1; i <= layeredCapacity(slot); i++) {
				if (id.equals(worn.get(wearKey(slot, i)))) return i;
			}
			return 0;
		}

		Item item(String id) {
			for (Item item : items) {
				if (item.id().equals(id)) {
					return item;
				}
			}
			return null;
		}
	}

	/** The last state the server sent, or null on servers without the wardrobe. */
	static State state;
	static AnimationFrames animation;

	private WardrobeNet() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(AnimationFrames.TYPE, AnimationFrames.CODEC.cast());
		ClientPlayNetworking.registerGlobalReceiver(AnimationFrames.TYPE, (payload, context) -> animation = payload);
		PayloadTypeRegistry.clientboundPlay().register(FittingEnd.TYPE, FittingEnd.CODEC.cast());
		PayloadTypeRegistry.clientboundPlay().register(MultiWear.TYPE, MultiWear.CODEC.cast());
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC.cast());
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC.cast());
		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> receive(context.client(), payload.json()));
		ClientPlayNetworking.registerGlobalReceiver(FittingEnd.TYPE, (payload, context) -> payload.apply(context.client().gui.screen()));
		ClientPlayNetworking.registerGlobalReceiver(MultiWear.TYPE, (payload, context) -> {});
		// Gate can swap backend connections without a DISCONNECT. The new play session must start clean.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	static void reset() {
		state = null;
		animation = null;
		CosmeticMotion.reset();
		FrameBlend.clear();
	}

	public static boolean available() {
		return ClientPlayNetworking.canSend(ActionPayload.TYPE);
	}

	/** Request an absolute target. Repeated clicks before a reply send the same request. */
	static void requestArmorVisibility() {
		if (state != null && state.armorVisibility() != ArmorVisibility.UNAVAILABLE) {
			send("armor_visibility", "id", state.armorVisibility() == ArmorVisibility.VISIBLE ? "hide" : "show");
		}
	}

	static void dye(String id, int rgb) {
		if (!available()) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("action", "dye");
		o.addProperty("id", id);
		o.addProperty("color", rgb);
		ClientPlayNetworking.send(new ActionPayload(o.toString()));
	}

	static void send(String action, String key, String value) {
		send(action(action, key, value));
	}

	static JsonObject removeAction(State state, String slot, String id) {
		JsonObject request = action("remove", "slot", slot);
		if (state.multiWear() && id != null) request.addProperty("id", id);
		return request;
	}

	private static JsonObject action(String action, String key, String value) {
		JsonObject request = new JsonObject();
		request.addProperty("action", action);
		if (key != null) request.addProperty(key, value);
		return request;
	}

	static void send(JsonObject request) {
		if (available()) ClientPlayNetworking.send(new ActionPayload(request.toString()));
	}

	private static Map<String, Clip> clips(JsonObject o) {
		if (!o.has("clips")) {
			return Map.of();
		}
		Map<String, Clip> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("clips").entrySet()) {
			JsonObject c = e.getValue().getAsJsonObject();
			out.put(e.getKey(), new Clip(c.get("start").getAsInt(), Math.max(1, c.get("frames").getAsInt()),
					!c.has("loop") || c.get("loop").getAsBoolean()));
		}
		return Map.copyOf(out);
	}

	private static void receive(Minecraft client, String json) {
		JsonObject root;
		try {
			root = JsonParser.parseString(json).getAsJsonObject();
			state = parseState(root);
		} catch (RuntimeException e) {
			MinehouseMenu.LOG.warn("Bad wardrobe state from the server", e);
			return;
		}
		if (client.gui.screen() instanceof WardrobeScreen screen) {
			screen.update(state);
			if (root.get("open").getAsBoolean()) screen.claimFitting();
		} else if (root.get("open").getAsBoolean()) {
			client.gui.setScreen(new WardrobeScreen(state));
		}
	}

	static State parseState(JsonObject root) {
		List<Item> items = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("items")) {
			JsonObject o = e.getAsJsonObject();
			items.add(new Item(o.get("id").getAsString(), o.get("slot").getAsString(), o.get("name").getAsString(),
					o.get("price").getAsInt(), o.get("owned").getAsBoolean(),
					o.has("color") ? o.get("color").getAsInt() : null,
					o.has("defaultColor") ? o.get("defaultColor").getAsInt() : null,
					o.has("frames") ? Math.max(1, o.get("frames").getAsInt()) : 1,
					o.has("fps") ? Math.clamp(o.get("fps").getAsInt(), 1, 20) : 20,
					o.has("kind") ? o.get("kind").getAsString() : null,
					clips(o), o.has("hideLegs") && o.get("hideLegs").getAsBoolean(),
					o.get("flatIcon") instanceof com.google.gson.JsonPrimitive flag && flag.isBoolean() && flag.getAsBoolean()));
		}
		Map<String, String> worn = new LinkedHashMap<>();
		JsonObject legacy = root.getAsJsonObject("worn");
		JsonObject layers = root.getAsJsonObject("wornLayers");
		for (String slot : SLOTS) {
			if (layers != null && layers.has(slot)) {
				var ids = layers.getAsJsonArray(slot);
				for (int i = 0; i < Math.min(ids.size(), layeredCapacity(slot)); i++) {
					worn.put(wearKey(slot, i + 1), ids.get(i).getAsString());
				}
			} else if (legacy.has(slot)) {
				worn.put(slot, legacy.get(slot).getAsString());
			}
		}
		return new State(root.get("coins").getAsInt(), root.get("store").getAsString(), List.copyOf(items), Map.copyOf(worn),
				ArmorVisibility.read(root), layers != null);
	}
}
