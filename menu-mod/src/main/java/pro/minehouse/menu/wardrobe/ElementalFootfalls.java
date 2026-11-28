package pro.minehouse.menu.wardrobe;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.phys.Vec3;

/** Tick-owned gait state. Stamps have no attachment to the player or camera once planted. */
final class ElementalFootfalls<T> {
	static final int LAST_FRAME = 36; // Inclusive endpoint of the baked once clip, at 20 fps.
	static final int MAX_STAMPS = 96;
	private static final double STRIDE = 0.65;
	private static final double HALF_STANCE = 0.13;
	private static final double GROUND_LIFT = 0.01;
	private static final double MAX_HORIZONTAL = 0.75;
	private static final double MAX_VERTICAL = 0.65;
	private static final double MIN_TRAVEL = 0.015;

	@FunctionalInterface
	interface Surface {
		/** Height of solid ground under the proposed foot, or NaN if there is none. */
		double height(Vec3 point);
	}

	record Stamp<T>(T wearer, String kind, Vec3 position, float yaw, long born) {
	}

	private static final class Gait {
		String kind;
		Vec3 last;
		double distance;
		boolean right;
		long seen;
	}

	// Entity IDs can be reused; steps belong to the tracked instance, not Entity.equals().
	private final Map<T, Gait> gaits = new IdentityHashMap<>();
	private final ArrayDeque<Stamp<T>> stamps = new ArrayDeque<>();
	private Object world;
	private long tick;

	void begin(Object world) {
		if (this.world != world) {
			clear();
			this.world = world;
		}
		tick++;
		while (!stamps.isEmpty() && tick - stamps.getFirst().born() > LAST_FRAME) stamps.removeFirst();
	}

	void clear() {
		world = null;
		tick = 0;
		gaits.clear();
		stamps.clear();
	}

	void sample(T wearer, String kind, Vec3 position, boolean groundedTravel, Surface surface) {
		Gait gait = gaits.computeIfAbsent(wearer, ignored -> new Gait());
		gait.seen = tick;
		if (!kind.equals(gait.kind)) {
			gait.kind = kind;
			gait.last = null;
			gait.distance = 0;
			gait.right = false;
		}

		Vec3 from = gait.last;
		gait.last = groundedTravel ? position : null;
		if (from == null || !groundedTravel) {
			gait.distance = 0;
			return;
		}

		double dx = position.x - from.x;
		double dz = position.z - from.z;
		double length = Math.hypot(dx, dz);
		if (length > MAX_HORIZONTAL || Math.abs(position.y - from.y) > MAX_VERTICAL) {
			gait.distance = 0;
			return;
		}
		if (length < MIN_TRAVEL) return;

		double toStep = STRIDE - gait.distance;
		if (length < toStep) {
			gait.distance += length;
			return;
		}
		// At most one mark per player tick. Catch-up movement can't emit a burst of steps.
		gait.distance = Math.min(length - toStep, STRIDE * 0.5);
		double side = gait.right ? -HALF_STANCE : HALF_STANCE;
		gait.right = !gait.right;
		Vec3 foot = from.lerp(position, toStep / length).add(-dz / length * side, 0, dx / length * side);
		double y = surface.height(foot);
		if (!Double.isFinite(y)) return;
		float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
		if (stamps.size() == MAX_STAMPS) stamps.removeFirst();
		stamps.addLast(new Stamp<>(wearer, kind, new Vec3(foot.x, y + GROUND_LIFT, foot.z), yaw, tick));
	}

	void end() {
		gaits.values().removeIf(gait -> gait.seen != tick);
		stamps.removeIf(stamp -> {
			Gait gait = gaits.get(stamp.wearer());
			return gait == null || !stamp.kind().equals(gait.kind);
		});
	}

	List<Stamp<T>> stamps() {
		return List.copyOf(stamps);
	}

	int frame(Stamp<T> stamp) {
		return (int) (tick - stamp.born());
	}
}
