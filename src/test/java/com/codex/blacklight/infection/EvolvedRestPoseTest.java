package com.codex.blacklight.infection;

import com.codex.blacklight.combat.EvolvedMotions;
import com.codex.blacklight.combat.EvolvedRestPose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedRestPoseTest {
    @Test void StaticSourceStancesBreatheWithoutChangingSourceOrAttackFrames(){
        for(String clip:new String[]{"idle","guard"}){
            var original=EvolvedMotions.sample(clip,0,true);var before=original.clone();
            var a=EvolvedRestPose.apply(original,clip,0,27);var b=EvolvedRestPose.apply(original,clip,20,27);
            assertArrayEquals(before,original);assertFalse(java.util.Arrays.equals(a,b));
            for(var p:new float[][]{a,b})for(float f:p)assertTrue(Float.isFinite(f));
        }
        var attack=EvolvedMotions.sample("thrust_left",20,false);
        assertSame(attack,EvolvedRestPose.apply(attack,"thrust_left",20,27));
    }
    @Test void AddedMotionKeepsTheActualHipAndKneeConnectionsAndHasNoAccumulatingDrift(){
        var source=EvolvedMotions.sample("guard",0,true);var bones=EvolvedMotions.bones();
        for(int t=0;t<120;t++){
            var pose=EvolvedRestPose.apply(source,"guard",t,81);var skin=EvolvedMotions.skin(pose,1);
            for(String side:new String[]{"R","L"}){
                int hip=EvolvedMotions.index("Hip_"+side),knee=EvolvedMotions.index("Knee_"+side);
                assertEquals(6,skin[hip].transformPosition(bones.get(hip).pivot()).distance(skin[knee].transformPosition(bones.get(knee).pivot())),.015);
            }
        }
        assertArrayEquals(EvolvedRestPose.apply(source,"guard",1,81),EvolvedRestPose.apply(source,"guard",1,81));
    }
}
