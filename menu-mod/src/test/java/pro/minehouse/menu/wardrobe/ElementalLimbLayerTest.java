package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ElementalLimbLayerTest {
	@Test
	void bindsEveryItemToItsOwnPosedLimb() {
		for (boolean slim : new boolean[] {true, false}) {
			PlayerModel model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
			AvatarRenderState state = new AvatarRenderState();
			state.pose = Pose.CROUCHING;
			state.isCrouching = true;
			state.walkAnimationPos = 2.1f;
			state.walkAnimationSpeed = 0.6f;
			model.setupAnim(state);
			model.root().yRot = 0.27f;
			model.body.zRot = 0.41f; // limbs are siblings of the body, not its children
			var parts = new net.minecraft.client.model.geom.ModelPart[] { model.rightArm, model.leftArm, model.rightLeg, model.leftLeg };
			var names = new String[] { "right_arm", "left_arm", "right_leg", "left_leg" };
			var limbs = ElementalLimbLayer.Limb.values();
			for (int i = 0; i < limbs.length; i++) {
				var limb = limbs[i];
				assertSame(parts[i], limb.part(model));
				String suffix = !slim && i < 2 ? "_wide" : "";
				assertEquals(Identifier.parse("minehouse-menu:elemental/ember/" + names[i] + suffix + "/f95"),
						limb.itemId("ember", slim, 95));
				PoseStack expected = new PoseStack();
				model.root().translateAndRotate(expected);
				parts[i].translateAndRotate(expected);
				PoseStack actual = new PoseStack();
				ElementalLimbLayer.limbFrame(model, parts[i], actual);
				// Center, outward/front directions and distal point in item-local blocks.
				for (var v : new Vector3f[] {new Vector3f(), new Vector3f(2 / 16f, 0, -2 / 16f), new Vector3f(0, -10 / 16f, 0)}) {
					var a = actual.last().pose().transformPosition(new Vector3f(v));
					var b = expected.last().pose().transformPosition(v.x, -v.y, v.z, new Vector3f());
					assertTrue(a.equals(b, 1e-6f), limb + ": " + a + " != " + b);
				}
			}
		}
	}

	@Test
	void resolvesAllFourPartsOrDrawsNothing() {
		var ids = new java.util.ArrayList<Identifier>();
		var partModels = ElementalLimbLayer.resolve("rime", false, 23, id -> {
			ids.add(id);
			var out = new ItemStackRenderState();
			out.newLayer();
			return out;
		});
		assertEquals(java.util.List.of("right_arm_wide", "left_arm_wide", "right_leg", "left_leg").stream()
				.map(part -> Identifier.parse("minehouse-menu:elemental/rime/" + part + "/f23")).toList(), ids);
		assertEquals(4, partModels.size());
		assertNull(ElementalLimbLayer.resolve("end", true, 20, id -> {
			var out = new ItemStackRenderState();
			if (!id.getPath().contains("left_leg")) out.newLayer();
			return out;
		}));
		assertNull(ElementalLimbLayer.resolve("end", true, 96, id -> fail("no such frame")));
	}
}
