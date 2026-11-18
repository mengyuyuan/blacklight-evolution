package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.military.vehicle.VehicleWeaponDefinition;
import com.codex.blacklight.client.renderer.GunplayVisuals;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;



/** Opt-in real projectile/client receipt QA. Only the camera and test actors are controlled. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HellfireVisual {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/client",WORLD="Creature Skills QA Final";
    private static final String[] NAMES={"crossing_close","crossing_impact","wall","lost_target"};
    private static final Vec3 START=new Vec3(0,100,0);
    private static final JsonArray frames=new JsonArray(),results=new JsonArray(),sounds=new JsonArray();
    private static final Set<String> saved=new HashSet<>(),activeSounds=new TreeSet<>();
    private static final List<SoundInstance> playing=new ArrayList<>();
    private static boolean initialized,queued;private static volatile boolean ready,done;
    private static volatile String error;private static volatile int scene,age,missileId=-1,removedAt=-1;
    private static int bootstrap;private static Path output;private static long started,receipts;
    private static VehicleOrdnanceProjectile missile;private static BlacklightCommonInfected target;
    private static BlackwatchSoldier owner;private static BlackwatchHelicopter origin;
    private static float initialHealth;private static ArmorStand camera;private static Vec3 last=START;

    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e){
        if(!initialized||!ready||e.getSound()==null)return;
        var name=e.getSound().getLocation().toString();if(!name.equals("blacklight:vehicle.rocket")&&!name.equals("blacklight:firearm.impact"))return;
        playing.add(e.getSound());var row=new JsonObject();row.addProperty("case",NAMES[scene]);row.addProperty("age",age);row.addProperty("name",name);sounds.add(row);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(System.getProperty("blacklight.test.hellfire","").isBlank()||done)return;var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("QA directory only");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;}
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("QA save only");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.hellfire")).toAbsolutePath().normalize();if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(45);mc.options.framerateLimit().set(60);mc.options.particles().set(ParticleStatus.ALL);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
                mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(1.0);mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.HOSTILE).set(1.0);
                mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"RUNNING");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(org.lwjgl.openal.AL10.alGetListenerf(org.lwjgl.openal.AL10.AL_GAIN)!=0)throw new IllegalStateException("QA became audible");
            for(var s:playing)if(mc.getSoundManager().isActive(s))activeSounds.add(NAMES[scene]+":"+s.getLocation());
            if(!queued){queued=true;ready=false;last=START;camera=new ArmorStand(mc.level,0,0,0);mc.setCameraEntity(camera);setup();return;}
            if(ready&&age>105){
                int expected=scene==3?1:2;if(GunplayVisuals.received()-receipts!=expected)throw new IllegalStateException("Wrong real receipt count: "+(GunplayVisuals.received()-receipts));
                if(!activeSounds.contains(NAMES[scene]+":blacklight:vehicle.rocket"))throw new IllegalStateException("Launch audio never active");
                if(scene!=3&&!activeSounds.contains(NAMES[scene]+":blacklight:firearm.impact"))throw new IllegalStateException("Impact audio never active");
                if(explosionEffects().size()!=0)throw new IllegalStateException("Hellfire presentation did not expire");
                if(scene!=3&&java.util.stream.StreamSupport.stream(frames.spliterator(),false).map(JsonElement::getAsJsonObject).filter(r->r.get("case").getAsString().equals(NAMES[scene]))
                        .flatMap(r->java.util.stream.StreamSupport.stream(r.getAsJsonArray("explosionEffects").spliterator(),false)).noneMatch(r->r.getAsJsonObject().get("effectAge").getAsDouble()>24))throw new IllegalStateException("Source three-second presentation ended at the old generic lifetime");
                if(scene<2&&target.getHealth()>=initialHealth||scene==2&&target.getHealth()!=initialHealth||scene==3&&!missile.trackingLost())throw new IllegalStateException("Scenario outcome mismatch");
                var row=new JsonObject();row.addProperty("case",NAMES[scene]);row.addProperty("receipts",GunplayVisuals.received()-receipts);row.addProperty("removedAt",removedAt);row.addProperty("initialHealth",initialHealth);row.addProperty("health",target.getHealth());row.addProperty("lost",missile.trackingLost());results.add(row);
                ready=false;playing.clear();if(++scene<NAMES.length){queued=false;return;}
                done=true;write();Files.writeString(output.resolve("status.txt"),"PASS: four real projectile/client scenarios with active launch and impact audio");mc.setCameraEntity(mc.player);mc.stop();
            }
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Hellfire client QA",ex);try{if(output!=null){write();Files.writeString(output.resolve("status.txt"),error);}}catch(Exception ignored){}mc.setCameraEntity(mc.player);mc.stop();}
    }
    private static void write()throws Exception{var json=new JsonObject();json.add("frames",frames);json.add("results",results);json.add("soundEvents",sounds);json.add("activeSounds",new Gson().toJsonTree(activeSounds));json.addProperty("error",error);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(json));}
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,24000,0,false,false));p.connection.teleport(12,102,-2,0,0);
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-8;x<=80;x++)for(int z=-12;z<=35;z++){level.setBlock(new BlockPos(x,96,z),((x%4==0||z%4==0)?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(),2);for(int y=97;y<=106;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        if(scene==2)for(int y=97;y<=104;y++)for(int z=-5;z<=5;z++)level.setBlock(new BlockPos(20,y,z),Blocks.BEDROCK.defaultBlockState(),2);
        owner=ModEntities.BLACKWATCH_SOLDIER.get().create(level);owner.setPos(START.add(-5,-2,-5));owner.setNoAi(true);owner.setNoGravity(true);owner.setSilent(true);owner.setInvisible(true);owner.setAutomaticUnitJoin(false);level.addFreshEntity(owner);
        origin=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);origin.setPos(START.add(-8,0,0));
        target=ModEntities.COMMON_INFECTED.get().create(level);target.setPos(START.add(40,-target.getBbHeight()*.5,0));target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);level.addFreshEntity(target);owner.setTarget(target);
        missile=null;missileId=-1;removedAt=-1;age=-40;started=level.getGameTime()+40;receipts=GunplayVisuals.received();ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!ready||done||mc.getSingleplayerServer()==null||e.level!=mc.getSingleplayerServer().overworld())return;
        try{
            var level=mc.getSingleplayerServer().overworld();age=(int)(level.getGameTime()-started);
            if(age<0){initialHealth=target.getHealth();return;}
            if(scene<2){target.setPos(START.add(40,-target.getBbHeight()*.5,age*.3));target.setDeltaMovement(0,0,.3);}
            if(missile==null){missile=new VehicleOrdnanceProjectile(level,owner,origin,"helicopter_hellfire");missile.setPos(START);missile.launchHellfire(new Vec3(1,0,0),Vec3.ZERO,target,target.getBoundingBox().getCenter(),false);level.addFreshEntity(missile);missileId=missile.getId();
                GunplayEffects.shot(owner,origin,VehicleWeaponDefinition.require("helicopter_hellfire").weapon(),START,START.add(1,0,0),0,new Vec3(0,1,0));}
            if(scene==3&&age==12)target.discard();
            if(missile.isRemoved()&&removedAt<0)removedAt=age;
        }catch(Exception ex){error=ex.toString();}
    }
    @SubscribeEvent public static void before(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.START)return;
        if(!initialized||!ready||done||camera==null)return;var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=mc.getFrameTime();var entity=mc.level.getEntity(missileId);if(entity!=null)last=renderPosition(entity,partial);
        Vec3 at,eye;if(scene==1&&age>=10){at=new Vec3(40,100,Math.min(30,Math.max(0,age)*.3));eye=at.add(-4,3,-10);}else if(scene==2){at=new Vec3(20,100,0);eye=at.add(-5,3,-10);}else{at=last;eye=at.add(-1.5,1,-4);}
        Vec3 position=eye.add(0,-camera.getEyeHeight(),0),look=at.subtract(eye);camera.setPos(position);camera.xo=position.x;camera.yo=position.y;camera.zo=position.z;camera.xOld=position.x;camera.yOld=position.y;camera.zOld=position.z;
        camera.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));camera.setXRot((float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();
        // LivingEntity cameras read head yaw rather than body yaw.
        camera.setYHeadRot(camera.getYRot());camera.yHeadRotO=camera.getYRot();camera.yBodyRot=camera.getYRot();camera.yBodyRotO=camera.getYRot();mc.setCameraEntity(camera);
    }
    private static Vec3 renderPosition(Entity entity,float partial){
        // LevelRenderer uses xOld; Entity.getPosition uses a different xo baseline.
        // Following xo can move a fast projectile out of this close camera's view.
        return new Vec3(net.minecraft.util.Mth.lerp(partial,entity.xOld,entity.getX()),net.minecraft.util.Mth.lerp(partial,entity.yOld,entity.getY()),net.minecraft.util.Mth.lerp(partial,entity.zOld,entity.getZ()));
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(!initialized||!ready||done||age<0||age>95)return;var mc=Minecraft.getInstance();String name=NAMES[scene]+"-"+String.format(Locale.ROOT,"%03d",age)+".png";if(!saved.add(name))return;
        try{
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("case",NAMES[scene]);row.addProperty("age",age);row.addProperty("partial",mc.getFrameTime());row.addProperty("receipts",GunplayVisuals.received()-receipts);row.addProperty("serverFlightAge",missile==null?-1:missile.flightAge());
            if(mc.level.getEntity(missileId) instanceof VehicleOrdnanceProjectile local){row.addProperty("clientFlightAge",local.flightAge());row.add("clientPosition",new Gson().toJsonTree(local.position()));row.add("heading",new Gson().toJsonTree(local.flightHeading()));row.add("velocity",new Gson().toJsonTree(local.getDeltaMovement()));row.addProperty("headingVelocityAngle",Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,local.flightHeading().dot(local.getDeltaMovement().normalize()))))));}
            if(missile!=null){row.add("serverPosition",new Gson().toJsonTree(missile.position()));row.addProperty("removed",missile.isRemoved());row.addProperty("lost",missile.trackingLost());}row.add("explosionEffects",explosionEffects());
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
    private static JsonArray explosionEffects()throws Exception{
        var field=GunplayVisuals.class.getDeclaredField("ACTIVE");field.setAccessible(true);var effects=(Collection<?>)field.get(null);var result=new JsonArray();var mc=Minecraft.getInstance();
        for(var effect:effects){var dataField=effect.getClass().getDeclaredField("data");dataField.setAccessible(true);var p=(com.codex.blacklight.network.GunplayFeedbackPayload)dataField.get(effect);if(p.weapon()!=13||p.event()!=1)continue;
            var timeField=effect.getClass().getDeclaredField("at");timeField.setAccessible(true);var row=new JsonObject();row.addProperty("effectAge",mc.level.getGameTime()+mc.getFrameTime()-(double)timeField.get(effect));row.addProperty("weapon",p.weapon());result.add(row);
        }if(result.size()>1)throw new IllegalStateException("Multiple explosions for one missile");return result;
    }
}
