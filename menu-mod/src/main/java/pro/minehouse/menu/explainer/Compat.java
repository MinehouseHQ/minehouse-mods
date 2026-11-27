package pro.minehouse.menu.explainer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;

/**
 * Makes the explainer's made-up render states drawable with other client mods around.
 *
 * <p>Player Animation Library (Emotecraft, Bendable Cuboids) fills its animation manager on a
 * player's render state when the state is extracted from a real player; the explainer builds its
 * states by hand, and their mixins in the hand layer then throw on the missing manager. An empty
 * manager is never active, so the player is drawn as is.
 *
 * <p>When a model still throws, it is drawn without what is in its hands from then on, and if
 * that throws too, not at all: an exception every frame builds a crash report every frame.
 */
final class Compat {
	private static final Map<EntityType<?>, Integer> BROKEN = new HashMap<>();
	private static boolean looked;
	private static Method setManager;
	private static Method getManager;
	private static Constructor<?> manager;

	private Compat() {
	}

	static boolean skip(EntityRenderState s) {
		return BROKEN.getOrDefault(s.entityType, 0) >= 2;
	}

	static void broke(EntityRenderState s) {
		BROKEN.merge(s.entityType, 1, Integer::sum);
	}

	static void prepare(EntityRenderState s) {
		if (s instanceof AvatarRenderState p) animManager(p);
		if (BROKEN.getOrDefault(s.entityType, 0) == 1 && s instanceof ArmedEntityRenderState a) {
			a.rightHandItemStack = ItemStack.EMPTY;
			a.leftHandItemStack = ItemStack.EMPTY;
			a.rightHandItemState.clear();
			a.leftHandItemState.clear();
			a.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			a.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		}
	}

	private static void animManager(AvatarRenderState s) {
		if (!looked) {
			looked = true;
			try {
				Class<?> state = Class.forName("com.zigythebird.playeranim.accessors.IAvatarAnimationState");
				Class<?> type = Class.forName("com.zigythebird.playeranim.animation.AvatarAnimManager");
				getManager = state.getMethod("playerAnimLib$getAnimManager");
				setManager = state.getMethod("playerAnimLib$setAnimManager", type);
				manager = type.getConstructor(Avatar.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				setManager = null; // not installed, or another version: the fallback above still holds
			}
		}
		if (setManager == null || !setManager.getDeclaringClass().isInstance(s)) return;
		try {
			if (getManager.invoke(s) == null) setManager.invoke(s, manager.newInstance((Object) null));
		} catch (ReflectiveOperationException | RuntimeException e) {
			setManager = null;
			Explainer.warnOnce("player animation library", new IllegalStateException(e));
		}
	}
}
