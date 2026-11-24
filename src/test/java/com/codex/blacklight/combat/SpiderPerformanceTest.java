package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiderPerformanceTest {
    private static final String[] UPPER={"thorax","veiled_head","crest","streamer","arm_lowered","elbow_lowered",
            "wrist_lowered","arm_raised","elbow_raised","wrist_raised"};

    @Test void everyCombatGestureHasAnAccentAndReleasesWithoutResidualRotation(){
        for(var move:SpiderActions.Move.values()){
            if(move==SpiderActions.Move.REST)continue;
            float peak=0;
            for(var bone:UPPER){
                var previous=new Quaternionf();
                for(int frame=0;frame<=100*4;frame++){
                    float tick=frame*.25F;
                    var rotation=SpiderUpperMotion.apply(move,bone,tick,1,new Quaternionf());
                    assertTrue(Math.abs(rotation.lengthSquared()-1)<.0001,move+" "+bone);
                    assertTrue(Math.abs(rotation.dot(previous))>.995F,"discontinuity "+move+" "+bone+" "+tick);
                    peak=Math.max(peak,rotation.angle());
                    previous=rotation;
                }
                assertEquals(1,previous.w,.0001,move+" "+bone);
            }
            assertTrue(peak>.20F,"gesture missing "+move);
        }
    }

    @Test void allFiveVolleysRecoilAtTheProjectileTicks(){
        for(int tick:new int[]{15,22,29,46,50}){
            assertTrue(SpiderUpperMotion.volleyKick(tick)>.75F,"release "+tick);
            assertTrue(SpiderUpperMotion.volleyKick(tick)>SpiderUpperMotion.volleyKick(tick-2),"anticipation "+tick);
        }
        assertEquals(0,SpiderUpperMotion.volleyKick(59));
    }

    @Test void freeFootUnloadsBeforeReachingAndLandsWithNearZeroVelocity(){
        var from=new Vec3(-3,2,6);
        for(var to:new Vec3[]{new Vec3(2,2,9),new Vec3(2,3,9),new Vec3(2,.25,9)}){
            assertEquals(from,SpiderFooting.swing(from,to,0));
            var lifted=SpiderFooting.swing(from,to,.08F);
            assertEquals(from.x,lifted.x,1e-9);assertTrue(lifted.y>from.y);
            assertEquals(to,SpiderFooting.swing(from,to,1));
            assertTrue(SpiderFooting.swing(from,to,.99F).distanceTo(to)<.004);
        }
    }
}
