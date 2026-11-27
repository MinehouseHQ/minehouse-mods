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
 * <li>a dark oval at the back of the slab is what is seen where the room has nothing, and a rim
 * round it stands in for the picture's.</li>
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
	/** The oval's mean edge, as a share of the window's half size (End core/item.fsh, models/build_end_window.py). */
	static final float EDGE = 0.9f;
	static final int SIDES = 32;
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
		final float a, b;
		/** The slab behind the plane the room is squeezed into: d -> d / (alpha d + beta). */
		final float alpha, beta, slab;
		/** The planes through the eye along the oval's edge: x, y, z each, inward. */
		final float[] edges = new float[SIDES * 3];
		/** The oval's corners on its plane, x, y, z each. */
		final float[] ring = new float[SIDES * 3];
		/** From the snapshot's coordinates to camera-relative ones. */
		final float ox, oy, oz;
		private final float[] bufA = new float[(SIDES + 8) * 2 * F];
		private final float[] bufB = new float[(SIDES + 8) * 2 * F];

		private Cut(float cx, float cy, float cz, float nx, float nz, float d0, float a, float b, float ox, float oy, float oz) {
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
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.slab = 0.03f + 0.01f * d0;
			this.alpha = 1f / (d0 + slab);
			this.beta = slab / (d0 + slab);
			for (int k = 0; k < SIDES; k++) {
				double t = k * Math.PI * 2 / SIDES;
				float ex = (float) Math.cos(t) * a, ey = (float) Math.sin(t) * b;
				ring[k * 3] = cx + rx * ex;
				ring[k * 3 + 1] = cy + ey;
				ring[k * 3 + 2] = cz + rz * ex;
			}
			for (int k = 0; k < SIDES; k++) {
				int j = (k + 1) % SIDES;
				float ax = ring[k * 3], ay = ring[k * 3 + 1], az = ring[k * 3 + 2];
				float bx = ring[j * 3], by = ring[j * 3 + 1], bz = ring[j * 3 + 2];
				float mx = ay * bz - az * by, my = az * bx - ax * bz, mz = ax * by - ay * bx;
				if (mx * cx + my * cy + mz * cz < 0) {
					mx = -mx;
					my = -my;
					mz = -mz;
				}
				edges[k * 3] = mx;
				edges[k * 3 + 1] = my;
				edges[k * 3 + 2] = mz;
			}
		}

		/** Null: the eye is in the window (about to be moved through it) or the window is behind it. */
		static Cut of(Window w, Vec3 cam, float scale) {
			WindowPayloads.View v = w.view;
			double cx = v.bx() - cam.x, cy = v.by() + v.height() / 2.0 - cam.y, cz = v.bz() - cam.z;
			double flat = Math.sqrt(cx * cx + cz * cz);
			if (flat < 0.35) {
				return null;
			}
			float a = v.width() / 2 * EDGE * scale, b = v.height() / 2 * EDGE * scale;
			return new Cut((float) cx, (float) cy, (float) cz, (float) (cx / flat), (float) (cz / flat), (float) flat, a, b,
					(float) (v.bx() - cam.x), (float) (v.by() - cam.y), (float) (v.bz() - cam.z));
		}

		/** The dark back of the oval and the rim round it. */
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
			// The rim: three bands, bright inside to violet outside, a little in front of the plane, wobbling slowly.
			float time = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
			float front = (d0 - 0.002f) / d0;
			band(p, vc, u, v, front, 1.0f, 1.025f, RIM_INNER, time, 0);
			band(p, vc, u, v, front, 1.025f, 1.055f, RIM_MID, time, 1.3f);
			band(p, vc, u, v, front, 1.055f, 1.085f, RIM_OUTER, time, 2.6f);
		}

		private void band(PoseStack.Pose p, VertexConsumer vc, float u, float v, float front, float from, float to, int[] rgb, float time, float phase) {
			int color = 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
			for (int k = 0; k < SIDES; k++) {
				int j = (k + 1) % SIDES;
				float wk = 1 + 0.012f * (float) Math.sin(time * 2.1f + k * 0.9f + phase);
				float wj = 1 + 0.012f * (float) Math.sin(time * 2.1f + j * 0.9f + phase);
				float[] p0 = edgePoint(k, from * wk, front), p1 = edgePoint(j, from * wj, front);
				float[] p2 = edgePoint(j, to * wj, front), p3 = edgePoint(k, to * wk, front);
				both(p, vc, u, v, color, p0, p1, p2, p3);
			}
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

		private float[] edgePoint(int k, float grow, float front) {
			float x = cx + (ring[k * 3] - cx) * grow, y = cy + (ring[k * 3 + 1] - cy) * grow, z = cz + (ring[k * 3 + 2] - cz) * grow;
			return new float[] { x * front, y * front, z * front };
		}

		/** The faces of the room that the oval lets through, squeezed into the slab. */
		void room(PoseStack.Pose p, VertexConsumer vc, List<Quad> quads) {
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
				float[] cur = bufB;
				float[] other = bufA;
				// Quick looks: where on the window's plane each corner is seen.
				boolean allIn = true, right = true, left = true, up = true, down = true;
				for (int i = 0; i < n; i++) {
					float x = cur[i * F], y = cur[i * F + 1], z = cur[i * F + 2];
					float d = x * nx + z * nz;
					float k = d0 / Math.max(d, 1e-4f);
					float ex = (x * k - cx) * rx + (z * k - cz) * rz;
					float ey = y * k - cy;
					float e = ex * ex / (a * a) + ey * ey / (b * b);
					allIn &= e < 0.985f;
					right &= ex > a;
					left &= ex < -a;
					up &= ey > b;
					down &= ey < -b;
				}
				if (right || left || up || down) {
					continue;
				}
				if (!allIn) {
					for (int k = 0; k < SIDES && n >= 3; k++) {
						n = clip(cur, n, other, edges[k * 3], edges[k * 3 + 1], edges[k * 3 + 2], 0);
						float[] t = cur;
						cur = other;
						other = t;
					}
					if (n < 3) {
						continue;
					}
				}
				// Squeeze along the rays from the eye, then hand on as a fan of (degenerate) quads.
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
