package pro.minehouse.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Preloading the MineHouse server pack, see {@link ServerPack}. */
class ServerPackTest {
	private static final UUID ID = UUID.fromString("0f0e5e2a-1b9e-3f43-b7b6-8a3a3e0f6c11");

	@TempDir
	Path tmp;

	@Test
	void recognisesOnlyMineHousePolymerUrls() {
		assertEquals("http://play.minehouse.pro:25524",
			ServerPack.mineHouseBase("http://play.minehouse.pro:25524/eu.pb4.polymer.autohost/polymer/resources+abc.zip"));
		assertEquals("http://staging.minehouse.pro:25524",
			ServerPack.mineHouseBase("http://staging.minehouse.pro:25524/eu.pb4.polymer.autohost/polymer/resources+abc.zip"));
		assertNull(ServerPack.mineHouseBase("http://evil.example:25524/eu.pb4.polymer.autohost/polymer/resources+abc.zip"));
		assertNull(ServerPack.mineHouseBase("http://notminehouse.pro/eu.pb4.polymer.autohost/x"));
		assertNull(ServerPack.mineHouseBase("https://www.minehouse.pro/resourcepack/steel.zip"));
		assertNull(ServerPack.mineHouseBase("not a url"));
	}

	@Test
	void normalizesHashes() {
		assertEquals("0123456789abcdef0123456789abcdef01234567",
			ServerPack.normalize(" 0123456789ABCDEF0123456789abcdef01234567 "));
		assertNull(ServerPack.normalize(""));
		assertNull(ServerPack.normalize("1234"));
		assertNull(ServerPack.normalize(null));
	}

	@Test
	void prefetchesNewPackAndPreloadsItAtStartup() throws Exception {
		byte[] pack = zip("{\"pack\":{\"pack_format\":80,\"description\":\"test\"}}");
		String sha1 = sha1(pack);
		AtomicInteger downloads = new AtomicInteger();
		HttpServer server = autohost(pack, sha1, downloads);
		try {
			ServerPack.useDirs(this.tmp.resolve("ours"), this.tmp.resolve("downloads"));
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			ServerPack.prefetchNow(base, ServerPack.State.EMPTY);
			assertEquals(1, downloads.get());
			assertTrue(Files.isRegularFile(ServerPack.file(sha1)));
			assertEquals(sha1, ServerPack.readState().sha1());
			assertEquals(ID.toString(), ServerPack.readState().id());

			// Next launch: same hash, nothing downloaded again.
			ServerPack.prefetchNow(base, ServerPack.readState());
			assertEquals(1, downloads.get());

			ServerPack.initializeForTest();
			assertEquals(sha1, ServerPack.activeSha1());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void ignoresPackWithWrongHash() throws Exception {
		byte[] pack = zip("{}");
		String claimed = "0123456789abcdef0123456789abcdef01234567";
		HttpServer server = autohost(pack, claimed, new AtomicInteger());
		try {
			ServerPack.useDirs(this.tmp.resolve("ours"), this.tmp.resolve("downloads"));
			ServerPack.prefetchNow("http://127.0.0.1:" + server.getAddress().getPort(), ServerPack.State.EMPTY);
			assertFalse(Files.exists(ServerPack.file(claimed)));
			ServerPack.initializeForTest();
			assertNull(ServerPack.activeSha1());
		} finally {
			server.stop(0);
		}
	}

	/** Polymer rewrote the pack mid-download: fewer bytes than promised, connection left open. */
	@Test
	void stalledDownloadTimesOut() throws Exception {
		byte[] pack = zip("{}");
		String sha1 = sha1(pack);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.createContext("/eu.pb4.polymer.autohost/minehouse/pack_info", ex -> {
			byte[] body = ("{\"id\":\"" + ID + "\",\"sha1\":\"" + sha1 + "\"}").getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, body.length);
			ex.getResponseBody().write(body);
			ex.close();
		});
		server.createContext("/eu.pb4.polymer.autohost/polymer/resources", ex -> {
			ex.sendResponseHeaders(200, 100_000);
			ex.getResponseBody().write(pack, 0, 10);
			ex.getResponseBody().flush();
			try {
				Thread.sleep(10_000);
			} catch (InterruptedException ignored) {
			}
		});
		server.start();
		int old = ServerPack.prefetchReadTimeoutMs;
		ServerPack.prefetchReadTimeoutMs = 500;
		try {
			ServerPack.useDirs(this.tmp.resolve("ours"), this.tmp.resolve("downloads"));
			long start = System.nanoTime();
			assertThrows(java.net.SocketTimeoutException.class,
				() -> ServerPack.prefetchNow("http://127.0.0.1:" + server.getAddress().getPort(), ServerPack.State.EMPTY));
			assertTrue(System.nanoTime() - start < 5_000_000_000L, "gave up long before the server did");
			assertFalse(Files.exists(ServerPack.file(sha1)));
		} finally {
			ServerPack.prefetchReadTimeoutMs = old;
			server.stop(0);
		}
	}

	@Test
	void noPackInfoOnServerIsFine() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			ex.sendResponseHeaders(403, -1);
			ex.close();
		});
		server.start();
		try {
			ServerPack.useDirs(this.tmp.resolve("ours"), this.tmp.resolve("downloads"));
			ServerPack.prefetchNow("http://127.0.0.1:" + server.getAddress().getPort(), ServerPack.State.EMPTY);
			assertNull(ServerPack.readState().sha1());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void adoptsPackVanillaDownloaded() throws Exception {
		byte[] pack = zip("{}");
		String sha1 = sha1(pack);
		Path vanilla = this.tmp.resolve("downloads").resolve(ID.toString()).resolve(sha1);
		Files.createDirectories(vanilla.getParent());
		Files.write(vanilla, pack);
		ServerPack.useDirs(this.tmp.resolve("ours"), this.tmp.resolve("downloads"));
		assertEquals(ServerPack.file(sha1), ServerPack.find(sha1, ID.toString()));
		assertTrue(Files.isRegularFile(ServerPack.file(sha1)));

		// A corrupted vanilla copy is not adopted.
		Files.write(this.tmp.resolve("downloads").resolve(ID.toString()).resolve("1111111111111111111111111111111111111111"), pack);
		assertNull(ServerPack.find("1111111111111111111111111111111111111111", ID.toString()));
	}

	@Test
	void keepsStateAcrossLaunches() {
		ServerPack.useDirs(this.tmp.resolve("ours"), this.tmp.resolve("downloads"));
		assertNull(ServerPack.readState().sha1());
		ServerPack.writeState(new ServerPack.State("http://play.minehouse.pro:25524", ID.toString(), "ABCDEF0123456789abcdef0123456789abcdef01"));
		ServerPack.State state = ServerPack.readState();
		assertEquals("http://play.minehouse.pro:25524", state.base());
		assertEquals("abcdef0123456789abcdef0123456789abcdef01", state.sha1());
	}

	/** Polymer autohost: serves anything under its prefix, the part after '+' is ignored. */
	private static HttpServer autohost(byte[] pack, String announcedSha1, AtomicInteger downloads) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/eu.pb4.polymer.autohost/minehouse/pack_info", ex -> {
			byte[] body = ("{\"id\":\"" + ID + "\",\"sha1\":\"" + announcedSha1 + "\"}").getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, body.length);
			ex.getResponseBody().write(body);
			ex.close();
		});
		server.createContext("/eu.pb4.polymer.autohost/polymer/resources", ex -> {
			downloads.incrementAndGet();
			ex.sendResponseHeaders(200, pack.length);
			ex.getResponseBody().write(pack);
			ex.close();
		});
		server.start();
		return server;
	}

	private static byte[] zip(String mcmeta) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("pack.mcmeta"));
			zip.write(mcmeta.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

	private static String sha1(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
	}
}
