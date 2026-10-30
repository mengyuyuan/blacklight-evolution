package com.codex.passives.gametest;

import com.codex.passives.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("adaptive_passives") @PrefixGameTestTemplate(false)
public final class PassiveCallbackGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var profile=new GameProfile(UUID.randomUUID(),"callback-qa");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setGameMode(GameType.SURVIVAL);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,2,2)));
        for(int i=0;i<65;i++)p.tick();
        p.setHealth(500);PassiveApi.grant(p,2,1);
        var d=PassiveData.read(p);d.regeneration=100;d.nextHeal=p.level().getGameTime()+20;d.save(p);
        return p;
    }
    @GameTest(template="empty",timeoutTicks=50,batch="passive_callback_grant")
    public static void healingDoesNotEraseAReentrantGrant(GameTestHelper h) {
        var p=player(h);Consumer<LivingHealEvent> listener=e->{if(e.getEntity()==p)PassiveApi.grant(p,1,2);};
        h.runAfterDelay(20,()->{
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                PassiveSystem.tick(p);var d=PassiveData.read(p);
                h.assertTrue(d.adaptationTier==2,"a grant inside LivingHealEvent survives the regeneration settlement");
                h.assertTrue(p.getHealth()==600&&d.regeneration==80,"exactly one ordinary regeneration pulse is charged");
                h.succeed();
            } finally {NeoForge.EVENT_BUS.unregister(listener);p.discard();}
        });
    }
    @GameTest(template="empty",timeoutTicks=50,batch="passive_callback_tick")
    public static void nestedTickCannotRepeatTheSameHealingPulse(GameTestHelper h) {
        var p=player(h);var calls=new AtomicInteger();
        Consumer<LivingHealEvent> listener=e->{if(e.getEntity()==p&&calls.incrementAndGet()==1)PassiveSystem.tick(p);};
        h.runAfterDelay(20,()->{
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                PassiveSystem.tick(p);var d=PassiveData.read(p);
                h.assertTrue(calls.get()==1&&p.getHealth()==600&&d.regeneration==80,
                        "reentrant tick cannot duplicate a due pulse: calls="+calls+" health="+p.getHealth()+" layers="+d.regeneration);
                h.succeed();
            } finally {NeoForge.EVENT_BUS.unregister(listener);p.discard();}
        });
    }
}
