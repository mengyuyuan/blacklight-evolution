package com.codex.blacklight.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

class RagdollRecoveryTest {
    @Test void explicitLongGetupMeetsItsDeadlineWithoutStartingWhileDown(){
        assertEquals(0,RagdollRecovery.progress(99,132,32));
        for(int subdivisions:new int[]{1,2,8}){
            double error=4;
            for(int i=0;i<32*subdivisions;i++)error*=1-RagdollRecovery.step(100+i/(double)subdivisions,100+(i+1)/(double)subdivisions,132,32);
            assertEquals(0,error,1e-8);
        }
        assertEquals(.5,RagdollRecovery.progress(116,132,32),1e-8);
    }
    @Test void reachesTheAnimationBeforeRetiringAtAnySubstepRate(){
        for(int subdivisions:new int[]{1,2,4,16}){
            double error=5;
            for(int i=0;i<16*subdivisions;i++){
                double from=84+i/(double)subdivisions,to=84+(i+1)/(double)subdivisions;
                error*=1-RagdollRecovery.step(from,to,100);
            }
            assertEquals(0,error,1e-8);
        }
    }
    @Test void schedulesTheSamePoseIndependentlyOfSubstepRate(){
        for(int subdivisions:new int[]{1,2,4,16}){
            double error=5;
            for(int i=0;i<8*subdivisions;i++){
                double from=84+i/(double)subdivisions,to=84+(i+1)/(double)subdivisions;
                error*=1-RagdollRecovery.step(from,to,100);
            }
            assertEquals(2.5,error,1e-6);
        }
    }
    @Test void leavesKnockdownAndHitstopUntouchedUntilRecovery(){
        assertEquals(0,RagdollRecovery.step(10,11,100));
        assertEquals(0,RagdollRecovery.step(83,84,100));
        assertEquals(0,RagdollRecovery.step(92,91,100));
    }
    @Test void completedRecoveryContinuesTrackingTheLivePose(){
        assertEquals(1,RagdollRecovery.step(100,101,100));
        assertEquals(1,RagdollRecovery.step(102,103,100));
    }
    @Test void authoritativeCompletionHandlesClockSkewWithoutRemovingEarlyInterruptionBlend(){
        for(long now=98;now<=100;now++)assertEquals(now,RagdollRecovery.releaseDeadline(now,100,true));
        assertEquals(100,RagdollRecovery.releaseDeadline(101,100,true));
        assertEquals(116,RagdollRecovery.releaseDeadline(100,132,true));
        assertEquals(116,RagdollRecovery.releaseDeadline(100,132,false));
        assertEquals(100,RagdollRecovery.releaseDeadline(99,100,false));
    }
    @Test void translatedAnimatedPivotsCloseAtTheirCurrentNativePose(){
        var parent=new RagdollJointProjection.Pose(new Vec3(1,2,3),new Quaternionf().rotationXYZ(.5F,.3F,-.2F),1.1F);
        var child=new RagdollJointProjection.Pose(new Vec3(1.7,1.4,2.8),new Quaternionf().rotationXYZ(-.8F,.1F,.6F),.9F);
        var pivot=new Vec3(1.2,1.8,2.7);
        var a=RagdollRecovery.localAnchor(pivot,parent.position(),parent.rotation(),parent.scale());
        var b=RagdollRecovery.localAnchor(pivot,child.position(),child.rotation(),child.scale());
        var result=RagdollJointProjection.close(List.of(parent,child),List.of(new RagdollJointProjection.Link(0,1,a,b)));
        assertTrue(result.positions()[1].distanceTo(child.position())<1e-6);
    }
    @Test void nativeRendererReceivesTheSameOffsetAndSettlesWithoutOvershoot(){
        assertEquals(1,RagdollRecovery.releaseWeight(0));assertEquals(0,RagdollRecovery.releaseWeight(8));
        assertEquals(1,RagdollRecovery.releaseWeight(-1));assertEquals(0,RagdollRecovery.releaseWeight(20));
        double previous=1;for(double t=0;t<=8;t+=.1){double w=RagdollRecovery.releaseWeight(t);assertTrue(w<=previous+1e-9&&w>=0);previous=w;}
    }
}
