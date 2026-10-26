package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectionRules;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ConsumeOpportunityGameTest {
    private static ServerPlayer player(GameTestHelper h){
        var profile=new GameProfile(UUID.randomUUID(),"ConsumeOpening");var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;p.setPos(h.absolutePos(new BlockPos(5,28,5)).getCenter());p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.getLevel().addNewPlayer(p);
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);return p;
    }
    private static void clean(ServerPlayer p,Mob mob){ImpactControl.cancel(mob);mob.discard();p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    @GameTest(template="wall_test",timeoutTicks=80,batch="consume_opening")
    public static void acceptedWeakeningCreatesAFiniteOpeningWithoutChangingNoAi(GameTestHelper h){
        var p=player(h);var mob=EntityType.HUSK.create(h.getLevel());mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);mob.setHealth(16);mob.setPos(p.position().add(2,0,0));mob.setNoGravity(true);
        mob.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(mob);
        try{
            h.assertTrue(!ConsumeSystem.healthReady(p,mob),"sixteen percent remains outside the consume health threshold");
            h.assertTrue(mob.hurt(mob.damageSources().playerAttack(p),2),"actual player damage is accepted");
            h.assertTrue(ConsumeSystem.healthReady(p,mob)&&ImpactControl.stunned(mob)&&!mob.isNoAi(),"ready target gets temporary AI control, without a persisted NoAI flag");
            var first=mob.getData(ModAttachments.HIT_REACTION);h.assertTrue(first.duration()==40,"consume opening lasts two seconds");
            mob.invulnerableTime=0;mob.hurt(mob.damageSources().playerAttack(p),1);
            h.assertTrue(mob.getData(ModAttachments.HIT_REACTION).started()==first.started(),"subsequent hits do not restart the consume stun");
            h.runAfterDelay(45,()->{try{h.assertTrue(!ImpactControl.stunned(mob)&&!mob.isNoAi(),"AI recovers automatically after the brief opening");h.succeed();}finally{clean(p,mob);}});
        }catch(Throwable failure){clean(p,mob);throw failure;}
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_opening")
    public static void directlyConsumableAnimalsAreNotAutomaticallyStunnedAtFullHealth(GameTestHelper h){
        var p=player(h);var mob=EntityType.COW.create(h.getLevel());mob.setPos(p.position().add(2,0,0));mob.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(mob);
        try{h.assertTrue(ConsumeSystem.healthReady(p,mob),"weak ordinary animals remain directly consumable");mob.hurt(mob.damageSources().playerAttack(p),1);h.assertTrue(!ImpactControl.stunned(mob)&&!mob.isNoAi(),"direct consumption eligibility alone does not freeze healthy wildlife");}
        finally{clean(p,mob);}h.succeed();
    }
}
