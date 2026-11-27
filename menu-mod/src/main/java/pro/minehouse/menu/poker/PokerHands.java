package pro.minehouse.menu.poker;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Your two cards held in your hands, in 3D, in front of the first-person view of the poker table:
 * the table's own card models (from the server pack) fanned out between your two arms (your skin).
 * The fan opens wider while the mouse is over it, and the hands sway a little after the mouse.
 * Drawn as a picture in the table screen ({@link PokerScreen}), so only you see your cards.
 */
public final class PokerHands extends PictureInPictureRenderer<PokerHands.State> {
	private static final int FULL_BRIGHT = 15728880;
	private static final Map<String, ItemStackRenderState> CARDS = new HashMap<>();
	private static final Map<Boolean, ModelPart[]> ARMS = new HashMap<>();

	/**
	 * @param cards  your two cards as the screen has them ("as", "td", "??" for a back), null when you hold none
	 * @param fan    how far the cards are spread, 0 closed … 1 wide
	 * @param swayX  where the mouse is across the screen, -1 … 1
	 * @param swayY  where the mouse is down the screen, -1 … 1
	 * @param lift   0 resting … 1 raised to look at closely
	 */
	public record State(String @Nullable [] cards, float fan, float swayX, float swayY, float lift, @Nullable PlayerSkin skin,
			int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
			implements PictureInPictureRenderState {
		public State(String @Nullable [] cards, float fan, float swayX, float swayY, float lift, @Nullable PlayerSkin skin,
				int x0, int y0, int x1, int y1, float scale) {
			this(cards, fan, swayX, swayY, lift, skin, x0, y0, x1, y1, scale, null, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
		}
	}

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	@Override
	protected String getTextureLabel() {
		return "minehouse poker hands";
	}

	@Override
	protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		// As ExplainerRenderer: turned half round about z, so y is up and the camera looks along +z
		// from -z; screen right is -x.
		pose.rotate(Axis.ZP, (float) Math.PI);
		// The hands sit low in the picture and lean back a little, swaying after the mouse.
		pose.translate(0, 0.24 + state.lift() * 0.06, 0);
		pose.rotateDegrees(Axis.XP, -18 + state.swayY() * 6 + state.lift() * 10);
		pose.rotateDegrees(Axis.YP, state.swayX() * 9);
		PoseStack.Pose base = pose.last().copy();

		String[] cards = state.cards();
		if (cards != null) {
			float spread = 7 + state.fan() * 13;
			for (int c = 0; c < cards.length; c++) {
				ItemStackRenderState item = card(mc, cards[c]);
				if (item == null || item.isEmpty()) continue;
				PoseStack own = new PoseStack();
				own.last().set(base);
				float side = c == 0 ? -1 : 1;
				// Held at the bottom: each card turns about the point where the thumbs press.
				own.translate(0, -0.12, -0.02 * c);
				own.rotateDegrees(Axis.ZP, side * spread);
				own.translate(side * -0.01 * state.fan(), 0.14, 0);
				own.scale(0.3F, 0.3F, 0.3F);
				// The model lies flat (face up, +y): stand it up facing the camera (the item renderer
				// itself puts the model's middle on the origin).
				own.rotateDegrees(Axis.XP, -90);
				// The face's top edge lies towards -z in the model: turn it in its own plane so the
				// card reads upright, not upside down.
				own.rotateDegrees(Axis.YP, 180);
				item.submit(own, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			}
		}

		PlayerSkin skin = state.skin();
		if (skin == null) return;
		boolean slim = skin.model() == PlayerModelType.SLIM;
		ModelPart[] arms = arms(mc, slim);
		Identifier texture = skin.body().texturePath();
		for (int a = 0; a < 2; a++) {
			PoseStack own = new PoseStack();
			own.last().set(base);
			float side = a == 0 ? 1 : -1; // 0: right arm, on the screen's right (-x)
			// The hand at the cards' lower corner, a little in front of them; the arm runs down and out
			// past the bottom of the picture.
			own.translate(-side * 0.085, -0.13, -0.04);
			own.rotateDegrees(Axis.ZP, -side * 38);
			own.rotateDegrees(Axis.XP, 25);
			// Model space (sixteenths of a block, the part scales itself): the arm hangs from its shoulder
			// along +y, here up (the picture is turned over), so move it down by its length to put the
			// hand's end on the origin.
			own.translate(0, -10F / 16, 0);
			collector.submitModelPart(arms[a], own, RenderTypes.entityTranslucent(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, -1, 0);
		}
	}

	/** The card's model from the server pack: «as» is minehouse-bank:poker/ace_spades, «??» the back. */
	private static @Nullable ItemStackRenderState card(Minecraft mc, String code) {
		if (!Items.PAPER.builtInRegistryHolder().areComponentsBound()) return null;
		return CARDS.computeIfAbsent(code, k -> {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("minehouse-bank", "poker/" + name(k)));
			ItemStackRenderState out = new ItemStackRenderState();
			mc.getItemModelResolver().updateForTopItem(out, stack, ItemDisplayContext.NONE, mc.level, null, 0);
			return out;
		});
	}

	static String name(String code) {
		if (code == null || code.length() != 2 || code.contains("?")) return "back";
		String c = code.toLowerCase(Locale.ROOT);
		String rank = switch (c.charAt(0)) {
			case 'a' -> "ace";
			case 'k' -> "king";
			case 'q' -> "queen";
			case 'j' -> "jack";
			case 't' -> "10";
			default -> String.valueOf(c.charAt(0));
		};
		String suit = switch (c.charAt(1)) {
			case 's' -> "spades";
			case 'h' -> "hearts";
			case 'd' -> "diamonds";
			default -> "clubs";
		};
		return rank + "_" + suit;
	}

	/** The two arms (right, left) of a player model of this build, each a part of its own at the origin. */
	private static ModelPart[] arms(Minecraft mc, boolean slim) {
		return ARMS.computeIfAbsent(slim, k -> {
			ModelPart root = mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
			ModelPart[] out = { root.getChild("right_arm"), root.getChild("left_arm") };
			for (ModelPart p : out) {
				p.resetPose();
				p.x = p.y = p.z = 0;
				p.xRot = p.yRot = p.zRot = 0;
			}
			return out;
		});
	}

	/** After a resource reload the cached card models are stale. */
	static void forget() {
		CARDS.clear();
	}
}
