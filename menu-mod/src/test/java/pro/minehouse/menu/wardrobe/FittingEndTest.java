package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class FittingEndTest {
	private static class OtherScreen extends Screen {
		int closes;
		OtherScreen() { super(null, null, Component.empty()); }
		@Override public void onClose() { closes++; }
	}

	private static final class FittingScreen extends OtherScreen implements WardrobeNet.FittingView {
		@Override public void expire() { onClose(); }
	}

	@Test void endIsNotAStateUpdateAndOnlyDismissesAFittingView() {
		var wire = new FriendlyByteBuf(Unpooled.buffer());
		try {
			WardrobeNet.FittingEnd.CODEC.encode(wire, WardrobeNet.FittingEnd.INSTANCE);
			assertEquals(0, wire.readableBytes());
			var end = WardrobeNet.FittingEnd.CODEC.decode(wire);
			assertEquals("minehouse-cosmetics:fitting_end", end.type().id().toString());
			var fitting = new FittingScreen();
			end.apply(fitting);
			assertEquals(1, fitting.closes);
			var other = new OtherScreen();
			end.apply(other);
			assertEquals(0, other.closes);
			end.apply(null); // never open a wardrobe to handle expiry
		} finally {
			wire.release();
		}
	}
}
