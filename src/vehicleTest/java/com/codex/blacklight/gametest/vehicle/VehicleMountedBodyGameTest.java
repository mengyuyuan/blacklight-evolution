package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

/** Real occupied tank + registered gun/ammunition -> native ray -> parent permission and animated contact. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleMountedBodyGameTest {
    private record Shot(Vec3 muzzle,Vec3 direction,EntityHitResult hit){}
    private enum Scene { HYDRA,GREENE,WALL,ALLIED_BODY,ALLIED_ACTOR }
    private static Vec3 mount(MilitaryVehicle vehicle,String id){
        var value=vehicle.definition().mountWorld(id,new VehicleDefinition.Vec3(vehicle.getX(),vehicle.getY(),vehicle.getZ()),
                vehicle.getYRot(),vehicle.turretYaw(),vehicle.gunPitch());
        return new Vec3(value.x(),value.y(),value.z());
    }
    private static EntityHitResult first(MilitaryVehicle vehicle,Vec3 from,Vec3 to){
        return ProjectileUtil.getEntityHitResult(vehicle.level(),vehicle,from,to,new AABB(from,to).inflate(.3),
                entity->entity.getRootVehicle()!=vehicle&&entity.isAlive()&&entity.isPickable());
    }
    private static Shot aimAtRealPart(MilitaryVehicle vehicle,LivingEntity boss,boolean appendage){
        var specification=VehicleWeaponDefinition.require("coaxial_machine_gun");
        for(var region:AnimatedHurtBody.of(boss).hurtRegions(0)){
            if(appendage&&!region.bone().startsWith("weak_"))continue;
            for(var box:region.boxes()){
                Vec3 to=box.center;boolean inRange=true;
                // Restore only an unoccupied fixture turret through its real save fields.
                // The actual firing boundary below still validates a real player, seat, lease and stock.
                for(int pass=0;pass<3;pass++){
                    Vec3 from=mount(vehicle,specification.breech());
                    float pitch=VehicleAim.pitch(from,to);
                    if(pitch< -25||pitch>15){inRange=false;break;}
                    var tag=vehicle.saveWithoutId(new CompoundTag());
                    tag.putFloat("VehicleTurretYaw",-VehicleAim.yaw(from,to));tag.putFloat("VehicleGunPitch",pitch);
                    vehicle.load(tag);
                }
                if(!inRange)continue;
                var muzzle=mount(vehicle,specification.muzzle());var direction=muzzle.subtract(mount(vehicle,specification.breech())).normalize();
                var hit=first(vehicle,muzzle,muzzle.add(direction.scale(60)));
                if(hit!=null&&CombatTargeting.resolveTarget(hit.getEntity())==boss&&hit.getEntity()!=boss
                        &&muzzle.distanceTo(hit.getLocation())>8&&VehicleDamageBridge.loaded((net.minecraft.server.level.ServerLevel)vehicle.level(),muzzle,hit.getLocation())
                        &&(!appendage||hit.getEntity() instanceof GreenePart part&&part.slot<3))return new Shot(muzzle,direction,hit);
            }
        }
        return null;
    }
    private static void scenario(GameTestHelper h,Scene scene,int x){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);
        for(int dx=-10;dx<=10;dx++)for(int z=25;z<=40;z++){
            f.level.setBlock(new BlockPos(f.x+dx,80,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=81;y<111;y++)f.level.setBlock(new BlockPos(f.x+dx,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        var vehicle=ModEntities.MILITARY_TANK.get().create(f.level);vehicle.setPos(f.x+.5,81,.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);
        LivingEntity boss=scene==Scene.GREENE?EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(f.level)
                :ModEntities.BLACKLIGHT_HYDRA.get().create(f.level);
        // Explicit existing encounter fixture; fresh opening has its own regression.
        if(boss instanceof EncounterMob standingMother){var standing=standingMother.greeneBattle().save();standing.putBoolean("OpeningPending",false);standingMother.greeneBattle().load(standing);}

        boss.setPos(f.x+.5,81,28);((Mob)boss).setNoAi(true);((Mob)boss).setPersistenceRequired();f.level.addFreshEntity(boss);f.entities.add(boss);
        h.runAfterDelay(90,()->{try(f){
            if(boss instanceof BlacklightHydra hydra)hydra.updateParts();else ((EncounterMob)boss).updateParts();
            var shot=aimAtRealPart(vehicle,boss,scene==Scene.GREENE);
            h.assertTrue(shot!=null,"fixture exposes a real native animated part within the actual tank gun's pitch and loaded range");
            var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setGameMode(GameType.SURVIVAL);player.setPos(f.x-3,81,0);
            h.assertTrue(player.startRiding(vehicle)&&vehicle.weaponOperator()==player&&vehicle.controlLease(player.getUUID())!=null,
                    "real player holds the tank's unoccupied weapon authority");
            // Prove reception uses the gun ray, even when the driver's own eye ray points away.
            player.setYRot(180);player.setXRot(80);
            var team=f.level.getScoreboard().addPlayerTeam("gveh"+x);
            try{
                float before=boss.getHealth();var saved=boss.saveWithoutId(new CompoundTag());
                int effects=boss instanceof BlacklightHydra hydra?hydra.incomingEffects():((EncounterMob)boss).incomingEffects();
                LivingEntity extra=null;
                if(scene==Scene.WALL){
                    var at=BlockPos.containing(shot.muzzle().lerp(shot.hit().getLocation(),.45));
                    for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)
                        f.level.setBlock(at.offset(dx,dy,dz),Blocks.BEDROCK.defaultBlockState(),2);
                }else if(scene==Scene.ALLIED_BODY){
                    f.level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);
                    f.level.getScoreboard().addPlayerToTeam(boss.getScoreboardName(),team);
                    extra=EntityType.ZOMBIE.create(f.level);((Mob)extra).setNoAi(true);
                    var at=shot.hit().getLocation().add(shot.direction().scale(7));extra.setPos(at.x,at.y-extra.getBbHeight()*.5,at.z);
                    f.level.addFreshEntity(extra);f.entities.add(extra);
                    var nearest=first(vehicle,shot.muzzle(),shot.muzzle().add(shot.direction().scale(60)));
                    h.assertTrue(nearest!=null&&CombatTargeting.resolveTarget(nearest.getEntity())==boss,
                            "the protected parent body's part is the actual first obstruction before the hostile living target");
                }else if(scene==Scene.ALLIED_ACTOR){
                    extra=EntityType.IRON_GOLEM.create(f.level);((Mob)extra).setNoAi(true);
                    var at=shot.muzzle().lerp(shot.hit().getLocation(),.45);extra.setPos(at.x,at.y-extra.getBbHeight()*.5,at.z);
                    f.level.addFreshEntity(extra);f.entities.add(extra);
                    f.level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);f.level.getScoreboard().addPlayerToTeam(extra.getScoreboardName(),team);
                    var nearest=first(vehicle,shot.muzzle(),shot.muzzle().add(shot.direction().scale(60)));
                    h.assertTrue(nearest!=null&&nearest.getEntity()==extra,"a real friendly actor stands before the multipart target");
                }
                float extraHealth=extra==null?0:extra.getHealth();vehicle.inventory().addAmmunition("machine_gun_round",1);
                h.assertTrue(VehicleAttackService.fire(vehicle,player,"coaxial_machine_gun")&&vehicle.inventory().ammunition("machine_gun_round")==0,
                        "real occupied vehicle firing entry accepts and spends exactly one registered machine-gun round");
                int afterEffects=boss instanceof BlacklightHydra hydra?hydra.incomingEffects():((EncounterMob)boss).incomingEffects();
                if(scene==Scene.HYDRA)h.assertTrue(boss.getHealth()<before&&afterEffects>effects
                        &&AnimatedHurtBody.of(boss).incomingPoint().distanceTo(shot.hit().getLocation())<.02,
                        "native tank gun damages Hydra at the actual part contact despite the driver's unrelated look ray");
                else if(scene==Scene.GREENE){
                    var after=boss.saveWithoutId(new CompoundTag());
                    h.assertTrue(boss.getHealth()==before&&afterEffects>effects
                            &&(after.getFloat("EncounterArmor0")<saved.getFloat("EncounterArmor0")||after.getFloat("EncounterArmor1")<saved.getFloat("EncounterArmor1")||after.getFloat("EncounterArmor2")<saved.getFloat("EncounterArmor2")),
                            "native tank gun damages the contacted independent Greene appendage without bypassing the protected core");
                }else h.assertTrue(boss.getHealth()==before&&afterEffects==effects&&(extra==null||extra.getHealth()==extraHealth),
                        "first cover, friendly actor or protected parent stops this bullet without damage or penetration: "+scene);
            }finally{f.level.getScoreboard().removePlayerTeam(team);}
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicleMountedBody",timeoutTicks=160)
    public static void occupiedTankGunHitsHydrasActualPart(GameTestHelper h){scenario(h,Scene.HYDRA,760000);}
    @GameTest(template="blackwatch_arena",batch="vehicleMountedBody",timeoutTicks=160)
    public static void occupiedTankGunHitsGreenesIndependentAppendage(GameTestHelper h){scenario(h,Scene.GREENE,761000);}
    @GameTest(template="blackwatch_arena",batch="vehicleMountedBody",timeoutTicks=160)
    public static void blockBeforeMultipartBodyStopsRealTankShot(GameTestHelper h){scenario(h,Scene.WALL,762000);}
    @GameTest(template="blackwatch_arena",batch="vehicleMountedBody",timeoutTicks=160)
    public static void alliedMultipartParentProtectsItsPartsAndShieldsHostileBehind(GameTestHelper h){scenario(h,Scene.ALLIED_BODY,763000);}
    @GameTest(template="blackwatch_arena",batch="vehicleMountedBody",timeoutTicks=160)
    public static void friendlyActorBeforeMultipartBodyStopsRealTankShot(GameTestHelper h){scenario(h,Scene.ALLIED_ACTOR,764000);}
    private static void aircraft(GameTestHelper h,boolean allied,int x){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);
        VehicleTestReadiness.run(h,f,()->{
            var vehicle=ModEntities.MILITARY_TANK.get().create(f.level);vehicle.setPos(f.x+.5,81,.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);
            var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setGameMode(GameType.SURVIVAL);player.setPos(f.x-3,81,0);
            h.assertTrue(player.startRiding(vehicle)&&vehicle.weaponOperator()==player,"real player operates the tank gun");
            var muzzle=mount(vehicle,"muzzle_secondary");
            var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level);craft.setPos(muzzle.x,muzzle.y-1.5,18);f.level.addFreshEntity(craft);f.entities.add(craft);
            var behind=EntityType.ZOMBIE.create(f.level);behind.setNoAi(true);behind.setPos(muzzle.x,muzzle.y-behind.getBbHeight()*.5,32);f.level.addFreshEntity(behind);f.entities.add(behind);
            var team=f.level.getScoreboard().addPlayerTeam("gair"+x);
            try{
                if(allied){
                    var crew=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);crew.setNoAi(true);crew.setPos(craft.position());f.level.addFreshEntity(crew);f.entities.add(crew);
                    f.level.getScoreboard().addPlayerToTeam(player.getScoreboardName(),team);f.level.getScoreboard().addPlayerToTeam(crew.getScoreboardName(),team);
                    h.assertTrue(crew.startRiding(craft)&&VehicleDamageBridge.protectedAircraft(player,craft),"real allied crew protects the aircraft body");
                }
                var nearest=first(vehicle,muzzle,muzzle.add(0,0,40));
                h.assertTrue(nearest!=null&&nearest.getEntity()==craft,"the actual aircraft surface is before the living target behind it");
                float aircraftBefore=craft.health(),behindBefore=behind.getHealth();vehicle.inventory().addAmmunition("machine_gun_round",1);
                h.assertTrue(VehicleAttackService.fire(vehicle,player,"coaxial_machine_gun")&&vehicle.inventory().ammunition("machine_gun_round")==0,
                        "real tank firing spends one actual round at the first aircraft surface");
                h.assertTrue(behind.getHealth()==behindBefore&&(allied?craft.health()==aircraftBefore:craft.health()<aircraftBefore),
                        "aircraft receives permitted ground-gun damage or allied-crew protection while always stopping penetration");
            }finally{f.level.getScoreboard().removePlayerTeam(team);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicleMountedBody",timeoutTicks=240)
    public static void realTankGunDamagesAircraftBeforeLivingTargetBehind(GameTestHelper h){aircraft(h,false,765000);}
    @GameTest(template="blackwatch_arena",batch="vehicleMountedBody",timeoutTicks=240)
    public static void alliedAircraftCrewProtectsBodyAndShieldsTargetBehind(GameTestHelper h){aircraft(h,true,766000);}
}
