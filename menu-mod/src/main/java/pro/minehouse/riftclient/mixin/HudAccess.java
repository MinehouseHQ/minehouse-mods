package pro.minehouse.riftclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;

@Mixin(Hud.class)
public interface HudAccess {
	@Accessor("overlayMessageString")
	Component minehouse$actionBar();
}
