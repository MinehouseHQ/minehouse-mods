package pro.minehouse.menu.skin;

import net.minecraft.world.item.component.ResolvableProfile;

/** Added to item display render states: which skin part the display is (see SkinParts). */
public interface SkinPartState {
	int minehouse$part();

	ResolvableProfile minehouse$profile();

	void minehouse$set(int part, ResolvableProfile profile);
}
