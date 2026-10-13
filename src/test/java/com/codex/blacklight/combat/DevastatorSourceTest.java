package com.codex.blacklight.combat;

import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.animation.WeaponMotionClock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/** Actual bundled source segments, not mocked timing numbers. */
class DevastatorSourceTest {
    @BeforeEach void requirePrivateProfile() {org.junit.jupiter.api.Assumptions.assumeTrue(LocalWeaponMotion.find("dev_groundspike").isPresent(),"Requires the private dev7 source motion profile");}
    @Test void formalSkillsHaveSeparateSourceChargeAndValidEffects() {
        for(var move:List.of(WeaponMove.DEV_GROUNDSPIKE,WeaponMove.DEV_AIR_GROUNDSPIKE,
                WeaponMove.DEV_TENDRIL,WeaponMove.DEV_AIR_TENDRIL,WeaponMove.DEV_CRITICAL_PAIN,
                WeaponMove.DEV_AIR_CRITICAL_PAIN,WeaponMove.BULLETDIVE_DROP)){
            var clip=LocalWeaponMotion.find(move.id).orElseThrow();
            var charge=clip.charge().orElseThrow();
            assertTrue(MotionLibrary.find(charge.animation()).isPresent());
            assertNotEquals(move.id,charge.animation());
            var timing=DevastatorTiming.of(move);
            assertTrue(timing.effectEnd()>timing.effectStart());
            assertTrue(timing.damage()>0&&timing.radius()>0);
            assertTrue(timing.targets()<=64);
        }
    }
    @Test void landingClocksCannotReachContactUntilRealImpactSeek() {
        for(var move:List.of(WeaponMove.BULLETDIVE_DROP,WeaponMove.DEV_AIR_GROUNDSPIKE)){
            var clip=LocalWeaponMotion.find(move.id).orElseThrow();var land=clip.landing().orElseThrow();
            var falling=new WeaponVisual(move.ordinal(),100,0,false,clip.charge().orElseThrow().releaseTime(),-1,land.holdTime());
            var onFloor=new WeaponVisual(move.ordinal(),100-Math.round(land.impactTime()*20),0,false,land.impactTime(),-1,-1);
            assertTrue(WeaponMotionClock.sample(falling,900).seconds()<land.impactTime());
            assertEquals(land.holdTime(),WeaponMotionClock.sample(falling,900).seconds(),.00001);
            assertTrue(WeaponMotionClock.sample(onFloor,103).seconds()>land.impactTime());
        }
    }
    @Test void bulletUnknownDamageMarkersUseExplicitMinecraftTuning() {
        var meta=LocalWeaponMotion.find("bulletdive_drop").orElseThrow().animation().getAsJsonObject("blacklight").getAsJsonObject("devastator");
        assertEquals(0,meta.get("damageSource").getAsFloat());
        var timing=DevastatorTiming.of(WeaponMove.BULLETDIVE_DROP);
        assertEquals(22,timing.damage(),.0001);assertEquals(6,timing.radius(),.0001);assertEquals(16,timing.targets());
        assertEquals(.5,timing.effectEnd()-timing.effectStart(),.0001);
    }
    @Test void sourceRangeUsesMeasuredActorScale() {
        assertEquals(32.70493,DevastatorTiming.of(WeaponMove.DEV_GROUNDSPIKE).radius(),.0001);
        assertEquals(24.52870,DevastatorTiming.of(WeaponMove.DEV_TENDRIL).radius(),.0001);
        assertEquals(163.52465,DevastatorTiming.of(WeaponMove.DEV_CRITICAL_PAIN).radius(),.0001);
    }
    @Test void painSourceProvidesRealVerticalAimVariants() {
        for(String key:List.of("dev_critical_pain","dev_air_critical_pain")){
            assertEquals(MotionLibrary.duration(key),MotionLibrary.duration(key+"_aim_up"),.00001);
            assertEquals(MotionLibrary.duration(key),MotionLibrary.duration(key+"_aim_down"),.00001);
            var neutral=MotionLibrary.clip(key).sample("Arm_R",3.2F,false);
            var up=MotionLibrary.clip(key+"_aim_up").sample("Arm_R",3.2F,false);
            var down=MotionLibrary.clip(key+"_aim_down").sample("Arm_R",3.2F,false);
            assertFalse(neutral.equals(up,.0001F));assertFalse(up.equals(down,.0001F));
        }
    }
}
