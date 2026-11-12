package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Opt-in, disposable real-server checks for the P1 combat-state adaptation. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @net.neoforged.fml.common.EventBusSubscriber(modid="blacklight")
public final class P1MilitaryGameTest {
    private static final Map<UUID,List<Long>> HITS=new HashMap<>();
    @GameTest(template="blackwatch_arena",batch="vehiclePlayerDents",timeoutTicks=40)
    public static void acceptedPlayerHitsPersistDentsAndRepairsRestoreSkin(GameTestHelper h){
        arena(h);var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(7.5,2,7.5)));h.getLevel().addFreshEntity(tank);
        var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.fromString("529836c5-afb2-4d73-bc1d-74a1538e8123"),"VehicleDentQA"));
        actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);actor.setPos(tank.position().add(4,0,1));
        Vec3 contact=tank.position().add(2,1.65,1);
        h.assertTrue(VehicleDamageBridge.hurtPhysical(actor,tank,80,null,contact),"real player melee damage bridge accepted");
        h.assertTrue(tank.dents().size()==1&&tank.health()==380,"skin dent accompanies actual armor-adjusted damage");
        double first=tank.dents().getFirst().depth();
        VehicleDamageBridge.hurtPhysical(actor,tank,80,null,contact);
        h.assertTrue(tank.dents().size()==1&&tank.dents().getFirst().depth()>first,"same contact deepens rather than splintering whole vehicle");
        var saved=new net.minecraft.nbt.CompoundTag();tank.addAdditionalSaveData(saved);
        var restored=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());restored.readAdditionalSaveData(saved);
        h.assertTrue(restored.dents().equals(tank.dents()),"saved deformation survives reload");
        restored.repair(40);h.assertTrue(restored.dents().getFirst().depth()<tank.dents().getFirst().depth(),"partial repair reduces deformation");
        restored.repair(1000);h.assertTrue(restored.dents().isEmpty(),"full repair restores original skin");
        tank.setInvulnerable(true);var before=tank.dents();
        h.assertTrue(!VehicleDamageBridge.hurtPhysical(actor,tank,80,null,contact)&&before.equals(tank.dents()),"rejected hit cannot deform armor");
        tank.discard();restored.discard();h.succeed();
    }
    @net.neoforged.bus.api.SubscribeEvent public static void damaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        var hits=HITS.get(e.getEntity().getUUID());if(hits!=null&&e.getNewDamage()>0&&e.getSource().getEntity() instanceof MilitarySoldier)hits.add(e.getEntity().level().getGameTime());
    }
    private static void arena(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<9;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
    }
    private static BlackwatchSoldier soldier(GameTestHelper h,WeaponDefinition weapon){
        var actor=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());
        actor.setPos(h.absoluteVec(new Vec3(7.5,2,3.5)));actor.setPersistenceRequired();actor.setAutomaticUnitJoin(false);
        actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        h.assertTrue(h.getLevel().addFreshEntity(actor),"real soldier registered");actor.initializeWeapon();
        var gun=new ItemStack(FirearmsRegistry.weapon(weapon.id()));FirearmItem.store(gun,new AmmoState(weapon.capacity(),0,0));
        actor.setItemSlot(EquipmentSlot.MAINHAND,gun);actor.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        actor.receiveAmmo(FirearmsRegistry.ammo(weapon.ammoId()),weapon.capacity()*3);
        return actor;
    }
    private static BlacklightInfectedHost victim(GameTestHelper h,double z){
        var actor=ModEntities.INFECTED_HOST.get().create(h.getLevel());actor.inherit(EntityType.COW.create(h.getLevel()));
        actor.setNoAi(true);actor.setPersistenceRequired();actor.setPos(h.absoluteVec(new Vec3(7.5,2,z)));
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);actor.setHealth(1000);
        actor.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.assertTrue(h.getLevel().addFreshEntity(actor),"real infected victim registered");HITS.put(actor.getUUID(),new ArrayList<>());return actor;
    }
    @GameTest(template="blackwatch_arena",batch="p1HullImpulse",timeoutTicks=40)
    public static void hullImpactKeepsContactAndConvertsDirectionToChassisSpace(GameTestHelper h){
        arena(h);var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(7.5,2,7.5)));tank.setYRot(90);h.getLevel().addFreshEntity(tank);
        var contact=tank.position().add(-2,1.5,0);float health=tank.health();
        h.assertTrue(tank.hurtAt(tank.damageSources().generic(),40,contact,new Vec3(1,0,0),VehicleFeedbackMotion.Impact.HEAVY),"real hull accepts damage");
        h.assertTrue(tank.health()==health-40&&tank.impactPoint().distanceTo(contact)<1e-5,"damage receipt retains exact contact");
        h.assertTrue(tank.impactPush().z>.99&&Math.abs(tank.impactPush().x)<.001,"world impulse rotates into hull coordinates");
        var lever=tank.impactLever();h.assertTrue(lever.z<-.4&&Math.abs(lever.x)<.001,"contact rotates into local hull coordinates");
        tank.setPos(tank.position().add(0,0,1));
        h.assertTrue(tank.impactLever().equals(lever),"moving after contact cannot slide the recorded impact lever");
        float strength=tank.impactStrength();tank.setInvulnerable(true);
        h.assertTrue(!tank.hurtAt(tank.damageSources().generic(),90,contact,new Vec3(-1,0,0),VehicleFeedbackMotion.Impact.EXPLOSIVE),"invulnerability rejects hit");
        h.assertTrue(tank.health()==health-40&&tank.impactStrength()==strength&&tank.impactPush().z>.99,"rejected damage cannot replace feedback");
        tank.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="p1HullBlast",timeoutTicks=40)
    public static void blastPushesAwayFromExplosionRatherThanAlongProjectileTravel(GameTestHelper h){
        arena(h);var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(7.5,2,7.5)));h.getLevel().addFreshEntity(tank);
        var actor=EntityType.ARMOR_STAND.create(h.getLevel());actor.setPos(tank.position().add(5,0,0));h.getLevel().addFreshEntity(actor);
        var arrow=EntityType.ARROW.create(h.getLevel());arrow.setDeltaMovement(0,0,1);
        float health=tank.health();
        int hits=VehicleDamageBridge.blast(h.getLevel(),actor,VehicleWeaponDefinition.require("tank_he").weapon(),tank.position().add(3.5,1.4,0),arrow,null);
        h.assertTrue(hits==1&&tank.health()<health,"nearby blast damages the hull once");
        h.assertTrue(tank.impactPush().x<-.9&&Math.abs(tank.impactPush().z)<.01,"blast pushes radially, not along shell velocity");
        actor.discard();tank.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="p1HullBullet",timeoutTicks=40)
    public static void lightBulletDoesNotEraseTheRecentHeavyImpulse(GameTestHelper h){
        arena(h);var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(7.5,2,7.5)));h.getLevel().addFreshEntity(tank);
        var contact=tank.position().add(-2,1,0);
        tank.hurtAt(tank.damageSources().generic(),50,contact,new Vec3(1,0,0),VehicleFeedbackMotion.Impact.HEAVY);float strength=tank.impactStrength();
        tank.hurtAt(tank.damageSources().generic(),1,contact,new Vec3(-1,0,0),VehicleFeedbackMotion.Impact.BULLET);
        h.assertTrue(tank.impactStrength()==strength&&tank.impactPush().x>.99,"small bullet preserves heavy spring");
        tank.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="p1ShotReceipt",timeoutTicks=40)
    public static void onlySuccessfulFireRecordsRecoilAndBlockedBreechDoesNotSpendAmmo(GameTestHelper h){
        arena(h);var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));h.getLevel().addFreshEntity(tank);
        var driver=soldier(h,WeaponCatalog.RIFLE);driver.startRiding(tank);tank.inventory().addAmmunition("tank_he_shell",2);tank.inventory().addAmmunition("machine_gun_round",3);
        h.runAtTickTime(3,()->{
            h.setBlock(7,4,9,Blocks.STONE);
            h.assertTrue(!VehicleAttackService.fire(tank,driver,"tank_he"),"barrel obstruction rejects shot");
            h.assertTrue(tank.primaryShot()<0&&tank.inventory().ammunition("tank_he_shell")==2,"no false kick or debit");
            h.setBlock(7,4,9,Blocks.AIR);
            h.assertTrue(VehicleAttackService.fire(tank,driver,"tank_he"),"clear main gun fires");
            long shot=tank.primaryShot();h.assertTrue(shot==h.getLevel().getGameTime()&&tank.lastPrimaryWeapon().equals("tank_he"),"server synchronizes actual weapon and shot time");
            h.assertTrue(!VehicleAttackService.fire(tank,driver,"tank_he")&&tank.primaryShot()==shot,"cooldown cannot restart recoil");
            h.assertTrue(VehicleAttackService.fire(tank,driver,"coaxial_machine_gun")&&tank.primaryShot()==shot&&tank.secondaryShot()==shot,"coax has independent receipt");
            driver.discard();tank.discard();h.succeed();
        });
    }
    @GameTest(template="blackwatch_arena",batch="p1Rifle",timeoutTicks=160)
    public static void actualEmptyMagazineReloadsButBulletCooldownDoesNot(GameTestHelper h){
        arena(h);var actor=soldier(h,WeaponCatalog.RIFLE);var target=victim(h,12.5);actor.setTarget(target);
        FirearmItem.store(actor.getMainHandItem(),new AmmoState(1,0,0));
        boolean[] reload={false},shot={false};int[] previous={1};
        h.onEachTick(()->{
            var ammo=FirearmItem.state(actor.getMainHandItem(),0);long now=h.getLevel().getGameTime();
            if(ammo.magazine()<previous[0])shot[0]=true;previous[0]=ammo.magazine();
            if(actor.phase()==MilitaryFireControl.RELOAD){
                h.assertTrue(MilitaryFireControl.reloading(ammo,WeaponCatalog.RIFLE,FirearmReloadSystem.started(actor.getMainHandItem()),now),"phase 3 requires a real active reload");reload[0]=true;
            }
        });
        h.runAtTickTime(140,()->{h.assertTrue(shot[0]&&reload[0]&&!HITS.get(target.getUUID()).isEmpty(),"real shooting and finite reload both occur: shot="+shot[0]+" reload="+reload[0]+" hits="+HITS.get(target.getUUID())+" reserve="+actor.reserveAmmo()+" magazine="+actor.magazineAmmo());actor.discard();target.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="p1Saw",timeoutTicks=120)
    public static void actualMachineGunUsesItsOwnCadenceInsteadOfThreeFixedShots(GameTestHelper h){
        arena(h);var actor=soldier(h,WeaponCatalog.MACHINE_GUN);var target=victim(h,12.5);actor.setTarget(target);
        List<Long> shots=new ArrayList<>();int[] previous={60};
        h.onEachTick(()->{
            int ammo=actor.magazineAmmo();if(ammo<previous[0])shots.add(h.getLevel().getGameTime());previous[0]=ammo;
            h.assertTrue(actor.phase()!=MilitaryFireControl.RELOAD,"a loaded SAW must not show fake reload between shots");
            if(shots.size()>=8){for(int i=1;i<8;i++)h.assertTrue(shots.get(i)-shots.get(i-1)==3,"SAW obeys its actual weapon cooldown: "+shots);actor.discard();target.discard();h.succeed();}
        });
    }
    @GameTest(template="blackwatch_arena",batch="p1Cover",timeoutTicks=120)
    public static void lowCoverReloadLowersRealHullThenStandsBackUp(GameTestHelper h){coverReload(h,false);}
    @GameTest(template="blackwatch_arena",batch="p1CoverDisarm",timeoutTicks=120)
    public static void disarmingCancelsCoverPoseAndRestoresHull(GameTestHelper h){coverReload(h,true);}
    private static void coverReload(GameTestHelper h,boolean disarm){
        arena(h);var actor=soldier(h,WeaponCatalog.RIFLE);var target=victim(h,12.5);actor.setTarget(target);
        for(int x=6;x<=9;x++)h.setBlock(x,2,5,Blocks.STONE_BRICKS);
        FirearmItem.store(actor.getMainHandItem(),new AmmoState(1,0,0));
        float standing=actor.getBbHeight(),eye=actor.getEyeHeight();long[] entered={-1};boolean[] cancelled={false};
        h.onEachTick(()->{
            if(actor.isCrouching()){
                if(entered[0]<0)entered[0]=h.getTick();
                h.assertTrue(actor.getBbHeight()<standing-.2&&actor.getEyeHeight()<eye-.2,"crouch changes real dimensions and eye height");
                if(disarm&&!cancelled[0]){cancelled[0]=true;actor.setEquipped(false);}
            }
            if(entered[0]>=0&&h.getTick()>entered[0]+3&&!actor.isCrouching()){
                h.assertTrue(Math.abs(actor.getBbHeight()-standing)<.001,"standing hull restored");
                h.assertTrue(disarm?actor.phase()==MilitaryFireControl.IDLE:actor.magazineAmmo()>0,"stand only after reload completion or cancellation");
                actor.discard();target.discard();h.succeed();
            }
        });
    }
    @GameTest(template="blackwatch_arena",batch="p1Melee",timeoutTicks=65)
    public static void gunButtDamageWaitsForContactAndDoesNotConsumeBullets(GameTestHelper h){melee(h,false,0);}
    @GameTest(template="blackwatch_arena",batch="p1Dodge",timeoutTicks=65)
    public static void dodgingTheStockSweepMakesItMiss(GameTestHelper h){melee(h,true,0);}
    @GameTest(template="blackwatch_arena",batch="p1Cancel",timeoutTicks=65)
    public static void losingTheWeaponCancelsThePendingMeleeHit(GameTestHelper h){melee(h,false,1);}
    @GameTest(template="blackwatch_arena",batch="p1Disarm",timeoutTicks=65)
    public static void removingTheActualHeldStackAlsoCancelsMelee(GameTestHelper h){melee(h,false,2);}
    @GameTest(template="blackwatch_arena",batch="p1MeleeWall",timeoutTicks=65)
    public static void stockStrideAndContactCannotPassThroughNewWall(GameTestHelper h){
        arena(h);var actor=soldier(h,WeaponCatalog.RIFLE);var target=victim(h,5.5);actor.setTarget(target);long[] start={-1};boolean[] wall={false};
        h.onEachTick(()->{
            if(start[0]<0&&actor.meleeStarted()>=0)start[0]=actor.meleeStarted();if(start[0]<0)return;
            long age=h.getLevel().getGameTime()-start[0];
            if(age>=3&&!wall[0]){wall[0]=true;for(int x=6;x<=9;x++)for(int y=2;y<=5;y++)h.setBlock(x,y,4,Blocks.STONE);}
            if(age>=18){
                h.assertTrue(HITS.get(target.getUUID()).isEmpty(),"wall blocks wrist damage");
                h.assertTrue(actor.getZ()<h.absoluteVec(new Vec3(0,0,4)).z,"authored root motion cannot pass wall");
                actor.discard();target.discard();h.succeed();
            }
        });
    }
    private static void melee(GameTestHelper h,boolean dodge,int cancel){
        arena(h);var actor=soldier(h,WeaponCatalog.RIFLE);var target=victim(h,5.5);actor.setTarget(target);
        var held=actor.getMainHandItem();
        long[] start={-1};boolean[] changed={false};
        h.onEachTick(()->{
            if(start[0]<0&&actor.meleeStarted()>=0)start[0]=actor.meleeStarted();
            if(start[0]<0)return;long age=h.getLevel().getGameTime()-start[0];
            var hits=HITS.get(target.getUUID());
            if(age<9)h.assertTrue(hits.isEmpty(),"wind-up cannot deal early damage: "+hits+" start="+start[0]);
            if(age>=4&&!changed[0]){changed[0]=true;if(dodge)target.setPos(target.position().add(4,0,0));if(cancel==1)actor.setEquipped(false);if(cancel==2)actor.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);}
            if(age==18){
                h.assertTrue(FirearmItem.state(held,0).magazine()==30,"stock strike does not fire or refill ammunition");
                h.assertTrue(dodge||cancel>0?hits.isEmpty():hits.size()==1&&hits.getFirst()-start[0]>=9&&hits.getFirst()-start[0]<=10,"one contact hit, or no hit after dodge/cancel: "+hits+" start="+start[0]);
                if(!dodge&&cancel==0&&com.codex.blacklight.animation.P1MilitaryMotion.local()!=null)
                    h.assertTrue(actor.getZ()-h.absoluteVec(new Vec3(7.5,2,3.5)).z>.25,"original stock attack advances the real server actor");
                if(cancel>0)h.assertTrue(actor.meleeStarted()<0&&actor.phase()==0,"weapon loss clears server action and client clock");actor.discard();target.discard();h.succeed();
            }
        });
    }
    @GameTest(template="blackwatch_arena",batch="p1TankReverse",timeoutTicks=45)
    public static void tankRegroupsAwayFromCloseThreatOnlyOncePerTick(GameTestHelper h){
        arena(h);var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(7.5,2,7.5)));h.getLevel().addFreshEntity(tank);
        var driver=soldier(h,WeaponCatalog.RIFLE);h.assertTrue(driver.startRiding(tank),"driver boarded");
        var target=victim(h,15.5);double initial=tank.getZ();
        h.runAtTickTime(3,()->{
            h.assertTrue(VehicleCombatSpacing.tick(tank,target),"close threat triggers regroup");double once=tank.getZ();
            h.assertTrue(once<initial,"real hull reverses away from threat");VehicleCombatSpacing.tick(tank,target);
            h.assertTrue(tank.getZ()==once,"repeated calls cannot advance the tank twice in one tick");
            driver.discard();target.discard();tank.discard();h.succeed();
        });
    }
    @GameTest(template="blackwatch_arena",batch="p1TankReverseWall",timeoutTicks=45)
    public static void retreatingTankStopsAtRearWall(GameTestHelper h){
        arena(h);var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());tank.setPos(h.absoluteVec(new Vec3(7.5,2,8.5)));h.getLevel().addFreshEntity(tank);
        var driver=soldier(h,WeaponCatalog.RIFLE);driver.startRiding(tank);var target=victim(h,15.5);
        for(int x=3;x<=12;x++)for(int y=2;y<=5;y++)h.setBlock(x,y,3,Blocks.STONE);
        boolean[] stopped={false};double[] previous={tank.getZ()};
        h.onEachTick(()->{VehicleCombatSpacing.tick(tank,target);if(tank.getZ()==previous[0])stopped[0]=true;previous[0]=tank.getZ();});
        h.runAtTickTime(25,()->{h.assertTrue(stopped[0],"rear wall eventually stops reversing");h.assertTrue(tank.getZ()>h.absoluteVec(new Vec3(0,0,7)).z,"tank hull never crosses rear wall");driver.discard();target.discard();tank.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="p1Tank",timeoutTicks=120)
    public static void tankChoosesMainGunForLargeThreatAndUsesTheChosenBreech(GameTestHelper h){
        arena(h);var vehicle=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());
        vehicle.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));h.getLevel().addFreshEntity(vehicle);
        var actor=soldier(h,WeaponCatalog.RIFLE);h.assertTrue(actor.startRiding(vehicle),"real soldier occupies vehicle");
        var target=victim(h,15.5);target.getAttribute(Attributes.SCALE).setBaseValue(2.5);
        vehicle.inventory().addAmmunition("tank_he_shell",2);vehicle.inventory().addAmmunition("machine_gun_round",20);
        h.onEachTick(()->{
            if(VehicleThreatBridge.engage(vehicle,target)){
                h.assertTrue(vehicle.inventory().ammunition("tank_he_shell")==1,"large threat selects main HE gun before coaxial");
                h.assertTrue(vehicle.inventory().ammunition("machine_gun_round")==20,"selection does not spend unselected gun ammo");
                h.assertTrue(!VehicleThreatBridge.engage(vehicle,target)||vehicle.inventory().ammunition("tank_he_shell")==1,"main breech cooldown is enforced");
                actor.discard();target.discard();vehicle.discard();h.succeed();
            }
        });
        h.runAtTickTime(110,()->h.fail("Tank did not shoot: legal="+VehicleThreatBridge.legalTarget(vehicle,target)+" aim="+vehicle.aimAiWeapon(actor,target,"tank_he")+" articulation="+VehicleKinematics.clearArticulation(vehicle,0,0,0,1)+" vehicle="+vehicle.position()+" yaw="+vehicle.turretYaw()+" pitch="+vehicle.gunPitch()+" target="+target.position()+" size="+target.getBbWidth()+"x"+target.getBbHeight()+" nearby="+h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,target.getBoundingBox().inflate(6),other->other!=target&&!VehicleThreatBridge.legalTarget(vehicle,other))+" he="+vehicle.inventory().ammunition("tank_he_shell")));
    }
}
