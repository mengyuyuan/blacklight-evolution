package com.codex.blacklight.gametest;

import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryMotionGameTest {
    @GameTest(template="blackwatch_arena",timeoutTicks=70)
    public static void acceptedHitStartsOneServerClockAndInterruptsRadio(GameTestHelper h){
        var actor=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());actor.setPos(h.absolutePos(new BlockPos(5,2,5)).getCenter());actor.setNoAi(true);actor.setPersistenceRequired();h.getLevel().addFreshEntity(actor);
        h.runAtTickTime(10,()->{
            h.assertTrue(actor.hitStarted()<0,"new soldier has no invented hit");actor.beginRadio();
            h.assertTrue(actor.hurt(h.getLevel().damageSources().generic(),1),"actual accepted hit");long start=actor.hitStarted();
            h.assertTrue(start==h.getLevel().getGameTime()&&actor.radioStarted()<0,"accepted hit uses server clock and interrupts actual radio state");
            h.assertTrue(!actor.hurt(h.getLevel().damageSources().generic(),1)&&actor.hitStarted()==start,"vanilla rejected duplicate cannot replay reaction");
        });
        h.runAtTickTime(35,()->{long before=actor.hitStarted();h.assertTrue(actor.hurt(h.getLevel().damageSources().generic(),1)&&actor.hitStarted()>before,"later accepted hit restarts the event");actor.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=50)
    public static void hitPresentationDoesNotPersistOrRefillAWeapon(GameTestHelper h){
        var actor=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());actor.setPos(h.absolutePos(new BlockPos(5,2,5)).getCenter());actor.setNoAi(true);actor.setPersistenceRequired();actor.initializeWeapon();h.getLevel().addFreshEntity(actor);
        h.runAtTickTime(10,()->{
            int magazine=actor.magazineAmmo(),reserve=actor.reserveAmmo();h.assertTrue(actor.hurt(h.getLevel().damageSources().generic(),1),"actual hit before save");
            var tag=new CompoundTag();actor.saveWithoutId(tag);var loaded=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());loaded.load(tag);
            h.assertTrue(loaded.hitStarted()<0&&loaded.magazineAmmo()==magazine&&loaded.reserveAmmo()==reserve,"load drops transient reaction and preserves finite equipment");
            actor.discard();loaded.discard();h.succeed();
        });
    }
}
