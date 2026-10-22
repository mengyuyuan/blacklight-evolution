package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ConsumeControlGameTest {
    private static ServerPlayer player(GameTestHelper h){
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)h.setBlock(x,1,z,Blocks.STONE);
        var profile=new GameProfile(UUID.randomUUID(),"consume-cc");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault()){
            @Override public void teleportTo(double x,double y,double z){setPos(x,y,z);}
        };
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for(int i=0;i<61;i++)p.tick();
        p.setPos(h.absoluteVec(new Vec3(3,2,3)));p.setYRot(0);p.setOnGround(true);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.activate(PowerForm.CLAWS);
        h.getLevel().addNewPlayer(p);p.setHealth(p.getMaxHealth());return p;
    }
    private static Mob spore(GameTestHelper h){
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("spore:inf_human"));
        var mob=(Mob)h.spawn(type,6,2,3);mob.setNoAi(true);return mob;
    }
    private static void pull(Mob attacker,ServerPlayer p){
        try {
            attacker.setTarget(p);
            var type=Class.forName("com.Harbinger.Spore.Sentities.AI.PullGoal");
            var goal=type.getConstructor(Mob.class,double.class,double.class).newInstance(attacker,12D,0D);
            type.getMethod("start").invoke(goal);
        }catch(ReflectiveOperationException e){throw new AssertionError("real Spore pull could not execute",e);}
    }
    private static void release(GameTestHelper h,ServerPlayer p){
        ConsumeSystem.cancel(p);p.setData(ModAttachments.BIOMASS_FORM,BiomassState.EMPTY);
        h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void realSporePullAndForcedGrabRespectConsume(GameTestHelper h){
        var p=player(h);var attacker=spore(h);
        pull(attacker,p);h.assertTrue(p.getDeltaMovement().lengthSqr()>0,"actual Spore pull moves an unprotected player");
        p.setDeltaMovement(Vec3.ZERO);
        p.setData(ModAttachments.CONSUMING,true);p.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.HOLD);
        pull(attacker,p);h.assertTrue(p.getDeltaMovement().lengthSqr()==0,"Spore cannot pull the carrier away from held prey");
        h.assertTrue(!p.startRiding(attacker,true),"forced passenger grab is rejected");
        p.push(2,1,0);p.knockback(1,1,0);
        h.assertTrue(p.getDeltaMovement().lengthSqr()==0,"both impulse and vanilla knockback are rejected");
        p.setDeltaMovement(.1,0,.2);
        h.assertTrue(p.getDeltaMovement().lengthSqr()>0,"holding still permits ordinary movement");
        p.setData(ModAttachments.CONSUMING,false);p.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.NONE);
        p.setDeltaMovement(Vec3.ZERO);pull(attacker,p);
        h.assertTrue(p.getDeltaMovement().lengthSqr()>0,"pull works again after release");
        // Also exercise the shared Entity hook with another kind of living entity.
        var rider=h.spawn(EntityType.COW,8,2,7);var mount=h.spawn(EntityType.MINECART,8,2,8);
        rider.setData(ModAttachments.CONSUMING,true);rider.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.HOLD);
        h.assertTrue(!rider.startRiding(mount,true),"protected entity rejects the real forced mount path");
        rider.setData(ModAttachments.CONSUMING,false);rider.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.NONE);
        h.assertTrue(rider.startRiding(mount,true),"real riding works again after release");rider.stopRiding();
        release(h,p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void committedMotionAndEffectsDoNotGrantGodMode(GameTestHelper h){
        var p=player(h);var attacker=spore(h);
        var stun=MobEffects.MOVEMENT_SLOWDOWN;
        h.assertTrue(p.addEffect(new MobEffectInstance(stun,200)),"control effect normally applies");
        p.setData(ModAttachments.CONSUMING,true);p.setData(ModAttachments.GRAB_STAGE,ConsumeVisual.CONSUME);
        ConsumeControl.releaseControls(p);
        h.assertTrue(!p.hasEffect(stun),"claim releases a pre-existing control effect");
        h.assertTrue(!p.addEffect(new MobEffectInstance(stun,200)),"control effect cannot replace the scene");
        h.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.POISON,200)),"non-control harmful effects still apply");
        p.setDeltaMovement(Vec3.ZERO);p.setDeltaMovement(new Vec3(1,2,3));p.setDeltaMovement(3,2,1);
        h.assertTrue(p.getDeltaMovement().lengthSqr()==0,"both direct velocity overloads respect scene ownership");
        try(var scope=ConsumeControl.motion(p)){p.setDeltaMovement(.2,0,.1);}
        h.assertTrue(p.getDeltaMovement().lengthSqr()>0,"authored scene motion is allowed");
        h.assertTrue(!ConsumeControl.permitsMotion(p),"motion permission is released after the scene scope");
        float before=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(attacker),1);
        h.assertTrue(p.getHealth()<before,"actor still takes real Spore attack damage");
        release(h,p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void fullGrabConsumeSurvivesRepeatedExternalControl(GameTestHelper h){
        var p=player(h);var attacker=spore(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);
        for(int i=0;i<50&&!ConsumeSystem.holding(p);i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}
        h.assertTrue(ConsumeSystem.holding(p),"real server claim reaches hold");
        ConsumeSystem.consume(p);h.assertTrue(ConsumeSystem.committed(p),"right click commits the real consume scene");
        for(int i=0;i<220&&!cow.isRemoved();i++){
            pull(attacker,p);p.push(2,1,0);p.startRiding(attacker,true);
            p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));
        }
        h.assertTrue(cow.isRemoved(),"repeated Spore control cannot prevent actual consume completion");
        h.assertTrue(!ConsumeControl.protectedEntity(p),"completion releases all temporary control protection");
        release(h,p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void biomassAbsorptionHasTheSameControlProtection(GameTestHelper h){
        var p=player(h);var attacker=spore(h);var cow=h.spawn(EntityType.COW,3,2,3);cow.setNoAi(true);
        p.setData(ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.ABSORBING,h.getLevel().getGameTime(),true,"","claws",cow.getId()));
        cow.addEffect(new MobEffectInstance(ModEffects.BIOMASS_CONTACT,100));
        p.setDeltaMovement(Vec3.ZERO);cow.setDeltaMovement(Vec3.ZERO);
        pull(attacker,p);p.setDeltaMovement(2,1,0);cow.push(2,1,0);cow.setDeltaMovement(new Vec3(1,2,0));
        h.assertTrue(p.getDeltaMovement().lengthSqr()==0&&cow.getDeltaMovement().lengthSqr()==0,"slime and claimed prey resist direct displacement");
        h.assertTrue(!p.startRiding(attacker,true)&&!cow.startRiding(attacker,true),"neither member can be stolen into a forced mount");
        p.setData(ModAttachments.BIOMASS_FORM,BiomassState.EMPTY);cow.removeEffect(ModEffects.BIOMASS_CONTACT);
        cow.push(1,0,0);h.assertTrue(cow.getDeltaMovement().lengthSqr()>0,"prey is fully released on cancel");
        release(h,p);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void biomassCanFinishRealSporePreyAndRestoreBody(GameTestHelper h){
        for(String id:new String[]{"spore:inf_human","spore:slasher"}){
            var p=player(h);long now=h.getLevel().getGameTime();
            var prey=(Mob)h.spawn(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)),3,2,3);prey.setNoAi(true);
            p.setData(ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.CRAWLING,now-20,true,"","claws",-1));
            BiomassSystem.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(BiomassSystem.state(p).phase()==BiomassState.ABSORBING,"real proximity claims "+id);
            p.setData(ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.ABSORBING,now-BiomassState.ABSORB_TICKS,true,"","claws",prey.getId()));
            BiomassSystem.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(BiomassSystem.state(p).consumed()&&!prey.isAlive(),"actual Spore death and consume commit complete for "+id);
            p.setData(ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.REFORMING,now-BiomassState.REFORM_TICKS,true,"","claws",-1));
            BiomassSystem.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(!BiomassSystem.active(p)&&!ConsumeControl.protectedEntity(p),"body restoration releases protection after "+id);
            release(h,p);
        }
        h.succeed();
    }
}
