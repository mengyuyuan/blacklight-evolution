package com.codex.blacklight.gametest;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.combat.CreatureAttack;
import com.codex.blacklight.movement.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CreatureCombatGameTest {
    private static void floor(GameTestHelper h){for(int x=1;x<12;x++)for(int z=1;z<12;z++)h.setBlock(x,1,z,Blocks.STONE);}
    @GameTest(template="wall_test",timeoutTicks=60)
    public static void hunterResistsLightStunlockButHeavyHitsStillInterrupt(GameTestHelper h){
        floor(h);var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),4,2,4);
        hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(1);
        var light=new com.codex.blacklight.combat.ImpactProfile(com.codex.blacklight.combat.HitReaction.STAGGER,0,8,.1,0,false);
        var heavy=new com.codex.blacklight.combat.ImpactProfile(com.codex.blacklight.combat.HitReaction.HEAVY,5,40,.5,.2,true);
        com.codex.blacklight.combat.ImpactControl.begin(hunter,light,0);
        h.assertTrue(com.codex.blacklight.combat.ImpactControl.stunned(hunter),"first light hit can interrupt");
        h.runAfterDelay(10,()->{
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.stunned(hunter),"light hit releases AI promptly");
            com.codex.blacklight.combat.ImpactControl.begin(hunter,light,0);
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.stunned(hunter),"repeat light hit cannot renew stunlock");
            float health=hunter.getHealth();hunter.hurt(hunter.damageSources().generic(),1);
            h.assertTrue(hunter.getHealth()<health,"stagger resistance grants no damage immunity");
            com.codex.blacklight.combat.ImpactControl.begin(hunter,heavy,0);
            h.assertTrue(com.codex.blacklight.combat.ImpactControl.stunned(hunter),"heavy knockdown remains effective during resistance");
            com.codex.blacklight.combat.ImpactControl.cancel(hunter);
        });
        h.runAfterDelay(34,()->{
            com.codex.blacklight.combat.ImpactControl.begin(hunter,light,0);
            h.assertTrue(com.codex.blacklight.combat.ImpactControl.stunned(hunter),"light interrupt becomes available again");
            hunter.discard();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void hunterBalanceMigratesWoundedStockMobsOnlyOnce(GameTestHelper h){
        var hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());
        var health=net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
        var damage=net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
        hunter.getAttribute(health).setBaseValue(48);hunter.getAttribute(damage).setBaseValue(7);
        com.codex.blacklight.power.CombatScale.synchronizeMob(hunter);hunter.setHealth(hunter.getMaxHealth()*.25F);
        var tag=new net.minecraft.nbt.CompoundTag();hunter.addAdditionalSaveData(tag);tag.remove("BlacklightHunterBalanceVersion");
        var copy=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());copy.readAdditionalSaveData(tag);
        h.assertTrue(copy.getMaxHealth()==480&&copy.getHealth()==120,"old wounded hunter doubles max HP without healing its health fraction");
        h.assertTrue(!com.codex.blacklight.combat.ConsumeSystem.healthReady(copy),"elite cannot be swallowed above the weakening threshold");
        copy.setHealth(72);
        h.assertTrue(com.codex.blacklight.combat.ConsumeSystem.healthReady(copy),"15 percent health retains the consume reward window");
        copy.setHealth(120);
        var saved=new net.minecraft.nbt.CompoundTag();copy.addAdditionalSaveData(saved);copy.readAdditionalSaveData(saved);
        h.assertTrue(copy.getMaxHealth()==480&&copy.getHealth()==120,"reload must not multiply stats again");
        hunter.getAttribute(health).setBaseValue(160);hunter.getAttribute(damage).setBaseValue(20);
        hunter.addAdditionalSaveData(tag);tag.remove("BlacklightHunterBalanceVersion");copy.readAdditionalSaveData(tag);
        h.assertTrue(copy.getAttribute(health).getBaseValue()==160&&copy.getAttribute(damage).getBaseValue()==20,"custom attributes are preserved");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void hunterDamageUsesMoveBudgetAndDoesNotBuffOrdinaryMobs(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HunterBudgetQA"));
        p.moveTo(h.absolutePos(new BlockPos(5,3,4)).getCenter());
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),4,3,4);
        h.assertTrue(hunter.beginAttack(CreatureAttack.SLAM,p),"slam can be selected");
        var source=p.damageSources().mobAttack(hunter);
        var ordinary=damageEvent(p,source,14);
        h.assertTrue(Math.abs(ordinary.getAmount()-13.44F)<.001,"ordinary players get the reduced duel damage");
        com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.PLAYER_STATE).infect();
        float difficulty=switch(p.level().getDifficulty()){case EASY->.75F;case HARD->1.5F;default->1;};
        float expected=com.codex.blacklight.combat.HunterBalance.infectedDamage(22.4F,CreatureAttack.SLAM,difficulty);
        h.assertTrue(Math.abs(damageEvent(p,source,14).getAmount()-expected)<.001,"registered handler applies hunter budget exactly once");
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(Math.abs(damageEvent(p,p.damageSources().mobAttack(zombie),3).getAmount()-.9F)<.001,"ordinary mob damage is unchanged");
        hunter.discard();h.succeed();
    }
    private static net.minecraftforge.event.entity.living.LivingHurtEvent damageEvent(
            net.minecraft.world.entity.LivingEntity p,net.minecraft.world.damagesource.DamageSource source,float amount){
        var event=new net.minecraftforge.event.entity.living.LivingHurtEvent(p,source,amount);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);return event;
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void hunterDuelLeavesOtherTargetsAndPlayerProgressionIntact(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HunterDuelQA"));
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),4,3,4);
        var cow=EntityType.COW.create(h.getLevel());
        cow.moveTo(hunter.position().add(1,0,0));
        var zombie=EntityType.ZOMBIE.create(h.getLevel());
        var playerAttack=p.damageSources().playerAttack(p);
        h.assertTrue(damageEvent(hunter,playerAttack,8).getAmount()==6,"only player attacks on hunters receive the duel modifier");
        h.assertTrue(damageEvent(hunter,playerAttack,16).getAmount()==12,"doubling upgraded attack damage still doubles damage dealt");
        h.assertTrue(damageEvent(cow,playerAttack,8).getAmount()==8,"player damage against other mobs is unchanged");
        h.assertTrue(damageEvent(hunter,hunter.damageSources().mobAttack(zombie),8).getAmount()==8,"other mobs hitting hunters are unchanged");
        h.assertTrue(damageEvent(hunter,hunter.damageSources().lava(),8).getAmount()==8,"environment damage is unchanged");
        h.assertTrue(damageEvent(hunter,hunter.damageSources().fellOutOfWorld(),1000).getAmount()==1000,"bypass damage remains intact");
        var arrow=EntityType.ARROW.create(h.getLevel());arrow.setOwner(p);
        h.assertTrue(damageEvent(hunter,p.damageSources().arrow(arrow,p),8).getAmount()==6,"player-owned projectiles use the same encounter rule");
        h.assertTrue(hunter.getMaxHealth()==480,"hunter health stays fixed rather than following player upgrades");
        h.assertTrue(hunter.beginAttack(CreatureAttack.SLAM,cow),"hunter can target another mob");
        h.assertTrue(Math.abs(damageEvent(cow,cow.damageSources().mobAttack(hunter),14).getAmount()-22.4F)<.001,"hunter damage against other mobs is unchanged");
        hunter.setNoAi(true);
        float before=hunter.getHealth();
        hunter.hurt(playerAttack,8);
        h.assertTrue(Math.abs(before-hunter.getHealth()-30)<.001,"real HP loss applies the duel modifier and infected units exactly once");
        hunter.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void interruptedBarrageDoesNotDeliverQueuedHits(GameTestHelper h){
        floor(h);var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),4,2,4);var cow=h.spawn(EntityType.COW,5,2,4);cow.setNoAi(true);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
        h.assertTrue(hunter.beginAttack(CreatureAttack.BARRAGE,cow),"barrage must start");
        h.runAfterDelay(5,()->{h.assertTrue(cow.getHealth()==100,"windup does not damage");hunter.setNoAi(true);});
        h.runAfterDelay(50,()->{h.assertTrue(!hunter.committedAttack()&&cow.getHealth()==100,"interrupt cancels every pending hit");hunter.discard();cow.discard();h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void hunterCannotHitThroughWallAddedDuringWindup(GameTestHelper h){
        floor(h);var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),4,2,4);var cow=h.spawn(EntityType.COW,6,2,4);cow.setNoAi(true);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
        h.assertTrue(hunter.beginAttack(CreatureAttack.SWIPE,cow),"swipe starts with line of sight");
        for(int y=2;y<7;y++)for(int z=2;z<8;z++)h.setBlock(5,y,z,Blocks.STONE);
        h.runAfterDelay(30,()->{h.assertTrue(cow.getHealth()==100,"new wall blocks committed strike");hunter.discard();cow.discard();h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void hunterLeapMovesCollisionBody(GameTestHelper h){
        floor(h);var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),3,2,4);var cow=h.spawn(EntityType.COW,9,2,4);cow.setNoAi(true);
        var start=hunter.position();h.assertTrue(hunter.beginAttack(CreatureAttack.LEAP,cow),"leap starts");
        h.runAfterDelay(15,()->h.assertTrue(hunter.position().distanceTo(start)>1,"leap must move its collision body"));
        h.runAfterDelay(50,()->{hunter.discard();cow.discard();h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void tacticalHopIsDirectionalRateLimitedWithBoundedInvulnerability(GameTestHelper h){
        floor(h);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HopQA"));
        p.moveTo(h.absolutePos(new BlockPos(4,2,4)).getCenter());p.setOnGround(true);
        var s=com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);
        var movement=com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.MOVEMENT_STATE);movement.setCameraYaw(0);
        PrototypeMovementSystem.acceptInput(p,false,true,0,1,false,false);
        h.assertTrue(p.getDeltaMovement().x<-.5&&p.getDeltaMovement().y>0&&p.getDeltaMovement().y<.2,"dodge travels sideways with low rise, not vanilla jump height");
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p,ModAttachments.PLAYER_ACTION).clipId().equals("basic_dodge_roll"),"server publishes original dodge roll pose");
        h.assertTrue(movement.hopInvulnerable()&&!p.getAbilities().invulnerable&&p.invulnerableTime==0,"hop uses a bounded window, not persistent invulnerability");
        h.assertTrue(incoming(p,p.damageSources().generic()).isCanceled(),"early hop cancels incoming damage on the registered event bus");
        h.assertTrue(!incoming(p,p.damageSources().fellOutOfWorld()).isCanceled(),"void damage still honors Minecraft bypass rules");
        p.setOnGround(true);p.setDeltaMovement(0,0,0);
        h.assertTrue(incoming(p,p.damageSources().generic()).isCanceled(),"grounded roll retains only the remaining bounded window");
        p.setOnGround(false);for(int i=0;i<6;i++)movement.tickHop();
        h.assertTrue(!incoming(p,p.damageSources().generic()).isCanceled(),"recovery is vulnerable");
        p.setOnGround(true);
        PrototypeMovementSystem.acceptInput(p,false,true,1,0,false,false);
        h.assertTrue(p.getDeltaMovement().lengthSqr()==0,"repeated packet cannot bypass cooldown");
        s.setPrototypeMode(false);movement.reset();
        PrototypeMovementSystem.acceptInput(p,false,true,1,0,false,false);
        h.assertTrue(!movement.hopping(),"ordinary mode cannot hop");h.succeed();
    }
    private static net.minecraftforge.event.entity.living.LivingAttackEvent incoming(
            net.minecraft.server.level.ServerPlayer p,net.minecraft.world.damagesource.DamageSource source){
        var event=new net.minecraftforge.event.entity.living.LivingAttackEvent(p,source,4);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);return event;
    }
}
