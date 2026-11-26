package pro.minehouse.menu.wardrobe;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ShoulderPoseTest {
	// JUnit doesn't install Fabric's render-state mixin. Supply just the two layer inputs.
	private static class State extends AvatarRenderState {
		final ItemProbe shoulder = new ItemProbe(), pack = new ItemProbe();

		@Override @SuppressWarnings("unchecked")
		public <T> T getData(RenderStateDataKey<T> key) {
			return (T) (key == WardrobeSelf.SHOULDER ? shoulder : key == WardrobeSelf.BACKPACK ? pack : null);
		}
	}

	private static class ItemProbe extends ItemStackRenderState {
		Matrix4f submitted;
		@Override public boolean isEmpty() { return false; }
		@Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline) {
			submitted = new Matrix4f(pose.last().pose());
		}
	}

	@Test
	void shoulderFollowsTorsoNotHeadAndLeavesBackpackOnItsPivot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var mesh = PlayerModel.createMesh(CubeDeformation.NONE, true);
		var player = new PlayerModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), true);
		var layer = new WardrobeSelf.BackpackLayer(() -> player);
		var state = new State();
		var pose = new PoseStack();
		layer.submit(pose, null, 0, state, 0, 0);
		Matrix4f rest = state.shoulder.submitted;
		assertNotNull(rest);
		assertEquals(rest, state.pack.submitted);

		// Looking almost straight up while turning is particularly visible from above.
		for (int[] look : new int[][] {{45, 0}, {0, -30}, {0, -80}, {45, -80}, {-45, -80}, {45, 65}}) {
			player.head.yRot = (float) Math.toRadians(look[0]);
			player.head.xRot = (float) Math.toRadians(look[1]);
			state.shoulder.submitted = state.pack.submitted = null;
			layer.submit(pose, null, 0, state, 0, 0);
			assertEquals(rest, state.shoulder.submitted, "shoulder moved at yaw/pitch " + look[0] + "/" + look[1]);
			assertEquals(rest, state.pack.submitted, "independent backpack lost its body mount");
		}
		player.body.xRot = .4f;
		layer.submit(pose, null, 0, state, 0, 0);
		assertNotEquals(rest, state.shoulder.submitted);
		assertEquals(state.pack.submitted, state.shoulder.submitted);
		assertEquals(new Matrix4f(), pose.last().pose());
	}
}
