package pro.minehouse.riftclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A see-through rift window, for players with this mod (Timur, 27.11: «сделать breach сквозным»,
 * like Immersive Portals; ideas from qouteall's Immersive Portals, Apache 2.0, none of its code).
 *
 * <p>The End mod sends the blocks round the spot a window leads to ({@link WindowPayloads.View}):
 * this client only ever has the level it stands in, so it can't see the dungeon's own chunks. A
 * window moves a player by a plain shift, so the room behind it is that snapshot shifted back by
 * the same amount. Every frame it is cut to what the oval lets through and drawn there; the
 * server's flat picture of the window is not drawn meanwhile.
 *
 * <p>How it is cut, with nothing but the game's own entity shaders (they work with Sodium, and
 * there is no stencil to lean on):
 * <ul>
 * <li>the window always turns to the viewer (a VERTICAL billboard), so it is a flat oval across
 * the view; each face of the room is clipped on the CPU to the cone from the eye through that oval
 * (a 32-gon) and to the far side of its plane;</li>
 * <li>then squeezed along the rays from the eye into a thin slab just behind the window plane, by
 * a projective map (planes stay planes, every point stays where it is on the screen). So the room
 * keeps its own order in depth, things in front of the window still hide it, and the world behind
 * the window (a hill, a wall) does not show through it;</li>
 * <li>a dark oval at the back of the slab is what is seen where the room has nothing; the rim is
 * the picture's own (End core/item.fsh), drawn block pixel by block pixel on the window's plane,
 * wobbling the same way (Timur, 28.11: «такую же рамку как была раньше»), and the room is cut to
 * its outline, slice by slice, since a wobbling oval is not convex.</li>
 * </ul>
 *
 * <p>Walking through: the server moves the player into the dungeon's dimension, which brings up the
 * loading screen. If that happens right after standing in a window this client knows, the screen is
 * skipped (see mixin.WindowLoadingScreenMixin) and the snapshot is drawn where it really is, until
 * the real chunks are there.
 *
 * <p>With an Iris shader pack this stays off and the server's picture is drawn as before.
 */
public final class PortalWindows {
	/** Corners of the cut round the oval (it wobbles in 9 waves, Cut.edge). */
	static final int SIDES = 64;
	/** Floats per vertex while clipping: x, y, z, u, v. */
	private static final int F = 5;
	/** After standing in a window this long, a new level of its target is taken for walking through it. */
	private static final long ARRIVE_WITHIN = 3_000_000_000L;
	private static final long FILL_FOR = 5_000_000_000L;
	private static final int[] RIM_INNER = argb(1f, 0.95f, 0.82f, 1.0f);
	private static final int[] RIM_MID = argb(1f, 0.7f, 0.45f, 0.98f);
	private static final int[] RIM_OUTER = argb(1f, 0.45f, 0.15f, 0.85f);
	/** Where the room has nothing: the dungeon's void. */
	private static final int BACKGROUND = 0xFF0C0616;
	private static final Direction[] DIRS = { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

	/** A face of the room, relative to the spot the window leads to. */
	record Quad(float[] xyzuv, int color, int light) {
	}

	static final class Window {
		final WindowPayloads.View view;
		final List<Quad> solid;
		final List<Quad> translucent;
		float scaleFrom;
		float scaleTo;
		long scaleStart;
		long scaleNanos;
		/** When this player last stood in it. */
		long inAt;

		Window(WindowPayloads.View view, List<Quad> solid, List<Quad> translucent) {
			this.view = view;
			this.solid = solid;
			this.translucent = translucent;
			this.scaleFrom = 0;
			this.scaleTo = view.scale();
			this.scaleStart = System.nanoTime();
			this.scaleNanos = 400_000_000L;
		}

		float scale() {
			if (scaleNanos <= 0) {
				return scaleTo;
			}
			float t = Math.min(1f, (System.nanoTime() - scaleStart) / (float) scaleNanos);
			return scaleFrom + (scaleTo - scaleFrom) * t;
		}

		Vec3 base() {
			return new Vec3(view.bx(), view.by(), view.bz());
		}
	}

	private static final Map<Integer, Window> WINDOWS = new HashMap<>();
	/** The window just walked through, while the real room loads, and since when. */
	private static Window arrived;
	private static long arrivedAt;
	private static int readyTicks;
	/** Entity ids of the pictures drawn this frame instead (by the last submit). */
	private static final java.util.Set<Integer> HIDDEN = new java.util.HashSet<>();
	private static Boolean shaders;
	private static long shadersCheckedAt;

	private PortalWindows() {
	}

	// ------------------------------------------------------------------ packets

	static void receive(WindowPayloads.View view) {
		try {
			Window w = build(view);
			Window old = WINDOWS.put(view.id(), w);
			if (old != null) {
				w.inAt = old.inAt;
			}
			MinehouseRiftClient.LOG.info("Rift window {}: {} faces behind it", view.id(), w.solid.size() + w.translucent.size());
		} catch (RuntimeException | DataFormatException e) {
			MinehouseRiftClient.LOG.warn("Rift window {}: could not read the room behind it", view.id(), e);
		}
	}

	static void receive(WindowPayloads.Scale scale) {
		Window w = WINDOWS.get(scale.id());
		if (w == null) {
			return;
		}
		w.scaleFrom = w.scale();
		w.scaleTo = scale.scale();
		w.scaleStart = System.nanoTime();
		w.scaleNanos = scale.ticks() * 50_000_000L;
	}

	static void receive(WindowPayloads.Gone gone) {
		Window w = WINDOWS.remove(gone.id());
		if (w != null && arrived == w) {
			arrived = null;
		}
	}

	static void clear() {
		WINDOWS.clear();
		HIDDEN.clear();
		arrived = null;
	}

	// ------------------------------------------------------------------ the room

	static Window build(WindowPayloads.View v) throws DataFormatException {
		int sx = v.sizeX(), sy = v.sizeY(), sz = v.sizeZ();
		int count = sx * sy * sz;
		byte[] raw = new byte[count * 3];
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(v.blocks());
			int n = 0;
			while (n < raw.length && !inflater.finished()) {
				int got = inflater.inflate(raw, n, raw.length - n);
				if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					break;
				}
				n += got;
			}
			if (n != raw.length) {
				throw new DataFormatException("room data is " + n + " bytes, not " + raw.length);
			}
		} finally {
			inflater.end();
		}
		BlockState[] palette = new BlockState[v.palette().length];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = Block.stateById(v.palette()[i]);
		}
		BlockState[] states = new BlockState[count];
		for (int i = 0; i < count; i++) {
			int p = (raw[i * 2] & 0xFF) << 8 | raw[i * 2 + 1] & 0xFF;
			states[i] = p < palette.length ? palette[p] : Blocks.AIR.defaultBlockState();
		}
		Minecraft mc = Minecraft.getInstance();
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		RandomSource random = RandomSource.create();
		List<BlockStateModelPart> parts = new ArrayList<>();
		List<Quad> solid = new ArrayList<>();
		List<Quad> translucent = new ArrayList<>();
		// Relative to the spot: the block coordinates minus the spot's.
		double ox = v.minX() - v.tx(), oy = v.minY() - v.ty(), oz = v.minZ() - v.tz();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					int i = (y * sz + z) * sx + x;
					BlockState state = states[i];
					if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
						continue;
					}
					pos.set(v.minX() + x, v.minY() + y, v.minZ() + z);
					BlockStateModel model = models.get(state);
					random.setSeed(state.getSeed(pos));
					parts.clear();
					model.collectParts(random, parts);
					int ownLight = raw[count * 2 + i] & 0xFF;
					for (BlockStateModelPart part : parts) {
						for (Direction dir : DIRS) {
							BlockState next = air;
							int light = ownLight;
							if (dir != null) {
								int nx = x + dir.getStepX(), ny = y + dir.getStepY(), nz = z + dir.getStepZ();
								if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sx && ny < sy && nz < sz) {
									int j = (ny * sz + nz) * sx + nx;
									next = states[j];
									light = Math.max(light >> 4, (raw[count * 2 + j] & 0xFF) >> 4) << 4
											| Math.max(light & 15, raw[count * 2 + j] & 15);
								} else {
									light = 0xF0 | light & 15;
								}
							}
							List<BakedQuad> quads = part.getQuads(dir);
							if (quads.isEmpty() || dir != null && !Block.shouldRenderFace(state, next, dir)) {
								continue;
							}
							for (BakedQuad quad : quads) {
								BakedQuad.MaterialInfo info = quad.materialInfo();
								int block = Math.max(light & 15, Math.max(info.lightEmission(), state.getLightEmission()));
								int packed = LightCoordsUtil.pack(block, light >> 4);
								int tint = info.isTinted() ? tint(mc, state, info.tintIndex()) : 0xFFFFFF;
								int color = shade(tint, quad.direction());
								float[] xyzuv = new float[4 * F];
								for (int k = 0; k < 4; k++) {
									var p = quad.position(k);
									long uv = quad.packedUV(k);
									xyzuv[k * F] = (float) (ox + x + p.x());
									xyzuv[k * F + 1] = (float) (oy + y + p.y());
									xyzuv[k * F + 2] = (float) (oz + z + p.z());
									xyzuv[k * F + 3] = UVPair.unpackU(uv);
									xyzuv[k * F + 4] = UVPair.unpackV(uv);
								}
								(info.layer() == ChunkSectionLayer.TRANSLUCENT ? translucent : solid).add(new Quad(xyzuv, color, packed));
							}
						}
					}
				}
			}
		}
		return new Window(v, solid, translucent);
	}

	private static int tint(Minecraft mc, BlockState state, int index) {
		try {
			BlockTintSource source = mc.getBlockColors().getTintSource(state, index);
			return source == null ? 0xFFFFFF : source.color(state) & 0xFFFFFF;
		} catch (RuntimeException e) {
			return 0xFFFFFF;
		}
	}

	/** The terrain's own shading by face, baked in: the entity shader is given an upward normal everywhere. */
	private static int shade(int rgb, Direction dir) {
		float s = dir == null ? 1f : switch (dir) {
			case DOWN -> 0.5f;
			case UP -> 1f;
			case NORTH, SOUTH -> 0.8f;
			case WEST, EAST -> 0.6f;
		};
		int r = (int) ((rgb >> 16 & 255) * s), g = (int) ((rgb >> 8 & 255) * s), b = (int) ((rgb & 255) * s);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ every tick

	static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		String dim = mc.level.dimension().identifier().toString();
		Vec3 feet = mc.player.position();
		long now = System.nanoTime();
		for (Window w : WINDOWS.values()) {
			if (!w.view.world().toString().equals(dim)) {
				continue;
			}
			Vec3 b = w.base();
			double dx = feet.x - b.x, dz = feet.z - b.z;
			if (dx * dx + dz * dz < 2.5 * 2.5 && feet.y > b.y - 2 && feet.y < b.y + w.view.height()) {
				w.inAt = now;
			}
		}
		if (arrived != null) {
			if (!arrived.view.target().toString().equals(dim) || now - arrivedAt > FILL_FOR) {
				arrived = null;
			} else {
				int cx = feet.x < 0 ? (int) Math.floor(feet.x) >> 4 : (int) feet.x >> 4;
				int cz = feet.z < 0 ? (int) Math.floor(feet.z) >> 4 : (int) feet.z >> 4;
				boolean ready = true;
				for (int i = -1; i <= 1 && ready; i++) {
					for (int j = -1; j <= 1 && ready; j++) {
						ready = mc.level.getChunkSource().hasChunk(cx + i, cz + j);
					}
				}
				// The chunks are in; give their meshes a moment to be built before the stand-in goes.
				readyTicks = ready ? readyTicks + 1 : 0;
				if (readyTicks > 12) {
					arrived = null;
				}
			}
		}
	}

	/**
	 * Called as the game is about to show the loading screen for a new level: true (skip it) when
	 * the new level is where a window this player just stood in leads.
	 */
	public static boolean walkedThrough(ClientLevel level) {
		if (level == null) {
			return false;
		}
		String dim = level.dimension().identifier().toString();
		long now = System.nanoTime();
		for (Window w : WINDOWS.values()) {
			if (w.view.target().toString().equals(dim) && now - w.inAt < ARRIVE_WITHIN) {
				arrived = w;
				arrivedAt = now;
				readyTicks = 0;
				MinehouseRiftClient.LOG.info("Rift window {}: walked through, no loading screen", w.view.id());
				return true;
			}
		}
		return false;
	}

	/** The server's picture of a window is not drawn while this draws the room through it. */
	public static boolean hidden(Entity e) {
		return !HIDDEN.isEmpty() && HIDDEN.contains(e.getId());
	}

	static boolean shaderPack() {
		long now = System.nanoTime();
		if (shaders != null && now - shadersCheckedAt < 2_000_000_000L) {
			return shaders;
		}
		shadersCheckedAt = now;
		boolean on = false;
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object instance = api.getMethod("getInstance").invoke(null);
				on = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				on = true; // unknown Iris: leave the picture to it
			}
		}
		shaders = on;
		return on;
	}

	// ------------------------------------------------------------------ every frame

	static void submit(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		HIDDEN.clear();
		if (mc.level == null || WINDOWS.isEmpty()) {
			return;
		}
		String dim = mc.level.dimension().identifier().toString();
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		PoseStack pose = ctx.poseStack();
		TextureAtlasSprite white = mc.getModelManager().getBlockStateModelSet().getParticleMaterial(Blocks.SNOW_BLOCK.defaultBlockState()).sprite();
		Window fill = arrived;
		if (fill != null && fill.view.target().toString().equals(dim)) {
			// Just walked through: the room where it really is, until the real one has loaded. A hair
			// further away than it is, so the real blocks win where both are there.
			final float ox = (float) (fill.view.tx() - cam.x), oy = (float) (fill.view.ty() - cam.y), oz = (float) (fill.view.tz() - cam.z);
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS),
					(p, vc) -> drawPlain(p, vc, fill.solid, ox, oy, oz));
			if (!fill.translucent.isEmpty()) {
				ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
						(p, vc) -> drawPlain(p, vc, fill.translucent, ox, oy, oz));
			}
		}
		if (shaderPack()) {
			return;
		}
		for (Window w : WINDOWS.values()) {
			if (!w.view.world().toString().equals(dim)) {
				continue;
			}
			Entity picture = mc.level.getEntity(w.view.entity());
			if (picture == null) {
				continue;
			}
			float s = w.scale();
			if (s < 0.05f) {
				continue;
			}
			Cut cut = Cut.of(w, cam, s);
			if (cut == null) {
				continue;
			}
			HIDDEN.add(w.view.entity());
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS),
					(p, vc) -> cut.frame(p, vc, white));
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS),
					(p, vc) -> cut.room(p, vc, w.solid));
			if (!w.translucent.isEmpty()) {
				ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
						(p, vc) -> cut.room(p, vc, w.translucent));
			}
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
					(p, vc) -> cut.glow(p, vc, white));
		}
	}

	private static void drawPlain(PoseStack.Pose p, VertexConsumer vc, List<Quad> quads, float ox, float oy, float oz) {
		final float k = 1.003f;
		for (Quad q : quads) {
			float[] a = q.xyzuv();
			for (int i = 0; i < 4; i++) {
				vertex(p, vc, (a[i * F] + ox) * k, (a[i * F + 1] + oy) * k, (a[i * F + 2] + oz) * k, a[i * F + 3], a[i * F + 4], q.color(), q.light());
			}
		}
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int color, int light) {
		vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
	}

	/**
	 * One window seen from one eye this frame: the oval's plane and edge, camera-relative, and the
	 * squeeze into the slab behind it.
	 */
	static final class Cut {
		/** Window centre, camera-relative. */
		final float cx, cy, cz;
		/** The way from the eye to the window, flat, and across it. */
		final float nx, nz, rx, rz;
		/** Distance from the eye to the window's plane. */
		final float d0;
		/** The window's half width and half height (as grown or shrunk now). */
		final float a, b;
		/** Block pixels from the middle to the side, across and up (16 to a block, as in End core/item.fsh). */
		final float gx, gy;
		/** The slab behind the plane the room is squeezed into: d -> d / (alpha d + beta). */
		final float alpha, beta, slab;
		/** Seconds, as the picture's wobble runs (GameTime * 1200 in core/item.fsh). */
		final float time;
		/** Where the room is cut off, round the oval: a share of the half size, at each of SIDES angles. */
		final float[] rad = new float[SIDES];
		/** Those points on the window's plane, x, y, z each. */
		final float[] ring = new float[SIDES * 3];
		/** Each slice of the oval (middle, ring k, ring k + 1) as three planes through the eye, x, y, z each, inward. */
		final float[] slices = new float[SIDES * 9];
		/** Every slice reaches at least this far out, and none further than rMax. */
		final float rMin, rMax;
		/** From the snapshot's coordinates to camera-relative ones. */
		final float ox, oy, oz;
		private final float[] bufA = new float[(SIDES + 8) * 2 * F];
		private final float[] bufB = new float[(SIDES + 8) * 2 * F];
		private final float[] bufC = new float[(SIDES + 8) * 2 * F];

		private Cut(float cx, float cy, float cz, float nx, float nz, float d0, float a, float b, float gx, float gy, float time,
				float ox, float oy, float oz) {
			this.cx = cx;
			this.cy = cy;
			this.cz = cz;
			this.nx = nx;
			this.nz = nz;
			this.rx = -nz;
			this.rz = nx;
			this.d0 = d0;
			this.a = a;
			this.b = b;
			this.gx = gx;
			this.gy = gy;
			this.time = time;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.slab = 0.03f + 0.01f * d0;
			this.alpha = 1f / (d0 + slab);
			this.beta = slab / (d0 + slab);
			// The room ends a pixel and a half inside the rim's outline, under its solid bands.
			float inset = 1.5f / Math.min(gx, gy);
			float lo = 1, hi = 0;
			for (int k = 0; k < SIDES; k++) {
				double t = k * Math.PI * 2 / SIDES;
				float r = edge((float) t, time) - inset;
				rad[k] = r;
				lo = Math.min(lo, r);
				hi = Math.max(hi, r);
				float ex = (float) Math.cos(t) * a * r, ey = (float) Math.sin(t) * b * r;
				ring[k * 3] = cx + rx * ex;
				ring[k * 3 + 1] = cy + ey;
				ring[k * 3 + 2] = cz + rz * ex;
			}
			// Between two ring points the cut runs straight, a little inside them.
			this.rMin = lo * (float) Math.cos(Math.PI * 2 / SIDES) * 0.98f;
			this.rMax = hi;
			for (int k = 0; k < SIDES; k++) {
				int j = (k + 1) % SIDES;
				plane(slices, k * 9, cx, cy, cz, ring, k * 3, ring, j * 3);
				plane(slices, k * 9 + 3, cx, cy, cz, ring, j * 3, ring, k * 3);
				float[] mid = { cx, cy, cz };
				planeThrough(slices, k * 9 + 6, ring, k * 3, ring, j * 3, mid, 0);
			}
		}

		/** The plane through the eye, the middle and ring point at p, facing ring point at q. */
		private static void plane(float[] out, int o, float cx, float cy, float cz, float[] ring, int p, float[] ring2, int q) {
			float px = ring[p], py = ring[p + 1], pz = ring[p + 2];
			float mx = cy * pz - cz * py, my = cz * px - cx * pz, mz = cx * py - cy * px;
			if (mx * ring2[q] + my * ring2[q + 1] + mz * ring2[q + 2] < 0) {
				mx = -mx;
				my = -my;
				mz = -mz;
			}
			out[o] = mx;
			out[o + 1] = my;
			out[o + 2] = mz;
		}

		/** The plane through the eye and the points at p and q, facing the point at m. */
		private static void planeThrough(float[] out, int o, float[] a, int p, float[] b, int q, float[] c, int m) {
			float ax = a[p], ay = a[p + 1], az = a[p + 2];
			float bx = b[q], by = b[q + 1], bz = b[q + 2];
			float mx = ay * bz - az * by, my = az * bx - ax * bz, mz = ax * by - ay * bx;
			if (mx * c[m] + my * c[m + 1] + mz * c[m + 2] < 0) {
				mx = -mx;
				my = -my;
				mz = -mz;
			}
			out[o] = mx;
			out[o + 1] = my;
			out[o + 2] = mz;
		}

		/**
		 * The oval's wobbling outline at this angle, as a share of the half size: exactly the
		 * picture's (End core/item.fsh), so the window looks as it did (Timur, 28.11).
		 */
		static float edge(float ang, float time) {
			return 0.9f + 0.035f * (float) Math.sin(ang * 5.0 + time * 1.7) + 0.025f * (float) Math.sin(ang * 9.0 - time * 2.3)
					+ 0.02f * (float) Math.sin(ang * 3.0 + time * 0.9);
		}

		/** Null: the eye is in the window (about to be moved through it) or the window is behind it. */
		static Cut of(Window w, Vec3 cam, float scale) {
			WindowPayloads.View v = w.view;
			double cx = v.bx() - cam.x, cy = v.by() + v.height() / 2.0 - cam.y, cz = v.bz() - cam.z;
			double flat = Math.sqrt(cx * cx + cz * cz);
			if (flat < 0.35) {
				return null;
			}
			float a = v.width() / 2 * scale, b = v.height() / 2 * scale;
			float time = (float) ((System.nanoTime() / 1e9) % 1200.0);
			return new Cut((float) cx, (float) cy, (float) cz, (float) (cx / flat), (float) (cz / flat), (float) flat, a, b,
					v.width() * 8, v.height() * 8, time, (float) (v.bx() - cam.x), (float) (v.by() - cam.y), (float) (v.bz() - cam.z));
		}

		/** The dark back of the oval, and the solid bands of the rim. */
		void frame(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite white) {
			float u = white.getU(0.5f), v = white.getV(0.5f);
			// The back: at the far side of the slab, just short of where the room ends up at infinity.
			float back = (d0 + slab * 0.97f) / d0;
			for (int k = 0; k < SIDES; k += 2) {
				// A fan as quads: the middle, then three corners.
				int k1 = (k + 1) % SIDES, k2 = (k + 2) % SIDES;
				both(p, vc, u, v, BACKGROUND,
						new float[] { cx * back, cy * back, cz * back },
						new float[] { ring[k2 * 3] * back, ring[k2 * 3 + 1] * back, ring[k2 * 3 + 2] * back },
						new float[] { ring[k1 * 3] * back, ring[k1 * 3 + 1] * back, ring[k1 * 3 + 2] * back },
						new float[] { ring[k * 3] * back, ring[k * 3 + 1] * back, ring[k * 3 + 2] * back });
			}
			rim(p, vc, u, v, false);
		}

		/** The rim's dim violet glow just inside the bands, over the room. */
		void glow(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite white) {
			rim(p, vc, white.getU(0.5f), white.getV(0.5f), true);
		}

		/**
		 * The picture's rim, block pixel by block pixel (End core/item.fsh): one pixel each of pale,
		 * light violet and violet in from the wobbling outline, then two of violet at half strength.
		 */
		private void rim(PoseStack.Pose p, VertexConsumer vc, float u, float v, boolean glow) {
			float g = Math.min(gx, gy);
			float front = (d0 - 0.002f) / d0;
			int x0 = (int) Math.floor(-gx), x1 = (int) Math.ceil(gx);
			int y0 = (int) Math.floor(-gy), y1 = (int) Math.ceil(gy);
			for (int j = y0; j < y1; j++) {
				float qy = (j + 0.5f) / gy;
				for (int i = x0; i < x1; i++) {
					float qx = (i + 0.5f) / gx;
					float rq = (float) Math.sqrt(qx * qx + qy * qy);
					if (rq < 0.65f || rq > 1f) {
						continue;
					}
					float e = edge((float) Math.atan2(qy, qx), time);
					if (rq > e) {
						continue;
					}
					float px = (e - rq) * g;
					int color;
					if (px >= 5) {
						continue;
					} else if (px >= 3) {
						if (!glow) {
							continue;
						}
						color = 0x80000000 | RIM_OUTER[0] << 16 | RIM_OUTER[1] << 8 | RIM_OUTER[2];
					} else {
						if (glow) {
							continue;
						}
						int[] c = px < 1 ? RIM_INNER : px < 2 ? RIM_MID : RIM_OUTER;
						color = 0xFF000000 | c[0] << 16 | c[1] << 8 | c[2];
					}
					float ax = i / gx * a, bx = (i + 1) / gx * a, ay = j / gy * b, by = (j + 1) / gy * b;
					both(p, vc, u, v, color, onPlane(ax, ay, front), onPlane(bx, ay, front), onPlane(bx, by, front), onPlane(ax, by, front));
				}
			}
		}

		private float[] onPlane(float ex, float ey, float front) {
			return new float[] { (cx + rx * ex) * front, (cy + ey) * front, (cz + rz * ex) * front };
		}

		/** A flat quad seen from either side (the render type may cull back faces). */
		private static void both(PoseStack.Pose p, VertexConsumer vc, float u, float v, int color, float[]... c) {
			int full = LightCoordsUtil.FULL_BRIGHT;
			for (int i = 0; i < 4; i++) {
				vertex(p, vc, c[i][0], c[i][1], c[i][2], u, v, color, full);
			}
			for (int i = 3; i >= 0; i--) {
				vertex(p, vc, c[i][0], c[i][1], c[i][2], u, v, color, full);
			}
		}

		/** The faces of the room that the oval lets through, squeezed into the slab. */
		void room(PoseStack.Pose p, VertexConsumer vc, List<Quad> quads) {
			float step = (float) (Math.PI * 2 / SIDES);
			for (Quad q : quads) {
				float[] src = q.xyzuv();
				float[] in = bufA;
				int n = 4;
				for (int i = 0; i < 4; i++) {
					in[i * F] = src[i * F] + ox;
					in[i * F + 1] = src[i * F + 1] + oy;
					in[i * F + 2] = src[i * F + 2] + oz;
					in[i * F + 3] = src[i * F + 3];
					in[i * F + 4] = src[i * F + 4];
				}
				// Behind the window's plane only.
				n = clip(in, n, bufB, nx, 0, nz, d0);
				if (n < 3) {
					continue;
				}
				// Quick looks: where on the window's plane each corner is seen, as a share of the half size.
				boolean allIn = true, right = true, left = true, up = true, down = true;
				float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
				float first = 0, lo = 0, hi = 0;
				for (int i = 0; i < n; i++) {
					float x = bufB[i * F], y = bufB[i * F + 1], z = bufB[i * F + 2];
					float d = x * nx + z * nz;
					float k = d0 / Math.max(d, 1e-4f);
					float ux = ((x * k - cx) * rx + (z * k - cz) * rz) / a;
					float uy = (y * k - cy) / b;
					allIn &= ux * ux + uy * uy < rMin * rMin;
					right &= ux > rMax;
					left &= ux < -rMax;
					up &= uy > rMax;
					down &= uy < -rMax;
					minX = Math.min(minX, ux);
					maxX = Math.max(maxX, ux);
					minY = Math.min(minY, uy);
					maxY = Math.max(maxY, uy);
					float ang = (float) Math.atan2(uy, ux);
					if (i == 0) {
						first = ang;
					} else {
						float dAng = ang - first;
						dAng -= (float) (Math.PI * 2) * Math.round(dAng / (Math.PI * 2));
						lo = Math.min(lo, dAng);
						hi = Math.max(hi, dAng);
					}
				}
				if (right || left || up || down) {
					continue;
				}
				if (allIn) {
					squeezeAndEmit(p, vc, bufB, n, q);
					continue;
				}
				// The oval wobbles, so it is not convex: cut by each slice it may reach (each one is).
				int s0, s1;
				if (minX <= 0 && maxX >= 0 && minY <= 0 && maxY >= 0) {
					s0 = 0;
					s1 = SIDES - 1;
				} else {
					s0 = (int) Math.floor((first + lo) / step);
					s1 = (int) Math.floor((first + hi) / step);
				}
				for (int s = s0; s <= s1; s++) {
					int k = Math.floorMod(s, SIDES);
					System.arraycopy(bufB, 0, bufC, 0, n * F);
					float[] cur = bufC, other = bufA;
					int m = n;
					for (int c = 0; c < 3 && m >= 3; c++) {
						m = clip(cur, m, other, slices[k * 9 + c * 3], slices[k * 9 + c * 3 + 1], slices[k * 9 + c * 3 + 2], 0);
						float[] t = cur;
						cur = other;
						other = t;
					}
					if (m >= 3) {
						squeezeAndEmit(p, vc, cur, m, q);
					}
				}
			}
		}

		/** Squeeze along the rays from the eye, then hand on as a fan of (degenerate) quads. */
		private void squeezeAndEmit(PoseStack.Pose p, VertexConsumer vc, float[] cur, int n, Quad q) {
			for (int i = 0; i < n; i++) {
				float d = cur[i * F] * nx + cur[i * F + 2] * nz;
				float k = 1f / (alpha * d + beta);
				cur[i * F] *= k;
				cur[i * F + 1] *= k;
				cur[i * F + 2] *= k;
			}
			for (int i = 1; i + 1 < n; i++) {
				emit(p, vc, cur, 0, q);
				emit(p, vc, cur, i, q);
				emit(p, vc, cur, i + 1, q);
				emit(p, vc, cur, i + 1, q);
			}
		}

		private static void emit(PoseStack.Pose p, VertexConsumer vc, float[] buf, int i, Quad q) {
			vertex(p, vc, buf[i * F], buf[i * F + 1], buf[i * F + 2], buf[i * F + 3], buf[i * F + 4], q.color(), q.light());
		}

		/** Sutherland-Hodgman: keeps the part of the polygon where (x, y, z) . (px, py, pz) >= d. */
		static int clip(float[] in, int n, float[] out, float px, float py, float pz, float d) {
			int m = 0;
			int max = out.length / F;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				float si = in[i * F] * px + in[i * F + 1] * py + in[i * F + 2] * pz - d;
				float sj = in[j * F] * px + in[j * F + 1] * py + in[j * F + 2] * pz - d;
				if (si >= 0 && m < max) {
					System.arraycopy(in, i * F, out, m * F, F);
					m++;
				}
				if ((si >= 0) != (sj >= 0) && m < max) {
					float t = si / (si - sj);
					for (int c = 0; c < F; c++) {
						out[m * F + c] = in[i * F + c] + (in[j * F + c] - in[i * F + c]) * t;
					}
					m++;
				}
			}
			return m;
		}
	}

	private static int[] argb(float bright, float r, float g, float b) {
		return new int[] { Math.min(255, (int) (r * bright * 255)), Math.min(255, (int) (g * bright * 255)), Math.min(255, (int) (b * bright * 255)) };
	}

	// ------------------------------------------------------------------ for the self-test

	static int windows() {
		return WINDOWS.size();
	}

	static Window window(int id) {
		return WINDOWS.get(id);
	}
}
