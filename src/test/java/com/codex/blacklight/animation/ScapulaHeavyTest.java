package com.codex.blacklight.animation;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScapulaHeavyTest {
    private static ScapulaCycle.Chamber chamber(String key,float tick,boolean right){
        var v=MotionLibrary.clip(key).sample("Scapula",tick/20,false).getTranslation(new Vector3f());
        return ScapulaCycle.chamber(key,tick,0,right,v.x,v.y);
    }
    private static Vector3f chestAim(String key,float tick){
        var rig=BlacklightSkeleton.get();
        var body=rig.getPoseMatrices(MotionLibrary.pose(key,tick/20,false));
        return ScapulaMount.chest(body).transformDirection(new Vector3f(0,0,-1)).normalize();
    }
    @Test void twoBeatThrowKeepsTheCounterShoulderLoadedUntilItsOwnRelease(){
        for(String branch:List.of("crush","return","sweep"))for(boolean lead:List.of(false,true)){
            String k=ScapulaCycle.actionKey("spines_ground_"+branch+"_throw",lead);
            assertEquals(4,ScapulaCycle.shotTick(k,lead));
            assertEquals(10,ScapulaCycle.shotTick(k,!lead));
            assertEquals(0,chamber(k,4,lead).growth());
            assertEquals(1,chamber(k,4,!lead).growth());
            assertEquals(1,chamber(k,9.98F,!lead).open(),.001);
            assertEquals(0,chamber(k,10,!lead).growth());
            assertEquals(1,chamber(k,10,lead).growth());
            assertTrue(chestAim(k,4).dot(chestAim(k,10))<.77F,k);
        }
    }
    @Test void chargedGroundCastDrawsBothSocketsAndDetachesBothOnOneFrame(){
        for(String branch:List.of("crush","return","sweep"))for(boolean lead:List.of(false,true)){
            var draw=ScapulaCycle.actionKey("spines_ground_"+branch+"_draw",lead);
            var cast=ScapulaCycle.actionKey("spines_ground_"+branch+"_charged",lead);
            for(boolean right:List.of(false,true)){
                assertEquals(1,chamber(draw,10,right).open());
                assertEquals(5,ScapulaCycle.shotTick(cast,right));
                assertEquals(1,chamber(cast,4.98F,right).growth());
                assertEquals(0,chamber(cast,5,right).growth());
                assertEquals(1,chamber(cast,16,right).growth());
            }
        }
    }
    @Test void tapAndChargedAirReleasesHaveDifferentSilhouettes(){
        var rig=BlacklightSkeleton.get();
        for(String branch:List.of("crush","return","sweep")){
            var tap=rig.getPoseMatrices(MotionLibrary.pose("spines_air_"+branch+"_throw",6/20F,false));
            var full=rig.getPoseMatrices(MotionLibrary.pose("spines_air_"+branch+"_charged",7.2F/20,false));
            assertTrue(new Matrix4f(tap[rig.chest.getId()]).transformDirection(new Vector3f(0,1,0))
                    .distance(new Matrix4f(full[rig.chest.getId()]).transformDirection(new Vector3f(0,1,0)))>.3F);
        }
    }
}
