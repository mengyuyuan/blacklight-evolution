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
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Website #9: exercise real Player.heal and the loader's registered heal event handlers. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FeedbackGameTest {
    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        var profile=new GameProfile(UUID.randomUUID(),"feedback-heal");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile);
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setGameMode(mode);
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);
        state.addEvolutionPoints(10000);state.buyCombatSkill(CombatSkill.CRITICAL_MASS);
        ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);
        GrowthSystem.updateHealth(p);
        p.setHealth(p.getMaxHealth());
        return p;
    }
    private static void consumeAtFullHealth(GameTestHelper h, ServerPlayer p) {
        float maximum=p.getMaxHealth();
        ModAttachments.setData(p,ModAttachments.CRITICAL_MASS,CriticalMassState.EMPTY);
        CriticalMassSystem.healFromConsume(p,maximum*.1F/CombatScale.units(p));
        h.assertTrue(Math.abs(ModAttachments.getData(p,ModAttachments.CRITICAL_MASS).reserve()-.22F)<.001F,
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
        h.assertTrue(ModAttachments.getData(p,ModAttachments.CRITICAL_MASS).reserve()==0,"passive healing does not create reserve");
        consumeAtFullHealth(h,p);
        p.removeAllEffects();p.setHealth(p.getMaxHealth());
        consumeAtFullHealth(h,p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void woundedConsumeOnlyStoresTheOverflow(GameTestHelper h) {
        var p=player(h,GameType.SURVIVAL);float maximum=p.getMaxHealth();
        p.setHealth(maximum*.95F);
        CriticalMassSystem.healFromConsume(p,maximum*.1F/CombatScale.units(p));
        h.assertTrue(Math.abs(ModAttachments.getData(p,ModAttachments.CRITICAL_MASS).reserve()-.11F)<.001F,
                "the red-health portion must not also be counted as reserve");h.succeed();
    }
}
