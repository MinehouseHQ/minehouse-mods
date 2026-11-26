package pro.minehouse.menu.wardrobe;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;

/**
 * What you are doing, for your own cosmetics that animate by event (Cosmetics 0.12.0+): the same
 * rules as the server's {@code Motion} in cosmetics-mod (keep the two in step), from the local
 * player, so what the client mod draws on you reacts at once. Other players' cosmetics come with
 * the frame the server picked.
 *
 * <p>{@link #pick} gives a position between two frames, which {@link FrameBlend} draws blended, so
 * the animation runs at the render frame rate instead of in steps.
 */
public final class CosmeticMotion {
	static final List<String> ONE_SHOTS = List.of("hurt", "attack");
	static final Map<String, List<String>> FALLBACK = Map.of(
			"sprint", List.of("sprint", "walk"),
			"sneak_walk", List.of("sneak_walk", "sneak", "walk"),
			"fall", List.of("fall", "jump"),
			"glide", List.of("glide", "fall", "jump"),
			"swim", List.of("swim", "walk"));
	static final double MOVING = 0.025;

	/** Two frames and how far between them: frame {@code a} at t = 0, {@code b} at t = 1. */
	public record Pick(int a, int b, float t) {
		/** Hold the last baked pose until the attachment changes; don't predict across bones. */
		Pick forMount(String id) {
			return KaratMount.onShoulder(id, a) == KaratMount.onShoulder(id, b) ? this : of(a);
		}

		static Pick of(int frame) {
			return new Pick(frame, frame, 0);
		}
	}

	private static String event = "idle";
	private static long since = System.currentTimeMillis();
	private static final long[] shots = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
	private static LocalPlayer seen;
	private static double x;
	private static double y;
	private static double z;
	private static double speed;
	private static double dy;
	private static int air;
	private static boolean swinging;
	private static float swing;
	private static int hurt;

	private CosmeticMotion() {
	}

	/** Every client tick. */
	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			seen = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (p != seen) {
			seen = p;
			x = p.getX();
			y = p.getY();
			z = p.getZ();
		}
		double sx = p.getX() - x;
		double sy = p.getY() - y;
		double sz = p.getZ() - z;
		x = p.getX();
		y = p.getY();
		z = p.getZ();
		double step = Math.sqrt(sx * sx + sz * sz);
		if (step > 8 || Math.abs(sy) > 8) {
			step = 0;
			sy = 0;
		}
		speed = speed * 0.5 + step * 0.5;
		dy = dy * 0.5 + sy * 0.5;
		air = p.onGround() || p.onClimbable() || p.isInWater() ? 0 : air + 1;
		String next = state(p);
		if (!next.equals(event)) {
			event = next;
			since = now;
		}

		boolean nowSwinging = p.isSwinging();
		float anim = p.getSwingAnimation(1);
		if (nowSwinging && (!swinging || anim < swing)) {
			shots[ONE_SHOTS.indexOf("attack")] = now;
		}
		swinging = nowSwinging;
		swing = anim;
		if (p.hurtTime > hurt) {
			shots[ONE_SHOTS.indexOf("hurt")] = now;
		}
		hurt = p.hurtTime;
	}

	private static String state(LocalPlayer p) {
		if (p.isPassenger()) {
			return "ride";
		}
		if (p.isFallFlying()) {
			return "glide";
		}
		if (p.isSwimming() || p.getPose() == Pose.SWIMMING || (p.isInWater() && !p.onGround())) {
			return "swim";
		}
		if (air >= 2) {
			return dy > 0.01 ? "jump" : dy < -0.08 ? "fall" : event.equals("fall") ? "fall" : "jump";
		}
		boolean moving = speed > MOVING;
		if (p.isCrouching()) {
			return moving ? "sneak_walk" : "sneak";
		}
		if (moving) {
			return p.isSprinting() ? "sprint" : "walk";
		}
		return "idle";
	}

	/** Where in its animation a cosmetic is on you now; {@code idleOnly}: in the wardrobe, which shows the idle loop. */
	static Pick pick(WardrobeNet.Item item, boolean idleOnly) {
		if (item == null || item.frames() <= 1 && !item.reacts()) {
			return null;
		}
		long now = System.currentTimeMillis();
		if (!idleOnly && item.reacts()) {
			for (int i = 0; i < ONE_SHOTS.size(); i++) {
				WardrobeNet.Clip shot = item.clips().get(ONE_SHOTS.get(i));
				if (shot != null && now - shots[i] < 1000L * shot.frames() / item.fps()) {
					return at(shot, now - shots[i], item.fps());
				}
			}
			for (String e : FALLBACK.getOrDefault(event, List.of(event))) {
				WardrobeNet.Clip clip = item.clips().get(e);
				if (clip != null && !e.equals("idle")) {
					return at(clip, now - since, item.fps());
				}
			}
		}
		return at(item.idle(), now, item.fps());
	}

	/** The position {@code millis} into a clip. */
	static Pick at(WardrobeNet.Clip clip, long millis, int fps) {
		double p = Math.max(0, millis) * fps / 1000.0;
		if (clip.loop()) {
			p = p % clip.frames();
		} else if (p >= clip.frames() - 1) {
			return Pick.of(clip.start() + clip.frames() - 1);
		}
		int a = clip.start() + (int) p;
		return new Pick(a, clip.next(a), (float) (p - Math.floor(p)));
	}
}
