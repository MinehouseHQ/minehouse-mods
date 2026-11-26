package pro.minehouse.menu.wardrobe;

import pro.minehouse.menu.Tr;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;

/**
 * The wardrobe in the fitting room, «floating panels» (variant E): the camera stands in front of
 * the player in the world ({@link WardrobeCamera}) and two cards hang in the air beside them, in the
 * site's colours (shadcn/ui zinc) with the Minecraft font. Left: the slot tabs and the cosmetics; right: the picked
 * one, its colours and what to do. Dots on the head and the back are the slots themselves (click
 * one), a dashed line runs from the right card to the slot being dressed. Drag anywhere else to turn
 * round.
 */
public final class WardrobeScreen extends Screen implements WardrobeNet.FittingView, Fitting {
	// The site's palette (zinc, dark).
	private static final int CARD = 0xDB09090B;
	private static final int BORDER = 0xFF27272A;
	private static final int MUTED_BG = 0xFF18181B;
	private static final int HOVER_BG = 0xFF27272A;
	private static final int FG = 0xFFFAFAFA;
	private static final int MUTED = 0xFFA1A1AA;
	private static final int FAINT = 0xFF71717A;
	private static final int DISABLED = 0xFF52525B;
	private static final int PRIMARY = 0xFFFAFAFA;
	private static final int PRIMARY_HOVER = 0xFFE4E4E7;
	private static final int ON_PRIMARY = 0xFF18181B;
	private static final int ACCENT = 0xFFA78BFA;
	private static final int GREEN = 0xFF4ADE80;
	private static final int RED = 0xFFF87171;


	private static final int PAD = 8;
	/** MineHouse Coins icon (16x16 pixel art), drawn at 8x8 next to prices and the balance. */
	private static final Identifier COIN = Identifier.fromNamespaceAndPath("minehouse-menu", "textures/gui/coin.png");
	private static final int COIN_SIZE = 8;
	private static final int COIN_GAP = 2;
	private static final int LEFT_W = 144;
	private static final int RIGHT_W = 134;
	private static final int TILE_W = 40;
	private static final int TILE_H = 46;
	private static final int GAP = 4;
	private static final int COLS = 3;
	private static final int TABS_H = 22;
	/** A tab showing just its icon. */
	private static final int TAB_ICON_W = 18;

	/**
	 * The tabs and the server slot their cosmetics are worn in. Backpacks, wings and capes share the
	 * "backpack" slot and one tab, «Тело» (one covering thing at a time, see Selection.pick);
	 * shoulder companions have their own slot and tab, «Плечо» (Cosmetics 0.27.0).
	 */
	enum Slot {
		HAT(Tr.s("Голова", "Head"), "hat", () -> Items.LEATHER_HELMET),
		SHOULDER(Tr.s("Плечо", "Shoulder"), "shoulder", () -> Items.FEATHER),
		BACKPACK(Tr.s("Тело", "Body"), "backpack", () -> Items.BUNDLE),
		BELT(Tr.s("Пояс", "Belt"), "belt", () -> Items.LEATHER_LEGGINGS),
		ARMOR(Tr.s("Броня", "Armor"), "armor", () -> Items.IRON_CHESTPLATE),
		PET(Tr.s("Питомцы", "Pets"), "pet", () -> Items.LEAD);

		final String title;
		final String id;
		/** Looked up only when drawn: the item registry isn't there in unit tests. */
		private final java.util.function.Supplier<Item> icon;
		private ItemStack stack;

		Slot(String title, String id, java.util.function.Supplier<Item> icon) {
			this.title = title;
			this.id = id;
			this.icon = icon;
		}

		ItemStack stack() {
			if (stack == null) {
				stack = new ItemStack(icon.get());
			}
			return stack;
		}

		boolean lists(WardrobeNet.Item item) {
			return item.slot().equals(id);
		}

		List<WardrobeNet.Item> grid(List<WardrobeNet.Item> catalog) {
			return catalog.stream().filter(this::lists).toList();
		}

		static Slot of(WardrobeNet.Item item) {
			for (Slot s : values()) {
				if (s.lists(item)) {
					return s;
				}
			}
			return HAT;
		}
	}

	private WardrobeNet.State state;
	private Button armorButton;
	private Slot slot = Slot.HAT;
	private final Selection selection = new Selection();

	/** The preview is the server outfit plus at most one tentative item. */
	static final class Selection {
		String selected;
		Map<String, String> trying = Map.of();

		void pick(WardrobeNet.State state, WardrobeNet.Item item, boolean canTry) {
			selected = item == null ? null : item.id();
			if (item == null || !canTry || state.wornPosition(item.slot(), item.id()) != 0) {
				trying = state.worn();
			} else {
				var preview = new HashMap<>(state.worn());
				int capacity = state.multiWear() ? WardrobeNet.layeredCapacity(item.slot()) : 1;
				int position = 0;
				if (capacity == 1) position = 1;
				else if ("wings".equals(item.kind()) || "cape".equals(item.kind())) {
					for (int i = 1; i <= capacity; i++) {
						var old = state.item(preview.get(WardrobeNet.wearKey(item.slot(), i)));
						if (old != null && ("wings".equals(old.kind()) || "cape".equals(old.kind()))) { position = i; break; }
					}
				}
				if (position == 0) for (int i = 1; i <= capacity; i++) {
					if (!preview.containsKey(WardrobeNet.wearKey(item.slot(), i))) { position = i; break; }
				}
				if (position == 0) { trying = state.worn(); return; }
				preview.put(WardrobeNet.wearKey(item.slot(), position), item.id());
				trying = Map.copyOf(preview);
			}
		}

		void update(WardrobeNet.State before, WardrobeNet.State after) {
			var item = after.item(selected);
			// A removal (including the feature being switched off) is not a new try-on.
			if (item != null && before.wornPosition(item.slot(), selected) != 0 && after.wornPosition(item.slot(), selected) == 0) {
				item = null;
			}
			pick(after, item, item != null && trying.containsValue(item.id()));
		}

		String petPreviewId(WardrobeNet.State state) {
			var item = state.item(selected);
			return item != null && Slot.PET.lists(item) && state.wornPosition(Slot.PET.id, item.id()) == 0 ? item.id() : null;
		}

		void tab(WardrobeNet.State state, Slot slot) {
			for (int i = 1; i <= WardrobeNet.layeredCapacity(slot.id); i++) {
				var item = state.item(state.worn().get(WardrobeNet.wearKey(slot.id, i)));
				if (item != null && slot.lists(item)) { pick(state, item, true); return; }
			}
			pick(state, null, false);
		}
	}

	/** id → colour being tried on (sent to the server at once if the player owns it). */
	private final Map<String, Integer> tint = new HashMap<>();
	private float previewYaw = 20;
	private boolean dragging;
	private double scroll;
	private final Map<String, ItemStackRenderState> models = new HashMap<>();

	// Layout, recomputed every frame (the panels follow the points beside the player).
	private Box left = new Box(0, 0, 0, 0);
	private Box right = new Box(0, 0, 0, 0);
	private Box bar = new Box(0, 0, 0, 0);
	private int headX;
	private int headY;
	private int backX;
	private int backY;
	private boolean backVisible;
	private int swatchY = -1;
	private int buttonsY;

	/** The HUD is hidden while you look at yourself; put back as it was. */
	private Boolean hudWasHidden;
	private boolean confirmingStoreLink;
	private boolean expired;

	WardrobeScreen(WardrobeNet.State state) {
		super(Component.literal(Tr.s("Гардероб", "Wardrobe")));
		this.state = state;
		selection.tab(state, slot);
	}

	void update(WardrobeNet.State state) {
		selection.update(this.state, state);
		this.state = state;
		refreshArmorButton();
	}

	private static final int ARMOR_ROW_H = 22;
	private final ItemStack armorIcon = new ItemStack(Items.NETHERITE_CHESTPLATE);

	@Override
	protected void init() {
		// Native focus, activation and narration; draw it in the same palette as the card.
		armorButton = addRenderableWidget(new Button(0, 0, LEFT_W - 2 * PAD, 18, Component.empty(),
				button -> WardrobeNet.requestArmorVisibility(), label -> label.get()) {
			@Override
			protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
				Box box = new Box(getX(), getY(), getRight(), getBottom());
				card(g, box, isHoveredOrFocused() ? HOVER_BG : MUTED_BG, isFocused() ? FG : BORDER);
				g.item(armorIcon, box.x0 + 3, box.y0 + 1);
				text(g, getMessage(), box.x0 + 21, box.y0 + 5, FG);
				boolean on = state.armorVisibility() == ArmorVisibility.VISIBLE;
				int x = box.x1 - 24, y = box.y0 + 4;
				card(g, new Box(x, y, x + 19, y + 10), on ? ACCENT : DISABLED, 0);
				int knob = x + (on ? 11 : 2);
				g.fill(knob, y + 2, knob + 6, y + 8, FG);
			}
		});
		armorButton.setTooltip(Tooltip.create(Component.translatable("minehouse.wardrobe.armor.hint")));
		refreshArmorButton();
	}

	private void refreshArmorButton() {
		if (armorButton == null) return;
		ArmorVisibility visibility = state.armorVisibility();
		armorButton.visible = armorButton.active = showsArmorToggle(slot, visibility);
		armorButton.setMessage(Component.translatable(visibility == ArmorVisibility.HIDDEN
				? "minehouse.wardrobe.armor.hidden" : "minehouse.wardrobe.armor.visible"));
		if (!armorButton.visible && armorButton.isFocused()) clearFocus();
	}

	static boolean showsArmorToggle(Slot slot, ArmorVisibility visibility) {
		return slot == Slot.ARMOR && visibility != ArmorVisibility.UNAVAILABLE;
	}

	private int armorRowHeight() {
		return showsArmorToggle(slot, state.armorVisibility()) ? ARMOR_ROW_H : 0;
	}

	void claimFitting() {
		petPreview(selection.petPreviewId(state)); // a fresh authorised open, even if the screen was already up
	}

	@Override
	public void added() {
		super.added();
		confirmingStoreLink = false;
		claimFitting();
		hudWasHidden = minecraft.gui.hud.isHidden();
		if (!hudWasHidden) {
			minecraft.gui.hud.toggle();
		}
	}

	@Override
	public void expire() {
		expired = true; // server already removed this session; don't echo pet_close for a later open
		onClose();
	}

	@Override
	public void removed() {
		if (hasPets() && !confirmingStoreLink && !expired) WardrobeNet.send("pet_close", null, null);
		if (hudWasHidden != null && !hudWasHidden && minecraft.gui.hud.isHidden()) {
			minecraft.gui.hud.toggle();
		}
		super.removed();
	}

	@Override
	public Map<String, String> trying() {
		// If a tentative style loses its last matching piece, show the server outfit instead.
		return selection.trying != state.worn() && requirement(selectedItem()) != null ? state.worn() : selection.trying;
	}

	@Override
	public float previewYaw() {
		return previewYaw;
	}

	@Override
	public Integer tint(String id) {
		return tint.get(id);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ data

	private List<WardrobeNet.Item> shown() {
		return slot.grid(state.items());
	}

	/** The item for a cosmetic, in its colour: the one being tried in the open wardrobe, else the player's. */
	static ItemStack stack(String id) {
		return stack(id, frameOf(id));
	}

	/** The same at a given frame of its animation (-1: it doesn't move). */
	static ItemStack stack(String id, int frame) {
		return stack(id, colorOf(id), frame);
	}

	private static ItemStack stack(String id, Integer rgb, int frame) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(WardrobeNet.NS, id));
		if (rgb != null) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		}
		if (frame >= 0) {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of("f" + frame), List.of()));
		}
		return stack;
	}

	/** The current frame of an animated cosmetic, -1 if it doesn't move. */
	static int frameOf(String id) {
		WardrobeNet.State st = WardrobeNet.state;
		WardrobeNet.Item item = st == null ? null : st.item(id);
		return item == null || item.frames() <= 1 ? -1 : item.frame();
	}

	static Integer colorOf(String id) {
		if (Minecraft.getInstance().gui.screen() instanceof Fitting fitting && fitting.fittingScene() && fitting.tint(id) != null) {
			return fitting.tint(id);
		}
		WardrobeNet.State st = WardrobeNet.state;
		WardrobeNet.Item item = st == null ? null : st.item(id);
		return item == null ? null : item.color();
	}

	private ItemStackRenderState model(WardrobeNet.Item item) {
		String id = item.id();
		int frame = item.frames() > 1 ? item.frame() : -1;
		Integer rgb = colorOf(id);
		return models.computeIfAbsent(id + "#" + rgb + "#" + frame, k -> {
			ItemStackRenderState out = new ItemStackRenderState();
			minecraft.getItemModelResolver().updateForTopItem(out, stack(id, rgb, frame),
					Slot.PET.lists(item) ? ItemDisplayContext.FIXED : ItemDisplayContext.HEAD, minecraft.level, null, 0);
			return out;
		});
	}

	private boolean worn(WardrobeNet.Item item) {
		return state.wornPosition(item.slot(), item.id()) != 0;
	}

	private static boolean full(WardrobeNet.State state, WardrobeNet.Item item) {
		if (item == null || !state.multiWear() || WardrobeNet.layeredCapacity(item.slot()) == 1
				|| state.wornPosition(item.slot(), item.id()) != 0) return false;
		if ("wings".equals(item.kind()) || "cape".equals(item.kind())) {
			for (String id : state.worn().values()) {
				var old = state.item(id);
				if (old != null && old.slot().equals(item.slot()) && ("wings".equals(old.kind()) || "cape".equals(old.kind()))) return false;
			}
		}
		return state.worn().keySet().stream().filter(k -> k.equals(item.slot()) || k.startsWith(item.slot() + ":")).count()
				>= WardrobeNet.layeredCapacity(item.slot());
	}

	private String requirement(WardrobeNet.Item item) {
		if (item == null || !Slot.ARMOR.id.equals(item.slot())) return null;
		return ArmorStyle.requirement(item.id(), slot -> minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemBySlot(slot));
	}

	private WardrobeNet.Item selectedItem() {
		return state.item(selection.selected);
	}

	// ------------------------------------------------------------------ colours

	private static final int[] DYES;
	static {
		DyeColor[] all = DyeColor.values();
		DYES = new int[all.length];
		for (int i = 0; i < all.length; i++) {
			DYES[i] = all[i].getTextureDiffuseColor() & 0xFFFFFF;
		}
	}
	private static final String[] DYE_NAMES = {Tr.s("Белый", "White"), Tr.s("Оранжевый", "Orange"), Tr.s("Сиреневый", "Magenta"), Tr.s("Голубой", "Light blue"), Tr.s("Жёлтый", "Yellow"), Tr.s("Лаймовый", "Lime"), Tr.s("Розовый", "Pink"),
			Tr.s("Серый", "Gray"), Tr.s("Светло-серый", "Light gray"), Tr.s("Бирюзовый", "Cyan"), Tr.s("Фиолетовый", "Purple"), Tr.s("Синий", "Blue"), Tr.s("Коричневый", "Brown"), Tr.s("Зелёный", "Green"), Tr.s("Красный", "Red"), Tr.s("Чёрный", "Black")};
	private static final int SWATCH = 12;
	private static final int SWATCH_GAP = 4;
	private static final int SWATCH_COLS = 7;

	/** Default colour first, then the 16 dyes. */
	private int swatchCount() {
		return DYES.length + 1;
	}

	private int swatchColor(WardrobeNet.Item item, int i) {
		return i == 0 ? item.defaultColor() : DYES[i - 1];
	}

	private Box swatch(int i) {
		int sx = right.x0 + PAD + (i % SWATCH_COLS) * (SWATCH + SWATCH_GAP);
		int sy = swatchY + (i / SWATCH_COLS) * (SWATCH + SWATCH_GAP);
		return new Box(sx, sy, sx + SWATCH, sy + SWATCH);
	}

	private int swatchRows() {
		return (swatchCount() + SWATCH_COLS - 1) / SWATCH_COLS;
	}

	// ------------------------------------------------------------------ text

	private static MutableComponent txt(String s) {
		return Component.literal(s);
	}

	private static MutableComponent bold(String s) {
		return Component.literal(s).withStyle(st -> st.withBold(true));
	}

	private void text(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
		g.text(font, c, x, y, color, false);
	}

	private void centered(GuiGraphicsExtractor g, Component c, int cx, int y, int color) {
		g.text(font, c, cx - font.width(c) / 2, y, color, false);
	}

	private static void coin(GuiGraphicsExtractor g, int x, int y) {
		g.blit(RenderPipelines.GUI_TEXTURED, COIN, x, y, 0.0F, 0.0F, COIN_SIZE, COIN_SIZE, 16, 16, 16, 16, 0xFFFFFFFF);
	}

	/** Width of a price: the number and the coin icon after it. */
	private int priceWidth(Component amount) {
		return font.width(amount) + COIN_GAP + COIN_SIZE;
	}

	/** A price centred on cx: the number, then the coin icon. */
	private void centeredPrice(GuiGraphicsExtractor g, Component amount, int cx, int y, int color) {
		int x = cx - priceWidth(amount) / 2;
		g.text(font, amount, x, y, color, false);
		coin(g, x + font.width(amount) + COIN_GAP, y - 1);
	}

	// ------------------------------------------------------------------ shapes

	record Box(int x0, int y0, int x1, int y1) {
		boolean over(double mx, double my) {
			return mx >= x0 && mx < x1 && my >= y0 && my < y1;
		}

		int w() {
			return x1 - x0;
		}
	}

	/** A card with 3 px rounded corners: background and a 1 px border, without overlapping (the background is see-through). */
	private static void card(GuiGraphicsExtractor g, Box b, int bg, int border) {
		int x0 = b.x0, y0 = b.y0, x1 = b.x1, y1 = b.y1;
		g.fill(x0 + 3, y0 + 1, x1 - 3, y0 + 2, bg);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3, bg);
		g.fill(x0 + 1, y0 + 3, x1 - 1, y1 - 3, bg);
		g.fill(x0 + 2, y1 - 3, x1 - 2, y1 - 2, bg);
		g.fill(x0 + 3, y1 - 2, x1 - 3, y1 - 1, bg);
		if (border == 0) {
			return;
		}
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
			if (i % 3 == 2) {
				continue;
			}
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

	// ------------------------------------------------------------------ the world

	/** Where a world point is on the screen (GUI pixels), or null if it is behind the camera. */
	private int[] project(Vec3 p) {
		Camera cam = minecraft.gameRenderer.mainCamera();
		Vec3 v = p.subtract(cam.position());
		Vector3fc f = cam.forwardVector();
		Vector3fc u = cam.upVector();
		Vector3fc l = cam.leftVector();
		double z = v.x * f.x() + v.y * f.y() + v.z * f.z();
		if (z < 0.05) {
			return null;
		}
		double x = -(v.x * l.x() + v.y * l.y() + v.z * l.z());
		double y = v.x * u.x() + v.y * u.y() + v.z * u.z();
		double k = (height / 2.0) / Math.tan(Math.toRadians(cam.getFov()) / 2);
		return new int[] {(int) Math.round(width / 2.0 + x / z * k), (int) Math.round(height / 2.0 - y / z * k)};
	}

	/** Lay the cards out beside the player and find the slot points on them. */
	private void layout(float partial) {
		int[] center = {width / 2, height / 2};
		Vec3 feet = minecraft.player.getPosition(partial);
		Camera cam = minecraft.gameRenderer.mainCamera();
		Vector3fc l = cam.leftVector();
		Vec3 side = new Vec3(l.x(), 0, l.z()).normalize();

		double bodyYaw = Math.toRadians(minecraft.player.getYRot() + previewYaw);
		Vec3 front = new Vec3(-Math.sin(bodyYaw), 0, Math.cos(bodyYaw));
		int[] head = project(feet.add(0, 1.78, 0));
		Vec3 toCam = cam.position().subtract(feet);
		Vec3 backAt = feet.add(0, 1.12, 0).subtract(front.scale(0.28));
		if (front.dot(new Vec3(toCam.x, 0, toCam.z).normalize()) > 0.3) {
			// Facing the camera: the back is behind the body, show its dot at the shoulder.
			backAt = feet.add(0, 1.3, 0).subtract(front.scale(0.2)).subtract(side.scale(0.42));
		}
		int[] back = project(backAt);
		int[] leftAt = project(feet.add(0, 1.05, 0).add(side.scale(1.15)));
		int[] rightAt = project(feet.add(0, 1.05, 0).subtract(side.scale(1.15)));
		if (head == null) {
			head = new int[] {center[0], center[1] - 60};
		}
		if (back == null) {
			back = new int[] {center[0], center[1] - 20};
		}
		headX = head[0];
		headY = head[1];
		backX = back[0];
		backY = back[1];
		// The back is hidden behind the body when facing the camera.
		backVisible = true;

		int leftH = Math.min(height - 48, TABS_H + PAD * 3 + 2 * (TILE_H + GAP) + 14 + rowsExtra() + armorRowHeight());
		int rightH = rightHeight();
		int lx = leftAt == null ? center[0] - 70 - LEFT_W : leftAt[0] - LEFT_W;
		int rx = rightAt == null ? center[0] + 70 : rightAt[0];
		// Keep them on the screen and off the player.
		lx = Mth.clamp(lx, 8, Math.max(8, center[0] - 46 - LEFT_W));
		rx = Mth.clamp(rx, Math.min(width - 8 - RIGHT_W, center[0] + 46), width - 8 - RIGHT_W);
		int ly = Mth.clamp((leftAt == null ? center[1] : leftAt[1]) - leftH / 2, 8, height - 40 - leftH);
		int ry = Mth.clamp((rightAt == null ? center[1] : rightAt[1]) - rightH / 2 + 12, 8, height - 40 - rightH);
		left = new Box(lx, ly, lx + LEFT_W, ly + leftH);
		right = new Box(rx, ry, rx + RIGHT_W, ry + rightH);
	}

	/** A third row of tiles when there is room for it. */
	private int rowsExtra() {
		int rows = (shown().size() + COLS - 1) / COLS;
		return rows > 2 && height > 260 ? TILE_H + GAP : 0;
	}

	/** Must match {@link #rightCard}: header, content, buttons, a rule, the balance. */
	private int rightHeight() {
		WardrobeNet.Item item = selectedItem();
		int content;
		if (item == null) {
			content = 40;
		} else {
			content = (font.split(bold(item.name()), RIGHT_W - PAD * 2).size() + requirementLines(item).size()) * 11 + 4;
			if (item.dyeable()) {
				content += 12 + swatchRows() * (SWATCH + SWATCH_GAP);
			}
		}
		return PAD + 16 + content + 6 + 18 + BALANCE_H + PAD - 2;
	}

	private static final int BALANCE_H = 6 + 5 + 12;

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// No shade: the player stands in the fitting room, the camera in front of them.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (minecraft.player == null) {
			return;
		}
		layout(a);
		slots(g, mouseX, mouseY);
		leftCard(g, mouseX, mouseY, a);
		rightCard(g, mouseX, mouseY);
		bottomBar(g, mouseX, mouseY);
		armorButton.setPosition(left.x0 + PAD, gridY1() + 2);
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	/** The dots on the head and the back, and the line from the right card to the slot being dressed. */
	private void slots(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		// Ground companions have no attachment point on the player's body.
		if (slot == Slot.PET) return;
		int sx = slot != Slot.HAT ? backX : headX;
		int sy = slot != Slot.HAT ? backY : headY;
		dashedLine(g, right.x0, right.y0 + 14, sx, sy, (ACCENT & 0xFFFFFF) | 0xC0000000);
		boolean hoverHead = Math.abs(mouseX - headX) <= 6 && Math.abs(mouseY - headY) <= 6;
		boolean hoverBack = Math.abs(mouseX - backX) <= 6 && Math.abs(mouseY - backY) <= 6;
		dot(g, headX, headY, slot == Slot.HAT ? ACCENT : (hoverHead ? FG : 0xFFD4D4D8), slot == Slot.HAT);
		if (backVisible) {
			dot(g, backX, backY, slot != Slot.HAT ? ACCENT : (hoverBack ? FG : 0xFFD4D4D8), slot != Slot.HAT);
		}
		if (hoverHead) {
			g.setTooltipForNextFrame(font, txt(Tr.s("Голова", "Head")), mouseX, mouseY);
		} else if (hoverBack && backVisible) {
			g.setTooltipForNextFrame(font, txt(Slot.BACKPACK.title), mouseX, mouseY);
		}
	}

	/** Rendering and mouse input share the same row, including when only icons fit. */
	record TabRow(int leftX, int leftY, Slot active, int titleWidth) {
		private static final int INNER = LEFT_W - PAD * 2 - 4;

		boolean labelled() {
			int wide = INNER - (Slot.values().length - 1) * TAB_ICON_W;
			return wide >= 16 + 3 + titleWidth + 6;
		}

		Box tab(Slot s) {
			Slot[] all = Slot.values();
			boolean labelled = labelled();
			int x = leftX + PAD + 2;
			for (Slot o : all) {
				int w;
				if (labelled) {
					w = o == active ? INNER - (all.length - 1) * TAB_ICON_W : TAB_ICON_W;
				} else {
					w = INNER / all.length + (o.ordinal() == all.length - 1 ? INNER % all.length : 0);
				}
				if (o == s) {
					return new Box(x, leftY + PAD + 2, x + w, leftY + PAD + TABS_H - 2);
				}
				x += w;
			}
			throw new IllegalStateException();
		}

		String tooltip(double mx, double my) {
			Slot hovered = hit(mx, my);
			return hovered == null || (hovered == active && labelled()) ? null : hovered.title;
		}

		Slot hit(double mx, double my) {
			for (Slot s : Slot.values()) {
				if (s.id != null && tab(s).over(mx, my)) return s;
			}
			return null;
		}
	}

	private TabRow tabRow() {
		return new TabRow(left.x0, left.y0, slot, font.width(slot.title));
	}

	private int gridY0() {
		return left.y0 + PAD + TABS_H + PAD;
	}

	private int gridY1() {
		return left.y1 - PAD - 12 - armorRowHeight();
	}

	private int tileX(int i) {
		return left.x0 + PAD + (i % COLS) * (TILE_W + GAP);
	}

	private int tileY(int i) {
		return gridY0() + (i / COLS) * (TILE_H + GAP) - (int) scroll;
	}

	private int maxScroll() {
		int rows = (shown().size() + COLS - 1) / COLS;
		return Math.max(0, rows * (TILE_H + GAP) - GAP - (gridY1() - gridY0()));
	}

	private void leftCard(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		card(g, left, CARD, BORDER);
		// Tabs, like shadcn's: a muted track with the active one raised.
		card(g, new Box(left.x0 + PAD, left.y0 + PAD, left.x1 - PAD, left.y0 + PAD + TABS_H), MUTED_BG, 0);
		TabRow row = tabRow();
		for (Slot s : Slot.values()) {
			Box t = row.tab(s);
			boolean on = s == slot;
			boolean hover = t.over(mouseX, mouseY);
			if (on) {
				card(g, t, 0xFF09090B, 0xFF27272A);
			} else if (hover && s.id != null) {
				card(g, t, HOVER_BG, 0);
			}
			int mid = (t.x0 + t.x1) / 2;
			int iy = t.y0 + (t.y1 - t.y0 - 16) / 2;
			if (on && row.labelled()) {
				int w = 16 + 3 + row.titleWidth();
				int ix = mid - w / 2;
				g.item(s.stack(), ix, iy);
				text(g, txt(s.title), ix + 19, iy + 4, FG);
			} else {
				g.item(s.stack(), mid - 8, iy);

			}
		}
		String tooltip = row.tooltip(mouseX, mouseY);
		if (tooltip != null) g.setTooltipForNextFrame(font, txt(tooltip), mouseX, mouseY);

		List<WardrobeNet.Item> items = shown();
		g.enableScissor(left.x0 + 1, gridY0() - 1, left.x1 - 1, gridY1());
		if (items.isEmpty()) {
			centered(g, txt(slot.id == null ? Tr.s("Скоро", "Soon") : Tr.s("Пока пусто", "Empty so far")), (left.x0 + left.x1) / 2, gridY0() + 30, MUTED);
		}
		float time = (minecraft.level != null ? minecraft.level.getGameTime() : 0) + a;
		for (int i = 0; i < items.size(); i++) {
			WardrobeNet.Item item = items.get(i);
			int x = tileX(i);
			int y = tileY(i);
			if (y > gridY1() || y + TILE_H < gridY0()) {
				continue;
			}
			Box b = new Box(x, y, x + TILE_W, y + TILE_H);
			boolean hover = b.over(mouseX, mouseY) && mouseY >= gridY0() && mouseY < gridY1();
			String need = requirement(item);
			boolean sel = item.id().equals(selection.selected);
			card(g, b, hover ? HOVER_BG : MUTED_BG, sel ? ACCENT : (worn(item) ? 0xFF3F3F46 : BORDER));
			if (item.flatIcon()) {
				// Use the inventory view for a sprite card: no HEAD pitch or hover rotation.
				g.pose().pushMatrix();
				g.pose().translate(x + (TILE_W - 32) / 2, y + 2);
				g.pose().scale(2f, 2f);
				g.item(stack(item.id()), 0, 0);
				g.pose().popMatrix();
			} else {
				float spin = hover || sel ? time * 4 : 35;
				g.guiRenderState.addPicturesInPictureState(new WardrobePreview.State(null, 0, model(item), spin,
						x + 3, y + 2, x + TILE_W - 3, y + TILE_H - 12, 28, g.scissorStack.peek()));
			}
			if (worn(item)) {
				centered(g, txt(need == null ? Tr.s("Надето", "Worn") : Tr.s("Выбран", "Selected")), x + TILE_W / 2, y + TILE_H - 10, ACCENT);
			} else if (need != null && item.owned()) {
				centered(g, txt(Tr.s("Нельзя", "Locked")), x + TILE_W / 2, y + TILE_H - 10, MUTED);
			} else if (item.owned()) {
				centered(g, txt(Tr.s("Есть", "Owned")), x + TILE_W / 2, y + TILE_H - 10, GREEN);
			} else {
				centeredPrice(g, txt(String.valueOf(item.price())), x + TILE_W / 2, y + TILE_H - 10, MUTED);
			}
			if (hover) {
				if (need == null) {
					g.setTooltipForNextFrame(font, txt(item.name()), mouseX, mouseY);
				} else {
					g.setComponentTooltipForNextFrame(font, List.of(txt(item.name()), txt(need)), mouseX, mouseY);
				}
			}
		}
		g.disableScissor();
		String hint = maxScroll() > 0 ? Tr.s("Колесо мыши: ещё", "Mouse wheel: more") : Tr.s("Двойной клик: надеть", "Double-click: wear");
		if (slot == Slot.ARMOR && maxScroll() == 0) hint = Tr.s("Клик: о стиле", "Click: style info");
		text(g, txt(hint), left.x0 + PAD, left.y1 - PAD - 8, FAINT);
	}

	private List<FormattedCharSequence> requirementLines(WardrobeNet.Item item) {
		String need = requirement(item);
		return need == null ? List.of() : font.split(txt(need), RIGHT_W - PAD * 2);
	}

	private Box actionButton() {
		int w = (RIGHT_W - PAD * 2 - 6) / 2;
		return new Box(right.x0 + PAD, buttonsY, right.x0 + PAD + w, buttonsY + 18);
	}

	private Box takeOffButton() {
		int w = (RIGHT_W - PAD * 2 - 6) / 2;
		return new Box(right.x1 - PAD - w, buttonsY, right.x1 - PAD, buttonsY + 18);
	}

	private String actionLabel(WardrobeNet.Item item) {
		if (item == null) {
			return Tr.s("Надеть", "Wear");
		}
		if (worn(item)) {
			return Tr.s("Снять", "Take off");
		}
		if (item.owned()) {
			return Tr.s("Надеть", "Wear");
		}
		return String.valueOf(item.price());
	}

	/** The action buys the item: its label is a price and gets the coin icon. */
	private static boolean buys(WardrobeNet.Item item) {
		return item != null && !item.owned();
	}

	enum Action {
		NONE, REMOVE, WEAR, BUY;

		static Action forItem(WardrobeNet.Item item, boolean worn, String requirement, int coins) {
			if (item == null) return NONE;
			if (worn) return REMOVE;
			if (item.owned()) return requirement == null ? WEAR : NONE;
			return coins >= item.price() ? BUY : NONE;
		}

		static Action forItem(WardrobeNet.State state, WardrobeNet.Item item, String requirement) {
			if (item == null) return NONE;
			return full(state, item) ? NONE : forItem(item, state.wornPosition(item.slot(), item.id()) != 0, requirement, state.coins());
		}
	}

	private Action action(WardrobeNet.Item item) {
		return Action.forItem(state, item, requirement(item));
	}

	/** All activation paths re-read the equipped stacks, independently of the last frame. */
	private void activate(WardrobeNet.Item item) {
		switch (action(item)) {
			case WEAR, REMOVE -> equipOrRemove(item);
			case BUY -> WardrobeNet.send("buy", "id", item.id());
			case NONE -> { }
		}
	}

	private void rightCard(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		card(g, right, CARD, BORDER);
		WardrobeNet.Item item = selectedItem();
		int x = right.x0 + PAD;
		int y = right.y0 + PAD;
		long used = state.worn().keySet().stream().filter(k -> k.equals(slot.id) || k.startsWith(slot.id + ":")).count();
		text(g, txt(slot.title + " (" + used + "/" + WardrobeNet.layeredCapacity(slot.id) + ")"), x, y + 2, MUTED);
		if (item != null) {
			boolean price = buys(item) && !worn(item);
			Component badge = txt(worn(item) ? (requirement(item) == null ? Tr.s("Надето", "Worn") : Tr.s("Выбран", "Selected")) : (item.owned() ? Tr.s("Есть", "Owned") : String.valueOf(item.price())));
			int bw = (price ? priceWidth(badge) : font.width(badge)) + 10;
			Box b = new Box(right.x1 - PAD - bw, y, right.x1 - PAD, y + 12);
			card(g, b, 0x00000000, worn(item) ? ACCENT : BORDER);
			if (price) {
				centeredPrice(g, badge, (b.x0 + b.x1) / 2, y + 2, FG);
			} else {
				centered(g, badge, (b.x0 + b.x1) / 2, y + 2, worn(item) ? ACCENT : (item.owned() ? GREEN : FG));
			}
		}
		y += 16;
		swatchY = -1;
		if (item == null) {
			text(g, txt(Tr.s("Выбери вещь слева", "Pick an item on the left")), x, y + 2, FG);
			text(g, txt(Tr.s("или точку на себе,", "or a spot on yourself")), x, y + 13, MUTED);
			text(g, txt(Tr.s("чтобы примерить", "to try it on")), x, y + 24, MUTED);
			y += 40;
		} else {
			for (FormattedCharSequence line : font.split(bold(item.name()), RIGHT_W - PAD * 2)) {
				g.text(font, line, x, y, FG, false);
				y += 11;
			}
			y += 4;
			for (FormattedCharSequence line : requirementLines(item)) {
				g.text(font, line, x, y, MUTED, false);
				y += 11;
			}
			if (item.dyeable()) {
				text(g, txt(Tr.s("Цвет", "Color")), x, y, MUTED);
				y += 12;
				swatchY = y;
				Integer c = colorOf(item.id());
				int current = c == null ? item.defaultColor() : c;
				for (int i = 0; i < swatchCount(); i++) {
					Box s = swatch(i);
					int rgb = swatchColor(item, i);
					boolean on = rgb == current;
					boolean hover = s.over(mouseX, mouseY);
					if (on || hover) {
						card(g, new Box(s.x0 - 2, s.y0 - 2, s.x1 + 2, s.y1 + 2), 0x00000000, on ? ACCENT : 0xFF71717A);
					}
					card(g, s, 0xFF000000 | rgb, 0x30FFFFFF);
					if (i == 0) {
						centered(g, Component.literal("↺"), (s.x0 + s.x1) / 2 + 1, s.y0 + 2, (rgb & 0xFFFFFF) > 0x808080 ? 0xFF18181B : 0xFFFFFFFF);
					}
					if (hover) {
						g.setTooltipForNextFrame(font, txt(i == 0 ? Tr.s("Как было", "Undo") : DYE_NAMES[i - 1]), mouseX, mouseY);
					}
				}
				y += swatchRows() * (SWATCH + SWATCH_GAP);
			}
		}
		y += 6;
		buttonsY = y;

		Box act = actionButton();
		boolean on = action(item) != Action.NONE;
		boolean hover = on && act.over(mouseX, mouseY);
		boolean remove = item != null && worn(item);
		if (remove) {
			card(g, act, hover ? 0x33F87171 : 0x00000000, RED);
			centered(g, txt(actionLabel(item)), (act.x0 + act.x1) / 2, act.y0 + 5, RED);
		} else {
			card(g, act, on ? (hover ? PRIMARY_HOVER : PRIMARY) : 0xFF27272A, 0);
			if (buys(item)) {
				centeredPrice(g, txt(actionLabel(item)), (act.x0 + act.x1) / 2, act.y0 + 5, on ? ON_PRIMARY : DISABLED);
			} else {
				centered(g, txt(actionLabel(item)), (act.x0 + act.x1) / 2, act.y0 + 5, on ? ON_PRIMARY : DISABLED);
			}
		}
		Box off = takeOffButton();
		boolean offOn = !state.worn().isEmpty();
		card(g, off, offOn && off.over(mouseX, mouseY) ? HOVER_BG : 0x00000000, BORDER);
		centered(g, txt(Tr.s("Снять всё", "Take all off")), (off.x0 + off.x1) / 2, off.y0 + 5, offOn ? FG : DISABLED);

		// The balance, under a rule.
		g.fill(right.x0 + 1, buttonsY + 24, right.x1 - 1, buttonsY + 25, BORDER);
		int by = buttonsY + 29;
		Component balance = bold(String.valueOf(state.coins()));
		text(g, balance, x, by + 2, FG);
		coin(g, x + font.width(balance) + COIN_GAP, by + 1);
		Component top = txt(Tr.s("Пополнить", "Top up"));
		int tw = font.width(top);
		boolean topHover = topUpButton().over(mouseX, mouseY);
		text(g, top, right.x1 - PAD - tw, by + 2, topHover ? FG : ACCENT);
	}

	private Box topUpButton() {
		int by = buttonsY + 29;
		int tw = font.width(txt(Tr.s("Пополнить", "Top up")));
		return new Box(right.x1 - PAD - tw - 2, by - 2, right.x1 - PAD + 2, by + 12);
	}

	/** The hints along the bottom, like keyboard shortcuts on the site. */
	private void bottomBar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		String[][] parts = {{Tr.s("ЛКМ", "LMB"), Tr.s("выбрать", "choose")}, {Tr.s("тяни", "drag"), Tr.s("повернуться", "turn")}, {"Esc", Tr.s("выйти", "exit")}};
		int w = 12;
		for (String[] p : parts) {
			w += font.width(txt(p[0])) + 8 + 4 + font.width(txt(p[1])) + 12;
		}
		int bx0 = width / 2 - w / 2;
		int by0 = height - 26;
		bar = new Box(bx0, by0, bx0 + w, by0 + 18);
		card(g, bar, CARD, BORDER);
		int x = bx0 + 12;
		for (String[] p : parts) {
			int kw = font.width(txt(p[0])) + 8;
			card(g, new Box(x, by0 + 3, x + kw, by0 + 15), 0x00000000, 0xFF3F3F46);
			text(g, txt(p[0]), x + 4, by0 + 5, FG);
			x += kw + 4;
			text(g, txt(p[1]), x, by0 + 5, MUTED);
			x += font.width(txt(p[1])) + 12;
		}
	}

	// ------------------------------------------------------------------ input

	private boolean hasPets() {
		return state.items().stream().anyMatch(Slot.PET::lists);
	}

	/** The server draws this slot even for our own player. No client equipment layer. */
	private void petPreview(String overrideId) {
		if (hasPets()) {
			WardrobeNet.send("pet_preview", overrideId == null ? null : "id", overrideId);
		}
	}

	private void equipOrRemove(WardrobeNet.Item item) {
		boolean remove = worn(item);
		if (Slot.PET.lists(item)) {
			petPreview(null); // follow authoritative equipment after the action, even if it is rejected
		}
		if (remove) WardrobeNet.send(WardrobeNet.removeAction(state, item.slot(), item.id()));
		else WardrobeNet.send("wear", "id", item.id());
	}

	/** The server may expire the fitting while the player is reading the store confirmation. */
	private static final class StoreLink extends ConfirmLinkScreen implements WardrobeNet.FittingView {
		StoreLink(WardrobeScreen parent, URI url) {
			super(accept -> {
				if (accept) com.mojang.blaze3d.Blaze3D.openUri(url);
				parent.minecraft.gui.setScreen(parent);
			}, url, true);
		}

		@Override public void expire() { onClose(); } // dismiss without invoking the return-to-parent callback
	}

	private void selectSlot(Slot s) {
		if (slot != s) {
			slot = s;
			scroll = 0;
			selection.tab(state, s);
			refreshArmorButton();
			petPreview(selection.petPreviewId(state));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (slot == Slot.ARMOR && event.isConfirmation() && getFocused() != armorButton) {
			activate(selectedItem());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}

		if (super.mouseClicked(event, doubleClick)) return true;

		// The slot points on the player.
		if (slot != Slot.PET && Math.abs(mx - headX) <= 6 && Math.abs(my - headY) <= 6) {
			selectSlot(Slot.HAT);
			return true;
		}
		if (slot != Slot.PET && backVisible && Math.abs(mx - backX) <= 6 && Math.abs(my - backY) <= 6) {
			selectSlot(Slot.BACKPACK);
			return true;
		}

		if (left.over(mx, my)) {
			Slot hit = tabRow().hit(mx, my);
			if (hit != null) {
				selectSlot(hit);
				return true;
			}
			List<WardrobeNet.Item> items = shown();
			for (int i = 0; i < items.size(); i++) {
				Box b = new Box(tileX(i), tileY(i), tileX(i) + TILE_W, tileY(i) + TILE_H);
				if (b.over(mx, my) && my >= gridY0() && my < gridY1()) {
					WardrobeNet.Item item = items.get(i);
					// Grid tiles are drawn by the screen, so they must take focus from the widget explicitly.
					if (getFocused() == armorButton) clearFocus();
					selection.pick(state, item, requirement(item) == null);
					petPreview(selection.petPreviewId(state));
					if (doubleClick && (item.owned() || worn(item))) {
						activate(item);
					}
					return true;
				}
			}
			return true;
		}

		if (right.over(mx, my)) {
			WardrobeNet.Item sel = selectedItem();
			if (sel != null && sel.dyeable() && swatchY >= 0) {
				for (int i = 0; i < swatchCount(); i++) {
					if (swatch(i).over(mx, my)) {
						int rgb = swatchColor(sel, i);
						tint.put(sel.id(), rgb);
						if (sel.owned()) {
							WardrobeNet.dye(sel.id(), rgb);
						}
						return true;
					}
				}
			}
			if (actionButton().over(mx, my)) {
				activate(sel);
				return true;
			}
			if (takeOffButton().over(mx, my) && !state.worn().isEmpty()) {
				petPreview(null);
				if (state.multiWear()) WardrobeNet.send("off_all", null, null);
				else for (String slot : state.wornSlots()) WardrobeNet.send("remove", "slot", slot);
				selection.pick(state, null, false);
				return true;
			}
			if (topUpButton().over(mx, my)) {
				confirmingStoreLink = true; // this screen is restored after the link confirmation
				minecraft.gui.setScreen(new StoreLink(this, URI.create(state.store())));
				return true;
			}
			return true;
		}

		if (bar.over(mx, my)) {
			return true;
		}
		// Anywhere else: turn round.
		dragging = true;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			previewYaw = Mth.wrapDegrees(previewYaw - (float) dx * 2.5f);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (left.over(x, y)) {
			scroll = Mth.clamp(scroll - scrollY * 20, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
}
