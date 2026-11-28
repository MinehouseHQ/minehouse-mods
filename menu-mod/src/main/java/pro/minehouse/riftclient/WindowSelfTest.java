package pro.minehouse.riftclient;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Checks of the see-through rift window ({@link PortalWindows}).
 *
 * <ul>
 * <li>{@link #check()}, in the rift self-test: the codec, that a room is read back into faces, and
 * that the rift breaks into its pieces and the cut sees through them.</li>
 * <li>-Pselftest=window -Pjoin=localhost:25565 with a local End server (end-mod runWindowDemo):
 * a screenshot every two seconds (window-N.png) while that server walks the player round a rift
 * and through it.</li>
 * </ul>
 */
final class WindowSelfTest {
	private WindowSelfTest() {
	}

	/** A box of 3 x 3 x 3 with a stone floor and a glowstone in the middle, behind a window at the origin. */
	static WindowPayloads.View sample() {
		int sx = 3, sy = 3, sz = 3, count = sx * sy * sz;
		byte[] raw = new byte[count * 3];
		for (int i = 0; i < count; i++) {
			int y = i / (sx * sz);
			int p = y == 0 ? 1 : i == 13 ? 2 : 0;
			raw[i * 2 + 1] = (byte) p;
			raw[count * 2 + i] = (byte) 0xF0;
		}
		Deflater d = new Deflater();
		d.setInput(raw);
		d.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[1024];
		while (!d.finished()) {
			out.write(chunk, 0, d.deflate(chunk));
		}
		d.end();
		int[] palette = { Block.getId(Blocks.AIR.defaultBlockState()), Block.getId(Blocks.STONE.defaultBlockState()),
				Block.getId(Blocks.GLOWSTONE.defaultBlockState()) };
		Identifier world = Identifier.withDefaultNamespace("overworld");
		Identifier target = Identifier.fromNamespaceAndPath("minehouse", "breach");
		return new WindowPayloads.View(7, 12345, world, target, 0.5, 64, 0.5, 1000.5, 100, 1000.5, 3.2f, 4.4f, 1f, 999, 99, 999,
				sx, sy, sz, palette, out.toByteArray());
	}

	/** Null when all is well, else what went wrong. */
	static String check() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		WindowPayloads.View sent = sample();
		WindowPayloads.View.CODEC.encode(buf, sent);
		WindowPayloads.View got = WindowPayloads.View.CODEC.decode(buf);
		if (buf.readableBytes() != 0 || got.id() != 7 || got.sizeY() != 3 || got.palette().length != 3 || !got.target().equals(sent.target())) {
			return "view codec";
		}
		PortalWindows.Window w;
		try {
			w = PortalWindows.build(got);
		} catch (Exception e) {
			return "build: " + e;
		}
		// Floor: 8 tops (the glowstone covers one), 12 sides round the edge (outside the box counts as air), 9 bottoms; the glowstone: 5 faces.
		if (w.solid.size() != 8 + 12 + 9 + 5) {
			return "faces " + w.solid.size();
		}
		// The glass: three panes and the slivers, every piece out once it has opened, seen from 4 blocks in front.
		if (w.shards.size() < 25) {
			return "shards " + w.shards.size();
		}
		ShardCut cut = new ShardCut(w.shards, 0, 0, 4, 2.2f, 1f, 0, true);
		int out = 0, edgeOn = 0;
		for (int i = 0; i < cut.count; i++) {
			out += cut.shown[i] ? 1 : 0;
			edgeOn += cut.shown[i] && cut.sides[i] == null ? 1 : 0;
		}
		if (!cut.through || cut.d0 < 3.9f || cut.d0 > 4.1f || out != w.shards.size() || edgeOn > out / 3) {
			return "shard cut " + out + "/" + w.shards.size() + ", edge-on " + edgeOn;
		}
		// From inside the middle nothing is seen through it.
		if (new ShardCut(w.shards, 0, 0, 0.1f, 2.2f, 1f, 0, true).through) {
			return "shard cut from inside";
		}
		float[] in = { 0, 0, 1, 0, 0, 0.2f, 0, 1, 0, 0, 0.2f, 0.2f, 1, 0, 0 };
		float[] tmp = new float[64];
		if (ShardCut.clip(in, 3, tmp, 0, 0, 1, 0.5f) != 3 || ShardCut.clip(in, 3, tmp, 0, 0, 1, 2f) != 0) {
			return "clip";
		}
		return null;
	}

	static void registerWorld() {
		int[] ticks = { 0 };
		int[] shots = { 0 };
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null || client.player == null) {
				return;
			}
			if (++ticks[0] % 40 != 0) {
				return;
			}
			int n = ++shots[0];
			Minecraft mc = client;
			MinehouseRiftClient.LOG.info("Window demo {}: {} at {}, windows {}, screen {}", n, mc.level.dimension().identifier(),
					mc.player.position(), PortalWindows.windows(), mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName());
			Screenshot.grab(client.gameDirectory, "window-" + n + ".png", client.gameRenderer.mainRenderTarget(), 1,
					msg -> MinehouseRiftClient.LOG.info("Screenshot: {}", msg.getString()));
			if (n >= 45) {
				MinehouseRiftClient.LOG.info("MINEHOUSE_WINDOW_DEMO_DONE");
				client.stop();
			}
		});
	}
}
