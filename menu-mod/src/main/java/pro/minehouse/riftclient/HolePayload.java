package pro.minehouse.riftclient;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Where the black hole of {@code "do": "black_hole"} really is, for the MineHouse client mod: the
 * entity ids of this viewer's two pictures (the client hides them and draws the hole itself), the
 * centre, the size and how open it is. Sent twice a second while the hole is up; the client drops
 * the hole when they stop. Must match pro.minehouse.events.HolePayload in Events.
 */
public record HolePayload(int core, int rays, double x, double y, double z, double size, double open)
		implements CustomPacketPayload {
	public static final Type<HolePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse", "black_hole"));
	public static final StreamCodec<ByteBuf, HolePayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeInt(p.core);
		buf.writeInt(p.rays);
		buf.writeDouble(p.x);
		buf.writeDouble(p.y);
		buf.writeDouble(p.z);
		buf.writeDouble(p.size);
		buf.writeDouble(p.open);
	}, buf -> new HolePayload(buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
			buf.readDouble(), buf.readDouble()));

	@Override
	public Type<HolePayload> type() {
		return TYPE;
	}
}
