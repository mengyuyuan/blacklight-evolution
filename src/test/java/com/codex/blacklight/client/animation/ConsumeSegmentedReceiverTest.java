package com.codex.blacklight.client.animation;

import com.codex.blacklight.client.model.CommonInfectedModel;
import com.codex.blacklight.combat.ConsumePlacementTrack;
import java.util.IdentityHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeSegmentedReceiverTest {
    private static Quaternionf q(ModelPart p){return new Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot);}
    @Test void capturedForearmsAndShinsFollowTheirOwnSourceWorldRotations(){
        var model=new CommonInfectedModel(CommonInfectedModel.layer().bakeRoot());
        var frame=ConsumePlacementTrack.projection("claws_head",.27F).orElseThrow();
        ConsumeVictimPose.projectHumanoid(model,new IdentityHashMap<ModelPart,PartPose>(),frame,
                ConsumePlacementTrack.contactFrame("claws_head",.27F).orElseThrow(),false);
        var parents=new ModelPart[]{model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        for(int i=0;i<parents.length;i++) {
            var child=parents[i].getChild(i<2?"forearm":"shin");
            var actual=q(parents[i]).mul(q(child));
            var expected=ConsumePlacementTrack.quaternion(frame,7+(7+i)*4);
            assertEquals(1,Math.abs(actual.dot(expected)),1e-5,"child world rotation "+i);
            assertTrue(Math.abs(child.xRot)+Math.abs(child.yRot)+Math.abs(child.zRot)>.1F,"child stayed rigid "+i);
        }
    }
    @Test void hangingBodyBendsAtTheKneesWithoutMovingItsNeckOrHipPivots(){
        var model=new CommonInfectedModel(CommonInfectedModel.layer().bakeRoot());
        var head=model.head.storePose();var rightHip=model.rightLeg.storePose();var leftHip=model.leftLeg.storePose();
        ConsumeVictimPose.dangleHumanoid(model,new IdentityHashMap<ModelPart,PartPose>(),.8F,1);
        var afterHead=model.head.storePose();
        assertEquals(head.x,afterHead.x);assertEquals(head.y,afterHead.y);assertEquals(head.z,afterHead.z);
        assertEquals(head.xRot,afterHead.xRot);assertEquals(head.yRot,afterHead.yRot);assertEquals(head.zRot,afterHead.zRot);
        assertEquals(rightHip.x,model.rightLeg.x);assertEquals(rightHip.y,model.rightLeg.y);assertEquals(rightHip.z,model.rightLeg.z);
        assertEquals(leftHip.x,model.leftLeg.x);assertEquals(leftHip.y,model.leftLeg.y);assertEquals(leftHip.z,model.leftLeg.z);
        assertTrue(model.rightLeg.getChild("shin").xRot>.5F);assertTrue(model.leftLeg.getChild("shin").xRot>.5F);
        var right=q(model.rightLeg).transform(new Vector3f(0,6,0));
        var left=q(model.leftLeg).transform(new Vector3f(0,6,0));
        assertTrue(right.distance(left)>.5F,"both legs still form the same straight column");
    }
}
