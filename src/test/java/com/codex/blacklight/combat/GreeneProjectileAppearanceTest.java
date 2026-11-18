package com.codex.blacklight.combat;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-template contracts; deferred with all other player validation. */
class GreeneProjectileAppearanceTest {
    private static final UUID SEED=UUID.fromString("1435ae7c-2be0-4707-8421-027eaed33c17");
    @Test void actualGunTemplatesKeepDistinctSourceSizesAndFinalTimes(){
        var flesh=GreeneProjectileAppearance.of(GreeneMissileFlight.Ammo.DEATH_FLOWER);
        var rifle=GreeneProjectileAppearance.of(GreeneMissileFlight.Ammo.RIFLE);
        var anti=GreeneProjectileAppearance.of(GreeneMissileFlight.Ammo.ANTI_TANK);
        assertSame(flesh,GreeneProjectileAppearance.of(GreeneMissileFlight.Ammo.INSECT_SWARM));
        assertSame(rifle,GreeneProjectileAppearance.of(GreeneMissileFlight.Ammo.SHOTGUN));
        assertSame(anti,GreeneProjectileAppearance.of(GreeneMissileFlight.Ammo.ANTI_AIR));
        assertEquals("Projectile_VomitMissile",flesh.template());assertEquals("Projectile_VomitMissile2",rifle.template());assertEquals("Projectile_VomitMissile3",anti.template());
        assertEquals(.93970665,flesh.sourceBounds().getYsize(),1e-6);
        assertEquals(1.3F,rifle.scale());assertEquals(1.5F,anti.scale());
        assertEquals(.3F,flesh.finalSeconds());assertEquals(.266667F,rifle.finalSeconds());
        assertEquals(6,flesh.finalTicks());assertEquals(6,rifle.finalTicks());
    }
    @Test void playbackTurnsMeshesOffAtTheirExactSourceStage(){
        for(var ammo:GreeneMissileFlight.Ammo.values()){
            boolean flightMesh=ammo!=GreeneMissileFlight.Ammo.DEATH_FLOWER&&ammo!=GreeneMissileFlight.Ammo.INSECT_SWARM;
            assertEquals(flightMesh,GreeneProjectileAppearance.meshVisible(ammo,false));
            assertFalse(GreeneProjectileAppearance.meshVisible(ammo,true),"final keeps FX but switches the primitive Off");
        }
    }
    @Test void sourceSpinIsWorldAxisMotionIndependentOfHeadingAndReturnsAfterItsPeriod(){
        var ammo=GreeneMissileFlight.Ammo.RIFLE;
        var start=GreeneProjectileAppearance.orientation(ammo,SEED,new Vec3(0,0,-1),0);
        var changedHeading=GreeneProjectileAppearance.orientation(ammo,SEED,new Vec3(1,0,0),0);
        assertEquals(1,Math.abs(start.dot(changedHeading)),1e-6,"randomOrient overrides launch facing");
        var period=GreeneProjectileAppearance.orientation(ammo,SEED,Vec3.ZERO,(float)(2*Math.PI/Math.sqrt(200)));
        assertEquals(1,Math.abs(start.dot(period)),1e-6);
        float seconds=.05F;var moved=GreeneProjectileAppearance.orientation(ammo,SEED,Vec3.ZERO,seconds);
        var relative=new org.joml.Quaternionf(moved).mul(new org.joml.Quaternionf(start).conjugate());
        var axis=new Vector3f(-1,0,1).normalize();var fixed=relative.transform(new Vector3f(axis));
        assertTrue(fixed.distance(axis)<1e-6,"world X/Z axis remains fixed during the spin");
        var up=relative.transform(new Vector3f(0,1,0));
        assertTrue(up.x>0&&up.z>0,"107be440 negates the angular increment");
    }
    @Test void nonSpinningGibAlignsSourceNegativeZWithoutChangingScale(){
        var heading=new Vec3(.3,.8,-.6).normalize();
        var rotation=GreeneProjectileAppearance.orientation(GreeneMissileFlight.Ammo.INSECT_SWARM,SEED,heading,2);
        var forward=rotation.transform(new Vector3f(0,0,-1));
        assertEquals(heading.x,forward.x,1e-6);assertEquals(heading.y,forward.y,1e-6);assertEquals(heading.z,forward.z,1e-6);
        assertEquals(1,forward.length(),1e-6);
    }
}
