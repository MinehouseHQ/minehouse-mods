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
 * translucent debug-quads pipeline), turned to the eye like a cylinder would look.
 *
 * <p>Like the sky, it is never out of reach: every vertex further than {@link #reach} is pulled in along its
 * ray to that distance, so it sits on the screen where it really is, is not eaten by the fog, and anything
 * nearer still hides it. However far, it is never thinner than about ten degrees across, like the far copies of the vanilla beam.
 *
 * <p>The white-out ({@link LimboPayloads.Flash}) is ours too: a soft white over the whole screen, a little
 * blue at first, that comes up over the given time, holds through the change of world and then clears.
 */
public final class LimboBeams {
	private static final float HEIGHT = 2400;
		/** Not thinner than this many radians, however far. */
	private static final float MIN_ANGLE = 0.09f;

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
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, vc) -> draw(p, vc, b, cam, time, reach));
		}
	}

	private static float ease(float x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}

	/** Columns across the beam, -1..1, denser in the middle where the core is thin. */
	private static final float[] ACROSS = new float[41];
	/** Heights above the foot: every 3 blocks low down, every 12 above. */
	private static final float[] UP;

	static {
		for (int i = 0; i < ACROSS.length; i++) {
			float t = i / (ACROSS.length - 1f) * 2 - 1;
			ACROSS[i] = Math.signum(t) * (0.35f * Math.abs(t) + 0.65f * t * t);
		}
		java.util.List<Float> up = new java.util.ArrayList<>();
		for (float h = 0; h < 600; h += 3) {
			up.add(h);
		}
		for (float h = 600; h <= HEIGHT; h += 12) {
			up.add(h);
		}
		UP = new float[up.size()];
		for (int i = 0; i < UP.length; i++) {
			UP[i] = up.get(i);
		}
	}

	/*
	 * The look is the vanilla one, the End mod's item.fsh mhBeam (Timur 28.11: «в ваниле луч лучше выглядит»):
	 * the same formula, worked out per vertex on a fine grid instead of per pixel, on one strip turned to the eye.
	 * x is across (-1..1), v down from the top (0..1) of a 2400-high sheet, as there.
	 */
	private static void draw(PoseStack.Pose p, VertexConsumer vc, State s, Vec3 cam, float time, float reach) {
		LimboPayloads.Beam b = s.beam;
		double ax = b.x() - cam.x, az = b.z() - cam.z;
		double hd = Math.max(1e-3, Math.sqrt(ax * ax + az * az));
		// across the beam, as the eye sees it
		float sx = (float) (-az / hd), sz = (float) (ax / hd);
		float g = ease(s.grow);
		float half = (float) Math.max(b.width() * 0.5f, MIN_ANGLE * hd) * g;
		double foot = b.y() - cam.y;
		int n = ACROSS.length;
		float[] prev = null;
		float pulse = (float) (0.82 + 0.18 * Math.sin(time * 5.0) * Math.sin(time * 1.7 + 1.0));
		for (float up : UP) {
			float v = 1 - up / HEIGHT;
			float fade = 1;
			// row: x y z r g b a per column
			float[] row = new float[n * 7];
			for (int c = 0; c < n; c++) {
				float x = ACROSS[c];
				int o = c * 7;
				put(row, o, ax + sx * x * half, foot + up, az + sz * x * half, reach);
				shade(row, o + 3, x, Math.max(v, 0), time, pulse, g * fade);
			}
			if (prev != null) {
				for (int c = 0; c + 1 < n; c++) {
					vertex(p, vc, prev, c);
					vertex(p, vc, prev, c + 1);
					vertex(p, vc, row, c + 1);
					vertex(p, vc, row, c);
				}
			}
			prev = row;
		}
	}

	/** item.fsh mhBeam: colour and alpha at x across, v down. */
	private static void shade(float[] out, int at, float x, float v, float time, float pulse, float k) {
		float ax = Math.abs(x);
		double h = v * 900.0;
		double wob = 0.03 * Math.sin(h * 0.35 - time * 3.0) + 0.02 * Math.sin(h * 0.9 + time * 5.0);
		double xc = Math.abs(x - wob);
		double core = Math.exp(-xc * xc * 110.0);
		double body = Math.exp(-xc * xc * 16.0);
		double haze = Math.exp(-ax * ax * 3.5) * (1.0 - ax);
		double run = Math.pow(0.5 + 0.5 * Math.sin(h * 1.3 - time * 16.0 + 2.0 * Math.sin(x * 5.0 + time * 0.7)), 5.0);
		double twist = Math.pow(0.5 + 0.5 * Math.sin(x * 11.0 - h * 0.45 + time * 7.0), 10.0) * body;
		double a = core * 1.4 + body * 0.6 * pulse + haze * 0.3 * pulse + run * body * 0.55 + twist * 0.45;
		a *= ease(v / 0.06f);
		a = Math.min(1, Math.max(0, a)) * k;
		double r = 0.62 + (0.55 - 0.62) * body, gr = 0.38 + (0.93 - 0.38) * body, bl = 1.0;
		double w = Math.min(1, Math.max(0, core * 1.6 + run * body * 0.5 + twist * 0.3));
		out[at] = (float) (r + (1 - r) * w);
		out[at + 1] = (float) (gr + (1 - gr) * w);
		out[at + 2] = (float) (bl + (1 - bl) * w);
		out[at + 3] = (float) a;
	}

	private static void put(float[] row, int at, double x, double y, double z, float reach) {
		double l = Math.sqrt(x * x + y * y + z * z);
		double k = l > reach ? reach / l : 1;
		row[at] = (float) (x * k);
		row[at + 1] = (float) (y * k);
		row[at + 2] = (float) (z * k);
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float[] row, int c) {
		int o = c * 7;
		vc.addVertex(p, row[o], row[o + 1], row[o + 2]).setColor(row[o + 3], row[o + 4], row[o + 5], row[o + 6]);
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
