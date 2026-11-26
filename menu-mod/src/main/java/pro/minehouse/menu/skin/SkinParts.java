package pro.minehouse.menu.skin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix4f;

/**
 * The player body MineHouse Events builds out of item displays (rigs with {@code skin_*} bones,
 * the rift death cutscene): one player head per body part, which a core shader
 * (stable_player_display) turns into that part of the skin. Shader packs (Iris) replace the core
 * shaders, and then the parts come out as garbled heads. With this mod the parts are drawn here as
 * the real player model's parts instead, which works with or without a shader pack.
 *
 * <p>Must match events-mod Puppeteer: part n is moved down by 1024·n blocks (outermost, in the
 * display's transformation), and the display's own frame sits on the pivot the shader hangs the
 * part from: the neck for head and torso, the top middle of arms and legs.
 */
public final class SkinParts {
	private static final String NAMESPACE = "minehouse-events";
	private static final String PREFIX = "player/";
	private static final List<String> PARTS = List.of("head", "right_arm", "left_arm", "torso", "right_leg", "left_leg");
	private static final float SPACING = 1024f;
	/** Draws ours next to the shader's for lining them up: -Dminehouse.skinParts=debug */
	private static final boolean DEBUG = "debug".equals(System.getProperty("minehouse.skinParts"));
	private static final boolean OFF = "off".equals(System.getProperty("minehouse.skinParts"));

	public static boolean debug() {
		return DEBUG;
	}

	private static final Map<Key, ModelPart> MODELS = new HashMap<>();

	private record Key(int part, boolean slim) {
	}

	private SkinParts() {
	}

	/** Which body part an item display's item is, or -1. */
	public static int partOf(ItemStack stack) {
		if (OFF || stack.isEmpty()) {
			return -1;
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model == null || !NAMESPACE.equals(model.getNamespace()) || !model.getPath().startsWith(PREFIX)) {
			return -1;
		}
		return PARTS.indexOf(model.getPath().substring(PREFIX.length()));
	}

	/**
	 * Draws the part. {@code pose} is the display's (entity position, orientation and
	 * transformation applied).
	 */
	public static void submit(int part, ResolvableProfile profile, Transformation transformation, PoseStack pose,
			SubmitNodeCollector nodes, int light, int outline) {
		PlayerSkinRenderCache.RenderInfo info = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile);
		boolean slim = info.playerSkin().model() == PlayerModelType.SLIM;
		ModelPart model = model(part, slim);
		if (model == null) {
			return;
		}
		pose.pushPose();
		// Undo the 1024·n blocks down (it is there for the shader only).
		Matrix4f t = transformation.getMatrixCopy();
		Matrix4f back = new Matrix4f(t).invert();
		Matrix4f moved = new Matrix4f(t);
		moved.m31(t.m31() + SPACING * part);
		pose.mulPose(back.mul(moved));
		if (DEBUG) {
			pose.translate(1.25f, 0, 0);
		}
		// Model space: y down, the front of the body is -z.
		pose.scale(-1, -1, 1);
		Identifier texture = info.playerSkin().body().texturePath();
		nodes.submitModelPart(model, pose, RenderTypes.entityTranslucent(texture), light, OverlayTexture.NO_OVERLAY, null, -1, outline);
		pose.popPose();
	}

	/** A part of its own (its pose is read when it is drawn, later), with its pivot moved to the shader's. */
	private static ModelPart model(int part, boolean slim) {
		return MODELS.computeIfAbsent(new Key(part, slim), k -> {
			ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
			String name = switch (part) {
				case 0 -> "head";
				case 1 -> "right_arm";
				case 2 -> "left_arm";
				case 3 -> "body";
				case 4 -> "right_leg";
				default -> "left_leg";
			};
			ModelPart p = root.getChild(name);
			p.resetPose();
			p.xRot = p.yRot = p.zRot = 0;
			// Where the part's own origin goes, so that the shader's pivot lands on the display's.
			float armMiddle = slim ? 0.5f : 1f;
			switch (part) {
				case 1 -> { p.x = armMiddle; p.y = 2; p.z = 0; }
				case 2 -> { p.x = -armMiddle; p.y = 2; p.z = 0; }
				default -> { p.x = 0; p.y = 0; p.z = 0; }
			}
			return p;
		});
	}
}
