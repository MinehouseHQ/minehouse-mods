package pro.minehouse.riftclient;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;

/**
 * The opening of Chapter 2: the hole the old world fell into, and a new universe out of it.
 *
 * <ol>
 * <li>0.5–3.5 s: on black, a small black hole with a pale blue rim; blue silk petals swirl in,
 * faster and brighter;
 * <li>3.5–4.0 s: it all falls into a point, and a star flare crosses out of it;
 * <li>4.0 s: a white flash, and the black gives way to a blue and violet nebula;
 * <li>4–8 s: we fly through clouds of gas, past streaking stars and small galaxies;
 * <li>8–10 s: it slows, a big pink galaxy turns ahead;
 * <li>10–11.5 s: one more flash, and out of it the new world shows.
 * </ol>
 *
 * <p>All of it is drawn on a sheet in front of the camera (black behind, light on top), so it
 * needs neither a screen nor a shader. The server starts it with {@code minehouse:intro}.
 */
public final class Intro {
	static final float LENGTH = 11.5f;
	private static final float D = 1f;
	private static final int CLOUDS = 46;
	private static final int STARS = 320;
	private static final int GALAXIES = 7;

	/** The scene's music (assets/minehouse-rift/sounds/intro.ogg); the cues below play over it. */
	private static final Identifier MUSIC = Identifier.fromNamespaceAndPath("minehouse-rift", "intro");

	private static long startedAt = -1;
	private static int cue;

	private Intro() {
	}

	public static void start() {
		startedAt = System.nanoTime();
		cue = 0;
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(new SimpleSoundInstance(MUSIC, SoundSource.MASTER, 1f, 1f, RandomSource.create(), false, 0,
				SoundInstance.Attenuation.NONE, 0, 0, 0, true));
	}

	public static boolean playing() {
		return startedAt >= 0;
	}

	static float time() {
		return (System.nanoTime() - startedAt) / 1e9f;
	}

	/** Sounds on cue; and the end. */
	public static void tick(Minecraft mc) {
		if (!playing()) {
			return;
		}
		float t = time();
		if (t > LENGTH || mc.level == null) {
			startedAt = -1;
			return;
		}
		float[] at = { 0.4f, 3.4f, 4.0f, 4.1f, 9.9f };
		while (cue < at.length && t >= at[cue]) {
			switch (cue) {
			case 0 -> sound(mc, SoundEvents.PORTAL_TRAVEL, 0.5f, 0.25f);
			case 1 -> sound(mc, SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1f);
			case 2 -> sound(mc, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1f);
			case 3 -> sound(mc, SoundEvents.END_PORTAL_SPAWN, 0.8f, 0.6f);
			case 4 -> sound(mc, SoundEvents.BEACON_ACTIVATE, 0.7f, 1f);
			default -> {
			}
			}
			cue++;
		}
	}

	private static void sound(Minecraft mc, net.minecraft.sounds.SoundEvent e, float pitch, float volume) {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}

	private static float smooth(float a, float b, float v) {
		float s = clamp((v - a) / (b - a));
		return s * s * (3 - 2 * s);
	}

	public static void submit(LevelRenderContext ctx) {
		if (!playing()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		final float t = time();
		Camera cam = mc.gameRenderer.mainCamera();
		final Vector3f look = new Vector3f(cam.forwardVector());
		final Vector3f up = new Vector3f(cam.upVector());
		final Vector3f right = new Vector3f(cam.leftVector()).negate();
		final Vector3f centre = new Vector3f(look).mul(D);
		PoseStack pose = ctx.poseStack();

		// The new world shows once the last flash is at its whitest.
		final float reveal = 10.3f;
		if (t < reveal) {
			TextureAtlasSprite white = mc.getModelManager().getBlockStateModelSet()
					.getParticleMaterial(Blocks.SNOW_BLOCK.defaultBlockState()).sprite();
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS), (p, vc) -> {
				Vector3f back = new Vector3f(look).mul(D * 1.25f);
				Vector3f ax = new Vector3f(right).mul(8), ay = new Vector3f(up).mul(8);
				Vector3f n = new Vector3f(look).negate();
				float[][] q = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
				for (int pass = 0; pass < 2; pass++) {
					for (int j = 0; j < 4; j++) {
						float[] v = q[pass == 0 ? j : 3 - j];
						vc.addVertex(p, back.x + ax.x * v[0] + ay.x * v[1], back.y + ax.y * v[0] + ay.y * v[1], back.z + ax.z * v[0] + ay.z * v[1])
								.setColor(0xFF000000).setUv(white.getU(0.5f), white.getV(0.5f)).setOverlay(OverlayTexture.NO_OVERLAY)
								.setLight(LightCoordsUtil.pack(0, 0)).setNormal(p, n.x, n.y, n.z);
					}
				}
			});
		}

		// 1-2. The hole, its petals, and the fall into a point.
		final float hole = 0.05f;
		final float grow = smooth(0.5f, 3.5f, t);
		final float fall = smooth(3.5f, 4.0f, t);
		if (t > 0.5f && t < 4.0f) {
			final float shrink = (1 - fall) * (1 - fall);
			final float bright = (0.6f + 1.2f * grow) * (1 + 1.5f * fall) * smooth(0.5f, 1.2f, t);
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(BlackHoleBlocks.PETAL), (p, vc) -> {
				for (int i = 0; i < 11; i++) {
					float a0 = i * 6.2832f / 11 + BlackHoleBlocks.hash(i, 1, 21, 0) * 0.5f + t * (0.25f + 0.5f * grow) + fall * 3;
					float reach = (7 + 7 * BlackHoleBlocks.hash(i, 2, 21, 0)) * (0.7f + 0.3f * grow);
					float breath = 0.7f + 0.3f * (float) Math.sin(t * 2.2f + i * 1.7f);
					float h = hole * 1.5f * shrink;
					BlackHoleBlocks.petal(p, vc, centre, right, up, h, a0 - 0.04f, reach, soft(0.3f * bright * breath, 1f, 0.35f, 0.7f));
					BlackHoleBlocks.petal(p, vc, centre, right, up, h, a0 + 0.04f, reach, soft(0.25f * bright * breath, 0.35f, 1f, 0.6f));
					BlackHoleBlocks.petal(p, vc, centre, right, up, h, a0, reach,
							softHeat(0.25f + 0.35f * BlackHoleBlocks.hash(i, 3, 21, 0), 0.9f * bright * breath));
				}
			});
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(BlackHoleBlocks.RING), (p, vc) -> {
				float r = hole / 0.78f * (1 - fall);
				BlackHoleBlocks.billboard(p, vc, centre, right, up, r, r, 0, softHeat(0.15f, (0.6f + 0.8f * grow) * (1 + fall)));
			});
		}

		// The star flare as it falls, and the flashes.
		final float flare = t < 4.0f ? fall * fall : clamp(1 - (t - 4.0f) / 0.7f);
		final float flash1 = t < 3.9f ? 0 : t < 4.05f ? smooth(3.9f, 4.05f, t) : 1 - smooth(4.05f, 4.9f, t);
		final float flash2 = t < 9.8f ? 0 : t < reveal ? smooth(9.8f, reveal, t) : 1 - smooth(reveal, LENGTH, t);
		if (flare > 0.01f) {
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(BlackHoleBlocks.RAY), (p, vc) -> {
				for (int i = 0; i < 8; i++) {
					boolean main = i % 2 == 0;
					float len = (main ? 1.3f : 0.45f) * (0.15f + 0.85f * flare);
					float wide = (main ? 0.05f : 0.025f) * (0.4f + 0.6f * flare);
					BlackHoleBlocks.ray(p, vc, centre, right, up, i * 0.7854f, 0, len, wide,
							soft(1.6f * flare, 0.85f, 0.93f, 1f));
				}
			});
		}

		// 3-5. The new universe: from the flash to the reveal.
		if (t > 3.95f && t < LENGTH) {
			final float born = smooth(3.95f, 4.3f, t);
			final float fade = 1 - smooth(10.0f, 11.2f, t);
			// Distance flown: fast at first, slowing.
			final float s = 7.5f * (1 - (float) Math.exp(-(t - 4) / 2.5f)) + 0.15f * Math.max(0, t - 4);
			final float speed = 3f * (float) Math.exp(-(t - 4) / 2.5f) + 0.15f;
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(BlackHoleBlocks.GLOW), (p, vc) -> {
				// A dim wash so the black reads as space full of gas.
				BlackHoleBlocks.billboard(p, vc, centre, right, up, 2.5f, 2.5f, 0,
						soft(0.5f * born * fade, 0.25f, 0.15f, 0.65f));
				for (int i = 0; i < CLOUDS; i++) {
					float x = (BlackHoleBlocks.hash(i, 1, 31, 0) - 0.5f) * 3.2f;
					float y = (BlackHoleBlocks.hash(i, 2, 31, 0) - 0.5f) * 2f;
					float z = 4 - ((s + BlackHoleBlocks.hash(i, 3, 31, 0) * 4) % 4) + 0.25f;
					float near = smooth(0.25f, 0.9f, z) * (1 - smooth(3.2f, 4.2f, z));
					float h = BlackHoleBlocks.hash(i, 4, 31, 0);
					int c = h < 0.4f ? soft(0.66f * near * born * fade, 0.55f, 0.17f, 0.82f)
							: h < 0.7f ? soft(0.62f * near * born * fade, 0.75f, 0.25f, 0.88f)
									: soft(0.66f * near * born * fade, 0.17f, 0.12f, 0.69f);
					Vector3f at = place(centre, right, up, x / z, y / z);
					float size = (0.9f + 0.8f * h) / z;
					BlackHoleBlocks.billboard(p, vc, at, right, up, size, size * 0.7f, h * 6, c);
				}
				// Galaxy cores rushing past, and the big one ahead.
				for (int i = 0; i < GALAXIES; i++) {
					float x = (BlackHoleBlocks.hash(i, 1, 41, 0) - 0.5f) * 2.4f;
					float y = (BlackHoleBlocks.hash(i, 2, 41, 0) - 0.5f) * 1.4f;
					float z = 4 - ((s + BlackHoleBlocks.hash(i, 3, 41, 0) * 4) % 4) + 0.25f;
					float near = smooth(0.25f, 0.8f, z) * (1 - smooth(3.2f, 4.2f, z));
					Vector3f at = place(centre, right, up, x / z, y / z);
					galaxy(p, vc, at, right, up, 0.06f / z, BlackHoleBlocks.hash(i, 4, 41, 0) * 3, t, near * born * fade);
				}
				float big = smooth(7.5f, 9.5f, t) * fade;
				if (big > 0) {
					Vector3f at = place(centre, right, up, 0.12f, 0.06f);
					galaxy(p, vc, at, right, up, 0.16f, 0.6f, t, big);
				}
				if (flash1 > 0) {
					BlackHoleBlocks.billboard(p, vc, centre, right, up, 3.5f, 3.5f, 0, soft(2.5f * flash1, 0.9f, 0.95f, 1f));
				}
				if (flash2 > 0) {
					BlackHoleBlocks.billboard(p, vc, centre, right, up, 4f, 4f, 0, soft(3f * flash2, 1f, 1f, 1f));
				}
			});
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(BlackHoleBlocks.RAY), (p, vc) -> {
				// Stars: streaks out from the middle, long while we fly fast.
				for (int i = 0; i < STARS; i++) {
					float x = (BlackHoleBlocks.hash(i, 1, 51, 0) - 0.5f) * 4f;
					float y = (BlackHoleBlocks.hash(i, 2, 51, 0) - 0.5f) * 4f;
					float z = 6 - ((s * 1.5f + BlackHoleBlocks.hash(i, 3, 51, 0) * 6) % 6) + 0.2f;
					float rFar = (float) Math.hypot(x, y) / (z + speed * 0.12f);
					float rNear = (float) Math.hypot(x, y) / z;
					float near = smooth(0.2f, 0.7f, z) * (1 - smooth(5f, 6.2f, z));
					float h = BlackHoleBlocks.hash(i, 4, 51, 0);
					int c = h < 0.5f ? soft(1.2f * near * born * fade, 1, 1, 1)
							: h < 0.8f ? soft(1.1f * near * born * fade, 0.6f, 0.8f, 1)
									: soft(1.1f * near * born * fade, 1, 0.6f, 0.85f);
					BlackHoleBlocks.ray(p, vc, centre, right, up, (float) Math.atan2(y, x), rFar,
							Math.max(0.004f, rNear - rFar), 0.014f / (0.4f + z * 0.3f), c);
				}
			});
		}
	}

	private static int soft(float bright, float r, float g, float b) {
		return BlackHoleBlocks.glowing(r * bright, g * bright, b * bright);
	}

	private static int softHeat(float t, float bright) {
		int c = BlackHoleBlocks.heat(t, 1);
		return soft(bright, (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f);
	}

	private static Vector3f place(Vector3f centre, Vector3f right, Vector3f up, float x, float y) {
		return new Vector3f(centre).add(right.x * x + up.x * y, right.y * x + up.y * y, right.z * x + up.z * y);
	}

	/** A spiral galaxy seen at a slant: a hot core, a pink haze, and two arms of glowing knots. */
	private static void galaxy(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f right, Vector3f up, float size,
			float tilt, float t, float bright) {
		if (bright < 0.01f) {
			return;
		}
		float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
		Vector3f a = new Vector3f(right).mul(ct).add(up.x * st, up.y * st, up.z * st);
		Vector3f b = new Vector3f(up).mul(ct).sub(right.x * st, right.y * st, right.z * st);
		Vector3f bb = new Vector3f(b).mul(0.4f);
		BlackHoleBlocks.billboard(p, vc, c, a, b, size * 2.2f, size * 0.8f, 0, soft(0.5f * bright, 0.75f, 0.3f, 0.85f));
		for (int arm = 0; arm < 2; arm++) {
			for (int k = 1; k <= 9; k++) {
				float s = k / 9f;
				float ang = arm * 3.1416f + t * 0.3f + 3.2f * s;
				float r = size * (0.25f + 1.5f * s);
				Vector3f at = new Vector3f(c).add(a.x * (float) Math.cos(ang) * r + bb.x * (float) Math.sin(ang) * r,
						a.y * (float) Math.cos(ang) * r + bb.y * (float) Math.sin(ang) * r, a.z * (float) Math.cos(ang) * r + bb.z * (float) Math.sin(ang) * r);
				float knot = size * (0.55f - 0.25f * s);
				int col = k % 3 == 0 ? soft((0.9f - 0.5f * s) * bright, 0.55f, 0.65f, 1f)
						: soft((0.9f - 0.5f * s) * bright, 0.95f, 0.5f, 0.9f);
				BlackHoleBlocks.billboard(p, vc, at, right, up, knot, knot, 0, col);
			}
		}
		BlackHoleBlocks.billboard(p, vc, c, right, up, size * 0.7f, size * 0.7f, 0, soft(1.6f * bright, 1f, 0.85f, 0.95f));
	}
}
