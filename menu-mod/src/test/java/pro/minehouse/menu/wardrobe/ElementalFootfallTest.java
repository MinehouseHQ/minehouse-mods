package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ElementalFootfallTest {
	private static final double EPSILON = 1e-9;
	private static final ElementalFootfalls.Surface FLAT = point -> 64.0;

	@Test
	void spacedFootfallsAlternateAndStayAtTheirWorldPositions() {
		var trail = new ElementalFootfalls<>();
		var world = new Object();
		for (int tick = 0; tick <= 8; tick++) {
			trail.begin(world);
			trail.sample(1, "ember", point(tick * 0.2, 0), true, FLAT);
			trail.end();
		}
		var stamps = trail.stamps();
		assertEquals(2, stamps.size());
		var first = stamps.getFirst();
		var second = stamps.getLast();
		assertEquals("ember", first.kind());
		assertEquals(0.65, first.position().x, EPSILON);
		assertEquals(1.30, second.position().x, EPSILON);
		assertEquals(64.01, first.position().y, EPSILON);
		assertEquals(0.13, first.position().z, EPSILON);
		assertEquals(-0.13, second.position().z, EPSILON);
		assertEquals(90, first.yaw(), EPSILON, "rotate model +Z to match the eastbound travel");

		trail.begin(world);
		trail.sample(1, "ember", point(1.6, 0.2), true, FLAT);
		trail.end();
		assertEquals(first, trail.stamps().getFirst());
		assertEquals(second, trail.stamps().getLast());
	}

	@Test
	void subThresholdJitterDoesNotAccumulateIntoSteps() {
		var trail = new ElementalFootfalls<>();
		var world = new Object();
		for (int tick = 0; tick < 150; tick++) {
			trail.begin(world);
			trail.sample(1, "ember", point(tick % 2 * 0.01, 0), true, FLAT);
			trail.end();
		}
		assertTrue(trail.stamps().isEmpty());
	}

	@Test
	void neitherLandingNorLargeCorrectionsBridgeThePreviousStride() {
		var trail = new ElementalFootfalls<>();
		var world = new Object();
		for (double x : new double[] {0, 0.6, 0.7, 1.1, 1.3, 15, 15.4}) {
			trail.begin(world);
			trail.sample(1, "rime", point(x, 0), x != 0.7, FLAT);
			trail.end();
			assertTrue(trail.stamps().isEmpty(), "unexpected mark at x=" + x);
		}
		trail.begin(world);
		trail.sample(1, "rime", point(15.7, 0), true, FLAT);
		trail.end();
		assertEquals(15.65, trail.stamps().getFirst().position().x, EPSILON);
	}

	@Test
	void onlyTerrainUnderTheOffsetFootCanSupplyTheAnchor() {
		var trail = new ElementalFootfalls<>();
		var world = new Object();
		for (double x : new double[] {0, 0.65, 1.30}) {
			trail.begin(world);
			trail.sample(1, "end", point(x, 0), true, foot -> foot.z > 0 ? Double.NaN : 63.5);
			trail.end();
		}
		assertEquals(1, trail.stamps().size());
		assertEquals(63.51, trail.stamps().getFirst().position().y, EPSILON);
	}

	@Test
	void authoredOnceClipReachesItsLastFrameThenExpiresEvenWhileStanding() {
		var trail = new ElementalFootfalls<>();
		var world = new Object();
		trail.begin(world);
		trail.sample(1, "end", point(0, 0), true, FLAT);
		trail.end();
		trail.begin(world);
		trail.sample(1, "end", point(0.7, 0), true, FLAT);
		trail.end();
		var stamp = trail.stamps().getFirst();
		for (int frame = 0; frame <= 36; frame++) {
			assertEquals(1, trail.stamps().size());
			assertEquals(frame, trail.frame(stamp));
			trail.begin(world);
			trail.sample(1, "end", point(0.7, 0), false, FLAT);
			trail.end();
		}
		assertTrue(trail.stamps().isEmpty());
	}

	@Test
	void worldReplacementDiscardsMarksAndPartialStrides() {
		var trail = new ElementalFootfalls<>();
		var firstWorld = new Object();
		for (double x : new double[] {0, 0.65, 1.25}) {
			trail.begin(firstWorld);
			trail.sample(1, "ember", point(x, 0), true, FLAT);
			trail.end();
		}
		assertEquals(1, trail.stamps().size());

		var secondWorld = new Object();
		trail.begin(secondWorld);
		trail.sample(1, "ember", point(1.35, 0), true, FLAT);
		trail.end();
		assertTrue(trail.stamps().isEmpty());
	}

	@Test
	void changingOneOutfitAndLosingAnotherDoesNotLeakOrEraseOtherWearers() {
		var trail = new ElementalFootfalls<Integer>();
		var world = new Object();
		for (double x : new double[] {0, 0.65}) {
			trail.begin(world);
			trail.sample(1, "ember", point(x, 0), true, FLAT);
			trail.sample(2, "rime", point(x, 1), true, FLAT);
			trail.sample(3, "end", point(x, 2), true, FLAT);
			trail.end();
		}
		assertEquals(3, trail.stamps().size());
		trail.begin(world);
		trail.sample(1, "rime", point(0.9, 0), true, FLAT);
		trail.sample(3, "end", point(0.9, 2), true, FLAT);
		trail.end();
		assertEquals(List.of(3), trail.stamps().stream().map(ElementalFootfalls.Stamp::wearer).toList());
	}

	@Test
	void crowdedTrailsEvictTheOldestMarks() {
		var trail = new ElementalFootfalls<Integer>();
		var world = new Object();
		for (double x : new double[] {0, 0.7}) {
			trail.begin(world);
			for (int id = 0; id < 128; id++) trail.sample(id, "rime", point(x, id), true, FLAT);
			trail.end();
		}
		assertEquals(96, trail.stamps().size());
		assertEquals(32, trail.stamps().getFirst().wearer());
		assertEquals(127, trail.stamps().getLast().wearer());
	}

	@Test
	void replacingAPlayerWithTheSameEntityIdDoesNotReuseTheirStrideOrMarks() {
		// Vanilla Entity.equals compares entity IDs, not the actual instance.
		record Player(int id) {}
		var trail = new ElementalFootfalls<Player>();
		var world = new Object();
		var wearer = new Player(7);
		for (double x : new double[] {0, 0.65, 1.25}) {
			trail.begin(world);
			trail.sample(wearer, "ember", point(x, 0), true, FLAT);
			trail.end();
		}
		assertEquals(1, trail.stamps().size());
		trail.begin(world);
		trail.sample(new Player(7), "ember", point(1.45, 0), true, FLAT);
		trail.end();
		assertTrue(trail.stamps().isEmpty());
	}

	@Test
	void fixedItemOriginIsPinnedToTheStampAndStaysYUpWhenTheCameraMoves() {
		var at = new Vec3(30_000_000.25, 65.01, -14.5);
		for (var camera : List.of(new Vec3(30_000_000, 66.5, -17), new Vec3(30_000_002, 71, -12))) {
			var poses = new PoseStack();
			ElementalTrailRenderer.applyWorldPose(poses, at, camera, 90);
			// Java 26.3's actual FIXED transform centers authored item-space (8,8,8).
			ItemTransform.NO_TRANSFORM.apply(false, poses.last());
			var origin = poses.last().pose().transformPosition(new Vector3f(0.5f));
			assertEquals(at.x - camera.x, origin.x, 1e-5);
			assertEquals(at.y - camera.y, origin.y, 1e-5);
			assertEquals(at.z - camera.z, origin.z, 1e-5);
			var up = poses.last().pose().transformPosition(new Vector3f(0.5f, 1.5f, 0.5f)).sub(origin);
			var forward = poses.last().pose().transformPosition(new Vector3f(0.5f, 0.5f, 1.5f)).sub(origin);
			assertEquals(1, up.y, 1e-5);
			assertEquals(1, forward.x, 1e-5);
		}
	}

	@Test
	void runtimeSelectsTheBakedStepAssetForEveryThemeAndFrame() {
		for (String kind : List.of("ember", "rime", "end")) {
			for (int frame = 0; frame <= ElementalFootfalls.LAST_FRAME; frame++) {
				var id = ElementalTrailRenderer.modelId(kind, frame);
				assertEquals("minehouse-menu:elemental/" + kind + "/step/f" + frame, id.toString());
				assertNotNull(getClass().getResource("/assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json"), id.toString());
			}
		}
	}

	private static Vec3 point(double x, double z) {
		return new Vec3(x, 64, z);
	}
}
