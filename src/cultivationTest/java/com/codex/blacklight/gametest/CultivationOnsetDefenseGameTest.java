package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CultivationOnsetDefenseGameTest {
    @GameTest(template="wall_test",timeoutTicks=80,batch="cult_incoming_onset")
    public static void onsetStillHasQiButConvertedHostDoesNotInheritPermanentShield(GameTestHelper h)throws Exception{
        var sourceMethod=CultivationCompatibilityGameTest.class.getDeclaredMethod("source",GameTestHelper.class,String.class);sourceMethod.setAccessible(true);
        var playerMethod=CultivationIncomingGameTest.class.getDeclaredMethod("player",GameTestHelper.class);playerMethod.setAccessible(true);
        var p=(ServerPlayer)playerMethod.invoke(null,h);var npc=(Mob)sourceMethod.invoke(null,h,"wandering_cultivator");BlacklightInfectedHost converted=null;
        try{
            npc.setNoGravity(true);npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);npc.setHealth(npc.getMaxHealth());
            h.assertTrue(BlacklightInfectionSystem.infect(npc,null),"native cultivator enters actual infection onset");
            float health=npc.getHealth();long qi=((Number)npc.getClass().getMethod("getCurrentQi").invoke(npc)).longValue();
            npc.hurt(p.damageSources().playerAttack(p),100);float shieldLoss=health-npc.getHealth();long qiAfter=((Number)npc.getClass().getMethod("getCurrentQi").invoke(npc)).longValue();
            npc.getClass().getMethod("setCombatQiForGameTest",long.class,long.class).invoke(npc,0L,1000000L);npc.invulnerableTime=0;health=npc.getHealth();npc.hurt(p.damageSources().playerAttack(p),100);float noQiLoss=health-npc.getHealth();
            converted=ModEntities.INFECTED_HOST.get().create(h.getLevel());converted.inherit(npc);converted.setNoGravity(true);converted.setNoAi(true);converted.setPos(npc.position().add(6,0,0));h.getLevel().addFreshEntity(converted);converted.setHealth(converted.getMaxHealth());
            health=converted.getHealth();converted.hurt(p.damageSources().playerAttack(p),100);float convertedLoss=health-converted.getHealth();
            var result=Map.of("onsetHpLossWithQi",shieldLoss,"qiBefore",qi,"qiAfter",qiAfter,"onsetHpLossWithoutQi",noQiLoss,"convertedHpLoss",convertedLoss,"convertedMaxHp",converted.getMaxHealth());
            System.out.println("CULTIVATION_INCOMING onset "+result);java.nio.file.Files.writeString(java.nio.file.Path.of("cultivation-onset.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            h.assertTrue(shieldLoss==0&&qiAfter<qi,"onset shield legitimately consumes Qi instead of health");
            h.assertTrue(noQiLoss>0&&convertedLoss>0,"draining Qi and finishing conversion both allow real HP damage");
        }finally{npc.discard();if(converted!=null)converted.discard();p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}h.succeed();
    }
}
