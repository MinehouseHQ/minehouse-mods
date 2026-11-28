package pro.minehouse.menu.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import pro.minehouse.menu.MinehouseMenu;

/** Optional Punchy public API integration. No dependency on its renderer internals. */
final class PunchyHandCompat {
	private static final String API = "punchy.api.PunchyApi";
	private static final String ID = "minehouse-client";

	private PunchyHandCompat() {}

	static void init() {
		if (!FabricLoader.getInstance().isModLoaded("punchy")) return;
		try {
			Class<?> api = Class.forName(API);
			if (!Boolean.TRUE.equals(api.getMethod("supports", String.class).invoke(null, "draw_on_arm"))) return;
			Class<?> event = Class.forName(API + "$ArmDraw");
			Class<?> handler = Class.forName(API + "$ArmDrawHandler");
			var draw = new DrawAccess(getter(event, "player", Object.class), getter(event, "leftArm", boolean.class),
					getter(event, "slim", boolean.class), getter(event, "armPart", Object.class),
					getter(event, "poseStack", Object.class), getter(event, "sink", Object.class),
					getter(event, "light", int.class), getter(event, "overlay", int.class));
			Object listener = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler}, (proxy, method, args) -> {
				return switch (method.getName()) {
					case "draw" -> { draw.submit(args[0]); yield null; }
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> "MineHouse elemental hand layer";
					default -> throw new UnsupportedOperationException(method.getName());
				};
			});
			api.getMethod("drawOnArm", String.class, handler).invoke(null, ID, listener);
			MinehouseMenu.LOG.info("Enabled Punchy hand overlays for elemental wardrobe items");
		} catch (ReflectiveOperationException | LinkageError e) {
			MinehouseMenu.LOG.warn("Could not enable optional Punchy hand overlays", e);
		}
	}

	private static MethodHandle getter(Class<?> event, String name, Class<?> type) throws ReflectiveOperationException {
		return MethodHandles.publicLookup().findVirtual(event, name, MethodType.methodType(type))
				.asType(MethodType.methodType(type, Object.class));
	}

	private record DrawAccess(MethodHandle player, MethodHandle left, MethodHandle slim, MethodHandle part,
			MethodHandle pose, MethodHandle sink, MethodHandle light, MethodHandle overlay) {
		void submit(Object event) throws Throwable {
			if (FirstPersonElementals.equipped(WardrobeNet.state) == null) return;
			if (player.invokeExact(event) instanceof LocalPlayer wearer && part.invokeExact(event) instanceof ModelPart armPart
					&& pose.invokeExact(event) instanceof PoseStack matrices && sink.invokeExact(event) instanceof SubmitNodeCollector collector) {
				FirstPersonElementals.draw(Minecraft.getInstance(), wearer,
						(boolean) left.invokeExact(event) ? HumanoidArm.LEFT : HumanoidArm.RIGHT,
						(boolean) slim.invokeExact(event) ? PlayerModelType.SLIM : PlayerModelType.WIDE,
						armPart, matrices, collector, (int) light.invokeExact(event), (int) overlay.invokeExact(event));
			}
		}
	}
}
