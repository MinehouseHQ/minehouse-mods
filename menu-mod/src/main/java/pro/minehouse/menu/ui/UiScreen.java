package pro.minehouse.menu.ui;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3fc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;

import pro.minehouse.menu.explainer.Explainer;
import pro.minehouse.menu.explainer.ExplainerRenderer;
import pro.minehouse.menu.ui.UiTree.Node;
import pro.minehouse.menu.ui.UiTree.Theme;
import pro.minehouse.menu.wardrobe.Fitting;
import pro.minehouse.menu.wardrobe.WardrobePreview;

/**
 * A window the server described (see {@link UiNet}), drawn in the MineHouse style: floating zinc
 * cards with the Minecraft font, as the wardrobe of 1.24.0. Updates from the server keep the scroll
 * positions (by node id). With scene "fitting" the camera stands in front of the player in the
 * world, the panels hang beside them and dragging turns them round.
 */
public final class UiScreen extends Screen implements Fitting {
	private static final Identifier COIN = Identifier.fromNamespaceAndPath("minehouse-menu", "textures/gui/coin.png");
	private static final int EDGE = 8;

	private record Panel(String at, int width, Node node) {
	}

	private record Marker(String at, boolean active, String tooltip, JsonObject click) {
	}

	private JsonObject json;
	private String id;
	private int rev;
	private String scene;
	private final List<Panel> panels = new ArrayList<>();
	private final List<Marker> markers = new ArrayList<>();
	private int linkFrom = -1;
	private String linkTo;
	private final List<String[]> hints = new ArrayList<>();
	private final Map<String, String> wear = new HashMap<>();
	private final Map<String, Integer> tint = new HashMap<>();

	/** Grid scroll by node id, kept across updates. */
	private final Map<String, Double> scrolls = new HashMap<>();
	private final Map<String, ItemStackRenderState> models = new HashMap<>();
	private final Map<String, ItemStack> items = new HashMap<>();
	private final Map<Node, Integer> grids = new IdentityHashMap<>();
	/** Text fields by node id: what was typed survives updates until the server sends another value. */
	private final Map<String, UiField> fields = new HashMap<>();
	/** Sliders by node id, kept the same way. */
	private final Map<String, UiSlider> sliders = new HashMap<>();
	/** The slider being dragged (its node id), or null. */
	private String sliding;
	/** The field that takes the keyboard (its node id), or null. */
	private String focused;
	private boolean firstRead = true;

	private float previewYaw = 20;
	private boolean dragging;
	private Boolean hudWasHidden;
	private boolean confirmingLink;
	private boolean closedByServer;
	private int[] markerXY = new int[0];
	private int barX0, barY0, barX1, barY1;

	/** Scene "wheel": the player wheel instead of panels. */
	private UiWheel wheel;
	private int wheelHover = -1;
	private float[] wheelGrow = new float[0];
	private final long openedAt = System.currentTimeMillis();
	/** The right button went down while this screen was up, or was already held when it opened (Shift + right click). */
	private boolean rightHeld = true;
	private boolean wheelChosen;
	/** Shift closes the wheel only once it was up: it is often still held from Shift + right click. */
	private boolean shiftArmed;
	/** Self-test: where the mouse is taken to be. */
	int[] testMouse;

	UiScreen(JsonObject json) {
		super(Component.literal(UiTree.str(json, "title", "")));
		read(json);
	}

	String id() {
		return id;
	}

	/** Self-test: what is in a field, or where a slider stands. */
	String fieldText(String field) {
		UiField f = fields.get(field);
		return f == null ? null : f.text;
	}

	String sliderText(String slider) {
		UiSlider sl = sliders.get(slider);
		return sl == null ? null : sl.text();
	}

	void update(JsonObject json) {
		read(json);
	}

	private void read(JsonObject json) {
		this.json = json;
		id = UiTree.str(json, "id", "");
		rev = UiTree.num(json, "rev", 0);
		scene = UiTree.str(json, "scene", "dim");
		panels.clear();
		markers.clear();
		hints.clear();
		wear.clear();
		tint.clear();
		for (JsonElement e : array(json, "panels")) {
			if (!e.isJsonObject()) continue;
			JsonObject p = e.getAsJsonObject();
			JsonObject node = p.has("node") && p.get("node").isJsonObject() ? p.getAsJsonObject("node") : new JsonObject();
			panels.add(new Panel(UiTree.str(p, "at", "center"), UiTree.num(p, "w", 160), UiTree.parse(node)));
		}
		for (JsonElement e : array(json, "markers")) {
			if (!e.isJsonObject()) continue;
			JsonObject m = e.getAsJsonObject();
			markers.add(new Marker(UiTree.str(m, "at", "head"), m.has("active") && m.get("active").getAsBoolean(),
					UiTree.str(m, "tooltip", null), m.has("click") && m.get("click").isJsonObject() ? m.getAsJsonObject("click") : null));
		}
		JsonObject link = json.has("link") && json.get("link").isJsonObject() ? json.getAsJsonObject("link") : null;
		linkFrom = link == null ? -1 : UiTree.num(link, "from", -1);
		linkTo = link == null ? null : UiTree.str(link, "to", null);
		for (JsonElement e : array(json, "hints")) {
			if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
				hints.add(new String[] {e.getAsJsonArray().get(0).getAsString(), e.getAsJsonArray().get(1).getAsString()});
			}
		}
		readFields();
		wheel = "wheel".equals(scene) && json.has("wheel") && json.get("wheel").isJsonObject() ? new UiWheel(json.getAsJsonObject("wheel")) : null;
		if (wheel != null && wheelGrow.length != wheel.slots.size()) wheelGrow = new float[wheel.slots.size()];
		JsonObject preview = json.has("preview") && json.get("preview").isJsonObject() ? json.getAsJsonObject("preview") : null;
		if (preview != null) {
			if (preview.has("wear") && preview.get("wear").isJsonObject()) {
				preview.getAsJsonObject("wear").entrySet().forEach(en -> wear.put(en.getKey(), en.getValue().getAsString()));
			}
			if (preview.has("tint") && preview.get("tint").isJsonObject()) {
				preview.getAsJsonObject("tint").entrySet().forEach(en -> tint.put(en.getKey(), en.getValue().getAsInt()));
			}
		}
	}

	/** Picks up the text fields: new ones, and ones the server gave another value. */
	private void readFields() {
		Map<String, UiField> seen = new HashMap<>();
		Map<String, UiSlider> seenSliders = new HashMap<>();
		for (Panel p : panels) collectFields(p.node(), seen, seenSliders);
		sliders.keySet().retainAll(seenSliders.keySet());
		for (Map.Entry<String, UiSlider> e : seenSliders.entrySet()) {
			UiSlider now = e.getValue();
			UiSlider was = sliders.get(e.getKey());
			// Being dragged, it stays under the mouse whatever the server says.
			if (was == null || (!e.getKey().equals(sliding) && (was.server != now.server || was.min != now.min || was.max != now.max || was.step != now.step))) {
				sliders.put(e.getKey(), now);
			}
		}
		if (sliding != null && !sliders.containsKey(sliding)) sliding = null;
		fields.keySet().retainAll(seen.keySet());
		for (Map.Entry<String, UiField> e : seen.entrySet()) {
			UiField now = e.getValue();
			UiField was = fields.get(e.getKey());
			if (was == null || !was.server.equals(now.server)) fields.put(e.getKey(), now);
			else was.max = now.max;
		}
		if (focused != null && !fields.containsKey(focused)) focused = null;
		if (firstRead) {
			for (Panel p : panels) {
				Node f = firstFocus(p.node());
				if (f != null && focused == null) focused = f.id;
			}
		}
		firstRead = false;
	}

	private static void collectFields(Node n, Map<String, UiField> out, Map<String, UiSlider> outSliders) {
		if (n.type.equals("input") && n.id != null) {
			out.put(n.id, new UiField(n.str("value", ""), n.num("max", 32), "digits".equals(n.str("chars"))));
		}
		if (n.type.equals("slider") && n.id != null) {
			outSliders.put(n.id, new UiSlider(dbl(n, "min", 0), dbl(n, "max", 1), dbl(n, "step", 0), dbl(n, "value", dbl(n, "min", 0))));
		}
		for (Node c : n.children) collectFields(c, out, outSliders);
	}

	private static double dbl(Node n, String key, double fallback) {
		JsonElement e = n.json.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
	}

	private static Node firstFocus(Node n) {
		if (n.type.equals("input") && n.id != null && n.flag("focus") && !n.flag("disabled")) return n;
		for (Node c : n.children) {
			Node f = firstFocus(c);
			if (f != null) return f;
		}
		return null;
	}

	private static JsonArray array(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}

	// ------------------------------------------------------------------ Fitting

	@Override
	public boolean fittingScene() {
		return "fitting".equals(scene) && minecraft != null && minecraft.player != null;
	}

	@Override
	public Map<String, String> trying() {
		return wear;
	}

	@Override
	public float previewYaw() {
		return previewYaw;
	}

	@Override
	public Integer tint(String id) {
		return tint.get(id);
	}

	// ------------------------------------------------------------------ lifecycle

	@Override
	public void added() {
		super.added();
		confirmingLink = false;
		if (fittingScene()) {
			hudWasHidden = minecraft.gui.hud.isHidden();
			if (!hudWasHidden) minecraft.gui.hud.toggle();
		}
	}

	@Override
	public void removed() {
		if (!confirmingLink && !closedByServer) {
			JsonObject msg = new JsonObject();
			msg.addProperty("window", id);
			msg.addProperty("closed", true);
			UiNet.send(msg);
		}
		if (hudWasHidden != null && !hudWasHidden && minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
		hudWasHidden = null;
		super.removed();
	}

	void closeByServer() {
		closedByServer = true;
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if ("dim".equals(scene)) super.extractBackground(g, mouseX, mouseY, a);
		else if (wheel != null) g.fill(0, 0, width, height, 0x50000000);
	}

	// ------------------------------------------------------------------ layout

	private UiTree tree(boolean tall) {
		Font f = font;
		return new UiTree(new UiTree.Measure() {
			@Override
			public int width(String text, boolean bold) {
				return f.width(styled(text, bold));
			}

			@Override
			public List<String> wrap(String text, boolean bold, int width) {
				List<String> out = new ArrayList<>();
				for (var line : f.getSplitter().splitLines(styled(text, bold), width, net.minecraft.network.chat.Style.EMPTY)) {
					out.add(line.getString());
				}
				return out.isEmpty() ? List.of("") : out;
			}
		}, tall);
	}

	private UiTree layout(float partial) {
		UiTree tree = tree(true);
		if (!place(tree, partial)) {
			tree = tree(false);
			place(tree, partial);
		}
		return tree;
	}

	/** Lays the panels out and places them; false if one doesn't fit the screen's height. */
	private boolean place(UiTree tree, float partial) {
		int[] center = {width / 2, height / 2};
		int[] left = null;
		int[] right = null;
		markerXY = new int[markers.size() * 2];
		if (fittingScene()) {
			Vec3 feet = minecraft.player.getPosition(partial);
			Camera cam = minecraft.gameRenderer.mainCamera();
			Vector3fc l = cam.leftVector();
			Vec3 side = new Vec3(l.x(), 0, l.z()).normalize();
			left = project(feet.add(0, 1.05, 0).add(side.scale(1.15)));
			right = project(feet.add(0, 1.05, 0).subtract(side.scale(1.15)));
			for (int i = 0; i < markers.size(); i++) {
				int[] p = project(markerPoint(markers.get(i).at(), feet, cam, side));
				if (p == null) p = new int[] {center[0], center[1] - 40};
				markerXY[i * 2] = p[0];
				markerXY[i * 2 + 1] = p[1];
			}
		}
		boolean fits = true;
		int bottom = hints.isEmpty() ? EDGE : 40;
		int stackLeft = EDGE;
		int stackRight = EDGE;
		int centerW = 0;
		for (Panel p : panels) {
			if (p.at().equals("center")) centerW += Math.min(p.width(), width - EDGE * 2) + (centerW > 0 ? EDGE : 0);
		}
		// On a narrow screen (big GUI scale) the panels start at the edge, not off it.
		int cx = Math.max(EDGE, center[0] - centerW / 2);
		for (Panel p : panels) {
			int w = Math.min(p.width(), width - EDGE * 2);
			int h = tree.layout(p.node(), 0, 0, w);
			if (h > height - bottom - EDGE) fits = false;
			int x;
			int y;
			switch (p.at()) {
				case "player_left" -> {
					x = left == null ? center[0] - 70 - w : left[0] - w;
					x = Mth.clamp(x, EDGE, Math.max(EDGE, center[0] - 46 - w));
					y = Mth.clamp((left == null ? center[1] : left[1]) - h / 2, EDGE, Math.max(EDGE, height - bottom - h));
				}
				case "player_right" -> {
					x = right == null ? center[0] + 70 : right[0];
					x = Mth.clamp(x, Math.min(width - EDGE - w, center[0] + 46), width - EDGE - w);
					y = Mth.clamp((right == null ? center[1] : right[1]) - h / 2 + 12, EDGE, Math.max(EDGE, height - bottom - h));
				}
				case "left" -> {
					x = EDGE;
					y = stackLeft;
					stackLeft += h + EDGE;
				}
				case "right" -> {
					x = width - EDGE - w;
					y = stackRight;
					stackRight += h + EDGE;
				}
				case "top" -> {
					x = center[0] - w / 2;
					y = EDGE;
				}
				case "bottom" -> {
					x = center[0] - w / 2;
					y = height - bottom - h;
				}
				default -> {
					x = cx;
					cx += w + EDGE;
					y = Math.max(EDGE, (height - bottom - h) / 2);
				}
			}
			UiTree.shift(p.node(), x, y);
		}
		// Grids: scroll (clamped) and remember which nodes they are.
		grids.clear();
		for (Panel p : panels) scrollGrids(p.node());
		return fits;
	}

	private void scrollGrids(Node n) {
		if (n.type.equals("grid")) {
			double s = Mth.clamp(scrolls.getOrDefault(key(n), 0.0), 0, n.maxScroll);
			scrolls.put(key(n), s);
			grids.put(n, (int) s);
			for (Node c : n.children) UiTree.shift(c, 0, -(int) s);
		}
		for (Node c : n.children) scrollGrids(c);
	}

	private static String key(Node n) {
		return n.id != null ? n.id : "";
	}

	private static Vec3 markerPoint(String at, Vec3 feet, Camera cam, Vec3 side) {
		if (at.equals("back")) {
			// Facing the camera, the back is behind the body: its dot goes to the shoulder.
			Minecraft mc = Minecraft.getInstance();
			UiScreen screen = mc.gui.screen() instanceof UiScreen s ? s : null;
			double bodyYaw = Math.toRadians(mc.player.getYRot() + (screen == null ? 0 : screen.previewYaw));
			Vec3 front = new Vec3(-Math.sin(bodyYaw), 0, Math.cos(bodyYaw));
			Vec3 toCam = cam.position().subtract(feet);
			if (front.dot(new Vec3(toCam.x, 0, toCam.z).normalize()) > 0.3) {
				return feet.add(0, 1.3, 0).subtract(front.scale(0.2)).subtract(side.scale(0.42));
			}
			return feet.add(0, 1.12, 0).subtract(front.scale(0.28));
		}
		if (at.equals("feet")) return feet.add(0, 0.1, 0);
		return feet.add(0, 1.78, 0);
	}

	/** Where a world point is on the screen (GUI pixels), or null if it is behind the camera. */
	private int[] project(Vec3 p) {
		Camera cam = minecraft.gameRenderer.mainCamera();
		Vec3 v = p.subtract(cam.position());
		Vector3fc f = cam.forwardVector();
		Vector3fc u = cam.upVector();
		Vector3fc l = cam.leftVector();
		double z = v.x * f.x() + v.y * f.y() + v.z * f.z();
		if (z < 0.05) return null;
		double x = -(v.x * l.x() + v.y * l.y() + v.z * l.z());
		double y = v.x * u.x() + v.y * u.y() + v.z * u.z();
		double k = (height / 2.0) / Math.tan(Math.toRadians(cam.getFov()) / 2);
		return new int[] {(int) Math.round(width / 2.0 + x / z * k), (int) Math.round(height / 2.0 - y / z * k)};
	}

	// ------------------------------------------------------------------ drawing

	private UiTree current;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (wheel != null) {
			drawWheel(g, mouseX, mouseY);
			return;
		}
		current = layout(a);
		if (fittingScene()) drawMarkers(g, mouseX, mouseY);
		float time = (minecraft.level != null ? minecraft.level.getGameTime() : System.currentTimeMillis() / 50f) + a;
		for (Panel p : panels) {
			Node n = p.node();
			card(g, n.x, n.y, n.x + n.w, n.y + n.h, color(n, "bg", Theme.CARD), color(n, "border", Theme.BORDER));
			draw(g, n, mouseX, mouseY, time, false, null);
		}
		drawHints(g);
		Node tip = find(mouseX, mouseY, n -> tooltip(n) != null);
		if (tip != null) g.setTooltipForNextFrame(font, Component.literal(tooltip(tip)), mouseX, mouseY);
	}

	/** A node's tooltip; a tab shown as an icon only gets its name. */
	private String tooltip(Node n) {
		String tip = n.str("tooltip");
		if (tip != null || !n.type.equals("tab")) return tip;
		return n.flag("active") && labelledTab(n) ? null : n.str("text");
	}

	private boolean labelledTab(Node tab) {
		for (Panel p : panels) {
			Node tabs = parentOf(p.node(), tab);
			if (tabs != null) return current.labelled(tabs, tab);
		}
		return false;
	}

	private static Node parentOf(Node n, Node child) {
		for (Node c : n.children) {
			if (c == child) return n;
			Node found = parentOf(c, child);
			if (found != null) return found;
		}
		return null;
	}

	private int color(Node n, String key, int fallback) {
		return UiTree.color(n.str(key), fallback);
	}

	/** @param hot the card around is hovered or selected (models turn) */
	private void draw(GuiGraphicsExtractor g, Node n, int mx, int my, float time, boolean hot, int[] clip) {
		boolean hover = n.over(mx, my) && (clip == null || inside(clip, mx, my));
		switch (n.type) {
			case "card" -> {
				boolean clickable = n.click() != null;
				int bg = color(n, "bg", Theme.MUTED_BG);
				if (clickable && hover) bg = Theme.HOVER_BG;
				int border = n.flag("selected") ? Theme.ACCENT : color(n, "border", Theme.BORDER);
				card(g, n.x, n.y, n.x + n.w, n.y + n.h, bg, border);
				hot = hot || hover || n.flag("selected");
			}
			case "col", "row" -> {
				if (n.str("bg") != null || n.str("border") != null) {
					card(g, n.x, n.y, n.x + n.w, n.y + n.h, color(n, "bg", 0), color(n, "border", 0));
				}
			}
			case "text" -> text(g, n);
			case "button" -> button(g, n, hover);
			case "input" -> input(g, n, hover);
			case "slider" -> slider(g, n, hover);
			case "tabs" -> {
				tabs(g, n, mx, my);
				return; // the tabs draw their icons themselves
			}
			case "grid" -> {
				int[] c = {n.x - 1, n.y - 1, n.x + n.w + 1, n.y + n.h};
				g.enableScissor(c[0], c[1], c[2], c[3]);
				if (n.children.isEmpty() && n.str("empty") != null) {
					Component e = Component.literal(n.str("empty"));
					g.text(font, e, n.x + n.w / 2 - font.width(e) / 2, n.y + Math.min(30, n.h / 2 - 4), Theme.MUTED, false);
				}
				for (Node child : n.children) {
					if (child.y > n.y + n.h || child.y + child.h < n.y) continue;
					draw(g, child, mx, my, time, hot, c);
				}
				g.disableScissor();
				if (n.maxScroll > 0) scrollbar(g, n, mx, my);
				return;
			}
			case "item" -> {
				ItemStack stack = item(n.str("item", "minecraft:barrier"), n.str("model"));
				g.item(stack, n.x + (n.w - 16) / 2, n.y + (n.h - 16) / 2);
			}
			case "model" -> model(g, n, hot, time);
			case "explainer" -> explainer(g, n);
			case "swatch" -> swatch(g, n, hover);
			case "divider" -> g.fill(n.x, n.y, n.x + n.w, n.y + 1, color(n, "color", Theme.BORDER));
			case "image" -> {
				String tex = n.str("texture");
				Identifier texture = tex == null ? null : Identifier.tryParse(tex);
				if (texture != null) g.blit(RenderPipelines.GUI_TEXTURED, texture, n.x, n.y, 0, 0, n.w, n.h, n.w, n.h, n.w, n.h, 0xFFFFFFFF);
			}
			default -> {
			}
		}
		for (Node c : n.children) draw(g, c, mx, my, time, hot, clip);
	}

	/** Track of a scrolling grid: just right of it, in the panel's padding. */
	private static int[] track(Node grid) {
		int x = grid.x + grid.w + 3;
		return new int[] {x, grid.y, x + 3, grid.y + grid.h};
	}

	/** The thumb's top and bottom on the track. */
	private int[] thumb(Node grid) {
		int[] t = track(grid);
		int h = t[3] - t[1];
		int th = Math.max(12, h * h / (h + grid.maxScroll));
		double s = scrolls.getOrDefault(key(grid), 0.0) / grid.maxScroll;
		int y = t[1] + (int) Math.round((h - th) * s);
		return new int[] {y, y + th};
	}

	/** Shows that the list goes on: a track with a thumb, which can be dragged. */
	private void scrollbar(GuiGraphicsExtractor g, Node grid, int mx, int my) {
		int[] t = track(grid);
		int[] th = thumb(grid);
		boolean hot = (scrollDrag != null && key(scrollDrag).equals(key(grid))) || (mx >= t[0] - 2 && mx < t[2] + 2 && my >= t[1] && my < t[3]);
		g.fill(t[0], t[1] + 1, t[2], t[3] - 1, Theme.BORDER);
		g.fill(t[0], th[0] + 1, t[2], th[1] - 1, hot ? Theme.FG : Theme.FAINT);
		g.fill(t[0] + 1, th[0], t[2] - 1, th[1], hot ? Theme.FG : Theme.FAINT);
	}

	private Node scrollDrag;
	private double scrollDragOffset;

	private Node trackAt(double mx, double my) {
		for (Node grid : grids.keySet()) {
			if (grid.maxScroll <= 0) continue;
			int[] t = track(grid);
			if (mx >= t[0] - 2 && mx < t[2] + 2 && my >= t[1] && my < t[3]) return grid;
		}
		return null;
	}

	private void dragScroll(Node grid, double my) {
		int[] t = track(grid);
		int[] th = thumb(grid);
		int room = (t[3] - t[1]) - (th[1] - th[0]);
		if (room <= 0) return;
		double s = (my - scrollDragOffset - t[1]) / room;
		scrolls.put(key(grid), Mth.clamp(s, 0, 1) * grid.maxScroll);
	}

	private static boolean inside(int[] clip, double mx, double my) {
		return mx >= clip[0] && mx < clip[2] && my >= clip[1] && my < clip[3];
	}

	private static MutableComponent styled(String s, boolean bold) {
		MutableComponent c = Component.literal(s);
		return bold ? c.withStyle(st -> st.withBold(true)) : c;
	}

	private void text(GuiGraphicsExtractor g, Node n) {
		boolean bold = n.flag("bold");
		int color = color(n, "color", Theme.FG);
		String align = n.str("align", "left");
		int y = n.y;
		for (String line : n.lines) {
			Component c = styled(line, bold);
			int w = font.width(c) + (n.flag("coin") ? UiTree.COIN_GAP + UiTree.COIN : 0);
			int x = align.equals("center") ? n.x + (n.w - w) / 2 : align.equals("right") ? n.x + n.w - w : n.x;
			g.text(font, c, x, y, color, false);
			if (n.flag("coin")) coin(g, x + font.width(c) + UiTree.COIN_GAP, y - 1);
			y += UiTree.LINE;
		}
	}

	private void button(GuiGraphicsExtractor g, Node n, boolean hover) {
		String style = n.str("style", "primary");
		boolean on = !n.flag("disabled");
		hover = hover && on;
		Component label = Component.literal(n.str("text", ""));
		int fg;
		switch (style) {
			case "danger" -> {
				card(g, n.x, n.y, n.x + n.w, n.y + n.h, hover ? 0x33F87171 : 0, on ? Theme.RED : Theme.DISABLED);
				fg = on ? Theme.RED : Theme.DISABLED;
			}
			case "outline" -> {
				card(g, n.x, n.y, n.x + n.w, n.y + n.h, hover ? Theme.HOVER_BG : 0, Theme.BORDER);
				fg = on ? Theme.FG : Theme.DISABLED;
			}
			case "ghost" -> {
				if (hover) card(g, n.x, n.y, n.x + n.w, n.y + n.h, Theme.HOVER_BG, 0);
				fg = on ? Theme.FG : Theme.DISABLED;
			}
			case "link" -> fg = !on ? Theme.DISABLED : hover ? Theme.FG : Theme.ACCENT;
			default -> {
				card(g, n.x, n.y, n.x + n.w, n.y + n.h, on ? (hover ? Theme.PRIMARY_HOVER : Theme.PRIMARY) : 0xFF27272A, 0);
				fg = on ? Theme.ON_PRIMARY : Theme.DISABLED;
			}
		}
		fg = color(n, "color", fg);
		int w = font.width(label) + (n.flag("coin") ? UiTree.COIN_GAP + UiTree.COIN : 0);
		int x = style.equals("link") ? n.x + n.w - w - 2 : n.x + (n.w - w) / 2;
		int y = n.y + (n.h - 8) / 2;
		g.text(font, label, x, y, fg, false);
		if (n.flag("coin")) coin(g, x + font.width(label) + UiTree.COIN_GAP, y - 1);
	}

	/** A text field: a dark box, the text (or the grey placeholder) and a blinking caret while it has the keyboard. */
	private void input(GuiGraphicsExtractor g, Node n, boolean hover) {
		UiField f = n.id == null ? null : fields.get(n.id);
		boolean on = f != null && !n.flag("disabled");
		boolean focus = on && n.id.equals(focused);
		card(g, n.x, n.y, n.x + n.w, n.y + n.h, Theme.MUTED_BG, focus ? Theme.ACCENT : hover && on ? Theme.FAINT : Theme.BORDER);
		int x0 = n.x + 5;
		int room = n.w - 10;
		int ty = n.y + (n.h - 8) / 2;
		if (f == null || f.text.isEmpty()) {
			String ph = n.str("placeholder");
			if (ph != null) {
				String shown = font.plainSubstrByWidth(ph, room);
				g.text(font, Component.literal(shown), x0, ty, Theme.FAINT, false);
			}
			if (focus && caretOn()) g.fill(x0, ty - 1, x0 + 1, ty + 9, Theme.FG);
			return;
		}
		// Scroll so the caret stays in the box.
		f.scrollTo(room, s -> font.width(s));
		String shown = font.plainSubstrByWidth(f.text.substring(f.scroll), room);
		g.enableScissor(n.x + 1, n.y + 1, n.x + n.w - 1, n.y + n.h - 1);
		g.text(font, Component.literal(shown), x0, ty, on ? Theme.FG : Theme.DISABLED, false);
		if (focus && caretOn()) {
			int cx = x0 + font.width(f.text.substring(f.scroll, f.cursor));
			g.fill(cx, ty - 1, cx + 1, ty + 9, Theme.FG);
		}
		g.disableScissor();
	}

	/** A slider: the track filled up to the thumb, "label: value" in the middle. */
	private void slider(GuiGraphicsExtractor g, Node n, boolean hover) {
		UiSlider sl = n.id == null ? null : sliders.get(n.id);
		boolean on = sl != null && !n.flag("disabled");
		boolean active = on && n.id.equals(sliding);
		card(g, n.x, n.y, n.x + n.w, n.y + n.h, Theme.MUTED_BG, active ? Theme.ACCENT : hover && on ? Theme.FAINT : Theme.BORDER);
		if (sl == null) return;
		int x0 = n.x + 3;
		int x1 = n.x + n.w - 3;
		int tx = x0 + (int) Math.round((x1 - x0 - 4) * sl.fraction());
		if (tx > x0) g.fill(x0, n.y + 3, tx + 2, n.y + n.h - 3, on ? (Theme.ACCENT & 0xFFFFFF) | 0x50000000 : 0x3052525B);
		g.fill(tx, n.y + 2, tx + 4, n.y + n.h - 2, !on ? Theme.DISABLED : active || hover ? Theme.FG : 0xFFD4D4D8);
		String label = n.str("label");
		String text = (label == null || label.isEmpty() ? "" : label + ": ") + sl.text() + n.str("unit", "");
		Component c = Component.literal(font.plainSubstrByWidth(text, n.w - 8));
		g.text(font, c, n.x + (n.w - font.width(c)) / 2, n.y + (n.h - 8) / 2, on ? Theme.FG : Theme.DISABLED, true);
	}

	/** The slider's node in the current layout, by id. */
	private Node sliderNode(String id) {
		for (Panel p : panels) {
			Node n = byType(p.node(), "slider", id);
			if (n != null) return n;
		}
		return null;
	}

	private static Node byType(Node n, String type, String id) {
		if (n.type.equals(type) && id.equals(n.id)) return n;
		for (Node c : n.children) {
			Node f = byType(c, type, id);
			if (f != null) return f;
		}
		return null;
	}

	private void slideTo(Node n, double mx) {
		UiSlider sl = sliders.get(n.id);
		if (sl == null) return;
		int x0 = n.x + 3;
		int x1 = n.x + n.w - 3;
		if (sl.setFraction((mx - x0 - 2) / Math.max(1, x1 - x0 - 4))) {
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 2.0F, 0.08F));
		}
	}

	private static boolean caretOn() {
		return System.currentTimeMillis() / 500 % 2 == 0;
	}

	private void tabs(GuiGraphicsExtractor g, Node n, int mx, int my) {
		card(g, n.x, n.y, n.x + n.w, n.y + n.h, Theme.MUTED_BG, 0);
		Node active = null;
		for (Node t : n.children) {
			if (t.flag("active")) active = t;
		}
		boolean labelled = current.labelled(n, active);
		for (int i = 0; i < n.children.size(); i++) {
			Node t = n.children.get(i);
			boolean on = t.flag("active");
			boolean hover = t.over(mx, my);
			if (on) card(g, t.x, t.y, t.x + t.w, t.y + t.h, 0xFF09090B, 0xFF27272A);
			else if (hover) card(g, t.x, t.y, t.x + t.w, t.y + t.h, Theme.HOVER_BG, 0);
			int mid = t.x + t.w / 2;
			int iy = t.y + (t.h - 16) / 2;
			ItemStack icon = item(t.str("icon", "minecraft:paper"));
			Component label = Component.literal(t.str("text", ""));
			if (on && labelled) {
				int w = 16 + 3 + font.width(label);
				int ix = mid - w / 2;
				g.item(icon, ix, iy);
				g.text(font, label, ix + 19, iy + 4, Theme.FG, false);
			} else {
				g.item(icon, mid - 8, iy);
			}
		}
	}

	/**
	 * A hall event's looping 3D scene (MineHouse End): the stage, the darkness over it with the
	 * glowing mobs on top, a countdown bar, and the step's caption under it.
	 */
	private void explainer(GuiGraphicsExtractor g, Node n) {
		float t = (System.currentTimeMillis() - openedAt) / 1000f;
		Explainer.Frame f;
		try {
			f = Explainer.frame(n.str("scene", ""), t, pro.minehouse.menu.Tr.russian());
		} catch (RuntimeException e) {
			Explainer.warnOnce("scene " + n.str("scene", ""), e);
			f = null;
		}
		if (f == null) return;
		int x0 = n.x;
		int y0 = n.y;
		int x1 = n.x + n.w;
		int y1 = n.y + n.h;
		// Caption: up to two lines under the stage.
		var lines = font.split(Component.literal(f.caption()), n.w - 10);
		if (lines.size() > 2) lines = lines.subList(0, 2);
		int capH = 6 + lines.size() * 9;
		int sy1 = y1 - capH;
		card(g, x0, y0, x1, y1, f.bg(), color(n, "border", Theme.BORDER));
		float scale = n.w / 7f;
		float lift = (sy1 - y0) * 0.3f / scale;
		var clip = g.scissorStack.peek();
		g.guiRenderState.addPicturesInPictureState(new ExplainerRenderer.State(f.world(), 18, 14, lift, x0 + 1, y0 + 1, x1 - 1, sy1, scale, clip));
		if (f.dark() > 0) g.fill(x0 + 1, y0 + 1, x1 - 1, sy1, (int) (f.dark() * 255) << 24 | 0x05050A);
		if (!f.glow().isEmpty()) {
			g.guiRenderState.addPicturesInPictureState(new ExplainerRenderer.State(f.glow(), 18, 14, lift, x0 + 1, y0 + 1, x1 - 1, sy1, scale, clip));
		}
		if (f.bar() >= 0) {
			int bx0 = x0 + 10;
			int bx1 = x1 - 10;
			g.fill(bx0, y0 + 14, bx1, y0 + 18, 0xFF3F3F46);
			int fill = bx0 + Math.round((bx1 - bx0) * f.bar());
			if (fill > bx0) g.fill(bx0, y0 + 14, fill, y0 + 18, f.bar() < 0.25f ? 0xFFEF4444 : 0xFFF87171);
		}
		// A dot per step, top right.
		for (int i = 0; i < f.steps(); i++) {
			int dx = x1 - 6 - (f.steps() - i) * 6;
			g.fill(dx, y0 + 6, dx + 3, y0 + 9, i == f.step() ? Theme.ACCENT : 0xFF52525B);
		}
		g.fill(x0 + 1, sy1, x1 - 1, y1 - 1, 0xC0000000);
		for (int i = 0; i < lines.size(); i++) {
			var line = lines.get(i);
			g.text(font, line, x0 + (n.w - font.width(line)) / 2, sy1 + 4 + i * 9, Theme.FG, false);
		}
		float loop = (t % f.length()) / f.length();
		g.fill(x0 + 1, sy1 - 1, x0 + 1 + Math.round((n.w - 2) * loop), sy1, 0x60FFFFFF);
	}

	private void model(GuiGraphicsExtractor g, Node n, boolean hot, float time) {
		ItemStackRenderState state = modelState(n);
		if (state == null) return;
		String spin = n.str("spin", "hover");
		float yaw = switch (spin) {
			case "always" -> time * 4;
			case "none" -> 35;
			default -> hot ? time * 4 : 35;
		};
		float scale = n.num("scale", (int) (Math.min(n.w, n.h) * 0.875f));
		g.guiRenderState.addPicturesInPictureState(new WardrobePreview.State(null, 0, state, yaw,
				n.x + 1, n.y, n.x + n.w - 1, n.y + n.h, scale, g.scissorStack.peek()));
	}

	private ItemStackRenderState modelState(Node n) {
		if (!itemsReady()) return null;
		String model = n.str("model");
		Identifier modelId = model == null ? null : Identifier.tryParse(model);
		if (modelId == null) return null;
		int frames = Math.max(1, n.num("frames", 1));
		int fps = Mth.clamp(n.num("fps", 20), 1, 20);
		int frame = frames > 1 ? (int) Math.floorMod(System.currentTimeMillis() * fps / 1000, (long) frames) : -1;
		Integer rgb = n.json.has("rgb") ? n.num("rgb", 0) : null;
		ItemDisplayContext context = switch (n.str("context", "head")) {
			case "fixed" -> ItemDisplayContext.FIXED;
			case "gui" -> ItemDisplayContext.GUI;
			default -> ItemDisplayContext.HEAD;
		};
		String k = model + "#" + rgb + "#" + frame + "#" + context;
		return models.computeIfAbsent(k, x -> {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, modelId);
			if (rgb != null) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			if (frame >= 0) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of("f" + frame), List.of()));
			ItemStackRenderState out = new ItemStackRenderState();
			minecraft.getItemModelResolver().updateForTopItem(out, stack, context, minecraft.level, null, 0);
			return out;
		});
	}

	/** Item stacks can be made only once the game has bound item components (in a world; not on the title screen). */
	private static boolean itemsReady() {
		return Items.PAPER.builtInRegistryHolder().areComponentsBound();
	}

	private ItemStack item(String id) {
		return item(id, null);
	}

	/** {@code model}: an item_model to show instead (server items sent as a vanilla item with their own model). */
	private ItemStack item(String id, String model) {
		if (!itemsReady()) return ItemStack.EMPTY;
		return items.computeIfAbsent(model == null ? id : id + "#" + model, k -> {
			Identifier i = Identifier.tryParse(id);
			var item = i == null ? null : BuiltInRegistries.ITEM.getValue(i);
			ItemStack stack = new ItemStack(item == null ? Items.BARRIER : item);
			Identifier m = model == null ? null : Identifier.tryParse(model);
			if (m != null) stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, m);
			return stack;
		});
	}

	private void swatch(GuiGraphicsExtractor g, Node n, boolean hover) {
		int rgb = n.num("rgb", 0) & 0xFFFFFF;
		boolean on = n.flag("selected");
		if (on || hover) card(g, n.x - 2, n.y - 2, n.x + n.w + 2, n.y + n.h + 2, 0, on ? Theme.ACCENT : Theme.FAINT);
		card(g, n.x, n.y, n.x + n.w, n.y + n.h, 0xFF000000 | rgb, 0x30FFFFFF);
		String label = n.str("label");
		if (label != null) {
			Component c = Component.literal(label);
			g.text(font, c, n.x + n.w / 2 - font.width(c) / 2 + 1, n.y + 2, rgb > 0x808080 ? 0xFF18181B : 0xFFFFFFFF, false);
		}
	}

	private void coin(GuiGraphicsExtractor g, int x, int y) {
		g.blit(RenderPipelines.GUI_TEXTURED, COIN, x, y, 0.0F, 0.0F, UiTree.COIN, UiTree.COIN, 16, 16, 16, 16, 0xFFFFFFFF);
	}

	private void drawMarkers(GuiGraphicsExtractor g, int mx, int my) {
		for (int i = 0; i < markers.size(); i++) {
			Marker m = markers.get(i);
			int x = markerXY[i * 2];
			int y = markerXY[i * 2 + 1];
			if (m.at().equals(linkTo) && linkFrom >= 0 && linkFrom < panels.size()) {
				Node from = panels.get(linkFrom).node();
				dashedLine(g, from.x, from.y + 14, x, y, (Theme.ACCENT & 0xFFFFFF) | 0xC0000000);
			}
		}
		for (int i = 0; i < markers.size(); i++) {
			Marker m = markers.get(i);
			int x = markerXY[i * 2];
			int y = markerXY[i * 2 + 1];
			boolean hover = Math.abs(mx - x) <= 6 && Math.abs(my - y) <= 6;
			dot(g, x, y, m.active() ? Theme.ACCENT : hover ? Theme.FG : 0xFFD4D4D8, m.active());
			if (hover && m.tooltip() != null) g.setTooltipForNextFrame(font, Component.literal(m.tooltip()), mx, my);
		}
	}

	/** The key hints along the bottom, like keyboard shortcuts on the site. */
	private void drawHints(GuiGraphicsExtractor g) {
		if (hints.isEmpty()) {
			barX0 = barY0 = barX1 = barY1 = 0;
			return;
		}
		int w = 12;
		for (String[] p : hints) w += font.width(p[0]) + 8 + 4 + font.width(p[1]) + 12;
		barX0 = width / 2 - w / 2;
		barY0 = height - 26;
		barX1 = barX0 + w;
		barY1 = barY0 + 18;
		card(g, barX0, barY0, barX1, barY1, Theme.CARD, Theme.BORDER);
		int x = barX0 + 12;
		for (String[] p : hints) {
			int kw = font.width(p[0]) + 8;
			card(g, x, barY0 + 3, x + kw, barY0 + 15, 0, 0xFF3F3F46);
			g.text(font, Component.literal(p[0]), x + 4, barY0 + 5, Theme.FG, false);
			x += kw + 4;
			g.text(font, Component.literal(p[1]), x, barY0 + 5, Theme.MUTED, false);
			x += font.width(p[1]) + 12;
		}
	}

	/** A card with 3 px rounded corners: background and a 1 px border, without overlapping (the background is see-through). */
	public static void card(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int bg, int border) {
		if (bg != 0) {
			g.fill(x0 + 3, y0 + 1, x1 - 3, y0 + 2, bg);
			g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3, bg);
			g.fill(x0 + 1, y0 + 3, x1 - 1, y1 - 3, bg);
			g.fill(x0 + 2, y1 - 3, x1 - 2, y1 - 2, bg);
			g.fill(x0 + 3, y1 - 2, x1 - 3, y1 - 1, bg);
		}
		if (border == 0) return;
		g.fill(x0 + 3, y0, x1 - 3, y0 + 1, border);
		g.fill(x0 + 3, y1 - 1, x1 - 3, y1, border);
		g.fill(x0, y0 + 3, x0 + 1, y1 - 3, border);
		g.fill(x1 - 1, y0 + 3, x1, y1 - 3, border);
		g.fill(x0 + 1, y0 + 2, x0 + 2, y0 + 3, border);
		g.fill(x0 + 2, y0 + 1, x0 + 3, y0 + 2, border);
		g.fill(x1 - 2, y0 + 2, x1 - 1, y0 + 3, border);
		g.fill(x1 - 3, y0 + 1, x1 - 2, y0 + 2, border);
		g.fill(x0 + 1, y1 - 3, x0 + 2, y1 - 2, border);
		g.fill(x0 + 2, y1 - 2, x0 + 3, y1 - 1, border);
		g.fill(x1 - 2, y1 - 3, x1 - 1, y1 - 2, border);
		g.fill(x1 - 3, y1 - 2, x1 - 2, y1 - 1, border);
	}

	private static void dashedLine(GuiGraphicsExtractor g, int ax, int ay, int bx, int by, int color) {
		double len = Math.hypot(bx - ax, by - ay);
		int steps = (int) (len / 2);
		for (int i = 0; i < steps; i++) {
			if (i % 3 == 2) continue;
			int x = (int) Math.round(ax + (bx - ax) * (double) i / steps);
			int y = (int) Math.round(ay + (by - ay) * (double) i / steps);
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void dot(GuiGraphicsExtractor g, int x, int y, int color, boolean ring) {
		g.fill(x - 1, y - 2, x + 2, y + 3, color);
		g.fill(x - 2, y - 1, x + 3, y + 2, color);
		if (ring) {
			int c = (color & 0xFFFFFF) | 0x70000000;
			g.fill(x - 2, y - 5, x + 3, y - 4, c);
			g.fill(x - 2, y + 5, x + 3, y + 6, c);
			g.fill(x - 5, y - 2, x - 4, y + 3, c);
			g.fill(x + 5, y - 2, x + 6, y + 3, c);
			g.fill(x - 4, y - 4, x - 3, y - 3, c);
			g.fill(x + 4, y - 4, x + 5, y - 3, c);
			g.fill(x - 4, y + 4, x - 3, y + 5, c);
			g.fill(x + 4, y + 4, x + 5, y + 5, c);
		}
	}

	// ------------------------------------------------------------------ wheel

	/** How much the wheel is scaled down to fit a small screen. */
	private float wheelScale() {
		int need = 2 * (UiWheel.RADIUS + UiWheel.TILE) + 90;
		return Math.min(1.0F, (height - 40) / (float) need);
	}

	private int[] wheelCenter() {
		return new int[] {width / 2, (int) (height / 2 - 18 * wheelScale())};
	}

	private void drawWheel(GuiGraphicsExtractor g, int mx, int my) {
		if (testMouse != null) {
			mx = testMouse[0];
			my = testMouse[1];
		}
		float k = wheelScale();
		int[] c = wheelCenter();
		int hover = wheel.pick(c[0], c[1], mx, my);
		if (hover != wheelHover) {
			wheelHover = hover;
			if (hover >= 0) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.8F));
		}
		float open = UiWheel.clamp01((System.currentTimeMillis() - openedAt) / 160F);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(c[0], c[1]);
		pose.scale(k, k);
		pose.translate(-c[0], -c[1]);
		wheel.draw(this, g, font, c[0], c[1], open, hover, wheelGrow);
		pose.popMatrix();
		drawHints(g);
	}

	ItemStack wheelItem(UiWheel.Slot slot) {
		return item(slot.item(), slot.model());
	}

	@Override
	public void tick() {
		super.tick();
		if (!com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
				&& !com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)) shiftArmed = true;
		for (int i = 0; i < wheelGrow.length; i++) wheelGrow[i] = UiWheel.approach(wheelGrow[i], i == wheelHover ? 1 : 0, 0.5F);
	}

	private boolean chooseWheel(int index) {
		if (wheel == null || index < 0 || index >= wheel.slots.size() || wheelChosen) return false;
		JsonObject click = wheel.slots.get(index).click();
		if (click == null) return false;
		wheelChosen = true;
		send(click);
		return true;
	}

	/** The node of the focused field in the current layout (for its submit click). */
	private Node focusedNode() {
		if (focused == null) return null;
		for (Panel p : panels) {
			Node n = byId(p.node(), focused);
			if (n != null) return n;
		}
		return null;
	}

	private static Node byId(Node n, String id) {
		if (n.type.equals("input") && id.equals(n.id)) return n;
		for (Node c : n.children) {
			Node f = byId(c, id);
			if (f != null) return f;
		}
		return null;
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		UiField f = focused == null ? null : fields.get(focused);
		if (f != null && wheel == null) {
			if (event.isAllowedChatCharacter()) f.insert(event.codepointAsString());
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		UiField f = focused == null || wheel != null ? null : fields.get(focused);
		if (f != null && !event.isEscape()) {
			int key = event.key();
			boolean word = event.hasControlDown();
			if (event.isConfirmation()) {
				Node n = focusedNode();
				JsonObject submit = n == null || n.flag("disabled") ? null : n.obj("submit");
				if (submit != null) send(submit);
			} else if (event.isPaste()) {
				f.insert(minecraft.keyboardHandler.getClipboard());
			} else if (event.isCopy()) {
				minecraft.keyboardHandler.setClipboard(f.text);
			} else if (event.isCut()) {
				minecraft.keyboardHandler.setClipboard(f.text);
				f.clear();
			} else if (event.isSelectAll()) {
				f.end();
			} else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
				f.backspace(word);
			} else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE) {
				f.delete(word);
			} else if (event.isLeft()) {
				f.left(word);
			} else if (event.isRight()) {
				f.right(word);
			} else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_HOME) {
				f.home();
			} else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_END) {
				f.end();
			} else if (event.isCycleFocus()) {
				cycleFocus(event.hasShiftDown());
			}
			// Typing doesn't reach the game's keys (inventory, chat, ...).
			return true;
		}
		if (f == null && wheel == null && event.isCycleFocus() && !fields.isEmpty()) {
			cycleFocus(event.hasShiftDown());
			return true;
		}
		if (wheel != null) {
			int key = event.key();
			if (key >= com.mojang.blaze3d.platform.InputConstants.KEY_1 && key <= com.mojang.blaze3d.platform.InputConstants.KEY_9) {
				return chooseWheel(key - com.mojang.blaze3d.platform.InputConstants.KEY_1) || true;
			}
			// Shift closes it, as it does the wheel in the world.
			if (key == com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT || key == com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT) {
				if (shiftArmed) onClose();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	/** Tab: the next field (in the order they are in the window). */
	private void cycleFocus(boolean back) {
		List<String> order = new ArrayList<>();
		for (Panel p : panels) fieldOrder(p.node(), order);
		if (order.isEmpty()) return;
		int i = focused == null ? -1 : order.indexOf(focused);
		i = back ? (i <= 0 ? order.size() - 1 : i - 1) : (i + 1) % order.size();
		focused = order.get(i);
		fields.get(focused).end();
	}

	private void fieldOrder(Node n, List<String> out) {
		if (n.type.equals("input") && n.id != null && fields.containsKey(n.id) && !n.flag("disabled")) out.add(n.id);
		for (Node c : n.children) fieldOrder(c, out);
	}

	// ------------------------------------------------------------------ input

	/** The deepest node under the mouse that passes the test (grids only where they are visible). */
	private Node find(double mx, double my, java.util.function.Predicate<Node> test) {
		for (int i = panels.size() - 1; i >= 0; i--) {
			Node hit = find(panels.get(i).node(), mx, my, test);
			if (hit != null) return hit;
		}
		return null;
	}

	private Node find(Node n, double mx, double my, java.util.function.Predicate<Node> test) {
		if (!n.over(mx, my)) return null;
		for (int i = n.children.size() - 1; i >= 0; i--) {
			Node c = n.children.get(i);
			if (n.type.equals("grid") && (c.y + c.h <= n.y || c.y >= n.y + n.h)) continue;
			Node hit = find(c, mx, my, test);
			if (hit != null) return hit;
		}
		return n.over(mx, my) && test.test(n) ? n : null;
	}

	private boolean overPanel(double mx, double my) {
		for (Panel p : panels) {
			if (p.node().over(mx, my)) return true;
		}
		return false;
	}

	private void send(JsonObject click) {
		if (click.has("url")) {
			try {
				URI uri = URI.create(click.get("url").getAsString());
				confirmingLink = true; // this screen comes back after the confirmation
				UiScreen self = this;
				minecraft.gui.setScreen(new ConfirmLinkScreen(accept -> {
					if (accept) com.mojang.blaze3d.Blaze3D.openUri(uri);
					minecraft.gui.setScreen(self);
				}, uri, true));
			} catch (IllegalArgumentException e) {
				// Not a link.
			}
			return;
		}
		if (click.has("close")) {
			onClose();
			return;
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("window", id);
		msg.addProperty("rev", rev);
		msg.add("click", click);
		if (!fields.isEmpty() || !sliders.isEmpty()) {
			JsonObject inputs = new JsonObject();
			fields.forEach((k, f) -> inputs.addProperty(k, f.text));
			sliders.forEach((k, sl) -> inputs.addProperty(k, sl.text()));
			msg.add("inputs", inputs);
		}
		UiNet.send(msg);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (wheel != null) {
			if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
				rightHeld = true;
				return true;
			}
			if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
				int[] c = wheelCenter();
				chooseWheel(wheel.pick(c[0], c[1], mx, my));
				return true;
			}
		}
		if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		if (fittingScene()) {
			for (int i = 0; i < markers.size(); i++) {
				if (Math.abs(mx - markerXY[i * 2]) <= 6 && Math.abs(my - markerXY[i * 2 + 1]) <= 6 && markers.get(i).click() != null) {
					send(markers.get(i).click());
					return true;
				}
			}
		}
		Node bar = trackAt(mx, my);
		if (bar != null) {
			int[] th = thumb(bar);
			// On the thumb: drag it from where it was taken; on the track: jump there.
			scrollDragOffset = my >= th[0] && my < th[1] ? my - th[0] : (th[1] - th[0]) / 2.0;
			scrollDrag = bar;
			dragScroll(bar, my);
			return true;
		}
		Node field = find(mx, my, n -> n.type.equals("input") && n.id != null && fields.containsKey(n.id) && !n.flag("disabled"));
		if (field != null) {
			focused = field.id;
			UiField f = fields.get(field.id);
			f.clickAt(mx - (field.x + 5), s -> font.width(s));
			return true;
		}
		focused = null;
		Node slider = find(mx, my, n -> n.type.equals("slider") && n.id != null && sliders.containsKey(n.id) && !n.flag("disabled"));
		if (slider != null) {
			sliding = slider.id;
			slideTo(slider, mx);
			return true;
		}
		Node hit = find(mx, my, n -> n.click() != null || n.dbl() != null);
		if (hit != null) {
			if (hit.click() != null) send(hit.click());
			if (doubleClick && hit.dbl() != null) send(hit.dbl());
			return true;
		}
		if (overPanel(mx, my) || (mx >= barX0 && mx < barX1 && my >= barY0 && my < barY1)) return true;
		if (fittingScene()) {
			dragging = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (sliding != null) {
			Node n = sliderNode(sliding);
			if (n != null) slideTo(n, event.x());
			return true;
		}
		if (scrollDrag != null) {
			// The layout is rebuilt every frame: find the same grid again by its id.
			for (Node grid : grids.keySet()) {
				if (key(grid).equals(key(scrollDrag))) {
					scrollDrag = grid;
					dragScroll(grid, event.y());
					return true;
				}
			}
			return true;
		}
		if (dragging) {
			previewYaw = Mth.wrapDegrees(previewYaw - (float) dx * 2.5f);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		// Hold the right button, point, let go. The click that opened the wheel lets go at once: that keeps it open.
		if (wheel != null && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && rightHeld) {
			rightHeld = false;
			if (System.currentTimeMillis() - openedAt > 250) {
				int[] c = wheelCenter();
				chooseWheel(wheel.pick(c[0], c[1], event.x(), event.y()));
			}
			return true;
		}
		dragging = false;
		scrollDrag = null;
		if (sliding != null) {
			Node n = sliderNode(sliding);
			UiSlider sl = sliders.get(sliding);
			sliding = null;
			JsonObject change = n == null || n.flag("disabled") ? null : n.obj("change");
			// Only if it moved from where the server put it (else a click on it would send the same again).
			if (change != null && sl != null && sl.value != sl.server) send(change);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		for (Map.Entry<Node, Integer> e : grids.entrySet()) {
			Node grid = e.getKey();
			if (grid.over(x, y) && grid.maxScroll > 0) {
				scrolls.put(key(grid), Mth.clamp(e.getValue() - scrollY * 20, 0, grid.maxScroll));
				return true;
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
}
