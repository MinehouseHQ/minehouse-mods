package pro.minehouse.riftclient;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The End opening's beam and white-out, for players with this mod (Timur 28.11: «с клиентским модом сделай»).
 * Must match pro.minehouse.end.cannon.LimboNet in the End mod.
 */
public final class LimboPayloads {
	private LimboPayloads() {
	}

	/**
	 * A beam out of a stronghold portal: its foot, the top of its hole, how wide; sent every half second while it
	 * stands (the client drops one it stops hearing about), {@code fading} for its last second and a half.
	 */
	public record Beam(int id, String world, double x, double y, double z, int top, float width, boolean fading)
			implements CustomPacketPayload {
		public static final Type<Beam> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse", "limbo_beam"));
		public static final StreamCodec<ByteBuf, Beam> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeInt(p.id);
			ByteBufCodecs.STRING_UTF8.encode(buf, p.world);
			buf.writeDouble(p.x);
			buf.writeDouble(p.y);
			buf.writeDouble(p.z);
			buf.writeInt(p.top);
			buf.writeFloat(p.width);
			buf.writeBoolean(p.fading);
		}, buf -> new Beam(buf.readInt(), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readDouble(), buf.readDouble(),
				buf.readDouble(), buf.readInt(), buf.readFloat(), buf.readBoolean()));

		@Override
		public Type<Beam> type() {
			return TYPE;
		}
	}

	/** The screen whitens over {@code ticks} and stays white until this player is in another world. */
	public record Flash(int ticks) implements CustomPacketPayload {
		public static final Type<Flash> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse", "limbo_flash"));
		public static final StreamCodec<ByteBuf, Flash> CODEC = StreamCodec.of((buf, p) -> buf.writeInt(p.ticks),
				buf -> new Flash(buf.readInt()));

		@Override
		public Type<Flash> type() {
			return TYPE;
		}
	}
}
