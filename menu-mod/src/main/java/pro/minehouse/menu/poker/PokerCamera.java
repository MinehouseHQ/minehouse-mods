package pro.minehouse.menu.poker;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The camera over a poker table: when a hand starts for a seated player it flies out of their
 * head up over the table (looking down at it from their side, the felt filling the screen) and
 * stays there while they play; it flies back when the table screen closes or the game stops.
 * The cards and chips are the server's, in the world; the screen only adds its UI.
 */
public final class PokerCamera {
	/** How far the camera is from the point it looks at, and how steeply it looks down. */
	private static final double DISTANCE = 2.6;
	private static final float PITCH = 62F;
	/** The point looked at sits this far from the middle towards the player, so the table rides high on the screen (their cards are drawn at the bottom). */
	private static final double TOWARD_PLAYER = 0.35;
	private static final long FLY_IN = 1100;
	private static final long FLY_OUT = 750;

	public record Shot(Vec3 position, float yRot, float xRot) {
	}

	/** The table's centre (top of the felt), its yaw (PokerWorld's frame) and which side we watch from (+1 front, -1 back). */
	private static Vec3 center;
	private static float tableYaw;
	/** The seat we watch from (PokerWorld: seat i at 90° + 60°·i around the table). */
	private static int seat;
	/** What the player turned and zoomed by hand: degrees around the table, degrees of tilt, distance factor. */
	private static float orbitYaw, orbitPitch;
	private static float zoom = 1;
	private static final float MIN_ZOOM = 0.55F, MAX_ZOOM = 2.2F;
	private static boolean wanted;
	/** 0 at the player's eyes, 1 over the table; moves towards wanted over time. */
	private static float progress;
	private static long lastFrame;

	private PokerCamera() {
	}

	/** Puts the camera over this table, behind seat {@code fromSeat} (flies there if it is not yet). */
	static void over(Vec3 tableCenter, float yaw, int fromSeat) {
		if (center == null || !center.equals(tableCenter) || seat != fromSeat) resetView();
		center = tableCenter;
		tableYaw = yaw;
		seat = fromSeat;
		wanted = true;
	}

	/** Turns the camera around the table (mouse drag): {@code dx} pixels sideways, {@code dy} up and down. */
	static void orbit(double dx, double dy) {
		orbitYaw = Mth.wrapDegrees(orbitYaw - (float) dx * 0.5F);
		orbitPitch = Mth.clamp(orbitPitch + (float) dy * 0.35F, 18F - PITCH, 89F - PITCH);
	}

	/** Zooms in (positive steps, the wheel forward) or out. */
	static void zoom(double steps) {
		zoom = Mth.clamp(zoom * (float) Math.pow(0.88, steps), MIN_ZOOM, MAX_ZOOM);
	}

	/** Back to the view from your seat. */
	static void resetView() {
		orbitYaw = 0;
		orbitPitch = 0;
		zoom = 1;
	}

	/**
	 * Whether your own body is drawn: not while the camera hangs over your seat (your head would
	 * fill the bottom of the screen), but when you turn the view to look at yourself from across.
	 */
	public static boolean showsSelf() {
		return progress <= 0.4F || Math.abs(orbitYaw) > 70;
	}

	static boolean turned() {
		return orbitYaw != 0 || orbitPitch != 0 || zoom != 1;
	}

	/** Flies the camera back to the player. */
	static void release() {
		wanted = false;
	}

	static boolean wanted() {
		return wanted;
	}

	/** Whether the camera is (mostly) over the table: the HUD hides then. */
	public static boolean hidesHud() {
		return progress > 0.4F || holding;
	}

	/** First person at the table (V): no camera over it, your cards in your hands (PokerHands). Kept for the session. */
	static boolean firstPerson;
	/** Your cards are in your hands now (first person and playing): the hand, hotbar and HUD hide. */
	static boolean holding;

	/** Advances the flight; null when the camera is the player's own. */
	public static Shot shot(Entity entity, Level level, float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		long dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
		lastFrame = now;
		if (mc.player == null || entity != mc.player || level == null || center == null) {
			progress = 0;
			return null;
		}
		if (wanted) progress = Math.min(1, progress + dt / (float) FLY_IN);
		else progress = Math.max(0, progress - dt / (float) FLY_OUT);
		if (progress <= 0) {
			resetView();
			return null;
		}

		Shot over = overTable();
		Vec3 eyes = entity.getEyePosition(partialTicks);
		float yaw0 = entity.getViewYRot(partialTicks);
		float pitch0 = entity.getViewXRot(partialTicks);
		float t = smooth(progress);
		// Up in an arc on the way: a little higher than the straight line in the middle of the flight.
		double lift = Math.sin(Math.PI * t) * 0.6;
		Vec3 pos = eyes.lerp(over.position(), t).add(0, lift, 0);
		float yaw = yaw0 + Mth.wrapDegrees(over.yRot() - yaw0) * t;
		float pitch = Mth.lerp(t, pitch0, over.xRot());
		return new Shot(pos, yaw, pitch);
	}

	private static Shot overTable() {
		double a = Math.toRadians(tableYaw);
		Vec3 front = new Vec3(Math.sin(a), 0, -Math.cos(a));
		Vec3 right = new Vec3(-Math.cos(a), 0, -Math.sin(a));
		// Out from the middle towards our seat, then turned by hand.
		double phi = Math.toRadians(90 + 60 * seat);
		Vec3 out = right.scale(Math.cos(phi)).add(front.scale(Math.sin(phi))).normalize();
		double o = Math.toRadians(orbitYaw);
		out = new Vec3(out.x * Math.cos(o) - out.z * Math.sin(o), 0, out.x * Math.sin(o) + out.z * Math.cos(o));
		float pitch = PITCH + orbitPitch;
		double distance = DISTANCE * zoom;
		// The point looked at sits a little towards us, so the table rides high on the screen (our cards are drawn at the bottom).
		Vec3 target = center.add(out.scale(TOWARD_PLAYER * Math.min(1, zoom)));
		double p = Math.toRadians(pitch);
		Vec3 pos = target.add(out.scale(Math.cos(p) * distance)).add(0, Math.sin(p) * distance, 0);
		Vec3 look = out.scale(-1);
		float yRot = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		return new Shot(pos, yRot, pitch);
	}

	private static float smooth(float t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
}
