package pro.minehouse.menu.tab;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What the server says about the tab list (MineHouse Auth {@code TabFeed}, channel
 * {@code minehouse-ui:tab}): roles, Nitro colours, streams, online count and TPS. No game classes,
 * so the parsing, order and grid are unit-tested.
 */
public record TabData(int online, int max, double tps, Map<UUID, Meta> players) {
	/** One player's decorations. {@code role} and {@code color} may be null (no role, plain name). */
	public record Meta(String role, Integer color, boolean live) {
		static final Meta NONE = new Meta(null, null, false);
	}

	/** A row to sort: what the comparator needs from the game's player info. */
	public record Row<T>(T info, String name, boolean spectator, Meta meta) {
	}

	/** Most rows in one column before a new column starts, like vanilla's 20. */
	public static final int MAX_ROWS = 20;
	/** At most this many players are drawn, like vanilla. */
	public static final int MAX_PLAYERS = 80;

	public Meta meta(UUID id) {
		Meta m = players.get(id);
		return m == null ? Meta.NONE : m;
	}

	/** Parses the server's message, or null if it is not one. */
	public static TabData parse(String raw) {
		JsonObject json;
		try {
			json = JsonParser.parseString(raw).getAsJsonObject();
		} catch (RuntimeException e) {
			return null;
		}
		Map<UUID, Meta> players = new HashMap<>();
		if (json.has("players") && json.get("players").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("players").entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				UUID id;
				try {
					id = UUID.fromString(e.getKey());
				} catch (IllegalArgumentException ex) {
					continue;
				}
				JsonObject p = e.getValue().getAsJsonObject();
				String role = p.has("r") ? p.get("r").getAsString() : null;
				players.put(id, new Meta(role, color(p.has("c") ? p.get("c").getAsString() : null), p.has("l") && p.get("l").getAsBoolean()));
			}
		}
		return new TabData(num(json, "online", players.size()), num(json, "max", 0),
				json.has("tps") ? json.get("tps").getAsDouble() : 20.0, Map.copyOf(players));
	}

	private static int num(JsonObject json, String key, int fallback) {
		try {
			return json.has(key) ? json.get(key).getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	/** {@code #rrggbb} to 0xRRGGBB, or null. */
	static Integer color(String hex) {
		if (hex == null || !hex.startsWith("#") || hex.length() != 7) return null;
		try {
			return Integer.parseInt(hex.substring(1), 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Staff first (admin, moderator, helper), then players, then bots; spectators after. */
	public static int rank(String role) {
		if (role == null) return 3;
		return switch (role) {
			case "admin" -> 0;
			case "moderator" -> 1;
			case "helper" -> 2;
			case "bot" -> 4;
			default -> 3;
		};
	}

	public static <T> void sort(List<Row<T>> rows) {
		rows.sort(Comparator.<Row<T>>comparingInt(r -> r.spectator() ? 1 : 0)
				.thenComparingInt(r -> rank(r.meta().role()))
				.thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
	}

	/** Columns and rows for {@code n} players: as few columns as fit {@link #MAX_ROWS}, rows spread evenly. */
	public static int[] grid(int n) {
		if (n <= 0) return new int[] {1, 0};
		int cols = (n + MAX_ROWS - 1) / MAX_ROWS;
		int rows = (n + cols - 1) / cols;
		return new int[] {cols, rows};
	}

	/** Ping colour: green, yellow, orange, red; grey when not known yet. */
	public static int pingColor(int ms) {
		if (ms < 0) return 0xFF71717A;
		if (ms < 80) return 0xFF4ADE80;
		if (ms < 150) return 0xFFFACC15;
		if (ms < 300) return 0xFFFB923C;
		return 0xFFF87171;
	}

	/** Bars lit out of 4 for a ping, like vanilla's icon. */
	public static int pingBars(int ms) {
		if (ms < 0) return 0;
		if (ms < 150) return 4;
		if (ms < 300) return 3;
		if (ms < 600) return 2;
		return 1;
	}

	/** TPS colour: green at 19.5+, yellow at 15+, red below. */
	public static int tpsColor(double tps) {
		if (tps >= 19.5) return 0xFF4ADE80;
		if (tps >= 15) return 0xFFFACC15;
		return 0xFFF87171;
	}
}
