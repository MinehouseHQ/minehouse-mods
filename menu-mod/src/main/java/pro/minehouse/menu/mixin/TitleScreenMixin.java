package pro.minehouse.menu.mixin;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.minehouse.menu.ChapterEnd;
import pro.minehouse.menu.MenuConfig;
import pro.minehouse.menu.PackModsScreen;
import pro.minehouse.menu.PackUpdater;
import pro.minehouse.menu.SelfTest;
import pro.minehouse.menu.ServerStatus;
import pro.minehouse.menu.intro.Intro;
import pro.minehouse.menu.intro.IntroScreen;

/**
 * Title screen tweaks:
 * - MineHouse logo instead of the Minecraft one;
 * - first row is a "Play on MineHouse" button that shows the server's online count;
 * - Singleplayer and Multiplayer share the second row;
 * - Realms is replaced by "Site" and "Discord".
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	private static final Identifier MINEHOUSE_LOGO = Identifier.fromNamespaceAndPath("minehouse-menu", "textures/gui/logo.png");
	private static final int ACCENT = 0xD8B4FE;

	private static final Identifier DOT = Identifier.fromNamespaceAndPath("minehouse-menu", "textures/gui/black_dot.png");
	private static final Identifier DOT_GLOW = Identifier.fromNamespaceAndPath("minehouse-menu", "textures/gui/black_dot_glow.png");

	private Button minehouse$play;
	private int minehouse$dotY = -1;

	@Shadow
	private boolean fading;

	@Shadow
	private SplashRenderer splash;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Redirect(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V")
	)
	private void minehouse$logo(LogoRenderer renderer, GuiGraphicsExtractor graphics, int width, float alpha) {
		if (this.minehouse$dotY >= 0) {
			minehouse$dot(graphics);
			return;
		}
		// 512x128 texture drawn at 256x64 GUI pixels so it stays sharp on GUI scale 2.
		graphics.blit(RenderPipelines.GUI_TEXTURED, MINEHOUSE_LOGO, width / 2 - 128, 22, 0.0F, 0.0F, 256, 64, 512, 128, 512, 128, ARGB.white(alpha));
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void minehouse$layout(CallbackInfo ci) {
		if (ChapterEnd.active()) {
			// Chapter 1 is over: the menu is black with the dot the world fell into instead of the logo.
			// You can still play, just not on MineHouse: no join button, no links, no Realms.
			this.minehouse$play = null;
			this.minehouse$dotY = 54;
			this.splash = null;
			Button singleplayer = minehouse$find("menu.singleplayer");
			Button multiplayer = minehouse$find("menu.multiplayer");
			Button realms = minehouse$find("menu.online");
			if (singleplayer != null && multiplayer != null && realms != null) {
				int left = this.width / 2 - 100;
				int top = singleplayer.getY();
				singleplayer.setPosition(left, top);
				singleplayer.setWidth(98);
				multiplayer.setPosition(left + 102, top);
				multiplayer.setWidth(98);
				this.removeWidget(realms);
			}
			ServerStatus.refresh(this.minecraft);
			return;
		}
		this.minehouse$dotY = -1;
		Button singleplayer = minehouse$find("menu.singleplayer");
		Button multiplayer = minehouse$find("menu.multiplayer");
		Button realms = minehouse$find("menu.online");
		if (singleplayer == null || multiplayer == null || realms == null) {
			return; // demo mode or another mod changed the screen: keep vanilla layout
		}
		int left = this.width / 2 - 100;
		int top = singleplayer.getY();
		int realmsY = realms.getY();

		this.minehouse$play = Button.builder(
				ServerStatus.playLabel(),
				b -> ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(MenuConfig.serverAddress), ServerStatus.data(), false, null)
			)
			.bounds(left, top, 200, 20)
			.build();
		this.addRenderableWidget(this.minehouse$play);

		int row2 = top + 24;
		singleplayer.setPosition(left, row2);
		singleplayer.setWidth(98);
		multiplayer.setPosition(left + 102, row2);
		multiplayer.setWidth(98);

		this.removeWidget(realms);
		int row3 = realmsY;
		List<Button> links = new ArrayList<>();
		links.add(Button.builder(Component.literal("Сайт"), ConfirmLinkScreen.confirmLink(this, URI.create(MenuConfig.siteUrl), true)).build());
		if (!MenuConfig.discordUrl.isEmpty()) {
			links.add(Button.builder(Component.literal("Discord"), ConfirmLinkScreen.confirmLink(this, URI.create(MenuConfig.discordUrl), true)).build());
		}
		Intro.Meta intro = Intro.meta();
		if (intro != null) {
			links.add(Button.builder(Component.literal("Интро"), b -> this.minecraft.gui.setScreen(new IntroScreen(this, intro, true))).build());
		}
		if (PackUpdater.managed()) {
			links.add(Button.builder(Component.literal("Моды"), b -> this.minecraft.gui.setScreen(new PackModsScreen(this)))
				.tooltip(Tooltip.create(Component.literal("Какие моды сборки включены")))
				.build());
		}
		int w = (200 - 4 * (links.size() - 1)) / links.size();
		for (int i = 0; i < links.size(); i++) {
			Button b = links.get(i);
			b.setPosition(left + i * (w + 4), row3);
			b.setWidth(w);
			this.addRenderableWidget(b);
		}

		ServerStatus.refresh(this.minecraft);
	}

	private Button minehouse$find(String key) {
		for (GuiEventListener child : this.children()) {
			if (child instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
				return b;
			}
		}
		return null;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void minehouse$tick(CallbackInfo ci) {
		ServerStatus.tick();
		ServerStatus.refresh(this.minecraft);
		if (this.minehouse$play != null) {
			this.minehouse$play.setMessage(ServerStatus.playLabel());
			this.minehouse$play.setTooltip(Tooltip.create(ServerStatus.line()));
		}
		SelfTest.onTitleTick(this.minecraft, this.minehouse$play != null || this.minehouse$dotY >= 0);
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void minehouse$blackBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (this.minehouse$dotY >= 0) {
			graphics.fill(0, 0, this.width, this.height, 0xFF000000);
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void minehouse$status(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (this.fading || this.minehouse$dotY >= 0) {
			return;
		}
		String version = PackUpdater.localVersion().isEmpty() ? MenuConfig.packVersion : PackUpdater.localVersion();
		String pack = "Сборка MineHouse" + (version.isEmpty() ? "" : " " + version);
		graphics.text(this.font, pack, 2, 2, ARGB.color(255, ACCENT));
		String update = switch (PackUpdater.phase()) {
			case CHECKING -> "Проверяю обновления…";
			case DOWNLOADING -> "Скачиваю обновление " + PackUpdater.remoteVersion() + ": " + PackUpdater.done() + " из " + PackUpdater.total();
			case READY -> "Обновление " + PackUpdater.remoteVersion() + " скачано, перезапусти игру";
			case FAILED -> "Не удалось обновить сборку";
			default -> null;
		};
		if (update != null) {
			int color = PackUpdater.phase() == PackUpdater.Phase.READY ? 0xFFFF55 : 0xA0A0A0;
			graphics.text(this.font, update, 2, 12, ARGB.color(255, color));
		}
	}

	/** The black dot in the middle of the screen: a speck of black with a pale blue rim, its glow slowly breathing. */
	private void minehouse$dot(GuiGraphicsExtractor graphics) {
		int cx = this.width / 2;
		int cy = this.minehouse$dotY;
		float t = ChapterEnd.seconds();
		float breath = 0.55f + 0.45f * (float) Math.sin(t * 1.3);
		int glow = 56;
		graphics.blit(RenderPipelines.GUI_TEXTURED, DOT_GLOW, cx - glow / 2, cy - glow / 2, 0.0F, 0.0F, glow, glow, 128, 128, 128, 128,
			ARGB.white(breath));
		int dot = 20;
		graphics.blit(RenderPipelines.GUI_TEXTURED, DOT, cx - dot / 2, cy - dot / 2, 0.0F, 0.0F, dot, dot, 64, 64, 64, 64, ARGB.white(1f));
	}
}
