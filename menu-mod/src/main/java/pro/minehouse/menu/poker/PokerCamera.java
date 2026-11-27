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
	private static int side = 1;
	private static boolean wanted;
	/** 0 at the player's eyes, 1 over the table; moves towards wanted over time. */
	private static float progress;
	private static long lastFrame;

	private PokerCamera() {
	}

	/** Puts the camera over this table (flies there if it is not yet). */
	static void over(Vec3 tableCenter, float yaw, int fromSide) {
		center = tableCenter;
		tableYaw = yaw;
		side = fromSide >= 0 ? 1 : -1;
		wanted = true;
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
		return progress > 0.4F;
	}

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
		if (progress <= 0) return null;

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
		Vec3 front = new Vec3(Math.sin(a), 0, -Math.cos(a)).scale(side);
		Vec3 target = center.add(front.scale(TOWARD_PLAYER));
		// Look from the player's side across the table.
		Vec3 look = front.scale(-1);
		double p = Math.toRadians(PITCH);
		Vec3 pos = target.add(front.scale(Math.cos(p) * DISTANCE)).add(0, Math.sin(p) * DISTANCE, 0);
		float yRot = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		return new Shot(pos, yRot, PITCH);
	}

	private static float smooth(float t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
}
