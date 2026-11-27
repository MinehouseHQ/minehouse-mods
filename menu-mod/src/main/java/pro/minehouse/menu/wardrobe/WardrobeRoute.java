package pro.minehouse.menu.wardrobe;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import pro.minehouse.menu.MinehouseMenu;

/**
 * The way to the fitting room (O away from one): the server finds the route over the terrain and
 * sends it on {@code minehouse-cosmetics:route}; here it becomes a glowing trail on the ground with
 * arrows sliding along it towards the room, and a beam of light over the room. Registering the
 * channel tells the server not to send its particle arrows.
 */
public final class WardrobeRoute {
	private static final Identifier ARROW = tex("arrow");
	private static final Identifier BAND = tex("band");
	private static final Identifier BEAM = tex("beam");
	private static final Identifier RING = tex("ring");

	/** How far ahead of the player the trail is drawn, blocks along the route. */
	static final double AHEAD = 36;
	static final double SPACING = 1.6;
	/** Blocks a second the arrows slide. */
	static final double SPEED = 2.4;
	private static final int PINK = 0xFF4FCF;
	private static final int VIOLET = 0xA05CFF;

	public record RoutePayload(String json) implements CustomPacketPayload {
		static final Type<RoutePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WardrobeNet.NS, "route"));
		static final StreamCodec<FriendlyByteBuf, RoutePayload> CODEC = ByteBufCodecs.stringUtf8(1 << 18).map(RoutePayload::new, RoutePayload::json).cast();

		@Override
		public Type<RoutePayload> type() {
			return TYPE;
		}
	}

	/** What is shown now; replaced whole on each packet. */
	record Shown(RouteLine line, Vec3 room, boolean complete, long since) {
	}

	private static volatile Shown shown;

	private WardrobeRoute() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(RoutePayload.TYPE, RoutePayload.CODEC.cast());
		ClientPlayNetworking.registerGlobalReceiver(RoutePayload.TYPE, (payload, context) -> receive(payload.json()));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> shown = null);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> shown = null);
		LevelRenderEvents.COLLECT_SUBMITS.register(WardrobeRoute::submit);
		if (Boolean.getBoolean("minehouse.route.demo")) RouteDemo.register();
	}

	static void receive(String json) {
		try {
			shown = parse(json, shown);
		} catch (RuntimeException e) {
			MinehouseMenu.LOG.warn("Bad wardrobe route packet", e);
			shown = null;
		}
	}

	/** {} clears; a route keeps the fade-in time of the one it replaces. */
	static Shown parse(String json, Shown before) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		if (!o.has("room")) return null;
		JsonArray r = o.getAsJsonArray("room");
		Vec3 room = new Vec3(r.get(0).getAsDouble(), r.get(1).getAsDouble(), r.get(2).getAsDouble());
		List<int[]> cells = new ArrayList<>();
		for (var e : o.getAsJsonArray("points")) {
			JsonArray a = e.getAsJsonArray();
			cells.add(new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
		}
		return new Shown(cells.isEmpty() ? null : RouteLine.of(cells), room, o.has("complete") && o.get("complete").getAsBoolean(),
				before != null ? before.since() : System.nanoTime());
	}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("minehouse-menu", "textures/route/" + name + ".png");
	}

	private static void submit(LevelRenderContext ctx) {
		Shown s = shown;
		Minecraft mc = Minecraft.getInstance();
		if (s == null || mc.player == null || mc.level == null) return;
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		Vec3 me = mc.player.position();
		float time = ((System.nanoTime() - s.since()) % 3_600_000_000_000L) / 1e9f;
		float appear = Math.min(1, time / 0.6f);
		PoseStack pose = ctx.poseStack();
		RouteLine line = s.line();

		if (line == null || line.size() < 2) {
			// No walkable way yet: one big arrow in front of the player, pointing at the room.
			Vec3 to = new Vec3(s.room().x - me.x, 0, s.room().z - me.z);
			if (to.lengthSqr() > 1e-4) {
				Vec3 f = to.normalize();
				Vector3f c = rel(me.add(f.scale(2.2)).add(0, 0.08 + 0.05 * Math.sin(time * 3), 0), cam);
				Vector3f fw = new Vector3f((float) f.x, 0, (float) f.z);
				Vector3f side = new Vector3f(-fw.z, 0, fw.x);
				ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(ARROW), (p, vc) ->
						quad(p, vc, c, side.mul(0.7f), new Vector3f(fw).mul(0.7f), argb(0.9f * appear, PINK)));
			}
		} else {
			double s0 = line.project(me.x, me.y, me.z);
			double end = Math.min(line.length(), s0 + AHEAD);
			// The trail: a soft band on the ground.
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(BAND), (p, vc) -> {
				for (double d = s0 + 0.5; d < end; d += 0.5) {
					RouteLine.At a = line.at(d + 0.25);
					float alpha = fade(d - s0, end - d) * 0.6f * appear;
					if (alpha <= 0.01f) continue;
					Vector3f fw = new Vector3f((float) a.dx(), (float) a.dy(), (float) a.dz());
					Vector3f side = side(fw);
					Vector3f c = rel(new Vec3(a.x(), a.y() + 0.04, a.z()), cam);
					quad(p, vc, c, side.mul(0.3f), fw.mul(0.27f), argb(alpha, mix(PINK, VIOLET, (float) ((d - s0) / AHEAD))));
				}
			});
			// The arrows, sliding towards the room.
			double phase = (time * SPEED) % SPACING;
			for (int layer = 0; layer < 2; layer++) {
			final boolean glow = layer == 0;
			ctx.submitNodeCollector().submitCustomGeometry(pose, glow ? RenderTypes.eyes(ARROW) : RenderTypes.entityTranslucentEmissive(ARROW), (p, vc) -> {
				double first = Math.floor(s0 / SPACING) * SPACING + phase;
				for (double d = first; d < end; d += SPACING) {
					if (d < s0 + 0.7) continue;
					RouteLine.At a = line.at(d);
					float alpha = fade(d - s0 - 0.4, end - d) * appear;
					if (alpha <= 0.01f) continue;
					Vector3f fw = new Vector3f((float) a.dx(), (float) a.dy(), (float) a.dz());
					Vector3f side = side(fw);
					// A gentle wave runs through the arrows; the nearest are a little larger.
					float wave = (float) Math.sin(d * 0.9 - time * 5);
					float size = (0.42f + 0.04f * wave + (float) Math.max(0, 0.08 * (1 - (d - s0) / 6))) * (glow ? 1.35f : 1f);
					Vector3f c = rel(new Vec3(a.x(), a.y() + (glow ? 0.06 : 0.08) + 0.03 * wave, a.z()), cam);
					int colour = glow ? argb(alpha * 0.3f, PINK) : argb(alpha * (0.85f + 0.15f * wave), mix(PINK, 0xFFFFFF, 0.12f + 0.12f * wave));
					quad(p, vc, c, side.mul(size), fw.mul(size), colour);
				}
			});
			}
		}

		// The room: a beam of light, seen from afar, and rings on the floor.
		Vec3 room = s.room();
		Vec3 toRoom = room.subtract(cam);
		double dist = Math.max(toRoom.length(), 1e-3);
		double far = Math.max(24, mc.options.getEffectiveRenderDistance() * 16 * 0.45);
		double k = Math.min(1, far / dist);
		float near = (float) Math.min(1, Math.max(0, (dist - 3) / 6));
		Vector3f base = new Vector3f((float) (toRoom.x * k), (float) (toRoom.y * k), (float) (toRoom.z * k));
		Vector3f look = new Vector3f((float) toRoom.x, 0, (float) toRoom.z);
		if (look.lengthSquared() < 1e-6) look.set(0, 0, 1);
		look.normalize();
		Vector3f right = new Vector3f(-look.z, 0, look.x);
		float beamH = (float) (7 * k);
		float pulse = 0.85f + 0.15f * (float) Math.sin(time * 2.2);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(BEAM), (p, vc) -> {
			Vector3f c = new Vector3f(base).add(0, beamH, 0);
			quad(p, vc, c, new Vector3f(right).mul((float) (0.55 * k)), new Vector3f(0, beamH, 0), argb(0.75f * pulse * near * appear, PINK));
			quad(p, vc, c, new Vector3f(right).mul((float) (0.2 * k)), new Vector3f(0, beamH, 0), argb(0.9f * near * appear, 0xFFFFFF));
		});
		if (dist < 48) {
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(RING), (p, vc) -> {
				Vector3f c = rel(room.add(0, 0.05, 0), cam);
				float a0 = time * 0.8f;
				Vector3f ax = new Vector3f((float) Math.cos(a0), 0, (float) Math.sin(a0));
				Vector3f ay = new Vector3f(-ax.z, 0, ax.x);
				quad(p, vc, c, new Vector3f(ax).mul(1.1f), new Vector3f(ay).mul(1.1f), argb(0.8f * appear, PINK));
				// A ring that spreads out and fades, again and again.
				float t = (time * 0.6f) % 1f;
				float r = 0.6f + 1.6f * t;
				quad(p, vc, new Vector3f(c).add(0, 0.01f, 0), new Vector3f(1, 0, 0).mul(r), new Vector3f(0, 0, 1).mul(r),
						argb((1 - t) * 0.7f * appear, VIOLET));
			});
		}
	}

	/** Fades in just ahead of the feet and out towards the far end of the trail. */
	static float fade(double fromPlayer, double toEnd) {
		float in = (float) Math.max(0, Math.min(1, fromPlayer / 1.5));
		float out = (float) Math.max(0, Math.min(1, toEnd / 6));
		return in * out;
	}

	/** Across the way, level with the ground (also for ladders, where forward is straight up). */
	private static Vector3f side(Vector3f fw) {
		Vector3f s = new Vector3f(fw).cross(0, 1, 0);
		if (s.lengthSquared() < 1e-4) {
			s.set(1, 0, 0);
		}
		return s.normalize().negate();
	}

	private static Vector3f rel(Vec3 at, Vec3 cam) {
		return new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z));
	}

	static int argb(float alpha, int rgb) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		return a << 24 | (rgb & 0xFFFFFF);
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	/** A two-sided quad round {@code c}; {@code ay} points to the texture's top (the arrow's tip). */
	private static void quad(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f ax, Vector3f ay, int color) {
		float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
		Vector3f n = new Vector3f(ax).cross(ay);
		if (n.lengthSquared() < 1e-9) return;
		n.normalize();
		for (int pass = 0; pass < 2; pass++) {
			for (int j = 0; j < 4; j++) {
				float[] q = corners[pass == 0 ? j : 3 - j];
				vc.addVertex(p, c.x + ax.x * q[0] + ay.x * q[1], c.y + ax.y * q[0] + ay.y * q[1], c.z + ax.z * q[0] + ay.z * q[1])
						.setColor(color).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(p, n.x, n.y, n.z);
			}
		}
	}
}
