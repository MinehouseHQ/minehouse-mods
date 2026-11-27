package pro.minehouse.menu.poker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;

import pro.minehouse.menu.Tr;

/**
 * The poker table from above. The server sends the table as this player sees it; this screen
 * keeps cards and chips as {@link Anim}s and plays what changed: cards fly from the deck and turn
 * over, bets slide in front of the players and then to the pot, the pot slides to the winner,
 * the winning five light up. The player's own seat is always at the bottom.
 */
public final class PokerScreen extends Screen {
	static final int CW = 33;
	static final int CH = 51;
	private static final Identifier BACK = Identifier.fromNamespaceAndPath("minehouse-menu", "textures/poker/back.png");

	// Colours: wood, felt, the site's gold.
	private static final int WOOD_DARK = 0xFF4A2C17;
	private static final int WOOD = 0xFF7A4A28;
	private static final int WOOD_LIGHT = 0xFF8E5A32;
	private static final int FELT = 0xFF17603C;
	private static final int FELT_DARK = 0xFF114D30;
	private static final int FELT_LINE = 0xFF2A7E55;
	private static final int GOLD = 0xFFFFD166;
	private static final int PANEL = 0xE6101014;
	private static final int PANEL_BORDER = 0xFF3A3A46;
	private static final int[] CHIP_COLORS = { 0xFFD63C3C, 0xFF3A6ED6, 0xFFECECEC, 0xFF2CA058, 0xFF28282E };
	private static final int[] CHIP_STRIPES = { 0xFFFAECEC, 0xFFECF2FC, 0xFF787882, 0xFFECFAF0, 0xFFFAD25A };

	private JsonObject state;
	private final String table;
	private int you = -1;
	private int hand = -1;
	private String stage = "waiting";
	private int toAct = -1;
	private long receivedAt;
	private long leftMs;
	private long turnMs = 30000;
	private boolean closedByServer;

	private final Anim[] board = new Anim[5];
	private final Anim[][] hole = new Anim[6][2];
	private final Anim[] bets = new Anim[6];
	private final Anim pot = new Anim(0, 0.34F, 1, 0, 0, 0);
	private final long[] betShown = new long[6];
	private final boolean[] dealt = new boolean[6];
	private int boardShown;
	private long potShown;
	private final Set<String> winCards = new HashSet<>();

	private record Floater(String text, int color, int seat, long start) {
	}

	private record Sound(long at, SoundEvent sound, float pitch, float volume) {
	}

	private final List<Floater> floaters = new ArrayList<>();
	private final List<Sound> sounds = new ArrayList<>();

	/** Raise size picked with the slider / wheel, as the total bet ("raise to"). */
	private long raiseTo;
	private long buyIn;
	private boolean dragging;
	/** Turning the camera around the table with the mouse (drag on the free part of the screen). */
	private boolean orbiting;
	/** The hand rankings are open (the «Комбинации» button or H). */
	private boolean showHands;
	/** Where the table's middle is, for turning to it in first person. */
	private Vec3 tableCenter;
	/** First person: how far the held cards are fanned and raised (animated), and whether the mouse holds them up. */
	private float fan, lift;
	private boolean peeking;
	private long lastHandsFrame;
	/** The action panel's top-left corner, for the wheel: over it the wheel moves the slider, elsewhere it zooms. */
	private int panelX0, panelY0;

	// Layout, recomputed each frame.
	private int cx, cy, pw = 196;
	/** The table stands in the world here (see {@link #readWorld}). */
	private boolean world;
	private float rx, ry;
	private final List<Button> buttons = new ArrayList<>();
	private int sliderX0, sliderX1, sliderY;
	private long sliderMin, sliderMax;

	private record Button(int x0, int y0, int x1, int y1, String label, int color, Runnable click, boolean enabled, String key) {
		boolean over(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	PokerScreen(JsonObject json) {
		super(Component.literal("Poker"));
		this.table = json.get("table").getAsString();
		long now = System.currentTimeMillis();
		for (int k = 0; k < 5; k++) board[k] = new Anim(deckX(), deckY(), 0, 0, 1, 0);
		for (int i = 0; i < 6; i++) {
			for (int c = 0; c < 2; c++) hole[i][c] = new Anim(deckX(), deckY(), 0, 0, 1, 0);
			bets[i] = new Anim(0, 0, 1, 0, 0, 0);
		}
		read(json);
		updateTimer(json);
	}

	@Override
	protected void init() {
		super.init();
		// Opening on a hand in progress (or resized): lay out what is there without dealing it again.
		layout();
		snap(System.currentTimeMillis());
	}

	/** Fits the table between the title and the action panel; seats stay clear of the panel. */
	private void layout() {
		pw = width < 560 ? 152 : 196;
		if (world) {
			// Only your cards are drawn: big, at the bottom in the middle, over the table in the world.
			rx = Mth.clamp(width * 0.32F, 120, 260);
			ry = rx * 0.5F;
			cx = width / 2;
			cy = Math.round(height - 34 - CH * HERO * cardScale() / 2 - 0.86F * ry);
			return;
		}
		ry = Mth.clamp((height - 153) / 1.61F, 40, 150);
		rx = Math.max(60, Math.min(Math.min(300, (width / 2F - 39) / 1.048F), ry * 2.1F));
		cx = width / 2;
		cy = Math.round(30 + 1.17F * ry);
	}

	/** Self-test only: on your move, check or call. */
	void autoPlay() {
		if (!state.has("options") || toAct != you) return;
		act(flag(state.getAsJsonObject("options"), "check") ? "check" : "call", 0);
	}

	String table() {
		return table;
	}

	/** Self-test: the middle of the first enabled button (GUI coordinates), or null. */
	int[] firstButton() {
		for (Button b : buttons) if (b.enabled()) return new int[] {(b.x0() + b.x1()) / 2, (b.y0() + b.y1()) / 2};
		return null;
	}

	int you() {
		return you;
	}

	void closeByServer() {
		closedByServer = true;
		onClose();
	}

	@Override
	public void removed() {
		PokerCamera.release();
		PokerCamera.holding = false;
		if (!closedByServer) PokerNet.send(table, "closed", 0, -1);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ state

	private void read(JsonObject json) {
		state = json;
		you = json.get("you").getAsInt();
		toAct = json.get("to_act").getAsInt();
		stage = json.get("stage").getAsString();
		if (buyIn <= 0) buyIn = json.get("min").getAsLong();
		readWorld(json);
	}

	/**
	 * The table stands in the world (in this dimension): its cards and chips are the server's, the
	 * screen only adds the UI, and the camera flies over the table while a seated player plays.
	 */
	private void readWorld(JsonObject json) {
		JsonObject w = json.has("world") && json.get("world").isJsonObject() ? json.getAsJsonObject("world") : null;
		Minecraft mc = Minecraft.getInstance();
		world = w != null && mc.level != null && mc.level.dimension().identifier().toString().equals(w.get("dim").getAsString());
		if (!world) {
			PokerCamera.release();
			return;
		}
		Vec3 center = new Vec3(w.get("x").getAsDouble(), w.get("y").getAsDouble(), w.get("z").getAsDouble());
		float yaw = w.get("yaw").getAsFloat();
		boolean playing = you >= 0 && !"waiting".equals(stage) && !flag(seat(you), "out");
		tableCenter = center;
		PokerCamera.holding = playing && PokerCamera.firstPerson;
		if (playing && !PokerCamera.firstPerson) {
			PokerCamera.over(center, yaw, you);
		} else {
			PokerCamera.release();
		}
	}

	private void updateTimer(JsonObject json) {
		receivedAt = System.currentTimeMillis();
		leftMs = json.get("left").getAsLong();
		turnMs = Math.max(1, json.get("turn").getAsLong());
	}

	private JsonArray seats() {
		return state.getAsJsonArray("seats");
	}

	private JsonObject seat(int i) {
		JsonElement e = seats().get(i);
		return e.isJsonObject() && !e.getAsJsonObject().has("empty") ? e.getAsJsonObject() : null;
	}

	private static long num(JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0;
	}

	private static boolean flag(JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).getAsBoolean();
	}

	private static List<String> cards(JsonObject seat) {
		List<String> out = new ArrayList<>();
		if (seat == null || !seat.has("cards")) return out;
		for (JsonElement e : seat.getAsJsonArray("cards")) out.add(e.getAsString());
		return out;
	}

	private List<String> boardCards() {
		List<String> out = new ArrayList<>();
		for (JsonElement e : state.getAsJsonArray("board")) out.add(e.getAsString());
		return out;
	}

	/** Lays out the table as it is, without animation (screen just opened). */
	private void snap(long now) {
		hand = state.get("hand").getAsInt();
		List<String> b = boardCards();
		for (int k = 0; k < 5; k++) {
			if (k < b.size()) board[k].jump(now, boardX(k), boardY(), 1, 0, 0, 1, b.get(k), 0);
			else board[k].jump(now, deckX(), deckY(), 0, 0, 1, 0, "??", 0);
		}
		boardShown = b.size();
		for (int i = 0; i < 6; i++) {
			JsonObject s = seat(i);
			List<String> c = cards(s);
			dealt[i] = c.size() == 2 && !flag(s, "folded");
			for (int k = 0; k < 2; k++) {
				if (dealt[i]) {
					String code = c.get(k);
					boolean open = i != you && !"??".equals(code);
					float sc = open ? SHOWN : holeScale(i);
					hole[i][k].jump(now, holeX(i, k, sc), holeY(i, k, sc), sc, holeRot(i, k), "??".equals(code) ? 1 : 0, 1, code, 0);
				} else {
					hole[i][k].jump(now, deckX(), deckY(), 0, 0, 1, 0, "??", 0);
				}
			}
			betShown[i] = num(s, "bet");
			bets[i].jump(now, betX(i), betY(i), 1, 0, 0, betShown[i] > 0 ? 1 : 0, null, betShown[i]);
		}
		potShown = potNow();
		pot.jump(now, 0, 0.34F, 1, 0, 0, potShown > 0 ? 1 : 0, null, potShown);
		winCards.clear();
		for (JsonElement e : state.getAsJsonArray("win")) winCards.add(e.getAsString());
		resetRaise();
	}

	private long potNow() {
		long p = num(state, "pot");
		if (p == 0 && "done".equals(stage)) return 0;
		// The pot in the middle is what was collected from earlier streets.
		long bets = 0;
		for (int i = 0; i < 6; i++) bets += num(seat(i), "bet");
		return Math.max(0, p - bets);
	}

	void update(JsonObject json) {
		long now = System.currentTimeMillis();
		JsonObject before = state;
		String stageBefore = stage;
		int handBefore = hand;
		read(json);
		updateTimer(json);
		long t = now;
		int newHand = json.get("hand").getAsInt();

		if (newHand != handBefore && !"waiting".equals(stage)) {
			// Sweep the last hand off the table.
			for (int k = 0; k < boardShown; k++) board[k].go(t, 260, deckX(), deckY(), 0.6F, 0, 1, 0, 0);
			for (int i = 0; i < 6; i++) {
				if (!dealt[i]) continue;
				for (int k = 0; k < 2; k++) hole[i][k].go(t, 260, deckX(), deckY(), 0.6F, 0, 1, 0, 0);
			}
			if (potShown > 0) pot.go(t, 200, 0, 0.34F, 1, 0, 0, 0, 0);
			winCards.clear();
			boardShown = 0;
			potShown = 0;
			hand = newHand;
			t += 320;
			sound(t - 40, SoundEvents.BOOK_PUT, 1.1F, 0.6F);

			// Deal: two rounds from the small blind, one card at a time.
			int button = json.get("button").getAsInt();
			int k = 0;
			for (int round = 0; round < 2; round++) {
				for (int n = 1; n <= 6; n++) {
					int i = Math.floorMod(button + n, 6);
					JsonObject s = seat(i);
					List<String> c = cards(s);
					if (s == null || !flag(s, "in") || c.size() < 2) continue;
					String code = c.get(round);
					long at = t + k * 110L;
					Anim a = hole[i][round];
					a.jump(at, deckX(), deckY(), 0.7F, 0, 1, 1, i == you ? code : "??", 0);
					a.go(at, 300, holeX(i, round), holeY(i, round), holeScale(i), holeRot(i, round), 1, 1, 0.12F);
					sound(at, SoundEvents.BOOK_PAGE_TURN, 1.5F + 0.04F * k, 0.5F);
					k++;
				}
			}
			for (int i = 0; i < 6; i++) dealt[i] = flag(seat(i), "in") && cards(seat(i)).size() == 2;
			// Your own two turn over once they've landed.
			if (you >= 0 && dealt[you]) {
				long at = t + k * 110L + 250;
				for (int c = 0; c < 2; c++) hole[you][c].go(at + c * 90L, 260, holeX(you, c), holeY(you, c), holeScale(you), holeRot(you, c), 0, 1, 0.03F);
				sound(at, SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.6F);
			}
			t += k * 110L + 300;
			java.util.Arrays.fill(betShown, 0);
		}

		// A street ended (bets went to the pot): the board grew, or the hand is over.
		List<String> b = boardCards();
		boolean collect = b.size() > boardShown || "done".equals(stage) && !"done".equals(stageBefore);
		if (collect) {
			boolean any = false;
			for (int i = 0; i < 6; i++) {
				if (betShown[i] <= 0) continue;
				bets[i].go(t + 150, 280, 0, 0.34F, 1, 0, 0, 0, 0.03F);
				betShown[i] = 0;
				any = true;
			}
			if (any) {
				sound(t + 250, SoundEvents.CHAIN_PLACE, 1.4F, 0.8F);
				t += 450;
			}
			long newPot = 0;
			JsonArray pots = state.getAsJsonArray("pots");
			for (JsonElement e : pots) newPot += e.getAsLong();
			if (!"done".equals(stage)) newPot = potNow();
			if (newPot > 0) {
				pot.go(t, 120, 0, 0.34F, 1.15F, 0, 0, 1, 0, null, newPot);
				pot.go(t + 120, 160, 0, 0.34F, 1, 0, 0, 1, 0);
			}
			potShown = newPot;
		}

		// Board: from the deck face down, turning over in the air. Streets dealt at once (all in) come one by one.
		long streetGap = 0;
		for (int k = boardShown; k < b.size(); k++) {
			if (k == 3 || k == 4) streetGap += (k > boardShown) ? 750 : 0;
			long at = t + streetGap + (k < 3 ? (k - Math.min(k, boardShown)) * 130L : 0);
			Anim a = board[k];
			a.jump(at, deckX(), deckY(), 0.7F, 0, 1, 1, b.get(k), 0);
			a.go(at, 420, boardX(k), boardY(), 1, 0, 0, 1, 0.16F);
			sound(at, SoundEvents.BOOK_PAGE_TURN, 1.3F + 0.05F * k, 0.6F);
		}
		if (b.size() > boardShown) t += streetGap + 520 + (b.size() - boardShown >= 3 ? 260 : 0);
		boardShown = b.size();

		// Folds go to the muck; shown hands turn over.
		for (int i = 0; i < 6; i++) {
			JsonObject s = seat(i);
			if (!dealt[i]) continue;
			if (s == null || flag(s, "folded") && i != you) {
				for (int c = 0; c < 2; c++) hole[i][c].go(t, 320, 0, 0.05F, 0.5F, 0.6F * (c - 0.5F), 1, 0, 0.05F);
				dealt[i] = false;
				sound(t, SoundEvents.WOOL_PLACE, 1.4F, 0.6F);
			} else if (s != null && flag(s, "folded") && i == you) {
				for (int c = 0; c < 2; c++) hole[i][c].go(t, 250, holeX(i, c), holeY(i, c) + 0.04F, holeScale(i), holeRot(i, c), 0, 0.45F, 0);
			} else {
				List<String> c = cards(s);
				for (int k = 0; k < 2 && k < c.size(); k++) {
					if (i != you && !"??".equals(c.get(k)) && "??".equals(hole[i][k].code)) {
						long at = t + k * 120L;
						hole[i][k].go(at, 240, holeX(i, k, SHOWN), holeY(i, k, SHOWN) - 0.03F, SHOWN * 1.15F, holeRot(i, k), 0.5F, 1, 0, c.get(k), 0);
						hole[i][k].go(at + 240, 180, holeX(i, k, SHOWN), holeY(i, k, SHOWN), SHOWN, holeRot(i, k), 0, 1, 0);
						sound(at, SoundEvents.BOOK_PAGE_TURN, 1.1F, 0.7F);
					}
				}
			}
		}

		// Bets: chips slide out from the player to the line.
		for (int i = 0; i < 6; i++) {
			JsonObject s = seat(i);
			long bet = num(s, "bet");
			if (bet > betShown[i]) {
				bets[i].jump(t, seatX(i) * 0.86F, seatY(i) * 0.86F, 1, 0, 0, 1, null, bet);
				bets[i].go(t, 260, betX(i), betY(i), 1, 0, 0, 1, 0.04F);
				sound(t, SoundEvents.CHAIN_PLACE, 1.8F, 0.6F);
				betShown[i] = bet;
			}
		}

		// Winners: the pot slides over, the winning five light up, +N floats up.
		if ("done".equals(stage) && !"done".equals(stageBefore)) {
			long at = t + 500;
			int winners = 0;
			for (int i = 0; i < 6; i++) {
				JsonObject s = seat(i);
				long won = num(s, "won");
				if (won <= 0) continue;
				Anim chips = bets[i];
				chips.jump(at, 0, 0.34F, 1.1F, 0, 0, 1, null, won);
				chips.go(at, 520, seatX(i) * 0.86F, seatY(i) * 0.86F, 1, 0, 0, 1, 0.08F);
				chips.go(at + 700, 250, seatX(i) * 0.86F, seatY(i) * 0.86F, 1, 0, 0, 0, 0);
				floaters.add(new Floater("+" + won, GOLD, i, at + 400));
				winners++;
			}
			if (winners > 0) {
				pot.go(at, 120, 0, 0.34F, 1, 0, 0, 0, 0);
				sound(at, SoundEvents.CHAIN_PLACE, 1.3F, 0.9F);
				sound(at + 450, you >= 0 && num(seat(you), "won") > 0 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.7F);
			}
			potShown = 0;
			winCards.clear();
			for (JsonElement e : state.getAsJsonArray("win")) winCards.add(e.getAsString());
		}

		// Your move: a little ping and the raise slider resets.
		if (toAct >= 0 && toAct == you && (before == null || before.get("to_act").getAsInt() != you)) {
			sound(now, SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 0.5F);
			resetRaise();
		}
		if (state.has("options") && (raiseTo < num(state.getAsJsonObject("options"), "min") || raiseTo > num(state.getAsJsonObject("options"), "max"))) resetRaise();
	}

	private void resetRaise() {
		JsonObject o = state.has("options") ? state.getAsJsonObject("options") : null;
		raiseTo = o == null ? 0 : num(o, "min");
	}

	private void sound(long at, SoundEvent sound, float pitch, float volume) {
		sounds.add(new Sound(at, sound, pitch, volume));
	}

	private void sound(long at, net.minecraft.core.Holder<SoundEvent> sound, float pitch, float volume) {
		sounds.add(new Sound(at, sound.value(), pitch, volume));
	}

	// ------------------------------------------------------------------ geometry (table units)

	/** Seat i drawn at d places clockwise from the bottom: your seat is always at the bottom. */
	private int display(int i) {
		return you >= 0 ? Math.floorMod(i - you, 6) : i;
	}

	/** Seat angles from the bottom, clockwise; the two lower ones sit high, clear of the action panel. */
	private static final int[] ANGLES = { 90, 158, 222, 270, 318, 22 };
	/** Card sizes against the board's: yours, others' face down, others' shown. */
	private static final float HERO = 1.25F, OPP = 0.6F, SHOWN = 0.8F;

	private double phi(int i) {
		return Math.toRadians(ANGLES[display(i)]);
	}

	float seatX(int i) {
		return (float) Math.cos(phi(i)) * 1.13F;
	}

	float seatY(int i) {
		return (float) Math.sin(phi(i)) * 1.17F;
	}

	private float holeX(int i, int c) {
		return holeX(i, c, holeScale(i));
	}

	/**
	 * Yours side by side at the bottom; others' face down beside the face, towards the middle;
	 * others' shown ones face up on the felt in front of the seat.
	 */
	private float holeX(int i, int c, float scale) {
		float cw = CW * scale * cardScale();
		float r = Math.max(1, rx);
		if (i == you) return (c - 0.5F) * (cw + 3) / r;
		if (scale >= SHOWN) return seatX(i) * 0.74F + (c - 0.5F) * (cw + 2) / r;
		float side = seatX(i) > 0.05F ? -1 : 1;
		return seatX(i) + side * (14 + cw / 2 + c * cw * 0.62F) / r;
	}

	private float holeY(int i, int c) {
		return holeY(i, c, holeScale(i));
	}

	private float holeY(int i, int c, float scale) {
		if (i == you) return 0.86F;
		if (scale >= SHOWN) return seatY(i) * 0.7F;
		return seatY(i) - 8 / Math.max(1, ry);
	}

	private float holeScale(int i) {
		return i == you ? HERO : OPP;
	}

	private float holeRot(int i, int c) {
		return (c - 0.5F) * (i == you ? 0.12F : 0.16F);
	}

	private float betX(int i) {
		return display(i) == 0 && you >= 0 ? -0.42F : seatX(i) * 0.55F;
	}

	private float betY(int i) {
		return display(i) == 0 && you >= 0 ? 0.5F : seatY(i) * 0.55F;
	}

	/** The dealer button lies on the felt in front of the seat, a little to its left. */
	private float buttonX(int i) {
		return (float) Math.cos(phi(i) - 0.45) * 1.13F * 0.78F;
	}

	private float buttonY(int i) {
		return (float) Math.sin(phi(i) - 0.45) * 1.17F * 0.78F;
	}

	/** The deck: on the drawn felt left of the board; over the table in the world, cards come from above. */
	private float deckX() {
		return world ? 0 : -0.66F;
	}

	private float deckY() {
		return world ? -1.9F : -0.08F;
	}

	private static float boardX(int k) {
		return (k - 2) * 0.205F;
	}

	private static float boardY() {
		return -0.06F;
	}

	/** Board card scale: the felt's width decides. */
	private float cardScale() {
		return rx <= 0 ? 1 : Mth.clamp(rx / 230F, 0.55F, 1.15F);
	}

	private int px(float x) {
		return Math.round(cx + x * rx);
	}

	private int py(float y) {
		return Math.round(cy + y * ry);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// In the world: no shade, the table is right there (and the camera over it while playing).
		if (world) return;
		g.fill(0, 0, width, height, 0xB0050507);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		long now = System.currentTimeMillis();
		playSounds(now);
		layout();
		if (world) {
			drawWorldUi(g, now, mouseX, mouseY);
			return;
		}

		drawTable(g);
		drawDeck(g);
		for (int k = 0; k < 5; k++) {
			if (k >= boardShown) drawSlot(g, k);
		}
		pot.update(now);
		int potRight = pot.a > 0.01F && pot.amount > 0 ? drawChips(g, px(pot.x), py(pot.y), pot.amount, pot.a, pot.s) : px(0) + 8;
		long potText = potShown;
		if (potText > 0 && pot.a > 0.5F) {
			Component t = Component.literal(Tr.s("Банк ", "Pot ") + potText).withStyle(st -> st.withBold(true));
			g.text(font, t, potRight + 4, py(0.34F) - 4, GOLD, true);
		}
		for (int k = 0; k < 5; k++) {
			board[k].update(now);
			drawCard(g, board[k]);
		}
		drawButton(g);
		for (int i = 0; i < 6; i++) if (i != you) drawSeat(g, i, now, mouseX, mouseY);
		for (int i = 0; i < 6; i++) {
			bets[i].update(now);
			if (bets[i].a > 0.01F && bets[i].amount > 0) {
				int right = drawChips(g, px(bets[i].x), py(bets[i].y), bets[i].amount, bets[i].a, 1);
				if (bets[i].a > 0.6F && betShown[i] > 0) g.text(font, Component.literal(Long.toString(bets[i].amount)), right + 3, py(bets[i].y) - 4, 0xFFFFFFFF, true);
			}
		}
		// Others' cards, then yours on top.
		for (int i = 0; i < 6; i++) {
			if (i == you) continue;
			for (int c = 0; c < 2; c++) {
				hole[i][c].update(now);
				drawCard(g, hole[i][c]);
			}
		}
		if (you >= 0) {
			for (int c = 0; c < 2; c++) {
				hole[you][c].update(now);
				drawCard(g, hole[you][c]);
			}
			drawHero(g, now);
		}
		drawFloaters(g, now);
		drawTop(g);
		drawLog(g);
		drawPanel(g, mouseX, mouseY);
		drawHands(g, mouseX, mouseY);
	}

	/** Over the table in the world: your cards, whose turn it is, the log and the panel; the rest is on the table. */
	private void drawWorldUi(GuiGraphicsExtractor g, long now, int mouseX, int mouseY) {
		for (int i = 0; i < 6; i++) {
			bets[i].update(now);
			pot.update(now);
			board[i % 5].update(now);
			for (int c = 0; c < 2; c++) hole[i][c].update(now);
		}
		if (you >= 0) {
			if (PokerCamera.holding) drawHeld(g, now, mouseX, mouseY);
			else if (dealt[you] || hole[you][0].a > 0.01F) {
				for (int c = 0; c < 2; c++) drawCard(g, hole[you][c]);
			}
			drawHero(g, now);
			if (toAct == you && !"done".equals(stage) && state.has("options")) {
				long left = Math.max(0, leftMs - (now - receivedAt));
				int y = py(0.86F) - Math.round(CH * HERO * cardScale() / 2) - 16;
				pill(g, Tr.s("Твой ход · ", "Your move · ") + (left + 999) / 1000 + Tr.s(" с", "s"), cx, y, left < 8000 ? 0xFFF87171 : GOLD);
			}
		}
		for (int k = floaters.size() - 1; k >= 0; k--) if (floaters.get(k).seat() != you) floaters.remove(k);
		drawFloaters(g, now);
		drawTop(g);
		drawLog(g);
		drawPanel(g, mouseX, mouseY);
		drawHands(g, mouseX, mouseY);
		if (PokerCamera.holding) {
			g.centeredText(font, Component.literal(Tr.s("Наведи на карты — раскрыть веер, зажми — поднести ближе · тяни — оглядеться",
					"Hover the cards to fan them, hold to bring them closer · drag to look around")), cx, 6, 0x90E4E4E7);
		} else if (PokerCamera.hidesHud()) {
			Component hint = Component.literal(PokerCamera.turned()
					? Tr.s("Двойной клик — вид с твоего места", "Double-click: back to your seat view")
					: Tr.s("Тяни мышью — крутить стол · колёсико — зум", "Drag to turn the table · wheel to zoom"));
			g.centeredText(font, hint, cx, 6, 0x90E4E4E7);
		}
	}

	private void playSounds(long now) {
		for (int k = sounds.size() - 1; k >= 0; k--) {
			Sound s = sounds.get(k);
			if (s.at() > now) continue;
			sounds.remove(k);
			if (now - s.at() < 400) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s.sound(), s.pitch(), s.volume()));
		}
	}

	/** Wood rim, felt, a line round the felt, the logo faint in the middle. */
	private void drawTable(GuiGraphicsExtractor g) {
		// Shadow under the table.
		ellipse(g, cx + 4, cy + 10, rx + 16, ry + 16, 0x50000000);
		ellipse(g, cx, cy, rx + 16, ry + 16, WOOD_DARK);
		ellipse(g, cx, cy, rx + 13, ry + 13, WOOD);
		// A lighter band on the rim's upper half, like light from above.
		for (int y = -Math.round(ry + 13); y < -Math.round(ry * 0.4F); y++) {
			float k = (float) Math.sqrt(Math.max(0, 1 - (y * y) / ((ry + 11) * (ry + 11))));
			int half = Math.round((rx + 11) * k);
			int inner = Math.round((rx + 2) * (float) Math.sqrt(Math.max(0, 1 - (y * y) / ((ry + 2) * (ry + 2)))));
			if (half > inner) {
				g.fill(cx - half, cy + y, cx - inner, cy + y + 1, WOOD_LIGHT);
				g.fill(cx + inner, cy + y, cx + half, cy + y + 1, WOOD_LIGHT);
			}
		}
		ellipse(g, cx, cy, rx + 2, ry + 2, 0xFF2B1A0E);
		ellipse(g, cx, cy, rx, ry, FELT_DARK);
		ellipse(g, cx, cy - 2, rx - 3, ry - 3, FELT);
		ring(g, cx, cy, rx - 18, ry - 16, FELT_LINE);
		// The logo: MINEHOUSE faint across the middle.
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy - ry * 0.58F);
		pose.scale(1.5F, 1.5F);
		Component logo = Component.literal("MINEHOUSE").withStyle(st -> st.withBold(true));
		g.text(font, logo, -font.width(logo) / 2, -4, 0x30FFFFFF, false);
		pose.popMatrix();
	}

	private void drawDeck(GuiGraphicsExtractor g) {
		float s = 0.7F;
		for (int k = 0; k < 4; k++) {
			blitCard(g, BACK, px(deckX()) - k, py(deckY()) - k, s, -0.15F, 1);
		}
	}

	private void drawSlot(GuiGraphicsExtractor g, int k) {
		int w = Math.round(CW * cardScale());
		int h = Math.round(CH * cardScale());
		int x = px(boardX(k)) - w / 2;
		int y = py(boardY()) - h / 2;
		card(g, x, y, x + w, y + h, 0x18000000, 0x40FFFFFF);
	}

	private void drawCard(GuiGraphicsExtractor g, Anim a) {
		if (a.a <= 0.01F || a.s <= 0.01F) return;
		boolean face = a.f < 0.5F && !"??".equals(a.code);
		Identifier tex = face ? texture(a.code) : BACK;
		// Turning over: squeeze across, the other side shows past the middle.
		float squeeze = Math.abs(1 - 2 * a.f);
		float alpha = a.a;
		boolean dim = "done".equals(stage) && !winCards.isEmpty() && face && !winCards.contains(a.code);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(px(a.x), py(a.y));
		pose.rotate(a.r);
		float cs = a.s * cardScale();
		pose.scale(cs * Math.max(0.04F, squeeze), cs);
		// Shadow.
		g.fill(-CW / 2 + 2, -CH / 2 + 3, CW - CW / 2 + 2, CH - CH / 2 + 3, (int) (alpha * 0x50) << 24);
		boolean glow = "done".equals(stage) && face && winCards.contains(a.code);
		if (glow) {
			int pulse = (int) (160 + 90 * Math.sin(System.currentTimeMillis() / 180.0));
			g.fill(-CW / 2 - 2, -CH / 2 - 2, CW - CW / 2 + 2, CH - CH / 2 + 2, pulse << 24 | (GOLD & 0xFFFFFF));
		}
		int color = ((int) (alpha * 255) << 24) | (dim ? 0x707070 : 0xFFFFFF);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, -CW / 2, -CH / 2, 0F, 0F, CW, CH, CW, CH, CW, CH, color);
		pose.popMatrix();
	}

	private void blitCard(GuiGraphicsExtractor g, Identifier tex, int x, int y, float scale, float rot, float alpha) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate(rot);
		pose.scale(scale, scale);
		g.fill(-CW / 2 + 1, -CH / 2 + 2, CW - CW / 2 + 1, CH - CH / 2 + 2, 0x40000000);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, -CW / 2, -CH / 2, 0F, 0F, CW, CH, CW, CH, CW, CH, ((int) (alpha * 255) << 24) | 0xFFFFFF);
		pose.popMatrix();
	}

	static Identifier texture(String code) {
		return Identifier.fromNamespaceAndPath("minehouse-menu", "textures/poker/" + code.toLowerCase(java.util.Locale.ROOT) + ".png");
	}

	/** A pile of chips: more chips for more АР, colours mixed, the top one with its stripes. */
	/** Returns the pile's right edge, for a label beside it. */
	private int drawChips(GuiGraphicsExtractor g, int x, int y, long amount, float alpha, float scale) {
		long big = Math.max(1, num(state, "big"));
		int n = (int) Mth.clamp(Math.round(1 + Math.log((double) amount / big + 1) / Math.log(1.8)), 1, 10);
		int a = (int) (alpha * 255) << 24;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		float zoom = scale * Math.max(1, cardScale() * 1.2F);
		pose.scale(zoom, zoom);
		// Up to three piles side by side as it grows.
		int piles = n > 6 ? 3 : n > 3 ? 2 : 1;
		int per = (n + piles - 1) / piles;
		int drawn = 0;
		for (int p = 0; p < piles; p++) {
			int ox = (p * 2 - (piles - 1)) * 6;
			for (int k = 0; k < per && drawn < n; k++, drawn++) {
				int c = drawn % CHIP_COLORS.length;
				int yy = -k * 2;
				chip(g, ox, yy, (CHIP_COLORS[c] & 0xFFFFFF) | a, (CHIP_STRIPES[c] & 0xFFFFFF) | a, k == per - 1 || drawn == n - 1);
			}
		}
		pose.popMatrix();
		return x + Math.round(((piles - 1) * 6 + 7) * zoom);
	}

	private static void chip(GuiGraphicsExtractor g, int x, int y, int color, int stripe, boolean top) {
		// 12 x 6 ellipse, 2 px of edge under it.
		int dark = (color & 0xFF000000) | ((color >> 1) & 0x7F7F7F);
		g.fill(x - 5, y + 1, x + 5, y + 4, dark);
		g.fill(x - 6, y, x + 6, y + 3, dark);
		g.fill(x - 4, y - 3, x + 4, y + 2, color);
		g.fill(x - 6, y - 2, x + 6, y + 1, color);
		g.fill(x - 5, y - 3, x + 5, y + 2, color);
		// Edge stripes.
		g.fill(x - 1, y + 2, x + 1, y + 4, stripe);
		g.fill(x - 6, y + 1, x - 5, y + 3, stripe);
		g.fill(x + 5, y + 1, x + 6, y + 3, stripe);
		if (top) {
			g.fill(x - 2, y - 2, x + 2, y + 1, stripe);
			g.fill(x - 1, y - 3, x + 1, y + 2, stripe);
		}
	}

	private void drawSeat(GuiGraphicsExtractor g, int i, long now, int mouseX, int mouseY) {
		JsonObject s = seat(i);
		int x = px(seatX(i));
		int y = py(seatY(i));
		if (s == null) {
			if (you >= 0) return;
			JsonElement e = seats().get(i);
			boolean reserved = e.isJsonObject() && flag(e.getAsJsonObject(), "reserved");
			boolean over = Math.abs(mouseX - x) < 26 && Math.abs(mouseY - y) < 11;
			card(g, x - 26, y - 11, x + 26, y + 11, over ? 0xC0303040 : 0x90000000, over ? GOLD : 0x60FFFFFF);
			Component t = Component.literal(reserved ? "…" : Tr.s("Сесть", "Sit"));
			g.centeredText(font, t, x, y - 4, over ? GOLD : 0xFFD0D0D0);
			return;
		}
		boolean turn = i == toAct && !"done".equals(stage);
		boolean dim = flag(s, "folded") || flag(s, "out");

		// Face, with the timer ring round it on their turn.
		int face = 22;
		int fx = x - face / 2;
		int fy = y - face / 2 - 8;
		if (turn) {
			long left = Math.max(0, leftMs - (now - receivedAt));
			float k = Mth.clamp(left / (float) turnMs, 0, 1);
			timer(g, fx + face / 2, fy + face / 2, face / 2 + 5, k, left < 8000 ? 0xFFF87171 : GOLD);
		}
		card(g, fx - 2, fy - 2, fx + face + 2, fy + face + 2, 0xFF000000, turn ? GOLD : 0xFF3A3A46);
		PlayerSkin skin = skin(s);
		if (skin != null) PlayerFaceExtractor.extractRenderState(g, skin, fx, fy, face);
		if (dim) g.fill(fx, fy, fx + face, fy + face, 0x90000000);

		// Name and stack in a plate under the face, the last move under that.
		Component n = Component.literal(name(s, 64)).withStyle(st -> st.withBold(true));
		String line2 = num(s, "stack") + Tr.s(" АР", " AR");
		int w = Math.max(font.width(n), font.width(line2)) + 10;
		int lx = x - w / 2;
		int ly = fy + face + 4;
		card(g, lx, ly, lx + w, ly + 22, dim ? 0x90000000 : 0xD0000000, turn ? GOLD : 0);
		g.text(font, n, lx + 5, ly + 2, dim ? 0xFF9A9AA5 : 0xFFF5F5F5, true);
		g.text(font, Component.literal(line2), lx + 5, ly + 12, GOLD, true);
		pill(g, moveText(s), x, ly + 24, moveColor(s));
	}

	/** Your seat: cards big at the bottom, name and stack under them, then what you hold. */
	private void drawHero(GuiGraphicsExtractor g, long now) {
		JsonObject me = seat(you);
		if (me == null) return;
		int x = px(0);
		int y = py(0.86F) + Math.round(CH * HERO * cardScale() / 2) + 3;
		boolean dim = flag(me, "folded") || flag(me, "out");
		Component n = Component.literal(name(me, 80)).withStyle(st -> st.withBold(true));
		Component st = Component.literal(num(me, "stack") + Tr.s(" АР", " AR"));
		int face = 10;
		int w = face + 4 + font.width(n) + 6 + font.width(st) + 10;
		int lx = x - w / 2;
		boolean turn = you == toAct && !"done".equals(stage);
		card(g, lx, y, lx + w, y + 13, dim ? 0x90000000 : 0xE0000000, turn ? GOLD : 0x40FFFFFF);
		PlayerSkin skin = skin(me);
		if (skin != null) PlayerFaceExtractor.extractRenderState(g, skin, lx + 3, y + 2, 9);
		g.text(font, n, lx + face + 6, y + 3, dim ? 0xFF9A9AA5 : 0xFFF5F5F5, true);
		g.text(font, st, lx + face + 6 + font.width(n) + 6, y + 3, GOLD, true);
		String hand = dealt[you] && me.has("hand") && !flag(me, "folded") ? me.get("hand").getAsString() : moveText(me);
		pill(g, hand, x, y + 15, me.has("hand") && dealt[you] ? GOLD : moveColor(me));
	}

	/** A short dark label centred on x. */
	private void pill(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		if (text == null || text.isEmpty()) return;
		Component m = Component.literal(text);
		int mw = font.width(m) + 8;
		card(g, x - mw / 2, y, x + mw / 2, y + 12, 0xC0000000, 0);
		g.text(font, m, x - mw / 2 + 4, y + 2, color, false);
	}

	private String name(JsonObject s, int maxWidth) {
		String name = s.get("name").getAsString();
		return font.width(name) <= maxWidth ? name : font.plainSubstrByWidth(name, maxWidth - 6) + "…";
	}

	/** The dealer button on the felt in front of its seat. */
	private void drawButton(GuiGraphicsExtractor g) {
		int b = (int) num(state, "button");
		if ("waiting".equals(stage) || b < 0 || b > 5 || seat(b) == null) return;
		int x = px(buttonX(b));
		int y = py(buttonY(b));
		ellipse(g, x + 1, y + 2, 6, 5, 0x60000000);
		ellipse(g, x, y, 6, 5, 0xFFF5F5F5);
		ellipse(g, x, y - 1, 5, 4, 0xFFFFFFFF);
		g.text(font, Component.literal("D").withStyle(st -> st.withBold(true)), x - 2, y - 4, 0xFF141414, false);
	}

	private String moveText(JsonObject s) {
		if (num(s, "won") > 0 && "done".equals(stage)) return s.has("hand") ? s.get("hand").getAsString() : Tr.s("забирает банк", "takes the pot");
		if (flag(s, "out")) return Tr.s("отошёл", "away");
		if (flag(s, "leaving")) return Tr.s("уходит", "leaving");
		if (flag(s, "folded")) return Tr.s("пас", "fold");
		if (s.has("hand") && "done".equals(stage)) return s.get("hand").getAsString();
		if (!s.has("move")) return "";
		long amt = num(s, "amount");
		return switch (s.get("move").getAsString()) {
			case "check" -> Tr.s("чек", "check");
			case "call" -> Tr.s("колл", "call");
			case "raise" -> Tr.s("рейз ", "raise ") + amt;
			case "all_in" -> Tr.s("олл-ин", "all in");
			default -> "";
		};
	}

	private int moveColor(JsonObject s) {
		if (num(s, "won") > 0) return GOLD;
		if (flag(s, "folded") || flag(s, "out")) return 0xFF9A9AA5;
		String m = s.has("move") ? s.get("move").getAsString() : "";
		return switch (m) {
			case "raise", "all_in" -> 0xFFF87171;
			case "call" -> 0xFF7DD3FC;
			default -> 0xFFD4D4D8;
		};
	}

	private PlayerSkin skin(JsonObject s) {
		try {
			UUID id = UUID.fromString(s.get("uuid").getAsString());
			Minecraft mc = Minecraft.getInstance();
			PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
			return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** A ring of dots round (x, y), the part {@code k} of it lit, going clockwise from the top. */
	private static void timer(GuiGraphicsExtractor g, int x, int y, int r, float k, int color) {
		int n = (int) (2 * Math.PI * r / 2.2);
		for (int i = 0; i < n; i++) {
			float t = i / (float) n;
			double a = -Math.PI / 2 + 2 * Math.PI * t;
			int dx = (int) Math.round(Math.cos(a) * r);
			int dy = (int) Math.round(Math.sin(a) * r);
			g.fill(x + dx - 1, y + dy - 1, x + dx + 1, y + dy + 1, t <= k ? color : 0x40FFFFFF);
		}
	}

	private void drawFloaters(GuiGraphicsExtractor g, long now) {
		for (int k = floaters.size() - 1; k >= 0; k--) {
			Floater f = floaters.get(k);
			float t = (now - f.start()) / 1600F;
			if (t > 1) {
				floaters.remove(k);
				continue;
			}
			if (t < 0) continue;
			int x = px(seatX(f.seat()) * 0.86F);
			int y = py(seatY(f.seat()) * 0.86F) - 10 - Math.round(24 * Anim.easeOut(t));
			int a = (int) (255 * Math.min(1, (1 - t) * 3));
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, y);
			float s = 1.4F + 0.4F * (float) Math.sin(Math.min(1, t * 4) * Math.PI);
			pose.scale(s, s);
			Component c = Component.literal(f.text()).withStyle(st -> st.withBold(true));
			g.text(font, c, -font.width(c) / 2, -4, (a << 24) | (f.color() & 0xFFFFFF), true);
			pose.popMatrix();
		}
	}

	private void drawTop(GuiGraphicsExtractor g) {
		String title = state.has("title") ? state.get("title").getAsString() : table;
		Component t = Component.literal(Tr.s("Покер · ", "Poker · ") + title).withStyle(st -> st.withBold(true));
		Component b = Component.literal(Tr.s("блайнды ", "blinds ") + num(state, "small") + "/" + num(state, "big") + Tr.s(" АР", " AR"));
		int w = Math.max(font.width(t), font.width(b)) + 12;
		card(g, 6, 6, 6 + w, 30, PANEL, PANEL_BORDER);
		g.text(font, t, 12, 9, 0xFFF5F5F5, false);
		g.text(font, b, 12, 19, 0xFFA1A1AA, false);
		if ("waiting".equals(stage) || "done".equals(stage) && num(state, "hand") == 0) {
			int ready = 0;
			for (int i = 0; i < 6; i++) if (seat(i) != null && num(seat(i), "stack") > 0 && !flag(seat(i), "out")) ready++;
			// Standing at the table does not count: say how many sit and, to someone standing, how to sit.
			String text;
			if (ready >= 2) text = Tr.s("Сейчас раздадим…", "Dealing soon…");
			else if (you < 0) text = ready == 1 ? Tr.s("За столом 1 из 2 — садись: кнопка справа внизу", "1 of 2 seated — take a seat: button bottom right")
					: Tr.s("Сядь за стол (кнопка справа внизу), нужно двое", "Take a seat (button bottom right), two needed");
			else text = Tr.s("Ждём второго игрока: пусть сядет за стол", "Waiting for a second player to take a seat");
			Component m = Component.literal(text);
			int mw = font.width(m) + 16;
			int my = world ? 36 : py(0.3F);
			card(g, cx - mw / 2, my, cx + mw / 2, my + 16, 0xA0000000, 0);
			g.centeredText(font, m, cx, my + 4, 0xFFE4E4E7);
		}
	}

	/** The last few moves, bottom left, under the lower left seat and clear of your cards. */
	private void drawLog(GuiGraphicsExtractor g) {
		JsonArray log = state.getAsJsonArray("log");
		if (log == null || log.isEmpty()) return;
		int left = 6;
		int right = cx - 64;
		if (right - left < 60) return;
		int lowerLeft = 1;
		for (int i = 0; i < 6; i++) if (display(i) == 1) lowerLeft = i;
		int top = world ? height - 66 : py(seatY(lowerLeft)) + 44;
		int n = Math.min(log.size(), Math.min(6, (height - 6 - top) / 10));
		if (n <= 0) return;
		int y = height - 6 - n * 10;
		for (int k = log.size() - n; k < log.size(); k++) {
			String text = log.get(k).getAsJsonObject().get("text").getAsString();
			if (font.width(text) > right - left) text = font.plainSubstrByWidth(text, right - left - 6) + "…";
			int color = k == log.size() - 1 ? 0xFFE4E4E7 : 0xFF8A8A95;
			g.text(font, Component.literal(text), left, y, color, true);
			y += 10;
		}
	}

	// ------------------------------------------------------------------ the action panel

	private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		buttons.clear();
		sliderMax = 0;
		int x0 = width - pw - 6;
		int x1 = width - 6;
		int y1 = height - 6;
		JsonObject o = state.has("options") ? state.getAsJsonObject("options") : null;
		JsonObject me = you >= 0 ? seat(you) : null;
		int bw = (pw - 16) / 3;

		if (o != null) {
			long min = num(o, "min");
			long max = num(o, "max");
			long call = num(o, "call");
			boolean check = flag(o, "check");
			int y0 = y1 - (max > 0 ? 70 : 36);
			card(g, x0, y0, x1, y1, PANEL, GOLD);
			// Time left to move, along the top edge.
			long left = Math.max(0, leftMs - (System.currentTimeMillis() - receivedAt));
			float k = Mth.clamp(left / (float) turnMs, 0, 1);
			g.fill(x0 + 3, y0 + 2, x0 + 3 + Math.round((pw - 6) * k), y0 + 3, left < 8000 ? 0xFFF87171 : GOLD);
			int by = y1 - 30;
			button(x0 + 6, by, x0 + 6 + bw, by + 24, Tr.s("Пас", "Fold"), 0xFF8A2F2F, () -> act("fold", 0), true, "F");
			button(x0 + 8 + bw, by, x0 + 8 + 2 * bw, by + 24, check ? Tr.s("Чек", "Check") : Tr.s("Колл\n", "Call\n") + call, 0xFF2F5A8A,
					() -> act(check ? "check" : "call", 0), true, "C");
			boolean allIn = max <= 0 || raiseTo >= max;
			button(x0 + 10 + 2 * bw, by, x1 - 6, by + 24, allIn ? Tr.s("Олл-ин", "All in") : Tr.s("Рейз\n", "Raise\n") + raiseTo,
					0xFF2F7A4A, () -> {
						if (max <= 0 || raiseTo >= max) act("allin", 0);
						else act("raise", raiseTo);
					}, max > 0 || !check && call < num(me, "stack") + call, "R");
			if (max > 0) {
				long pot = num(state, "pot");
				long mine = num(o, "mine");
				long half = Mth.clamp(mine + call + (pot + call) / 2, min, max);
				long full = Mth.clamp(mine + call + pot + call, min, max);
				String[] names = { Tr.s("Мин", "Min"), "½", Tr.s("Банк", "Pot"), Tr.s("Всё", "Max") };
				long[] values = { min, half, full, max };
				int pwid = (pw - 18) / 4;
				for (int p = 0; p < 4; p++) {
					long v = values[p];
					int bx = x0 + 6 + p * (pwid + 2);
					button(bx, y0 + 6, bx + pwid, y0 + 20, names[p], raiseTo == v ? 0xFF6A5520 : 0xFF26262E, () -> raiseTo = v, true, null);
				}
				slider(g, x0 + 10, x1 - 10, y0 + 29, min, max, raiseTo);
			}
		} else if (me != null) {
			boolean inHand = flag(me, "in") && !"done".equals(stage) && !"waiting".equals(stage);
			long stack = num(me, "stack");
			long max = num(state, "max");
			int y0 = y1 - 62;
			card(g, x0, y0, x1, y1, PANEL, PANEL_BORDER);
			int by = y0 + 6;
			int half = (pw - 14) / 2;
			if (flag(me, "out")) button(x0 + 6, by, x0 + 6 + half, by + 24, Tr.s("Вернуться", "Back in"), 0xFF2F7A4A, () -> act("back", 0), true, null);
			else button(x0 + 6, by, x0 + 6 + half, by + 24, Tr.s("Отойти", "Sit out"), 0xFF3A3A46, () -> act("sitout", 0), true, null);
			long add = Math.max(0, Math.min(max - stack, num(state, "min")));
			button(x0 + 8 + half, by, x1 - 6, by + 24, Tr.s("Докупить\n", "Top up\n") + add + Tr.s(" фишек", " chips"), 0xFF5A4A1A, () -> act("topup", add), !inHand && add > 0, null);
			button(x0 + 6, y1 - 28, x1 - 6, y1 - 6, Tr.s("Выйти и забрать ", "Leave and cash out ") + stack + Tr.s(" фишек", " chips"), 0xFF6A2F2F,
					() -> act("leave", 0), true, null);
			if (toAct >= 0 && toAct != you && !"done".equals(stage)) {
				JsonObject who = seat(toAct);
				if (who != null) {
					Component t = Component.literal(Tr.s("Ходит ", "To act: ") + name(who, 90));
					g.text(font, t, x1 - font.width(t) - 2, y0 - 11, 0xFFA1A1AA, true);
				}
			}
		} else {
			long min = num(state, "min");
			long max = num(state, "max");
			int y0 = y1 - 62;
			card(g, x0, y0, x1, y1, PANEL, GOLD);
			g.text(font, Component.literal(Tr.s("За стол", "Take a seat")).withStyle(st -> st.withBold(true)), x0 + 8, y0 + 6, 0xFFF5F5F5, false);
			Component range = Component.literal(min + "–" + max);
			g.text(font, range, x1 - 8 - font.width(range), y0 + 6, 0xFF8A8A95, false);
			buyIn = Mth.clamp(buyIn, min, max);
			slider(g, x0 + 10, x1 - 10, y0 + 25, min, max, buyIn);
			button(x0 + 6, y1 - 28, x1 - 6, y1 - 6, Tr.s("Купить ", "Buy ") + buyIn + Tr.s(" фишек и сесть за стол", " chips and sit down"), 0xFF2F7A4A,
					() -> act("sit", buyIn), true, null);
		}

		panelX0 = x0;
		panelY0 = height;
		for (Button b : buttons) panelY0 = Math.min(panelY0, b.y0() - 8);
		if (sliderMax > 0) panelY0 = Math.min(panelY0, sliderY - 12);
		for (Button b : buttons) {
			boolean over = b.enabled() && b.over(mouseX, mouseY);
			int bg = !b.enabled() ? 0xFF1E1E24 : over ? lighten(b.color()) : b.color();
			card(g, b.x0(), b.y0(), b.x1(), b.y1(), bg, over ? 0xFFFFFFFF : 0x50FFFFFF);
			int color = b.enabled() ? 0xFFFFFFFF : 0xFF5A5A65;
			String[] lines = b.label().split("\n");
			int mid = (b.x0() + b.x1()) / 2;
			int top = (b.y0() + b.y1()) / 2 - lines.length * 5 + 1;
			for (int l = 0; l < lines.length; l++) {
				Component c = Component.literal(lines[l]).withStyle(st -> st.withBold(lines.length == 1 && b.y1() - b.y0() > 18));
				g.centeredText(font, c, mid, top + l * 10, l == 1 ? (b.enabled() ? 0xFFFFE6A0 : color) : color);
			}
			if (b.key() != null && b.enabled() && b.x1() - b.x0() > 40) g.text(font, Component.literal(b.key()), b.x1() - 7, b.y0() + 2, 0x70FFFFFF, false);
		}
	}

	/** The raise or buy-in slider: a track, the gold part up to the value, a knob. */
	private void slider(GuiGraphicsExtractor g, int x0, int x1, int y, long min, long max, long value) {
		sliderX0 = x0;
		sliderX1 = x1;
		sliderY = y;
		sliderMin = min;
		sliderMax = max;
		g.fill(x0, y - 1, x1, y + 2, 0xFF3A3A46);
		float k = max == min ? 1 : (value - min) / (float) (max - min);
		int kx = x0 + Math.round((x1 - x0) * Mth.clamp(k, 0, 1));
		g.fill(x0, y - 1, kx, y + 2, GOLD);
		card(g, kx - 4, y - 5, kx + 4, y + 6, 0xFFFFE6A0, 0xFF8A6A20);
	}

	private static int lighten(int c) {
		int r = Math.min(255, ((c >> 16) & 0xFF) + 30);
		int gg = Math.min(255, ((c >> 8) & 0xFF) + 30);
		int b = Math.min(255, (c & 0xFF) + 30);
		return 0xFF000000 | r << 16 | gg << 8 | b;
	}

	private void button(int x0, int y0, int x1, int y1, String label, int color, Runnable click, boolean enabled, String key) {
		buttons.add(new Button(x0, y0, x1, y1, label, color, click, enabled, key));
	}

	private void act(String a, long amount) {
		PokerNet.send(table, a, amount, -1);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
	}

	// ------------------------------------------------------------------ first person

	/** Between the camera over the table and first person with your cards in your hands. */
	private void toggleView() {
		PokerCamera.firstPerson = !PokerCamera.firstPerson;
		if (PokerCamera.firstPerson) lookAtTable();
		if (state != null) readWorld(state);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.1F));
	}

	/** Turns you to look down at the middle of the table. */
	private void lookAtTable() {
		if (minecraft.player == null || tableCenter == null) return;
		Vec3 eyes = minecraft.player.getEyePosition();
		Vec3 d = tableCenter.subtract(eyes);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) Math.toDegrees(Math.atan2(-d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		minecraft.player.setYRot(yaw);
		minecraft.player.setXRot(Mth.clamp(pitch, -90, 90));
		minecraft.player.yRotO = yaw;
		minecraft.player.xRotO = minecraft.player.getXRot();
	}

	/** Whether the mouse is over the held cards (the bottom middle of the screen). */
	private boolean overHeld(double mx, double my) {
		return Math.abs(mx - cx) < height * 0.3 && my > height * 0.5;
	}

	/** Your two cards in your hands, in 3D (PokerHands), with your name and hand under them. */
	private void drawHeld(GuiGraphicsExtractor g, long now, int mouseX, int mouseY) {
		float dt = lastHandsFrame == 0 ? 0 : Math.min(0.1F, (now - lastHandsFrame) / 1000F);
		lastHandsFrame = now;
		boolean over = overHeld(mouseX, mouseY);
		fan += ((over || peeking ? 1 : 0.15F) - fan) * Math.min(1, dt * 9);
		lift += ((peeking ? 1 : 0) - lift) * Math.min(1, dt * 8);
		String[] cards = null;
		if (dealt[you] || hole[you][0].a > 0.01F) cards = new String[] { hole[you][0].code, hole[you][1].code };
		float swayX = Mth.clamp((mouseX - cx) / (float) Math.max(1, width / 2), -1, 1);
		float swayY = Mth.clamp((mouseY - height * 0.7F) / (height * 0.3F), -1, 1);
		var skin = minecraft.player == null ? null : minecraft.player.getSkin();
		int y0 = Math.round(height * 0.28F);
		g.guiRenderState.addPicturesInPictureState(new PokerHands.State(cards, fan, swayX, swayY, lift, skin, 0, y0, width, height, height * 1.05F));
	}

	// ------------------------------------------------------------------ hand rankings

	/** The hands from best to worst: names and an example (cards in lower case are kickers, drawn dim). */
	private static final String[][] HANDS = {
			{ "Роял-флеш", "Royal flush", "AS KS QS JS TS" },
			{ "Стрит-флеш", "Straight flush", "9H 8H 7H 6H 5H" },
			{ "Каре", "Four of a kind", "QC QD QH QS 7d" },
			{ "Фулл-хаус", "Full house", "KH KC KD 4S 4H" },
			{ "Флеш", "Flush", "AD JD 8D 6D 2D" },
			{ "Стрит", "Straight", "TC 9D 8S 7H 6C" },
			{ "Сет", "Three of a kind", "7S 7H 7D kc 2s" },
			{ "Две пары", "Two pair", "JH JS 5C 5D ah" },
			{ "Пара", "Pair", "AS AH kc 9d 4s" },
			{ "Старшая карта", "High card", "AC qd 9s 6h 3c" },
	};

	/** Which row of {@link #HANDS} the hand name the server sent (e.g. «пара тузов», «flush») is. */
	private static int handRow(String hand) {
		if (hand == null) return -1;
		String h = hand.toLowerCase(java.util.Locale.ROOT);
		String[][] starts = { { "роял", "royal" }, { "стрит-флеш", "straight flush" }, { "каре", "four" }, { "фулл", "full" },
				{ "флеш", "flush" }, { "стрит", "straight" }, { "сет", "three" }, { "две пары", "two pair" }, { "пара", "pair" },
				{ "старшая", " high" } };
		for (int i = 0; i < starts.length; i++) {
			if (h.startsWith(starts[i][0]) || (i == 9 ? h.endsWith(starts[i][1]) : h.startsWith(starts[i][1]))) return i;
		}
		return -1;
	}

	/** The «Комбинации» button under the title and, when open, the list of hands with yours lit up. */
	private void drawHands(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		String label = Tr.s("Комбинации", "Hand rankings");
		int bx1 = 6 + font.width(label) + 22;
		button(6, 34, bx1, 48, label, showHands ? 0xFF6A5520 : 0xFF26262E, () -> showHands = !showHands, true, null);
		Button b = buttons.get(buttons.size() - 1);
		boolean over = b.over(mouseX, mouseY);
		card(g, b.x0(), b.y0(), b.x1(), b.y1(), over ? lighten(b.color()) : b.color(), over ? 0xFFFFFFFF : 0x50FFFFFF);
		g.text(font, Component.literal(label), b.x0() + 6, b.y0() + 3, 0xFFFFFFFF, false);
		g.text(font, Component.literal("H"), b.x1() - 9, b.y0() + 3, 0x70FFFFFF, false);
		if (world && you >= 0) {
			String view = PokerCamera.firstPerson ? Tr.s("Вид сверху", "Top view") : Tr.s("От первого лица", "First person");
			int vx0 = bx1 + 4;
			int vx1 = vx0 + font.width(view) + 22;
			button(vx0, 34, vx1, 48, view, 0xFF26262E, this::toggleView, true, null);
			Button v = buttons.get(buttons.size() - 1);
			boolean vo = v.over(mouseX, mouseY);
			card(g, v.x0(), v.y0(), v.x1(), v.y1(), vo ? lighten(v.color()) : v.color(), vo ? 0xFFFFFFFF : 0x50FFFFFF);
			g.text(font, Component.literal(view), v.x0() + 6, v.y0() + 3, 0xFFFFFFFF, false);
			g.text(font, Component.literal("V"), v.x1() - 9, v.y0() + 3, 0x70FFFFFF, false);
		}
		if (!showHands) return;

		JsonObject me = you >= 0 ? seat(you) : null;
		int mine = me != null && me.has("hand") ? handRow(me.get("hand").getAsString()) : -1;
		int rowH = 25;
		int x0 = 6;
		int y0 = 52;
		int x1 = x0 + 232;
		int y1 = y0 + 20 + HANDS.length * rowH + 4;
		card(g, x0, y0, x1, y1, PANEL, GOLD);
		g.text(font, Component.literal(Tr.s("От сильной к слабой", "Best to worst")).withStyle(st -> st.withBold(true)), x0 + 8, y0 + 7, 0xFFF5F5F5, false);
		for (int row = 0; row < HANDS.length; row++) {
			final int i = row;
			int y = y0 + 20 + i * rowH;
			if (i == mine) g.fill(x0 + 3, y, x1 - 3, y + rowH - 1, 0x40E0B040);
			int color = i == mine ? GOLD : 0xFFE4E4E7;
			g.text(font, Component.literal((i + 1) + "."), x0 + 8, y + 8, 0xFF8A8A95, false);
			Component name = Component.literal(Tr.s(HANDS[i][0], HANDS[i][1])).withStyle(st -> st.withBold(i == mine));
			g.text(font, name, x0 + 24, y + 8, color, false);
			String[] cards = HANDS[i][2].split(" ");
			for (int c = 0; c < cards.length; c++) {
				boolean kicker = Character.isLowerCase(cards[c].charAt(cards[c].length() - 1));
				int cx0 = x1 - 8 - (cards.length - c) * 16;
				miniCard(g, texture(cards[c]), cx0, y + 1, kicker);
			}
		}
	}

	/** A small card for the rankings: 15×23, dim when it is only a kicker. */
	private static void miniCard(GuiGraphicsExtractor g, Identifier tex, int x, int y, boolean dim) {
		int w = 15;
		int h = 23;
		g.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x50000000);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, CW, CH, CW, CH, dim ? 0xFF6A6A6A : 0xFFFFFFFF);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		// Left is not 0 here: 26.3 numbers mouse buttons its own way (InputConstants).
		boolean left = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
		if (!left) {
			// Right (or middle) button: drag anywhere to turn the camera around the table (or your head).
			if (PokerCamera.wanted() || PokerCamera.holding) {
				orbiting = true;
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		for (Button b : buttons) {
			if (b.enabled() && b.over(mx, my)) {
				b.click().run();
				return true;
			}
		}
		if (sliderMax > 0 && my > sliderY - 8 && my < sliderY + 9 && mx > sliderX0 - 6 && mx < sliderX1 + 6) {
			dragging = true;
			slide(mx);
			return true;
		}
		// An empty seat: sit there.
		if (you < 0 && !world) {
			for (int i = 0; i < 6; i++) {
				if (seat(i) != null) continue;
				int x = px(seatX(i));
				int y = py(seatY(i));
				if (Math.abs(mx - x) < 26 && Math.abs(my - y) < 14) {
					PokerNet.send(table, "sit", buyIn, i);
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
					return true;
				}
			}
		}
		if (PokerCamera.holding) {
			if (overHeld(mx, my)) peeking = true;
			else orbiting = true;
			return true;
		}
		// The free part of the screen: drag to turn the camera, double-click to look from your seat again.
		if (PokerCamera.wanted()) {
			if (doubleClick) PokerCamera.resetView();
			orbiting = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			slide(event.x());
			return true;
		}
		if (orbiting) {
			if (PokerCamera.holding) {
				if (minecraft.player != null) minecraft.player.turn(dx * 2, dy * 2);
			} else {
				PokerCamera.orbit(dx, dy);
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		orbiting = false;
		peeking = false;
		return super.mouseReleased(event);
	}

	private void slide(double mx) {
		float k = Mth.clamp((float) ((mx - sliderX0) / Math.max(1, sliderX1 - sliderX0)), 0, 1);
		long big = Math.max(1, num(state, "big"));
		long v = sliderMin + Math.round((sliderMax - sliderMin) * k);
		// Round to the big blind, except at the ends.
		if (v != sliderMin && v != sliderMax) v = Mth.clamp(Math.round((double) v / big) * big, sliderMin, sliderMax);
		if (state.has("options")) raiseTo = v;
		else buyIn = v;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		long big = Math.max(1, num(state, "big"));
		// Over the table (not the action panel) the wheel zooms the camera.
		if (PokerCamera.wanted() && !(x >= panelX0 && y >= panelY0)) {
			PokerCamera.zoom(scrollY);
			return true;
		}
		if (state.has("options") && sliderMax > 0) {
			raiseTo = Mth.clamp(raiseTo + Math.round(Math.signum(scrollY)) * big, sliderMin, sliderMax);
			return true;
		}
		if (you < 0 && sliderMax > 0) {
			buyIn = Mth.clamp(buyIn + Math.round(Math.signum(scrollY)) * big * 5, sliderMin, sliderMax);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_H) {
			showHands = !showHands;
			return true;
		}
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_V && world && you >= 0) {
			toggleView();
			return true;
		}
		if (state.has("options")) {
			for (Button b : buttons) {
				if (b.key() == null || !b.enabled()) continue;
				if (key == b.key().charAt(0) || key == Character.toLowerCase(b.key().charAt(0))) {
					b.click().run();
					return true;
				}
			}
			if (key == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) {
				JsonObject o = state.getAsJsonObject("options");
				act(flag(o, "check") ? "check" : "call", 0);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ shapes

	/** A filled ellipse round (x, y), one fill per row. */
	private static void ellipse(GuiGraphicsExtractor g, int x, int y, float rx, float ry, int color) {
		int h = Math.round(ry);
		for (int dy = -h; dy <= h; dy++) {
			float k = (float) Math.sqrt(Math.max(0, 1 - (dy * dy) / (ry * ry)));
			int half = Math.round(rx * k);
			g.fill(x - half, y + dy, x + half, y + dy + 1, color);
		}
	}

	/** A 1 px ellipse outline. */
	private static void ring(GuiGraphicsExtractor g, int x, int y, float rx, float ry, int color) {
		int n = (int) (Math.PI * (rx + ry));
		for (int i = 0; i < n; i++) {
			double a = 2 * Math.PI * i / n;
			int dx = (int) Math.round(Math.cos(a) * rx);
			int dy = (int) Math.round(Math.sin(a) * ry);
			g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color);
		}
	}

	/** A box with 2 px rounded corners. */
	static void card(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int bg, int border) {
		if (bg != 0) {
			g.fill(x0 + 2, y0, x1 - 2, y1, bg);
			g.fill(x0, y0 + 2, x0 + 2, y1 - 2, bg);
			g.fill(x1 - 2, y0 + 2, x1, y1 - 2, bg);
			g.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, bg);
			g.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, bg);
			g.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, bg);
			g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, bg);
		}
		if (border == 0) return;
		g.fill(x0 + 2, y0, x1 - 2, y0 + 1, border);
		g.fill(x0 + 2, y1 - 1, x1 - 2, y1, border);
		g.fill(x0, y0 + 2, x0 + 1, y1 - 2, border);
		g.fill(x1 - 1, y0 + 2, x1, y1 - 2, border);
		g.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, border);
		g.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, border);
		g.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, border);
		g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, border);
	}
}
