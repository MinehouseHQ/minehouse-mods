package pro.minehouse.menu.tab;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import pro.minehouse.menu.MinehouseMenu;

/**
 * Channel {@code minehouse-ui:tab} (MineHouse Auth {@code TabFeed}). Registering it tells the server
 * this client draws its own tab list; from the first message until disconnect {@link TabOverlay}
 * draws it instead of vanilla's. A server that never sends it keeps the vanilla list.
 */
public final class TabNet {
	public record Payload(String json) implements CustomPacketPayload {
		static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse-ui", "tab"));
		static final StreamCodec<FriendlyByteBuf, Payload> CODEC = ByteBufCodecs.stringUtf8(1 << 18).map(Payload::new, Payload::json).cast();

		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}

	private static volatile TabData data;

	private TabNet() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> {
			TabData next = TabData.parse(payload.json());
			if (next == null) {
				MinehouseMenu.LOG.warn("Bad tab list data from the server");
				return;
			}
			data = next;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> data = null);
		if ("tab".equals(System.getProperty("minehouse.selftest"))) TabSelfTest.register();
	}

	/** The latest data, or null when the server does not send it (then vanilla draws). */
	public static TabData data() {
		return data;
	}
}
