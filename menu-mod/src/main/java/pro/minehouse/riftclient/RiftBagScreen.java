package pro.minehouse.riftclient;

import pro.minehouse.menu.Tr;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Rift's own window for things: a chest of the dungeon, or the bag (the player's inventory) on
 * its own key, since E is an ability there. The slots work as in the usual game; on top of that a
 * right click on a piece of armour puts it on, and the column on the left shows what is worn.
 */
public final class RiftBagScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
	static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final String[] WORN_NAMES = {Tr.s("Шлем", "Helmet"), Tr.s("Нагрудник", "Chestplate"), Tr.s("Поножи", "Leggings"), Tr.s("Ботинки", "Boots")};
	private static final int BOX = 18;
	private static final int GEAR_W = 30;
	/** The bag's crafting grid and its result: of no use in a run, neither drawn nor clickable. */
	private static final int CRAFT_SLOTS = 5;

	private final boolean chest;
	private final int rows;

	private RiftBagScreen(T menu, Inventory inventory, Component title, boolean chest, int rows, int height) {
		super(menu, inventory, title, 176, height);
		this.chest = chest;
		this.rows = rows;
	}

	public static RiftBagScreen<ChestMenu> chest(ChestMenu menu, Inventory inventory) {
		return new RiftBagScreen<>(menu, inventory, Component.literal(Tr.s("СУНДУК", "CHEST")), true, menu.getRowCount(), chestHeight(menu.getRowCount()));
	}

	public static RiftBagScreen<InventoryMenu> bag(Inventory inventory) {
		return new RiftBagScreen<>(inventory.player.inventoryMenu, inventory, Component.literal(Tr.s("СУМКА", "BAG")), false, 0, 166);
	}

	/** As tall as the usual chest window of that many rows, so the slots of the menu fall in place. */
	static int chestHeight(int rows) {
		return 114 + rows * 18;
	}

	/** What the server is asked: a slot of the open window to put on, or -1 - n to take piece n off. */
	static int takeOff(int piece) {
		return -1 - piece;
	}

	/** The piece of armour a stack would be worn as, or -1 when it is not armour. */
	static int piece(net.minecraft.world.entity.LivingEntity who, ItemStack stack) {
		if (stack.isEmpty()) {
			return -1;
		}
		EquipmentSlot slot = who.getEquipmentSlotForItem(stack);
		for (int i = 0; i < WORN.length; i++) {
			if (WORN[i] == slot) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void init() {
		super.init();
		if (this.chest) {
			this.addRenderableWidget(new Ui.Flat(this.leftPos + this.imageWidth - 8 - 78, this.topPos + 4, 78, 12, Component.literal(Tr.s("ЗАБРАТЬ ВСЁ", "TAKE ALL")),
					Ui.ACCENT, Ui.INK, -1, 0.75f, this::takeAll));
		}
	}

	private void takeAll() {
		for (int i = 0; i < this.rows * 9 && i < this.menu.slots.size(); i++) {
			Slot slot = this.menu.slots.get(i);
			if (slot.hasItem()) {
				this.slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
			}
		}
	}

	private boolean hidden(Slot slot) {
		return !this.chest && slot.index < CRAFT_SLOTS;
	}

	private int gearX() {
		return this.leftPos - GEAR_W - 4;
	}

	private int gearY(int piece) {
		return this.topPos + 20 + piece * (BOX + 2);
	}

	private int gearAt(double x, double y) {
		if (!this.chest) {
			return -1;
		}
		for (int i = 0; i < WORN.length; i++) {
			int bx = this.gearX() + 6;
			int by = this.gearY(i);
			if (x >= bx && x < bx + BOX && y >= by && y < by + BOX) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		Ui.panel(g, x - 1, y - 1, this.imageWidth + 2, this.imageHeight + 2, Ui.EDGE, 1);
		Ui.panel(g, x, y, this.imageWidth, this.imageHeight, Ui.PANEL, 0.97f);
		Ui.panel(g, x, y, this.imageWidth, 2, Ui.ACCENT, 1);
		for (Slot slot : this.menu.slots) {
			if (!this.hidden(slot)) {
				box(g, x + slot.x - 1, y + slot.y - 1);
			}
		}
		if (this.chest) {
			int gx = this.gearX();
			int gh = 20 + WORN.length * (BOX + 2) + 4;
			Ui.panel(g, gx - 1, y - 1, GEAR_W + 2, gh + 2, Ui.EDGE, 1);
			Ui.panel(g, gx, y, GEAR_W, gh, Ui.PANEL, 0.97f);
			Ui.panel(g, gx, y, GEAR_W, 2, Ui.ACCENT, 1);
			for (int i = 0; i < WORN.length; i++) {
				box(g, gx + 6, this.gearY(i));
			}
		}
	}

	private static void box(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + BOX, y + BOX, Ui.a(Ui.EDGE, 1));
		g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, Ui.a(Ui.INK, 1));
	}

	@Override
	protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
		if (!this.hidden(slot)) {
			super.extractSlot(g, slot, mouseX, mouseY);
		}
	}

	/** Drawn from the window's corner, as the slots are. */
	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (this.chest) {
			g.text(this.font, this.title, 8, 6, Ui.a(Ui.WHITE, 1), false);
			g.text(this.font, Component.literal(Tr.s("СУМКА", "BAG")), 8, this.imageHeight - 94, Ui.a(Ui.MUTED, 1), false);
			g.text(this.font, Component.literal(Tr.s("ПКМ по броне: надеть", "Right-click armor: wear")), 8, this.imageHeight + 5, Ui.a(Ui.MUTED, 1), true);
			int gx = this.gearX() - this.leftPos;
			Ui.big(g, this.font, Tr.s("БРОНЯ", "ARMOR"), gx + GEAR_W / 2, 8, 0.5f, Ui.a(Ui.MUTED, 1), 1);
			for (int i = 0; i < WORN.length; i++) {
				ItemStack worn = this.minecraft.player.getItemBySlot(WORN[i]);
				int bx = gx + 7;
				int by = this.gearY(i) - this.topPos + 1;
				if (!worn.isEmpty()) {
					g.item(worn, bx, by);
					g.itemDecorations(this.font, worn, bx, by);
				}
			}
		} else {
			g.text(this.font, this.title, 32, 10, Ui.a(Ui.WHITE, 1), false);
			g.text(this.font, Component.literal(Tr.s("Броня: слоты слева", "Armor: slots on the left")), 32, 24, Ui.a(Ui.MUTED, 1), false);
			g.text(this.font, Component.literal(Tr.s("Вторая рука", "Off hand")), 98, 67, Ui.a(Ui.MUTED, 1), false);
			g.text(this.font, Component.literal(Tr.s("Shift + клик по броне: надеть", "Shift + click armor: wear")), 8, this.imageHeight + 5, Ui.a(Ui.MUTED, 1), true);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int piece = this.gearAt(mouseX, mouseY);
		if (piece >= 0 && this.menu.getCarried().isEmpty()) {
			ItemStack worn = this.minecraft.player.getItemBySlot(WORN[piece]);
			if (worn.isEmpty()) {
				g.setTooltipForNextFrame(this.font, Component.literal(WORN_NAMES[piece] + Tr.s(": пусто", ": empty")), mouseX, mouseY);
			} else {
				g.setTooltipForNextFrame(this.font, worn, mouseX, mouseY);
			}
			return;
		}
		if (this.hoveredSlot != null && this.hidden(this.hoveredSlot)) {
			return;
		}
		super.extractTooltip(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int piece = this.gearAt(event.x(), event.y());
		if (piece >= 0) {
			if (!this.minecraft.player.getItemBySlot(WORN[piece]).isEmpty()) {
				equip(takeOff(piece));
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
		if (slot != null && this.hidden(slot)) {
			return;
		}
		// In a chest a right click on armour wears it; the bag has its own armour slots.
		if (this.chest && slot != null && input == ContainerInput.PICKUP && button == 1 && this.menu.getCarried().isEmpty()
				&& piece(this.minecraft.player, slot.getItem()) >= 0 && ClientPlayNetworking.canSend(RiftUiPayloads.Equip.TYPE)) {
			equip(slot.index);
			return;
		}
		super.slotClicked(slot, slotId, button, input);
	}

	private static void equip(int what) {
		if (ClientPlayNetworking.canSend(RiftUiPayloads.Equip.TYPE)) {
			ClientPlayNetworking.send(new RiftUiPayloads.Equip(what));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (MinehouseRiftClient.bagKey() != null && MinehouseRiftClient.bagKey().matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void containerTick() {
		// The window is the run's: when the run is over (or we fell) it closes.
		if (!RiftHud.inRun() || RiftHud.run().downLeft() >= 0) {
			this.onClose();
		}
	}
}
