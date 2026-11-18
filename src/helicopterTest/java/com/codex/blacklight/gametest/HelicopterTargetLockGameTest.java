package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.aircraft.*;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Real connected seats, normal ticks, server lock acceptance and the same aim
 * conversion used by the client. Physical Tab dispatch/HUD need client review. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterTargetLockGameTest {
    @GameTest(template="helicopter_arena",batch="airTargetLockPilot",timeoutTicks=240)
    public static void emptyHandPilotLockDrivesActualOffAxisCannonHit(GameTestHelper h){manual(h,false,351008);}
    @GameTest(template="helicopter_arena",batch="airTargetLockCopilot",timeoutTicks=240)
    public static void emptyHandCopilotLockDrivesActualOffAxisCannonHit(GameTestHelper h){manual(h,true,352032);}
    private static void manual(GameTestHelper h,boolean copilot,int x){
        var f=new HelicopterPilotGameTest.Fixture(h,x);var craft=f.craft(12);
        var driver=HelicopterPilotGameTest.player(h,f,false);h.assertTrue(driver.startRiding(craft,true),"real driver boards");
        var operator=copilot?HelicopterPilotGameTest.player(h,f,false):driver;
        if(copilot)h.assertTrue(operator.startRiding(craft,true)&&craft.pilot().slot(operator.getUUID())==1,"real second player occupies copilot seat");
        h.assertTrue(operator.getMainHandItem().isEmpty()&&!com.codex.blacklight.power.PrototypeMetabolism.active(operator),"fixture relies on aircraft access, not a held gun or prototype mode");
        craft.combat().emptyPlayerStores();craft.combat().addPlayerAmmunition(HelicopterSupplies.CANNON,1);
        var target=ModEntities.COMMON_INFECTED.get().create(f.level);Vec3 anchor=new Vec3(x+8,81,18);
        target.setPos(anchor);target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);target.setHealth(500);f.entities.add(target);
        int[] time={0};long[] sequence={0};boolean[] inserted={false},done={false},accepted={false};float[] beforeShot={Float.NaN};
        h.onEachTick(()->{
            if(done[0])return;
            try{
            if(!inserted[0]){if(!f.level.isPositionEntityTicking(target.blockPosition()))return;h.assertTrue(f.level.addFreshEntity(target),"target insertion in ticking chunk");inserted[0]=true;}
            int t=++time[0];target.setPos(anchor);target.setDeltaMovement(Vec3.ZERO);
            if(t==100){
                h.assertTrue(ExplicitTarget.enabled(operator),"empty-handed pilot/copilot can enter the actual Tab gate");
                h.assertTrue(ExplicitTarget.candidates(operator,64).contains(target),"client candidate collection offers ground opponent");
                CombatTargeting.requestLock(operator,target.getId());
                h.assertTrue(CombatTargeting.lockedEntity(operator)==target,"same server request used by TargetLockPayload accepts target");
            }
            if (t >= 100) {
                // Embedded GameTest connections do not tick like a signed-in client.
                // Run the presentation hook that a normal player receives every tick.
                com.codex.blacklight.animation.PlayerActions.get().synchronizeMode(operator, false);
                h.assertTrue(CombatTargeting.lockedEntity(operator) == target,
                        "unarmed helicopter operator retains the same target after mode synchronization at " + t);
            }
            if(t>=100&&t%2==0){
                // The camera/player faces away; only the selected target can supply this aim.
                operator.setYRot(90);operator.setXRot(0);
                Vec3 freeAim=craft.weaponPoint(HelicopterArmament.GUN).add(-120,0,0);
                var aim=HelicopterManualAim.resolve(operator,craft,CombatTargeting.lockedSelection(operator),freeAim,64);
                h.assertTrue(aim.locked()&&aim.point().distanceToSqr(freeAim)>100,"client's shared aim path consumes the lock rather than the off-axis view ray");
                // Infected health units are multiplied on world join. Observe the
                // actual pre-shot health; 500 is the unscaled fixture attribute.
                if(t>=140&&Float.isNaN(beforeShot[0]))beforeShot[0]=target.getHealth();
                accepted[0]|=craft.pilot().accept(operator,craft.pilot().lease(operator.getUUID()),sequence[0]++,0,0,aim.yaw(),aim.pitch(),t>=140?VehicleControlAuthority.PRIMARY_FIRE:0);
            }
            var source=target.getLastDamageSource();
            if(source!=null&&source.getEntity()==operator&&source.getDirectEntity()==craft){
                String proof="seat="+craft.pilot().slot(operator.getUUID())+" tick="+t+" accepted="+accepted[0]+" before="+beforeShot[0]+" after="+target.getHealth()+" maximum="+target.getMaxHealth()+" alive="+target.isAlive()+" removed="+target.isRemoved()+" registered="+(f.level.getEntity(target.getUUID())==target)+" sourceOperator="+(source.getEntity()==operator)+" sourceCraft="+(source.getDirectEntity()==craft)+" shots="+craft.combat().gunShots()+" rounds="+craft.combat().rounds();
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_LOCK_HIT {}",proof);
                h.assertTrue(accepted[0]&&target.getHealth()<beforeShot[0]&&target.isAlive()&&!target.isRemoved()&&f.level.getEntity(target.getUUID())==target,"actual selected target receives the connected operator's cannon damage: "+proof);
                h.assertTrue(craft.combat().gunShots()==1&&craft.combat().rounds()==0,"one actual player round is consumed");
                done[0]=true;CombatTargeting.requestLock(operator,-1);f.close();h.succeed();
            }
            }catch(RuntimeException|Error failure){done[0]=true;CombatTargeting.requestLock(operator,-1);f.close();throw failure;}
        });
        h.runAfterDelay(220,()->{if(!done[0]){done[0]=true;String detail="seat="+craft.pilot().slot(operator.getUUID())+" enabled="+ExplicitTarget.enabled(operator)+" accepted="+accepted[0]+" shots="+craft.combat().shots()+" health="+target.getHealth()+" phase="+craft.phase()+" rotor="+craft.rotor()+" gun="+craft.gunYaw()+","+craft.gunPitch();CombatTargeting.requestLock(operator,-1);f.close();h.fail("locked cannon failed: "+detail);}});
    }

    @GameTest(template="helicopter_arena",batch="airTargetLockFoot",timeoutTicks=100)
    public static void onFootPrototypeAndRifleSelectAircraftSkinInsteadOfCrew(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,353056);var player=HelicopterPilotGameTest.player(h,f,true);
        h.runAfterDelay(25,()->{try(f){
            var craft=f.craft(8);var crew=f.crew(craft);player.setPos(f.x,81,20);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            h.assertTrue(ExplicitTarget.enabled(player),"ground prototype may lock aircraft without a firearm");
            var choices=ExplicitTarget.candidates(player,64);
            h.assertTrue(choices.contains(craft)&&crew.stream().noneMatch(choices::contains),"aircraft has one hull identity, without competing pilot or passengers");
            h.assertTrue(TargetSelection.fromHit(crew.get(0)).target()==craft,"crosshair on the pilot resolves to the aircraft for locking only");
            CombatTargeting.requestLock(player,crew.get(0).getId());
            h.assertTrue(CombatTargeting.lockedEntity(player)==craft&&CombatTargeting.locked(player)==null,"server also canonicalizes passenger lock requests to the nonliving hull");
            Vec3 point=ExplicitTarget.point(player,craft),nearest=HelicopterSkinContact.closestPoint(craft,player.getEyePosition());
            double actualDistance=player.getEyePosition().distanceToSqr(point),nearestDistance=player.getEyePosition().distanceToSqr(nearest);
            String skinProof="eye="+player.getEyePosition()+" selected="+point+" nearest="+nearest+" selectedDistanceSquared="+actualDistance+" nearestDistanceSquared="+nearestDistance+" craft="+craft.position();
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_LOCK_SKIN {}",skinProof);
            // Symmetric left/right airframe boxes can have equally near points.
            // Verify the minimum distance, not an arbitrary iteration-order tie.
            h.assertTrue(Math.abs(actualDistance-nearestDistance)<1e-6&&point.z>craft.getZ(),"uncovered frontal lock chooses the nearest nose skin, not the tail or driver: "+skinProof);
            h.assertTrue(HelicopterSkinContact.boxes(craft).stream().anyMatch(b->b.nearest(point).distanceToSqr(point)<1e-6),"HUD/firearm aim point lies on a real structural cuboid");
            var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);state.setPrototypeMode(false);ModAttachments.setData(player,ModAttachments.PLAYER_STATE,state);
            var rifle=FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id());var stack=rifle.getDefaultInstance();FirearmItem.store(stack,new AmmoState(30,0,0));player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            CombatTargeting.requestLock(player,craft.getId());h.assertTrue(CombatTargeting.lockedEntity(player)==craft,"ordinary ground rifle user can lock the same aircraft");
            player.setYRot(90);player.setXRot(0);float before=craft.health();
            h.assertTrue(rifle.use(f.level,player,InteractionHand.MAIN_HAND).getResult().consumesAction(),"actual rifle Item.use accepts off-axis shot");
            h.assertTrue(FirearmItem.state(player.getMainHandItem(),0).magazine()==29&&craft.health()<before,"actual rifle round hits the accepted mechanical aircraft");
            CombatTargeting.requestLock(player,-1);
        }h.succeed();});
    }

    @GameTest(template="helicopter_arena",batch="airTargetLockSafety",timeoutTicks=100)
    public static void ownCrewProtectedCrewCrashAndCoverCannotBecomeAircraftLocks(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,354080);var own=f.craft(0);var player=HelicopterPilotGameTest.player(h,f,false);player.startRiding(own,true);
        var copilot=HelicopterPilotGameTest.player(h,f,false);copilot.startRiding(own,true);
        h.runAfterDelay(25,()->{try(f){
            var target=f.craft(0);target.setPos(f.x+12,81,18);
            h.assertTrue(!ExplicitTarget.selectable(player,own)&&!ExplicitTarget.selectable(player,copilot),"own hull and own occupants are never lock candidates");
            CombatTargeting.requestLock(player,copilot.getId());h.assertTrue(CombatTargeting.lockedEntity(player)==null,"requesting own copilot cannot bypass own-aircraft rejection");
            CombatTargeting.requestLock(player,target.getId());h.assertTrue(CombatTargeting.lockedEntity(player)==target,"uncovered live target initially locks");
            Vec3 fallback=own.weaponPoint(HelicopterArmament.GUN).add(-40,0,0);var selected=new TargetSelection(target,-1);
            for(int x=-20;x<=30;x++)for(int y=81;y<=112;y++)f.level.setBlock(new BlockPos(f.x+x,y,8),Blocks.BEDROCK.defaultBlockState(),2);
            h.assertTrue(!selected.visibleWithinRange(player,64),"complete cover blocks all aircraft skin sightlines");
            var aim=HelicopterManualAim.resolve(player,own,selected,fallback,64);
            h.assertTrue(!aim.locked()&&aim.point().equals(fallback),"blocked stale lock falls back to crosshair, never through-wall aim");
            CombatTargeting.requestLock(player,target.getId());h.assertTrue(CombatTargeting.lockedEntity(player)==null,"server rejects covered aircraft");
            for(int x=-20;x<=30;x++)for(int y=81;y<=112;y++)f.level.setBlock(new BlockPos(f.x+x,y,8),Blocks.AIR.defaultBlockState(),2);
            var protectedCrew=HelicopterPilotGameTest.player(h,f,false);protectedCrew.setGameMode(GameType.CREATIVE);h.assertTrue(protectedCrew.startRiding(target,true),"protected real player occupies target aircraft");
            h.assertTrue(ExplicitTarget.resolve(protectedCrew)==target&&!ExplicitTarget.selectable(player,target),"crew canonicalization retains occupant/PvP protection");
            CombatTargeting.requestLock(player,protectedCrew.getId());h.assertTrue(CombatTargeting.lockedEntity(player)==null,"network passenger identity cannot evade protected aircraft policy");
            protectedCrew.stopRiding();CombatTargeting.requestLock(player,target.getId());h.assertTrue(CombatTargeting.lockedEntity(player)==target,"removing protection admits target again");
            target.setFlightPhase(BlackwatchHelicopter.Phase.CRASH);
            h.assertTrue(target.health()>0&&!ExplicitTarget.selectable(player,target)&&CombatTargeting.lockedEntity(player)==null,"positive-health crew-loss crash releases lock before entity deletion");
            own.setFlightPhase(BlackwatchHelicopter.Phase.CRASH);h.assertTrue(!ExplicitTarget.enabled(player),"crashing own aircraft cannot grant empty-handed lock access");
            CombatTargeting.requestLock(player,-1);
        }h.succeed();});
    }
}
