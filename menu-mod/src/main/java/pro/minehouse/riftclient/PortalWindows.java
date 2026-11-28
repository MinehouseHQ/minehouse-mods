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
 * <p>The window is drawn as broken violet glass, like the rift into the mini-game but in 3D
 * (Timur, 28.11: «все разломы как рифт в миниигру, фиолетовые, объёмные, в осколках видно мир, куда
 * он ведёт»; «в трёх плоскостях, осколки разлетаются во все стороны»): see {@link Shards} for the
 * pieces and {@link ShardCut} for how the room is cut to them, with nothing but the game's own
 * entity shaders (they work with Sodium, and there is no stencil to lean on).
 *
 * <p>The rift into the mini-game at the spawn (rift-mod's animated picture) is drawn the same way;
 * this client has nothing of the place it leads to, so its pieces show the violet deep.
 *
 * <p>Walking through: the server moves the player into the dungeon's dimension, which brings up the
 * loading screen. If that happens right after standing in a window this client knows, the screen is
 * skipped (see mixin.WindowLoadingScreenMixin) and the snapshot is drawn where it really is, until
 * the real chunks are there.
 *
 * <p>With an Iris shader pack this stays off and the server's picture is drawn as before.
 */
public final class PortalWindows {
	/** Floats per vertex while clipping: x, y, z, u, v. */
	private static final int F = 5;
	/** After standing in a window this long, a new level of its target is taken for walking through it. */
	private static final long ARRIVE_WITHIN = 3_000_000_000L;
	private static final long FILL_FOR = 5_000_000_000L;
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
		/** Its broken glass, and when it was first shown here (the pieces fly out from then). */
		final List<Shards.Shard> shards;
		long born = System.nanoTime();

		Window(WindowPayloads.View view, List<Quad> solid, List<Quad> translucent) {
			this.view = view;
			this.solid = solid;
			this.translucent = translucent;
			this.scaleFrom = 0;
			this.scaleTo = view.scale();
			this.scaleStart = System.nanoTime();
			this.scaleNanos = 400_000_000L;
			this.shards = Shards.generate(view.id() * 7919L + 17);
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

	/** A show's rift (Events "do": "rift"), on the server's word: as it is, since when, and when last heard of. */
	private static final class ShowRift {
		ShowRiftPayload last;
		long born;
		long heard;
		List<Shards.Shard> shards;
	}

	/** By the first id of its displays. */
	private static final Map<Integer, ShowRift> SHOW_RIFTS = new HashMap<>();
	private static final long SHOW_RIFT_SILENCE = 1_500_000_000L;

	static void receive(ShowRiftPayload payload) {
		if (payload.hide().length == 0) {
			return;
		}
		int key = payload.hide()[0];
		if (payload.open() <= 0) {
			SHOW_RIFTS.remove(key);
			return;
		}
		long now = System.nanoTime();
		ShowRift r = SHOW_RIFTS.computeIfAbsent(key, k -> {
			ShowRift n = new ShowRift();
			n.born = now;
			n.shards = Shards.generate(k * 7919L + 23);
			return n;
		});
		r.last = payload;
		r.heard = now;
	}

	static void receive(WindowPayloads.View view) {
		try {
			Window w = build(view);
			Window old = WINDOWS.put(view.id(), w);
			if (old != null) {
				w.inAt = old.inAt;
				w.born = old.born;
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
		VOID_BORN.clear();
		SHOW_RIFTS.clear();
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
		if (mc.level == null) {
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
		float time = (float) ((System.nanoTime() / 1e9) % 1200.0);
		long now = System.nanoTime();
		float u = white.getU(0.5f), v = white.getV(0.5f);
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
			WindowPayloads.View view = w.view;
			ShardCut cut = new ShardCut(w.shards, (float) (view.bx() - cam.x), (float) (view.by() + view.height() / 2.0 - cam.y),
					(float) (view.bz() - cam.z), view.height() / 2 * s, (now - w.born) / 1e9f / Shards.OPEN_SECONDS, time, false);
			float ox = (float) (view.bx() - cam.x), oy = (float) (view.by() - cam.y), oz = (float) (view.bz() - cam.z);
			HIDDEN.add(view.entity());
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS), (p, vc) -> {
				cut.back(p, vc, u, v, BACKGROUND, false);
				cut.edges(p, vc, u, v);
			});
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS),
					(p, vc) -> cut.room(p, vc, w.solid, ox, oy, oz));
			if (!w.translucent.isEmpty()) {
				ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
						(p, vc) -> cut.room(p, vc, w.translucent, ox, oy, oz));
			}
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
					(p, vc) -> cut.sheen(p, vc, u, v));
		}
		voidRifts(ctx, mc, cam, pose, u, v, time, now);
		showRifts(ctx, cam, pose, u, v, time, now);
	}

	/** About the tear's size in that picture (6.4 blocks, the tear some 44 px of 64). */
	private static final float RIFT_PICTURE_RADIUS = 2.3f;
	private static final int DEEP = 0xFF12081F;
	/** Pictures of the rift into the mini-game, by entity id: when first seen. */
	private static final Map<Integer, Long> VOID_BORN = new HashMap<>();
	private static final Map<Integer, List<Shards.Shard>> VOID_SHARDS = new HashMap<>();

	/**
	 * Whether this draws rift-mod's pictures as broken glass right now: not with a shader pack, and
	 * not in the mode's own top-down looks, which draw their tears with the screen shader
	 * (RiftTear). Outside the mode that shader drew the blue tear; this takes its place.
	 */
	static boolean drawsRiftPictures() {
		return !shaderPack() && !TopDown.active() && !TopDown.fixed();
	}

	/** The rift into the mini-game: the same broken glass, the deep inside (no room of it here). */
	private static void voidRifts(LevelRenderContext ctx, Minecraft mc, Vec3 cam, PoseStack pose, float u, float v, float time, long now) {
		if (!drawsRiftPictures()) {
			VOID_BORN.clear();
			VOID_SHARDS.clear();
			return;
		}
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!RiftTear.picture(e)) {
				continue;
			}
			Vec3 at = e.position();
			if (at.distanceToSqr(cam) > 128 * 128) {
				continue;
			}
			seen.add(e.getId());
			long born = VOID_BORN.computeIfAbsent(e.getId(), id -> now);
			List<Shards.Shard> shards = VOID_SHARDS.computeIfAbsent(e.getId(), id -> Shards.generate(31L * (long) Math.floor(at.x) + (long) Math.floor(at.z)));
			ShardCut cut = new ShardCut(shards, (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z), RIFT_PICTURE_RADIUS,
					(now - born) / 1e9f / Shards.OPEN_SECONDS, time, false);
			HIDDEN.add(e.getId());
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS), (p, vc) -> {
				cut.back(p, vc, u, v, DEEP, true);
				cut.edges(p, vc, u, v);
			});
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
					(p, vc) -> cut.sheen(p, vc, u, v));
		}
		VOID_BORN.keySet().retainAll(seen);
		VOID_SHARDS.keySet().retainAll(seen);
	}

	/**
	 * A show's rift (Events "do": "rift", and the End mod's window blocks a show puts up, as in the
	 * End rifts trailer): broken violet glass round its
	 * middle with the deep inside, as big as most of the crack; the crack's displays are hidden.
	 */
	private static void showRifts(LevelRenderContext ctx, Vec3 cam, PoseStack pose, float u, float v, float time, long now) {
		Minecraft mc = Minecraft.getInstance();
		// Gone when the server stops telling, or when its picture (the first display) is gone.
		SHOW_RIFTS.values().removeIf(r -> now - r.heard > SHOW_RIFT_SILENCE || mc.level == null || mc.level.getEntity(r.last.hide()[0]) == null);
		if (SHOW_RIFTS.isEmpty() || !drawsRiftPictures()) {
			return;
		}
		for (ShowRift r : SHOW_RIFTS.values()) {
			ShowRiftPayload p = r.last;
			float radius = (float) (p.radius() * p.open());
			if (radius < 0.05f) {
				continue;
			}
			for (int id : p.hide()) {
				HIDDEN.add(id);
			}
			ShardCut cut = new ShardCut(r.shards, (float) (p.x() - cam.x), (float) (p.y() - cam.y), (float) (p.z() - cam.z), radius,
					(now - r.born) / 1e9f / Shards.OPEN_SECONDS, time, false);
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS), (pp, vc) -> {
				cut.back(pp, vc, u, v, DEEP, true);
				cut.edges(pp, vc, u, v);
			});
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS),
					(pp, vc) -> cut.sheen(pp, vc, u, v));
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

	// ------------------------------------------------------------------ for the self-test

	static int windows() {
		return WINDOWS.size();
	}

	static Window window(int id) {
		return WINDOWS.get(id);
	}
}
