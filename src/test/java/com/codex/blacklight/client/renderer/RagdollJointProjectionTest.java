package com.codex.blacklight.client.renderer;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollJointProjectionTest {
    @Test void collisionAndInterpolationCannotPullAnUncutSkeletonApart(){
        var links=List.of(new RagdollJointProjection.Link(1,2,new Vec3(0,-.6,0),new Vec3(0,.5,0)),
                new RagdollJointProjection.Link(0,1,new Vec3(.4,0,0),new Vec3(0,.5,0)));
        for(int frame=0;frame<=120;frame++){
            float t=frame/120F;
            var poses=List.of(new RagdollJointProjection.Pose(new Vec3(0,1,0),new Quaternionf().rotationZ(t),1),
                    new RagdollJointProjection.Pose(new Vec3(4,-1,0),new Quaternionf().rotationX(t*2),1),
                    new RagdollJointProjection.Pose(new Vec3(-3,2,5),new Quaternionf().rotationY(-t),.8F));
            var result=RagdollJointProjection.close(poses,links);
            result.lift(new double[]{0,.3,.7});
            for(var l:links){
                var a=result.positions()[l.parent()].add(new Vec3(poses.get(l.parent()).rotation().transform(l.parentAnchor().scale(poses.get(l.parent()).scale()).toVector3f())));
                var b=result.positions()[l.child()].add(new Vec3(poses.get(l.child()).rotation().transform(l.childAnchor().scale(poses.get(l.child()).scale()).toVector3f())));
                assertEquals(0,a.distanceTo(b),1e-6,"frame "+frame);
            }
            assertArrayEquals(new int[]{0,0,0},result.components());
            assertEquals(1.7,result.positions()[0].y,1e-6);
        }
    }
    @Test void CutSeversOnlyItsOwnConnection(){
        var poses=List.of(new RagdollJointProjection.Pose(Vec3.ZERO,new Quaternionf(),1),
                new RagdollJointProjection.Pose(new Vec3(5,2,0),new Quaternionf(),1),
                new RagdollJointProjection.Pose(new Vec3(6,2,0),new Quaternionf(),1));
        var result=RagdollJointProjection.close(poses,List.of(new RagdollJointProjection.Link(1,2,new Vec3(1,0,0),Vec3.ZERO)));
        result.lift(new double[]{.5,0,0});
        assertEquals(new Vec3(0,.5,0),result.positions()[0]);
        assertEquals(poses.get(1).position(),result.positions()[1]);
        assertEquals(poses.get(2).position(),result.positions()[2]);
        assertArrayEquals(new int[]{0,1,1},result.components());
    }
    @Test void sharpContactUsesTheSkinAfterJointClosureAndSupportLift(){
        var rotation=new Quaternionf().rotationY(.35F);
        var raw=List.of(new RagdollJointProjection.Pose(new Vec3(0,1,0),new Quaternionf(),1),
                new RagdollJointProjection.Pose(new Vec3(5,-.5,3),rotation,1));
        var joined=RagdollJointProjection.close(raw,List.of(new RagdollJointProjection.Link(0,1,new Vec3(0,-.5,0),new Vec3(0,.25,0))));
        joined.lift(new double[]{0,.7});
        var skin=FiniteMeshCutTest.box(new Vec3(-.25,-.25,-.25),new Vec3(.25,.25,.25),1).stream().flatMap(List::stream).toList();
        var visible=new RagdollPoseHistory.Pose(2,joined.positions()[1],rotation,1);
        var uncorrected=new RagdollPoseHistory.Pose(2,raw.get(1).position(),rotation,1);
        var sweep=List.of(new com.codex.blacklight.network.RagdollImpactPayload.BladeSpan(List.of(new Vec3(-.5,1,-.6),new Vec3(.5,1,-.6)),1,Vec3.ZERO,180),
                new com.codex.blacklight.network.RagdollImpactPayload.BladeSpan(List.of(new Vec3(-.5,1,.6),new Vec3(.5,1,.6)),2,Vec3.ZERO,180));
        for(var pose:List.of(visible,uncorrected)){
            var local=sweep.stream().map(s->new com.codex.blacklight.network.RagdollImpactPayload.BladeSpan(s.points().stream().map(p->pose.toBind(p,Vec3.ZERO)).toList(),s.time(),Vec3.ZERO,180)).toList();
            var hit=ContactSection.contact(new SweptBladeSurface(local).triangles(),skin);
            if(pose==visible)assertNotNull(hit,"The visible blade crosses the connected, lifted skin");
            else assertNull(hit,"The raw simulated part is elsewhere and must not authorize this cut");
        }
    }
}
