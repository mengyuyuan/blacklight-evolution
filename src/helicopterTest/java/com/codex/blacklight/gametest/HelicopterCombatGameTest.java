package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterCombatGameTest {
    private record LongSortie(HelicopterPilotGameTest.Fixture area,BlacklightCommonInfected target,BlackwatchHelicopter craft,Vec3[] anchor){}
    private static LongSortie longSortie(GameTestHelper h,int index){
        var area=new HelicopterPilotGameTest.Fixture(h,264000+index*512);var level=area.level;var at=new Vec3(area.x+12,81,0);
        var target=ModEntities.COMMON_INFECTED.get().create(level);target.setPos(at);target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);target.setHealth(4000);level.addFreshEntity(target);area.entities.add(target);
        var craft=area.craft(16);var crew=area.crew(craft);for(int i=0;i<crew.size();i++)if(i<2)crew.get(i).setNoAi(false);else crew.get(i).discard();
        var caller=ModEntities.BLACKWATCH_OFFICER.get().create(level);caller.setPos(area.x-12,81,0);caller.setAutomaticUnitJoin(false);level.addFreshEntity(caller);caller.setTarget(target);
        h.assertTrue(craft.deployTo(new Vec3(area.x,81,12)),"isolated long source engagement");craft.combat().supportCall(caller);caller.discard();
        var tag=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());craft.load(tag);
        Vec3[] anchor={at};h.onEachTick(()->{if(!target.isRemoved()){target.setPos(anchor[0]);target.setDeltaMovement(Vec3.ZERO);}});return new LongSortie(area,target,craft,anchor);
    }
    private static void arena(GameTestHelper h){
        Vec3 center=h.absoluteVec(new Vec3(24,12,16));
        for(var entity:h.getLevel().getEntitiesOfClass(Entity.class,new AABB(center,center).inflate(128),e->!(e instanceof net.minecraft.world.entity.player.Player)))entity.discard();
        for(int x=0;x<48;x++)for(int z=0;z<32;z++)h.setBlock(x,1,z,Blocks.SMOOTH_STONE);
    }
    private static BlacklightCommonInfected enemy(GameTestHelper h){
        var target=ModEntities.COMMON_INFECTED.get().create(h.getLevel());target.setPos(h.absoluteVec(new Vec3(35,2,16)));target.setNoAi(true);target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);target.setHealth(400);h.getLevel().addFreshEntity(target);return target;
    }
    private static BlackwatchHelicopter sortie(GameTestHelper h,LivingEntity opponent){
        var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());craft.setPos(h.absoluteVec(new Vec3(10,18,16)));h.getLevel().addFreshEntity(craft);
        for(int i=0;i<2;i++){
            var pilot=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());pilot.setPos(craft.position());pilot.setAutomaticUnitJoin(false);h.getLevel().addFreshEntity(pilot);pilot.startRiding(craft,true);
        }
        var caller=ModEntities.BLACKWATCH_OFFICER.get().create(h.getLevel());caller.setPos(h.absoluteVec(new Vec3(5,2,5)));caller.setAutomaticUnitJoin(false);h.getLevel().addFreshEntity(caller);caller.setTarget(opponent);
        h.assertTrue(craft.deployTo(h.absoluteVec(new Vec3(24,2,16))),"supported mission");craft.combat().supportCall(caller);caller.discard();
        // Begin after troop delivery, already covered by the separate real-transport scenario.
        var tag=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());craft.load(tag);return craft;
    }
    @GameTest(template="helicopter_arena",batch="airCombatShot",timeoutTicks=650)
    public static void PursuitStrafeAndRealWeaponsDamageTheTargetAndPreservePlayerStores(GameTestHelper h){
        var fixture=longSortie(h,0);var target=fixture.target();var craft=fixture.craft();Vec3 start=craft.position();boolean[] rocketDamage={false};
        h.onEachTick(()->{if(target.getLastDamageSource()!=null&&target.getLastDamageSource().getDirectEntity() instanceof com.codex.blacklight.firearms.HellfireBlastEntity
                &&target.getLastDamageSource().getEntity() instanceof MilitarySoldier operator&&operator.getVehicle()==craft&&craft.combat().rocketShots()>0)rocketDamage[0]=true;});
        h.succeedWhen(()->{
            h.assertTrue(craft.combat().gunShots()>0&&craft.combat().rocketShots()>0,"both mounted weapons fired: "+craft.phase()+" rounds="+craft.combat().rounds()+" rockets="+craft.combat().rockets()+" yaw="+craft.gunYaw()+" pitch="+craft.gunPitch());
            h.assertTrue(rocketDamage[0]&&target.getHealth()<target.getMaxHealth()-4,"real projectile and gun damage accepted: health="+target.getHealth()+" max="+target.getMaxHealth()+" rocket="+rocketDamage[0]);
            h.assertTrue(craft.position().distanceTo(start)>2&&craft.phase()==BlackwatchHelicopter.Phase.STRAFE,"real pursuit and strafing displacement");
            var tag=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(tag);var restored=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.combat().rounds()==craft.combat().rounds()&&restored.combat().rockets()==craft.combat().rockets(),"save/load cannot refill weapons");
            h.assertTrue(craft.combat().rounds()==240&&craft.combat().rockets()==8,"AI leaves player profile inventory untouched");
            fixture.area().close();
        });
    }
    @GameTest(template="helicopter_arena",batch="airCombatFriend",timeoutTicks=650)
    public static void TroopsBesideTheEnemySuppressRocketsButAllowAClearGunShot(GameTestHelper h){
        var fixture=longSortie(h,1);var target=fixture.target();var craft=fixture.craft();
        var ally=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());ally.setNoAi(true);ally.setPos(target.position().add(0,0,2));h.getLevel().addFreshEntity(ally);float health=ally.getHealth();
        fixture.area().entities.add(ally);
        boolean[] hovered={false};h.onEachTick(()->{h.assertTrue(!target.isRemoved(),"persistent nearby-friend target remains registered: reason="+target.getRemovalReason());hovered[0]|=craft.combat().hellfireAttack().hovering();});
        // Remote forced chunks may start entity ticks later than the GameTest clock.
        // Let the original 15-second avoid expire before checking the MC safety
        // preflight. An impossible explosive shot must not silence the chin gun.
        h.succeedWhen(()->{
            var state=new net.minecraft.nbt.CompoundTag();craft.combat().save(state);
            h.assertTrue(craft.tickCount>=420&&craft.combat().hellfireAttack().ready()&&craft.combat().gunShots()>3,"source avoid expired and clear gun ray remains active: guns="+craft.combat().gunShots()+" phase="+craft.phase()+" craftTick="+craft.tickCount+" position="+craft.position()+" state="+state);
            h.assertTrue(!hovered[0]&&craft.combat().rocketShots()==0&&ally.getHealth()==health,"nearby friendly prevents an unusable hover branch and explosive damage");fixture.area().close();
        });
    }
    @GameTest(template="helicopter_arena",batch="airCombatWall",timeoutTicks=150)
    public static void OpaqueCoverPreventsAcquisitionAndWeaponsDoNotShootThroughIt(GameTestHelper h){
        arena(h);var target=enemy(h);var craft=sortie(h,target);
        for(int y=2;y<29;y++)for(int z=0;z<32;z++)h.setBlock(20,y,z,Blocks.STONE);
        h.runAfterDelay(115,()->{h.assertTrue(craft.combat().shots()==0&&target.getHealth()==target.getMaxHealth(),"no acquisition or shooting through a wall");
            h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.RETURN,"abandon unavailable target");h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airCombatCivilian",timeoutTicks=150)
    public static void SupportDoesNotAcquireUnrelatedWildlife(GameTestHelper h){
        arena(h);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(h.absoluteVec(new Vec3(35,2,16)));h.getLevel().addFreshEntity(cow);var craft=sortie(h,null);
        h.runAfterDelay(115,()->{h.assertTrue(craft.combat().shots()==0&&cow.getHealth()==cow.getMaxHealth(),"wildlife is outside proactive enemy selection");h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airCombatEmpty",timeoutTicks=5200)
    public static void sourceAiFiresBeyondEightRocketsWithEmptyPlayerStoresAndPersistsCounters(GameTestHelper h){
        var fixture=new HelicopterPilotGameTest.Fixture(h,260800);var level=fixture.level;
        var target=ModEntities.COMMON_INFECTED.get().create(level);Vec3 targetAt=new Vec3(fixture.x+12,81,0);target.setPos(targetAt);target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);level.addFreshEntity(target);fixture.entities.add(target);
        var craft=fixture.craft(16);var crew=fixture.crew(craft);for(int i=0;i<crew.size();i++)if(i<2)crew.get(i).setNoAi(false);else crew.get(i).discard();
        var caller=ModEntities.BLACKWATCH_OFFICER.get().create(level);caller.setPos(fixture.x-12,81,0);caller.setAutomaticUnitJoin(false);level.addFreshEntity(caller);caller.setTarget(target);
        h.assertTrue(craft.deployTo(new Vec3(fixture.x,81,12)),"isolated source ammunition mission");craft.combat().supportCall(caller);caller.discard();
        var tag=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());tag.getCompound("AirCombat").putInt("Rounds",0);tag.getCompound("AirCombat").putInt("Rockets",0);craft.load(tag);craft.combat().rapid().clear();
        boolean[] restored={false},redispatched={false},airDamage={false};String[] previous={""};var launchTicks=new java.util.ArrayList<Integer>();int[] observedShots={0};float[] previousHealth={target.getHealth()};
        var secondSortie=java.util.EnumSet.noneOf(BlackwatchHelicopter.Phase.class);
        h.onEachTick(()->{h.assertTrue(!target.isRemoved(),"persistent sortie target remains registered: reason="+target.getRemovalReason());target.setPos(targetAt);target.setDeltaMovement(Vec3.ZERO);
            var damage=target.getLastDamageSource();
            if(target.getHealth()<previousHealth[0]&&damage!=null&&damage.getEntity() instanceof MilitarySoldier actor&&actor.getVehicle()==craft
                    &&(damage.getDirectEntity()==craft||damage.getDirectEntity() instanceof com.codex.blacklight.firearms.HellfireBlastEntity))airDamage[0]=true;
            previousHealth[0]=target.getHealth();
            if(craft.phase()==BlackwatchHelicopter.Phase.RETURN){
                h.assertTrue(!redispatched[0],"two actual sorties must reach nine accepted launches: "+launchTicks+" phases="+secondSortie);
                // The isolated first engagement still obeys the 2400-tick MC
                // sortie budget. Dispatch the SAME aircraft again through public
                // mission/radio APIs; no age, phase, ammunition or shot-count edits.
                h.assertTrue(craft.deployTo(new Vec3(fixture.x,81,12)),"same aircraft accepts a second supported mission after natural return");
                var nextCaller=ModEntities.BLACKWATCH_OFFICER.get().create(level);nextCaller.setPos(fixture.x-12,81,0);nextCaller.setAutomaticUnitJoin(false);level.addFreshEntity(nextCaller);nextCaller.setTarget(target);craft.combat().supportCall(nextCaller);nextCaller.discard();
                h.assertTrue(craft.combat().start(),"real radio caller reactivates support without replenishing magazines");redispatched[0]=true;
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_AMMO_REDISPATCH tick={} launches={} rounds={} rockets={}",craft.tickCount,launchTicks,craft.combat().rounds(),craft.combat().rockets());
            }
            if(redispatched[0])secondSortie.add(craft.phase());
            if(craft.combat().rocketShots()>observedShots[0]){observedShots[0]=craft.combat().rocketShots();launchTicks.add(craft.tickCount);}
            String current=craft.combat().tactic()+"/"+craft.combat().maneuverStage()+"/"+craft.combat().hellfireAttack().stage()+"/"+craft.combat().rocketShots();
            if(!current.equals(previous[0])||craft.tickCount%200==0){previous[0]=current;var trace=new net.minecraft.nbt.CompoundTag();craft.combat().save(trace);
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_AMMO_TIMELINE tick={} phase={} state={} pos={} target={} launches={} data={}",craft.tickCount,craft.phase(),current,craft.position(),target.position(),launchTicks,trace);}
            if(!restored[0]&&craft.combat().rocketShots()>=4){
            int gun=craft.combat().gunShots(),rocket=craft.combat().rocketShots(),total=craft.combat().shots();var saved=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(saved);craft.load(saved);
            h.assertTrue(craft.combat().gunShots()==gun&&craft.combat().rocketShots()==rocket&&craft.combat().shots()==total,"accepted shot counters survive real reload");restored[0]=true;
        }});
        h.succeedWhen(()->{
            h.assertTrue(restored[0]&&redispatched[0]&&craft.combat().rocketShots()>=9&&craft.combat().gunShots()>0,"same AI airframe keeps firing past eight rockets across real sorties: "+craft.combat().rocketShots()+" phase="+craft.phase()+" targetHealth="+target.getHealth()+" position="+craft.position()+" launches="+launchTicks+" state="+previous[0]);
            h.assertTrue(craft.combat().rounds()==0&&craft.combat().rockets()==0&&craft.combat().rapid().left()+craft.combat().rapid().right()==0,"AI neither consumes nor refills any depleted player magazine");
            h.assertTrue(airDamage[0]&&target.getHealth()<target.getMaxHealth()&&craft.phase()==BlackwatchHelicopter.Phase.STRAFE,"real aircraft weapons damage the target with source infinite-ammo policy");
            h.assertTrue(secondSortie.containsAll(java.util.EnumSet.of(BlackwatchHelicopter.Phase.CLIMB,BlackwatchHelicopter.Phase.APPROACH,BlackwatchHelicopter.Phase.DESCEND,BlackwatchHelicopter.Phase.UNLOAD,BlackwatchHelicopter.Phase.DEPART,BlackwatchHelicopter.Phase.PURSUIT,BlackwatchHelicopter.Phase.STRAFE)),"second public mission completes its actual flight phases: "+secondSortie);
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_INFINITE_AI_VERIFIED gunShots={} rocketShots={} restored={} playerRounds={} playerRockets={} launches={} secondSortie={} airDamage={}",craft.combat().gunShots(),craft.combat().rocketShots(),restored[0],craft.combat().rounds(),craft.combat().rockets(),launchTicks,secondSortie,airDamage[0]);
            fixture.close();
        });
    }
    @GameTest(template="helicopter_arena",batch="airCombatFriendRay",timeoutTicks=650)
    public static void FriendlyCrossingTheActualGunRayStopsTheMachineGun(GameTestHelper h){
        var fixture=longSortie(h,3);var target=fixture.target();var craft=fixture.craft();
        var ally=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());ally.setNoAi(true);ally.setNoGravity(true);ally.setPos(craft.position().lerp(target.position(),.5));h.getLevel().addFreshEntity(ally);fixture.area().entities.add(ally);float health=ally.getHealth();boolean[] cleared={false};
        h.onEachTick(()->{
            if(cleared[0])return;
            var at=craft.weaponPoint(com.codex.blacklight.military.aircraft.HelicopterArmament.GUN).lerp(target.getBoundingBox().getCenter(),.5);ally.setPos(at.add(0,-ally.getBbHeight()*.5,0));ally.setDeltaMovement(Vec3.ZERO);
            if(craft.tickCount>=200&&craft.phase()==BlackwatchHelicopter.Phase.STRAFE){h.assertTrue(craft.combat().gunShots()==0&&ally.getHealth()==health,"actual intervening friendly blocks gunfire");cleared[0]=true;ally.setPos(target.position().add(0,0,5));}
        });
        h.succeedWhen(()->{h.assertTrue(cleared[0]&&craft.combat().gunShots()>0&&ally.getHealth()==health,"after the friendly clears, the actual gun fires without hurting it");fixture.area().close();});
    }
    @GameTest(template="helicopter_arena",batch="airCombatOccluded",timeoutTicks=260)
    public static void AnAcquiredTargetMovingBehindCoverDoesNotUpdateLastSeenOrReceiveFire(GameTestHelper h){
        var fixture=longSortie(h,4);var target=fixture.target();var craft=fixture.craft();Vec3[] last={null};int[] stores={0,0};
        h.runAfterDelay(75,()->{
            h.assertTrue(craft.combat().targetId()!=null,"live target acquired before cover appears");last[0]=craft.combat().lastObserved();stores[0]=craft.combat().gunShots();stores[1]=craft.combat().rocketShots();
            Vec3 covered=target.position().add(5,0,0);var center=net.minecraft.core.BlockPos.containing(covered);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=8;y++)if(Math.abs(x)==2||Math.abs(z)==2||y==8)h.getLevel().setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
            fixture.anchor()[0]=covered;target.setPos(covered);h.assertTrue(h.getLevel().noCollision(target),"covered target has an unoccupied interior");
        });
        h.runAfterDelay(105,()->{h.assertTrue(last[0].equals(craft.combat().lastObserved()),"occluded motion cannot update last observation");
            h.assertTrue(craft.combat().gunShots()==stores[0]&&craft.combat().rocketShots()==stores[1],"no new shots into cover");});
        h.runAfterDelay(200,()->{h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.RETURN,"persistent occlusion ends pursuit");fixture.area().close();h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airCombatCooperate",timeoutTicks=150)
    public static void DeliveredTroopsReceiveTheRadioTargetOnlyWithinSightAndTerritory(GameTestHelper h){
        var fixture=longSortie(h,2);var target=fixture.target();var craft=fixture.craft();
        var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());soldier.setAutomaticUnitJoin(false);soldier.setPos(target.position().add(-9,0,0));h.getLevel().addFreshEntity(soldier);fixture.area().entities.add(soldier);soldier.adoptFieldPost();
        boolean[] done={false};int[] age={0};h.onEachTick(()->{
            if(done[0])return;
            try{
                h.assertTrue(++age[0]<100,"isolated handoff area became entity-ticking before timeout");
                if(h.getLevel().getEntity(target.getUUID())!=target)return;
                soldier.setTarget(null);craft.combat().assignDeliveredTroop(soldier);
                h.assertTrue(soldier.getTarget()==target,"radio target passed to available field soldier: available="+soldier.tacticsAvailable()+" valid="+soldier.validTarget(target)+" sight="+soldier.hasLineOfSight(target)+" territory="+BlackwatchSquadSystem.withinTerritory(soldier,target.position()));soldier.setTarget(null);
                var wall=target.blockPosition().offset(-5,0,0);for(int y=0;y<3;y++)for(int z=-2;z<=2;z++)h.getLevel().setBlock(wall.offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
                craft.combat().assignDeliveredTroop(soldier);h.assertTrue(soldier.getTarget()==null,"handoff cannot assign an opponent behind solid cover");
                for(int y=0;y<3;y++)for(int z=-2;z<=2;z++)h.getLevel().setBlock(wall.offset(0,y,z),Blocks.AIR.defaultBlockState(),2);
                target.setPos(target.position().add(80,0,0));h.assertTrue(!BlackwatchSquadSystem.withinTerritory(soldier,target.position()),"opponent is actually outside the delivered soldier's territory");
                craft.combat().assignDeliveredTroop(soldier);h.assertTrue(soldier.getTarget()==null,"handoff cannot assign an opponent outside territory");done[0]=true;fixture.area().close();h.succeed();
            }catch(RuntimeException ex){done[0]=true;fixture.area().close();throw ex;}
        });
    }
    @GameTest(template="helicopter_arena",batch="airCombatLost",timeoutTicks=260)
    public static void LostTargetStopsFiringAndReturnsInsteadOfOrbitingForever(GameTestHelper h){
        // Keep this no-opponent scenario outside the other native templates' acquisition radius.
        var fixture=new HelicopterPilotGameTest.Fixture(h,259000);var level=fixture.level;
        var target=ModEntities.COMMON_INFECTED.get().create(level);target.setPos(fixture.x+12,81,0);target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);target.setHealth(4000);level.addFreshEntity(target);fixture.entities.add(target);
        var craft=fixture.craft(16);var crew=fixture.crew(craft);for(int i=0;i<crew.size();i++)if(i<2)crew.get(i).setNoAi(false);else crew.get(i).discard();
        var caller=ModEntities.BLACKWATCH_OFFICER.get().create(level);caller.setPos(fixture.x-12,81,0);caller.setAutomaticUnitJoin(false);level.addFreshEntity(caller);caller.setTarget(target);
        h.assertTrue(craft.deployTo(new Vec3(fixture.x,81,12)),"isolated supported mission");craft.combat().supportCall(caller);caller.discard();
        var tag=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());craft.load(tag);
        h.runAfterDelay(80,target::discard);
        h.runAfterDelay(200,()->{try(fixture){h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.RETURN&&craft.combat().targetId()==null,"dead/removed target releases combat lock: "+craft.phase()+" / "+craft.combat().targetId());}h.succeed();});
    }
}
