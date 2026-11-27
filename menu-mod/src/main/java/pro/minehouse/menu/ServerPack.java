package pro.minehouse.menu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jspecify.annotations.Nullable;

/**
 * The MineHouse server resource pack (Polymer autohost), loaded before the player joins.
 *
 * <p>Without the mod, joining means: the server pushes its pack, the client downloads it (or finds
 * it in {@code downloads/} by hash) and reloads all resources, which takes a while with a big
 * pack. With the mod:
 * <ul>
 *   <li>the pack is a regular resource pack from game start ({@link #source}), so it is part of
 *       the loading screen the game shows anyway;</li>
 *   <li>when the server pushes a pack with the same SHA-1, {@link #onPush} answers "loaded" at
 *       once, no download and no reload (also no reload when leaving the server);</li>
 *   <li>at game start {@link #startPrefetch} asks the server which pack is current
 *       ({@code minehouse/pack_info}, served by MineHouse Ambient through Polymer) and downloads a
 *       new one in the background, so a pack update does not cost a download on join.</li>
 * </ul>
 * Anything unexpected (no file, other hash, a broken pack) falls back to the vanilla flow.
 */
public final class ServerPack {
	public static final String PACK_ID = "minehouse/server_pack";
	/** Connect and per-read timeouts for pack downloads, ours and vanilla's ({@code HttpUtilMixin}). */
	public static final int CONNECT_TIMEOUT_MS = 10_000;
	public static final int READ_TIMEOUT_MS = 30_000;
	/** {@link #READ_TIMEOUT_MS} for the prefetch; tests shorten it. */
	static int prefetchReadTimeoutMs = READ_TIMEOUT_MS;
	static final String AUTOHOST = "/eu.pb4.polymer.autohost/";
	private static final Pattern SHA1 = Pattern.compile("^[a-f0-9]{40}$");
	private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, true);
	private static final Gson GSON = new Gson();
	private static final long STARTUP_WAIT_MS = 2_000L;

	private static @Nullable Path dir;
	private static @Nullable Path vanillaCache;

	/** The file our repository source offers, null when none (vanilla handles the server pack). */
	private static volatile @Nullable Path active;
	private static volatile @Nullable String activeSha1;
	private static volatile boolean initialized;
	private static volatile @Nullable CompletableFuture<Void> prefetch;

	private ServerPack() {
	}

	/** Remembered between launches: where the MineHouse pack comes from and its newest hash. */
	record State(@Nullable String base, @Nullable String id, @Nullable String sha1) {
		static final State EMPTY = new State(null, null, null);
	}

	// ---- startup -------------------------------------------------------------------------------

	/** Pre-launch: learns the current pack hash and downloads it if it is new. Never blocks. */
	public static void startPrefetch() {
		if (Boolean.getBoolean("minehouse.serverpack.off")) {
			return;
		}
		State state = readState();
		String base = state.base() != null ? state.base() : defaultBase();
		if (base == null) {
			return;
		}
		CompletableFuture<Void> future = new CompletableFuture<>();
		Thread thread = new Thread(() -> {
			try {
				prefetchNow(base, state);
			} catch (Throwable t) {
				MinehouseMenu.LOG.info("MineHouse server pack prefetch skipped: {}", t.toString());
			} finally {
				future.complete(null);
			}
		}, "MineHouse server pack prefetch");
		thread.setDaemon(true);
		prefetch = future;
		thread.start();
	}

	static void prefetchNow(String base, State state) throws IOException {
		String body;
		HttpURLConnection info = open(base + AUTOHOST + "minehouse/pack_info+info.json", 4_000, 5_000);
		try {
			if (info.getResponseCode() != 200) {
				// The server has no MineHouse Ambient with pack_info yet: the pack is still cached on join.
				MinehouseMenu.LOG.info("MineHouse server pack info: HTTP {}", info.getResponseCode());
				return;
			}
			try (InputStream in = info.getInputStream()) {
				body = new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
			}
		} finally {
			info.disconnect();
		}
		JsonObject json = GSON.fromJson(body, JsonObject.class);
		String sha1 = normalize(json.has("sha1") ? json.get("sha1").getAsString() : null);
		String id = json.has("id") ? json.get("id").getAsString() : state.id();
		if (sha1 == null) {
			return;
		}
		writeState(new State(base, id, sha1));
		if (find(sha1, id) != null) {
			return;
		}
		Files.createDirectories(dir());
		Path tmp = Files.createTempFile(dir(), "download-", ".tmp");
		try {
			// HttpURLConnection, not HttpClient: HttpClient's timeout ends with the headers, and a
			// body that stops coming (Polymer rewriting the pack right after a restart) would hang
			// the prefetch forever. Here every read must make progress within READ_TIMEOUT.
			HttpURLConnection pack = open(base + AUTOHOST + "polymer/resources+" + sha1 + ".zip", CONNECT_TIMEOUT_MS, prefetchReadTimeoutMs);
			try {
				if (pack.getResponseCode() != 200) {
					MinehouseMenu.LOG.info("MineHouse server pack download: HTTP {}", pack.getResponseCode());
					return;
				}
				try (InputStream in = pack.getInputStream()) {
					Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				pack.disconnect();
			}
			String actual = sha1(tmp);
			if (!sha1.equals(actual)) {
				// The pack changed between the two requests: next launch gets the new one.
				MinehouseMenu.LOG.info("MineHouse server pack hash mismatch: expected {}, got {}", sha1, actual);
				return;
			}
			Files.move(tmp, file(sha1), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			MinehouseMenu.LOG.info("MineHouse server pack {} downloaded ahead of joining", sha1);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static HttpURLConnection open(String url, int connectTimeoutMs, int readTimeoutMs) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(connectTimeoutMs);
		connection.setReadTimeout(readTimeoutMs);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("User-Agent", "MineHouse client");
		return connection;
	}

	/** First resource load: picks the newest local copy of the MineHouse pack. */
	private static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		if (Boolean.getBoolean("minehouse.serverpack.off")) {
			return;
		}
		CompletableFuture<Void> running = prefetch;
		if (running != null) {
			try {
				running.get(STARTUP_WAIT_MS, TimeUnit.MILLISECONDS);
			} catch (Exception ignored) {
				// Still downloading: start with what we have, the new one is used on join.
			}
		}
		State state = readState();
		Path file = state.sha1() != null ? find(state.sha1(), state.id()) : null;
		if (file != null) {
			activate(file, state.sha1());
		} else {
			// The newest is not here (yet): fall back to the newest we have, it is usually close.
			newestLocal().ifPresent(p -> activate(p, p.getFileName().toString().substring(0, 40)));
		}
		prune();
	}

	private static void activate(Path file, String sha1) {
		active = file;
		activeSha1 = sha1;
		try {
			Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
		} catch (IOException ignored) {
		}
		MinehouseMenu.LOG.info("MineHouse server pack {} preloaded", sha1);
	}

	private static void deactivate() {
		active = null;
		activeSha1 = null;
	}

	// ---- repository source ---------------------------------------------------------------------

	/** Wraps vanilla's server pack source: ours goes next to the packs vanilla downloads. */
	public static RepositorySource source(RepositorySource vanilla) {
		return output -> {
			vanilla.loadPacks(output);
			initialize();
			Pack pack = pack();
			if (pack != null) {
				output.accept(pack);
			}
		};
	}

	private static @Nullable Pack pack() {
		Path file = active;
		if (file == null || !Files.isRegularFile(file)) {
			return null;
		}
		PackLocationInfo location = new PackLocationInfo(
			PACK_ID, Component.literal("MineHouse"), PackSource.SERVER, Optional.empty()
		);
		Pack.ResourcesSupplier resources = new FilePackResources.FileResourcesSupplier(file);
		Pack.Metadata metadata = Pack.readPackMetadata(
			location, resources, SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES), PackType.CLIENT_RESOURCES
		);
		if (metadata == null) {
			MinehouseMenu.LOG.warn("MineHouse server pack {} has no valid pack.mcmeta, not using it", file);
			forget(file);
			return null;
		}
		return new Pack(location, resources, metadata, SELECTION);
	}

	/** A resource reload failed and the game falls back to defaults: never offer our pack again. */
	public static void onRecovery() {
		Path file = active;
		if (file != null) {
			MinehouseMenu.LOG.warn("Resource reload failed, dropping the preloaded MineHouse server pack {}", file);
			forget(file);
		}
	}

	private static void forget(Path file) {
		deactivate();
		try {
			if (file.startsWith(dir())) {
				Files.deleteIfExists(file);
			}
		} catch (IOException ignored) {
		}
	}

	// ---- join ----------------------------------------------------------------------------------

	/**
	 * Server pushed a pack. Returns true when we answered it ourselves and vanilla must not run.
	 * Called on the render thread.
	 */
	public static boolean onPush(Minecraft minecraft, Connection connection, UUID id, String url, @Nullable String hash) {
		if (Boolean.getBoolean("minehouse.serverpack.off")) {
			return false;
		}
		String sha1 = normalize(hash);
		if (sha1 == null) {
			return false;
		}
		String base = mineHouseBase(url);
		if (sha1.equals(activeSha1) && active != null && Files.isRegularFile(active)) {
			// Same pack as the one loaded since game start: nothing to download or reload.
			if (base != null) {
				writeState(new State(base, id.toString(), sha1));
			}
			reply(connection, id, ServerboundResourcePackPacket.Action.ACCEPTED);
			reply(connection, id, ServerboundResourcePackPacket.Action.DOWNLOADED);
			reply(connection, id, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
			MinehouseMenu.LOG.info("MineHouse server pack {} already loaded, skipping download and reload", sha1);
			return true;
		}
		if (base == null) {
			// Some other server's pack: vanilla, on top of ours.
			return false;
		}
		writeState(new State(base, id.toString(), sha1));
		Path file = find(sha1, id.toString());
		if (file == null) {
			// New pack we don't have: vanilla downloads it (with its toast) into downloads/, our old
			// copy goes away in the same reload, next launch preloads the new one.
			deactivate();
			return false;
		}
		// We have it (prefetched at start, or vanilla downloaded it earlier): swap and reload once.
		reply(connection, id, ServerboundResourcePackPacket.Action.ACCEPTED);
		reply(connection, id, ServerboundResourcePackPacket.Action.DOWNLOADED);
		activate(file, sha1);
		minecraft.reloadResourcePacks().whenComplete((ok, error) -> minecraft.execute(() -> {
			boolean loaded = error == null && sha1.equals(activeSha1);
			reply(connection, id, loaded
				? ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED
				: ServerboundResourcePackPacket.Action.FAILED_RELOAD);
		}));
		return true;
	}

	private static void reply(Connection connection, UUID id, ServerboundResourcePackPacket.Action action) {
		if (connection.isConnected()) {
			connection.send(new ServerboundResourcePackPacket(id, action));
		}
	}

	/** {@code http://play.minehouse.pro:25524} for a MineHouse Polymer pack URL, null for others. */
	static @Nullable String mineHouseBase(String url) {
		try {
			URL parsed = URI.create(url).toURL();
			String host = parsed.getHost().toLowerCase(Locale.ROOT);
			boolean ours = host.equals("minehouse.pro") || host.endsWith(".minehouse.pro");
			if (!ours || !parsed.getPath().startsWith(AUTOHOST)
				|| !("http".equals(parsed.getProtocol()) || "https".equals(parsed.getProtocol()))) {
				return null;
			}
			return parsed.getProtocol() + "://" + parsed.getHost() + (parsed.getPort() >= 0 ? ":" + parsed.getPort() : "");
		} catch (Exception e) {
			return null;
		}
	}

	private static @Nullable String defaultBase() {
		String address = MenuConfig.serverAddress;
		if (address == null) {
			return null;
		}
		String host = address.contains(":") ? address.substring(0, address.indexOf(':')) : address;
		host = host.toLowerCase(Locale.ROOT);
		return host.endsWith("minehouse.pro") ? "http://" + host + ":25524" : null;
	}

	// ---- files ---------------------------------------------------------------------------------

	private static Path dir() {
		if (dir == null) {
			dir = FabricLoader.getInstance().getGameDir().resolve("minehouse").resolve("server-pack");
		}
		return dir;
	}

	private static Path vanillaCache() {
		if (vanillaCache == null) {
			vanillaCache = FabricLoader.getInstance().getGameDir().resolve("downloads");
		}
		return vanillaCache;
	}

	static Path file(String sha1) {
		return dir().resolve(sha1 + ".zip");
	}

	/** Our copy of the pack with this hash; adopts vanilla's {@code downloads/<id>/<sha1>}. */
	static @Nullable Path find(String sha1, @Nullable String id) {
		Path ours = file(sha1);
		if (Files.isRegularFile(ours)) {
			return ours;
		}
		if (id == null) {
			return null;
		}
		try {
			Path vanilla = vanillaCache().resolve(UUID.fromString(id).toString()).resolve(sha1);
			if (!Files.isRegularFile(vanilla) || !sha1.equals(sha1(vanilla))) {
				return null;
			}
			Files.createDirectories(dir());
			Path tmp = Files.createTempFile(dir(), "copy-", ".tmp");
			try {
				Files.copy(vanilla, tmp, StandardCopyOption.REPLACE_EXISTING);
				Files.move(tmp, ours, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} finally {
				Files.deleteIfExists(tmp);
			}
			return ours;
		} catch (Exception e) {
			return null;
		}
	}

	private static Optional<Path> newestLocal() {
		if (!Files.isDirectory(dir())) {
			return Optional.empty();
		}
		try (Stream<Path> files = Files.list(dir())) {
			return files.filter(ServerPack::isPackFile).max((a, b) -> Long.compare(mtime(a), mtime(b)));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/** Keeps the active pack and the newest known one, removes older copies. */
	private static void prune() {
		if (!Files.isDirectory(dir())) {
			return;
		}
		State state = readState();
		try (Stream<Path> files = Files.list(dir())) {
			files.filter(ServerPack::isPackFile)
				.filter(p -> !p.equals(active) && (state.sha1() == null || !p.equals(file(state.sha1()))))
				.forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (IOException ignored) {
					}
				});
		} catch (IOException ignored) {
		}
	}

	private static boolean isPackFile(Path p) {
		String name = p.getFileName().toString();
		return name.length() == 44 && name.endsWith(".zip") && SHA1.matcher(name.substring(0, 40)).matches();
	}

	private static long mtime(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}

	static String sha1(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] buf = new byte[1 << 16];
			for (int n; (n = in.read(buf)) > 0; ) {
				digest.update(buf, 0, n);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	static @Nullable String normalize(@Nullable String hash) {
		if (hash == null) {
			return null;
		}
		String lower = hash.trim().toLowerCase(Locale.ROOT);
		return SHA1.matcher(lower).matches() ? lower : null;
	}

	static State readState() {
		Path path = dir().resolve("state.json");
		try {
			if (!Files.isRegularFile(path)) {
				return State.EMPTY;
			}
			State state = GSON.fromJson(Files.readString(path), State.class);
			return state == null ? State.EMPTY : new State(state.base(), state.id(), normalize(state.sha1()));
		} catch (Exception e) {
			return State.EMPTY;
		}
	}

	static synchronized void writeState(State state) {
		try {
			Files.createDirectories(dir());
			Path tmp = Files.createTempFile(dir(), "state-", ".tmp");
			Files.writeString(tmp, GSON.toJson(state));
			Files.move(tmp, dir().resolve("state.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			MinehouseMenu.LOG.warn("Could not save the MineHouse server pack state", e);
		}
	}

	/** Tests: point the caches somewhere else. */
	static void useDirs(Path ours, Path vanilla) {
		dir = ours;
		vanillaCache = vanilla;
		active = null;
		activeSha1 = null;
		initialized = false;
		prefetch = null;
	}

	static @Nullable String activeSha1() {
		return activeSha1;
	}

	static void initializeForTest() {
		initialize();
	}
}
