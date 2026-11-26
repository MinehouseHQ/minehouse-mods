package pro.minehouse.curbfit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.EquipmentClientInfo.Layer;
import net.minecraft.resources.Identifier;

/** Match the active slot definition, not the presence of a pack somewhere in the stack. */
public final class Style {
    private static final Map<Identifier,Identifier> FINISH = new HashMap<>();
    private static final Identifier LEATHER = curb("leather"), HARDWARE = LEATHER.withSuffix("_hardware");
    static {
        for (String material : List.of("gold", "diamond", "netherite", "iron", "leather")) {
            Identifier curb = curb(material);
            FINISH.put(Identifier.withDefaultNamespace(material), curb);
            FINISH.put(curb, curb);
        }
    }
    private static Identifier curb(String material) {
        return Identifier.fromNamespaceAndPath("minehouse-cosmetics", "armor_" + material + "_curb");
    }
    public static boolean matches(Identifier key, List<Layer> active) {
        Identifier expected = FINISH.get(key);
        if (expected == null) return false;
        boolean found = false;
        for (Layer layer : active) {
            if (layer.usePlayerTexture()) return false;
            if (expected.equals(layer.textureId())) found = true;
            else if (!(expected.equals(LEATHER) && HARDWARE.equals(layer.textureId()))) return false;
        }
        return found;
    }
    private Style() {}
}
