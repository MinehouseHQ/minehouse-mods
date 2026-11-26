package pro.minehouse.wardrobe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The same worn poses sent via equipment and displays, for the player whose client draws its own outfit. */
public record AnimationFrames(int entityId, Frame hat, Frame backpack, Frame belt) implements CustomPacketPayload {
	public static final Type<AnimationFrames> TYPE = new Type<>(Identifier.parse("minehouse-cosmetics:animation_frames"));
	public static final StreamCodec<FriendlyByteBuf, AnimationFrames> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AnimationFrames::entityId,
			Frame.CODEC, AnimationFrames::hat,
			Frame.CODEC, AnimationFrames::backpack,
			Frame.CODEC, AnimationFrames::belt,
			AnimationFrames::new);

	public record Frame(String cosmetic, int index) {
		public static final Frame NONE = new Frame("", -1);
		private static final StreamCodec<FriendlyByteBuf, Frame> CODEC = StreamCodec.of(
				(buf, frame) -> {
					buf.writeVarInt(frame.index() + 1);
					if (frame.index() >= 0) buf.writeUtf(frame.cosmetic());
				},
				buf -> {
					int index = buf.readVarInt() - 1;
					return index < 0 ? NONE : new Frame(buf.readUtf(), index);
				});
	}

	@Override public Type<AnimationFrames> type() { return TYPE; }
}
