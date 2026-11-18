package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.*;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real flight and passenger capture, in the explicit disposable creature QA world only. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HelicopterVisual {
    private static Path output;private static boolean initialized,done,crashQueued;private static int ticks;
    private static volatile int craftId=-1;private static volatile String error;private static volatile boolean finished;
    private static final JsonArray frames=new JsonArray();
    private static final Set<String> captured=new HashSet<>();private static final Set<String> sounds=new TreeSet<>();
    private static String phase="";private static int phaseAge;
    private static final boolean combatMode=Boolean.getBoolean("blacklight.test.helicopterCombat");
    private static volatile int enemyId=-1;private static int clientShots;private static double maximumMountError;
    private static double mountError(BlackwatchHelicopter craft)throws Exception{
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(craft);
        var modelField=renderer.getClass().getDeclaredField("model");modelField.setAccessible(true);
        var model=(com.codex.blacklight.client.model.HelicopterModel)modelField.get(renderer);var root=model.pose(craft,0);
        double error=0;
        for(int index=0;index<9;index++){int side=index==0?0:index<5?-1:1,tube=(index-1)%4;
            var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(craft.getYRot()));pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(craft.bank()));
            root.getChild(side==0?"gun":side<0?"pod_left":"pod_right").translateAndRotate(pose);
            var tip=side==0?new Vec3(0,0,1.2):com.codex.blacklight.military.aircraft.HelicopterHellfireRack.slot(tube);
            var rendered=pose.last().pose().transformPosition(new org.joml.Vector3f((float)tip.x,(float)tip.y,(float)tip.z));
            var pivot=side==0?com.codex.blacklight.military.aircraft.HelicopterArmament.GUN:com.codex.blacklight.military.aircraft.HelicopterArmament.pod(side);
            var aim=side==0?new com.codex.blacklight.military.aircraft.HelicopterArmament.Aim(craft.gunYaw(),craft.gunPitch()):craft.podAim(side);
            var direction=com.codex.blacklight.military.aircraft.HelicopterArmament.rotate(com.codex.blacklight.military.aircraft.HelicopterArmament.direction(aim.yaw(),aim.pitch()),craft.getYRot(),craft.bank());
            var actual=side==0?craft.weaponPoint(pivot).add(direction.scale(1.2)):craft.hellfireLaunchPoint(side,tube);
            error=Math.max(error,actual.distanceTo(craft.position().add(rendered.x,rendered.y,rendered.z)));
        }
        return error;
    }
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event){
        if(initialized&&(event.getName().startsWith("vehicle.helicopter")||combatMode&&(event.getName().equals("vehicle.coaxial")||event.getName().equals("vehicle.rocket"))))sounds.add(event.getName());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done||System.getProperty("blacklight.test.helicopterVisual","").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').startsWith("D:/CodexCache/creature-combat-qa/")
                    ||!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Not the disposable helicopter QA world");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.helicopterVisual")).toAbsolutePath().normalize();if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: required");Files.createDirectories(output);
                Files.writeString(output.resolve("status.txt"),"RUNNING");initialized=true;
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(55);mc.options.framerateLimit().set(30);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);
                var playerId=mc.player.getUUID();server.execute(()->{try{
                    var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);
                    for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(-48,75,-40,72,125,40),e->!(e instanceof net.minecraft.world.entity.player.Player)))entity.discard();
                    for(int x=-36;x<=56;x++)for(int z=-20;z<=20;z++){
                        level.setBlock(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                        for(int y=81;y<=110;y++)if(!level.isEmptyBlock(new BlockPos(x,y,z)))level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                    }
                    var p=server.getPlayerList().getPlayer(playerId);p.setGameMode(GameType.SPECTATOR);p.connection.teleport(0,102,25,0,20);
                    var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);craft.setPos(-20,81,0);level.addFreshEntity(craft);
                    for(int i=0;i<6;i++){
                        var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);soldier.setPos(craft.position());soldier.setAutomaticUnitJoin(false);soldier.setPersistenceRequired();soldier.initializeNativeLoadout("native-air-qa:"+i);
                        soldier.getPersistentData().putBoolean("HelicopterTroop",i>=2);if(combatMode&&i>=2)soldier.setNoAi(true);level.addFreshEntity(soldier);if(!soldier.startRiding(craft,true))throw new IllegalStateException("Passenger failed to mount");
                    }
                    if(!craft.deployTo(new Vec3(22,81,0)))throw new IllegalStateException("Fixture landing refused");craftId=craft.getId();
                    if(combatMode){
                        var enemy=ModEntities.COMMON_INFECTED.get().create(level);enemy.setPos(22,81,18);enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(4000);enemy.setHealth(4000);level.addFreshEntity(enemy);enemyId=enemy.getId();
                        var caller=ModEntities.BLACKWATCH_OFFICER.get().create(level);caller.setPos(-30,81,0);caller.setAutomaticUnitJoin(false);level.addFreshEntity(caller);caller.setTarget(enemy);craft.combat().supportCall(caller);caller.discard();
                    }
                }catch(Exception failure){error=failure.toString();}});return;
            }
            ticks++;mc.getToasts().clear();mc.mouseHandler.releaseMouse();for(var key:mc.options.keyMappings)key.setDown(false);
            if(ticks>2300)throw new IllegalStateException("Flight capture timed out");
            if(finished){
                Files.writeString(output.resolve("frames.json"),new GsonBuilder().setPrettyPrinting().create().toJson(frames));
                Files.writeString(output.resolve("sounds.json"),new Gson().toJson(sounds));
                if(!captured.contains("UNLOAD")||!captured.contains("RETURN")||!captured.contains("CRASH")||!sounds.contains("vehicle.helicopter_rotor")||!sounds.contains("vehicle.helicopter_turbine")||!sounds.contains("vehicle.helicopter_door")||!sounds.contains("vehicle.helicopter_alarm"))throw new IllegalStateException("Missing flight/cue evidence "+captured+" "+sounds);
                if(combatMode&&(!captured.contains("STRAFE_FIRE")||!sounds.contains("vehicle.coaxial")||!sounds.contains("vehicle.rocket")||maximumMountError>.0001))throw new IllegalStateException("Combat evidence missing or mount mismatch: "+captured+" "+sounds+" "+maximumMountError);
                Files.writeString(output.resolve("mount-error.txt"),Double.toString(maximumMountError));
                Files.writeString(output.resolve("status.txt"),"PASS: actual flight, landing, passengers, return and crash captured; visual review required");done=true;mc.stop();return;
            }
            var craft=mc.level.getEntity(craftId);if(!(craft instanceof BlackwatchHelicopter aircraft)){
                if(crashQueued)server.execute(()->{if(server.overworld().getEntity(craftId)==null)finished=true;});return;
            }
            String next=aircraft.phase().name();if(!next.equals(phase)){phase=next;phaseAge=0;}else phaseAge++;
            var at=aircraft.position();double side=phase.equals("UNLOAD")&&phaseAge>60?-1:1;
            Vec3 camera=at.add(11*side,6,14);Vec3 look=at.add(0,1.6,0).subtract(camera.add(0,mc.player.getEyeHeight(),0));
            float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));
            mc.player.setPos(camera);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;
            clientShots=aircraft.airShots();maximumMountError=Math.max(maximumMountError,mountError(aircraft));
            var id=mc.player.getUUID();boolean removeEnemy=combatMode&&phase.equals("STRAFE")&&phaseAge>240;boolean crash=phase.equals("RETURN")&&phaseAge>20&&!crashQueued;if(crash)crashQueued=true;
            server.execute(()->{var p=server.getPlayerList().getPlayer(id);if(p!=null){p.setPos(camera);p.setYRot(yaw);p.setXRot(pitch);}var entity=server.overworld().getEntity(craftId);
                if(removeEnemy&&server.overworld().getEntity(enemyId)!=null)server.overworld().getEntity(enemyId).discard();
                if(entity instanceof BlackwatchHelicopter c){if(crash)c.hurt(c.damageSources().generic(),1000);}else if(crashQueued)finished=true;});
            var row=new JsonObject();row.addProperty("tick",ticks);row.addProperty("phase",phase);row.addProperty("x",at.x);row.addProperty("y",at.y);row.addProperty("z",at.z);row.addProperty("passengers",aircraft.getPassengers().size());row.addProperty("rotor",aircraft.rotor());row.addProperty("airShots",aircraft.airShots());row.addProperty("gunYaw",aircraft.gunYaw());row.addProperty("gunPitch",aircraft.gunPitch());row.addProperty("mountError",maximumMountError);frames.add(row);
        }catch(Exception failure){done=true;failure.printStackTrace();try{if(output!=null)Files.writeString(output.resolve("status.txt"),"FAIL: "+failure);}catch(Exception ignored){}mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(!initialized||done||phaseAge<(phase.equals("SPIN_UP")?35:8)||phase.isEmpty())return;
        String name=phase.equals("UNLOAD")&&phaseAge>65?"UNLOAD_REVERSE":phase;
        if(combatMode&&phase.equals("STRAFE")&&clientShots>=3)name="STRAFE_FIRE";
        if(captured.contains(name))return;
        try(var shot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){shot.writeToFile(output.resolve(name+".png"));captured.add(name);}catch(Exception failure){error=failure.toString();}
    }
}
