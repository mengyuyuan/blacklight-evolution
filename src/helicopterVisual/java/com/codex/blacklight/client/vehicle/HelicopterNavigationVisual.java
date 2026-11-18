package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.aircraft.HelicopterNavigation;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Disposable native-client proof. Only fixtures/camera are positioned: the mission flies itself. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HelicopterNavigationVisual {
    private static Path output;private static boolean initialized,done;private static int ticks,labelAge;
    private static String lastLabel="";
    private static volatile int craftId=-1;private static volatile String error,label="",evidence;
    private static final Set<String> captured=new TreeSet<>(),sounds=new TreeSet<>();
    // Below this line the mutable mission state belongs exclusively to the integrated-server thread.
    private static final JsonArray frames=new JsonArray(),missions=new JsonArray();
    private static final Map<Long,Boolean> forced=new HashMap<>();private static final List<Entity> crew=new ArrayList<>();
    private static BlackwatchHelicopter aircraft;private static int scene,side,sceneTicks,forcedCount,maxBudget,returnTicks;
    private static long lastServerTick=Long.MIN_VALUE;private static boolean changed,delivered;
    private static double maximumY,opposite;private static UUID original;

    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void wall(ServerLevel level,int from,int to,int top){
        for(int z=from;z<=to;z++)for(int y=81;y<=top;y++)for(int x=0;x<=1;x++){
            var block=y%7==0?Blocks.POLISHED_ANDESITE:(z%4==0&&y%7>=2&&y%7<=4?Blocks.LIGHT_BLUE_CONCRETE:Blocks.GRAY_CONCRETE);
            level.setBlock(new BlockPos(x,y,z),block.defaultBlockState(),2);
        }
    }
    private static void prepare(ServerLevel level){
        if(aircraft!=null)aircraft.discard();crew.forEach(Entity::discard);crew.clear();
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(-62,75,-62,62,140,62),e->!(e instanceof net.minecraft.world.entity.player.Player)))entity.discard();
        for(int x=-58;x<=58;x++)for(int z=-58;z<=58;z++){
            level.setBlock(new BlockPos(x,80,z),(Math.abs(x-24)<=7&&Math.abs(z)<=7?Blocks.SMOOTH_QUARTZ:Blocks.SMOOTH_STONE).defaultBlockState(),2);
            for(int y=81;y<=135;y++){var pos=new BlockPos(x,y,z);if(!level.isEmptyBlock(pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
        }
        wall(level,scene==0?-12:-56,scene==0?12:56,scene==0?134:100);
        aircraft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);aircraft.setPos(-24,97,0);level.addFreshEntity(aircraft);original=aircraft.getUUID();
        for(int i=0;i<6;i++){
            var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);soldier.setPos(aircraft.position());soldier.setAutomaticUnitJoin(false);soldier.setPersistenceRequired();soldier.setNoAi(i>=2);
            soldier.getPersistentData().putBoolean("HelicopterTroop",i>=2);soldier.initializeNativeLoadout("air-route-visual:"+scene+":"+i);level.addFreshEntity(soldier);
            require(soldier.startRiding(aircraft,true),"real original crew mount");crew.add(soldier);
        }
        require(aircraft.deployTo(new Vec3(24,81,0)),"supported destination");craftId=aircraft.getId();sceneTicks=returnTicks=0;changed=delivered=false;maximumY=97;opposite=0;side=0;maxBudget=0;
    }
    private static void advance(ServerLevel level){
        if(evidence!=null||error!=null||aircraft==null||lastServerTick==level.getGameTime())return;lastServerTick=level.getGameTime();sceneTicks++;
        require(sceneTicks<2400,"navigation scene timeout "+scene+" / "+aircraft.phase()+" / "+aircraft.navigation().status()+" / "+aircraft.position());
        require(!aircraft.isRemoved(),"aircraft left before return evidence");
        require(aircraft.clearAt(aircraft.position(),aircraft.getYRot()),"actual hull/rotor collision during "+aircraft.phase());
        require(aircraft.health()==360&&original.equals(aircraft.getUUID())&&crew.stream().allMatch(Entity::isAlive),"same healthy craft and original living crew");
        require(level.getForcedChunks().size()==forcedCount,"navigation changed chunk tickets");
        int budget=HelicopterNavigation.nodesUsed(level);maxBudget=Math.max(maxBudget,budget);require(budget<=HelicopterNavigation.NODES_PER_LEVEL,"world search budget");
        maximumY=Math.max(maximumY,aircraft.getY());
        if(scene==0&&!changed&&Math.abs(aircraft.getZ())>8&&aircraft.getX()<-7.2){changed=true;side=aircraft.getZ()>0?1:-1;wall(level,side>0?12:-56,side>0?56:-12,134);}
        if(changed)opposite=Math.max(opposite,-side*aircraft.getZ());
        if(aircraft.getPassengers().size()==2&&crew.subList(2,6).stream().noneMatch(Entity::isPassenger))delivered=true;
        String prefix=scene==0?"TOWER_":"LOW_";var phase=aircraft.phase();
        if(phase==BlackwatchHelicopter.Phase.APPROACH){
            if(scene==0)label=prefix+(changed&&opposite<18?"REPLAN":opposite>19?"DETOUR":aircraft.navigation().searching()?"SEARCH":"APPROACH");
            else label=prefix+(aircraft.getY()>101?"CROSSING":aircraft.getY()>98?"CLIMB":aircraft.navigation().searching()?"SEARCH":"APPROACH");
        }else label=prefix+phase.name();
        var row=new JsonObject();row.addProperty("scene",scene);row.addProperty("tick",sceneTicks);row.addProperty("phase",phase.name());row.addProperty("navigation",aircraft.navigation().status().name());
        row.addProperty("x",aircraft.getX());row.addProperty("y",aircraft.getY());row.addProperty("z",aircraft.getZ());row.addProperty("yaw",aircraft.getYRot());row.addProperty("passengers",aircraft.getPassengers().size());
        row.addProperty("searches",aircraft.navigation().searches());row.addProperty("invalidations",aircraft.navigation().invalidations());row.addProperty("expanded",aircraft.navigation().expanded());row.addProperty("worldNodes",budget);row.addProperty("waypoints",aircraft.navigation().waypoints().size());frames.add(row);
        if(phase==BlackwatchHelicopter.Phase.RETURN&&++returnTicks>24){
            require(delivered&&aircraft.getPassengers().size()==2,"four original troops delivered before return");
            require(aircraft.navigation().searches()>0,"real obstacle caused route search");
            if(scene==0)require(changed&&opposite>19&&aircraft.navigation().invalidations()>0&&aircraft.navigation().searches()>=2,"blocked route replanned to opposite side");
            else require(maximumY>100.9,"low wall crossed in three dimensions");
            var result=new JsonObject();result.addProperty("scene",scene);result.addProperty("ticks",sceneTicks);result.addProperty("sameCraft",original.toString());result.addProperty("delivered",4);result.addProperty("maxHeight",maximumY);result.addProperty("oppositeExcursion",opposite);result.addProperty("maxWorldNodes",maxBudget);result.addProperty("searches",aircraft.navigation().searches());result.addProperty("invalidations",aircraft.navigation().invalidations());
            var ids=new JsonArray();crew.forEach(e->ids.add(e.getUUID().toString()));result.add("originalCrew",ids);missions.add(result);
            if(++scene<2)prepare(level);else{
                aircraft.discard();crew.forEach(Entity::discard);
                // Remove only this fixture's wall before another QA profile reuses the disposable world.
                for(int x=0;x<=1;x++)for(int z=-56;z<=56;z++)for(int y=81;y<=134;y++){var pos=new BlockPos(x,y,z);if(!level.isEmptyBlock(pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
                forced.forEach((key,value)->{var chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,value);});
                var report=new JsonObject();report.add("missions",missions);report.add("frames",frames);evidence=new GsonBuilder().setPrettyPrinting().create().toJson(report);
            }
        }
    }
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event){if(initialized&&event.getName().startsWith("vehicle.helicopter"))sounds.add(event.getName());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done||System.getProperty("blacklight.test.helicopterNavigation","").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
        try{
            require(mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').startsWith("D:/CodexCache/creature-combat-qa/")&&server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"),"disposable native QA world required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.helicopterNavigation")).toAbsolutePath().normalize();require(output.startsWith(Path.of("D:/CodexCache")),"D: output required");Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"RUNNING");initialized=true;
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(55);mc.options.framerateLimit().set(30);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);
                var id=mc.player.getUUID();server.execute(()->{try{
                    var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);
                    for(int cx=-4;cx<=4;cx++)for(int cz=-4;cz<=4;cz++){long key=ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}forcedCount=level.getForcedChunks().size();
                    var player=server.getPlayerList().getPlayer(id);player.setGameMode(GameType.SPECTATOR);player.connection.teleport(-38,105,18,0,20);prepare(level);
                }catch(Exception failure){error=failure.toString();}});return;
            }
            ticks++;mc.getToasts().clear();mc.mouseHandler.releaseMouse();for(var key:mc.options.keyMappings)key.setDown(false);require(ticks<5000,"native client timed out");
            if(evidence!=null){
                Files.writeString(output.resolve("evidence.json"),evidence);Files.writeString(output.resolve("sounds.json"),new Gson().toJson(sounds));
                require(captured.containsAll(List.of("TOWER_SEARCH","TOWER_REPLAN","TOWER_DETOUR","TOWER_DESCEND","TOWER_UNLOAD","TOWER_RETURN","LOW_SEARCH","LOW_CROSSING","LOW_DESCEND","LOW_UNLOAD","LOW_RETURN")),"missing screenshots "+captured);
                require(sounds.containsAll(List.of("vehicle.helicopter_rotor","vehicle.helicopter_turbine","vehicle.helicopter_door")),"missing actual sound events "+sounds);
                Files.writeString(output.resolve("status.txt"),"PASS: actual dynamic tower replan and low wall crossing, original troops delivered, return observed; visual review required");done=true;mc.stop();return;
            }
            if(!label.equals(lastLabel)){lastLabel=label;labelAge=0;}else labelAge++;
            var entity=mc.level.getEntity(craftId);if(!(entity instanceof BlackwatchHelicopter craft))return;
            Vec3 camera=craft.position().add(craft.getX()<0?-11:11,6,craft.getZ()<0?-14:14),look=craft.position().add(0,1.6,0).subtract(camera.add(0,mc.player.getEyeHeight(),0));
            float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));
            mc.player.setPos(camera);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;var id=mc.player.getUUID();
            server.execute(()->{try{var player=server.getPlayerList().getPlayer(id);if(player!=null){player.setPos(camera);player.setYRot(yaw);player.setXRot(pitch);}advance(server.overworld());}catch(Exception failure){error=failure.toString();}});
        }catch(Exception failure){done=true;failure.printStackTrace();try{if(output!=null)Files.writeString(output.resolve("status.txt"),"FAIL: "+failure);}catch(Exception ignored){}mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(!initialized||done||labelAge<3||lastLabel.isEmpty()||captured.contains(lastLabel))return;
        try(var shot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){shot.writeToFile(output.resolve(lastLabel+".png"));captured.add(lastLabel);}catch(Exception failure){error=failure.toString();}
    }
}
