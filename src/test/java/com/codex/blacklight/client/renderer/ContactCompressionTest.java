package com.codex.blacklight.client.renderer;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactCompressionTest {
    private static final Vec3 NORMAL = new Vec3(0, 0, 1);

    private static AdaptiveRagdoll.Vertex vertex(double x, double y) {
        return new AdaptiveRagdoll.Vertex(null, null, new Vec3(x, y, 0), NORMAL,
                (float)(x + .5), (float)(y + .5), -1, 0, 0);
    }

    @Test void broadFlatFaceDentsAtContactEvenWhenItsCornersAreOutsideTheStrike() {
        var face = List.of(vertex(-.5,-.5), vertex(.5,-.5), vertex(.5,.5), vertex(-.5,.5));
        var press = new ContactCompression(Vec3.ZERO, NORMAL.scale(-1), .27, 23);
        press.strength = ContactCompression.weight(1);
        for (var corner : face) assertSame(corner.p, press.point(corner.p));
        var skin = press.surface(face);
        assertTrue(skin.size() > 4);
        var center = skin.stream().filter(v -> v.p.lengthSqr() < 1e-10).findFirst().orElseThrow();
        assertEquals(.5, center.u, 1e-6);
        assertEquals(.5, center.v, 1e-6);
        assertEquals(-press.depth, press.point(center.p).z, 1e-8);
        assertEquals(0, center.p.z);
        assertEquals(4, face.size());
    }

    @Test void overlappingLayersKeepMatchingGridsWhenTheSubdivisionBudgetIsReached() {
        var quad = List.of(vertex(-.5,-.5), vertex(.5,-.5), vertex(.5,.5), vertex(-.5,.5));
        var layered = new java.util.ArrayList<AdaptiveRagdoll.Vertex>();
        for (int i = 0; i < 24; i++) layered.addAll(quad);
        var press = new ContactCompression(Vec3.ZERO, NORMAL.scale(-1), .25, 0);
        var skin = press.surface(layered);
        assertTrue(skin.size() <= layered.size() + 1536);
        int perLayer = skin.size() / 24;
        assertTrue(perLayer > 4);
        for (int i = perLayer; i < skin.size(); i++)
            assertEquals(skin.get(i % perLayer).p, skin.get(i).p);
        assertEquals(0xff808080, press.shade(0, 0xff808080));
        assertEquals(0x80, press.shade(press.depth, 0x80808080) >>> 24);
    }

    @Test void tiltedNormalsFollowTheDentedSurfaceAsItRecoils() {
        var press = new ContactCompression(Vec3.ZERO, NORMAL.scale(-1), .35, 23);
        var p = new Vec3(.12,.08,0);
        press.strength = ContactCompression.weight(1);
        var normal = press.normal(p,NORMAL);
        double h = .0005;
        var dx = press.point(p.add(h,0,0)).subtract(press.point(p.add(-h,0,0)));
        var dy = press.point(p.add(0,h,0)).subtract(press.point(p.add(0,-h,0)));
        assertTrue(normal.dot(dx.normalize().cross(dy.normalize()).normalize()) > .99999);
        double peak = press.point(p).distanceTo(p);
        for (double tick : new double[]{2,3,4,5,6}) {
            press.strength = ContactCompression.weight(tick);
            double next = press.point(p).distanceTo(p);
            assertTrue(next < peak);
            peak = next;
        }
        assertEquals(.24, press.strength, 1e-8);
        assertSame(NORMAL, press.normal(new Vec3(1,0,0),NORMAL));
    }
}