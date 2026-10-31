package com.codex.blacklight.infection;

import com.codex.blacklight.combat.EvolvedMotions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedFormTest {
    @Test void lateAgeCannotReplaceTheMealAndMatureOrganGates(){
        assertFalse(EvolvedForm.eligible(TissueInheritance.EMPTY,Long.MAX_VALUE));
        var mature=Map.of(TissueCore.BLADE,new TissueInheritance.Organ(350,0,0,0,1,8));
        assertFalse(EvolvedForm.eligible(new TissueInheritance(mature,5,2100,true,null,1,6),504000));
        assertFalse(EvolvedForm.eligible(new TissueInheritance(mature,6,2100,true,null,1,5),504000));
        var forming=Map.of(TissueCore.BLADE,new TissueInheritance.Organ(350,10,3600,3600,1,8));
        assertFalse(EvolvedForm.eligible(new TissueInheritance(forming,6,2100,true,null,1,6),504000));
        assertTrue(EvolvedForm.eligible(new TissueInheritance(mature,6,2100,true,null,1,6),504000));
    }
    @Test void bodyReclamationHidesTheSwapAndReleasesTheCover(){
        assertEquals(1,EvolvedForm.oldBody(0));assertEquals(0,EvolvedForm.humanBody(119));
        assertEquals(0,EvolvedForm.oldBody(EvolvedForm.COMMIT));assertEquals(1,EvolvedForm.cover(EvolvedForm.COMMIT));
        assertEquals(1,EvolvedForm.humanBody(200));assertEquals(0,EvolvedForm.cover(200));
        float previous=0;for(int tick=120;tick<=200;tick++){float t=EvolvedForm.humanBody(tick);assertTrue(t>=previous);previous=t;}
    }
    @Test void adaptedCombatRotationsAreFiniteAndTheLoopJoinsSmoothly(){
        for(String key:new String[]{"run","sprint","left","right","back","rush","thrust_left","thrust_right","slash","leap","dodge_left","dodge_right","transition","consume","victim","hit","heavy_hit","guard","taunt_sharpen","taunt_beckon","idle_shrug","idle_neck","guard_forward","guard_back","guard_left","guard_right"}){
            var clip=EvolvedMotions.clip(key);assertNotNull(clip);assertTrue(clip.seconds()>0);
            for(int frame=0;frame<150;frame++)for(float value:EvolvedMotions.sample(key,frame*.31F,false))assertTrue(Float.isFinite(value));
        }
        var loop=EvolvedMotions.clip("run");var before=EvolvedMotions.sample("run",loop.seconds()*20-.0001F,true);var after=EvolvedMotions.sample("run",.0001F,true);
        for(int i=0;i<before.length;i+=7){float dot=0;for(int j=0;j<4;j++)dot+=before[i+j]*after[i+j];assertTrue(Math.abs(dot)>.9999);}
        assertEquals(67,EvolvedMotions.bones().size());
        for(var m:EvolvedMotions.skin(null,0))assertTrue(m.equals(new org.joml.Matrix4f(),.00001F),"rest mesh retains the standard block body");
    }
    @Test void originalRootCurvesContainRetreatLungeAndAirborneTravel(){
        assertTrue(EvolvedMotions.forwardExtent("rush")>2.9);
        assertTrue(EvolvedMotions.forwardExtent("thrust_left")>5);
        var slash=EvolvedMotions.clip("slash");assertTrue(slash.root().stream().anyMatch(p->p[2]<-6));
        assertTrue(EvolvedMotions.clip("leap").root().stream().anyMatch(p->p[1]>1));
        for(String key:new String[]{"rush","slash","leap","thrust_left","thrust_right","dodge_left","dodge_right"}){
            assertEquals(0,EvolvedMotions.root(key,0).length(),.0001);
            for(int tick=0;tick<80;tick++)assertTrue(EvolvedMotions.root(key,tick).isFinite());
        }
        var left=EvolvedMotions.root("dodge_left",100);var right=EvolvedMotions.root("dodge_right",100);
        assertEquals(-left.x,right.x,.03);assertEquals(left.z,right.z,.03);
    }
    @Test void adaptedLegsKeepTheirLengthsAndSourceForwardDirection(){
        var bones=EvolvedMotions.bones();
        float duration=EvolvedMotions.clip("run").seconds()*20;
        for(int frame=0;frame<17;frame++){
            var skin=EvolvedMotions.skin(EvolvedMotions.sample("run",frame*duration/16,false),1);
            for(String side:new String[]{"R","L"}){
                int hip=EvolvedMotions.index("Hip_"+side),knee=EvolvedMotions.index("Knee_"+side);
                var h=skin[hip].transformPosition(bones.get(hip).pivot());var k=skin[knee].transformPosition(bones.get(knee).pivot());
                assertEquals(6,h.distance(k),.015,"Minecraft thigh stays attached at its real hip");
            }
        }
        var skin=EvolvedMotions.skin(EvolvedMotions.sample("run",duration*.5F,false),1);
        int knee=EvolvedMotions.index("Knee_R");var aim=skin[knee].transformDirection(new org.joml.Vector3f(0,1,0));
        assertEquals(.0221,aim.x,.002,"source frame-eight right shin X direction");
        assertEquals(-.4072,aim.y,.002,"source airborne knee bend changes Y handedness exactly once");
        assertEquals(.9131,aim.z,.002,"source rearward shin stays toward +Z instead of being mirrored into the face");
    }
}
