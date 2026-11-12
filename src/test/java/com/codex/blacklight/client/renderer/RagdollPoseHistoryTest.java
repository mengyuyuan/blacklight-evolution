package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RagdollPoseHistoryTest {
    private static BladeSpan blade(double time,double z,Vec3 origin){
        return new BladeSpan(List.of(new Vec3(-1,0,z),new Vec3(1,0,z)),time,origin,180);
    }
    @Test void oldBodyLocationCannotBeCutAfterTheBodyHasMoved(){
        var h=new RagdollPoseHistory();
        h.record(0,Vec3.ZERO,new Quaternionf(),1);
        h.record(1,new Vec3(4,0,0),new Quaternionf(),1);
        h.record(2,new Vec3(4,0,0),new Quaternionf(),1);
        var old=new SweptBladeSurface(h.localPath(List.of(blade(1,-1,Vec3.ZERO),blade(2,1,Vec3.ZERO)),Vec3.ZERO));
        var actual=new SweptBladeSurface(h.localPath(List.of(blade(1,-1,new Vec3(4,0,0)),blade(2,1,new Vec3(4,0,0))),Vec3.ZERO));
        assertFalse(old.covers(Vec3.ZERO));assertTrue(actual.covers(Vec3.ZERO));
    }
    @Test void stillBladeCanCutAMovingBodyAndMustKeepBothTimedSamples(){
        var h=new RagdollPoseHistory();
        h.record(10,new Vec3(0,0,-1),new Quaternionf(),1);
        h.record(11,new Vec3(0,0,1),new Quaternionf(),1);
        var a=blade(10,0,Vec3.ZERO);var b=blade(11,0,Vec3.ZERO);
        assertFalse(a.near(b));
        var surface=new SweptBladeSurface(h.localPath(List.of(a,b),Vec3.ZERO));
        assertFalse(surface.empty());assertTrue(surface.covers(Vec3.ZERO));
    }
    @Test void interpolationIncludesRotationAndScaleAndSnapshotsOwnTheirQuaternion(){
        var h=new RagdollPoseHistory();var turn=new Quaternionf().rotationZ((float)Math.PI/2);
        h.record(10,Vec3.ZERO,new Quaternionf(),1);h.record(12,new Vec3(4,0,0),turn,.5F);turn.identity();
        var at=h.sample(11);assertEquals(.75F,at.scale());
        double d=Math.sqrt(.5)*.75;
        assertEquals(0,at.toBind(new Vec3(2+d,d,0),Vec3.ZERO).distanceTo(new Vec3(1,0,0)),1e-6);
    }
    @Test void childPiecesInheritTheParentsHistoricalRigidTransform(){
        var h=new RagdollPoseHistory();var child=new RagdollPoseHistory();
        h.record(10,new Vec3(3,4,5),new Quaternionf().rotationZ((float)Math.PI/2),.5F);
        child.inherit(h,new Vec3(2,0,0));
        var world=new Vec3(2.5,5,5);
        assertEquals(0,h.sample(10).toBind(world,Vec3.ZERO).distanceTo(child.sample(10).toBind(world,new Vec3(2,0,0))),1e-6);
    }
    @Test void historyIsBoundedAndNeverExtrapolatesFutureMotion(){
        var h=new RagdollPoseHistory();for(int i=0;i<120;i++)h.record(i,new Vec3(i,0,0),new Quaternionf(),1);
        assertEquals(24,h.sample(-10).position().x);assertEquals(119,h.sample(999).position().x);
        assertThrows(IllegalArgumentException.class,()->h.record(118,Vec3.ZERO,new Quaternionf(),1));
    }
    @Test void receiverFrameDoesNotLoseDistantWorldPrecision(){
        var h=new RagdollPoseHistory();var origin=new Vec3(29_000_000.125,251,29_000_000.25);
        h.record(10,origin,new Quaternionf(),1);h.record(11,origin,new Quaternionf(),1);
        var a=new BladeSpan(List.of(new Vec3(-1,0,-1),new Vec3(1,0,-1)),10,origin,90);
        var b=new BladeSpan(List.of(new Vec3(-1,0,1),new Vec3(1,0,1)),11,origin,90);
        var surface=new SweptBladeSurface(h.localPath(List.of(a,b),Vec3.ZERO));
        assertTrue(surface.covers(Vec3.ZERO));assertFalse(surface.covers(new Vec3(2,0,0)));
    }
}
