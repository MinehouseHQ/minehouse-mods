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
 * a column of light from the portal up past the sky, round and with depth: a white-hot core, a cyan body and a
 * violet haze as three tubes of light one inside the other, light running up it and two strands winding round
 * it, blended over what is behind (the translucent debug-quads pipeline, no depth written).
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
	private static int tintStart, tintEnd = -1000;
	private static String tintWorld;

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

	static void receive(LimboPayloads.Tint p) {
		Minecraft mc = Minecraft.getInstance();
		if (p.ticks() <= 0) {
			tintEnd = Math.min(tintEnd, clientTicks);
			pro.minehouse.riftclient.cine.CinePlayer.stop();
			return;
		}
		// the vision itself is a film the client draws in 3D (VisionFilm); the veil stays for when it cannot play
		pro.minehouse.riftclient.cine.CinePlayer.play(pro.minehouse.riftclient.vision.VisionFilm::new, 0);
		tintStart = clientTicks;
		tintEnd = clientTicks + p.ticks();
		tintWorld = mc.level == null ? null : mc.level.dimension().identifier().toString();
	}

	static void clear() {
		BEAMS.clear();
		flashStart = -1;
		tintEnd = -1000;
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
		if (tintEnd > clientTicks && mc.level != null && !mc.level.dimension().identifier().toString().equals(tintWorld)) {
			tintEnd = clientTicks;
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

	/** Points round each tube. */
	private static final int AROUND = 28;
	private static final float[] COS = new float[AROUND + 1], SIN = new float[AROUND + 1];
	/** Heights above the foot: every 3 blocks low down, every 10 above. */
	private static final float[] UP;

	/*
	 * The three tubes, outside in (drawn in that order): radius as a share of the half-width, strength, colour.
	 * Haze violet, body cyan, core nearly white, as item.fsh mhBeam has them across its strip.
	 */
	private static final float[] RADIUS = {0.78f, 0.3f, 0.1f};
	private static final float[][] COLOUR = {{0.62f, 0.38f, 1f}, {0.55f, 0.93f, 1f}, {0.9f, 0.98f, 1f}};

	static {
		for (int i = 0; i <= AROUND; i++) {
			double t = i * Math.PI * 2 / AROUND;
			COS[i] = (float) Math.cos(t);
			SIN[i] = (float) Math.sin(t);
		}
		java.util.List<Float> up = new java.util.ArrayList<>();
		for (float h = 0; h < 300; h += 3) {
			up.add(h);
		}
		for (float h = 300; h <= HEIGHT; h += 10) {
			up.add(h);
		}
		UP = new float[up.size()];
		for (int i = 0; i < UP.length; i++) {
			UP[i] = up.get(i);
		}
	}

	/*
	 * A real volume of light round the axis (Timur 28.11: «сделай луч объемным… в клиентском моде»), not a strip
	 * turned to the eye: three tubes one inside the other, each glowing as much as the eye's ray runs through it.
	 * A ray through a round column at side offset s crosses 2·sqrt(R²−s²) of it, which on the tube's surface is
	 * the share of it that faces the eye, and the steeper the eye looks up or down the longer the ray stays
	 * inside, so alpha = 1 − exp(−strength · facing / horizontal share of the ray). Seen from the side that is the
	 * same soft column as before; from below or above, near the foot, it is a round shaft going away into the sky.
	 * Light runs up the body, two strands wind round it, the haze breathes, as in mhBeam. The tubes write no depth
	 * (debug quads), so the inner ones show through the outer.
	 */
	private static void draw(PoseStack.Pose p, VertexConsumer vc, State s, Vec3 cam, float time, float reach) {
		LimboPayloads.Beam b = s.beam;
		double ax = b.x() - cam.x, az = b.z() - cam.z;
		double hd = Math.max(1e-3, Math.sqrt(ax * ax + az * az));
		float g = ease(s.grow);
		float half = (float) Math.max(b.width() * 0.5f, MIN_ANGLE * hd) * g;
		double foot = b.y() - cam.y;
		float pulse = (float) (0.82 + 0.18 * Math.sin(time * 5.0) * Math.sin(time * 1.7 + 1.0));
		int n = AROUND + 1;
		// far off it is only a few pixels across: the haze and the body carry it, so they glow harder
		float far = ease((float) ((hd - 100) / 300));
		for (int layer = 0; layer < RADIUS.length; layer++) {
			float r = RADIUS[layer] * half;
			// standing inside a tube, its walls would only fog the view: let it go as the eye comes in
			float inside = ease((float) ((hd - r) / (r * 1.5)));
			if (inside <= 0.002f) {
				continue;
			}
			float[] prev = null;
			for (float up : UP) {
				double y = foot + up;
				// the axis wanders a little, as mhBeam's wob
				double wx = half * (0.03 * Math.sin(up * 0.13 - time * 3.0) + 0.02 * Math.sin(up * 0.34 + time * 5.0));
				double wz = half * (0.03 * Math.cos(up * 0.11 - time * 2.6) + 0.02 * Math.sin(up * 0.29 - time * 4.3));
				float top = ease((1 - up / HEIGHT) / 0.06f);
				float[] row = new float[n * 7];
				for (int c = 0; c < n; c++) {
					double px = ax + wx + COS[c] * r, pz = az + wz + SIN[c] * r;
					double h = Math.sqrt(px * px + pz * pz);
					double l = Math.sqrt(h * h + y * y);
					double facing = h < 1e-4 ? 1 : Math.abs(COS[c] * px + SIN[c] * pz) / h;
					double through = facing / Math.max(h / Math.max(l, 1e-4), 0.35);
					int o = c * 7;
					put(row, o, px, y, pz, reach);
					shade(row, o + 3, layer, c, up, through, time, pulse, far, g * top * inside);
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
	}

	/** Colour and alpha of one tube's point c round, up blocks above the foot, a ray through it of that much. */
	private static void shade(float[] out, int at, int layer, int c, float up, double through, float time,
			float pulse, float far, float k) {
		double angle = c * Math.PI * 2 / AROUND;
		double strength, white;
		if (layer == 0) {
			strength = (0.22 + far * 0.1) * pulse;
			white = 0;
		} else if (layer == 1) {
			// light running up, a band every ~13 blocks; two strands winding round, a turn every ~37
			double run = Math.pow(0.5 + 0.5 * Math.sin(up * 0.49 - time * 16.0 + 2.0 * Math.sin(angle + time * 0.7)), 5.0);
			double twist = Math.pow(0.5 + 0.5 * Math.sin(angle * 2 - up * 0.17 + time * 7.0), 10.0);
			strength = (0.4 + far * 1.4) * pulse + run * 0.5 + twist * 0.7;
			white = Math.min(1, run * 0.5 + twist * 0.45);
		} else {
			strength = 1.5 + far * 2.5;
			white = 0.7;
		}
		// each tube is seen twice along the ray, its near and its far wall: half each
		double a = (1 - Math.exp(-strength * 0.5 * through)) * k;
		float[] col = COLOUR[layer];
		out[at] = (float) (col[0] + (1 - col[0]) * white);
		out[at + 1] = (float) (col[1] + (1 - col[1]) * white);
		out[at + 2] = (float) (col[2] + (1 - col[2]) * white);
		out[at + 3] = (float) Math.min(1, Math.max(0, a));
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

	/** The white-out over the whole screen, and under it the vision's steel-blue look. */
	static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!pro.minehouse.riftclient.cine.CinePlayer.playing()) {
			tint(g, delta);
		}
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

	/**
	 * The vision in the limbo (Timur 28.11, after the 3 Body Problem titles: almost monochrome, cold steel-blue): a
	 * blue veil over everything, deeper at the edges, in over a second and out over a second and a half.
	 */
	private static void tint(GuiGraphicsExtractor g, DeltaTracker delta) {
		float now = clientTicks + delta.getGameTimeDeltaPartialTick(false);
		if (now >= tintEnd + 30) {
			return;
		}
		float a = Math.min(ease((now - tintStart) / 20f), 1 - ease((now - tintEnd) / 30f));
		if (a <= 0.003f) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		g.fill(0, 0, w, h, argb(a * 0.34f, 0x34, 0x5A, 0x86));
		// a vignette: the edges sink into a dark blue
		int steps = 12;
		int band = Math.max(2, Math.min(w, h) / 5 / steps);
		for (int i = 0; i < steps; i++) {
			float k = 1 - i / (float) steps;
			int c = argb(a * 0.11f * k * k, 0x08, 0x12, 0x24);
			int o = i * band;
			g.fill(o, o, w - o, o + band, c);
			g.fill(o, h - o - band, w - o, h - o, c);
			g.fill(o, o + band, o + band, h - o - band, c);
			g.fill(w - o - band, o + band, w - o, h - o - band, c);
		}
	}

	private static int argb(float a, int r, int g, int b) {
		return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (r << 16) | (g << 8) | b;
	}
}
