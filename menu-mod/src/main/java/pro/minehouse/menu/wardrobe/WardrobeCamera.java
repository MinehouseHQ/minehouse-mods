package pro.minehouse.menu.wardrobe;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The wardrobe's camera: in front of the player, a little above, looking back at them, so you see
 * yourself in the world (the fitting room) instead of a picture in the window. Pulled in when a
 * wall is in the way. While the pet is looked at ({@link Fitting#petFocus()}) it flies over to the
 * fitting pet beside you, and back again when you leave its tab, eased in and out.
 */
public final class WardrobeCamera {
	private static final double DISTANCE = 2.8;
	private static final double LOOK_AT = 1.0;
	private static final double HEIGHT = 1.35;
	/** Seconds for the flight from you to the pet (or back). */
	static final double FLIGHT = 0.7;

	public record Shot(Vec3 position, float yRot, float xRot) {
	}

	/** Where the camera stands and the point it looks at. */
	record View(Vec3 position, Vec3 target) {
	}

	/** 0 = on you, 1 = on the pet; moves linearly in time, eased when drawn. */
	private static double flight;
	private static long lastNanos;
	private static Object lastScreen;
	private static View lastPet;

	private WardrobeCamera() {
	}

	public static Shot shot(Entity entity, Level level, float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.gui.screen() instanceof Fitting fitting) || !fitting.fittingScene() || entity == null || entity != mc.player || level == null) {
			lastScreen = null;
			return null;
		}
		long now = System.nanoTime();
		if (lastScreen != fitting) {
			// A fresh open starts on you; the wardrobe itself still cuts in from first person.
			lastScreen = fitting;
			flight = 0;
			lastPet = null;
			lastNanos = now;
		}
		double dt = Math.min(0.1, Math.max(0, (now - lastNanos) / 1e9));
		lastNanos = now;

		float yaw = entity.getYRot();
		double rad = Math.toRadians(yaw);
		Vec3 front = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
		Vec3 feet = entity.getPosition(partialTicks);
		View self = view(level, entity, feet.add(0, HEIGHT, 0), feet.add(0, LOOK_AT, 0), front, DISTANCE);

		View pet = fitting.petFocus() ? petView(level, entity, front, partialTicks) : null;
		if (pet != null) lastPet = pet;
		double goal = pet != null ? 1 : 0;
		double step = dt / FLIGHT;
		flight = goal > flight ? Math.min(goal, flight + step) : Math.max(goal, flight - step);
		if (flight > 0 && lastPet == null) flight = 0; // never had a pet to fly to

		View v = flight <= 0 ? self : blend(self, lastPet, ease(flight));
		Vec3 look = v.target().subtract(v.position());
		float yRot = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float xRot = (float) Math.toDegrees(Math.atan2(-look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
		return new Shot(v.position(), yRot, xRot);
	}

	/** Smooth start and stop (ease in-out cubic), 0..1 → 0..1. */
	static double ease(double t) {
		t = Math.max(0, Math.min(1, t));
		return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
	}

	/**
	 * Between the two views. The camera travels on a slight arc (lifted at mid-flight), and the look
	 * point slides from you to the pet, so the turn and the move happen together.
	 */
	static View blend(View from, View to, double t) {
		Vec3 pos = from.position().lerp(to.position(), t).add(0, Math.sin(Math.PI * t) * .25, 0);
		return new View(pos, from.target().lerp(to.target(), t));
	}

	/** A camera at {@code distance} along {@code front} from {@code eye}, pulled in before walls. */
	private static View view(Level level, Entity entity, Vec3 eye, Vec3 target, Vec3 front, double distance) {
		Vec3 wanted = eye.add(front.scale(distance));
		HitResult hit = level.clip(new ClipContext(eye, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
		Vec3 pos = wanted;
		if (hit.getType() != HitResult.Type.MISS) {
			double d = Math.max(0.6, hit.getLocation().distanceTo(eye) - 0.25);
			pos = eye.add(front.scale(d));
		}
		return new View(pos, target);
	}

	/** Where the pet's dot goes: just over the fitting pet, or null while there is none. */
	public static Vec3 petPoint(float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return null;
		Display.ItemDisplay pet = fittingPet(mc.level, mc.player);
		if (pet == null) return null;
		double[] hs = heightAndSize(pet, partialTicks);
		return pet.getPosition(partialTicks).add(0, hs[0] + 0.3 * hs[1], 0);
	}

	/** Whether this display is your fitting pet while the fitting room is open: dragging turns it. */
	public static float petTurn(Display.ItemDisplay display) {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.gui.screen() instanceof Fitting f) || !f.fittingScene() || display.getVehicle() != null) return 0;
		return isFittingPet(WardrobeSelf.passengerStack(display)) ? f.petYaw() : 0;
	}

	private static Display.ItemDisplay fittingPet(Level level, Entity entity) {
		Display.ItemDisplay best = null;
		double bestD = Double.MAX_VALUE;
		for (Display.ItemDisplay d : level.getEntitiesOfClass(Display.ItemDisplay.class, entity.getBoundingBox().inflate(3))) {
			if (d.getVehicle() != null || !isFittingPet(WardrobeSelf.passengerStack(d))) continue;
			double dist = d.distanceToSqr(entity);
			if (dist < bestD) {
				bestD = dist;
				best = d;
			}
		}
		return best;
	}

	/** The point to look at over the display's position and the model's size (from its transformation). */
	private static double[] heightAndSize(Display.ItemDisplay best, float partialTicks) {
		double up = 0.45;
		double size = 1;
		var state = best.renderState();
		if (state != null) {
			var t = state.transformation().get(partialTicks);
			org.joml.Matrix4f m = t.getMatrixCopy();
			Vector3f tr = m.getTranslation(new Vector3f());
			Vector3f sc = m.getScale(new Vector3f());
			size = Math.max(0.3, Math.max(sc.x(), sc.y()));
			// The FIXED model is a unit cube around the translation; aim a bit below its centre.
			up = Math.max(0.2, Math.min(1.6, tr.y() - 0.1 * size));
		}
		return new double[] {up, size};
	}

	/** The fitting pet beside you (custom_model_data string 2 "fitting", only yours), or null. */
	private static View petView(Level level, Entity entity, Vec3 front, float partialTicks) {
		Display.ItemDisplay best = fittingPet(level, entity);
		if (best == null) return null;
		double[] hs = heightAndSize(best, partialTicks);
		double size = hs[1];
		Vec3 target = best.getPosition(partialTicks).add(0, hs[0], 0);
		// In front of the pet, the same way as you face, a little higher than its head; bigger pets further.
		double distance = Math.max(1.3, Math.min(3.0, 0.9 + 1.0 * size));
		return view(level, entity, target.add(0, 0.35 + 0.2 * size, 0), target, front, distance);
	}

	private static boolean isFittingPet(ItemStack stack) {
		if (stack.isEmpty()) return false;
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return cmd != null && "fitting".equals(cmd.getString(2));
	}
}
