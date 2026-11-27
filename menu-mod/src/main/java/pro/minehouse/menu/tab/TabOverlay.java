package pro.minehouse.menu.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;

import pro.minehouse.menu.MinehouseMenu;
import pro.minehouse.menu.Tr;
import pro.minehouse.menu.ui.UiScreen;
import pro.minehouse.menu.ui.UiTree.Theme;

/**
 * The MineHouse tab list, drawn instead of vanilla's while the server sends {@link TabData}: a
 * card in the style of the server windows with the logo, players in columns (face, name in the
 * Nitro colour, role and stream badges, ping), and online, ping and TPS at the bottom. The
 * Styled Player List header and footer are not drawn here: their texts are part of this design.
 */
public final class TabOverlay {
	static final Identifier LOGO = Identifier.fromNamespaceAndPath(MinehouseMenu.MOD_ID, "textures/gui/tab_logo.png");
	static final int PAD = 10;
	static final int ROW = 12;
	static final int GAP = 10;
	static final int FACE = 8;
	/** Purple of the separators and the title, from the site. */
	static final int DEEP = 0xFF3B1D6E;
	static final int PURPLE = 0xFFA855F7;
	static final int LILAC = 0xFFD8B4FE;
	static final int SUBTLE = 0xFF8B7AA8;

	/** A row of the list: what is drawn for one player. */
	public record Line(UUID id, String name, PlayerSkin skin, int ping, boolean spectator) {
	}

	private static boolean visible;
	private static long openedAt;

	private TabOverlay() {
	}

	/** Vanilla's {@code setVisible}: remembers when the list opened, for the slide-in. */
	public static void setVisible(boolean value) {
		if (value && !visible) openedAt = Util.getMillis();
		visible = value;
	}

	/** Draws our list if the server sent its data; false to let vanilla draw. */
	public static boolean draw(GuiGraphicsExtractor g, int screenWidth) {
		TabData data = TabNet.data();
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (data == null || connection == null) return false;
		List<Line> lines = new ArrayList<>();
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			lines.add(new Line(info.getProfile().id(), info.getProfile().name(), info.getSkin(), info.getLatency(),
					info.getGameMode() == GameType.SPECTATOR));
		}
		PlayerInfo self = mc.player == null ? null : connection.getPlayerInfo(mc.player.getUUID());
		draw(g, mc.font, screenWidth, data, lines, self == null ? -1 : self.getLatency(), Util.getMillis() - openedAt);
		return true;
	}

	/** Draws the list for these players; {@code age} is milliseconds since it opened. */
	public static void draw(GuiGraphicsExtractor g, Font font, int screenWidth, TabData data, List<Line> lines, int selfPing, long age) {
		boolean ru = Tr.russian();
		List<TabData.Row<Line>> rows = new ArrayList<>();
		for (Line line : lines) rows.add(new TabData.Row<>(line, line.name(), line.spectator(), data.meta(line.id())));
		TabData.sort(rows);
		if (rows.size() > TabData.MAX_PLAYERS) rows = rows.subList(0, TabData.MAX_PLAYERS);
		int[] grid = TabData.grid(rows.size());
		int cols = grid[0];
		int perCol = grid[1];

		// Column width: the widest name and badges, so the ping lines up on the right.
		int nameW = 40;
		int badgeW = 0;
		for (TabData.Row<Line> r : rows) {
			nameW = Math.max(nameW, font.width(r.name()));
			badgeW = Math.max(badgeW, badgesWidth(font, r.meta(), ru));
		}
		int pingW = font.width("999") + 9;
		int colW = 2 + FACE + 4 + nameW + (badgeW > 0 ? 4 + badgeW : 0) + 8 + pingW;
		int gridW = cols * colW + (cols - 1) * GAP;

		Component title = gradient("MINEHOUSE", LILAC, PURPLE).withStyle(s -> s.withBold(true));
		Component subtitle = Component.literal(ru ? "vanilla+ сервер" : "vanilla+ server");
		Component stats = stats(data, rows.size(), selfPing, ru);
		int headW = 16 + 4 + font.width(title);
		int innerW = Math.max(Math.max(gridW, headW), Math.max(font.width(stats), 180));
		int panelW = innerW + 2 * PAD;
		int listH = Math.max(perCol, 1) * ROW;
		int panelH = PAD + 14 + 3 + 10 + 6 + 5 + listH + 5 + 6 + 10 + 2 + 10 + PAD - 4;

		var pose = g.pose();
		pose.pushMatrix();
		// Slides down a little as it opens.
		float t = Math.min(1F, age / 140F);
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		float scale = panelW > screenWidth - 8 ? (screenWidth - 8F) / panelW : 1F;
		pose.translate(screenWidth / 2F, 8 - 6 * (1 - ease));
		pose.scale(scale, scale);
		int x0 = -panelW / 2;
		int x1 = x0 + panelW;
		int y = 0;
		UiScreen.card(g, x0, y, x1, y + panelH, Theme.CARD, Theme.BORDER);

		// Header: logo and title, the tagline, a separator.
		y += PAD;
		int hx = -headW / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, hx, y, 0F, 0F, 16, 14, 48, 42, 48, 42, 0xFFFFFFFF);
		g.text(font, title, hx + 20, y + 3, Theme.FG, false);
		y += 14 + 3;
		g.centeredText(font, subtitle.copy().withColor(SUBTLE & 0xFFFFFF), 0, y, SUBTLE);
		y += 10 + 6;
		separator(g, x0 + PAD, x1 - PAD, y);
		y += 5;

		// Players in columns.
		int gx = -gridW / 2;
		for (int i = 0; i < rows.size(); i++) {
			int col = i / Math.max(perCol, 1);
			int row = i % Math.max(perCol, 1);
			int rx = gx + col * (colW + GAP);
			int ry = y + row * ROW;
			row(g, font, rows.get(i), rx, ry, colW, row % 2 == 0, ru);
		}
		if (rows.isEmpty()) g.centeredText(font, Component.literal(ru ? "Никого нет" : "Nobody here"), 0, y + 2, Theme.FAINT);
		y += listH + 5;

		// Footer: a separator, the numbers, the site.
		separator(g, x0 + PAD, x1 - PAD, y);
		y += 6;
		g.centeredText(font, stats, 0, y, SUBTLE);
		y += 10 + 2;
		g.centeredText(font, Component.literal("minehouse.pro").withColor(PURPLE & 0xFFFFFF), 0, y, PURPLE);
		pose.popMatrix();
	}

	private static void row(GuiGraphicsExtractor g, Font font, TabData.Row<Line> r, int x, int y, int w, boolean stripe, boolean ru) {
		Line line = r.info();
		TabData.Meta meta = r.meta();
		if (stripe) g.fill(x, y, x + w, y + ROW, 0x0CFFFFFF);
		if (line.skin() != null) PlayerFaceExtractor.extractRenderState(g, line.skin(), x + 2, y + 2, FACE);
		int nameColor = line.spectator() ? Theme.FAINT : meta.color() != null ? 0xFF000000 | meta.color() : Theme.FG;
		Component name = Component.literal(line.name());
		if (line.spectator()) name = name.copy().withStyle(s -> s.withItalic(true));
		int nx = x + 2 + FACE + 4;
		g.text(font, name, nx, y + 2, nameColor, false);

		int bx = nx + font.width(name) + 4;
		if (meta.live()) bx = pill(g, font, bx, y + 1, "● LIVE", 0xFFEF4444) + 3;
		String role = roleLabel(meta.role(), ru);
		if (role != null) pill(g, font, bx, y + 1, role, roleColor(meta.role()));

		// Ping on the right: the number and four bars.
		int px = x + w - 2;
		int color = TabData.pingColor(line.ping());
		int lit = TabData.pingBars(line.ping());
		for (int b = 0; b < 4; b++) {
			int h = 2 + b * 2;
			int bxr = px - 7 + b * 2;
			g.fill(bxr, y + 10 - h, bxr + 1, y + 10, b < lit ? color : 0xFF3F3F46);
		}
		String ping = line.ping() < 0 ? "?" : Integer.toString(Math.min(line.ping(), 999));
		g.text(font, ping, px - 9 - font.width(ping), y + 2, color, false);
	}

	private static int badgesWidth(Font font, TabData.Meta meta, boolean ru) {
		int w = 0;
		if (meta.live()) w += font.width("● LIVE") + 6 + 3;
		String role = roleLabel(meta.role(), ru);
		if (role != null) w += font.width(role) + 6;
		return w;
	}

	/** A small rounded label; returns its right edge. */
	static int pill(GuiGraphicsExtractor g, Font font, int x, int y, String text, int color) {
		int w = font.width(text) + 6;
		int bg = (color & 0xFFFFFF) | 0x38000000;
		g.fill(x + 1, y, x + w - 1, y + 10, bg);
		g.fill(x, y + 1, x + 1, y + 9, bg);
		g.fill(x + w - 1, y + 1, x + w, y + 9, bg);
		g.text(font, text, x + 3, y + 1, color, false);
		return x + w;
	}

	static String roleLabel(String role, boolean ru) {
		if (role == null) return null;
		return switch (role) {
			case "admin" -> ru ? "Админ" : "Admin";
			case "moderator" -> ru ? "Модер" : "Mod";
			case "helper" -> ru ? "Хелпер" : "Helper";
			case "bot" -> ru ? "Бот" : "Bot";
			default -> null;
		};
	}

	/** The colours of the Styled Player List dots. */
	static int roleColor(String role) {
		return switch (role) {
			case "admin" -> 0xFFFF5F5F;
			case "moderator" -> 0xFFFACC15;
			case "helper" -> 0xFF60A5FA;
			case "bot" -> 0xFF7DD3FC;
			default -> Theme.MUTED;
		};
	}

	private static Component stats(TabData data, int listed, int selfPing, boolean ru) {
		int online = Math.max(data.online(), listed);
		MutableComponent c = Component.empty().withColor(SUBTLE & 0xFFFFFF);
		c.append(Component.literal(ru ? "Онлайн " : "Online "));
		c.append(Component.literal(Integer.toString(online)).withColor(0xFFFFFF));
		if (data.max() > 0) c.append(Component.literal("/" + data.max()));
		c.append(Component.literal("   ·   ").withColor(0x52525B));
		c.append(Component.literal(ru ? "Пинг " : "Ping "));
		c.append(Component.literal(selfPing < 0 ? "?" : Integer.toString(selfPing)).withColor(TabData.pingColor(selfPing) & 0xFFFFFF));
		c.append(Component.literal(ru ? " мс" : " ms"));
		c.append(Component.literal("   ·   ").withColor(0x52525B));
		c.append(Component.literal("TPS "));
		c.append(Component.literal(String.format(java.util.Locale.ROOT, "%.1f", data.tps())).withColor(TabData.tpsColor(data.tps()) & 0xFFFFFF));
		return c;
	}

	/** Text with a colour running from {@code from} to {@code to} across its letters. */
	static MutableComponent gradient(String text, int from, int to) {
		MutableComponent c = Component.empty();
		int n = text.length();
		for (int i = 0; i < n; i++) {
			float k = n == 1 ? 0 : i / (float) (n - 1);
			c.append(Component.literal(String.valueOf(text.charAt(i))).withColor(lerp(from, to, k) & 0xFFFFFF));
		}
		return c;
	}

	/** The purple line of the Styled Player List header: dark at the ends, bright in the middle. */
	static void separator(GuiGraphicsExtractor g, int x0, int x1, int y) {
		int w = x1 - x0;
		int step = 2;
		for (int x = x0; x < x1; x += step) {
			float k = (x - x0) / (float) Math.max(1, w);
			float mid = 1 - Math.abs(k * 2 - 1);
			g.fill(x, y, Math.min(x + step, x1), y + 1, lerp(DEEP, PURPLE, mid));
		}
	}

	static int lerp(int a, int b, float k) {
		int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		return 0xFF000000 | Math.round(ar + (br - ar) * k) << 16 | Math.round(ag + (bg - ag) * k) << 8 | Math.round(ab + (bb - ab) * k);
	}
}
