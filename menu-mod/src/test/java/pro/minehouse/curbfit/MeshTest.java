package pro.minehouse.curbfit;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MeshTest {
    @BeforeAll static void init() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    static List<Polygon> front(ModelPart p) {
        List<Polygon> out = new ArrayList<>();
        p.visit(new PoseStack(), (pose,path,index,cube)-> {
            if (path.isEmpty()) for (var f : cube.polygons) if(f.normal().z()==-1) out.add(f);
        });
        return out;
    }
    @Test void aSingleOpaqueJacketPixelLiftsOnlyItsOwnJewelryPatch() {
        try (var skin = new NativeImage(64,64,true)) {
            skin.setPixel(22,39,0xff112233); // front jacket: x=20..27, y=36..47
            skin.setPixel(23,39,0x80112233); // translucent is not opaque support
            var m = FitMesh.bake(EquipmentSlot.CHEST, true, SkinMask.from(skin));
            var base = front(m.body); var raised = front(m.jacket);
            assertEquals(1,base.size()); assertEquals(1,raised.size());
            for(var v:base.getFirst().vertices()) assertEquals(-2-1/64f,v.z());
            for(var v:raised.getFirst().vertices()) {
                assertEquals(-2.25f-1/64f,v.z());
                // The exact jacket cell stays opaque, but its jewelry UV is measured
                // against the underlying body, not stretched to the jacket's grid.
                assertTrue(Math.abs(v.x()+2.125f)<1e-5 || Math.abs(v.x()+1.0625f)<1e-5);
                assertTrue(Math.abs(v.y()-2.875f)<1e-5 || Math.abs(v.y()-47f/12f)<1e-5);
                assertEquals((24+v.x())/64f,v.u(),1e-5);
                assertEquals((20+v.y())/32f,v.v(),1e-5);
            }
            assertTrue(front(m.leftLeg).isEmpty());
            AvatarRenderState a = new AvatarRenderState(); a.showJacket=false; m.setupAnim(a);
            assertFalse(m.jacket.visible); a.showJacket=true; m.setupAnim(a); assertTrue(m.jacket.visible);
        }
    }
    @Test void chestFrontKeepsTheSameTexelScaleOnBaseAndJacket() {
        try (var skin = new NativeImage(64,64,true)) {
            for(int y=36;y<48;y++) for(int x=20;x<28;x++) skin.setPixel(x,y,-1);
            for (boolean slim : List.of(false,true)) {
                var m = FitMesh.bake(EquipmentSlot.CHEST, slim, SkinMask.from(skin));
                var base = front(m.body).getFirst().vertices();
                var jacket = front(m.jacket).getFirst().vertices();
                for (int i=0; i<4; i++) {
                    assertEquals(base[i].x(),jacket[i].x(),1e-5,"same U location in world space");
                    assertEquals(base[i].y(),jacket[i].y(),1e-5,"same V location in world space");
                    assertEquals(base[i].u(),jacket[i].u(),1e-5);
                    assertEquals(base[i].v(),jacket[i].v(),1e-5);
                    assertEquals(-0.25f,jacket[i].z()-base[i].z(),1e-5,"only the depth changes");
                }
            }
        }
    }
    @Test void slotsAndSkinDistancesOnWideAndSlim() {
        try(var skin = new NativeImage(64,64,true)) {
            for(int y=0;y<64;y++) for(int x=0;x<64;x++) skin.setPixel(x,y,-1);
            var mask=SkinMask.from(skin);
            for(boolean slim:List.of(true,false)) for(var s:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
                var m=FitMesh.bake(s,slim,mask);
                var base=s==EquipmentSlot.HEAD?m.head:s==EquipmentSlot.FEET?m.leftLeg:m.body;
                var overlay=s==EquipmentSlot.HEAD?m.hat:s==EquipmentSlot.FEET?m.leftPants:m.jacket;
                float z=s==EquipmentSlot.HEAD?-4:-2, shell=s==EquipmentSlot.HEAD?.5f:.25f;
                for(var p:List.of(base,overlay)) {
                    var polys=front(p); assertEquals(1,polys.size());
                    for(var v:polys.getFirst().vertices()) assertEquals(z-(p==overlay?shell:0)-1/64f,v.z());
                }
                if(s==EquipmentSlot.CHEST) {
                    var f=front(m.rightArm).getFirst();
                    float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
                    for(var v:f.vertices()){min=Math.min(min,v.x());max=Math.max(max,v.x());}
                    assertEquals(slim?3:4,max-min,0.04); // native skin arm, not wide armor
                }
            }
        }
    }
}
