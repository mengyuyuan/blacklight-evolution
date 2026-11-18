package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoliathSlamTest {
    @Test void nativeShortHitKeepsItsOffsetsRadiiAndStrongestFirstDamage(){
        assertTrue(GoliathSlam.spheres(37,Vec3.ZERO,0).isEmpty());
        assertTrue(GoliathSlam.spheres(39,Vec3.ZERO,0).isEmpty());
        var spheres=GoliathSlam.spheres(38,Vec3.ZERO,0);assertEquals(2,spheres.size());
        assertEquals(1,spheres.get(0).damageScale());assertEquals(400F/750F,spheres.get(1).damageScale());
        assertEquals(2.6469351102433007,spheres.get(0).radius(),.000001);
        assertEquals(5.2938702204866015,spheres.get(1).radius(),.000001);
        assertTrue(spheres.get(0).origin().distanceTo(new Vec3(.756267174355229,1.512534348710458,.756267174355229))<.000001);
        for(float yaw:new float[]{90,-137}){
            var root=new Vec3(11,7,-9);var rotated=GoliathSlam.spheres(38,root,yaw);
            assertTrue(rotated.get(0).origin().distanceTo(spheres.get(0).origin().yRot((float)Math.toRadians(-yaw)).add(root))<.000001);
        }
    }
    @Test void articulatedSourceHandCanReceiveContactWhileItsRootColumnIsOutsideTheAttack(){
        var spheres=GoliathSlam.spheres(38,Vec3.ZERO,0);var inner=spheres.get(0);
        var pose=GoliathMotion.attack(EncounterMove.HAMMER,38);
        var local=GoliathContact.regions(pose,Vec3.ZERO,0,0).stream().filter(r->r.bone().equals("right_hand")).findFirst().orElseThrow().boxes().get(0);
        var root=inner.origin().subtract(local.center);
        var hand=GoliathContact.regions(pose,root,0,0).stream().filter(r->r.bone().equals("right_hand")).findFirst().orElseThrow().boxes().get(0);
        double half=EncounterKind.GOLIATH.width/2;
        var column=new AABB(root.x-half,root.y,root.z-half,root.x+half,root.y+EncounterKind.GOLIATH.height,root.z+half);
        assertFalse(GoliathSlam.bounds(spheres).intersects(column),"actual source giant wrist reaches beyond the parent's root enumeration");
        assertFalse(GoliathSlam.canReach(Vec3.ZERO,0,column));
        var contact=GoliathSlam.contact(inner,"right_hand",hand);assertNotNull(contact);
        assertEquals("right_hand",contact.bone());assertEquals(1,contact.damageScale());
        assertTrue(hand.contains(contact.point()));assertEquals(1,contact.normal().length(),.000001);
    }
    @Test void emptyParentSpaceAndOuterOnlySurfacesDoNotBecomeAnInnerCircleHit(){
        var spheres=GoliathSlam.spheres(38,Vec3.ZERO,0);var inner=spheres.get(0);var outer=spheres.get(1);
        var center=inner.origin().add(4,0,0);var box=new AABB(center.add(-.1,-.1,-.1),center.add(.1,.1,.1));
        assertNull(GoliathSlam.contact(inner,"limb",box));
        var contact=GoliathSlam.contact(outer,"limb",box);assertNotNull(contact);assertEquals(400F/750F,contact.damageScale());
        assertEquals("limb",contact.bone());assertEquals(box.minX,contact.point().x,.000001);
        var away=box.move(20,0,0);
        assertTrue(new AABB(inner.origin(),inner.origin()).inflate(1).contains(inner.origin()),"a root query may contain the center");
        assertNull(GoliathSlam.contact(outer,"limb",away),"that empty parent area cannot replace the actual receiving surface");
        assertNull(GoliathSlam.bounds(java.util.List.of()));
    }
}
