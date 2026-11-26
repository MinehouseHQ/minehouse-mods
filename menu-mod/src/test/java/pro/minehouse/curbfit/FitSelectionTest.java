package pro.minehouse.curbfit;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.*;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FitSelectionTest {
    @BeforeAll static void init() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void onlyAdultRealPlayersAndTheirHumanoidPasses() {
        var player = new AvatarRenderState(); player.entityType=EntityTypes.PLAYER;
        assertTrue(FitSelection.playerPass(player,LayerType.HUMANOID));
        assertTrue(FitSelection.playerPass(player,LayerType.HUMANOID_LEGGINGS));
        assertFalse(FitSelection.playerPass(player,LayerType.WINGS));
        player.entityType=EntityTypes.MANNEQUIN; assertFalse(FitSelection.playerPass(player,LayerType.HUMANOID));
        assertFalse(FitSelection.playerPass(new HumanoidRenderState(),LayerType.HUMANOID));
        player.entityType=EntityTypes.PLAYER; player.isBaby=true; assertFalse(FitSelection.playerPass(player,LayerType.HUMANOID));
    }
}
