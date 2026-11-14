package pro.minehouse.riftclient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import pro.minehouse.riftclient.mixin.BossBarsAccess;

/**
 * A restart of the server seen as the rift taking the world: in the last minute of the countdown
 * tears creep in from the corners of the screen, in the last seconds one opens across the middle
 * and swallows the picture. The countdown is read from the server's boss bar ("Перезапуск через
 * 3:45"), so it works on every MineHouse server without anything new from it.
 */
public final class RiftRestart {
	static final Pattern CLOCK = Pattern.compile("Перезапуск через (?:(\\d+):)?(\\d+):(\\d\\d)");
	/** The tears start this many seconds before the restart, the middle one this many. */
	static final float CREEP = 5;
	static final float OPEN = 5;

	/** Seconds left as last told, and when that was; below zero: no restart is coming. */
	private static int told = -1;
	private static long toldAt;

	/**
	 * In the limbo (where Gate keeps players while their server restarts) the player is inside the
	 * rift: its other side fills the screen. Known by the server's brand or by the limbo's line
	 * above the hotbar; lasts until the next server.
	 */
	private static boolean limbo;
	private static long limboSince;
	/** A white flash that fades: on the way into the limbo and out of it. */
	private static long flashAt;
	private static Object stale;
	static final String LIMBO_LINE = "Вернём тебя на сервер";
	static final float FLASH_MS = 1400;

	private RiftRestart() {
	}

	/** A new server: out of the limbo with a flash, and no old countdown. */
	static void joined() {
		// The line above the hotbar outlives the server that sent it: the old one is no sign.
		stale = ((pro.minehouse.riftclient.mixin.HudAccess) Minecraft.getInstance().gui.hud).minehouse$actionBar();
		if (limbo) {
			flashAt = System.currentTimeMillis();
		}
		limbo = false;
		told = -1;
	}

	static boolean inLimbo() {
		return limbo;
	}

	static boolean limboSign(String brand, String actionBar) {
		return (brand != null && brand.toLowerCase(java.util.Locale.ROOT).contains("limbo")) || (actionBar != null && actionBar.contains(LIMBO_LINE));
	}

	/** How far inside the rift the screen is (0..1). */
	static float inside() {
		return limbo ? Math.min(1, (System.currentTimeMillis() - limboSince) / 600f) : 0;
	}

	static float flash() {
		float f = 1 - (System.currentTimeMillis() - flashAt) / FLASH_MS;
		return Math.max(0, Math.min(1, f));
	}

	/** Seconds in a boss bar's name, or -1 when it is not the countdown. */
	static int seconds(String name) {
		Matcher m = CLOCK.matcher(name);
		if (!m.find()) {
			return -1;
		}
		int hours = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
		return hours * 3600 + Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
	}

	static void tick(Minecraft mc) {
		if (mc.level != null && mc.player != null && !limbo) {
			net.minecraft.network.chat.Component bar = ((pro.minehouse.riftclient.mixin.HudAccess) mc.gui.hud).minehouse$actionBar();
			if (limboSign(mc.player.connection.serverBrand(), bar == null || bar == stale ? null : bar.getString())) {
				limbo = true;
				limboSince = System.currentTimeMillis();
				flashAt = limboSince;
			}
		}
		if (mc.level == null) {
			limbo = false;
		}
		int now = -1;
		if (mc.level != null) {
			for (LerpingBossEvent e : ((BossBarsAccess) mc.gui.hud.getBossOverlay()).minehouse$events().values()) {
				now = Math.max(now, seconds(e.getName().getString()));
			}
		}
		if (now != told) {
			told = now;
			toldAt = System.currentTimeMillis();
		}
	}

	/** Seconds left right now, counted on between the server's words; below zero: none. */
	static float left() {
		if (told < 0) {
			return -1;
		}
		return Math.max(0, told - (System.currentTimeMillis() - toldAt) / 1000f);
	}

	static boolean any() {
		float left = left();
		return limbo || flash() > 0 || (left >= 0 && left <= CREEP);
	}

	private static float[] tear(float x0, float y0, float x1, float y1, float grown, float open, float seed) {
		return new float[] {x0, y0, x0 + (x1 - x0) * grown, y0 + (y1 - y0) * grown, open, seed, 0, 0};
	}

	/** The tears of the screen for this many seconds left, as the shader takes them (see {@link RiftTear#project}). */
	static List<float[]> tears(float left) {
		List<float[]> out = new ArrayList<>();
		if (limbo || flash() > 0) {
			// Inside the rift: two tears drift slowly over its other side. The first slot carries
			// how far inside the screen is and the flash.
			float t = (System.currentTimeMillis() % 100000) / 1000f;
			float sway = (float) Math.sin(t * 0.3) * 0.05f;
			float[] first = tear(0.22f + sway, 0.12f, 0.36f - sway, 0.9f, 1, limbo ? 0.7f : 0, 2.2f);
			first[6] = inside();
			first[7] = flash();
			out.add(first);
			if (limbo) {
				out.add(tear(0.80f - sway, 0.95f, 0.66f + sway, 0.2f, 1, 0.6f, 6.4f));
			}
			return out;
		}
		if (left < 0 || left > CREEP) {
			return out;
		}
		float p = 1 - left / CREEP;
		if (left <= OPEN) {
			float q = 1 - left / OPEN;
			// The last second and a half it tears wide open: the other side fills the screen.
			float burst = left < 1.5f ? (1.5f - left) / 1.5f : 0;
			out.add(tear(0.5f, 0.5f - 0.45f * q, 0.5f, 0.5f + 0.45f * q, 1, 0.5f + 0.7f * q + 14 * burst * burst, 3.7f));
		}
		// They come out of nothing, not at once.
		float grown = 0.1f + 0.9f * p;
		float open = Math.min(1, p * 4) * (0.5f + 0.5f * p);
		out.add(tear(-0.02f, 1.02f, 0.34f, 0.56f, grown, open, 1.3f));
		out.add(tear(1.02f, -0.02f, 0.68f, 0.42f, grown, open, 8.1f));
		if (p > 0.5f) {
			out.add(tear(1.02f, 0.9f, 0.74f, 0.7f, (p - 0.5f) * 2, open, 5.9f));
		}
		return out;
	}
}
