package pro.minehouse.curbfit;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Resolve and transform the target in a real client-side Fabric class loader, without starting a window. */
public final class LoaderSmoke {
    public static void main(String[] args) throws Exception {
        var knot = new Knot(EnvType.CLIENT);
        var cl = knot.init(new String[]{"--gameDir", ".", "--version", "26.3"});
        var target = Class.forName("net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer", false, cl);
        for (var m:target.getDeclaredMethods()) if(m.getName().contains("curbfit$model")) {
            System.out.println("PASS: packaged Fabric mixin merged into " + target.getName()); return;
        }
        throw new AssertionError("Packaged mixin did not merge");
    }
}
