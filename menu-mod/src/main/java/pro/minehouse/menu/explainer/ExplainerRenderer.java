package pro.minehouse.menu.explainer;

import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Draws an event explainer's little 3D stage into a window: players, mobs, blocks and items at
 * their places in the scene (see {@link Explainer}). The camera looks at the stage from the front
 * and a bit above: scene x goes to the right of the screen, z into it, y up.
 */
public final class ExplainerRenderer extends PictureInPictureRenderer<ExplainerRenderer.State> {
	static final int FULL_BRIGHT = 15728880;

	/** What is drawn at a place: an entity, a block (1 x 1 x 1, standing on y), a turning item, or fire. */
	public enum Kind {
		ENTITY, BLOCK, ITEM, FLAME
	}

	/** One thing on the stage. Made anew each frame, so a state is never equal to the last one. */
	public static final class Actor {
		final Kind kind;
		final @Nullable EntityRenderState entity;
		final @Nullable ItemStackRenderState item;
		double x;
		double y;
		double z;
		/** ITEM: yaw, degrees; FLAME: width. */
		float spin;
		/** ITEM: size in blocks; FLAME: height. */
		float size = 1;

		Actor(Kind kind, @Nullable EntityRenderState entity, @Nullable ItemStackRenderState item, double x, double y, double z) {
			this.kind = kind;
			this.entity = entity;
			this.item = item;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	/**
	 * @param lift  how far below the middle of the box the floor is, in blocks
	 * @param yaw   the camera's turn around the stage, degrees (0: straight from the front)
	 * @param pitch how much the camera looks down, degrees
	 */
	public record State(List<Actor> actors, float yaw, float pitch, float lift,
			int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
			implements PictureInPictureRenderState {
		public State(List<Actor> actors, float yaw, float pitch, float lift, int x0, int y0, int x1, int y1, float scale,
				@Nullable ScreenRectangle scissorArea) {
			this(actors, yaw, pitch, lift, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
		}
	}

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	@Override
	protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		// As GuiEntityRenderer: upside down because GUI y goes down. After the turn the camera stands
		// at -z looking at +z, so the screen's right is -x: scene x is flipped below.
		pose.translate(0, state.lift(), 0);
		pose.rotate(Axis.ZP, (float) Math.PI);
		pose.rotateDegrees(Axis.XP, -state.pitch());
		pose.rotateDegrees(Axis.YP, state.yaw());
		CameraRenderState camera = new CameraRenderState();
		camera.orientation = new Quaternionf().rotationYXZ((float) Math.toRadians(state.yaw()), (float) Math.toRadians(-state.pitch()), 0);
		for (Actor a : state.actors()) {
			try {
				switch (a.kind) {
					case ENTITY -> {
						if (a.entity != null) mc.getEntityRenderDispatcher().submit(a.entity, camera, -a.x, a.y, a.z, pose, collector);
					}
					case BLOCK -> {
						if (a.item == null || a.item.isEmpty()) continue;
						pose.pushPose();
						pose.translate(-a.x, a.y + 0.5, a.z);
						a.item.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
						pose.popPose();
					}
					case ITEM -> {
						if (a.item == null || a.item.isEmpty()) continue;
						pose.pushPose();
						pose.translate(-a.x, a.y, a.z);
						pose.rotateDegrees(Axis.YP, a.spin);
						pose.scale(a.size, a.size, a.size);
						a.item.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
						pose.popPose();
					}
					case FLAME -> {
						if (a.entity == null) continue;
						pose.pushPose();
						pose.translate(-a.x, a.y, a.z);
						collector.submitFlame(pose, a.entity, camera.orientation);
						pose.popPose();
					}
				}
			} catch (RuntimeException e) {
				// A model that can't be drawn this way must not take the window down.
				Explainer.warnOnce(a.kind + " " + (a.entity != null ? a.entity.entityType : "item"), e);
			}
		}
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected String getTextureLabel() {
		return "minehouse explainer";
	}
}
