package pro.minehouse.riftclient;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import pro.minehouse.riftclient.mixin.BossBarsAccess;

/**
 * Server notices that come as vanilla boss bars (the countdown to a restart): in the mode they are
 * drawn in its own style, a bright plate under the top of the screen, because the vanilla bar is
 * lost against the dark picture and under the lobby screen.
 */
public final class RiftNotice {
	private RiftNotice() {
	}

	/** Whether the mode's interface is on: then the vanilla bars are not drawn. */
	public static boolean on() {
		return TopDown.active() || TopDown.fixed() || Minecraft.getInstance().gui.screen() instanceof PartyScreen;
	}

	static int color(BossEvent.BossBarColor c) {
		return switch (c) {
			case GREEN -> Ui.GREEN;
			case YELLOW -> Ui.YELLOW;
			case RED, PINK -> Ui.RED;
			case BLUE -> 0x60A5FA;
			case PURPLE -> Ui.ACCENT;
			default -> Ui.WHITE;
		};
	}

	/** The plates, one under another, centred, the first at {@code top}. */
	static void draw(GuiGraphicsExtractor g, Font font, int screenWidth, int top) {
		Map<UUID, LerpingBossEvent> events = ((BossBarsAccess) Minecraft.getInstance().gui.hud.getBossOverlay()).minehouse$events();
		int y = top;
		for (LerpingBossEvent e : events.values()) {
			int text = font.width(e.getName());
			int w = Math.max(180, text + 24);
			int x = (screenWidth - w) / 2;
			int rgb = color(e.getColor());
			Ui.panel(g, x - 1, y - 1, w + 2, 26, rgb, 0.9f);
			Ui.panel(g, x, y, w, 24, Ui.PANEL, 1f);
			g.centeredText(font, e.getName(), screenWidth / 2, y + 4, 0xFFFFFFFF);
			Ui.bar(g, x + 8, y + 16, w - 16, 3, e.getProgress(), rgb, Ui.EDGE, 1f);
			y += 30;
		}
	}
}
