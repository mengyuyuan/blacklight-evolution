package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.config.CrossModCombatConfig;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Execute the real combo contact selection and hurt call against the installed shield. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CultivationClawShieldGameTest {
    @GameTest(template="wall_test",timeoutTicks=160,batch="cultivation_claw_shield")
    public static void clawComboDoesNotBounceOffNpcShield(GameTestHelper h)throws Exception {
        var source=CultivationCompatibilityGameTest.class.getDeclaredMethod("source",GameTestHelper.class,String.class);source.setAccessible(true);
        var createPlayer=CultivationCompatibilityGameTest.class.getDeclaredMethod("player",GameTestHelper.class);createPlayer.setAccessible(true);
        var mob=(net.minecraft.world.entity.Mob)source.invoke(null,h,"wandering_cultivator");
        var player=(ServerPlayer)createPlayer.invoke(null,h);
        h.getLevel().addNewPlayer(player);
        var shield=Class.forName("com.xiaoxiang.cultivation.event.PersonalSpiritShieldHandler");
        boolean policy=CrossModCombatConfig.CULTIVATION_NATIVE_HITS.get();
        var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        var combat=ModAttachments.getData(player,ModAttachments.CLAW_COMBAT);
        var hit=ClawCombatSystem.class.getDeclaredMethod("performClawHit",ServerPlayer.class,state.getClass(),ClawCombatState.class);hit.setAccessible(true);
        try {
            mob.moveTo(player.position().add(0,0,1.8));player.setYRot(0);
            var spell=Class.forName("com.xiaoxiang.cultivation.cultivation.spell.Spell");
            Object personal=java.util.Arrays.stream(spell.getEnumConstants()).filter(v->((Enum<?>)v).name().equals("PERSONAL_SPIRIT_SHIELD")).findFirst().orElseThrow();
            mob.getClass().getMethod("forceLearnSpellForGameTest",spell).invoke(mob,personal);
            shield.getMethod("armOrdinaryShieldAndUpdateForGameTest",LivingEntity.class).invoke(null,mob);
            float maximum=mob.getMaxHealth();
            CrossModCombatConfig.CULTIVATION_NATIVE_HITS.set(false);
            combat.beginNextAttack();combat.setAttackDirection(0,1);player.setDeltaMovement(Vec3.ZERO);mob.invulnerableTime=0;
            hit.invoke(null,player,state,combat);
            float blocked=maximum-mob.getHealth();double bounced=player.getDeltaMovement().length();
            h.assertTrue(blocked==0&&bounced>.1,"baseline must reproduce shield stopping real claws and repelling attacker; loss="+blocked+" bounce="+bounced);
            CrossModCombatConfig.CULTIVATION_NATIVE_HITS.set(true);
            mob.setHealth(maximum);mob.invulnerableTime=0;player.setDeltaMovement(Vec3.ZERO);
            combat.reset();combat.beginNextAttack();combat.setAttackDirection(0,1);
            hit.invoke(null,player,state,combat);
            float lost=maximum-mob.getHealth();double fixedBounce=player.getDeltaMovement().length();
            System.out.println("CULT_CLAW_SHIELD blocked="+blocked+" bounce="+bounced+" fixedLoss="+lost+" fixedBounce="+fixedBounce);
            h.assertTrue(lost>0,"actual ordinary claw combo must damage shielded cultivation NPC");
            h.assertTrue(fixedBounce<.001,"our own hit must not trigger shield shockwave");
            for(int i=0;i<2;i++){
                mob.setHealth(maximum);mob.invulnerableTime=0;player.setDeltaMovement(Vec3.ZERO);
                combat.beginNextAttack();combat.setAttackDirection(0,1);hit.invoke(null,player,state,combat);
                h.assertTrue(mob.getHealth()<maximum&&player.getDeltaMovement().length()<.001,"each real combo branch must damage without repulsion");
            }
            state.setPrototypeMode(false);mob.setHealth(maximum);mob.invulnerableTime=0;player.setDeltaMovement(Vec3.ZERO);
            boolean accepted=mob.hurt(player.damageSources().playerAttack(player),20);
            h.assertTrue(!accepted&&mob.getHealth()==maximum,"ordinary non-Prototype player still respects shield");
            var canceled=new net.minecraftforge.event.entity.living.LivingAttackEvent(player,mob.damageSources().mobAttack(mob),20);
            h.assertTrue(!com.codex.blacklight.compat.CultivationCompatibility.bypassMobShield(canceled),"never bypass player shields");
            h.succeed();
        }finally{
            CrossModCombatConfig.CULTIVATION_NATIVE_HITS.set(policy);
            shield.getMethod("clearShieldForTest",LivingEntity.class).invoke(null,mob);
            mob.discard();h.getLevel().removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template="wall_test",timeoutTicks=160,batch="cultivation_weasel_claw")
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void infectedWeaselKeepsItsOriginalShieldButCanBeHit(GameTestHelper h)throws Exception{
        var source=CultivationCompatibilityGameTest.class.getDeclaredMethod("source",GameTestHelper.class,String.class);source.setAccessible(true);
        var createPlayer=CultivationCompatibilityGameTest.class.getDeclaredMethod("player",GameTestHelper.class);createPlayer.setAccessible(true);
        var mob=(net.minecraft.world.entity.Mob)source.invoke(null,h,"huangshulang");
        var player=(ServerPlayer)createPlayer.invoke(null,h);h.getLevel().addNewPlayer(player);
        var shield=Class.forName("com.xiaoxiang.cultivation.event.PersonalSpiritShieldHandler");
        boolean policy=CrossModCombatConfig.CULTIVATION_NATIVE_HITS.get();
        try{
            var realm=Class.forName("com.xiaoxiang.cultivation.cultivation.realm.Realm");
            mob.getClass().getMethod("initializeRealm",realm).invoke(mob,Enum.valueOf((Class)realm,"TRUE_IMMORTAL"));
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION.get(),Integer.MAX_VALUE));
            com.codex.blacklight.infection.NativeInfection.tick(mob,h.getLevel());
            var id=mob.getUUID();mob.moveTo(player.position().add(0,0,1.8));
            float maximum=mob.getMaxHealth();
            CrossModCombatConfig.CULTIVATION_NATIVE_HITS.set(false);
            boolean old=mob.hurt(player.damageSources().playerAttack(player),20);
            // Beast Qi shields may absorb only a fraction, unlike the fully armed NPC fixture.
            System.out.println("CULT_WEASEL_BASELINE accepted="+old+" hpLoss="+(maximum-mob.getHealth()));
            mob.setHealth(maximum);
            CrossModCombatConfig.CULTIVATION_NATIVE_HITS.set(true);mob.invulnerableTime=0;player.setDeltaMovement(Vec3.ZERO);
            int qiBefore=((Number)mob.getClass().getMethod("getCurrentQi").invoke(mob)).intValue();
            var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
            var combat=ModAttachments.getData(player,ModAttachments.CLAW_COMBAT);combat.beginNextAttack();combat.setAttackDirection(0,1);
            var hit=ClawCombatSystem.class.getDeclaredMethod("performClawHit",ServerPlayer.class,state.getClass(),ClawCombatState.class);hit.setAccessible(true);
            hit.invoke(null,player,state,combat);
            h.assertTrue(mob.getHealth()<maximum&&player.getDeltaMovement().length()<.001,"real claw combo damages infected weasel without shield bounce");
            h.assertTrue(mob.getUUID().equals(id)&&com.codex.blacklight.infection.NativeInfection.active(mob),"infection remains on the same native weasel");
            h.assertTrue(qiBefore==((Number)mob.getClass().getMethod("getCurrentQi").invoke(mob)).intValue(),"native hit must not erase or rewrite the beast's cultivation capability");
            System.out.println("CULT_WEASEL_CLAW hpLoss="+(maximum-mob.getHealth())+" originalType="+mob.getType()+" bounce="+player.getDeltaMovement().length());
            h.succeed();
        }finally{
            CrossModCombatConfig.CULTIVATION_NATIVE_HITS.set(policy);shield.getMethod("clearShieldForTest",LivingEntity.class).invoke(null,mob);
            mob.discard();h.getLevel().removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }
}
