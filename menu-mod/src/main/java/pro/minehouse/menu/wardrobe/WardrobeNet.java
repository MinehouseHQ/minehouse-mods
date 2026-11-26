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
import pro.minehouse.wardrobe.IskrikTimeline;

/**
 * The wardrobe channel of the MineHouse Cosmetics server mod. One JSON string each way; the
 * format is documented in cosmetics-mod {@code WardrobeNet}.
 */
public final class WardrobeNet {
	static final String NS = "minehouse-cosmetics";

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

	/**
	 * color: what the player dyed it (or the default), null if it can't be dyed. frames/fps: an
	 * animated cosmetic (Cosmetics 0.9.0+): one model per frame, picked by custom_model_data "f<n>";
	 * frames 1 = still. For Iskrik, use the wearer's UUID and level time to choose a pose.
	 * kind: "cape" or "wings" are worn on the back but listed on their own tab; otherwise null.
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

		/** The clip a frame belongs to (the idle loop if none). */
		Clip clipOf(int frame) {
			for (Clip c : clips.values()) {
				if (frame >= c.start() && frame < c.start() + c.frames()) {
					return c;
				}
			}
			return idle();
		}

		boolean cape() {
			return "cape".equals(kind);
		}

		boolean dyeable() {
			return color != null;
		}

		/** The owner, F5 layer and wardrobe cards use the same local player's game tick. */
		int frame() {
			var player = Minecraft.getInstance().player;
			return frameAt(player == null ? null : player.getUUID(), player == null ? 0 : player.level().getGameTime(),
					System.currentTimeMillis());
		}

		int frameAt(UUID wearer, long gameTick, long millis) {
			if (IskrikTimeline.matches(id, frames, fps)) return wearer == null ? 0 : IskrikTimeline.frame(wearer, gameTick);
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

	public record State(int coins, String store, List<Item> items, Map<String, String> worn, ArmorVisibility armorVisibility) {
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

	private WardrobeNet() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(FittingEnd.TYPE, FittingEnd.CODEC.cast());
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC.cast());
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC.cast());
		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> receive(context.client(), payload.json()));
		ClientPlayNetworking.registerGlobalReceiver(FittingEnd.TYPE, (payload, context) -> payload.apply(context.client().gui.screen()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state = null);
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
		if (!available()) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("action", action);
		if (key != null) {
			o.addProperty(key, value);
		}
		ClientPlayNetworking.send(new ActionPayload(o.toString()));
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
		} catch (RuntimeException e) {
			MinehouseMenu.LOG.warn("Bad wardrobe state from the server", e);
			return;
		}
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
		root.getAsJsonObject("worn").entrySet().forEach(en -> worn.put(en.getKey(), en.getValue().getAsString()));
		state = new State(root.get("coins").getAsInt(), root.get("store").getAsString(), List.copyOf(items), Map.copyOf(worn),
				ArmorVisibility.read(root));

		if (client.gui.screen() instanceof WardrobeScreen screen) {
			screen.update(state);
			if (root.get("open").getAsBoolean()) screen.claimFitting();
		} else if (root.get("open").getAsBoolean()) {
			client.gui.setScreen(new WardrobeScreen(state));
		}
	}
}
