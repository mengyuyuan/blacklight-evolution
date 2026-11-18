package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class BruteFlinchTest {
    @Test void firstObservedFrameKeepsTheActualInterruptedWindup(){
        var from=CreaturePoseTable.BRUTE.sample("ground_slam",18);
        var idle=CreaturePoseTable.BRUTE.sample("idle",0);
        var target=BruteFlinch.recoil(idle,HitReaction.STAGGER,ImpactProfile.LEFT,.5F);
        var entry=BruteFlinch.enter(from,target,0);var settled=BruteFlinch.enter(from,target,3);
        for(int b=0;b<from.length;b++){assertArrayEquals(from[b],entry[b],.00001F);assertArrayEquals(target[b],settled[b],.00001F);}
    }
    @Test void directionalRecoilReturnsToTheSameIdlePose(){
        var idle=CreaturePoseTable.BRUTE.sample("idle",0);int torso=Arrays.asList(CreaturePoseTable.BRUTE.bones).indexOf("torso");
        var left=BruteFlinch.recoil(idle,HitReaction.STAGGER,ImpactProfile.LEFT,.25F);
        var right=BruteFlinch.recoil(idle,HitReaction.STAGGER,ImpactProfile.RIGHT,.25F);
        assertEquals(left[torso][1]-idle[torso][1],-(right[torso][1]-idle[torso][1]),.00001F);
        for(float p:new float[]{0,1}){var out=BruteFlinch.recoil(idle,HitReaction.HEAVY,ImpactProfile.BODY,p);for(int b=0;b<idle.length;b++)assertArrayEquals(idle[b],out[b],.00001F);}
        assertFalse(BruteFlinch.standing(HitReaction.DOWN));assertFalse(BruteFlinch.standing(HitReaction.DEATH));
    }
    @Test void standingBodyFlinchesKeepTheirFistsAboveTheFloor(){
        var floor=new AABB(-4,-1,-4,4,0,4);var idle=CreaturePoseTable.BRUTE.sample("idle",0);
        for(int kind:new int[]{HitReaction.STAGGER,HitReaction.HEAVY,HitReaction.LASH})for(int motion:new int[]{ImpactProfile.BODY,ImpactProfile.LEFT,ImpactProfile.RIGHT})for(float p=0;p<=1;p+=.025F)
            for(var part:BruteContact.body(BruteFlinch.recoil(idle,kind,motion,p),Vec3.ZERO,0))assertFalse(part.box().touches(floor.deflate(.001)),part.bone()+" "+p);
    }
}
