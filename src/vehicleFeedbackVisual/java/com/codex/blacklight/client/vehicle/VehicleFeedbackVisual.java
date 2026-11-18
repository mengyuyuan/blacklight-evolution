package com.codex.blacklight.client.vehicle;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;

/** Production-path weapon and impact views, in the explicit disposable D: QA world. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleFeedbackVisual {
    private static final boolean DENTS=Boolean.getBoolean("blacklight.test.vehicleDents");
    private static final boolean AUDIO=Boolean.getBoolean("blacklight.test.vehicleAudio");
    private static final String[] CASES=AUDIO?new String[]{"military_truck","military_apc","military_tank","blackwatch_truck","blackwatch_apc","blackwatch_tank"}:DENTS?new String[]{"dent_tank_side","dent_truck_front","dent_apc_side","dent_tank_turret","dent_tank_roof","dent_repair"}:new String[]{"tank_fire","apc_fire","tank_coax","tank_heavy_left","tank_bullet_right","truck_heavy_right","tank_occupant_hit","tank_shell_direct","tank_shell_blast","tank_roof_heavy"};
    private static final int DURATION=AUDIO?120:DENTS?80:56;
    private static final int ONLY_SCENE=DENTS?Integer.getInteger("blacklight.test.vehicleDentScene",-1):-1;
    private static int index=Math.max(0,ONLY_SCENE),tick,last=-1,carId=-1,driverId=-1,attackerId=-1;
    private static boolean initialized,queued,done,opening;
    private static volatile boolean ready;
    private static volatile String error;
    private static Path output;
    private static final JsonArray frames=new JsonArray();
    private static final List<JsonObject> receipts=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<String> sounds=new java.util.concurrent.CopyOnWriteArrayList<>();
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e){
        if(AUDIO&&initialized&&!done&&e.getName().startsWith("vehicle."))sounds.add(e.getName());
    }
    @SubscribeEvent public static void tick(ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(System.getProperty("blacklight.test.vehicleFeedback","").isBlank()||done)return;
        var mc=Minecraft.getInstance();
        if(!opening&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase("D:/CodexCache/forge-1201-qa/vehicles"))throw new IllegalStateException("Refused non-QA directory");
            opening=true;String world="Blackwatch P1 QA";
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(world)))throw new IllegalStateException("Use quickPlayWorld for the existing QA world");
            mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(7711L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            return;
        }
        var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase("D:/CodexCache/forge-1201-qa/vehicles")
                    ||!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Blackwatch P1 QA"))throw new IllegalStateException("Refused non-QA world");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.vehicleFeedback")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"Capturing current vehicle feedback");
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(55);mc.options.framerateLimit().set(30);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.setScreen(null);mc.getWindow().setTitle("Vehicle feedback - isolated QA");initialized=true;
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;tick=0;last=-1;frames.asList().clear();receipts.clear();sounds.clear();setup();return;}
            if(!ready)return;tick++;
            if(tick>=60&&tick<60+DURATION){
                final int age=tick-60,scene=index;
                server.execute(()->{
                    try{
                        var level=server.overworld();var car=(MilitaryVehicle)level.getEntity(carId);var driver=(MilitarySoldier)level.getEntity(driverId);var attacker=(LivingEntity)level.getEntity(attackerId);
                        if(AUDIO){audioAction(age,car,driver);return;}
                        if(DENTS){dentAction(scene,age,car);return;}
                        boolean action=false;
                        if(scene<3&&(age==8||scene==1&&age>=14&&(age-14)%6==0||scene==2&&age>=12&&(age-12)%4==0))
                            action=VehicleAttackService.fire(car,driver,scene==0?"tank_he":scene==1?"autocannon":"coaxial_machine_gun");
                        if((scene>=3&&scene<=6||scene==9)&&(age==8||scene==4&&age>=12&&(age-12)%4==0)){
                            Vec3 from=attacker.getEyePosition(),to=car.position().add(scene==9?new Vec3(1.3,1.4,1.5):new Vec3(0,1.4,0));var hit=VehicleDamageBridge.nearest(level,attacker,null,from,to);
                            if(hit==null)throw new IllegalStateException("No real hull contact");
                            action=scene==4?VehicleDamageBridge.hurt(attacker,car,WeaponCatalog.RIFLE,5,null,hit.point()):VehicleDamageBridge.hurtPhysical(attacker,car,80,null,hit.point());
                        }
                        if((scene==7||scene==8)&&age==8){
                            var origin=ModEntities.BLACKWATCH_TANK.get().create(level);
                            var shell=new VehicleOrdnanceProjectile(level,attacker,origin,"tank_he");
                            shell.setPos(car.position().add(scene==7?new Vec3(8,1.4,0):new Vec3(3.7,1.4,8)));
                            shell.setDeltaMovement(scene==7?new Vec3(-2.8,0,0):new Vec3(0,0,-2.8));
                            action=level.addFreshEntity(shell);
                        }
                        var row=new JsonObject();row.addProperty("tick",age);row.addProperty("time",level.getGameTime());row.addProperty("action",action);row.addProperty("health",car.health());row.addProperty("primaryShot",car.primaryShot());row.addProperty("secondaryShot",car.secondaryShot());row.addProperty("impactAt",car.impactAt());row.addProperty("strength",car.impactStrength());row.addProperty("contactX",car.impactPoint().x);receipts.add(row);
                    }catch(Exception failure){error=failure.toString();}
                });
            }
            if(tick>=60+DURATION){
                var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(output.resolve(CASES[index]+"-frames.json"),gson.toJson(frames));Files.writeString(output.resolve(CASES[index]+"-server.json"),gson.toJson(receipts));
                if(AUDIO)Files.writeString(output.resolve(CASES[index]+"-sounds.json"),gson.toJson(sounds));
                if(++index==CASES.length||ONLY_SCENE>=0){done=true;Files.writeString(output.resolve("status.txt"),"Captured "+(ONLY_SCENE>=0?1:CASES.length)+" production-path cases; review required");mc.stop();}else queued=false;
            }
        }catch(Exception failure){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Vehicle feedback QA",failure);try{if(output!=null)Files.writeString(output.resolve("status.txt"),failure.toString());}catch(Exception ignored){}mc.stop();}
    }
    private static void setup(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();final int scene=index;
        server.execute(()->{try{
            var level=server.overworld();var p=server.getPlayerList().getPlayer(playerId);p.stopRiding();p.setGameMode(GameType.SPECTATOR);
            for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(-16,80,-16,20,96,36),a->a instanceof MilitaryVehicle||a instanceof MilitarySoldier||a instanceof BlacklightInfectedHost||a instanceof net.minecraft.world.entity.monster.Zombie||a instanceof net.minecraft.world.entity.decoration.ArmorStand||a instanceof VehicleOrdnanceProjectile))entity.discard();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);
            for(int x=-14;x<=16;x++)for(int z=-16;z<=30;z++){level.setBlock(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);for(int y=81;y<=89;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            Vec3 camera=DENTS?new Vec3(7,84,9):new Vec3(10,84,13),look=new Vec3(0,82.6,0).subtract(camera.add(0,p.getEyeHeight(),0));
            p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
            var car=(DENTS?(scene==1?ModEntities.BLACKWATCH_TRUCK.get():scene==2?ModEntities.BLACKWATCH_APC.get():ModEntities.BLACKWATCH_TANK.get()):(scene==1?ModEntities.BLACKWATCH_APC.get():scene==5?ModEntities.BLACKWATCH_TRUCK.get():ModEntities.BLACKWATCH_TANK.get())).create(level);car.setPos(0,81,0);level.addFreshEntity(car);
            if(AUDIO){car.discard();car=List.of(ModEntities.MILITARY_TRUCK,ModEntities.MILITARY_APC,ModEntities.MILITARY_TANK,ModEntities.BLACKWATCH_TRUCK,ModEntities.BLACKWATCH_APC,ModEntities.BLACKWATCH_TANK).get(scene).get().create(level);car.setPos(0,81,0);level.addFreshEntity(car);car.inventory().issueInitialLoadout();}
            var driver=ModEntities.BLACKWATCH_SOLDIER.get().create(level);driver.setPos(0,81,0);driver.setAutomaticUnitJoin(false);driver.setPersistenceRequired();level.addFreshEntity(driver);driver.startRiding(car,true);
            if(AUDIO&&scene<3){driver.stopRiding();driver.discard();var military=ModEntities.MILITARY_SOLDIER.get().create(level);military.setPos(0,81,0);military.setAutomaticUnitJoin(false);military.setPersistenceRequired();level.addFreshEntity(military);military.startRiding(car,true);driverId=military.getId();}
            if(DENTS){driver.stopRiding();driver.discard();}
            car.inventory().addAmmunition("tank_he_shell",3);car.inventory().addAmmunition("autocannon_round",20);car.inventory().addAmmunition("machine_gun_round",40);
            var attacker=EntityType.ARMOR_STAND.create(level);attacker.setInvisible(true);attacker.setNoGravity(true);attacker.setPos(scene==3?-8:8,81,0);attacker.setInvulnerable(true);level.addFreshEntity(attacker);
            if(!DENTS&&!AUDIO){
                if(scene==9)attacker.setPos(1.3,88,1.5);
                if(scene==8)level.setBlock(new BlockPos(3,82,0),Blocks.OBSIDIAN.defaultBlockState(),2);
                if(scene==6){driver.stopRiding();p.setGameMode(GameType.SURVIVAL);p.connection.teleport(0,81,0,0,0);p.startRiding(car,true);}
            }
            carId=car.getId();if(!AUDIO||scene>=3)driverId=driver.getId();attackerId=attacker.getId();ready=true;
        }catch(Exception failure){error=failure.toString();}});
    }
    private static void dentAction(int scene,int age,MilitaryVehicle car){
        var level=(net.minecraft.server.level.ServerLevel)car.level();boolean action=false;
        var actor=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.fromString("529836c5-afb2-4d73-bc1d-74a1538e8123"),"VehicleDentQA"));
        actor.setGameMode(GameType.SURVIVAL);
        Vec3 local=switch(scene){case 1->new Vec3(0,1.9,3.75);case 2->new Vec3(1.65,1.65,1);case 3->new Vec3(1.2,2.6,.2);case 4->new Vec3(1.1,2,2.8);default->new Vec3(2,1.65,1);};
        Vec3 surface=car.position().add(local);
        Vec3 from=surface.add(scene==1?new Vec3(0,0,4):scene==4?new Vec3(0,5,0):new Vec3(4,0,0));
        actor.setPos(from.subtract(0,actor.getEyeHeight(),0));
        if(age==8||age==28){
            var hit=VehicleDamageBridge.nearest(level,actor,null,from,surface.subtract(from.subtract(surface).normalize().scale(2)));
            if(hit==null)throw new IllegalStateException("No mechanical skin contact");
            action=VehicleDamageBridge.hurtPhysical(actor,car,scene==1?20:80,null,hit.point());
        }
        if(scene==3&&age>=38&&age<68){
            var tag=new net.minecraft.nbt.CompoundTag();car.addAdditionalSaveData(tag);tag.putFloat("VehicleTurretYaw",(age-37)*2);car.readAdditionalSaveData(tag);
        }
        if(scene==5&&age==53){car.repair(1000);action=true;}
        var row=new JsonObject();row.addProperty("tick",age);row.addProperty("action",action);row.addProperty("health",car.health());row.addProperty("dents",car.dents().size());
        row.addProperty("depth",car.dents().stream().mapToDouble(VehicleDeformation.Dent::depth).max().orElse(0));row.addProperty("turretYaw",car.turretYaw());
        if(!car.dents().isEmpty())row.addProperty("bone",car.dents().get(0).bone());receipts.add(row);
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!initialized||done||!ready||tick<60||tick>=60+DURATION||tick==last)return;
        if(AUDIO&&(tick-60)%3!=0)return;
        var mc=Minecraft.getInstance();if(!(mc.level.getEntity(carId) instanceof MilitaryVehicle car))return;
        try{
            String name=CASES[index]+"-"+String.format(Locale.ROOT,"%03d",tick-60)+".png";
            try(var picture=Screenshot.takeScreenshot(mc.getMainRenderTarget())){picture.writeToFile(output.resolve(name));}
            var pose=car.feedback(mc.getFrameTime());var row=new JsonObject();row.addProperty("image",name);row.addProperty("tick",tick-60);row.addProperty("pitch",pose.pitch());row.addProperty("roll",pose.roll());row.addProperty("recoil",pose.primaryRecoil());row.addProperty("secondaryRecoil",pose.secondaryRecoil());row.addProperty("cameraPitch",mc.gameRenderer.getMainCamera().getXRot());row.addProperty("cameraRoll",Math.toDegrees(mc.gameRenderer.getMainCamera().rotation().getEulerAnglesYXZ(new org.joml.Vector3f()).z));row.addProperty("dents",car.dents().size());row.addProperty("depth",car.dents().stream().mapToDouble(VehicleDeformation.Dent::depth).max().orElse(0));frames.add(row);last=tick;
            if(AUDIO){
                row.addProperty("engineRunning",car.engineRunning());row.addProperty("driveLoad",car.driveLoad());
                var field=VehicleAudio.class.getDeclaredField("LOOPS");field.setAccessible(true);var loops=(Map<?,?>)field.get(null);var pair=loops.get(car);
                row.addProperty("loopPairs",loops.size());
                if(pair!=null)for(String key:List.of("engine","rolling")){var method=pair.getClass().getDeclaredMethod(key);method.setAccessible(true);var sound=(net.minecraft.client.resources.sounds.SoundInstance)method.invoke(pair);row.addProperty(key+"Active",mc.getSoundManager().isActive(sound));row.addProperty(key+"Volume",sound.getVolume());row.addProperty(key+"Pitch",sound.getPitch());}
            }
        }catch(Exception failure){error=failure.toString();}
    }
    private static void audioAction(int age,MilitaryVehicle car,MilitarySoldier driver){
        if(age>=10&&age<30)car.moveVehicle(.10F,0);
        if(age>=30&&age<50)car.moveVehicle(-.10F,0);
        if(age>=50&&age<65)car.moveVehicle(0,2);
        if(age==65)car.moveVehicle(0,0);
        boolean fired=false;
        if(age==16||age==42){String weapon=car.chassisId().equals("tank")?"tank_he":"autocannon";if(car.definition().weapons().containsKey(weapon))fired=VehicleAttackService.fire(car,driver,weapon);}
        if(age==36||age==40||age==44){String weapon=car.chassisId().equals("tank")?"coaxial_machine_gun":"vehicle_rocket";if(car.definition().weapons().containsKey(weapon))fired=VehicleAttackService.fire(car,driver,weapon);}
        if(age==85)driver.stopRiding();
        var row=new JsonObject();row.addProperty("tick",age);row.addProperty("engineRunning",car.engineRunning());row.addProperty("driveLoad",car.driveLoad());row.addProperty("fired",fired);receipts.add(row);
    }
}
