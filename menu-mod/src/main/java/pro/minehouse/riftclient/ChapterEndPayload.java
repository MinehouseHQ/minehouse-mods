package pro.minehouse.riftclient;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code minehouse:chapter_end}, no data: Chapter 1 is over, the server is about to kick everyone
 * and stop ({@link pro.minehouse.menu.ChapterEnd}). Must match pro.minehouse.events.ChapterEndPayload.
 */
public record ChapterEndPayload() implements CustomPacketPayload {
	public static final ChapterEndPayload INSTANCE = new ChapterEndPayload();
	public static final Type<ChapterEndPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("minehouse", "chapter_end"));
	public static final StreamCodec<ByteBuf, ChapterEndPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ChapterEndPayload> type() {
		return TYPE;
	}
}
