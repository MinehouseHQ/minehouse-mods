package pro.minehouse.curbfit;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

/** Extraction-thread LRU. Native queue retains the chosen model, not a temporarily edited shared model. */
final class SkinFits {
    private record Shape(boolean slim, EquipmentSlot slot) {}
    private record Entry(Object revision, SkinMask mask, Map<Shape,PlayerModel> models) {}
    private static final LinkedHashMap<Identifier,Entry> SKINS = new LinkedHashMap<>(32,.75f,true);
    static PlayerModel model(Identifier skinId, boolean slim, EquipmentSlot slot) {
        var mc=Minecraft.getInstance();
        var texture=mc.getTextureManager().getTexture(skinId);
        // A newly downloaded skin has new pixels. Resource textures get a new GPU image on F3+T.
        Object revision = texture instanceof DynamicTexture d ? d.getPixels() : texture.getTexture();
        Entry e=SKINS.get(skinId);
        if(e==null || e.revision!=revision) {
            SkinMask mask=SkinMask.EMPTY;
            if (texture instanceof DynamicTexture d) mask=SkinMask.from(d.getPixels());
            else if (texture instanceof SimpleTexture simple) {
                try (var stream=mc.getResourceManager().open(simple.resourceId()); var img=NativeImage.read(stream)) {
                    mask=SkinMask.from(img);
                } catch(IOException ignored) { /* missing/custom skin: fit to base only */ }
            }
            e=new Entry(revision,mask,new HashMap<>()); SKINS.put(skinId,e);
            if (SKINS.size()>64) SKINS.pollFirstEntry();
        }
        SkinMask mask=e.mask;
        return e.models.computeIfAbsent(new Shape(slim,slot), k -> FitMesh.bake(slot,slim,mask));
    }
    private SkinFits() {}
}
