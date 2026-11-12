package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.vehicle.VehicleThreatBridge;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real renderer/server capture; opt-in source set, only a disposable D: QA world. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class P1MilitaryVisual {
    private static final String PROPERTY="blacklight.test.p1MilitaryVisual",WORLD="Blackwatch P1 QA";
    private static final String[] CASES={"stock_contact","rifle_reload","saw_fire","tank_main_gun","tank_regroup","kneeling_reload"};
    private static boolean opening,initialized,done,queued;
    private static volatile boolean ready;
    private static volatile int actorId=-1,targetId=-1,vehicleId=-1;
    private static volatile String serverError;
    private static int index,tick,lastCapture=-1,renderedId=-1;
    private static Path output;
    private static final JsonArray frames=new JsonArray();
    private static final List<JsonObject> serverFrames=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static int duration(){return index==0?28:index==1||index==5?90:65;}
    private static boolean vehicleCase(int scenario){return scenario==3||scenario==4;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(System.getProperty(PROPERTY,"").isBlank()||done)return;
        var mc=Minecraft.getInstance();
        try{
            if(!opening&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.screen.onClose();return;}
            if(!opening&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){
                if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase("D:/CodexCache/p1-military-qa/client"))throw new IllegalStateException("Refused non-QA directory");
                opening=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
                if(Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD)))throw new IllegalStateException("Existing QA save; use explicit quick play");
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SPECTATOR,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(7716L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            var server=mc.getSingleplayerServer();if(server==null||mc.level==null||mc.player==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("Refused non-QA world");
            if(serverError!=null)throw new IllegalStateException(serverError);
            if(!initialized){
                output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: output required");
                Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"Capturing current build");initialized=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(55);mc.options.framerateLimit().set(30);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.setScreen(null);mc.mouseHandler.releaseMouse();
                mc.getWindow().setTitle("Blackwatch P1 - isolated frame verification");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;tick=0;lastCapture=-1;frames.asList().clear();serverFrames.clear();setup();return;}
            if(!ready)return;tick++;
            if(tick>=80){
                final int age=tick-80;final int scenario=index;
                server.execute(()->{
                    try{
                        var level=server.overworld();var a=(MilitarySoldier)level.getEntity(actorId);var v=(LivingEntity)level.getEntity(targetId);
                        if(a==null||v==null)throw new IllegalStateException("Missing QA actors");
                        if(age==0){a.setNoAi(false);if(!vehicleCase(scenario))a.setTarget(v);}
                        if(vehicleCase(scenario))VehicleThreatBridge.engage((MilitaryVehicle)level.getEntity(vehicleId),v);
                        if(scenario==4)com.codex.blacklight.military.vehicle.VehicleCombatSpacing.tick((MilitaryVehicle)level.getEntity(vehicleId),v);
                        var row=new JsonObject();row.addProperty("tick",age);row.addProperty("gameTime",level.getGameTime());row.addProperty("phase",a.phase());row.addProperty("magazine",a.magazineAmmo());row.addProperty("meleeStarted",a.meleeStarted());row.addProperty("victimHealth",v.getHealth());
                        row.addProperty("actorZ",a.getZ());
                        row.addProperty("crouching",a.isCrouching());
                        if(vehicleCase(scenario)){var car=(MilitaryVehicle)level.getEntity(vehicleId);row.addProperty("he",car.inventory().ammunition("tank_he_shell"));row.addProperty("coax",car.inventory().ammunition("machine_gun_round"));row.addProperty("turretYaw",car.turretYaw());row.addProperty("vehicleZ",car.getZ());}
                        serverFrames.add(row);
                    }catch(Exception error){serverError=error.toString();}
                });
            }
            if(tick>=80+duration()){
                Files.writeString(output.resolve(CASES[index]+"-frames.json"),new GsonBuilder().setPrettyPrinting().create().toJson(frames));
                Files.writeString(output.resolve(CASES[index]+"-server.json"),new GsonBuilder().setPrettyPrinting().create().toJson(serverFrames));
                if(++index==CASES.length){done=true;Files.writeString(output.resolve("status.txt"),"Captured six production-path cases; visual review required");mc.stop();}else queued=false;
            }
        }catch(Exception error){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("P1 military visual QA",error);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error.toString());}catch(Exception ignored){}mc.stop();}
    }
    private static void setup(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();final int scenario=index;
        server.execute(()->{
            try{
                var level=server.overworld();
                // Only this explicitly verified disposable QA world is touched on a repeat run.
                for(var old:level.getEntitiesOfClass(Entity.class,new net.minecraft.world.phys.AABB(-16,80,-12,20,94,36),entity->entity instanceof MilitarySoldier||entity instanceof MilitaryVehicle||entity instanceof BlacklightInfectedHost))old.discard();
                for(int id:new int[]{actorId,targetId,vehicleId}){var old=level.getEntity(id);if(old!=null)old.discard();}
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);
                for(int x=-12;x<=16;x++)for(int z=-16;z<=30;z++){level.setBlock(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);for(int y=81;y<=88;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
                if(scenario==5)for(int x=-1;x<=1;x++)level.setBlock(new BlockPos(x,81,1),Blocks.STONE_BRICKS.defaultBlockState(),2);
                var p=server.getPlayerList().getPlayer(playerId);Vec3 camera=vehicleCase(scenario)?new Vec3(10,85,14):new Vec3(5,81.8,4);
                if(scenario==5)camera=new Vec3(5,81.8,-2);
                Vec3 look=(vehicleCase(scenario)?new Vec3(0,82.5,8):new Vec3(0,82,1)).subtract(camera.add(0,p.getEyeHeight(),0));
                p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,Math.sqrt(look.x*look.x+look.z*look.z))));
                var a=ModEntities.BLACKWATCH_SOLDIER.get().create(level);a.setPos(0,81,0);a.setYRot(0);a.setYHeadRot(0);a.setYBodyRot(0);a.setNoAi(true);a.setAutomaticUnitJoin(false);a.setPersistenceRequired();a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);level.addFreshEntity(a);a.initializeWeapon();
                var definition=scenario==2?WeaponCatalog.MACHINE_GUN:WeaponCatalog.RIFLE;var gun=new ItemStack(FirearmsRegistry.weapon(definition.id()));FirearmItem.store(gun,new AmmoState(scenario==1||scenario==5?1:definition.capacity(),0,0));a.setItemSlot(EquipmentSlot.MAINHAND,gun);a.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);a.receiveAmmo(FirearmsRegistry.ammo(definition.ammoId()),definition.capacity()*3);
                var v=ModEntities.INFECTED_HOST.get().create(level);v.inherit(EntityType.COW.create(level));v.setNoAi(true);v.setPersistenceRequired();v.setPos(0,81,scenario==0?2:scenario==3?21:scenario==4?8:10);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.setHealth(1000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);level.addFreshEntity(v);
                vehicleId=-1;if(vehicleCase(scenario)){var car=ModEntities.BLACKWATCH_TANK.get().create(level);car.setPos(0,81,0);level.addFreshEntity(car);a.startRiding(car,true);car.inventory().addAmmunition("tank_he_shell",2);car.inventory().addAmmunition("machine_gun_round",20);if(scenario==3)v.getAttribute(Attributes.SCALE).setBaseValue(2.5);vehicleId=car.getId();}
                actorId=a.getId();targetId=v.getId();ready=true;
            }catch(Exception error){serverError=error.toString();}
        });
    }
    @SubscribeEvent public static void drawn(RenderLivingEvent.Post<?,?> e){if(e.getEntity().getId()==actorId)renderedId=actorId;}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||done||!ready||tick<80||tick>=80+duration()||tick==lastCapture)return;
        var mc=Minecraft.getInstance();if(!(mc.level.getEntity(actorId) instanceof MilitarySoldier actor))return;
        try{
            String name=CASES[index]+"-"+String.format(Locale.ROOT,"%03d",tick-80)+".png";
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("tick",tick-80);row.addProperty("phase",actor.phase());row.addProperty("meleeStarted",actor.meleeStarted());row.addProperty("actorRendered",renderedId==actorId);row.addProperty("gameTime",mc.level.getGameTime());frames.add(row);lastCapture=tick;renderedId=-1;
        }catch(Exception error){serverError=error.toString();}
    }
}
