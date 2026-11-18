package pro.minehouse.riftclient;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code minehouse:intro}, no data: the server asks to play the opening of Chapter 2
 * ({@link Intro}). Steel sends it on a player's first visit.
 */
public record IntroPayload() implements CustomPacketPayload {
	public static final IntroPayload INSTANCE = new IntroPayload();
	public static final Type<IntroPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse", "intro"));
	public static final StreamCodec<ByteBuf, IntroPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<IntroPayload> type() {
		return TYPE;
	}
}
