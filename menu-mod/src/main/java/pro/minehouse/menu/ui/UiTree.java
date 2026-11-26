package pro.minehouse.menu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A server window's nodes and their layout (format: MineHouse Guides {@code ServerUi}). No
 * Minecraft classes here, only text measuring through {@link Measure}, so the layout is unit
 * tested; {@link UiScreen} draws it.
 */
public final class UiTree {
	public static final int LINE = 10;
	public static final int BUTTON_H = 18;
	public static final int TABS_H = 22;
	public static final int TAB_ICON_W = 18;
	public static final int COIN = 8;
	public static final int COIN_GAP = 2;

	/** Text widths and line breaking of the client's font. */
	public interface Measure {
		int width(String text, boolean bold);

		List<String> wrap(String text, boolean bold, int width);
	}

	public static final class Node {
		public final String type;
		public final JsonObject json;
		public final List<Node> children = new ArrayList<>();
		public final String id;
		final int fixedW;
		final int fixedH;
		final int padX;
		final int padY;
		final int gap;
		final boolean grow;

		// Layout result, GUI pixels.
		public int x;
		public int y;
		public int w;
		public int h;
		/** Text broken into lines (text nodes). */
		public List<String> lines = List.of();
		/** Grid: rows shown at once, and how far it can scroll. */
		public int visibleRows;
		public int maxScroll;

		Node(JsonObject json) {
			this.json = json;
			this.type = UiTree.str(json, "t", "col");
			this.id = UiTree.str(json, "id", null);
			this.fixedW = UiTree.num(json, "w", -1);
			this.fixedH = UiTree.num(json, "h", -1);
			int pad = UiTree.num(json, "pad", 0);
			this.padX = UiTree.num(json, "px", pad);
			this.padY = UiTree.num(json, "py", pad);
			this.gap = UiTree.num(json, "gap", type.equals("grid") ? 4 : 0);
			this.grow = UiTree.num(json, "grow", 0) > 0;
			JsonElement c = json.get("children");
			if (c != null && c.isJsonArray()) {
				for (JsonElement e : c.getAsJsonArray()) {
					if (e.isJsonObject()) children.add(new Node(e.getAsJsonObject()));
				}
			}
		}

		public String str(String key) {
			return UiTree.str(json, key, null);
		}

		public String str(String key, String fallback) {
			return UiTree.str(json, key, fallback);
		}

		public int num(String key, int fallback) {
			return UiTree.num(json, key, fallback);
		}

		public boolean flag(String key) {
			JsonElement e = json.get(key);
			return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
		}

		public JsonObject obj(String key) {
			JsonElement e = json.get(key);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		}

		public JsonObject click() {
			return flag("disabled") ? null : obj("click");
		}

		public JsonObject dbl() {
			return flag("disabled") ? null : obj("dbl");
		}

		public boolean over(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		/** Takes the width of its column (else its own width). */
		boolean fills() {
			return switch (type) {
				case "col", "card", "row", "tabs", "divider", "text", "model" -> true;
				default -> false;
			};
		}
	}

	public static Node parse(JsonObject json) {
		return new Node(json);
	}

	private final Measure measure;
	/** Grids may show up to their maxRows (the screen is tall enough). */
	private final boolean tall;

	public UiTree(Measure measure, boolean tall) {
		this.measure = measure;
		this.tall = tall;
	}

	// ------------------------------------------------------------------ sizes

	/** The width a node wants on its own (in a row). */
	public int intrinsic(Node n) {
		if (n.fixedW >= 0) return n.fixedW;
		return switch (n.type) {
			case "text" -> textWidth(n);
			case "button" -> n.str("style", "primary").equals("link") ? textWidth(n) + 4 : textWidth(n) + 16;
			case "item" -> 16;
			case "swatch" -> 12;
			case "model" -> 32;
			case "image", "space", "divider", "tabs" -> 0;
			case "grid" -> gridWidth(n);
			case "row" -> {
				int w = 0;
				for (Node c : n.children) w += intrinsic(c);
				yield w + Math.max(0, n.children.size() - 1) * n.gap + n.padX * 2;
			}
			default -> { // col, card
				int w = 0;
				for (Node c : n.children) w = Math.max(w, intrinsic(c));
				yield w + n.padX * 2;
			}
		};
	}

	private int textWidth(Node n) {
		String text = n.str("text", "");
		int w = measure.width(text, n.flag("bold"));
		return n.flag("coin") ? w + COIN_GAP + COIN : w;
	}

	/** The lines with "…" at the end of the last one, cut so it still fits. */
	private List<String> ellipsis(List<String> lines, boolean bold, int w) {
		List<String> out = new ArrayList<>(lines);
		String last = out.getLast().stripTrailing();
		while (!last.isEmpty() && measure.width(last + "…", bold) > w) last = last.substring(0, last.length() - 1).stripTrailing();
		out.set(out.size() - 1, last + "…");
		return out;
	}

	private static int gridWidth(Node n) {
		int cols = Math.max(1, n.num("cols", 1));
		return cols * n.num("cw", 16) + (cols - 1) * n.gap + n.padX * 2;
	}

	/** Lays the node out at (x, y) with width w; returns its height. */
	public int layout(Node n, int x, int y, int w) {
		n.x = x;
		n.y = y;
		n.w = w;
		int h = switch (n.type) {
			case "text" -> {
				boolean bold = n.flag("bold");
				String text = n.str("text", "");
				n.lines = n.flag("wrap") ? measure.wrap(text, bold, Math.max(1, w)) : List.of(text);
				int max = n.num("lines", 0);
				if (max > 0 && n.lines.size() > max) n.lines = ellipsis(n.lines.subList(0, max), bold, w);
				yield Math.max(1, n.lines.size()) * LINE - 1;
			}
			case "button" -> n.str("style", "primary").equals("link") ? LINE + 2 : BUTTON_H;
			case "item" -> 16;
			case "swatch" -> 12;
			case "model" -> 32;
			case "image" -> Math.max(0, n.fixedH);
			case "divider" -> 1;
			case "space" -> 0;
			case "tabs" -> {
				int[][] boxes = tabs(n);
				for (int i = 0; i < n.children.size(); i++) {
					Node t = n.children.get(i);
					t.x = boxes[i][0];
					t.y = y + 2;
					t.w = boxes[i][1] - boxes[i][0];
					t.h = TABS_H - 4;
				}
				yield TABS_H;
			}
			case "grid" -> grid(n);
			case "row" -> row(n);
			default -> col(n);
		};
		n.h = n.fixedH >= 0 ? n.fixedH : h;
		return n.h;
	}

	private int col(Node n) {
		int inner = n.w - n.padX * 2;
		int cy = n.y + n.padY;
		boolean first = true;
		for (Node c : n.children) {
			if (!first) cy += n.gap;
			first = false;
			int cw = c.fixedW >= 0 ? c.fixedW : c.fills() ? inner : Math.min(inner, intrinsic(c));
			int cx = n.x + n.padX;
			String align = n.str("align", "left");
			if (cw < inner && align.equals("center")) cx += (inner - cw) / 2;
			else if (cw < inner && align.equals("right")) cx += inner - cw;
			cy += layout(c, cx, cy, cw);
		}
		return cy - n.y + n.padY;
	}

	private int row(Node n) {
		int inner = n.w - n.padX * 2;
		int fixed = 0;
		int growing = 0;
		for (Node c : n.children) {
			if (c.grow && c.fixedW < 0) growing++;
			else fixed += intrinsic(c);
		}
		int gaps = Math.max(0, n.children.size() - 1) * n.gap;
		int left = Math.max(0, inner - fixed - gaps);
		int share = growing == 0 ? 0 : left / growing;
		int extra = growing == 0 ? 0 : left - share * growing;
		// Widths first, then heights to centre the children vertically.
		int[] widths = new int[n.children.size()];
		int g = 0;
		for (int i = 0; i < widths.length; i++) {
			Node c = n.children.get(i);
			if (c.grow && c.fixedW < 0) widths[i] = share + (++g == growing ? extra : 0);
			else widths[i] = intrinsic(c);
		}
		int maxH = 0;
		int cx = n.x + n.padX;
		for (int i = 0; i < widths.length; i++) {
			maxH = Math.max(maxH, layout(n.children.get(i), cx, n.y + n.padY, widths[i]));
			cx += widths[i] + n.gap;
		}
		cx = n.x + n.padX;
		for (int i = 0; i < widths.length; i++) {
			Node c = n.children.get(i);
			int dy = (maxH - c.h) / 2;
			if (dy != 0) layout(c, cx, n.y + n.padY + dy, widths[i]);
			cx += widths[i] + n.gap;
		}
		return maxH + n.padY * 2;
	}

	private int grid(Node n) {
		int cols = Math.max(1, n.num("cols", 1));
		int cw = n.num("cw", 16);
		int ch = n.num("ch", 16);
		// Cells narrower than asked when the grid doesn't fit (a narrow screen): never past its edges.
		int room = n.w - n.padX * 2 - (cols - 1) * n.gap;
		if (cols * cw > room) cw = Math.max(16, room / cols);
		int rows = (n.children.size() + cols - 1) / cols;
		int min = n.num("rows", 0);
		int max = Math.max(min, n.num("maxRows", min));
		int visible = min <= 0 ? rows : Math.max(min, Math.min(rows, tall ? max : min));
		n.visibleRows = visible;
		int viewH = visible * (ch + n.gap) - (visible > 0 ? n.gap : 0);
		int fullH = rows * (ch + n.gap) - (rows > 0 ? n.gap : 0);
		n.maxScroll = Math.max(0, fullH - viewH);
		for (int i = 0; i < n.children.size(); i++) {
			Node c = n.children.get(i);
			int cx = n.x + n.padX + (i % cols) * (cw + n.gap);
			int cy = n.y + n.padY + (i / cols) * (ch + n.gap);
			c.h = ch;
			layout(c, cx, cy, cw);
			c.h = ch;
		}
		return viewH + n.padY * 2;
	}

	/** Moves a laid out node (and everything in it) by dx, dy: grids scroll this way. */
	public static void shift(Node n, int dx, int dy) {
		n.x += dx;
		n.y += dy;
		for (Node c : n.children) shift(c, dx, dy);
	}

	// ------------------------------------------------------------------ tabs

	/** The tab boxes [x0, x1] inside a tabs node; the active one gets its name if there is room. */
	public int[][] tabs(Node n) {
		List<Node> all = n.children;
		int count = Math.max(1, all.size());
		int inner = n.w - 4;
		Node active = null;
		for (Node t : all) {
			if (t.flag("active")) active = t;
		}
		boolean labelled = labelled(n, active);
		int[][] out = new int[all.size()][2];
		int x = n.x + 2;
		for (int i = 0; i < all.size(); i++) {
			Node t = all.get(i);
			int w;
			if (labelled) {
				w = t == active ? inner - (all.size() - 1) * TAB_ICON_W : TAB_ICON_W;
			} else {
				w = inner / count + (i == all.size() - 1 ? inner % count : 0);
			}
			out[i][0] = x;
			out[i][1] = x + w;
			x += w;
		}
		return out;
	}

	/** Whether the open tab has room for its name beside the icon (the rest show only icons). */
	public boolean labelled(Node tabs, Node active) {
		if (active == null) return false;
		int wide = tabs.w - 4 - (tabs.children.size() - 1) * TAB_ICON_W;
		return wide >= 16 + 3 + measure.width(active.str("text", ""), false) + 6;
	}

	// ------------------------------------------------------------------ json

	static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	static int num(JsonObject o, String key, int fallback) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
		return e.getAsInt();
	}

	/** Colour: a theme name or #rrggbb / #aarrggbb; fallback if missing or unknown. */
	public static int color(String value, int fallback) {
		if (value == null) return fallback;
		Integer named = Theme.NAMED.get(value);
		if (named != null) return named;
		if (value.startsWith("#")) {
			try {
				long v = Long.parseLong(value.substring(1), 16);
				return value.length() == 7 ? (int) (0xFF000000L | v) : (int) v;
			} catch (NumberFormatException e) {
				return fallback;
			}
		}
		return fallback;
	}

	/** The site's palette (shadcn/ui zinc, dark), the same as the wardrobe's. */
	public static final class Theme {
		public static final int CARD = 0xDB09090B;
		public static final int BORDER = 0xFF27272A;
		public static final int MUTED_BG = 0xFF18181B;
		public static final int HOVER_BG = 0xFF27272A;
		public static final int FG = 0xFFFAFAFA;
		public static final int MUTED = 0xFFA1A1AA;
		public static final int FAINT = 0xFF71717A;
		public static final int DISABLED = 0xFF52525B;
		public static final int PRIMARY = 0xFFFAFAFA;
		public static final int PRIMARY_HOVER = 0xFFE4E4E7;
		public static final int ON_PRIMARY = 0xFF18181B;
		public static final int ACCENT = 0xFFA78BFA;
		public static final int GREEN = 0xFF4ADE80;
		public static final int RED = 0xFFF87171;

		static final Map<String, Integer> NAMED = Map.ofEntries(Map.entry("card", CARD), Map.entry("border", BORDER),
				Map.entry("muted_bg", MUTED_BG), Map.entry("hover", HOVER_BG), Map.entry("fg", FG), Map.entry("muted", MUTED),
				Map.entry("faint", FAINT), Map.entry("disabled", DISABLED), Map.entry("accent", ACCENT), Map.entry("green", GREEN),
				Map.entry("red", RED), Map.entry("none", 0));

		private Theme() {
		}
	}
}
