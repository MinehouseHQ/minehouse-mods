package pro.minehouse.menu.poker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import pro.minehouse.menu.MinehouseMenu;

/**
 * Poker tables of MineHouse Bank: the server sends the table as this player sees it, the client
 * draws it from above ({@link PokerScreen}) and sends back the moves. Registering the state channel
 * tells the server this client draws the table (else it gets a window or a vanilla dialog).
 * Protocol in bank-mod's {@code pro.minehouse.bank.poker.PokerNet}.
 */
public final class PokerNet {
	static final String NS = "minehouse-poker";

	public record StatePayload(String json) implements CustomPacketPayload {
		static final Type<StatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "state"));
		static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = ByteBufCodecs.stringUtf8(1 << 18).map(StatePayload::new, StatePayload::json).cast();

		@Override
		public Type<StatePayload> type() {
			return TYPE;
		}
	}

	public record ClosePayload(String table) implements CustomPacketPayload {
		static final Type<ClosePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "close"));
		static final StreamCodec<FriendlyByteBuf, ClosePayload> CODEC = ByteBufCodecs.stringUtf8(256).map(ClosePayload::new, ClosePayload::table).cast();

		@Override
		public Type<ClosePayload> type() {
			return TYPE;
		}
	}

	public record ActPayload(String json) implements CustomPacketPayload {
		static final Type<ActPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "act"));
		static final StreamCodec<FriendlyByteBuf, ActPayload> CODEC = ByteBufCodecs.stringUtf8(2048).map(ActPayload::new, ActPayload::json).cast();

		@Override
		public Type<ActPayload> type() {
			return TYPE;
		}
	}

	private PokerNet() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClosePayload.TYPE, ClosePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActPayload.TYPE, ActPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> receive(context.client(), payload.json()));
		if ("poker".equals(System.getProperty("minehouse.selftest"))) PokerSelfTest.register();
		if ("pokerworld".equals(System.getProperty("minehouse.selftest"))) PokerSelfTest.registerWorld();
		ClientPlayNetworking.registerGlobalReceiver(ClosePayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof PokerScreen screen && screen.table().equals(payload.table())) screen.closeByServer();
		});
	}

	static void receive(Minecraft client, String raw) {
		JsonObject json;
		try {
			json = JsonParser.parseString(raw).getAsJsonObject();
		} catch (RuntimeException e) {
			MinehouseMenu.LOG.warn("Bad poker table from the server", e);
			return;
		}
		String table = json.has("table") ? json.get("table").getAsString() : "";
		boolean open = json.has("open") && json.get("open").getAsBoolean();
		if (client.gui.screen() instanceof PokerScreen screen && screen.table().equals(table)) {
			screen.update(json);
		} else if (open) {
			client.gui.setScreen(new PokerScreen(json));
		}
	}

	static void send(String table, String action, long amount, int seat) {
		JsonObject msg = new JsonObject();
		msg.addProperty("table", table);
		msg.addProperty("a", action);
		if (amount != 0) msg.addProperty("amount", amount);
		if (seat >= 0) msg.addProperty("seat", seat);
		if (ClientPlayNetworking.canSend(ActPayload.TYPE)) ClientPlayNetworking.send(new ActPayload(msg.toString()));
	}
}
