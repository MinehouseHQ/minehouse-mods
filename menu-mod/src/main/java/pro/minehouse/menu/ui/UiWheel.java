package pro.minehouse.menu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.resources.DefaultPlayerSkin;

import pro.minehouse.menu.ui.UiTree.Theme;

/**
 * The player wheel of a server window with scene "wheel": tiles in a ring round the other player's
 * face, in the style of the windows. The tile is picked by the direction of the mouse from the
 * middle (the cursor starts there), so it doesn't have to be on the tile itself.
 */
final class UiWheel {
	/** Distance from the middle to the tiles' middles, GUI pixels. */
	static final int RADIUS = 62;
	static final int TILE = 30;
	static final int CENTER = 40;
	/** Closer to the middle than this nothing is picked. */
	static final int DEAD = 16;

	record Slot(String label, String tooltip, String item, String model, JsonObject click) {
	}

	final UUID face;
	final String name;
	final List<Slot> slots = new ArrayList<>();

	UiWheel(JsonObject json) {
		UUID uuid = null;
		try {
			uuid = UUID.fromString(UiTree.str(json, "face", ""));
		} catch (IllegalArgumentException e) {
			// No face: just the name.
		}
		face = uuid;
		name = UiTree.str(json, "name", "");
		if (json.has("slots") && json.get("slots").isJsonArray()) {
			for (JsonElement e : json.getAsJsonArray("slots")) {
				if (!e.isJsonObject()) continue;
				JsonObject s = e.getAsJsonObject();
				slots.add(new Slot(UiTree.str(s, "label", ""), UiTree.str(s, "tooltip", null), UiTree.str(s, "item", "minecraft:paper"),
						UiTree.str(s, "model", null), s.has("click") && s.get("click").isJsonObject() ? s.getAsJsonObject("click") : null));
			}
		}
	}

	/** Middle of slot {@code i} relative to the wheel's middle: clockwise from the top. */
	int[] offset(int i) {
		double angle = -Math.PI / 2 + 2 * Math.PI * i / slots.size();
		return new int[] {(int) Math.round(Math.cos(angle) * RADIUS), (int) Math.round(Math.sin(angle) * RADIUS)};
	}

	/** The slot the mouse points at from the middle (cx, cy), or -1. */
	int pick(double cx, double cy, double mx, double my) {
		double dx = mx - cx;
		double dy = my - cy;
		if (slots.isEmpty() || dx * dx + dy * dy < DEAD * DEAD) return -1;
		// Angle clockwise from the top, 0..2pi.
		double a = Math.atan2(dx, -dy);
		if (a < 0) a += 2 * Math.PI;
		double step = 2 * Math.PI / slots.size();
		return (int) Math.floor((a + step / 2) / step) % slots.size();
	}

	PlayerSkin skin() {
		Minecraft mc = Minecraft.getInstance();
		if (face == null) return null;
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(face);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(face);
	}

	/**
	 * Draws the wheel round (cx, cy).
	 *
	 * @param open 0..1 while it opens (grows from the middle)
	 * @param hover the slot under the mouse, or -1
	 * @param grow 0..1 per slot, how far its hover has grown
	 */
	void draw(UiScreen screen, GuiGraphicsExtractor g, Font font, int cx, int cy, float open, int hover, float[] grow) {
		float ease = 1 - (1 - open) * (1 - open) * (1 - open);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(0.7F + 0.3F * ease, 0.7F + 0.3F * ease);

		// A faint ring the tiles sit on.
		ring(g, RADIUS, 0x30FFFFFF);

		for (int i = 0; i < slots.size(); i++) {
			int[] o = offset(i);
			float k = 1 + 0.18F * grow[i];
			pose.pushMatrix();
			pose.translate(o[0], o[1]);
			pose.scale(k, k);
			int h = TILE / 2;
			boolean on = i == hover;
			UiScreen.card(g, -h, -h, h, h, on ? Theme.HOVER_BG : Theme.MUTED_BG, on ? Theme.ACCENT : 0xFF3F3F46);
			g.item(screen.wheelItem(slots.get(i)), -8, -8);
			pose.popMatrix();
		}

		// The face on a dark disc in the middle.
		int c = CENTER / 2;
		UiScreen.card(g, -c, -c, c, c, 0xF209090B, Theme.BORDER);
		PlayerSkin skin = skin();
		if (skin != null) PlayerFaceExtractor.extractRenderState(g, skin, -12, -12, 24);
		pose.popMatrix();

		// Under the wheel: the name, or what the hovered tile does.
		int y = cy + RADIUS + TILE / 2 + 10;
		if (hover >= 0) {
			Slot s = slots.get(hover);
			label(g, font, cx, cy + offset(hover)[1] - TILE / 2 - 16, offset(hover)[0], s.label());
			List<FormattedCharSequence> tip = s.tooltip() == null ? List.of() : font.split(Component.literal(s.tooltip()), 220);
			int w = font.width(Component.literal(s.label()).withStyle(st -> st.withBold(true)));
			for (FormattedCharSequence line : tip) w = Math.max(w, font.width(line));
			int h = 8 + UiTree.LINE + tip.size() * UiTree.LINE + 2;
			UiScreen.card(g, cx - w / 2 - 8, y, cx + w / 2 + 8, y + h, Theme.CARD, Theme.BORDER);
			g.centeredText(font, Component.literal(s.label()).withStyle(st -> st.withBold(true)), cx, y + 5, Theme.FG);
			int ty = y + 5 + UiTree.LINE;
			for (FormattedCharSequence line : tip) {
				g.text(font, line, cx - font.width(line) / 2, ty, Theme.MUTED, false);
				ty += UiTree.LINE;
			}
		} else if (!name.isEmpty()) {
			Component n = Component.literal(name).withStyle(st -> st.withBold(true));
			int w = font.width(n);
			UiScreen.card(g, cx - w / 2 - 8, y, cx + w / 2 + 8, y + 18, Theme.CARD, Theme.BORDER);
			g.centeredText(font, n, cx, y + 5, Theme.FG);
		}
	}

	/** The hovered action's name in a small card above its tile. */
	private static void label(GuiGraphicsExtractor g, Font font, int cx, int y, int dx, String text) {
		Component c = Component.literal(text);
		int w = font.width(c);
		int x = cx + dx;
		UiScreen.card(g, x - w / 2 - 5, y - 3, x + w / 2 + 5, y + 11, Theme.CARD, Theme.BORDER);
		g.centeredText(font, c, x, y, Theme.FG);
	}

	/** A dotted circle of radius r round the current origin. */
	private static void ring(GuiGraphicsExtractor g, int r, int color) {
		int n = (int) (2 * Math.PI * r / 3);
		for (int i = 0; i < n; i++) {
			double a = 2 * Math.PI * i / n;
			int x = (int) Math.round(Math.cos(a) * r);
			int y = (int) Math.round(Math.sin(a) * r);
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	static float approach(float value, float goal, float step) {
		return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
	}

	static float clamp01(float v) {
		return Mth.clamp(v, 0, 1);
	}
}
