package pro.minehouse.riftclient;

import pro.minehouse.menu.Tr;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import pro.minehouse.riftclient.mixin.BossBarsAccess;
import pro.minehouse.riftclient.mixin.HudAccess;

/**
 * A restart of the server looks like the first screen of the MineHouse site: in the last seconds
 * of the countdown the world fades into a near-black field with waves of dots along the bottom,
 * and in the limbo (where Gate keeps players while their server restarts) that field carries the
 * name, one line and a small status plate. The countdown is read from the server's boss bar
 * ("Перезапуск через 3:45"), so it works on every MineHouse server without anything new from it.
 */
public final class RiftRestart {
	static final Pattern CLOCK = Pattern.compile("(?:Перезапуск(?: сервера)? через|(?:Server )?[Rr]estart in) (?:(\\d+):)?(\\d+):(\\d\\d)");
	/** The world fades out over this many last seconds. */
	static final float FADE = 3;
	static final String LIMBO_LINE = "Вернём тебя на сервер";
	/** Back on a server the field fades away over this long. */
	static final float BACK_MS = 900;

	/** Seconds left as last told, and when that was; below zero: no restart is coming. */
	private static int told = -1;
	private static long toldAt;
	/** Known by the server's brand or by the limbo's line above the hotbar; lasts until the next server. */
	private static boolean limbo;
	private static long limboSince;
	private static long backAt;
	private static Object stale;

	private RiftRestart() {
	}

	/** A new server: out of the limbo, and no old countdown. */
	static void joined() {
		// The line above the hotbar outlives the server that sent it: the old one is no sign.
		stale = ((HudAccess) Minecraft.getInstance().gui.hud).minehouse$actionBar();
		if (limbo) {
			backAt = System.currentTimeMillis();
		}
		limbo = false;
		told = -1;
	}

	public static boolean inLimbo() {
		return limbo;
	}

	static boolean limboSign(String brand, String actionBar) {
		return (brand != null && brand.toLowerCase(java.util.Locale.ROOT).contains("limbo")) || (actionBar != null && actionBar.contains(LIMBO_LINE));
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
			Component bar = ((HudAccess) mc.gui.hud).minehouse$actionBar();
			if (limboSign(mc.player.connection.serverBrand(), bar == null || bar == stale ? null : bar.getString())) {
				limbo = true;
				// Straight from a countdown the field is already there; otherwise it comes in.
				limboSince = System.currentTimeMillis() - (told >= 0 ? 10000 : 0);
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

	/** How much of the screen the field has taken (0..1). */
	static float field() {
		float f = 0;
		if (limbo) {
			f = Math.min(1, (System.currentTimeMillis() - limboSince) / 600f);
		} else {
			float left = left();
			if (left >= 0 && left <= FADE) {
				float t = 1 - left / FADE;
				f = t * t * (3 - 2 * t);
			}
			f = Math.max(f, 1 - (System.currentTimeMillis() - backAt) / BACK_MS);
		}
		return Math.max(0, Math.min(1, f));
	}

	static boolean any() {
		return field() > 0;
	}

	/** The words of the limbo, laid out as on the site: a small plate, the name, one line. */
	static void draw(GuiGraphicsExtractor g, Font font, int w, int h) {
		if (!limbo) {
			return;
		}
		float a = Math.min(1, Math.max(0, (System.currentTimeMillis() - limboSince - 300) / 500f));
		if (a <= 0.02f) {
			return;
		}
		int cy = h / 2 - 14;
		String plate = Tr.s("перезапуск сервера · play.minehouse.pro", "server restart · play.minehouse.pro");
		int pw = font.width(plate) + 22;
		Ui.panel(g, w / 2 - pw / 2, cy - 30, pw, 13, 0x18181B, 0.9f * a);
		boolean lit = (System.currentTimeMillis() / 700) % 2 == 0;
		g.fill(w / 2 - pw / 2 + 6, cy - 25, w / 2 - pw / 2 + 9, cy - 22, Ui.ink(lit ? Ui.YELLOW : 0x7A6510, a));
		g.text(font, plate, w / 2 - pw / 2 + 14, cy - 27, Ui.ink(0xE4E4E7, a), false);
		Ui.big(g, font, Component.literal("MINEHOUSE").withStyle(Style.EMPTY.withBold(true)), w / 2, cy - 8, 4f, Ui.ink(Ui.WHITE, a), 1);
		g.centeredText(font, Tr.s("Вернём тебя на сервер автоматически, выходить не нужно.", "We'll bring you back to the server automatically, no need to leave."), w / 2, cy + 36, Ui.ink(0xD4D4D8, a));
	}
}
