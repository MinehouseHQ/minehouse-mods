package pro.minehouse.menu.mixin;

import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pro.minehouse.menu.skin.SkinPartState;

@Mixin(ItemDisplayEntityRenderState.class)
public abstract class SkinPartStateMixin implements SkinPartState {
	@Unique
	private int minehouse$part = -1;
	@Unique
	private ResolvableProfile minehouse$profile;

	@Override
	public int minehouse$part() {
		return minehouse$part;
	}

	@Override
	public ResolvableProfile minehouse$profile() {
		return minehouse$profile;
	}

	@Override
	public void minehouse$set(int part, ResolvableProfile profile) {
		minehouse$part = part;
		minehouse$profile = profile;
	}
}
