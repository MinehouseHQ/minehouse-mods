package pro.minehouse.riftclient;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/**
 * The End opening's beam as this mod draws it (Timur 28.11: «луч шейдером… возможно даже в скайбоксе», then
 * «с клиентским модом сделай»). The server only says where it stands ({@link LimboPayloads.Beam}); here it is
 * a column of light from the portal up past the sky: a white-hot core, a cyan body, a violet haze that swells
 * at the foot, light running up it and two strands twisting round it, all added onto what is behind (the
 * lightning's own pipeline), turned to the eye like a cylinder would look.
 *
 * <p>Like the sky, it is never out of reach: every vertex further than {@link #reach} is pulled in along its
 * ray to that distance, so it sits on the screen where it really is, is not eaten by the fog, and anything
 * nearer still hides it. However far, it is never thinner than about two degrees.
 *
 * <p>The white-out ({@link LimboPayloads.Flash}) is ours too: a soft white over the whole screen, a little
 * blue at first, that comes up over the given time, holds through the change of world and then clears.
 */
public final class LimboBeams {
	private static final double HEIGHT = 3000;
	private static final int SEGMENTS = 160;
	/** Not thinner than this many radians, however far. */
	private static final float MIN_ANGLE = 0.04f;

	private static final Map<Integer, State> BEAMS = new HashMap<>();
	private static int clientTicks;

	// the white-out: ticks to come up over, when it started, the world it started in, when that world changed
	private static int flashTicks;
	private static int flashStart = -1;
	private static String flashWorld;
	private static int flashLeft = -1;

	private LimboBeams() {
	}

	static final class State {
		LimboPayloads.Beam beam;
		int heard;
		float grow;
	}

	static void receive(LimboPayloads.Beam p) {
		State s = BEAMS.computeIfAbsent(p.id(), id -> new State());
		s.beam = p;
		s.heard = clientTicks;
	}

	static void receive(LimboPayloads.Flash p) {
		Minecraft mc = Minecraft.getInstance();
		flashTicks = Math.max(1, p.ticks());
		flashStart = clientTicks;
		flashWorld = mc.level == null ? null : mc.level.dimension().identifier().toString();
		flashLeft = -1;
	}

	static void clear() {
		BEAMS.clear();
		flashStart = -1;
	}

	static void tick(Minecraft mc) {
		clientTicks++;
		Iterator<State> it = BEAMS.values().iterator();
		while (it.hasNext()) {
			State s = it.next();
			boolean going = s.beam.fading() || clientTicks - s.heard > 40;
			s.grow = going ? Math.max(0, s.grow - 0.035f) : Math.min(1, s.grow + 0.07f);
			if (going && s.grow <= 0) {
				it.remove();
			}
		}
		if (flashStart >= 0 && flashLeft < 0 && mc.level != null
				&& !mc.level.dimension().identifier().toString().equals(flashWorld)) {
			flashLeft = clientTicks;
		}
		if (flashStart >= 0 && (flashLeft >= 0 && clientTicks - flashLeft > 60 || clientTicks - flashStart > 20 * 30)) {
			flashStart = -1;
		}
	}

	/** How far the beam is drawn at most: inside the fog, like the sky is. */
	private static float reach(Minecraft mc) {
		return Math.max(48, Math.min(mc.options.getEffectiveRenderDistance() * 16 * 0.55f, 180));
	}

	static void submit(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || BEAMS.isEmpty()) {
			return;
		}
		String dim = mc.level.dimension().identifier().toString();
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		float time = (mc.level.getGameTime() % 24000 + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20f;
		float reach = reach(mc);
		PoseStack pose = ctx.poseStack();
		for (State s : BEAMS.values()) {
			if (!s.beam.world().equals(dim) || s.grow <= 0.001f) {
				continue;
			}
			final State b = s;
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> draw(p, vc, b, cam, time, reach));
		}
	}

	private static float ease(float x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}

	private static void draw(PoseStack.Pose p, VertexConsumer vc, State s, Vec3 cam, float time, float reach) {
		LimboPayloads.Beam b = s.beam;
		double ax = b.x() - cam.x, az = b.z() - cam.z;
		double hd = Math.max(1e-3, Math.sqrt(ax * ax + az * az));
		// across the beam, as the eye sees it
		float sx = (float) (-az / hd), sz = (float) (ax / hd);
		float g = ease(s.grow);
		float w = (float) Math.max(b.width() * 0.5f, MIN_ANGLE * hd) * g;
		double y0 = b.y() - cam.y, y1 = b.y() + HEIGHT - cam.y;
		float[] ys = new float[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			// thicker near the foot, where it is seen up close
			float f = i / (float) SEGMENTS;
			ys[i] = (float) (y0 + (y1 - y0) * f * f);
		}
		double groundRise = b.top() - b.y();
		// the violet haze, swelling at the foot and at the mouth of the hole
		layer(p, vc, ys, ax, az, sx, sz, reach, (y, h) -> w * (2.6f + 3.5f * (float) Math.exp(-h / 14.0)
				+ 2.5f * (float) Math.exp(-Math.abs(h - groundRise) / 10.0)), 0.55f, 0.32f, 1.0f, (y, h) -> 0.11f * g, null);
		// the cyan body, light running up it
		layer(p, vc, ys, ax, az, sx, sz, reach, (y, h) -> w * 1.0f, 0.45f, 0.88f, 1.0f,
				(y, h) -> (0.32f + 0.22f * run(h, time)) * g, null);
		// the white core
		layer(p, vc, ys, ax, az, sx, sz, reach, (y, h) -> w * 0.22f, 1f, 1f, 1f, (y, h) -> (0.85f + 0.15f * run(h, time)) * g, null);
		// two strands twisting round it
		for (int k = 0; k < 2; k++) {
			final float phase = k * (float) Math.PI;
			layer(p, vc, ys, ax, az, sx, sz, reach, (y, h) -> w * 0.08f, 0.85f, 0.95f, 1f,
					(y, h) -> 0.5f * g * (0.6f + 0.4f * (float) Math.cos(h * 0.05 - time * 3 + phase)),
					(y, h) -> w * 0.55f * (float) Math.sin(h * 0.05 - time * 3 + phase));
		}
	}

	/** Light running up the beam: bright bands, quickly. */
	private static float run(double h, float time) {
		double s = 0.5 + 0.5 * Math.sin(h * 0.045 - time * 9.0);
		return (float) (s * s * s * s);
	}

	interface Fn {
		float at(float y, double h);
	}

	/**
	 * One strip up the beam, its middle line {@code offset} off the axis: full colour along the middle, nothing at
	 * the edges. {@code h} is the height above the foot.
	 */
	private static void layer(PoseStack.Pose p, VertexConsumer vc, float[] ys, double ax, double az, float sx, float sz,
			float reach, Fn width, float r, float gr, float bl, Fn alpha, Fn offset) {
		double foot = ys[0];
		float[] prev = null;
		for (int i = 0; i < ys.length; i++) {
			float y = ys[i];
			double h = y - foot;
			float half = width.at(y, h);
			float off = offset == null ? 0 : offset.at(y, h);
			float a = alpha.at(y, h);
			// fade out where it leaves the sky, and in from the foot
			a *= (float) Math.min(1, h / 3.0) * (1 - ease((float) ((h - (HEIGHT - 900)) / 900)));
			double cx = ax + sx * off, cz = az + sz * off;
			float[] row = {
				// left, middle, right: x y z each, pulled into reach
				0, 0, 0, 0, 0, 0, 0, 0, 0, a
			};
			put(row, 0, cx - sx * half, y, cz - sz * half, reach);
			put(row, 3, cx, y, cz, reach);
			put(row, 6, cx + sx * half, y, cz + sz * half, reach);
			if (prev != null) {
				quad(p, vc, prev, row, 0, r, gr, bl);
				quad(p, vc, prev, row, 6, r, gr, bl);
			}
			prev = row;
		}
	}

	private static void put(float[] row, int at, double x, double y, double z, float reach) {
		double l = Math.sqrt(x * x + y * y + z * z);
		double k = l > reach ? reach / l : 1;
		row[at] = (float) (x * k);
		row[at + 1] = (float) (y * k);
		row[at + 2] = (float) (z * k);
	}

	/** Edge column {@code e} (0 left or 6 right) to the middle, between two rows. */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, float[] a, float[] b, int e, float r, float g, float bl) {
		vc.addVertex(p, a[e], a[e + 1], a[e + 2]).setColor(r, g, bl, 0f);
		vc.addVertex(p, a[3], a[4], a[5]).setColor(r, g, bl, a[9]);
		vc.addVertex(p, b[3], b[4], b[5]).setColor(r, g, bl, b[9]);
		vc.addVertex(p, b[e], b[e + 1], b[e + 2]).setColor(r, g, bl, 0f);
	}

	/** The white-out over the whole screen. */
	static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (flashStart < 0) {
			return;
		}
		float t = clientTicks - flashStart + delta.getGameTimeDeltaPartialTick(false);
		float a = ease(t / flashTicks);
		if (flashLeft >= 0) {
			float since = clientTicks - flashLeft + delta.getGameTimeDeltaPartialTick(false);
			a *= 1 - ease((since - 25) / 35f);
		}
		if (a <= 0.003f) {
			return;
		}
		float blue = 1 - ease(t / (flashTicks * 0.8f));
		int rr = 255 - (int) (40 * blue), gg = 255 - (int) (12 * blue);
		int argb = ((int) (a * 255) << 24) | (rr << 16) | (gg << 8) | 255;
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb);
	}
}
