package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.client.renderer.VehicleDestructionVisuals;
import com.codex.blacklight.network.VehicleDestructionPayload;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Opt-in destruction receipts from real server damage in a hidden disposable world. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleDestructionVisual {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/neo-client",WORLD="Creature Skills QA Final";
    private static final String[] NAMES={"truck","apc","tank","helicopter","submerged_tank"};
    private static final JsonArray frames=new JsonArray(),results=new JsonArray();private static final Set<String> saved=new HashSet<>(),sounds=new TreeSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,age,scene,removedAt=-1;
    private static boolean initialized,queued;private static int bootstrap;private static long started,received;private static Path output;private static Entity actor;
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent e){if(initialized)sounds.add(e.getName());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(System.getProperty("blacklight.test.vehicleDestruction","").isBlank()||done)return;var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("QA directory only");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;}
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("QA save only");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){output=Path.of(System.getProperty("blacklight.test.vehicleDestruction")).toAbsolutePath().normalize();if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(45);mc.options.framerateLimit().set(45);mc.options.particles().set(ParticleStatus.ALL);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"RUNNING");}
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;received=VehicleDestructionVisuals.received();setup();return;}
            if(ready&&age>440){
                if(VehicleDestructionVisuals.received()-received!=1)throw new IllegalStateException("Expected exactly one receipt: "+(VehicleDestructionVisuals.received()-received));
                if(VehicleDestructionVisuals.active()!=0)throw new IllegalStateException("Wreck did not expire");
                var row=new JsonObject();row.addProperty("case",NAMES[scene]);row.addProperty("receipts",VehicleDestructionVisuals.received()-received);row.addProperty("removedAt",removedAt);row.addProperty("expired",true);results.add(row);
                ready=false;if(++scene<NAMES.length){queued=false;return;}
                var p=mc.player.position();long now=mc.level.getGameTime();long before=VehicleDestructionVisuals.received();
                for(int i=0;i<12;i++){var receipt=new VehicleDestructionPayload(90000+i,i%4,true,p.x+4,p.y,p.z,0,0,0,false,now,i);VehicleDestructionVisuals.accept(receipt);VehicleDestructionVisuals.accept(receipt);}
                if(VehicleDestructionVisuals.active()!=8||VehicleDestructionVisuals.received()-before!=12)throw new IllegalStateException("Cap or duplicate suppression failed");
                done=true;write();Files.writeString(output.resolve("status.txt"),"PASS: five native destruction cases, duplicates, expiry and cap");mc.stop();
            }
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Vehicle destruction QA",ex);try{if(output!=null){write();Files.writeString(output.resolve("status.txt"),error);}}catch(Exception ignored){}mc.stop();}
    }
    private static void write()throws Exception {var json=new JsonObject();json.add("frames",frames);json.add("results",results);json.add("sounds",new Gson().toJsonTree(sounds));json.addProperty("error",error);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(json));}
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        for(var e:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++){level.setBlock(new BlockPos(x,80,z),((x%4==0||z%4==0)?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(),2);for(int y=81;y<103;y++)level.setBlock(new BlockPos(x,y,z),(scene==4&&Math.abs(x)<9&&Math.abs(z)<9&&y<=85?Blocks.WATER:Blocks.AIR).defaultBlockState(),2);}
        actor=switch(scene){case 0->ModEntities.MILITARY_TRUCK.get().create(level);case 1->ModEntities.BLACKWATCH_APC.get().create(level);case 2,4->ModEntities.MILITARY_TANK.get().create(level);default->ModEntities.BLACKWATCH_HELICOPTER.get().create(level);};
        actor.setPos(0,scene==3?90:81,0);actor.setYRot(scene%2==0?25:110);level.addFreshEntity(actor);
        p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        Vec3 camera=new Vec3(15,87,21),look=new Vec3(0,scene==3?85:82.5,0).subtract(camera.add(0,p.getEyeHeight(),0));p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        actorId=actor.getId();started=level.getGameTime()+25;age=-25;removedAt=-1;ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;
        age=(int)(e.getLevel().getGameTime()-started);
        try{
            if(age==20){
                if(!actor.hurt(actor.damageSources().generic(),100000))throw new IllegalStateException("Lethal damage not accepted");
                for(int i=0;i<5;i++)if(actor.hurt(actor.damageSources().generic(),100000))throw new IllegalStateException("Repeated destruction damage accepted");
                if(scene==3&&actor.isRemoved())throw new IllegalStateException("Helicopter skipped crash flight");
            }
            if(actor.isRemoved()&&removedAt<0)removedAt=age;
            if(age==110&&!actor.isRemoved())throw new IllegalStateException("Destruction never finished");
            if(age==125)for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)if(e.getLevel().getBlockState(new BlockPos(x,80,z)).isAir())throw new IllegalStateException("Visual explosion destroyed terrain");
        }catch(Exception ex){error=ex.toString();}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done||scene>=NAMES.length)return;var mc=Minecraft.getInstance();int t=age;
        if(t<0||t%4!=0&&!(t>=18&&t<=38))return;String name=NAMES[scene]+"-"+String.format(Locale.ROOT,"%03d",t)+".png";if(!saved.add(name))return;
        try{var row=new JsonObject();row.addProperty("image",name);row.addProperty("case",NAMES[scene]);row.addProperty("age",t);row.addProperty("active",VehicleDestructionVisuals.active());row.addProperty("received",VehicleDestructionVisuals.received()-received);row.add("wreck",geometry());try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static JsonObject geometry()throws Exception{
        var f=VehicleDestructionVisuals.class.getDeclaredField("SCENES");f.setAccessible(true);var scenes=(java.util.Collection<?>)f.get(null);var row=new JsonObject();if(scenes.isEmpty())return row;
        var s=scenes.iterator().next();var ground=field(s,"ground");var model=ground==null?field(s,"helicopter"):ground;
        var root=(net.minecraft.client.model.geom.ModelPart)field(model,"root");var origin=(Vec3)field(s,"origin");double[] low={Double.POSITIVE_INFINITY};int[] cubes={0};
        root.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{cubes[0]++;for(float x:new float[]{cube.minX,cube.maxX})for(float y:new float[]{cube.minY,cube.maxY})for(float z:new float[]{cube.minZ,cube.maxZ})low[0]=Math.min(low[0],pose.pose().transformPosition(new org.joml.Vector3f(x/16,y/16,z/16)).y);});
        row.addProperty("lowest",origin.y+low[0]);row.addProperty("cubes",cubes[0]);if(origin.y+low[0]<80.95)throw new IllegalStateException("Rendered wreck penetrates floor: "+(origin.y+low[0]));return row;
    }
}
