package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.HellfireBlastEntity;
import com.codex.blacklight.military.aircraft.HelicopterHellfireAttack;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Real autonomous weapon cycle; this harness controls only setup, harmless target and camera. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HelicopterHoverVisual {
    private static final String WORLD="Creature Skills QA Final";
    private static final Vec3 TARGET=new Vec3(0,81,0);
    private static final JsonArray frames=new JsonArray(),trace=new JsonArray(),soundEvents=new JsonArray();
    private static final Set<Integer> captured=new HashSet<>();
    private static final Set<String> states=new TreeSet<>(),activeSounds=new TreeSet<>();
    private static final List<SoundInstance> playing=new ArrayList<>();
    private static boolean initialized,done;private static volatile boolean ready,finished;
    private static Path output;private static volatile String error,stage="STRAFE";
    private static volatile int age,craftId=-1,firstHover=-1,firstShot=-1;private static long start;
    private static BlackwatchHelicopter craft;private static BlacklightCommonInfected target;private static ArmorStand camera;
    private static int gunAtHover,lastRocket;private static boolean impact;private static double launchSpeed;
    private static double maximumRackError;private static int rackSamples;
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent e){
        if(!initialized||e.getSound()==null)return;String name=e.getSound().getLocation().toString();
        if(!name.startsWith("blacklight:vehicle.")&&!name.equals("blacklight:firearm.impact"))return;
        playing.add(e.getSound());var row=new JsonObject();row.addProperty("age",age);row.addProperty("sound",name);soundEvents.add(row);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(done||System.getProperty("blacklight.test.hover","").isBlank())return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').startsWith("D:/CodexCache/creature-combat-qa/")||!mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("Disposable QA world required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.hover")).toAbsolutePath().normalize();if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: required");Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"RUNNING");
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(75);mc.options.framerateLimit().set(60);mc.options.particles().set(ParticleStatus.ALL);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
                mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(1.0);mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.HOSTILE).set(1.0);mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.NEUTRAL).set(1.0);
                mc.setScreen(null);camera=new ArmorStand(mc.level,0,0,0);initialized=true;setup();return;
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(org.lwjgl.openal.AL10.alGetListenerf(org.lwjgl.openal.AL10.AL_GAIN)!=0)throw new IllegalStateException("QA became audible");
            for(var sound:playing)if(mc.getSoundManager().isActive(sound))activeSounds.add(sound.getLocation().toString());
            if(finished){
                if(rackSamples<20||maximumRackError>1e-4)throw new IllegalStateException("Rendered rack/actual launch mismatch: "+maximumRackError+" samples="+rackSamples);
                if(!states.containsAll(List.of("WAIT","AIM","FIRE","RECOVER")))throw new IllegalStateException("Missing actual attack state: "+states);
                if(!activeSounds.containsAll(List.of("blacklight:vehicle.coaxial","blacklight:vehicle.rocket","blacklight:firearm.impact")))throw new IllegalStateException("Missing active native weapon audio: "+activeSounds);
                done=true;write();Files.writeString(output.resolve("status.txt"),"PASS: actual AI hover, one guided shot, impact and resumed gun; native screenshots require review");mc.setCameraEntity(mc.player);mc.stop();
            }
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Native hover visual QA",ex);try{if(output!=null){write();Files.writeString(output.resolve("status.txt"),"FAIL: "+error);}}catch(Exception ignored){}mc.setCameraEntity(mc.player);mc.stop();}
    }
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        p.setGameMode(GameType.SPECTATOR);p.connection.teleport(0,102,10,0,0);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,24000,0,false,false));
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-60;x<=60;x++)for(int z=-60;z<=60;z++){level.setBlock(new BlockPos(x,80,z),((x%8==0||z%8==0)?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(),2);for(int y=81;y<=115;y++)if(!level.isEmptyBlock(new BlockPos(x,y,z)))level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        target=ModEntities.COMMON_INFECTED.get().create(level);target.setPos(TARGET);target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);level.addFreshEntity(target);
        craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);craft.setPos(-26,95,0);level.addFreshEntity(craft);craftId=craft.getId();
        for(int i=0;i<2;i++){var pilot=ModEntities.BLACKWATCH_SOLDIER.get().create(level);pilot.setPos(craft.position());pilot.setAutomaticUnitJoin(false);pilot.setPersistenceRequired();level.addFreshEntity(pilot);if(!pilot.startRiding(craft,true))throw new IllegalStateException("Native crew boarding failed");}
        var caller=ModEntities.BLACKWATCH_OFFICER.get().create(level);caller.setPos(-30,81,20);caller.setAutomaticUnitJoin(false);level.addFreshEntity(caller);caller.setTarget(target);
        if(!craft.deployTo(new Vec3(-20,81,0)))throw new IllegalStateException("Native sortie refused");craft.combat().supportCall(caller);caller.discard();
        var tag=new CompoundTag();craft.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());craft.load(tag);start=level.getGameTime();ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        var mc=Minecraft.getInstance();if(!ready||finished||done||mc.getSingleplayerServer()==null||e.getLevel()!=mc.getSingleplayerServer().overworld())return;
        try{
            var level=mc.getSingleplayerServer().overworld();age=(int)(level.getGameTime()-start);target.setPos(TARGET);target.setDeltaMovement(Vec3.ZERO);
            // Vanilla sound packets are sent around the server player, independently
            // of the detached render camera. Keep the spectator within audible range.
            if(age%10==0){var observer=level.getServer().getPlayerList().getPlayer(mc.player.getUUID());if(observer!=null)observer.connection.teleport(craft.getX()+6,craft.getY()+7,craft.getZ()+8,0,0);}
            var attack=craft.combat().hellfireAttack();stage=attack.stage().name();states.add(stage);
            if(attack.hovering()){
                if(firstHover<0){firstHover=age;gunAtHover=craft.combat().gunShots();if(firstHover<299||gunAtHover==0)throw new IllegalStateException("Source preparation bypassed");}
                if(craft.combat().gunShots()!=gunAtHover)throw new IllegalStateException("Gun overlapped Hellfire hover");
            }
            if(craft.combat().rocketShots()>lastRocket){firstShot=age;launchSpeed=craft.getDeltaMovement().length();if(!stage.equals("FIRE")||launchSpeed>=.04||firstShot-firstHover<20)throw new IllegalStateException("Bad actual launch state/speed/wait");lastRocket=craft.combat().rocketShots();}
            impact|=target.getLastDamageSource()!=null&&target.getLastDamageSource().getDirectEntity() instanceof HellfireBlastEntity;
            var row=new JsonObject();row.addProperty("age",age);row.addProperty("stage",stage);row.addProperty("phase",craft.phase().name());row.addProperty("avoid",attack.avoidTicks());row.addProperty("hoverAge",attack.hoverAge());row.addProperty("gun",craft.combat().gunShots());row.addProperty("rocket",craft.combat().rocketShots());row.addProperty("speed",craft.getDeltaMovement().length());row.addProperty("health",target.getHealth());row.add("position",new Gson().toJsonTree(craft.position()));trace.add(row);
            if(age>650)throw new IllegalStateException("Native hover cycle timed out");
            if(firstShot>=0&&age>firstShot+100){if(!impact||craft.combat().rocketShots()!=1||attack.hovering()||craft.combat().gunShots()<=gunAtHover||craft.combat().rounds()!=240||craft.combat().rockets()!=8)throw new IllegalStateException("Native impact/recovery/stores not proven");finished=true;}
        }catch(Exception ex){error=ex.toString();}
    }
    @SubscribeEvent public static void before(RenderFrameEvent.Pre e){
        if(!initialized||!ready||done||camera==null)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(craftId) instanceof BlackwatchHelicopter local))return;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);Vec3 at=new Vec3(net.minecraft.util.Mth.lerp(partial,local.xOld,local.getX()),net.minecraft.util.Mth.lerp(partial,local.yOld,local.getY()),net.minecraft.util.Mth.lerp(partial,local.zOld,local.getZ())).add(0,1.5,0);
        Vec3 targetAt=TARGET.add(0,1,0),side=targetAt.subtract(at).multiply(1,0,1).normalize().cross(new Vec3(0,1,0));
        Vec3 center=at.lerp(targetAt,.4),eye=center.add(side.scale(16)).add(0,8,0);
        if(age>=80&&age<=180){center=at.add(0,-.25,0);eye=at.add(com.codex.blacklight.military.aircraft.HelicopterArmament.rotate(new Vec3(-6,.4,7),local.getYRot(),local.bank()));}
        Vec3 position=eye.add(0,-camera.getEyeHeight(),0),look=center.subtract(eye);
        try{checkRacks(local,partial);}catch(Exception ex){error=ex.toString();}
        camera.setPos(position);camera.xo=position.x;camera.yo=position.y;camera.zo=position.z;camera.xOld=position.x;camera.yOld=position.y;camera.zOld=position.z;
        camera.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));camera.setXRot((float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();camera.setYHeadRot(camera.getYRot());camera.yHeadRotO=camera.getYRot();mc.setCameraEntity(camera);
    }
    private static void checkRacks(BlackwatchHelicopter local,float partial)throws Exception{
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(local);var field=renderer.getClass().getDeclaredField("model");field.setAccessible(true);
        var model=(com.codex.blacklight.client.model.HelicopterModel)field.get(renderer);var root=model.pose(local,partial);
        for(int side:new int[]{-1,1})for(int slot=0;slot<4;slot++){
            var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(local.getYRot()));pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(local.bank()));
            var node=root.getChild("hellfire_"+side+"_"+slot);if(!node.visible)throw new IllegalStateException("Infinite-ammo AI lost rendered rail");node.translateAndRotate(pose);
            var point=pose.last().pose().transformPosition(new org.joml.Vector3f());Vec3 rendered=local.position().add(point.x,point.y,point.z);
            maximumRackError=Math.max(maximumRackError,rendered.distanceTo(local.hellfireLaunchPoint(side,slot)));rackSamples++;
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done||age<1||(age<285||age>firstShot+30&&firstShot>=0)&&age%10!=0||!captured.add(age))return;
        try{var mc=Minecraft.getInstance();String name=String.format(Locale.ROOT,"hover-%03d-%s.png",age,stage);try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}var row=new JsonObject();row.addProperty("image",name);row.addProperty("age",age);row.addProperty("stage",stage);frames.add(row);}catch(Exception ex){error=ex.toString();}
    }
    private static void write()throws Exception{var result=new JsonObject();result.add("frames",frames);result.add("trace",trace);result.add("soundEvents",soundEvents);result.add("activeSounds",new Gson().toJsonTree(activeSounds));result.addProperty("firstHover",firstHover);result.addProperty("firstShot",firstShot);result.addProperty("launchSpeed",launchSpeed);result.addProperty("impact",impact);result.addProperty("maximumRackError",maximumRackError);result.addProperty("rackSamples",rackSamples);result.addProperty("error",error);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
}
