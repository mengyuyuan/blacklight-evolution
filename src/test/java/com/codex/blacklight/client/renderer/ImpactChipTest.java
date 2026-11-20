package com.codex.blacklight.client.renderer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;

class ImpactChipTest {
    private static final Vec3 IN = new Vec3(0, 0, -1);
    private static List<Vertex> face(double z) {
        List<Vertex> mesh = new ArrayList<>();
        for (Vec3 p : List.of(new Vec3(-.5,-.5,z),new Vec3(.5,-.5,z),new Vec3(.5,.5,z),new Vec3(-.5,.5,z)))
            mesh.add(new Vertex(null,null,p,IN.scale(-1),(float)(p.x+.5),(float)(p.y+.5),-1,0,0));
        return mesh;
    }
    private static double texturedArea(List<Vertex> mesh) {
        double sum = 0;
        for (int i=0; i<mesh.size(); i+=4) {
            if (mesh.get(i).color != -1) continue;
            Vec3 p=mesh.get(i).p, a=mesh.get(i+1).p.subtract(p), b=mesh.get(i+2).p.subtract(p), c=mesh.get(i+3).p.subtract(p);
            sum += .5 * (a.cross(b).length() + b.cross(c).length());
        }
        return sum;
    }

    @Test void eachBlowDetachesMoreTexturedChipsAndLeavesAnIndentedWound() {
        for (int n=1; n<=4; n++) {
            var mesh=face(0);
            var cut=ImpactChip.carve(mesh, new Vec3(.44,.02,0),IN,.18,n,null);
            assertEquals(n,cut.chips().size());
            assertTrue(cut.cut());
            assertTrue(cut.skin().stream().anyMatch(v -> v.p.z < -.03 && v.color != -1));
            double loose=cut.chips().stream().mapToDouble(ImpactChipTest::texturedArea).sum();
            assertTrue(loose>0);
            assertEquals(texturedArea(mesh),texturedArea(cut.skin())+loose,1e-8);
            assertTrue(cut.skin().contains(mesh.get(0)));
        }
    }
    @Test void farSideAndMissesStayIntactAndClothingDoesNotEmitDuplicateChips() {
        var mesh=face(-.5);
        var miss=ImpactChip.carve(mesh, Vec3.ZERO,IN,.2,4,null);
        assertSame(mesh,miss.skin());
        assertFalse(miss.cut());
        assertTrue(miss.chips().isEmpty());
        var overlay=ImpactChip.carve(face(0),Vec3.ZERO,IN,.2,0,null);
        assertTrue(overlay.cut());
        assertTrue(overlay.chips().isEmpty());
    }
}
