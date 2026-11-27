package pro.minehouse.menu.poker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import pro.minehouse.menu.MinehouseMenu;

/**
 * Local look at the poker table (xvfb-run ./gradlew runSelftestClient -Pselftest=poker): feeds the
 * screen a made-up hand as the server would send it (a stranger's view, then sitting down, the deal,
 * the flop, a raise on the river, the showdown) and screenshots each step as poker-*.png. Prints
 * MINEHOUSE_POKER_OK if the screen stayed open throughout.
 */
final class PokerSelfTest {
	private static int ticks;
	private static String problem;
	private static int seenDisplays;
	private static boolean clickFailed;

	private PokerSelfTest() {
	}

	/**
	 * In the world (-Pselftest=pokerworld -Pjoin=localhost:25565, with bank-mod's runPokerDemo):
	 * asks the server for a demo table with bots, plays check/call, screenshots pw-NN.png every two seconds.
	 */
	static void registerWorld() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.getConnection() == null) return;
			ticks++;
			try {
				if (ticks == 60) shot(client, "pw-00-before.png");
				if (ticks == 70) client.getConnection().sendCommand("poker demo standing");
				// Sit down with a real mouse click (xdotool clicks where this line says, see bank-mod's README): the panel must take the mouse.
				if (ticks >= 100 && ticks < 300 && ticks % 20 == 0 && client.gui.screen() instanceof PokerScreen s && s.you() < 0) {
					int[] b = s.firstButton();
					double k = client.getWindow().getGuiScale();
					if (b != null) MinehouseMenu.LOG.info("POKER_CLICK_AT {} {} (mouse at {} {}, grabbed {}, screen {})", Math.round(b[0] * k), Math.round(b[1] * k), client.mouseHandler.xpos(), client.mouseHandler.ypos(), client.mouseHandler.isMouseGrabbed(), client.gui.screen());
				}
				if (ticks == 300) {
					boolean sat = client.gui.screen() instanceof PokerScreen s && s.you() >= 0;
					MinehouseMenu.LOG.info("Poker world: sat with a mouse click: {}", sat);
					if (!sat) clickFailed = true;
				}
				if (ticks > 300 && client.gui.screen() instanceof PokerScreen s && ticks % 30 == 0) s.autoPlay();
				if (ticks > 70 && ticks % 40 == 0 && ticks <= 70 + 40 * 22) shot(client, String.format("pw-%02d.png", (ticks - 70) / 40));
				if (ticks % 200 == 0 && client.level != null) {
					int displays = 0;
					StringBuilder where = new StringBuilder();
					for (var e : client.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.Display.ItemDisplay d) {
							displays++;
							if (displays <= 3) where.append(' ').append(d.position()).append(d.getItemStack().get(net.minecraft.core.component.DataComponents.ITEM_MODEL));
						}
					}
					MinehouseMenu.LOG.info("Poker world: {} item displays near, player at {}:{}", displays, client.player.position(), where);
					seenDisplays = Math.max(seenDisplays, displays);
				}
				if (ticks == 70 + 40 * 23) {
					MinehouseMenu.LOG.info(!(client.gui.screen() instanceof PokerScreen) ? "MINEHOUSE_POKER_WORLD_FAIL: no table screen"
								: seenDisplays == 0 ? "MINEHOUSE_POKER_WORLD_FAIL: no cards or chips in the world"
								: clickFailed ? "MINEHOUSE_POKER_WORLD_FAIL: a mouse click on Sit did nothing" : "MINEHOUSE_POKER_WORLD_OK");
					client.stop();
				}
			} catch (RuntimeException e) {
				MinehouseMenu.LOG.error("MINEHOUSE_POKER_WORLD_FAIL", e);
				client.stop();
			}
		});
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticks == 0 && (client.gui.overlay() != null || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen))) return;
			ticks++;
			try {
				switch (ticks) {
					case 20 -> {
						PokerNet.receive(client, state(-1, 0, "waiting", false).toString());
						check(client.gui.screen() instanceof PokerScreen, "the table opens");
					}
					case 45 -> shot(client, "poker-sit.png");
					case 50 -> PokerNet.receive(client, state(0, 1, "preflop", true).toString());
					// The deal takes about two seconds.
					case 62 -> shot(client, "poker-deal.png");
					case 100 -> shot(client, "poker-preflop.png");
					case 110 -> PokerNet.receive(client, state(0, 1, "flop", false).toString());
					case 118 -> shot(client, "poker-flop-flying.png");
					case 150 -> shot(client, "poker-flop.png");
					case 160 -> PokerNet.receive(client, state(0, 1, "river", true).toString());
					case 210 -> shot(client, "poker-river.png");
					case 220 -> PokerNet.receive(client, state(0, 1, "done", false).toString());
					case 252 -> shot(client, "poker-showdown.png");
					case 300 -> shot(client, "poker-won.png");
					case 310 -> {
						check(client.gui.screen() instanceof PokerScreen, "the table stays open");
						if (client.gui.screen() instanceof PokerScreen s) s.closeByServer();
						check(!(client.gui.screen() instanceof PokerScreen), "the server closes it");
					}
					case 320 -> {
						MinehouseMenu.LOG.info(problem == null ? "MINEHOUSE_POKER_OK" : "MINEHOUSE_POKER_FAIL: " + problem);
						client.stop();
					}
					default -> {
					}
				}
			} catch (RuntimeException e) {
				MinehouseMenu.LOG.error("MINEHOUSE_POKER_FAIL", e);
				client.stop();
			}
		});
	}

	private static void shot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, msg -> MinehouseMenu.LOG.info("Screenshot: {}", msg.getString()));
	}

	private static void check(boolean ok, String what) {
		if (!ok && problem == null) problem = what;
	}

	private static final String[] NAMES = {"Steve", null, "Alex", "Sunny", null, "Kai"};
	private static final String[] UUIDS = {"8667ba71-b85a-4004-af54-457a9734eed7", null, "ec561538-f3fd-461d-aff5-086b22154bce",
			"069a79f4-44e9-4726-a5be-fca90e38aaf5", null, "61699b2e-d327-4a01-9f1e-0ea8c3f06bc6"};

	/** The table as the server would send it to a viewer in seat {@code you} (-1: standing). */
	private static JsonObject state(int you, int hand, String stage, boolean yourTurn) {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("table", "selftest");
		o.addProperty("open", true);
		o.addProperty("small", 5);
		o.addProperty("big", 10);
		o.addProperty("min", 100);
		o.addProperty("max", 1000);
		o.addProperty("hand", hand);
		o.addProperty("stage", stage);
		o.addProperty("button", 5);
		o.addProperty("to_act", yourTurn ? 0 : "done".equals(stage) || "waiting".equals(stage) ? -1 : 2);
		o.addProperty("left", 21000);
		o.addProperty("turn", 30000);
		boolean done = "done".equals(stage);
		JsonArray board = new JsonArray();
		String[] all = {"QS", "JS", "2H", "7D", "TS"};
		int n = switch (stage) {
			case "flop" -> 3;
			case "turn" -> 4;
			case "river", "done" -> 5;
			default -> 0;
		};
		for (int k = 0; k < n; k++) board.add(all[k]);
		o.add("board", board);
		JsonArray win = new JsonArray();
		if (done) for (String c : new String[] {"AS", "KS", "QS", "JS", "TS"}) win.add(c);
		o.add("win", win);
		JsonArray pots = new JsonArray();
		long pot = switch (stage) {
			case "preflop" -> 15;
			case "flop" -> 40;
			case "river" -> 220;
			case "done" -> 420;
			default -> 0;
		};
		if (done) pots.add(pot);
		o.add("pots", pots);
		o.addProperty("pot", done ? 0 : pot);
		o.addProperty("bet", "river".equals(stage) ? 100 : "preflop".equals(stage) ? 10 : 0);
		o.addProperty("you", you);

		JsonArray seats = new JsonArray();
		for (int i = 0; i < 6; i++) {
			if (NAMES[i] == null || i == 0 && you < 0 && "waiting".equals(stage)) {
				JsonObject e = new JsonObject();
				e.addProperty("empty", true);
				e.addProperty("reserved", false);
				seats.add(e);
				continue;
			}
			JsonObject s = new JsonObject();
			s.addProperty("name", NAMES[i]);
			s.addProperty("uuid", UUIDS[i]);
			boolean inHand = hand > 0;
			boolean folded = i == 3 && !"preflop".equals(stage) && inHand;
			long bet = 0;
			if ("preflop".equals(stage)) bet = i == 0 ? 5 : i == 2 ? 10 : 0;
			if ("river".equals(stage) && i == 2) bet = 100;
			s.addProperty("stack", 500 - bet - (i == 0 || i == 2 ? pot / 3 : 0));
			s.addProperty("bet", bet);
			s.addProperty("in", inHand);
			s.addProperty("folded", folded);
			s.addProperty("allin", false);
			s.addProperty("out", false);
			s.addProperty("leaving", false);
			s.addProperty("won", done && i == 0 ? 420 : 0);
			if (folded) {
				s.addProperty("move", "fold");
				s.addProperty("amount", 0);
			} else if ("river".equals(stage) && i == 2) {
				s.addProperty("move", "raise");
				s.addProperty("amount", 100);
			} else if ("flop".equals(stage) || "river".equals(stage)) {
				s.addProperty("move", "check");
				s.addProperty("amount", 0);
			}
			JsonArray cards = new JsonArray();
			if (inHand && !(folded && i != you)) {
				String[] mine = i == 0 ? new String[] {"AS", "KS"} : i == 2 ? new String[] {"QH", "QD"} : new String[] {"9C", "8C"};
				boolean visible = i == you || done && (i == 0 || i == 2 || i == 5);
				for (String c : mine) cards.add(visible ? c : "??");
			}
			s.add("cards", cards);
			if (done && i == 0) s.addProperty("hand", "роял-флеш");
			else if (done && i == 2) s.addProperty("hand", "сет дам");
			else if (done && i == 5) s.addProperty("hand", "стрит до дамы");
			else if (i == you && n >= 3) s.addProperty("hand", n == 5 ? "роял-флеш" : "флеш-дро");
			seats.add(s);
		}
		o.add("seats", seats);

		if (yourTurn && you >= 0) {
			JsonObject opt = new JsonObject();
			boolean river = "river".equals(stage);
			opt.addProperty("check", false);
			opt.addProperty("call", river ? 100 : 5);
			opt.addProperty("min", river ? 200 : 20);
			opt.addProperty("max", river ? 427 : 500);
			opt.addProperty("mine", river ? 0 : 5);
			o.add("options", opt);
		}
		JsonArray log = new JsonArray();
		String[] lines = switch (stage) {
			case "preflop" -> new String[] {"Раздача #1", "Steve ставит малый блайнд 5", "Alex ставит большой блайнд 10"};
			case "flop" -> new String[] {"Sunny: пас", "Флоп: Q♠ J♠ 2♥", "Steve: чек", "Alex: чек"};
			case "river" -> new String[] {"Тёрн: 7♦", "Ривер: 10♠", "Steve: чек", "Alex: рейз до 100"};
			case "done" -> new String[] {"Steve: колл 100", "Alex: сет дам", "Steve: роял-флеш", "Steve забирает 420 АР"};
			default -> new String[] {"Steve встал из-за стола"};
		};
		for (String line : lines) {
			JsonObject l = new JsonObject();
			l.addProperty("text", line);
			l.addProperty("seat", -1);
			log.add(l);
		}
		o.add("log", log);
		return o;
	}
}
