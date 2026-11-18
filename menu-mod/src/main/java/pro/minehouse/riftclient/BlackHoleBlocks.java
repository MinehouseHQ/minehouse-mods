package pro.minehouse.riftclient;

import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The black hole of the end of the rifts, drawn as a thing in the world and made of blocks.
 *
 * <ul>
 * <li>the horizon: a ball of black cubes;
 * <li>the disk: thousands of small glowing cubes on a tilted disk, white-hot inside, blue, then
 * violet outside, faster the nearer they are, brighter on the side that comes towards you;
 * <li>the disk's far side bent by the hole: a ring of the same cubes round the black, always facing
 * you, like in the films;
 * <li>a glow round it all and slow blue rays, like the trailer's;
 * <li>pieces of the world (grass, stone, logs...) spiralling in.
 * </ul>
 *
 * <p>The server (Events, cue {@code black_hole}) shows everyone flat pictures; on the core one it
 * writes where the hole really is, how big it is and how open ({@code minehouse_hole} in the item's
 * custom data). This client finds that, hides the pictures and draws the hole itself. Without the
 * data (an older server) the pictures stay.
 *
 * <p>The hole is hundreds of blocks wide and far away, past the fog. So, like the server's
 * pictures, it is drawn nearer and smaller by the same factor: it looks just as big, and the fog
 * doesn't eat it.
 */
public final class BlackHoleBlocks {
	static final Identifier CORE = Identifier.fromNamespaceAndPath("minehouse-events", "black_hole_core");
	static final Identifier RAYS = Identifier.fromNamespaceAndPath("minehouse-events", "black_hole_rays");
	static final String KEY = "minehouse_hole";
	static final Identifier GLOW = Identifier.fromNamespaceAndPath("minehouse-rift", "textures/fx/glow.png");
	static final Identifier RAY = Identifier.fromNamespaceAndPath("minehouse-rift", "textures/fx/ray.png");
	static final Identifier RING = Identifier.fromNamespaceAndPath("minehouse-rift", "textures/fx/ring.png");
	static final Identifier PETAL = Identifier.fromNamespaceAndPath("minehouse-rift", "textures/fx/petal.png");
	/** Silk petals swirling round the hole, and the bits along each. */
	static final int PETALS = 9;
	static final int PETAL_STEPS = 14;

	/** The black ball's radius, as a share of the hole's size (its whole glow on the server). */
	static final double HORIZON = 0.075;
	/** Cubes across the ball's radius. */
	static final int CELLS = 7;
	/** Glowing cubes on the disk, and in the bent ring over the hole. */
	static final int DISK = 4200;
	static final int HALO = 1400;
	/** Pieces of the world falling in at once. */
	static final int PIECES = 110;
	static final int BEAMS = 10;
	/** The disk leans this far from flat, so it is seen from the side and from above. */
	static final float TILT = 0.13f;
	private static final List<Block> STUFF = List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.OAK_LOG,
			Blocks.OAK_LEAVES, Blocks.COBBLESTONE, Blocks.SAND, Blocks.DEEPSLATE, Blocks.OAK_PLANKS, Blocks.GRAVEL);

	record Hole(Vec3 center, double size, double open) {
	}

	private static Hole hole;
	private static double shownOpen;
	private static final int[][] SHELL = cells(CELLS - 1.0, CELLS);

	private BlackHoleBlocks() {
	}

	private static int[][] cells(double from, double to) {
		int n = (int) Math.ceil(to);
		java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
		for (int i = -n; i <= n; i++) {
			for (int j = -n; j <= n; j++) {
				for (int k = -n; k <= n; k++) {
					double d = Math.sqrt(i * i + j * j + k * k);
					if (d > from && d <= to) {
						out.add(new int[] { i, j, k });
					}
				}
			}
		}
		return out.toArray(new int[0][]);
	}

	/** The hole the server wrote on this picture, or null. */
	static Hole read(Entity e) {
		if (!(e instanceof Display.ItemDisplay d) || !CORE.equals(d.getItemStack().get(DataComponents.ITEM_MODEL))) {
			return null;
		}
		CustomData data = d.getItemStack().get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		CompoundTag tag = data.copyTag().getCompoundOrEmpty(KEY);
		if (tag.isEmpty()) {
			return null;
		}
		return new Hole(new Vec3(tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 0), tag.getDoubleOr("z", 0)),
				tag.getDoubleOr("size", 0), tag.getDoubleOr("open", 0));
	}

	static void tick(Minecraft mc) {
		Hole found = null;
		if (mc.level != null) {
			for (Entity e : mc.level.entitiesForRendering()) {
				found = read(e);
				if (found != null) {
					break;
				}
			}
		}
		if (found == null) {
			shownOpen = 0;
		} else {
			// The server says how open it is twice a second; in between it eases there.
			shownOpen += (found.open() - shownOpen) * 0.12;
		}
		hole = found;
	}

	/** The server's flat pictures give way while this draws the hole. */
	public static boolean hidden(Entity e) {
		if (hole == null || !(e instanceof Display.ItemDisplay d)) {
			return false;
		}
		Identifier model = d.getItemStack().get(DataComponents.ITEM_MODEL);
		return CORE.equals(model) || RAYS.equals(model);
	}

	static float hash(int a, int b, int c, int d) {
		int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x1b873593 ^ d * 0x5bd1e995;
		h ^= h >>> 15;
		h *= 0x2c1b3c6d;
		h ^= h >>> 12;
		return (h & 0xffffff) / (float) 0x1000000;
	}

	/** Disk colour from hot (0, inner edge) to cool (1, outer edge), times a brightness. */
	static int heat(float t, float bright) {
		// White, then ice blue, then the blue of the trailer, then deep blue with a hint of violet.
		float r, g, b;
		if (t < 0.3f) {
			float s = t / 0.3f;
			r = 1 - 0.44f * s;
			g = 1 - 0.18f * s;
			b = 1;
		} else if (t < 0.65f) {
			float s = (t - 0.3f) / 0.35f;
			r = 0.56f - 0.27f * s;
			g = 0.82f - 0.19f * s;
			b = 1;
		} else {
			float s = (t - 0.65f) / 0.35f;
			r = 0.29f + 0.1f * s;
			g = 0.63f - 0.4f * s;
			b = 1 - 0.3f * s;
		}
		return 0xFF000000 | channel(r * bright) << 16 | channel(g * bright) << 8 | channel(b * bright);
	}

	private static int channel(float v) {
		return Math.max(0, Math.min(255, (int) (v * 255)));
	}

	/** One frame: everything above, handed to the game to draw with the world. */
	static void submit(LevelRenderContext ctx) {
		Hole h = hole;
		Minecraft mc = Minecraft.getInstance();
		if (h == null || shownOpen < 0.002 || mc.level == null) {
			return;
		}
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		Vec3 rel = h.center().subtract(cam);
		double dist = Math.max(rel.length(), 1e-3);
		// Nearer than the fog; everything shrinks by the same factor, so it looks as big.
		double far = Math.max(32, mc.options.getEffectiveRenderDistance() * 16 * 0.45);
		double k = Math.min(1, far / dist);
		final Vector3f at = new Vector3f((float) (rel.x * k), (float) (rel.y * k), (float) (rel.z * k));
		final float radius = (float) (h.size() * shownOpen * HORIZON * k);
		final float cell = radius / CELLS;
		final float time = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
		// The viewer's frame round the hole: towards it, and two sideways axes across the view.
		final Vector3f look = new Vector3f(at).normalize();
		Vector3f up0 = Math.abs(look.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		final Vector3f right = new Vector3f(look).cross(up0).normalize();
		final Vector3f up = new Vector3f(right).cross(look).normalize();
		// The disk's plane: whoever looks, sees it nearly edge on and a little from above, leant a
		// little sideways, like in the films. Its back half hides behind the black, its front half
		// crosses it.
		float roll = TILT * 0.5f;
		final Vector3f diskX = new Vector3f(right).mul((float) Math.cos(roll)).add(new Vector3f(up).mul((float) Math.sin(roll)));
		final Vector3f upr = new Vector3f(up).mul((float) Math.cos(roll)).sub(new Vector3f(right).mul((float) Math.sin(roll)));
		final Vector3f diskZ = new Vector3f(look).mul((float) Math.cos(TILT)).sub(new Vector3f(upr).mul((float) Math.sin(TILT))).normalize();
		final Vector3f diskN = new Vector3f(diskZ).cross(diskX).normalize();

		TextureAtlasSprite white = sprite(mc, Blocks.SNOW_BLOCK);
		TextureAtlasSprite[] stuff = new TextureAtlasSprite[STUFF.size()];
		for (int i = 0; i < stuff.length; i++) {
			stuff[i] = sprite(mc, STUFF.get(i));
		}
		PoseStack pose = ctx.poseStack();

		// Behind everything: a wide glow, and the rays, set just past the ball so it covers their middle.
		Vector3f back = new Vector3f(look).mul(radius * 1.2f).add(at);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(GLOW), (p, vc) -> {
			billboard(p, vc, back, right, up, radius * 11f, radius * 11f, 0, heat(0.65f, 0.9f));
			billboard(p, vc, back, right, up, radius * 3.6f, radius * 3.6f, 0, heat(0.15f, 1.6f));
			// A flat flare along the disk.
			billboard(p, vc, back, diskX, upr, radius * 9f, radius * 1.2f, 0, heat(0.25f, 1.2f));
		});
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(RAY), (p, vc) -> {
			for (int i = 0; i < BEAMS; i++) {
				float a = i * 6.2832f / BEAMS + hash(i, 1, 3, 0) * 0.4f + time * 0.03f * (i % 2 == 0 ? 1 : -0.6f);
				float len = radius * (5f + 6f * hash(i, 2, 3, 0)) * (0.85f + 0.15f * (float) Math.sin(time * 0.7f + i));
				float wide = radius * (0.5f + 0.7f * hash(i, 3, 3, 0));
				ray(p, vc, back, right, up, a, radius * 0.6f, len, wide, heat(0.3f + 0.4f * hash(i, 4, 3, 0), 0.8f));
			}
		});
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(PETAL), (p, vc) -> {
			// Silk: long translucent petals swirling in, each with faint pink and green edges.
			for (int i = 0; i < PETALS; i++) {
				float a0 = i * 6.2832f / PETALS + hash(i, 1, 13, 0) * 0.5f + time * 0.22f;
				float reach = 4.5f + 3.5f * hash(i, 2, 13, 0);
				float breath = 0.7f + 0.3f * (float) Math.sin(time * 1.3f + i * 1.7f);
				int main = heat(0.3f + 0.35f * hash(i, 3, 13, 0), 0.85f * breath);
				petal(p, vc, back, right, up, radius, a0 - 0.035f, reach, argb(0.32f * breath, 1f, 0.35f, 0.7f));
				petal(p, vc, back, right, up, radius, a0 + 0.035f, reach, argb(0.25f * breath, 0.35f, 1f, 0.6f));
				petal(p, vc, back, right, up, radius, a0, reach, main);
			}
		});
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(RING), (p, vc) -> {
			// The thin photon ring, hugging the black.
			billboard(p, vc, at, right, up, radius * 1.42f, radius * 1.42f, 0, heat(0.05f, 0.9f));
		});

		// The ball and the falling pieces: solid.
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS), (p, vc) -> {
			Quaternionf none = new Quaternionf();
			for (int[] c : SHELL) {
				cube(p, vc, at.x + c[0] * cell, at.y + c[1] * cell, at.z + c[2] * cell, cell * 0.52f, none, white, 0xFF000000,
						LightCoordsUtil.pack(0, 0));
			}
			int light = LightCoordsUtil.pack(10, 15);
			Quaternionf spin = new Quaternionf();
			Vector3f pos = new Vector3f();
			for (int i = 0; i < PIECES; i++) {
				float h1 = hash(i, 1, 7, 0), h2 = hash(i, 2, 7, 0), h3 = hash(i, 3, 7, 0), h4 = hash(i, 4, 7, 0);
				float period = 9 + 6 * h1;
				float phase = (time / period + h2) % 1f;
				float r = radius * (1.3f + 6f * (float) Math.pow(1 - phase, 1.4));
				// Falling pieces settle onto the disk as they go in.
				float ang = h1 * 6.2832f + phase * (3f + 3f * h2) * (1 + 2f * phase);
				float lift = (h3 - 0.5f) * 2.2f * (1 - phase);
				diskPoint(pos, at, diskX, upr, look, radius, r, ang, lift * radius);
				float size = cell * (0.25f + 0.3f * h4) * Math.min(1, phase / 0.08f) * Math.max(0, Math.min(1, (r - radius * 1.3f) / radius));
				if (size <= 0.001f) {
					continue;
				}
				spin.rotationXYZ(time * (0.6f + h3), time * (0.4f + h4), h1 * 3);
				cube(p, vc, pos.x, pos.y, pos.z, size, spin, stuff[(int) (hash(i, 6, 7, 0) * stuff.length)], 0xFFFFFFFF, light);
			}
		});

		// The disk and its bent far side: glowing cubes.
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(TextureAtlas.LOCATION_BLOCKS), (p, vc) -> {
			Quaternionf rot = new Quaternionf();
			Vector3f pos = new Vector3f();
			float rin = radius * 1.5f, rout = radius * 4.8f;
			for (int i = 0; i < DISK; i++) {
				float h1 = hash(i, 1, 9, 0), h2 = hash(i, 2, 9, 0), h3 = hash(i, 3, 9, 0);
				// More of them inside, where it is hottest.
				float t = (float) Math.pow(h1, 1.5);
				// Rings with darker gaps between them, like Saturn's.
				float band = 0.55f + 0.45f * (float) Math.cos(t * 38f);
				if (hash(i, 5, 9, 0) > band) {
					t = (float) Math.pow(hash(i, 6, 9, 0), 2.5);
				}
				float r = rin + (rout - rin) * t;
				// Kepler: the inner part turns much faster.
				float omega = 0.9f * (float) Math.pow(rin / r, 1.5);
				float ang = h2 * 6.2832f + time * omega;
				diskPoint(pos, at, diskX, upr, look, radius, r, ang, (h3 - 0.5f) * radius * 0.05f * (1 + 2 * t));
				// Brighter on the side that comes at the viewer: the disk turns from left to right in front.
				float doppler = 1 + 0.6f * (float) Math.cos(ang);
				float flick = 0.75f + 0.25f * (float) Math.sin(time * (2 + 3 * h3) + i);
				float bright = (2.6f - 1.6f * t) * doppler * flick;
				float size = cell * (0.1f + 0.1f * t) * (0.6f + 0.6f * h3);
				rot.rotationAxis(ang, diskN.x, diskN.y, diskN.z);
				cube(p, vc, pos.x, pos.y, pos.z, size, rot, white, heat(t, bright), LightCoordsUtil.FULL_BRIGHT);
			}
			// The far side of the disk, bent up over and down under the hole: a ring that faces you.
			for (int i = 0; i < HALO; i++) {
				float h1 = hash(i, 1, 11, 0), h2 = hash(i, 2, 11, 0), h3 = hash(i, 3, 11, 0);
				float t = (float) Math.pow(h1, 1.3) * 0.55f;
				float r = radius * (1.1f + 0.6f * t);
				float ang = h2 * 6.2832f + time * 0.5f * (1 - t);
				float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
				// Thickest over and under the hole, where the disk's back shows.
				float keep = 0.45f + 0.55f * Math.abs(sa);
				if (h3 > keep) {
					continue;
				}
				pos.set(right).mul(ca * r).add(up.x * sa * r, up.y * sa * r, up.z * sa * r).add(at);
				float bright = (1.9f - 1.6f * t) * (0.8f + 0.2f * (float) Math.sin(time * 3 + i));
				float size = cell * (0.13f + 0.18f * t);
				rot.rotationAxis(ang, look.x, look.y, look.z);
				cube(p, vc, pos.x, pos.y, pos.z, size, rot, white, heat(t * 0.6f, bright), LightCoordsUtil.FULL_BRIGHT);
			}
		});
	}

	/**
	 * A point on the disk as the viewer sees it: across ({@code x}), down the screen as far as the
	 * tilt shows it, and in depth. The depth is squeezed, so the near half doesn't fly into the
	 * camera, but the near half still passes in front of the black and the far half behind it.
	 */
	private static void diskPoint(Vector3f out, Vector3f at, Vector3f x, Vector3f up, Vector3f look, float hole, float r,
			float ang, float lift) {
		float across = (float) Math.cos(ang) * r;
		float along = (float) Math.sin(ang) * r;
		float down = along * (float) Math.sin(TILT) - lift;
		float depth = along > 0 ? along * 0.35f
				: -(1.15f * hole * Math.min(1, -along / (0.6f * hole)) - 0.2f * along);
		out.set(at).add(x.x * across - up.x * down + look.x * depth, x.y * across - up.y * down + look.y * depth,
				x.z * across - up.z * down + look.z * depth);
	}

	private static TextureAtlasSprite sprite(Minecraft mc, Block block) {
		return mc.getModelManager().getBlockStateModelSet().getParticleMaterial(block.defaultBlockState()).sprite();
	}

	private static int argb(float bright, float r, float g, float b) {
		return 0xFF000000 | channel(r * bright) << 16 | channel(g * bright) << 8 | channel(b * bright);
	}

	/**
	 * One silk petal: a ribbon facing the viewer that winds out from the hole along a spiral,
	 * widening as it goes, {@code reach} hole radii long.
	 */
	static void petal(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f right, Vector3f up, float hole, float a0,
			float reach, int color) {
		Vector3f n = new Vector3f(right).cross(up).normalize();
		float[] px = new float[PETAL_STEPS + 1], py = new float[PETAL_STEPS + 1], wx = new float[PETAL_STEPS + 1],
				wy = new float[PETAL_STEPS + 1];
		for (int k = 0; k <= PETAL_STEPS; k++) {
			float s = k / (float) PETAL_STEPS;
			float r = hole * (1.05f + reach * s);
			float a = a0 + 1.9f * s;
			float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
			px[k] = ca * r;
			py[k] = sa * r;
			// Across the ribbon: mostly sideways to the spiral, growing outwards.
			float w = hole * (0.5f + 2.8f * s);
			wx[k] = -sa * w;
			wy[k] = ca * w;
		}
		for (int k = 0; k < PETAL_STEPS; k++) {
			float u0 = k / (float) PETAL_STEPS, u1 = (k + 1) / (float) PETAL_STEPS;
			float[][] q = { { px[k] - wx[k] / 2, py[k] - wy[k] / 2, u0, 0 }, { px[k + 1] - wx[k + 1] / 2, py[k + 1] - wy[k + 1] / 2, u1, 0 },
					{ px[k + 1] + wx[k + 1] / 2, py[k + 1] + wy[k + 1] / 2, u1, 1 }, { px[k] + wx[k] / 2, py[k] + wy[k] / 2, u0, 1 } };
			for (int pass = 0; pass < 2; pass++) {
				for (int j = 0; j < 4; j++) {
					float[] v = q[pass == 0 ? j : 3 - j];
					vc.addVertex(p, c.x + right.x * v[0] + up.x * v[1], c.y + right.y * v[0] + up.y * v[1], c.z + right.z * v[0] + up.z * v[1])
							.setColor(color).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
							.setNormal(p, n.x, n.y, n.z);
				}
			}
		}
	}

	/** A flat picture facing the viewer, turned by {@code angle}. */
	static void billboard(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f right, Vector3f up, float w, float hgt,
			float angle, int color) {
		float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
		Vector3f ax = new Vector3f(right).mul(ca).add(up.x * sa, up.y * sa, up.z * sa).mul(w);
		Vector3f ay = new Vector3f(up).mul(ca).sub(right.x * sa, right.y * sa, right.z * sa).mul(hgt);
		quad(p, vc, c, ax, ay, 0, 1, color);
	}

	/** A soft streak from {@code from} out to {@code len} along {@code angle}, facing the viewer. */
	static void ray(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f right, Vector3f up, float angle, float from,
			float len, float wide, int color) {
		float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
		Vector3f dir = new Vector3f(right).mul(ca).add(up.x * sa, up.y * sa, up.z * sa);
		Vector3f side = new Vector3f(up).mul(ca).sub(right.x * sa, right.y * sa, right.z * sa).mul(wide / 2);
		Vector3f mid = new Vector3f(dir).mul(from + len / 2).add(c);
		quad(p, vc, mid, dir.mul(len / 2), side, 0, 1, color);
	}

	/** A quad centred on {@code c} with half axes {@code ax} (u) and {@code ay} (v). */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f ax, Vector3f ay, float u0, float u1, int color) {
		float[][] corners = { { -1, -1, u0, 1 }, { 1, -1, u1, 1 }, { 1, 1, u1, 0 }, { -1, 1, u0, 0 } };
		Vector3f n = new Vector3f(ax).cross(ay).normalize();
		for (int pass = 0; pass < 2; pass++) {
			for (int j = 0; j < 4; j++) {
				float[] q = corners[pass == 0 ? j : 3 - j];
				vc.addVertex(p, c.x + ax.x * q[0] + ay.x * q[1], c.y + ax.y * q[0] + ay.y * q[1], c.z + ax.z * q[0] + ay.z * q[1])
						.setColor(color).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(p, n.x, n.y, n.z);
			}
		}
	}

	private static final float[][] FACES = {
			// normal, then four corners (counter-clockwise from outside)
			{ 0, 1, 0, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1 },
			{ 0, -1, 0, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1 },
			{ 0, 0, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1 },
			{ 0, 0, -1, 1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1 },
			{ 1, 0, 0, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1 },
			{ -1, 0, 0, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1 } };
	private static final float[][] UV = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } };

	/** A cube of half side {@code half} around (x, y, z), turned by {@code rot}. */
	static void cube(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float half, Quaternionf rot,
			TextureAtlasSprite sprite, int color, int light) {
		Vector3f v = new Vector3f();
		Vector3f n = new Vector3f();
		for (float[] f : FACES) {
			n.set(f[0], f[1], f[2]).rotate(rot);
			for (int i = 0; i < 4; i++) {
				v.set(f[3 + i * 3], f[4 + i * 3], f[5 + i * 3]).mul(half).rotate(rot);
				vc.addVertex(p, x + v.x, y + v.y, z + v.z).setColor(color).setUv(sprite.getU(UV[i][0]), sprite.getV(UV[i][1]))
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, n.x, n.y, n.z);
			}
		}
	}
}
