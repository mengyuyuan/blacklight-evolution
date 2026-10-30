package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectionRules;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CombatContinuationAuditGameTest {
    @GameTest(template="wall_test",timeoutTicks=50,batch="audit_mutant_death")
    public static void aDeadSpitterCannotFinishItsPendingAttack(GameTestHelper h){spitter(h,true);}
    @GameTest(template="wall_test",timeoutTicks=50,batch="audit_mutant_noai")
    public static void aDisabledSpitterCannotFinishItsPendingAttack(GameTestHelper h){spitter(h,false);}
    private static void spitter(GameTestHelper h,boolean dead){
        var p=MovingCombatGameTest.player(h,PowerForm.CLAWS);p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(5,180,5)));
        var body=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_SPITTER.get().create(h.getLevel());
        body.setPos(p.position().add(0,0,4));body.setNoGravity(true);h.getLevel().addFreshEntity(body);body.setTarget(p);
        try{
            var goal=body.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g.getClass().getSimpleName().equals("AssaultGoal")).findFirst().orElseThrow();
            h.assertTrue(goal.canUse(),"the actual spitter goal has a live target");goal.tick();
            if(dead){body.setHealth(0);body.die(body.damageSources().genericKill());}else body.setNoAi(true);
            for(int i=0;i<17;i++)body.tick();
            var shots=h.getLevel().getEntitiesOfClass(com.codex.blacklight.entity.BlacklightSpit.class,body.getBoundingBox().inflate(20),s->s.getOwner()==body);
            h.assertTrue(shots.isEmpty(),"death/NoAI stops the pending source attack rather than firing from a corpse");
            h.succeed();
        }finally{
            for(var shot:h.getLevel().getEntitiesOfClass(com.codex.blacklight.entity.BlacklightSpit.class,body.getBoundingBox().inflate(30),s->s.getOwner()==body))shot.discard();
            body.discard();p.discard();
        }
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="audit_melee_death")
    public static void aFatalHitCallbackStopsTheRemainingMeleeAndHammerSplash(GameTestHelper h){check(h,true);}
    @GameTest(template="wall_test",timeoutTicks=50,batch="audit_melee_form")
    public static void aFormChangeInTheHitCallbackCannotContinueTheOldWeapon(GameTestHelper h){check(h,false);}
    private static void check(GameTestHelper h,boolean fatal){
        var p=MovingCombatGameTest.player(h,PowerForm.HAMMERFISTS);p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(5,180,5)));p.setOnGround(true);
        var floor=p.blockPosition().below();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        var primary=EntityType.COW.create(h.getLevel());var other=EntityType.COW.create(h.getLevel());
        for(var mob:new Mob[]{primary,other}){
            mob.setNoAi(true);mob.setNoGravity(true);mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);mob.setHealth(10000);
            mob.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        }
        primary.setPos(p.position().add(0,0,1.3));other.setPos(p.position().add(.7,0,1.3));h.getLevel().addFreshEntity(primary);h.getLevel().addFreshEntity(other);
        boolean[] callback={false};
        Consumer<LivingDamageEvent.Post> listener=e->{if(e.getEntity()!=primary||e.getNewDamage()<=0)return;callback[0]=true;
            if(fatal){p.setHealth(0);p.die(p.damageSources().genericKill());}
            else p.getData(ModAttachments.PLAYER_STATE).activate(PowerForm.NONE);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try{
            WeaponCombatSystem.request(p,false);
            for(int t=0;t<50&&!callback[0]&&WeaponCombatSystem.busy(p);t++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
            h.assertTrue(callback[0]&&primary.getHealth()<10000,"the first real authored contact must be accepted");
            h.assertTrue(other.getHealth()==10000,"a dead/disarmed actor cannot hurt another victim with the stale melee session: hp="+other.getHealth());
            h.succeed();
        }finally{NeoForge.EVENT_BUS.unregister(listener);WeaponCombatSystem.cancel(p);primary.discard();other.discard();p.discard();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)h.getLevel().setBlock(floor.offset(x,0,z),Blocks.AIR.defaultBlockState(),2);}
    }
}
