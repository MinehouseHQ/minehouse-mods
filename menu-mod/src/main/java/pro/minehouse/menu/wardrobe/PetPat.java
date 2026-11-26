package pro.minehouse.menu.wardrobe;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Stroking a pet (MineHouse Cosmetics 0.16.0+, cosmetics-mod {@code PetPetting}): the server says who
 * strokes a pet where; we draw that player crouched over it, one hand stroking its head, the other on
 * the knee. For you yourself the camera swings out over your shoulder for the stroke, and back.
 * Players without this mod see vanilla arm swings and hearts instead.
 */
public final class PetPat {
	/** Ticks to swing the camera out and back, and to reach out and take the hand away. */
	private static final float BLEND = 6;
	/**
	 * From behind the right shoulder but well out to the side and above, so the pet in front of you
	 * isn't hidden behind your back (and whatever you wear on it).
	 */
	private static final double CAMERA_BACK = .3, CAMERA_SIDE = 2.1, CAMERA_UP = .55;
	/** Moving away (walking, knocked back) ends your stroke on your screen at once. */
	private static final double LEAVE = .6;

	public record PatPayload(int player, double x, double y, double z, float top, int delay, int ticks) implements CustomPacketPayload {
		static final Type<PatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WardrobeNet.NS, "pet_pat"));
		static final StreamCodec<FriendlyByteBuf, PatPayload> CODEC = CustomPacketPayload.codec(PatPayload::write, PatPayload::new);

		PatPayload(FriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
		}

		void write(FriendlyByteBuf buf) {
			buf.writeVarInt(player);
			buf.writeDouble(x);
			buf.writeDouble(y);
			buf.writeDouble(z);
			buf.writeFloat(top);
			buf.writeVarInt(delay);
			buf.writeVarInt(ticks);
		}

		@Override
		public Type<PatPayload> type() {
			return TYPE;
		}
	}

	/** One stroke. Times are in ticks since {@code start} (wall clock, so it moves smoothly between ticks). */
	private static final class Pat {
		final long start = System.nanoTime();
		double x, y, z;
		float top;
		/** The hand reaches the pet at {@code delay}, lets go by {@code end}. */
		float delay, end;
		Vec3 from;

		float t() {
			return (System.nanoTime() - start) / 5e7f;
		}

		/** 0..1: how far into the pose (or the over-the-shoulder shot). */
		float weight(float t, float in) {
			return smooth((t - in) / BLEND) * (1 - smooth((t - (end - BLEND)) / BLEND));
		}
	}

	public record Shot(Vec3 position, float yRot, float xRot) {
	}

	private static final Map<Integer, Pat> pats = new HashMap<>();

	private PetPat() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(PatPayload.TYPE, PatPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(PatPayload.TYPE, (payload, context) -> receive(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> pats.clear());
		ClientTickEvents.END_CLIENT_TICK.register(PetPat::tick);
	}

	private static void receive(Minecraft mc, PatPayload p) {
		Pat pat = pats.get(p.player());
		if (p.ticks() <= 0) {
			if (pat != null) {
				pat.end = Math.min(pat.end, pat.t() + BLEND);
			}
			return;
		}
		if (pat == null || pat.t() > pat.end - BLEND) {
			pat = new Pat();
			pats.put(p.player(), pat);
			Entity e = mc.level == null ? null : mc.level.getEntity(p.player());
			pat.from = e == null ? null : e.position();
		}
		float t = pat.t();
		pat.x = p.x();
		pat.y = p.y();
		pat.z = p.z();
		pat.top = p.top();
		// The pet has come up: the hand goes to where it is now.
		pat.delay = p.delay() == 0 ? Math.min(t, Math.max(pat.delay, 0)) : t + p.delay();
		if (p.delay() == 0 && pat.delay < t - 1) {
			pat.delay = t;
		}
		pat.end = t + p.ticks();
	}

	private static void tick(Minecraft mc) {
		pats.values().removeIf(p -> p.t() > p.end + 1);
		var me = mc.player;
		if (me == null) {
			pats.clear();
			return;
		}
		Pat pat = pats.get(me.getId());
		if (pat == null) {
			return;
		}
		float t = pat.t();
		if (pat.from != null && me.position().distanceTo(pat.from) > LEAVE) {
			pat.end = Math.min(pat.end, t + BLEND);
			return;
		}
		if (t < pat.end - BLEND) {
			// Face the pet, so the server (and everyone) sees you turned to it.
			double dx = pat.x - me.getX(), dz = pat.z - me.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) Math.toDegrees(Math.atan2(me.getEyeY() - pat.top, Math.max(.3, Math.hypot(dx, dz))));
			me.setYRot(yaw);
			me.setXRot(Math.clamp(pitch, -10, 70));
			me.setYHeadRot(yaw);
			me.setYBodyRot(yaw);
		}
	}

	/** Is this player stroking a pet that is low enough to crouch to? */
	public static void extract(Avatar avatar, AvatarRenderState state) {
		Pat pat = pats.get(avatar.getId());
		if (pat != null && pat.top - avatar.getY() < 1.1 && pat.weight(pat.t(), pat.delay - BLEND) > .5f) {
			state.isCrouching = true;
		}
	}

	/** After vanilla posed the model: the stroking arm, the hand on the knee, the gaze on the pet. */
	public static void pose(AvatarRenderState state, HumanoidModel<?> model) {
		Pat pat = pats.get(state.id);
		if (pat == null) {
			return;
		}
		float t = pat.t();
		float w = pat.weight(t, pat.delay - BLEND);
		if (w <= 0) {
			return;
		}
		double horiz = Math.clamp(Math.hypot(pat.x - state.x, pat.z - state.z), .3, 1.2);
		double shoulder = state.isCrouching ? 1.1 : 1.4;
		// The arm hangs at xRot 0 and points ahead at -PI/2; aim it at the top of the head.
		float below = (float) Math.clamp(Math.atan2(shoulder - (pat.top - state.y), horiz), -.2, 1.3);
		float stroke = t - pat.delay;
		float s = (float) Math.sin(stroke * 2 * Math.PI / 14);
		float c = (float) Math.cos(stroke * 2 * Math.PI / 14);
		float on = smooth(stroke / 3);
		float armX = -((float) Math.PI / 2 - below) + .2f * s * on;
		float armY = -.15f + .05f * c * on;
		float armZ = .1f + .05f * c * on;
		model.rightArm.xRot = lerp(w, model.rightArm.xRot, armX);
		model.rightArm.yRot = lerp(w, model.rightArm.yRot, armY);
		model.rightArm.zRot = lerp(w, model.rightArm.zRot, armZ);
		model.leftArm.xRot = lerp(w, model.leftArm.xRot, -.55f);
		model.leftArm.yRot = lerp(w, model.leftArm.yRot, .1f);
		model.leftArm.zRot = lerp(w, model.leftArm.zRot, -.08f);
		model.body.yRot = lerp(w, model.body.yRot, 0);
		float look = (float) Math.clamp(Math.atan2(shoulder + .3 - (pat.top - state.y), horiz), 0, 1.1);
		model.head.xRot = lerp(w, model.head.xRot, look + .06f * s * on);
		model.head.zRot = lerp(w, model.head.zRot, .12f * on);
	}

	/** Is the camera out of your head for a stroke? (The first-person hand is hidden then.) */
	public static boolean cameraOut() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		Pat pat = pats.get(mc.player.getId());
		return pat != null && pat.weight(pat.t(), 0) > 0;
	}

	/** Your own stroke: the camera over your right shoulder, looking down at the pet. */
	public static Shot shot(Entity entity, Level level, float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == null || entity != mc.player || level == null || mc.gui.screen() != null) {
			return null;
		}
		Pat pat = pats.get(entity.getId());
		if (pat == null) {
			return null;
		}
		float k = pat.weight(pat.t(), 0);
		if (k <= 0) {
			return null;
		}
		Vec3 eye = entity.getEyePosition(partialTicks);
		Vec3 pet = new Vec3(pat.x, pat.top, pat.z);
		double yaw = Math.atan2(-(pat.x - eye.x), pat.z - eye.z);
		Vec3 front = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 wanted = eye.subtract(front.scale(CAMERA_BACK)).add(right.scale(CAMERA_SIDE)).add(0, CAMERA_UP, 0);
		HitResult hit = level.clip(new ClipContext(eye, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
		Vec3 far = wanted;
		if (hit.getType() != HitResult.Type.MISS) {
			double d = Math.max(.3, hit.getLocation().distanceTo(eye) - .25);
			far = eye.add(wanted.subtract(eye).normalize().scale(d));
		}
		Vec3 target = pet.subtract(0, .25, 0).scale(.8).add(eye.subtract(0, .5, 0).scale(.2));
		Vec3 look = target.subtract(far);
		float farYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float farPitch = (float) Math.toDegrees(Math.atan2(-look.y, Math.hypot(look.x, look.z)));
		float e = smooth(k);
		Vec3 pos = eye.add(far.subtract(eye).scale(e));
		float yRot = entity.getViewYRot(partialTicks);
		float xRot = entity.getViewXRot(partialTicks);
		return new Shot(pos, yRot + e * wrap(farYaw - yRot), xRot + e * (farPitch - xRot));
	}

	private static float wrap(float degrees) {
		degrees %= 360;
		if (degrees >= 180) {
			degrees -= 360;
		}
		if (degrees < -180) {
			degrees += 360;
		}
		return degrees;
	}

	private static float lerp(float w, float a, float b) {
		return a + (b - a) * w;
	}

	private static float smooth(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}
}
