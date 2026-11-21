package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.combat.ConsumeVisual;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeBodyImpactTest {
    private final Vec3 grip=new Vec3(2097152.3,72,-4194304.2);
    @Test void chestContactHoldsTheGripAndMovesTheOutlineBeforeRebound(){
        var type=ConsumeBodyImpact.stroke("normal",ConsumeVisual.CONSUME);
        assertNull(ConsumeBodyImpact.frame(type,0,1,0,grip,71,1.8));
        var hit=ConsumeBodyImpact.frame(type,0,1,1.65,grip,71,1.8);
        var head=grip.add(0,.65,0);
        assertTrue(hit.point(head).distanceTo(head)>.28);
        assertEquals(0,hit.point(grip).distanceTo(grip),1e-6);
        var easing=ConsumeBodyImpact.frame(type,0,1,3.95,grip,71,1.8);
        assertTrue(easing.point(head).subtract(head).dot(hit.point(head).subtract(head))<0);
        assertNull(ConsumeBodyImpact.frame(type,0,1,5.75,grip,71,1.8));
    }
    @Test void groundStrikesKeepTheFloorAndAlternateTheirDirection(){
        var body=grip.add(.15,0,.8);
        for(String name:new String[]{"normal_head_stomp","normal_beatdown"}){
            var type=ConsumeBodyImpact.stroke(name,ConsumeVisual.CONSUME);
            var a=ConsumeBodyImpact.frame(type,77,1,1.6,grip,grip.y,1);
            var b=ConsumeBodyImpact.frame(type,77,-1,1.6,grip,grip.y,1);
            assertEquals(body.y,a.point(body).y,1e-6);
            assertTrue(a.point(body).distanceTo(body)>.25);
            assertTrue(a.point(body).subtract(body).dot(b.point(body).subtract(body))<0);
        }
    }
    @Test void mountedSkinKeepsItsFeetAndSharesWorldAndCameraCoordinates(){
        var chest=grip.add(.9,0,.45);
        var feet=chest.subtract(0,2.6,0);
        var camera=grip.add(2,1,2);
        for(int stage:new int[]{ConsumeVisual.MOUNT_CONSUME,ConsumeVisual.HUNTER_CONSUME}){
            var type=ConsumeBodyImpact.stroke("normal",stage);
            var frame=ConsumeBodyImpact.frame(type,15,-1,1.6,grip,feet.y,3.0);
            assertEquals(0,frame.point(feet).distanceTo(feet),1e-7);
            assertTrue(frame.point(chest).distanceTo(chest)>.1);
            var local=frame.cameraPoint(chest.subtract(camera).toVector3f(),camera);
            assertEquals(0,camera.add(local.x,local.y,local.z).distanceTo(frame.point(chest)),1e-6);
        }
    }
    @Test void attackerCounterweightsTheFreeShoulderWithoutMovingFeetOrContactAtOnset(){
        var pose=new BlacklightPose();
        ConsumeBodyImpact.actorPose(pose,ConsumeBodyImpact.Stroke.CHEST,1,0);
        assertTrue(pose.getJointTransformData().isEmpty());
        ConsumeBodyImpact.actorPose(pose,ConsumeBodyImpact.Stroke.CHEST,1,1);
        assertTrue(pose.get("Chest").rotation().angle()>.16);
        assertTrue(pose.get("Arm_L").rotation().angle()>.14);
        assertNull(pose.get("Arm_R"));assertNull(pose.get("Root"));assertNull(pose.get("Leg_R"));
    }
    @Test void groundFragmentsHaveAFreeArcBeforeRetrievalAndPreserveTheirSkinThickness(){
        var v=ConsumeShardMotion.eject(new Vec3(.7,0,.7),1,1);
        assertTrue(v.horizontalDistance()>.16);
        double y=0,peak=0;
        for(int i=0;i<12;i++){v=v.add(0,-.049*.25,0);y+=v.y*.25;peak=Math.max(peak,y);}
        assertTrue(peak>.4);
        assertEquals(0,ConsumeShardMotion.draw(true,1,4.999));
        assertEquals(.5,ConsumeShardMotion.draw(true,1,7),1e-6);
        assertEquals(1,ConsumeShardMotion.draw(true,1,9));
        assertEquals(.8,ConsumeShardMotion.draw(false,.8,0));
        var landed=ConsumeShardMotion.bounce(new Vec3(.13,-.20,.06),new Vec3(0,1,0));
        assertTrue(landed.y>.05&&landed.y<.2);assertTrue(landed.x>0&&landed.x<.13);
        assertEquals(0,ConsumeShardMotion.bounce(new Vec3(.02,-.02,0),new Vec3(0,1,0)).y,1e-8);
        assertEquals(.6,ConsumeShardMotion.thickness(.4,0),1e-6);
        assertEquals(1,ConsumeShardMotion.thickness(.4,3),1e-6);
    }
}
