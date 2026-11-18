package com.codex.blacklight.combat;
import com.codex.blacklight.entity.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class JuggernautContactTest {
    @Test void guardFlashIsOnTheIncomingSurfaceInsteadOfInsideTheFist(){
        for(int age:new int[]{10,16,28,33})for(float yaw:new float[]{0,70,-135}){
            Vec3 origin=new Vec3(10,80,20),from=origin.add(new Vec3(0,1.5,5).yRot((float)Math.toRadians(-yaw)));
            Vec3 at=JuggernautContact.guardContact(age,origin,yaw,from);
            var l=JuggernautContact.fist(EncounterMove.GUARD,age,true,origin,yaw).center();
            var r=JuggernautContact.fist(EncounterMove.GUARD,age,false,origin,yaw).center();
            assertTrue(at.distanceTo(from)<Math.min(l.distanceTo(from),r.distanceTo(from)));
            assertTrue(Math.min(at.distanceTo(l),at.distanceTo(r))>.3);
        }
    }
    @Test void nativeCurvesAndTwoDistinctWristWindows(){
        var table=SourceBossMotion.get(EncounterKind.JUGGERNAUT);
        assertEquals("infected3_com_2hit_combo",table.clip("HAMMER").source());
        assertEquals("infected3_com_2hand_slam",table.clip("SLAM").source());
        var move=SourceBossAction.get(EncounterKind.JUGGERNAUT,EncounterMove.HAMMER);
        assertEquals(2,move.windows.length);assertEquals("Wrist_L",move.windows[0].joint());assertEquals("Wrist_R",move.windows[1].joint());
        assertTrue(move.windows[1].begin()-move.windows[0].end()>.5);
        assertTrue(move.duration()>60);
    }
    @Test void oversizedAuthoredFistsStayAboveSupportPlane(){
        for(var move:new EncounterMove[]{EncounterMove.HAMMER,EncounterMove.SLAM,EncounterMove.GUARD}){
            int duration=SourceBossAction.get(EncounterKind.JUGGERNAUT,move).duration();
            for(float age=0;age<=duration;age+=.5F)for(boolean left:new boolean[]{true,false}){
                var fist=JuggernautContact.fist(move,age,left,Vec3.ZERO,0);
                for(var p:fist.corners()){assertTrue(Double.isFinite(p.x+p.y+p.z));assertTrue(p.y>-.12,move+" penetrates floor at "+age+": "+p);}
            }
        }
    }
    @Test void onlyActiveFistContactCanDamageAndDistantTargetsMiss(){
        var move=EncounterMove.HAMMER;var source=SourceBossAction.get(EncounterKind.JUGGERNAUT,move);
        for(var w:source.windows){float t=(w.begin()+w.end())*10;
            Vec3 p=JuggernautContact.fist(move,t,w.joint().equals("Wrist_L"),Vec3.ZERO,0).center();var box=new AABB(p,p).inflate(.1);
            assertNotNull(JuggernautContact.contact(move,w,t-.5F,t,Vec3.ZERO,0,box));
            assertNull(JuggernautContact.contact(move,w,0,1,Vec3.ZERO,0,box));
            assertNull(JuggernautContact.contact(move,w,t-.5F,t,Vec3.ZERO,0,box.move(8,0,0)));
        }
        assertFalse(JuggernautContact.canReach(move,Vec3.ZERO,0,new AABB(-.5,0,5,.5,2,6)));
    }
    @Test void actualSlamDescentAndWindupHaveDifferentContactHeights(){
        var high=JuggernautContact.fist(EncounterMove.SLAM,36,false,Vec3.ZERO,0);
        var low=JuggernautContact.fist(EncounterMove.SLAM,44,false,Vec3.ZERO,0);
        assertTrue(high.center().y>3);assertTrue(low.center().y<1.5);assertTrue(high.center().y-low.center().y>2);
        assertFalse(JuggernautContact.guarding(0));assertTrue(JuggernautContact.guarding(20));assertFalse(JuggernautContact.guarding(40));
    }
    @Test void geometryPreviewFindsPigInFrontButNotBehind(){
        var pig=new AABB(-.45,0,2.55,.45,.9,3.45);
        assertTrue(JuggernautContact.canReach(EncounterMove.HAMMER,Vec3.ZERO,0,pig));
        assertFalse(JuggernautContact.canReach(EncounterMove.HAMMER,Vec3.ZERO,0,pig.move(0,0,-6)));
    }
}
