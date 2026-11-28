package pro.minehouse.riftclient;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A show's rift ({@code "do": "rift"}) for the MineHouse client mod, which draws it as broken
 * violet glass like every rift now (Timur, 28.11: «все разломы замени на новые»): the ids of the
 * crack's displays (the client hides them), the middle, the size and how open it is (0 shut, 1
 * open). Sent twice a second while it is up; the client drops it when they stop or open is 0.
 * Must match pro.minehouse.events.ShowRiftPayload in Events.
 */
public record ShowRiftPayload(int[] hide, double x, double y, double z, double radius, double open) implements CustomPacketPayload {
	public static final Type<ShowRiftPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse", "show_rift"));
	public static final StreamCodec<ByteBuf, ShowRiftPayload> CODEC = StreamCodec.of((buf, p) -> {
		ByteBufCodecs.VAR_INT.encode(buf, p.hide.length);
		for (int id : p.hide) {
			buf.writeInt(id);
		}
		buf.writeDouble(p.x);
		buf.writeDouble(p.y);
		buf.writeDouble(p.z);
		buf.writeDouble(p.radius);
		buf.writeDouble(p.open);
	}, buf -> {
		int n = ByteBufCodecs.VAR_INT.decode(buf);
		if (n < 0 || n > 4096) {
			throw new IllegalArgumentException("show_rift: " + n + " ids");
		}
		int[] hide = new int[n];
		for (int i = 0; i < n; i++) {
			hide[i] = buf.readInt();
		}
		return new ShowRiftPayload(hide, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	});

	@Override
	public Type<ShowRiftPayload> type() {
		return TYPE;
	}
}
