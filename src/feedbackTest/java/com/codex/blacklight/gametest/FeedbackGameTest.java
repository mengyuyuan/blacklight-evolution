package com.codex.blacklight.gametest;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Website #9/#12: real server-player healing, incoming damage and Devastator resource spending. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FeedbackGameTest {
    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        var profile=new GameProfile(UUID.randomUUID(),"feedback-heal");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setGameMode(mode);
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);
        state.addEvolutionPoints(10000);state.buyCombatSkill(CombatSkill.CRITICAL_MASS);
        p.setData(ModAttachments.PLAYER_STATE,state);
        GrowthSystem.updateHealth(p);
        p.setHealth(p.getMaxHealth());
        return p;
    }
    private static void consumeAtFullHealth(GameTestHelper h, ServerPlayer p) {
        float maximum=p.getMaxHealth();
        p.setData(ModAttachments.CRITICAL_MASS,CriticalMassState.EMPTY);
        CriticalMassSystem.healFromConsume(p,maximum*.1F/CombatScale.units(p));
        h.assertTrue(Math.abs(p.getData(ModAttachments.CRITICAL_MASS).reserve()-.22F)<.001F,
                "full-health consume adds 0.22 reserve through the real healing event");
        h.assertTrue(p.getHealth()==maximum,"overheal must not change normal maximum health");
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void creativeFullHealthConsumeCreatesReserve(GameTestHelper h) {
        consumeAtFullHealth(h,player(h,GameType.CREATIVE));h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void regenerationAndHealthBoostDoNotDisableLaterConsume(GameTestHelper h) {
        var p=player(h,GameType.SURVIVAL);
        p.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST,200,4));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,200,4));
        p.setHealth(p.getMaxHealth()-1);p.heal(1000);
        h.assertTrue(p.getData(ModAttachments.CRITICAL_MASS).reserve()==0,"passive healing does not create reserve");
        consumeAtFullHealth(h,p);
        p.removeAllEffects();p.setHealth(p.getMaxHealth());
        consumeAtFullHealth(h,p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void woundedConsumeOnlyStoresTheOverflow(GameTestHelper h) {
        var p=player(h,GameType.SURVIVAL);float maximum=p.getMaxHealth();
        p.setHealth(maximum*.95F);
        CriticalMassSystem.healFromConsume(p,maximum*.1F/CombatScale.units(p));
        h.assertTrue(Math.abs(p.getData(ModAttachments.CRITICAL_MASS).reserve()-.11F)<.001F,
                "the red-health portion must not also be counted as reserve");h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void devastatorPaysOnlyMissingReserve(GameTestHelper h) {
        var p=player(h,GameType.SURVIVAL);float maximum=p.getMaxHealth();
        for(float reserve:new float[]{.001F,.25F,1F,1.25F}) {
            p.setHealth(maximum);
            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(reserve,true));
            h.assertTrue(CriticalMassSystem.spend(p),"positive reserve permits release");
            float expected=maximum-Math.max(0,1-reserve)*maximum*.25F;
            h.assertTrue(Math.abs(p.getHealth()-expected)<.001F,"red health pays only the reserve deficit: "+reserve);
        }
        p.setData(ModAttachments.CRITICAL_MASS,CriticalMassState.EMPTY);
        float before=p.getHealth();
        h.assertTrue(!CriticalMassSystem.spend(p)&&p.getHealth()==before,"rejected release cannot charge red health");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void incomingDamageOnlyChargesOverflow(GameTestHelper h) {
        var p=player(h,GameType.SURVIVAL);
        // Advance the real spawn protection instead of bypassing damage events.
        for(int i=0;i<61;i++)p.tick();
        float maximum=p.getMaxHealth();
        for(float damageFraction:new float[]{.01F,.025F,.05F}) {
            p.setHealth(maximum);p.invulnerableTime=0;p.setInvulnerable(false);
            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(.1F,true));
            p.hurt(p.damageSources().generic(),maximum*damageFraction/CombatScale.units(p));
            float expected=maximum-Math.max(0,damageFraction-.025F)*maximum;
            h.assertTrue(Math.abs(p.getHealth()-expected)<.001F,"only overflow reaches red health: "+damageFraction+", actual="+p.getHealth()+", expected="+expected);
            float reserve=Math.max(0,.1F-damageFraction/.25F);
            h.assertTrue(Math.abs(p.getData(ModAttachments.CRITICAL_MASS).reserve()-reserve)<.001F,"the real damage pipeline must consume reserve");
        }
        h.succeed();
    }
}
