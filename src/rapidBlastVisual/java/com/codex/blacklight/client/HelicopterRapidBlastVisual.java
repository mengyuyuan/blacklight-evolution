package com.codex.blacklight.client;

import com.codex.blacklight.client.vehicle.HelicopterControls;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.codex.blacklight.military.aircraft.HelicopterHellfireRack;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;



/** Disposable native client: real pilot, normal network input, actual projectiles and HUD. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HelicopterRapidBlastVisual {
    private static final JsonArray frames=new JsonArray(),trace=new JsonArray(),launches=new JsonArray(),soundEvents=new JsonArray();
    private static final Set<Integer> captured=new HashSet<>();
    private static final Set<UUID> observed=new HashSet<>();
    private static final Map<UUID,Integer> blastAges=new HashMap<>();
    private static final Set<Long> impactReceipts=new HashSet<>();
    private static final JsonArray blastTrace=new JsonArray();
    private static double maximumEffectAge;
    private static final Set<String> activeSounds=new TreeSet<>();
    private static final List<SoundInstance> playing=new ArrayList<>();
    private static boolean initialized,done,selectedRapid,selectedHellfire,rapidHud,hellfireHud;
    private static volatile boolean ready,finished;
    private static volatile int age,craftId=-1;
    private static volatile String error;
    private static int lastRapid=-1,rapidCount,hellfireCount,spentRails;
    private static double maximumMuzzleError;
    private static long start;
    private static Path output;
    private static BlackwatchHelicopter craft;
    private static ArmorStand camera;

    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e){
        if(!initialized||e.getSound()==null)return;String name=e.getSound().getLocation().toString();
        if(!name.startsWith("blacklight:vehicle.")&&!name.equals("blacklight:firearm.impact"))return;
        playing.add(e.getSound());var row=new JsonObject();row.addProperty("age",age);row.addProperty("sound",name);soundEvents.add(row);
    }
    @SubscribeEvent public static void input(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.START)return;
        if(!ready||done)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;
        for(var key:mc.options.keyMappings)key.setDown(false);
        if(age>=110&&!selectedRapid){HelicopterControls.requestWeaponSwitch();selectedRapid=true;}
        if(age>=180&&!selectedHellfire){HelicopterControls.requestWeaponSwitch();selectedHellfire=true;}
        mc.options.keyUse.setDown(age>=120&&age<160||age>=195&&age<201);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(done||System.getProperty("blacklight.test.rapidblast","").isBlank())return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').startsWith("D:/CodexCache/creature-combat-qa/")||!mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable QA world required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.rapidblast")).toAbsolutePath().normalize();if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: required");Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"RUNNING");
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.fov().set(75);mc.options.guiScale().set(2);mc.options.framerateLimit().set(60);mc.options.particles().set(ParticleStatus.ALL);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
                for(var source:List.of(net.minecraft.sounds.SoundSource.MASTER,net.minecraft.sounds.SoundSource.HOSTILE,net.minecraft.sounds.SoundSource.NEUTRAL))mc.options.getSoundSourceOptionInstance(source).set(1.0);
                mc.setScreen(null);camera=new ArmorStand(mc.level,0,0,0);initialized=true;setup();return;
            }
            mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(org.lwjgl.openal.AL10.alGetListenerf(org.lwjgl.openal.AL10.AL_GAIN)!=0)throw new IllegalStateException("QA became audible");
            for(var sound:playing)if(mc.getSoundManager().isActive(sound))activeSounds.add(sound.getLocation().toString());
            if(mc.level.getEntity(craftId) instanceof BlackwatchHelicopter local){
                var data=local.controlData();rapidHud|=data.getBoolean("RapidSelected")&&data.getInt("RapidLeft")<75&&data.getInt("RapidRight")<75;
                hellfireHud|=age>205&&!data.getBoolean("RapidSelected")&&data.getInt("Rockets")==7;
                if(age>215){var renderer=mc.getEntityRenderDispatcher().getRenderer(local);var field=renderer.getClass().getDeclaredField("model");field.setAccessible(true);var model=(com.codex.blacklight.client.model.HelicopterModel)field.get(renderer);var root=model.pose(local,0);int hidden=0;for(int side:new int[]{-1,1})for(int slot=0;slot<4;slot++)if(!root.getChild("hellfire_"+side+"_"+slot).visible)hidden++;spentRails=Math.max(spentRails,hidden);}
            }
            inspectEffects();
            if(finished){
                if(impactReceipts.size()!=rapidCount||maximumEffectAge<18||maximumEffectAge>21)throw new IllegalStateException("Native70mm cosmetic receipt/lifetime mismatch: "+impactReceipts.size()+"/"+maximumEffectAge);
                if(!rapidHud||!hellfireHud||spentRails!=1)throw new IllegalStateException("Native HUD/finite rail visibility not proven");
                if(!activeSounds.containsAll(List.of("blacklight:vehicle.rocket","blacklight:firearm.impact","blacklight:vehicle.helicopter_rotor","blacklight:vehicle.helicopter_turbine")))throw new IllegalStateException("Missing active native audio: "+activeSounds);
                done=true;write();Files.writeString(output.resolve("status.txt"),"PASS: actual rapid flight/impact,20tick typed server timeline and cosmetic receipt expiry, network controls and finite rail; screenshots require review");mc.options.keyUse.setDown(false);mc.setCameraEntity(mc.player);mc.stop();
            }
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Native rapid visual QA",ex);try{if(output!=null){write();Files.writeString(output.resolve("status.txt"),"FAIL: "+error);}}catch(Exception ignored){}mc.options.keyUse.setDown(false);mc.setCameraEntity(mc.player);mc.stop();}
    }
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        p.stopRiding();for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        p.setGameMode(GameType.CREATIVE);p.connection.teleport(0,94,0,0,0);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,24000,0,false,false));
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++){level.setBlock(new BlockPos(x,80,z),((x%8==0||z%8==0)?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(),2);for(int y=81;y<=115;y++)if(!level.isEmptyBlock(new BlockPos(x,y,z)))level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(int x=-9;x<=9;x++)for(int y=82;y<=110;y++)level.setBlock(new BlockPos(x,y,32),Blocks.OBSIDIAN.defaultBlockState(),2);
        craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);craft.setPos(0,94,0);level.addFreshEntity(craft);craftId=craft.getId();if(!p.startRiding(craft,true))throw new IllegalStateException("Real pilot boarding failed");start=level.getGameTime();ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!ready||finished||done||mc.getSingleplayerServer()==null||e.level!=mc.getSingleplayerServer().overworld())return;
        try{
            var level=mc.getSingleplayerServer().overworld();age=(int)(level.getGameTime()-start);
            for(var blast:level.getEntitiesOfClass(com.codex.blacklight.firearms.HellfireBlastEntity.class,craft.getBoundingBox().inflate(50),com.codex.blacklight.firearms.HellfireBlastEntity::rapidBlast)){
                blastAges.merge(blast.getUUID(),blast.blastAge(),Math::max);var sample=new JsonObject();sample.addProperty("age",age);sample.addProperty("id",blast.getUUID().toString());sample.addProperty("blastAge",blast.blastAge());blastTrace.add(sample);
            }
            for(var rocket:level.getEntitiesOfClass(VehicleOrdnanceProjectile.class,craft.getBoundingBox().inflate(12),p->p.flightAge()<=1)){
                if(!observed.add(rocket.getUUID()))continue;
                var row=new JsonObject();row.addProperty("age",age);row.addProperty("weapon",rocket.weaponId());row.addProperty("flightAge",rocket.flightAge());row.addProperty("speed",rocket.getDeltaMovement().length());
                if(rocket.rapidRocket()){
                    int side=++rapidCount%2==1?-1:1;var tag=new CompoundTag();rocket.saveWithoutId(tag);var actual=new Vec3(tag.getDouble("VehicleShotX"),tag.getDouble("VehicleShotY"),tag.getDouble("VehicleShotZ"));
                    maximumMuzzleError=Math.max(maximumMuzzleError,actual.distanceTo(craft.weaponPoint(HelicopterHellfireRack.rapidPod(side).add(0,0,.748163))));
                    if(lastRapid>=0&&age-lastRapid!=5)throw new IllegalStateException("Real client rapid cadence "+(age-lastRapid));lastRapid=age;
                    if(tag.contains("HellfireFlight")||tag.hasUUID("HellfireTarget"))throw new IllegalStateException("Rapid inherited guidance");
                }else if(rocket.hellfire())hellfireCount++;
                launches.add(row);
            }
            var row=new JsonObject();row.addProperty("age",age);row.addProperty("selectedRapid",craft.combat().rapidSelected());row.addProperty("left",craft.combat().rapid().left());row.addProperty("right",craft.combat().rapid().right());row.addProperty("rapidShots",craft.combat().rapid().shots());row.addProperty("hellfire",craft.combat().rockets());row.addProperty("rotor",craft.rotor());trace.add(row);
            if(age>=300){if(blastAges.size()!=rapidCount||blastAges.values().stream().anyMatch(n->n<19)||!level.getEntitiesOfClass(com.codex.blacklight.firearms.HellfireBlastEntity.class,craft.getBoundingBox().inflate(50),com.codex.blacklight.firearms.HellfireBlastEntity::rapidBlast).isEmpty())throw new IllegalStateException("Native70mm20tick timeline lifecycle mismatch: "+blastAges);
                if(rapidCount<7||rapidCount>9||hellfireCount!=1||craft.combat().rapid().shots()!=rapidCount||craft.combat().rapid().left()+craft.combat().rapid().right()!=150-rapidCount||craft.combat().rockets()!=7||craft.combat().rounds()!=240||craft.combat().rapidSelected()||maximumMuzzleError>1e-4)throw new IllegalStateException("Real network firing/stores mismatch rapid="+rapidCount+" Hellfire="+hellfireCount);finished=true;}
        }catch(Exception ex){error=ex.toString();}
    }
    @SubscribeEvent public static void before(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.START)return;
        if(!initialized||!ready||done||camera==null)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(craftId) instanceof BlackwatchHelicopter local))return;
        Vec3 eye=local.position().add(-7,5,-11),center=new Vec3(0,95.4,32),position=eye.add(0,-camera.getEyeHeight(),0),look=center.subtract(eye);
        camera.setPos(position);camera.xo=position.x;camera.yo=position.y;camera.zo=position.z;camera.xOld=position.x;camera.yOld=position.y;camera.zOld=position.z;
        camera.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));camera.setXRot((float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();camera.setYHeadRot(camera.getYRot());camera.yHeadRotO=camera.getYRot();mc.setCameraEntity(camera);
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(!initialized||!ready||done||age<1||((age<105||age>225)&&age%10!=0)||!captured.add(age))return;
        try{var mc=Minecraft.getInstance();String name=String.format(Locale.ROOT,"rapidblast-%03d.png",age);try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}var row=new JsonObject();row.addProperty("image",name);row.addProperty("age",age);frames.add(row);}catch(Exception ex){error=ex.toString();}
    }
    private static void inspectEffects()throws Exception{
        var mc=Minecraft.getInstance();var field=com.codex.blacklight.client.renderer.GunplayVisuals.class.getDeclaredField("ACTIVE");field.setAccessible(true);
        for(Object effect:(java.util.Collection<?>)field.get(null)){
            var getter=effect.getClass().getDeclaredMethod("data");getter.setAccessible(true);var p=(com.codex.blacklight.network.GunplayFeedbackPayload)getter.invoke(effect);
            if(p.event()!=1||p.weapon()!=14)continue;impactReceipts.add(p.seed());var time=effect.getClass().getDeclaredMethod("at");time.setAccessible(true);double lived=mc.level.getGameTime()-((Number)time.invoke(effect)).doubleValue();maximumEffectAge=Math.max(maximumEffectAge,lived);
            if(finished)throw new IllegalStateException("Expired70mm effect survived the native scene");
        }
    }
    private static void write()throws Exception{var result=new JsonObject();result.add("frames",frames);result.add("trace",trace);result.add("launches",launches);result.add("blastTrace",blastTrace);result.add("blastMaximumAges",new Gson().toJsonTree(blastAges));result.addProperty("rapidImpactReceipts",impactReceipts.size());result.addProperty("maximumEffectAge",maximumEffectAge);result.add("soundEvents",soundEvents);result.add("activeSounds",new Gson().toJsonTree(activeSounds));result.addProperty("rapidCount",rapidCount);result.addProperty("hellfireCount",hellfireCount);result.addProperty("maximumMuzzleError",maximumMuzzleError);result.addProperty("rapidHud",rapidHud);result.addProperty("hellfireHud",hellfireHud);result.addProperty("spentRails",spentRails);result.addProperty("error",error);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
}
