package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="blacklight")
public final class VehicleFireGameTest {
    private static final Set<UUID> REJECT=new HashSet<>();
    private static final Map<UUID,Integer> REJECTIONS=new HashMap<>();
    @SubscribeEvent public static void spawning(EntityJoinLevelEvent event){
        if(event.getEntity() instanceof VehicleOrdnanceProjectile projectile&&projectile.getOwner()!=null&&REJECT.contains(projectile.getOwner().getUUID())){
            REJECTIONS.merge(projectile.getOwner().getUUID(),1,Integer::sum);event.setCanceled(true);
        }
    }
    private static MilitaryVehicle apc(MilitaryVehicleGameTest.Fixture f){var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);return v;}
    private static net.minecraft.server.level.ServerPlayer driver(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,MilitaryVehicle vehicle){
        var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x-2,81,0);h.assertTrue(p.startRiding(vehicle),"real driver boards");return p;
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void actualGunMuzzleOwnerIdentityAmmoAndCooldownAreOneShot(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,195000);var v=apc(f);var p=driver(h,f,v);v.inventory().addAmmunition("autocannon_round",2);
        h.runAfterDelay(20,()->{try(f){
            h.assertTrue(VehicleAttackService.fire(v,p,"autocannon"),"actual valid driver fires one real autocannon projectile");
            h.assertTrue(!VehicleAttackService.fire(v,p,"autocannon")&&v.inventory().ammunition("autocannon_round")==1,"same-tick spam cannot bypass actual stock and cooldown");
            var shells=f.level.getEntitiesOfClass(VehicleOrdnanceProjectile.class,v.getBoundingBox().inflate(8));
            h.assertTrue(shells.size()==1,"exactly one real projectile was registered");var shell=shells.getFirst();f.entities.add(shell);
            var mount=v.definition().mountWorld("muzzle_primary",new VehicleDefinition.Vec3(v.getX(),v.getY(),v.getZ()),v.getYRot(),v.turretYaw(),v.gunPitch());
            h.assertTrue(shell.position().distanceTo(new Vec3(mount.x(),mount.y(),mount.z()))<.05&&shell.getOwner()==p&&shell.weaponId().equals("autocannon"),"projectile starts at actual gun muzzle and keeps actual actor and weapon identity");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void blockedBarrelAndEmptyStockNeverFireOrSpendAmmo(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,196000);var v=apc(f);var p=driver(h,f,v);v.inventory().addAmmunition("autocannon_round",2);
        h.runAfterDelay(20,()->{try(f){
            var origin=new VehicleDefinition.Vec3(v.getX(),v.getY(),v.getZ());var a=v.definition().mountWorld("breech_primary",origin,0,0,0);var b=v.definition().mountWorld("muzzle_primary",origin,0,0,0);
            var wall=BlockPos.containing((a.x()+b.x())/2,(a.y()+b.y())/2,(a.z()+b.z())/2);f.level.setBlock(wall,Blocks.IRON_BLOCK.defaultBlockState(),2);
            h.assertTrue(!VehicleAttackService.fire(v,p,"autocannon")&&v.inventory().ammunition("autocannon_round")==2,"a real wall across the actual barrel blocks spawning without debit");
            h.assertTrue(!VehicleAttackService.fire(v,p,"vehicle_rocket")&&v.inventory().ammunition("rocket_round")==0,"empty secondary mount cannot create a projectile");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void realGunnerRevokesDriverWeaponAuthority(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,197000);var v=apc(f);var p=driver(h,f,v);var gunner=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(gunner);gunner.setPos(f.x-2,81,0);h.assertTrue(gunner.startRiding(v),"real second player occupies gunner seat");v.inventory().addAmmunition("autocannon_round",2);
        h.runAfterDelay(20,()->{try(f){
            h.assertTrue(!VehicleAttackService.fire(v,p,"autocannon")&&v.inventory().ammunition("autocannon_round")==2,"driver cannot fire an occupied gunner mount");
            h.assertTrue(VehicleAttackService.fire(v,gunner,"autocannon")&&v.inventory().ammunition("autocannon_round")==1,"actual gunner can fire its actual weapon once");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void canceledProjectileJoinRestoresOnlyItsOwnUncommittedDebit(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,198000);var v=apc(f);var p=driver(h,f,v);v.inventory().addAmmunition("autocannon_round",2);
        h.runAfterDelay(20,()->{try(f){
            var before=v.inventory().snapshot();REJECT.add(p.getUUID());
            try{h.assertTrue(!VehicleAttackService.fire(v,p,"autocannon"),"actual rejected entity join cannot report fired");}
            finally{REJECT.remove(p.getUUID());}
            h.assertTrue(REJECTIONS.getOrDefault(p.getUUID(),0)==1&&v.inventory().snapshot().equals(before),"the actual cancellation event happened and restores exactly its own pending debit");REJECTIONS.remove(p.getUUID());
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void existingRifleHitsActualMechanicalHull(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,199000);var tank=ModEntities.MILITARY_TANK.get().create(f.level);tank.setPos(f.x+.5,81,7);f.level.addFreshEntity(tank);f.entities.add(tank);var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x+.5,81,-6);
        h.runAfterDelay(20,()->{try(f){
            float before=tank.health();FirearmsCombat.fire(p,WeaponCatalog.RIFLE,new Vec3(0,0,1),1);
            h.assertTrue(tank.health()<before&&Math.abs((before-tank.health())-.4F)<.001,"ordinary real rifle query reaches the mechanical parent with heavy-armor coefficient");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void liveAutocannonProjectileDamagesOneMechanicalHullExactlyOnce(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,200000);var v=apc(f);var p=driver(h,f,v);v.inventory().addAmmunition("autocannon_round",1);
        var target=ModEntities.MILITARY_TANK.get().create(f.level);target.setPos(f.x+.5,81,12);f.level.addFreshEntity(target);f.entities.add(target);
        h.runAfterDelay(20,()->{
            h.assertTrue(VehicleAttackService.fire(v,p,"autocannon"),"actual projectile is fired towards the real armored target");
            h.runAfterDelay(8,()->{try(f){h.assertTrue(Math.abs(target.health()-(target.maxHealth()-2.4F))<.001,"direct projectile plus splash/parent/part must not double-hit the same heavy hull");h.assertTrue(v.inventory().ammunition("autocannon_round")==0,"the live impact retains its one actual debit");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void sameKindHeAndApKeepTheirActualWeaponIdentityThroughEntitySave(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,201000)){
            var tank=ModEntities.MILITARY_TANK.get().create(f.level);tank.setPos(f.x+.5,81,.5);f.level.addFreshEntity(tank);f.entities.add(tank);var p=driver(h,f,tank);
            for(String id:new String[]{"tank_he","tank_ap"}){
                var projectile=new VehicleOrdnanceProjectile(f.level,p,tank,id);projectile.setPos(f.x+.5,83,5);
                var copy=ModEntities.VEHICLE_ORDNANCE.get().create(f.level);copy.load(projectile.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
                h.assertTrue(copy.weaponId().equals(id),"actual projectile NBT must retain "+id+" instead of selecting the first ROCKET kind");projectile.discard();copy.discard();
            }
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void deadShooterCannotLeaveAnUnattributedDamagingProjectile(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,202000);var v=apc(f);var p=driver(h,f,v);v.inventory().addAmmunition("autocannon_round",1);
        var target=ModEntities.MILITARY_TANK.get().create(f.level);target.setPos(f.x+.5,81,12);f.level.addFreshEntity(target);f.entities.add(target);
        h.runAfterDelay(20,()->{
            h.assertTrue(VehicleAttackService.fire(v,p,"autocannon"),"real owner fires before dying");p.setHealth(0);h.assertTrue(!p.isAlive(),"the actual projectile owner is dead");
            h.runAfterDelay(8,()->{try(f){h.assertTrue(target.health()==target.maxHealth()&&v.inventory().ammunition("autocannon_round")==0,"dead-owner projectile cannot damage or refund its already fired round");}h.succeed();});
        });
    }
}
