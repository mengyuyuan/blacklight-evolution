package com.codex.blacklight.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneEmergenceShapeTest {
    @Test void largeSphereFollowsActualRootAndConvertsUnitsOnce(){
        var root=new Vec3(10,-8,20);double scale=GreeneMotion.table().sourceUnitBlocks();
        var samples=GreeneEmergenceShape.samples(GreeneEmergenceClock.Track.R20,root.add(0,-1,0),root,73,scale);
        assertEquals(1,samples.size());assertEquals(root,samples.get(0).center());
        assertEquals(20*scale,samples.get(0).radius(),1e-12);
        // A surface target can touch the sphere while its source is underground.
        assertNotNull(GreeneEmergenceShape.contact(samples.get(0),new AABB(9.9,0,19.9,10.1,1,20.1)));
        assertNull(GreeneEmergenceShape.contact(samples.get(0),new AABB(20,0,20,21,1,21)));
    }
    @Test void smallSphereUsesDiscreteRootSamplesRatherThanAnAnalyticCapsule(){
        // Radius3 * 1/3 =>1. Source yaw180 maps to identity root rotation.
        var samples=GreeneEmergenceShape.samples(GreeneEmergenceClock.Track.R3,Vec3.ZERO,new Vec3(3,0,0),180,1D/3);
        assertEquals(3,samples.size());assertEquals(new Vec3(1,0,0),samples.get(0).center());
        var missedBetweenSamples=new AABB(1.499,.989,-.001,1.501,.991,.001);
        assertNotNull(ProjectileSphereContact.sweep(Vec3.ZERO,new Vec3(3,0,0),1,missedBetweenSamples));
        assertTrue(samples.stream().allMatch(s->GreeneEmergenceShape.contact(s,missedBetweenSamples)==null));
    }
    @Test void identityPreviousRotationAffectsCountButNeverRadius(){
        var root=new Vec3(0,-4,0);
        var identity=GreeneEmergenceShape.samples(GreeneEmergenceClock.Track.R3,root,root,180,1);
        var halfTurn=GreeneEmergenceShape.samples(GreeneEmergenceClock.Track.R3,root,root,0,1);
        assertEquals(1,identity.size());assertEquals(4,halfTurn.size());
        assertTrue(halfTurn.stream().allMatch(s->s.center().equals(root)&&s.radius()==3));
    }
    @Test void tangentSurvivesBroadphaseButOutsideRadiusDoesNotHit(){
        var samples=GreeneEmergenceShape.samples(GreeneEmergenceClock.Track.R20,Vec3.ZERO,Vec3.ZERO,0,.5);
        var tangent=new AABB(10,0,0,11,1,1);
        assertTrue(GreeneEmergenceShape.bounds(samples).intersects(tangent));
        assertNotNull(GreeneEmergenceShape.contact(samples.get(0),tangent));
        assertNull(GreeneEmergenceShape.contact(samples.get(0),tangent.move(.001,0,0)));
    }
}
