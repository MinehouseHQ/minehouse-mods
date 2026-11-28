package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class FirstPersonElementalsTest {
	@Test
	void onlyTheServerEquippedBeltSelectsAnEffect() {
		var preview = WardrobeNet.parseState(JsonParser.parseString("""
				{"coins":0,"store":"","items":[{"id":"elemental_ember","slot":"belt","name":"Ember","price":0,"owned":true}],
				 "worn":{"hat":"elemental_end"},"preview":{"belt":"elemental_ember"}}
				""").getAsJsonObject());
		assertNull(FirstPersonElementals.equipped(null));
		assertNull(FirstPersonElementals.equipped(preview));
		assertNull(FirstPersonElementals.equipped(new WardrobeNet.State(0, "", List.of(),
				Map.of("belt", "elemental_ember_extra"), ArmorVisibility.UNAVAILABLE)));
		assertEquals("ember", FirstPersonElementals.equipped(state("elemental_ember")));
		assertEquals("rime", FirstPersonElementals.equipped(state("elemental_rime")));
		assertEquals("end", FirstPersonElementals.equipped(state("elemental_end")));
	}

	@Test
	void framesLoopEvery96GameTicksAndSelectThePhysicalArmAndWidth() {
		assertEquals("minehouse-menu:elemental/ember/right_arm/f0",
				FirstPersonElementals.modelId("ember", HumanoidArm.RIGHT, PlayerModelType.SLIM, 0).toString());
		assertEquals("minehouse-menu:elemental/rime/left_arm_wide/f95",
				FirstPersonElementals.modelId("rime", HumanoidArm.LEFT, PlayerModelType.WIDE, 95).toString());
		assertEquals("minehouse-menu:elemental/end/left_arm/f0",
				FirstPersonElementals.modelId("end", HumanoidArm.LEFT, PlayerModelType.SLIM, 96).toString());
		assertEquals(FirstPersonElementals.modelId("end", HumanoidArm.RIGHT, PlayerModelType.WIDE, 95),
				FirstPersonElementals.modelId("end", HumanoidArm.RIGHT, PlayerModelType.WIDE, -1));
	}

	@Test
	void reflectedWindingKeepsOutwardNormalsAndKeepsUvsAttachedToVertices() {
		var a = new Vector3f(0, 0, 0);
		var b = new Vector3f(1, 0, 0);
		var c = new Vector3f(1, 1, 0);
		var d = new Vector3f(0, 1, 0);
		var q = new BakedQuad(a, b, c, d, 11L, 22L, 33L, 44L, Direction.SOUTH,
				new BakedQuad.MaterialInfo(null, null, null, null, null, -1, null, 0));
		var reversed = FirstPersonElementals.reverseWinding(q);
		assertEquals(List.of(a, d, c, b), List.of(reversed.position0(), reversed.position1(), reversed.position2(), reversed.position3()));
		assertEquals(List.of(11L, 44L, 33L, 22L), List.of(reversed.packedUV0(), reversed.packedUV1(), reversed.packedUV2(), reversed.packedUV3()));
		assertSame(q.materialInfo(), reversed.materialInfo());
		assertSame(Direction.SOUTH, reversed.direction());
		var pose = new PoseStack();
		FirstPersonElementals.applyArmPose(pose, PartPose.offset(5, 2, 0));
		assertEquals(new Vector3f(5f / 16, 2f / 16, 0), pose.last().pose().transformPosition(new Vector3f()));
		assertEquals(new Vector3f(5f / 16 + 1, 2f / 16 - 1, 0), pose.last().pose().transformPosition(new Vector3f(1, 1, 0)));
		var p0 = pose.last().pose().transformPosition(reversed.position0(), new Vector3f());
		var p1 = pose.last().pose().transformPosition(reversed.position1(), new Vector3f());
		var p2 = pose.last().pose().transformPosition(reversed.position2(), new Vector3f());
		assertTrue(p1.sub(p0).cross(p2.sub(p0)).dot(pose.last().normal().transform(0, 0, 1, new Vector3f())) > 0);
	}

	private static WardrobeNet.State state(String id) {
		return new WardrobeNet.State(0, "", List.of(), Map.of("belt", id), ArmorVisibility.UNAVAILABLE);
	}
}
