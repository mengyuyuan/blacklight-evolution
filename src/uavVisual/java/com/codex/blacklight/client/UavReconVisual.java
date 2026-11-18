package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Opt-in real scanner/transport lifecycle in a guarded, hidden disposable QA save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class UavReconVisual {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/neo-client",WORLD="Creature Skills QA Final";
    private static final String[] NAMES={"scan_and_sortie","occlusion","destroyed","submerged_destroyed","audio_limits","audio_world_exit"};
    private static final JsonArray frames=new JsonArray(),results=new JsonArray();
    private static final Set<String> saved=new HashSet<>(),sounds=new TreeSet<>(),lampStates=new TreeSet<>();
    private static final Set<String> visibleLampColors=new TreeSet<>();private static boolean nightRed;private static long wreckReceipts;
    private static final JsonArray audioEvents=new JsonArray();private static final Set<String> audioChecks=new TreeSet<>();private static int activeAudioSamples;
    private static final Set<UUID> crew=new HashSet<>(),delivered=new HashSet<>();
    private static volatile boolean ready,done,changed;private static volatile String error;private static volatile int age,scene,droneId;
    private static boolean initialized,queued;private static int bootstrap,beamFrames;private static long started;private static Path output;
    private static MilitaryUav drone;private static LivingEntity target;private static BlackwatchHelicopter craft;
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent e){if(initialized){sounds.add(e.getName());if(e.getName().contains("uav_")&&e.getSound()!=null){var row=new JsonObject();row.addProperty("case",NAMES[Math.min(scene,NAMES.length-1)]);row.addProperty("age",age);row.addProperty("cue",e.getName());row.addProperty("loop",e.getSound().isLooping());audioEvents.add(row);}}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(System.getProperty("blacklight.test.uav","").isBlank()||done)return;var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("QA directory only");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;}
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("QA save only");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){output=Path.of(System.getProperty("blacklight.test.uav")).toAbsolutePath().normalize();if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(55);mc.options.framerateLimit().set(45);mc.options.particles().set(ParticleStatus.ALL);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
                // Silent QA suppresses only the actual AL listener gain, retaining real
                // channel playback. A logical zero master volume would bypass the engine.
                mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(1.0);mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.HOSTILE).set(1.0);
                if(org.lwjgl.openal.AL10.alGetListenerf(org.lwjgl.openal.AL10.AL_GAIN)!=0)throw new IllegalStateException("QA output not muted");
                mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"RUNNING");}
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;wreckReceipts=com.codex.blacklight.client.renderer.VehicleDestructionVisuals.received();setup();return;}
            if(ready&&age>(scene==0?720:scene==5?60:scene==4?300:scene>=2?1920:220)){
                if(scene==2||scene==3){
                    if(com.codex.blacklight.client.renderer.VehicleDestructionVisuals.received()-wreckReceipts!=1)throw new IllegalStateException("Expected one UAV destruction receipt");
                    if(com.codex.blacklight.client.renderer.VehicleDestructionVisuals.active()!=0)throw new IllegalStateException("UAV wreck did not expire");
                }
                ready=false;if(++scene<NAMES.length){queued=false;return;}
                var voicesField=com.codex.blacklight.client.vehicle.UavAudio.class.getDeclaredField("VOICES");voicesField.setAccessible(true);
                var lastVoices=(Map<?,?>)voicesField.get(null);if(lastVoices.size()!=1)throw new IllegalStateException("Missing active UAV before world exit");
                var lastLoop=(net.minecraft.client.resources.sounds.SoundInstance)field(lastVoices.values().iterator().next(),"loop");if(!mc.getSoundManager().isActive(lastLoop))throw new IllegalStateException("No native channel before world exit");
                write();mc.level.disconnect();mc.disconnect();if(!((Map<?,?>)voicesField.get(null)).isEmpty())throw new IllegalStateException("World exit retained UAV audio");audioChecks.add("unload");
                for(String cue:new String[]{"uav_rotor","uav_scan","uav_seeing","uav_alert","uav_break"})if(sounds.stream().noneMatch(s->s.endsWith(cue)))throw new IllegalStateException("Missing native sound: "+cue);
                if(sounds.stream().anyMatch(s->s.endsWith("uav_transmit")||s.endsWith("uav_confirm")))throw new IllegalStateException("Obsolete UAV radio beeps still triggered");
                if(activeAudioSamples<15||!audioChecks.containsAll(Set.of("idle","seeing","alert","cap4","silent","far","reenter","removed","unload")))throw new IllegalStateException("Incomplete native audio checks: "+audioChecks+" active="+activeAudioSamples);
                for(String cue:new String[]{"uav_scan","uav_seeing","uav_alert"}){long count=java.util.stream.StreamSupport.stream(audioEvents.spliterator(),false).map(JsonElement::getAsJsonObject).filter(r->r.get("case").getAsString().equals("scan_and_sortie")&&r.get("cue").getAsString().endsWith(cue)).count();if(count!=1)throw new IllegalStateException("State entry retriggered or missing "+cue+": "+count);}
                if(beamFrames<10)throw new IllegalStateException("Missing native lens measurements: "+beamFrames);
                if(!lampStates.containsAll(Set.of("green","yellow","red")))throw new IllegalStateException("Missing source lamp states: "+lampStates);
                if(!visibleLampColors.containsAll(Set.of("green","yellow","red"))||!nightRed)throw new IllegalStateException("Lamp geometry hidden or not emissive: "+visibleLampColors+" night red="+nightRed);
                done=true;write();Files.writeString(output.resolve("status.txt"),"PASS: source lamp states, real sortie/handoff, occlusion, destruction, native audio state channels, range/cap/silence/removal and world unload");mc.stop();
            }
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("UAV QA",ex);try{if(output!=null){write();Files.writeString(output.resolve("status.txt"),error);}}catch(Exception ignored){}mc.stop();}
    }
    private static void write()throws Exception{var json=new JsonObject();json.add("frames",frames);json.add("results",results);json.add("sounds",new Gson().toJsonTree(sounds));json.add("sourceLampStates",new Gson().toJsonTree(lampStates));json.add("visibleLampColors",new Gson().toJsonTree(visibleLampColors));json.addProperty("nightRed",nightRed);json.add("audioEvents",audioEvents);json.add("audioChecks",new Gson().toJsonTree(audioChecks));json.addProperty("activeAudioSamples",activeAudioSamples);json.addProperty("error",error);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(json));}
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        for(var actor:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(actor instanceof net.minecraft.world.entity.player.Player))actor.discard();
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int cx=-4;cx<=4;cx++)for(int cz=-4;cz<=4;cz++)level.setChunkForced(cx,cz,true);
        for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++){level.setBlock(new BlockPos(x,80,z),((x%4==0||z%4==0)?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(),2);for(int y=81;y<=105;y++){var pos=new BlockPos(x,y,z);if(!level.getBlockState(pos).isAir())level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}}
        target=ModEntities.COMMON_INFECTED.get().create(level);((Mob)target).setNoAi(true);((Mob)target).setPersistenceRequired();target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);target.setHealth(100000);target.setPos(0,81,5);level.addFreshEntity(target);
        drone=EncounterEntities.UAV.get().create(level);drone.setNoAi(true);drone.setPersistenceRequired();drone.setPos(0,86,0);level.addFreshEntity(drone);droneId=drone.getId();craft=null;crew.clear();delivered.clear();changed=false;
        if(scene==3){for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=81;y<=85;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),2);drone.setPos(0,81,0);}
        if(scene==4){target.discard();for(int i=1;i<6;i++){var extra=EncounterEntities.UAV.get().create(level);extra.setNoAi(true);extra.setPersistenceRequired();extra.setPos(i*2,86,0);level.addFreshEntity(extra);}}
        if(scene==5)target.discard();
        p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        camera(p,drone.position().add(8,2,10),drone.position().add(0,-2,0));started=level.getGameTime()+25;age=-25;ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    private static void camera(net.minecraft.server.level.ServerPlayer p,Vec3 at,Vec3 look){var v=look.subtract(at.add(0,p.getEyeHeight(),0));p.connection.teleport(at.x,at.y,at.z,(float)Math.toDegrees(Math.atan2(-v.x,v.z)),(float)-Math.toDegrees(Math.atan2(v.y,v.horizontalDistance())));}
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;var level=server.overworld();
        age=(int)(level.getGameTime()-started);
        try{
            if(scene==5)return;
            if(scene==4){
                var p=server.getPlayerList().getPlayer(mc.player.getUUID());
                if(age==60)drone.setSilent(true);
                if(age==100)camera(p,new Vec3(100,86,100),drone.position());
                if(age==145)camera(p,new Vec3(3,86,6),drone.position());
                if(age==250)level.getEntitiesOfClass(MilitaryUav.class,new AABB(-20,75,-20,20,105,20)).forEach(MilitaryUav::discard);
                return;
            }
            if(age==0&&scene!=3)drone.setNoAi(false);
            if(age>=0&&age%5==0){var p=server.getPlayerList().getPlayer(mc.player.getUUID());if(scene==0&&age>130)camera(p,new Vec3(38,101,38),new Vec3(15,85,5));else if(scene>=2&&changed)camera(p,new Vec3(drone.getX()+7,84,drone.getZ()+9),new Vec3(drone.getX(),81.4,drone.getZ()));else{var forward=Vec3.directionFromRotation(0,drone.getYRot());camera(p,drone.position().add(forward.scale(5)).add(0,1,0),drone.position().add(0,.8,0));}}
            if(scene==0&&age==75){level.setDayTime(18000);server.getPlayerList().getPlayer(mc.player.getUUID()).removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);}
            if(scene==0&&age==125){level.setDayTime(6000);server.getPlayerList().getPlayer(mc.player.getUUID()).addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));}
            var aircraft=level.getEntitiesOfClass(BlackwatchHelicopter.class,new AABB(-96,65,-96,96,128,96));
            if(scene==0){
                if(craft==null&&!aircraft.isEmpty()){craft=aircraft.get(0);if(craft.getPassengers().size()!=6)throw new IllegalStateException("Expected six real crew");craft.getPassengers().stream().filter(a->a.getPersistentData().getBoolean("HelicopterTroop")).forEach(a->crew.add(a.getUUID()));}
                for(UUID id:crew)if(level.getEntity(id) instanceof MilitarySoldier s&&!s.isPassenger()&&s.getTarget()==target)delivered.add(id);
                if(age==720){if(craft==null||delivered.size()!=4||drone.supportCooldown()<2900)throw new IllegalStateException("Incomplete native sortie: "+delivered.size());var row=new JsonObject();row.addProperty("case",NAMES[scene]);row.addProperty("actualCrew",6);row.addProperty("deliveredWithTarget",delivered.size());row.addProperty("cooldown",drone.supportCooldown());results.add(row);}
            }else{
                if(!changed&&(scene==3?age==20:drone.signalProgress()>=20)){changed=true;if(scene>=2){drone.hurt(level.damageSources().generic(),10000);for(int i=0;i<5;i++)if(drone.hurt(level.damageSources().generic(),10000))throw new IllegalStateException("Repeated UAV death accepted");}else{for(int y=81;y<=104;y++)for(int d=-2;d<=2;d++){level.setBlock(new BlockPos(-2,y,5+d),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(2,y,5+d),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(d,y,3),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(d,y,7),Blocks.STONE.defaultBlockState(),2);}for(int a=-2;a<=2;a++)for(int b=3;b<=7;b++)level.setBlock(new BlockPos(a,104,b),Blocks.STONE.defaultBlockState(),2);}}
                if(age==220){if(!changed||!aircraft.isEmpty()||drone.signalProgress()!=0||scene==2&&drone.isAlive())throw new IllegalStateException("Interrupted transmission dispatched");var row=new JsonObject();row.addProperty("case",NAMES[scene]);row.addProperty("preventedDispatch",true);results.add(row);}
            }
        }catch(Exception ex){error=ex.toString();}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done||scene>=NAMES.length||age<0||age%5!=0)return;var mc=Minecraft.getInstance();String name=NAMES[scene]+"-"+String.format(Locale.ROOT,"%03d",age)+".png";if(!saved.add(name))return;
        try{var row=new JsonObject();row.addProperty("image",name);row.addProperty("case",NAMES[scene]);row.addProperty("age",age);row.addProperty("scan",drone.scanProgress());row.addProperty("signal",drone.signalProgress());row.addProperty("supportWait",drone.supportCooldown());row.addProperty("delivered",delivered.size());if(craft!=null)row.addProperty("aircraftPhase",craft.phase().name());
            if(mc.level.getEntity(droneId) instanceof MilitaryUav local){row.addProperty("clientScan",local.scanProgress());if(scene<4)row.add("lamp",lamp(local));}
            row.add("wreck",wreck());row.add("audio",audio());
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                if(scene==0&&age<=125&&row.has("lamp")&&row.getAsJsonObject("lamp").has("state")){
                    String state=row.getAsJsonObject("lamp").get("state").getAsString();int pixels=0;
                    for(int y=0;y<shot.getHeight();y++)for(int x=0;x<shot.getWidth();x++){int abgr=shot.getPixelRGBA(x,y),r=abgr&255,g=(abgr>>>8)&255,b=(abgr>>>16)&255;
                        if(switch(state){case "green"->g>160&&g>r*1.6&&g>b*1.8;case "yellow"->r>170&&g>140&&b<90;default->r>170&&g<100&&b<90;})pixels++;
                    }
                    row.addProperty("lampColoredPixels",pixels);if(pixels>=2){visibleLampColors.add(state);if(state.equals("red")&&age>=85&&age<=120)nightRed=true;}
                }
                shot.writeToFile(output.resolve(name));
            }frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static JsonObject audio()throws Exception{
        var row=new JsonObject();var mc=Minecraft.getInstance();var f=com.codex.blacklight.client.vehicle.UavAudio.class.getDeclaredField("VOICES");f.setAccessible(true);var voices=(Map<?,?>)f.get(null);
        if(voices.size()>4)throw new IllegalStateException("UAV audio voice cap exceeded");row.addProperty("voices",voices.size());
        if(org.lwjgl.openal.AL10.alGetListenerf(org.lwjgl.openal.AL10.AL_GAIN)!=0)throw new IllegalStateException("QA became audible");
        if(scene==4){
            row.add("playerPosition",new Gson().toJsonTree(mc.player.position()));
            long nearby=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(o->o instanceof MilitaryUav&&!o.isRemoved()&&o.distanceToSqr(mc.player)<40*40).count();row.addProperty("nearbyClientUavs",nearby);
            if(age>=40&&age<=55){if(voices.size()!=4)throw new IllegalStateException("Expected four nearby voices");audioChecks.add("cap4");}
            if(age>=70&&age<=90){if(voices.keySet().stream().anyMatch(o->((MilitaryUav)o).getId()==droneId))throw new IllegalStateException("Silent UAV retains its voice");audioChecks.add("silent");}
            if(age>=115&&age<=130){if(!voices.isEmpty())throw new IllegalStateException("Out of range UAV still audible");audioChecks.add("far");}
            // Teleporting beyond the view radius unloads chunks. Allow bounded client
            // chunk delivery before checking recovery, while recording actual arrival.
            if(age>=160&&age<250&&voices.size()==4)audioChecks.add("reenter");
            if(age>=225&&age<250&&voices.size()!=4)throw new IllegalStateException("Nearby UAV voices did not return: "+row);
            if(age>=275){if(!voices.isEmpty())throw new IllegalStateException("Removed UAV retains its voice");audioChecks.add("removed");}
        }
        for(var entry:voices.entrySet()){
            var drone=(MilitaryUav)entry.getKey();var voice=entry.getValue();var loop=(net.minecraft.client.resources.sounds.SoundInstance)field(voice,"loop");if(loop==null)continue;
            int state=(int)field(voice,"state");String expected=state==1?"uav_seeing":"uav_rotor";
            if(!loop.getLocation().getPath().endsWith(expected))throw new IllegalStateException("Wrong UAV state loop");
            if(((Collection<?>)field(voice,"entries")).size()>2)throw new IllegalStateException("Unbounded entry cue overlap");
            if(mc.getSoundManager().isActive(loop)){activeAudioSamples++;audioChecks.add(state==1?"seeing":state==2?"alert":"idle");}
            double delta=new Vec3(loop.getX(),loop.getY(),loop.getZ()).distanceTo(drone.position().add(0,.6,0));if(delta>.35)throw new IllegalStateException("Detached UAV sound: "+delta);
            row.addProperty("loop",loop.getLocation().toString());row.addProperty("active",mc.getSoundManager().isActive(loop));row.addProperty("positionError",delta);
        }
        return row;
    }
    private static JsonObject wreck()throws Exception{
        var row=new JsonObject();if(scene<2||scene>=4||!changed)return row;
        var f=com.codex.blacklight.client.renderer.VehicleDestructionVisuals.class.getDeclaredField("SCENES");f.setAccessible(true);var scenes=(java.util.Collection<?>)f.get(null);row.addProperty("active",scenes.size());
        if(scenes.isEmpty())return row;var s=scenes.iterator().next();var uav=field(s,"uav");if(uav==null)throw new IllegalStateException("Wrong UAV wreck model");
        var root=(net.minecraft.client.model.geom.ModelPart)field(uav,"root");var origin=(Vec3)field(s,"origin");var data=(com.codex.blacklight.network.VehicleDestructionPayload)field(s,"data");double[] low={Double.POSITIVE_INFINITY};int[] cubes={0};
        root.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{cubes[0]++;for(float x:new float[]{cube.minX,cube.maxX})for(float y:new float[]{cube.minY,cube.maxY})for(float z:new float[]{cube.minZ,cube.maxZ})low[0]=Math.min(low[0],pose.pose().transformPosition(new org.joml.Vector3f(x/16,y/16,z/16)).y);});
        row.addProperty("lowest",origin.y+low[0]);row.addProperty("cubes",cubes[0]);row.addProperty("submerged",data.submerged());
        if(origin.y+low[0]<80.95)throw new IllegalStateException("UAV wreck below floor: "+(origin.y+low[0]));if(scene==3&&!data.submerged())throw new IllegalStateException("Submerged UAV receipt dry");return row;
    }
    private static JsonObject lamp(MilitaryUav drone)throws Exception{
        var mc=Minecraft.getInstance();var row=new JsonObject();
        if(!(mc.getEntityRenderDispatcher().getRenderer(drone) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return row;
        var processor=renderer.getGeoModel().getAnimationProcessor();
        if(processor.getBone("rotor_0")==null||processor.getBone("rotor_1")!=null)throw new IllegalStateException("Source UAV has one rotor");
        if(!drone.isAlive())return row;
        String[] names={"green","yellow","red"};int count=0;software.bernie.geckolib.cache.object.GeoBone head=null;
        for(int i=0;i<names.length;i++){var b=(software.bernie.geckolib.cache.object.GeoBone)processor.getBone("lamp_"+names[i]);if(b==null)throw new IllegalStateException("Missing lamp bone");if(!b.isHidden()){count++;if(drone.warningLamp()!=i)throw new IllegalStateException("Wrong visible recognition lamp");head=b;}}
        if(count!=1)throw new IllegalStateException("Expected one visible recognition lamp: "+count);
        boolean tracked=head.isTrackingMatrices();head.setTrackingMatrices(true);if(!tracked)return row;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        var matrix=new org.joml.Matrix4f(head.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
        var p=matrix.transformPosition(new org.joml.Vector3f(0,0,0));
        var from=new Vec3(p.x,p.y,p.z).subtract(drone.position()).add(drone.getPosition(partial));
        float yaw=net.minecraft.util.Mth.rotLerp(partial,drone.yBodyRotO,drone.yBodyRot);
        var offset=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw)).transformPosition(new org.joml.Vector3f(-.155F,.428F,-.964F));
        var expected=drone.getPosition(partial).add(offset.x,offset.y+.01,offset.z);double error=from.distanceTo(expected);
        String state=names[drone.warningLamp()];lampStates.add(state);row.addProperty("state",state);row.add("nativePosition",new Gson().toJsonTree(from));row.addProperty("sourceLocatorError",error);
        if(error>.004)throw new IllegalStateException("Lamp diverges from source locator: "+error);
        beamFrames++;return row;
    }
}
