package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.power.PowerForm;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MechanicalTargetGameTest {
    @GameTest(template="blackwatch_arena",timeoutTicks=300,batch="mechanical_target")
    public static void realGunMeleeLocksAndBlackwatchImmunity(GameTestHelper h){
        var l=h.getLevel();var base=new BlockPos(52008,100,52008);var forced=new ArrayList<ChunkPos>();var actors=new ArrayList<Entity>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var c=new ChunkPos((base.getX()>>4)+x,(base.getZ()>>4)+z);if(!l.getForcedChunks().contains(c.toLong())){forced.add(c);l.setChunkForced(c.x,c.z,true);}l.getChunk(c.x,c.z);}
        for(int x=-12;x<=12;x++)for(int z=-12;z<=24;z++)l.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"MechanicalTargetQA"));p.setGameMode(GameType.SURVIVAL);p.moveTo(base.getX()+.5,100,base.getZ()+.5,0,0);p.setOnGround(true);l.addNewPlayer(p);actors.add(p);
        var tank=ModEntities.BLACKWATCH_TANK.get().create(l);tank.moveTo(p.getX(),100,p.getZ()+tank.definition().length()/2+1.5,0,0);l.addFreshEntity(tank);actors.add(tank);
        var rifle=FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id());var stack=rifle.getDefaultInstance();FirearmItem.store(stack,new AmmoState(30,0,0));p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(l);soldier.moveTo(p.getX()+8,100,p.getZ()+8,0,0);soldier.setNoAi(true);l.addFreshEntity(soldier);actors.add(soldier);
        var civilian=EntityType.ZOMBIE.create(l);civilian.setNoAi(true);civilian.moveTo(p.getX()-8,100,p.getZ()+8,0,0);l.addFreshEntity(civilian);actors.add(civilian);
        h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"mechanical arena actually ticks"))
        .thenExecute(()->{
            CombatTargeting.requestLock(p,tank.getId());h.assertTrue(CombatTargeting.lockedEntity(p)==tank,"ordinary gun user locks native tank");
            com.codex.blacklight.animation.PlayerActions.get().synchronizeMode(p,false);
            h.assertTrue(CombatTargeting.lockedEntity(p)==tank,"ordinary-mode synchronization preserves a gun user's accepted lock");
            h.assertTrue(CombatTargeting.locked(p)==null&&CombatTargeting.lockedEntity(p)==tank,"living-body query must not clear mechanical lock");
            p.setYRot(90);float before=tank.health();rifle.use(l,p,InteractionHand.MAIN_HAND);
            h.assertTrue(tank.health()<before,"actual right-click rifle takes tank health with off-axis accepted lock");
            h.assertTrue(FirearmItem.state(stack,0).magazine()==29,"lock shot consumes one actual round");
            p.getCooldowns().removeCooldown(rifle);for(int y=0;y<5;y++)l.setBlock(base.offset(0,y,1),Blocks.STONE.defaultBlockState(),3);
            before=tank.health();p.setYRot(0);FirearmsCombat.fire(p,WeaponCatalog.RIFLE,new Vec3(0,0,1),1);
            h.assertTrue(tank.health()==before,"real wall blocks vehicle damage");
            for(int y=0;y<5;y++)l.setBlock(base.offset(0,y,1),Blocks.AIR.defaultBlockState(),3);
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();
            for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.MUSCLE_MASS,PowerForm.WHIPFIST}){
                WeaponCombatSystem.cancel(p);state.unlock(form);state.setPrototypeMode(true);state.activate(form);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);
                p.setPos(base.getX()+.5,100,base.getZ()+.5);p.setYRot(90);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);
                CombatTargeting.requestLock(p,tank.getId());h.assertTrue(CombatTargeting.lockedEntity(p)==tank,"prototype locks native tank: "+form);
                float health=tank.health();WeaponCombatSystem.request(p,false);var move=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec();
                h.assertTrue(move!=null,"native weapon input starts an action: "+form);
                for(int tick=0;tick<move.duration+2;tick++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
                h.assertTrue(tank.health()<health,"authored contact window takes real tank health: "+form);
                h.assertTrue(health-tank.health()<=move.damageFor(form)*.5+.01,"one accepted mechanical contact per ordinary action: "+form);
                System.out.println("MECHANICAL_REAL_HIT form="+form+" from="+health+" to="+tank.health());
            }
            WeaponCombatSystem.cancel(p);CombatTargeting.requestLock(p,-1);
            h.assertTrue(!BlacklightInfectionSystem.infect(soldier,civilian),"Blackwatch rejects direct vial/contact infection");
            soldier.addEffect(new MobEffectInstance(ModEffects.BLACKLIGHT_INFECTION.get(),600));com.codex.blacklight.registry.ModAttachments.setData(soldier, ModAttachments.INFECTION_START,0L);BlacklightInfectionSystem.preserveBlackwatch(soldier);
            h.assertTrue(!BlacklightInfectionSystem.isInfected(soldier)&&!soldier.hasEffect(ModEffects.BLACKLIGHT_INFECTION.get()),"saved native Blackwatch loses stale infection, retaining real entity");
            h.assertTrue(BlacklightInfectionSystem.infect(civilian,soldier),"civilian infection remains available");
            civilian.discard();h.assertTrue(com.codex.blacklight.military.vehicle.VehicleDamageBridge.hurtSpecial(p,tank,tank.health()+1)
                    &&tank.health()==0&&tank.isRemoved(),"enough lawful mechanical damage destroys tank once");
            var common=ModEntities.COMMON_INFECTED.get().create(l);common.moveTo(soldier.getX(),100,soldier.getZ()+10,0,0);common.setNoAi(true);l.addFreshEntity(common);actors.add(common);
            soldier.setNoAi(false);soldier.initializeNativeLoadout("blackwatch-immunity-qa");soldier.setTarget(common);soldier.getPersistentData().putInt("QACommon",common.getId());
        }).thenIdle(160).thenExecute(()->{
            var common=l.getEntity(soldier.getPersistentData().getInt("QACommon"));h.assertTrue(common==null||!common.isAlive(),"real Blackwatch AI kills nearby ordinary infected");
            h.assertTrue(soldier.isAlive()&&!BlacklightInfectionSystem.isInfected(soldier),"Blackwatch remains operational and immune during cleanup");
            for(var e:actors){if(e==p)l.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);else e.discard();}for(var c:forced)l.setChunkForced(c.x,c.z,false);
            System.out.println("MECHANICAL_TARGET_PASS real rifle + five weapon forms, wall, tank lock, infection immunity and real AI cleanup");h.succeed();
        });
    }
}
