package pro.minehouse.curbfit;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;

/** Rebuild the six native player bones. Skin supplies positions, slot armor supplies UVs.
 * Outer bones contain only the fully opaque cells. Native setupAnim controls pose and visibility. */
final class FitMesh {
    static final float EPS = 1f/64;
    private record Bone(String name, String overlay) {}
    private static final List<Bone> BONES = List.of(new Bone("head","hat"), new Bone("body","jacket"),
            new Bone("left_arm","left_sleeve"), new Bone("right_arm","right_sleeve"),
            new Bone("left_leg","left_pants"), new Bone("right_leg","right_pants"));

    static PlayerModel bake(EquipmentSlot slot, boolean slim, SkinMask mask) {
        ModelPart skin = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),64,64).bakeRoot();
        MeshDefinition template = PlayerModel.createArmorMeshSet(CubeDeformation.NONE,CubeDeformation.NONE).get(slot);
        ModelPart armor = LayerDefinition.create(template,64,32).bakeRoot();
        Map<String,ModelPart> bones = new LinkedHashMap<>();
        for (Bone bone : BONES) {
            ModelPart base = skin.getChild(bone.name), outer = base.getChild(bone.overlay);
            List<Cube> baseMesh = new ArrayList<>(), outerMesh = new ArrayList<>();
            Cube uv = ownCube(armor.getChild(bone.name)); // slot filters, including waist on leggings
            if (uv != null) {
                fit(ownCube(base), uv, null, baseMesh);
                fit(ownCube(outer), uv, mask, outerMesh);
            }
            ModelPart patch = part(outer,outerMesh,Map.of());
            bones.put(bone.name,part(base,baseMesh,Map.of(bone.overlay,patch)));
        }
        return new PlayerModel(part(skin,List.of(),bones),slim);
    }
    private static ModelPart part(ModelPart source,List<Cube> cubes,Map<String,ModelPart> children) {
        var out=new ModelPart(cubes,children);
        out.setInitialPose(source.getInitialPose()); out.resetPose(); return out;
    }
    private static Cube ownCube(ModelPart part) {
        Cube[] out = new Cube[1];
        part.visit(new PoseStack(), (pose,path,index,cube)->{ if (path.isEmpty()) out[0]=cube; });
        return out[0];
    }
    private static void fit(Cube skin,Cube armor,SkinMask mask,List<Cube> out) {
        for (Polygon face : skin.polygons) {
            Polygon tex = Arrays.stream(armor.polygons).filter(f -> f.normal().equals(face.normal())).findFirst().orElseThrow();
            Vertex[] a=face.vertices(), t=new Vertex[4];
            // Match physical corners, not array indices: the left armor limb is mirrored
            // but the player's left sleeve/pants use their own unmirrored UV island.
            for(int i=0;i<4;i++) {
                float best=Float.MAX_VALUE;
                for(Vertex v:tex.vertices()) {
                    float dx=v.x()-a[i].x(), dy=v.y()-a[i].y(), dz=v.z()-a[i].z();
                    float dist=dx*dx+dy*dy+dz*dz;
                    if(dist<best) { best=dist; t[i]=v; }
                }
            }
            if(mask==null) { emit(face,t,0,0,1,1,out); continue; }
            int w=Math.round(64*(Math.abs(a[1].u()-a[0].u())+Math.abs(a[1].v()-a[0].v())));
            int h=Math.round(64*(Math.abs(a[3].u()-a[0].u())+Math.abs(a[3].v()-a[0].v())));
            // Walk in face-local coordinates; merge opaque rectangles before emitting.
            boolean[][] filled=new boolean[h][w];
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                float s=(x+.5f)/w,r=(y+.5f)/h;
                filled[y][x]=mask.opaque((int)(64*u(a,s,r)),(int)(64*v(a,s,r)));
            }
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(filled[y][x]) {
                int x1=x+1,y1=y+1;
                while(x1<w && filled[y][x1]) x1++;
                rows: while(y1<h) { for(int i=x;i<x1;i++) if(!filled[y1][i]) break rows; y1++; }
                for(int j=y;j<y1;j++) Arrays.fill(filled[j],x,x1,false);
                emit(face,t,(float)x/w,(float)y/h,(float)x1/w,(float)y1/h,out);
            }
        }
    }
    private static float mix(float a,float b,float d,float s,float r) { return a+(b-a)*s+(d-a)*r; }
    private static float u(Vertex[] t,float s,float r){return mix(t[0].u(),t[1].u(),t[3].u(),s,r);}
    private static float v(Vertex[] t,float s,float r){return mix(t[0].v(),t[1].v(),t[3].v(),s,r);}
    private static Vertex corner(Polygon p,Vertex[] tex,float s,float r) {
        var a=p.vertices()[0]; var b=p.vertices()[1]; var d=p.vertices()[3]; var n=p.normal();
        return new Vertex(mix(a.x(),b.x(),d.x(),s,r)+n.x()*EPS, mix(a.y(),b.y(),d.y(),s,r)+n.y()*EPS,
                mix(a.z(),b.z(),d.z(),s,r)+n.z()*EPS, u(tex,s,r),v(tex,s,r));
    }
    private static final Set<Direction> ONE_FACE=Set.of(Direction.NORTH);
    private static void emit(Polygon p,Vertex[] uv,float s0,float r0,float s1,float r1,List<Cube> out) {
        // Build a private one-quad Cube; native compile and outline/trim handling stay intact.
        var q=new Cube(0,0,0,0,0,1,1,1,0,0,0,false,64,32,ONE_FACE);
        q.polygons[0]=new Polygon(new Vertex[]{corner(p,uv,s0,r0),corner(p,uv,s1,r0),
                corner(p,uv,s1,r1),corner(p,uv,s0,r1)},p.normal()); out.add(q);
    }
    private FitMesh() {}
}
