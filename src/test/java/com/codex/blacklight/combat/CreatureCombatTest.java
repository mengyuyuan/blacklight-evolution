package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureCombatTest {
    @Test void recoveryDoesNotUnwindEquivalentRotations(){
        float[] a={0,0,(float)Math.toRadians(179),0,0,0};
        float[] b={0,0,(float)Math.toRadians(-179),0,4,0};
        var mid=CreaturePoseTable.blend(a,b,.5F);
        var direction=new org.joml.Quaternionf().rotationZYX(mid[2],mid[1],mid[0]).transform(new org.joml.Vector3f(1,0,0));
        assertTrue(direction.x<-.999F,"The recovery should cross 180 degrees, not spin through zero");
        assertEquals(2,mid[4],1e-6);
    }
    @Test void attacksHaveWindupAndRecoveryAndNeverHitDuringDodge(){
        for(var move:CreatureAttack.values()){
            for(int t=-2;t<4;t++)assertFalse(move.hitsAt(t));
            for(int t=move.ticks-5;t<move.ticks+5;t++)assertFalse(move.hitsAt(t));
            if(move!=CreatureAttack.NONE)assertTrue(move.recovery>=12);
        }
        for(int t=0;t<40;t++)assertFalse(CreatureAttack.DODGE.hitsAt(t));
    }
    @Test void targetBehindCannotReceiveForwardSwipe(){
        assertTrue(CreatureAttack.inArc(0,2,0,.15));assertFalse(CreatureAttack.inArc(0,-2,0,.15));
        assertTrue(CreatureAttack.inArc(-2,0,90,.15));assertFalse(CreatureAttack.inArc(2,0,90,.15));
        assertNotEquals(CreatureAttack.LEAP,CreatureAttack.choose(2,0));assertEquals(CreatureAttack.LEAP,CreatureAttack.choose(7,0));
    }
    @Test void everyActionHasFinitePoseIncludingLateTrackingAndEndClamp(){
        var table=CreaturePoseTable.HUNTER;
        for(var move:CreatureAttack.values())for(float t=-1;t<move.ticks+10;t+=.25F)
            for(var bone:table.sample(move.clip,t))for(float angle:bone)assertTrue(Float.isFinite(angle));
    }
}
