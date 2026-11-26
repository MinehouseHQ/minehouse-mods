package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pro.minehouse.wardrobe.AnimationFrames;
import pro.minehouse.wardrobe.AnimationFrames.Frame;

class AnimationFramesTest {
	private static final int WEARER = 42;
	private static final WardrobeNet.Item KARAT = item("karat", "hat", 1378, 5, Map.of());
	private static final WardrobeNet.Item ISKRIK = item("iskrik", "backpack", 448, 20, Map.of());
	private static final WardrobeNet.Item BELT = item("spider_legs", "belt", 1, 20,
			Map.of("idle", new WardrobeNet.Clip(0, 1, true), "walk", new WardrobeNet.Clip(4, 8, true)));

	@AfterEach
	void clearConnection() {
		WardrobeNet.reset();
	}

	@Test
	void reactiveBeltNeverPredictsTheNextFrameOfAHeldOrInterruptedClip() {
		receive(new AnimationFrames(WEARER, Frame.NONE, Frame.NONE, new Frame("spider_legs", 11)));
		assertEquals(CosmeticMotion.Pick.of(11), pick(BELT));
		receive(new AnimationFrames(WEARER, Frame.NONE, Frame.NONE, new Frame("spider_legs", 0)));
		assertEquals(CosmeticMotion.Pick.of(0), pick(BELT));
	}

	@Test
	void receivedKaratFrameDeterminesTheOwnersMountAtEachHandoff() {
		for (int frame : new int[] {286, 287, 651, 652, 975, 976, 1370, 1371, 1377, 0}) {
			receive(new AnimationFrames(WEARER, new Frame("karat", frame), Frame.NONE, Frame.NONE));
			var owner = pick(KARAT);
			assertEquals(frame, owner.a());
			assertEquals(frame, owner.b());
			assertEquals(KaratMount.onShoulder("karat", frame), KaratMount.onShoulder("karat", owner.b()));
		}
	}

	@Test
	void serverOwnsTheBackpackAndReactiveBeltIndependently() {
		receive(new AnimationFrames(WEARER, Frame.NONE, new Frame("iskrik", 328), new Frame("spider_legs", 11)));
		assertEquals(CosmeticMotion.Pick.of(328), pick(ISKRIK));
		var belt = pick(BELT);
		assertEquals(11, belt.a());
		assertEquals(11, belt.b()); // no prediction of either the next walk frame or the idle clip
		assertFalse(CosmeticMotion.draw(CosmeticMotion.worn(WEARER + 1, BELT, false)));
		receive(new AnimationFrames(WEARER, Frame.NONE, new Frame("different_backpack", 99), Frame.NONE));
		assertFalse(CosmeticMotion.draw(pick(ISKRIK)));
		assertFalse(CosmeticMotion.draw(pick(BELT)));
		assertTrue(CosmeticMotion.draw(CosmeticMotion.worn(WEARER, BELT, true)), "try-on is independent of the live outfit");
		assertNull(pick(item("static_belt", "belt", 1, 20, Map.of())));
	}

	@Test
	void backendJoinClearsSnapshotEvenIfEntityIdIsReused() {
		WardrobeNet.state = new WardrobeNet.State(0, "", java.util.List.of(BELT), Map.of(), ArmorVisibility.UNAVAILABLE);
		receive(new AnimationFrames(WEARER, Frame.NONE, Frame.NONE, new Frame("spider_legs", 11)));
		WardrobeNet.reset(); // Gate backend JOIN, without relying on DISCONNECT
		assertNull(WardrobeNet.state);
		assertNull(WardrobeNet.animation);
		assertEquals(0, pick(BELT).a()); // older backend still uses the local idle until it sees movement
	}

	private static CosmeticMotion.Pick pick(WardrobeNet.Item item) {
		return CosmeticMotion.worn(WEARER, item, false);
	}

	private static void receive(AnimationFrames payload) {
		var buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			AnimationFrames.CODEC.encode(buf, payload);
			WardrobeNet.animation = AnimationFrames.CODEC.decode(buf);
			assertEquals(payload, WardrobeNet.animation);
			assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}

	private static WardrobeNet.Item item(String id, String slot, int frames, int fps, Map<String, WardrobeNet.Clip> clips) {
		return new WardrobeNet.Item(id, slot, id, 0, true, null, null, frames, fps, null, clips, false, false);
	}
}
