package com.codex.blacklight.infection;

import com.codex.blacklight.client.model.InfectedModelPose;
import com.codex.blacklight.combat.EvolvedAttack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeInfectedProfileTest {
    @Test void sourceAbilitiesAreIndependentOfTheirLocomotorFamily(){
        assertEquals(NativeInfectedProfile.SONIC,NativeInfectedProfile.of("minecraft:warden"));
        assertEquals(NativeInfectedProfile.WITHER,NativeInfectedProfile.of("minecraft:wither"));
        assertEquals(NativeInfectedProfile.GUARDIAN,NativeInfectedProfile.of("minecraft:elder_guardian"));
        assertEquals(NativeInfectedProfile.BOW,NativeInfectedProfile.saved("another:archer","ranged"));
        assertEquals(NativeInfectedProfile.MELEE,NativeInfectedProfile.saved("another:beast",""));
    }
    @Test void everyRangedWeaponHasTelegraphContactAndRealRecovery(){
        for(var p:NativeInfectedProfile.values())if(p.ranged()){assertTrue(p.contact>0&&p.contact<p.duration);assertTrue(p.cooldown>p.duration/2);assertTrue(p.range>=14);}
    }
    @Test void specializedEffectsStartAroundContactAndEndDuringRecovery(){
        for(var move:EvolvedSpecialMove.values()){
            assertFalse(EvolvedAttack.trail(move.key,0,0));assertFalse(EvolvedAttack.trail(move.key,move.duration,0));
            if(move!=EvolvedSpecialMove.MEND&&move!=EvolvedSpecialMove.VOLLEY)assertTrue(EvolvedAttack.trail(move.key,move.contact,0));
        }
    }
    @Test void giantChargePoseChangesThenReturnsAndNeverLeaksToAnotherBody(){
        for(String action:new String[]{"SONIC","WITHER","DRAGON"}){
            var idle=new PoseStack();InfectedModelPose.nativeAction(action,"chest",0,idle);
            var charge=new PoseStack();InfectedModelPose.nativeAction(action,"chest",18,charge);
            assertFalse(idle.last().pose().equals(charge.last().pose(),.0001F));
            var finish=new PoseStack();InfectedModelPose.nativeAction(action,"chest",100,finish);assertTrue(new org.joml.Matrix4f().equals(finish.last().pose(),.0001F));
        }
    }
    @Test void aCorruptCosmeticMagnitudeCannotAllocateUnboundedGeometry(){
        var p=new com.codex.blacklight.network.InfectedImpactPayload(1,2,999,0,0,0,Float.NaN,0,0,Float.POSITIVE_INFINITY,1);
        assertEquals(5,p.style());assertEquals(.18F,p.size());assertEquals(1,p.dy());
        for(var kind:InfectedAttackStyle.values())assertTrue(kind.lifetime>=12&&kind.lifetime<=24);
    }
    @Test void squidAnimationKeepsCompletingCyclesWithoutOriginalEntityResetPackets(){
        for(long start:new long[]{0,1000,100000,999950}){float min=1,max=0;for(long t=start;t<start+120;t++){float a=NativeSpeciesMotion.squidTentacle(t,77);min=Math.min(min,a);max=Math.max(max,a);assertTrue(Float.isFinite(a)&&a>=0&&a<=Math.PI*.25);}assertTrue(max-min>.6,"no frozen native tentacles after any number of cycles");}
    }
}
