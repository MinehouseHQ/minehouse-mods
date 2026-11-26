package pro.minehouse.curbfit;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.model.EquipmentClientInfo.Layer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StyleTest {
    static final String NS = "minehouse-cosmetics:armor_";
    static boolean matches(String key, String... layers) {
        List<Layer> active = Arrays.stream(layers).map(Identifier::parse).map(Layer::new).toList();
        return Style.matches(Identifier.parse(key), active);
    }
    @Test void ordinaryResourcesAreNeverEnough() {
        for (String m : List.of("iron", "gold", "leather", "diamond", "netherite")) {
            String key = "minecraft:" + m, curb = NS + m + "_curb";
            assertFalse(matches(key, key));
            assertTrue(matches(key, curb)); assertTrue(matches(curb, curb));
            assertFalse(matches(key, "other:armor_" + m + "_curb"));
            assertFalse(matches(key, curb, key));
            assertFalse(matches(key, NS + "copper_curb"));
            assertFalse(matches(key));
        }
        assertTrue(matches("minecraft:leather", NS+"leather_curb", NS+"leather_curb_hardware"));
        assertFalse(matches("minecraft:gold", NS+"diamond_curb"));
        assertFalse(matches("minecraft:leather", NS+"leather_curb_hardware"));
    }
}
