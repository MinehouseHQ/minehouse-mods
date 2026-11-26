package pro.minehouse.menu.ui;

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
 * Server windows (MineHouse Guides {@code ServerUi}): the server describes a window, this mod
 * draws it ({@link UiScreen}) and sends the clicks back. Registering the channels tells the server
 * this client draws them.
 */
public final class UiNet {
	static final String NS = "minehouse-ui";
	/** The newest format this client understands. */
	static final int VERSION = 1;

	public record WindowPayload(String json) implements CustomPacketPayload {
		static final Type<WindowPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "window"));
		static final StreamCodec<FriendlyByteBuf, WindowPayload> CODEC = ByteBufCodecs.stringUtf8(1 << 20).map(WindowPayload::new, WindowPayload::json).cast();

		@Override
		public Type<WindowPayload> type() {
			return TYPE;
		}
	}

	public record ClosePayload(String id) implements CustomPacketPayload {
		static final Type<ClosePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "close"));
		static final StreamCodec<FriendlyByteBuf, ClosePayload> CODEC = ByteBufCodecs.stringUtf8(256).map(ClosePayload::new, ClosePayload::id).cast();

		@Override
		public Type<ClosePayload> type() {
			return TYPE;
		}
	}

	public record ActionPayload(String json) implements CustomPacketPayload {
		static final Type<ActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "action"));
		static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = ByteBufCodecs.stringUtf8(8192).map(ActionPayload::new, ActionPayload::json).cast();

		@Override
		public Type<ActionPayload> type() {
			return TYPE;
		}
	}

	private UiNet() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(WindowPayload.TYPE, WindowPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClosePayload.TYPE, ClosePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(WindowPayload.TYPE, (payload, context) -> receive(context.client(), payload.json()));
		ClientPlayNetworking.registerGlobalReceiver(ClosePayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof UiScreen screen && screen.id().equals(payload.id())) screen.closeByServer();
		});
		if ("ui".equals(System.getProperty("minehouse.selftest"))) UiSelfTest.register();
	}

	/** Shows a window: updates it if it is open, opens it if the server says so. */
	public static void receive(Minecraft client, String raw) {
		JsonObject json;
		try {
			json = JsonParser.parseString(raw).getAsJsonObject();
		} catch (RuntimeException e) {
			MinehouseMenu.LOG.warn("Bad window from the server", e);
			return;
		}
		if (UiTree.num(json, "v", 1) > VERSION) {
			MinehouseMenu.LOG.warn("Window {} is in a newer format ({}) than this client knows ({}); drawing what it can",
					UiTree.str(json, "id", "?"), UiTree.num(json, "v", 1), VERSION);
		}
		String id = UiTree.str(json, "id", "");
		boolean open = json.has("open") && json.get("open").getAsBoolean();
		if (client.gui.screen() instanceof UiScreen screen && screen.id().equals(id)) {
			screen.update(json);
		} else if (open) {
			client.gui.setScreen(new UiScreen(json));
		}
	}

	/** Whether the server draws its windows through us (it registered the window channels). */
	public static boolean available() {
		return ClientPlayNetworking.canSend(ActionPayload.TYPE);
	}

	static void send(JsonObject message) {
		if (ClientPlayNetworking.canSend(ActionPayload.TYPE)) {
			ClientPlayNetworking.send(new ActionPayload(message.toString()));
		}
	}
}
