package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MutantCombatTest {
    @Test void speciesSelectDifferentCounterplayAndNeverSelectAnUnrelatedClip(){
        assertEquals(MutantMove.STALKER_POUNCE,MutantMove.choose("STALKER",6,0));
        assertEquals(MutantMove.SPITTER_RETREAT,MutantMove.choose("SPITTER",2,0));
        assertEquals(MutantMove.SPITTER_VOLLEY,MutantMove.choose("SPITTER",10,2));
        assertEquals(MutantMove.BRUTE_CHARGE,MutantMove.choose("BRUTE",8,0));
        assertEquals(MutantMove.BRUTE_SLAM,MutantMove.choose("BRUTE",3,2));
        for(String species:new String[]{"STALKER","SPITTER","BRUTE"})for(int i=-10;i<10;i++)for(double distance:new double[]{1,3,6,12})
            assertEquals(species,MutantMove.choose(species,distance,i).species);
    }
    @Test void everyMoveHasRecoveryAndAnAnimationMatchingTheServerClock(){
        for(var move:MutantMove.values())if(move!=MutantMove.NONE){
            var table=CreaturePoseTable.mutant(move.species);
            assertEquals(move.ticks/20F,table.seconds(move.clip),.001,move.name());
            assertTrue(move.recovery>=12);
            for(int t=-3;t<8;t++)assertFalse(move.contact(t),move.name());
            for(int t=move.ticks-5;t<move.ticks+5;t++)assertFalse(move.contact(t),move.name());
            for(float t=-1;t<move.ticks+2;t+=.5F)for(float[] bone:table.sample(move.clip,t)){
                assertEquals(9,bone.length);
                for(float value:bone)assertTrue(Float.isFinite(value));
                for(int i=6;i<9;i++)assertTrue(bone[i]>.5&&bone[i]<1.5,"limbs cannot collapse during a skill");
            }
            var start=table.sample(move.clip,0);var end=table.sample(move.clip,move.ticks);
            for(int b=0;b<table.bones.length;b++)assertArrayEquals(start[b],end[b],.0001F,"recovery returns the authored joints");
        }
    }
    @Test void slamContactFollowsRaisedArmsAndRetreatHasNoDamageWindow(){
        var table=CreaturePoseTable.BRUTE;
        int arm=java.util.Arrays.asList(table.bones).indexOf("right_arm");
        assertTrue(table.sample("ground_slam",18)[arm][0]>2.5,"both arms visibly raised during telegraph");
        double low=BruteContact.boxes(MutantMove.BRUTE_SLAM,26,net.minecraft.world.phys.Vec3.ZERO,0,true).stream().mapToDouble(b->b.bounds.minY).min().orElseThrow();
        assertTrue(low<.03,"descending fists reach the ground plane");
        for(int t=0;t<50;t++)assertFalse(MutantMove.SPITTER_RETREAT.contact(t));
        assertEquals(MutantMove.NONE,MutantMove.byId(-1));assertEquals(MutantMove.NONE,MutantMove.byId(1000));
    }
    @Test void lateTrackingAndReorderedSamplingProduceIdenticalPose(){
        var table=CreaturePoseTable.SPITTER;var expected=table.sample("volley",24.5F);
        table.sample("retreat",11);table.sample("spit",7);table.sample("volley",1000);
        var actual=table.sample("volley",24.5F);
        for(int i=0;i<expected.length;i++)assertArrayEquals(expected[i],actual[i]);
        assertTrue(expected[1][6]<1,"throat contracts when the volley releases");
    }
}
