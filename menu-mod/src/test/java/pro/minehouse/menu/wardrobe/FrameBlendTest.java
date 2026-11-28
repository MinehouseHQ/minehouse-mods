package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import pro.minehouse.menu.mixin.ItemStackRenderStateAccess;
import pro.minehouse.menu.mixin.LayerRenderStateAccess;

class FrameBlendTest {
	@ParameterizedTest
	@ValueSource(floats = {1f, 2f})
	void resolveTheActualIdlePoseAfterTheLastTreatFrame(float t) {
		var out = new ItemStackRenderState();
		FrameBlend.resolve(out, new CosmeticMotion.Pick(190, 29, t), ItemStackRenderState::new, (state, frame) -> {
			// As the server holds f29, never bake the old sculk-bearing f190 into the output.
			assertEquals(29, frame);
			assertSame(out, state);
			state.newLayer();
		});
		assertFalse(out.isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"0.49, 190", "0.5, 29", "0.99, 29", "1.0, 29"})
	void switchWholePoseWhenQuadCountsDiffer(float t, int expectedFrame) {
		var out = new State();
		var treatBody = quads(0);
		var idleBody = quads(4);
		FrameBlend.resolve(out, new CosmeticMotion.Pick(190, 29, t), State::new, (state, frame) -> {
			state.clear();
			state.newLayer().setQuads(frame == 190 ? treatBody : idleBody);
			state.newLayer().setQuads(frame == 190 ? quads(8) : ItemQuads.EMPTY);
		});
		assertSame(expectedFrame == 190 ? treatBody : idleBody, out.layers.get(0).minehouse$quads());
		assertEquals(expectedFrame == 190 ? 1 : 0, out.layers.get(1).minehouse$quads().all().size());
	}

	@Test
	void switchWholePoseWhenLayerCountsDiffer() {
		var out = new State();
		FrameBlend.resolve(out, new CosmeticMotion.Pick(190, 29, .5f), State::new, (state, frame) -> {
			state.clear();
			state.newLayer().setQuads(quads(4));
			if (frame == 190) {
				state.newLayer().setQuads(quads(8));
			}
		});
		assertEquals(1, out.layers.size());
	}

	@Test
	void interpolateMatchingQuadsWithoutChangingTheBakedFrames() {
		var out = new State();
		var start = quads(0);
		var end = quads(4);
		FrameBlend.resolve(out, new CosmeticMotion.Pick(28, 29, .75f), State::new, (state, frame) ->
				state.newLayer().setQuads(frame == 28 ? start : end));
		assertEquals(3f, out.layers.getFirst().minehouse$quads().all().getFirst().position0().x());
		assertEquals(0f, start.all().getFirst().position0().x());
		assertEquals(4f, end.all().getFirst().position0().x());
	}

	private static ItemQuads quads(float x) {
		var p = new Vector3f(x, 0, 0);
		var material = new BakedQuad.MaterialInfo(null, null,
				RenderTypes.itemCutout(Identifier.withDefaultNamespace("test")), null, null, -1, null, 0);
		return ItemQuads.split(List.of(new BakedQuad(p, p, p, p, 0L, 0L, 0L, 0L, Direction.NORTH, material)));
	}

	// Plain JUnit doesn't install the Fabric mixin accessors.
	private static class State extends ItemStackRenderState implements ItemStackRenderStateAccess {
		final List<Layer> layers = new ArrayList<>();

		@Override public Layer newLayer() {
			var layer = new Layer(this);
			layers.add(layer);
			return layer;
		}

		@Override public void clear() { layers.clear(); }
		@Override public int minehouse$activeLayerCount() { return layers.size(); }
		@Override public Layer[] minehouse$layers() { return layers.toArray(Layer[]::new); }
		@Override public void minehouse$displayContext(net.minecraft.world.item.ItemDisplayContext context) {
			throw new UnsupportedOperationException("FrameBlend does not set the display context directly");
		}
	}

	private static class Layer extends ItemStackRenderState.LayerRenderState implements LayerRenderStateAccess {
		private ItemQuads quads;

		Layer(State owner) { owner.super(); }
		@Override public void setQuads(ItemQuads quads) { this.quads = quads; }
		@Override public ItemQuads minehouse$quads() { return quads; }
	}

	@Test
	void waitForTheMountChangeRatherThanBlendIntoAPoseOnAnotherBone() {
		for (int f : new int[]{286, 651, 975, 1370}) {
			var pick = new CosmeticMotion.Pick(f, f + 1, 1f); // e.g. late equipment/display packet
			assertEquals(CosmeticMotion.Pick.of(f), pick.forMount("karat"));
			assertSame(pick, pick.forMount("ordinary_hat"));
		}
	}

	@Test
	void blendNormallyOnTheSamePerchIncludingTheLoopSeam() {
		for (int f : new int[]{0, 287, 652, 976, 1377}) {
			var pick = new CosmeticMotion.Pick(f, (f + 1) % 1378, .6f);
			assertSame(pick, pick.forMount("karat"));
		}
	}
}
