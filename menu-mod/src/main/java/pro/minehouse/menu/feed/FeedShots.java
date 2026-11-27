package pro.minehouse.menu.feed;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import pro.minehouse.menu.MinehouseMenu;

/**
 * F2 into the site feed (MineHouse Chat 0.14.0+, chat-mod {@code FeedShots}). The server says it takes
 * screenshots ({@code feed_hello}); after F2 we tell it the size of the new picture, and it offers
 * [В ленту] in chat. Only when the player clicks it does the server send an upload link: we PUT the PNG
 * there (scaled down if it's too big) and report back. The uploaded picture stays as a small texture,
 * so the server's /post window can show it ({@link ThumbsFeature}). Nothing leaves the computer before that click.
 */
public final class FeedShots {
	static final String NS = "minehouse";
	/** How long to wait for vanilla to write the file after F2. */
	private static final long WAIT_MS = 8000;

	public record HelloPayload(boolean on) implements CustomPacketPayload {
		static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "feed_hello"));
		static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC = CustomPacketPayload.codec(
				(p, buf) -> buf.writeBoolean(p.on), buf -> new HelloPayload(buf.readBoolean()));

		@Override
		public Type<HelloPayload> type() {
			return TYPE;
		}
	}

	public record ShotPayload(int width, int height) implements CustomPacketPayload {
		static final Type<ShotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "feed_shot"));
		static final StreamCodec<FriendlyByteBuf, ShotPayload> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeVarInt(p.width);
					buf.writeVarInt(p.height);
				}, buf -> new ShotPayload(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<ShotPayload> type() {
			return TYPE;
		}
	}

	public record UploadPayload(String id, String url, int maxBytes) implements CustomPacketPayload {
		static final Type<UploadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "feed_upload"));
		static final StreamCodec<FriendlyByteBuf, UploadPayload> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeUtf(p.id);
					buf.writeUtf(p.url, 4096);
					buf.writeVarInt(p.maxBytes);
				}, buf -> new UploadPayload(buf.readUtf(), buf.readUtf(4096), buf.readVarInt()));

		@Override
		public Type<UploadPayload> type() {
			return TYPE;
		}
	}

	public record UploadedPayload(String id, String error) implements CustomPacketPayload {
		static final Type<UploadedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "feed_uploaded"));
		static final StreamCodec<FriendlyByteBuf, UploadedPayload> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeUtf(p.id);
					buf.writeUtf(p.error);
				}, buf -> new UploadedPayload(buf.readUtf(), buf.readUtf()));

		@Override
		public Type<UploadedPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Sent by nobody: a client that registers it shows uploaded screenshots in server windows as the
	 * texture {@code minehouse:feed_shot/<id>} (ServerUi "image" nodes, client 1.51.0+).
	 */
	public record ThumbsFeature() implements CustomPacketPayload {
		static final Type<ThumbsFeature> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "feed_thumbs"));
		static final StreamCodec<FriendlyByteBuf, ThumbsFeature> CODEC = StreamCodec.unit(new ThumbsFeature());

		@Override
		public Type<ThumbsFeature> type() {
			return TYPE;
		}
	}

	/** Thumbnail width; the height keeps the screenshot's proportions. */
	private static final int THUMB_W = 256;
	private static final java.util.Set<Identifier> thumbs = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static volatile boolean on;
	/** The last F2 picture, the one an upload link is for. */
	private static volatile Path last;

	private FeedShots() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UploadPayload.TYPE, UploadPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ThumbsFeature.TYPE, ThumbsFeature.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShotPayload.TYPE, ShotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UploadedPayload.TYPE, UploadedPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (p, ctx) -> on = p.on());
		ClientPlayNetworking.registerGlobalReceiver(UploadPayload.TYPE, (p, ctx) -> upload(p));
		ClientPlayNetworking.registerGlobalReceiver(ThumbsFeature.TYPE, (p, ctx) -> {
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			on = false;
			last = null;
			client.execute(FeedShots::releaseThumbs);
		});
	}

	/** F2 was pressed (ScreenshotMixin). Vanilla writes the file on another thread; we find it there. */
	public static void taken(File gameDirectory) {
		if (!on) {
			return;
		}
		long since = System.currentTimeMillis() - 1000;
		Path dir = gameDirectory.toPath().resolve("screenshots");
		Thread t = new Thread(() -> find(dir, since), "MineHouse feed shot");
		t.setDaemon(true);
		t.start();
	}

	private static void find(Path dir, long since) {
		long until = System.currentTimeMillis() + WAIT_MS;
		try {
			while (System.currentTimeMillis() < until) {
				Thread.sleep(300);
				Optional<Path> shot = newest(dir, since);
				if (shot.isEmpty()) {
					continue;
				}
				long size = Files.size(shot.get());
				Thread.sleep(300);
				if (size == 0 || size != Files.size(shot.get())) {
					continue;
				}
				int[] wh = pngSize(shot.get());
				if (wh == null) {
					return;
				}
				last = shot.get();
				Minecraft.getInstance().execute(() -> {
					if (on && ClientPlayNetworking.canSend(ShotPayload.TYPE)) {
						ClientPlayNetworking.send(new ShotPayload(wh[0], wh[1]));
					}
				});
				return;
			}
		} catch (IOException | InterruptedException e) {
			MinehouseMenu.LOG.debug("Feed shot not found: {}", e.getMessage());
		}
	}

	private static Optional<Path> newest(Path dir, long since) throws IOException {
		File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(".png"));
		if (files == null) {
			return Optional.empty();
		}
		return Arrays.stream(files)
				.filter(f -> f.lastModified() >= since)
				.max(Comparator.comparingLong(File::lastModified))
				.map(File::toPath);
	}

	/** Width and height from the PNG header (IHDR), or null if it isn't a PNG. */
	static int[] pngSize(Path file) throws IOException {
		byte[] head = new byte[24];
		try (InputStream in = Files.newInputStream(file)) {
			if (in.readNBytes(head, 0, 24) < 24) {
				return null;
			}
		}
		if ((head[0] & 0xff) != 0x89 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
			return null;
		}
		return new int[] { readInt(head, 16), readInt(head, 20) };
	}

	private static int readInt(byte[] b, int at) {
		return (b[at] & 0xff) << 24 | (b[at + 1] & 0xff) << 16 | (b[at + 2] & 0xff) << 8 | b[at + 3] & 0xff;
	}

	private static void upload(UploadPayload p) {
		Path file = last;
		if (file == null) {
			reply(p.id(), "no_file");
			return;
		}
		Thread t = new Thread(() -> {
			String error = "";
			Path tmp = null;
			try {
				byte[] png = Files.readAllBytes(file);
				while (png.length > p.maxBytes()) {
					tmp = tmp == null ? Files.createTempFile("minehouse-shot", ".png") : tmp;
					png = halve(png, tmp);
				}
				HttpRequest request = HttpRequest.newBuilder(URI.create(p.url()))
						.timeout(Duration.ofSeconds(60))
						.header("Content-Type", "image/png")
						.header("x-upsert", "true")
						.PUT(HttpRequest.BodyPublishers.ofByteArray(png))
						.build();
				HttpResponse<String> r = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (r.statusCode() / 100 != 2) {
					MinehouseMenu.LOG.warn("Feed shot upload: HTTP {} {}", r.statusCode(), r.body());
					error = "http_" + r.statusCode();
				}
			} catch (IOException | InterruptedException | RuntimeException e) {
				MinehouseMenu.LOG.warn("Feed shot upload failed", e);
				error = "upload";
			} finally {
				if (tmp != null) {
					try {
						Files.deleteIfExists(tmp);
					} catch (IOException ignored) {
					}
				}
			}
			NativeImage thumb = error.isEmpty() ? thumbnail(file) : null;
			String result = error;
			Minecraft.getInstance().execute(() -> {
				if (thumb != null) {
					registerThumb(p.id(), thumb);
				}
				reply(p.id(), result);
			});
		}, "MineHouse feed upload");
		t.setDaemon(true);
		t.start();
	}

	/** A small copy of the screenshot for the server's windows, or null. */
	private static NativeImage thumbnail(Path file) {
		try (NativeImage src = NativeImage.read(Files.readAllBytes(file))) {
			int w = Math.min(THUMB_W, src.getWidth());
			int h = Math.max(1, Math.round(src.getHeight() * (w / (float) src.getWidth())));
			NativeImage dst = new NativeImage(w, h, false);
			src.resizeSubRectTo(0, 0, src.getWidth(), src.getHeight(), dst);
			return dst;
		} catch (IOException | RuntimeException e) {
			MinehouseMenu.LOG.debug("Feed shot thumbnail failed: {}", e.getMessage());
			return null;
		}
	}

	/** The texture a server window shows for an uploaded screenshot (ids are the site's UUIDs). */
	static Identifier thumbId(String id) {
		return Identifier.fromNamespaceAndPath(NS, "feed_shot/" + id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^0-9a-f-]", ""));
	}

	private static void registerThumb(String id, NativeImage image) {
		Identifier key = thumbId(id);
		DynamicTexture texture = new DynamicTexture(key::toString, image);
		texture.upload();
		Minecraft.getInstance().getTextureManager().register(key, texture);
		thumbs.add(key);
	}

	private static void releaseThumbs() {
		for (Identifier key : thumbs) {
			Minecraft.getInstance().getTextureManager().release(key);
		}
		thumbs.clear();
	}

	/** The picture at half the size, as PNG. */
	private static byte[] halve(byte[] png, Path tmp) throws IOException {
		try (NativeImage src = NativeImage.read(png)) {
			int w = Math.max(1, src.getWidth() / 2), h = Math.max(1, src.getHeight() / 2);
			try (NativeImage dst = new NativeImage(w, h, false)) {
				src.resizeSubRectTo(0, 0, src.getWidth(), src.getHeight(), dst);
				dst.writeToFile(tmp);
			}
		}
		return Files.readAllBytes(tmp);
	}

	private static void reply(String id, String error) {
		if (ClientPlayNetworking.canSend(UploadedPayload.TYPE)) {
			ClientPlayNetworking.send(new UploadedPayload(id, error));
		}
	}
}
