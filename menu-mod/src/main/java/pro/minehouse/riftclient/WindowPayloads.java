package pro.minehouse.riftclient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The see-through rift window (End 0.33.1+): the room behind a window, its size, and that it is
 * gone. Must match pro.minehouse.end.rift.WindowView in the End mod. See {@link PortalWindows}.
 */
public final class WindowPayloads {
	private WindowPayloads() {
	}

	/**
	 * A window in {@code world} standing at {@code b} (bottom middle), {@code width} by
	 * {@code height}, shown by the display {@code entity}; walking through it moves you by
	 * {@code t - b} into {@code target}. The blocks round {@code t}: a box from {@code min} of
	 * {@code size}, x fastest, then z, then y; {@code blocks} deflated is two bytes of palette index
	 * per block, then a byte of light (sky << 4 | block) per block.
	 */
	public record View(int id, int entity, Identifier world, Identifier target, double bx, double by, double bz,
			double tx, double ty, double tz, float width, float height, float scale, int minX, int minY, int minZ,
			int sizeX, int sizeY, int sizeZ, int[] palette, byte[] blocks) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse-end", "window_view"));
		public static final StreamCodec<FriendlyByteBuf, View> CODEC = StreamCodec.of((buf, v) -> {
			buf.writeVarInt(v.id);
			buf.writeVarInt(v.entity);
			buf.writeIdentifier(v.world);
			buf.writeIdentifier(v.target);
			buf.writeDouble(v.bx);
			buf.writeDouble(v.by);
			buf.writeDouble(v.bz);
			buf.writeDouble(v.tx);
			buf.writeDouble(v.ty);
			buf.writeDouble(v.tz);
			buf.writeFloat(v.width);
			buf.writeFloat(v.height);
			buf.writeFloat(v.scale);
			buf.writeInt(v.minX);
			buf.writeInt(v.minY);
			buf.writeInt(v.minZ);
			buf.writeVarInt(v.sizeX);
			buf.writeVarInt(v.sizeY);
			buf.writeVarInt(v.sizeZ);
			buf.writeVarIntArray(v.palette);
			buf.writeByteArray(v.blocks);
		}, buf -> new View(buf.readVarInt(), buf.readVarInt(), buf.readIdentifier(), buf.readIdentifier(), buf.readDouble(),
				buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarIntArray(), buf.readByteArray()));

		@Override
		public Type<View> type() {
			return TYPE;
		}
	}

	/** The window eases to {@code scale} (1: full size) over {@code ticks}. */
	public record Scale(int id, float scale, int ticks) implements CustomPacketPayload {
		public static final Type<Scale> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse-end", "window_scale"));
		public static final StreamCodec<FriendlyByteBuf, Scale> CODEC = StreamCodec.of((buf, s) -> {
			buf.writeVarInt(s.id);
			buf.writeFloat(s.scale);
			buf.writeVarInt(s.ticks);
		}, buf -> new Scale(buf.readVarInt(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<Scale> type() {
			return TYPE;
		}
	}

	public record Gone(int id) implements CustomPacketPayload {
		public static final Type<Gone> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse-end", "window_gone"));
		public static final StreamCodec<FriendlyByteBuf, Gone> CODEC = StreamCodec.of((buf, g) -> buf.writeVarInt(g.id),
				buf -> new Gone(buf.readVarInt()));

		@Override
		public Type<Gone> type() {
			return TYPE;
		}
	}
}
