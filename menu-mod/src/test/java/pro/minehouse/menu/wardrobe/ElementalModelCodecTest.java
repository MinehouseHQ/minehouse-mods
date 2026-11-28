package pro.minehouse.menu.wardrobe;

import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ElementalModelCodecTest {
    @Test
    void allLimbAndStepAssetsLoadWithTheNativeCodecs() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemModels.bootstrap();
        var ops = new PlaceholderLookupProvider(ClientRegistryLayer.createRegistryAccess().compositeAccess())
                .createSerializationContext(JsonOps.INSTANCE);
        for (String theme : List.of("ember", "rime", "end")) {
            for (String part : List.of("right_arm", "left_arm", "right_arm_wide", "left_arm_wide", "right_leg", "left_leg", "step")) {
                for (int frame = 0; frame < (part.equals("step") ? 37 : 96); frame++) {
                    String path = "elemental/" + theme + "/" + part + "/f" + frame + ".json";
                    try (var item = resource("items/" + path); var mesh = resource("models/" + path)) {
                        assertNotNull(ClientItem.CODEC.parse(ops, StrictJsonParser.parse(item)).getOrThrow(), path);
                        assertNotNull(CuboidModel.fromStream(mesh), path);
                    } catch (Exception e) {
                        throw new AssertionError(path, e);
                    }
                }
            }
        }
    }

    private static InputStreamReader resource(String path) {
        var stream = ElementalModelCodecTest.class.getResourceAsStream("/assets/minehouse-menu/" + path);
        assertNotNull(stream, path);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
