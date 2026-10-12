package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.animation.NativePlayerActionBackend;
import com.codex.blacklight.client.renderer.AuthoredDevastatorMesh;
import com.codex.blacklight.client.renderer.DevastatorEffectRenderer;
import com.codex.blacklight.client.renderer.LiquidBiomassMaterial;
import com.codex.blacklight.client.renderer.RigidBiomassMaterial;
import com.codex.blacklight.combat.BulletdiveTerrain;
import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.combat.WeaponCombatSystem;
import com.codex.blacklight.combat.WeaponVisual;
import com.codex.blacklight.movement.MovementState;
import com.codex.blacklight.network.WeaponInputPayload;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.stb.STBImageWrite;

/** Real network/input, authoritative damage and native framebuffer acceptance.
 * The literal disposable game directory/save guards all fixture and camera changes. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class StandaloneSkillSmoke {
    private static final String PROPERTY="blacklight.test.standaloneSkill";
    private static final Path QA_DIRECTORY=Path.of("D:/Blacklight-Standalone/run-native-qa").toAbsolutePath().normalize();
    private static final String QA_SAVE="Blacklight Standalone QA v4";
    private enum Mode { RELEASE, LOW_RESERVE, LOCKED, CANCEL, SHORT }
    private record Case(String name,int kind,boolean air,Mode mode) {
        CombatSkill skill(){return switch(kind){case 0->CombatSkill.GROUNDSPIKE_GRAVEYARD;case 1->CombatSkill.TENDRIL_BARRAGE;case 2->CombatSkill.CRITICAL_PAIN;default->CombatSkill.BULLETDIVE_DROP;};}
        String move(){return kind==3?"bulletdive_drop":(air?"dev_air_":"dev_")+new String[]{"groundspike","tendril","critical_pain"}[kind];}
        boolean uniquePainTarget(){return name.equals("ground_pain_far")||name.equals("ground_pain_up");}
        boolean distantPain(){return name.equals("ground_pain_far");}
        boolean verticalPain(){return name.equals("ground_pain_up");}
    }
    private enum Stage { SETTLE,CHECKS,PREPARE,READY,START,CHARGE,RELEASE,TAIL,VERIFY,NEXT,DRAIN,DONE }
    private static final List<Case> CASES=cases();
    private static final JsonArray requests=new JsonArray(),serverTicks=new JsonArray(),clientTicks=new JsonArray(),hits=new JsonArray(),views=new JsonArray(),results=new JsonArray();
    private static final Set<String> violations=new LinkedHashSet<>(),captures=new LinkedHashSet<>(),sourceClips=new LinkedHashSet<>();
    private static final Map<KeyMapping,Boolean> savedKeys=new IdentityHashMap<>();
    private static final Map<Integer,Integer> hitCounts=new HashMap<>();
    private static final Map<Integer,Double> fixtureInitialRanges=new HashMap<>();
    private static final Set<Integer> fixtureTargets=new HashSet<>(),farTargets=new HashSet<>();
    private static final List<Double> nativeFrameDeltasMs=new ArrayList<>(),effectFrameDeltasMs=new ArrayList<>(),caseFrameDeltasMs=new ArrayList<>();
    private static Stage stage=Stage.SETTLE;
    private static volatile boolean serverReady;
    private static volatile ServerSample serverSample;
    private record ServerSample(int caseIndex,long tick,int kind,int phase,long session,long released,
                                float reserve,String clip,double y,boolean ground,boolean gliding,int totalHits,int duplicates,int farTargetHits) { }
    private static boolean initialized,disabled,completed,observedSession,observedRelease,observedEffect,observedLanding;
    private static int index,stageTicks,totalFrames,reserveDrops,pngLevel,pngFilter,maximumVertices;
    private static volatile int highestControllerHits;
    private static volatile boolean controllerSpent;
    private static volatile DevastatorSystem.Snapshot lastControllerSnapshot;
    private static volatile BulletdiveTerrain.Snapshot lastTerrainSnapshot;
    private static volatile boolean injectionScheduled;
    private static volatile JsonObject ordinaryInjection;
    private static volatile int primaryTargetId=-1;
    private static volatile UUID primaryTargetUuid;
    private static volatile Vec3 primaryTargetPosition;
    private static volatile boolean primaryServerLocked,observedVerticalServerDirection,observedUniquePrimaryHit;
    private static boolean observedVerticalClientDirection;
    private static JsonObject primaryTrackingAck;
    private static long shortReleaseAgeTicks=-1;
    private static boolean laneReady;
    private static float previousReserve=Float.NaN;
    private static long observedSessionId=Long.MIN_VALUE;
    private static UUID fixtureOwner;
    private static Path output;
    private static CameraType previousCamera;
    private static boolean previousGui,previousPause,previousMouse;
    private static int previousFov;
    private static float previousYaw,previousPitch;
    private static ThreadPoolExecutor writer;
    private static final AtomicInteger submittedImages=new AtomicInteger(),writtenImages=new AtomicInteger();
    private static volatile String imageError;
    private static CompletableFuture<JsonObject> criticalCheckFuture;
    private static CompletableFuture<JsonObject> terrainCheckFuture;
    private static JsonObject criticalMassChecks,terrainChecks;
    private static long lastNativeFrameNanos;
    private static boolean authoredModelAvailable,gpuMeshesReady,sideCameraActive;
    private static String sideCaptureName;
    private static Entity sidePreviousCameraEntity;
    private static CameraType sidePreviousCameraType;
    private StandaloneSkillSmoke() { }
    private static List<Case> cases() {
        var cases=new ArrayList<Case>();
        for(int kind=0;kind<3;kind++)for(boolean air:new boolean[]{false,true})
            cases.add(new Case((air?"air_":"ground_")+new String[]{"graveyard","barrage","pain"}[kind],kind,air,Mode.RELEASE));
        cases.add(new Case("ground_pain_far",2,false,Mode.RELEASE));
        cases.add(new Case("ground_pain_up",2,false,Mode.RELEASE));
        cases.add(new Case("bulletdive",3,true,Mode.RELEASE));
        cases.add(new Case("graveyard_low_reserve",0,false,Mode.LOW_RESERVE));
        cases.add(new Case("barrage_locked",1,false,Mode.LOCKED));
        cases.add(new Case("pain_charge_cancel",2,false,Mode.CANCEL));
        cases.add(new Case("pain_short_charge",2,false,Mode.SHORT));
        String filter=System.getProperty("blacklight.test.standaloneSkill.cases","");
        return cases.stream().filter(c->filter.isBlank()||Arrays.stream(filter.split(",")).anyMatch(s->s.trim().equals(c.name()))).toList();
    }
    private static boolean active(){return initialized&&!disabled&&!completed;}
    private static Case current(){return index<CASES.size()?CASES.get(index):null;}
    private static void advance(Stage next){stage=next;stageTicks=0;}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void isolate(ClientTickEvent.Pre event) {
        if(!active())return;var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();
        for(var key:savedKeys.keySet()){key.setDown(false);while(key.consumeClick()){} }
        if(current()!=null&&current().kind()==3&&serverReady&&mc.player!=null&&mc.player.getY()>257&&!mc.player.onGround()
                &&(stage==Stage.PREPARE||stage==Stage.READY||stage==Stage.START||stage==Stage.CHARGE))mc.options.keyJump.setDown(true);
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if(mc.player!=null){mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event){if(active())event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if(active()){event.setYaw(sideCameraActive?67.5F:-135);event.setPitch(sideCameraActive?8F:-12);event.setRoll(0);}
    }
    @SubscribeEvent public static void sideHand(net.neoforged.neoforge.client.event.RenderHandEvent event){
        if(active()&&sideCameraActive)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void distance(CalculateDetachedCameraDistanceEvent event) {
        if(active())event.setDistance((sideCameraActive?7F:12F)/Math.max(.001F,event.getEntityScalingFactor()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        if(disabled||completed||System.getProperty(PROPERTY,"").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(server==null||mc.level==null||mc.player==null)return;
        var game=mc.gameDirectory.toPath().toAbsolutePath().normalize();var save=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!game.equals(QA_DIRECTORY)||!save.equals(QA_DIRECTORY.resolve("saves").resolve(QA_SAVE))){
            disabled=true;BlacklightNeoMod.LOGGER.error("Skill QA refused non-disposable directory/save: {} / {}",game,save);return;
        }
        try {
            if(!initialized){setup(mc);return;}
            if(imageError!=null)throw new IllegalStateException(imageError);
            stageTicks++;if(stageTicks%20==0)write(false,null);
            if(stageTicks>(stage==Stage.CHECKS?1200:420))throw new IllegalStateException("Skill QA timed out: "+stage+" / "+(current()==null?"done":current().name())+" / "+serverSample);
            recordClient(mc);
            switch(stage) {
                case SETTLE -> {if(stageTicks>=80){if(CASES.isEmpty())throw new IllegalStateException("No selected skill cases");startCriticalChecks(mc);}}
                case CHECKS -> {if(terrainCheckFuture==null&&criticalCheckFuture.isDone()){
                    criticalMassChecks=criticalCheckFuture.join();write(false,null);
                    if(!criticalMassChecks.has("passed")||!criticalMassChecks.get("passed").getAsBoolean())throw new IllegalStateException("Real Critical Mass / Adrenaline acceptance failed: "+criticalMassChecks);
                    startTerrainChecks(mc);
                }else if(terrainCheckFuture!=null&&terrainCheckFuture.isDone()){
                    terrainChecks=terrainCheckFuture.join();write(false,null);
                    if(!terrainChecks.has("passed")||!terrainChecks.get("passed").getAsBoolean())throw new IllegalStateException("Real Bulletdive terrain acceptance failed: "+terrainChecks);
                    prepare(mc);
                }}
                case PREPARE -> {if(serverReady&&serverSample!=null&&serverSample.caseIndex()==index&&stageTicks>=2
                        &&mc.player.getData(ModAttachments.DEVASTATOR_VISUAL).kind()<0
                        &&Math.abs(mc.player.getData(ModAttachments.CRITICAL_MASS).reserve()-(current().mode()==Mode.LOW_RESERVE?.25F:1F))<.001F
                        &&(current().kind()!=3||serverSample.gliding()&&mc.player.getData(ModAttachments.GLIDE_UNTIL)>mc.level.getGameTime())
                        &&targetTrackingAcknowledged(mc)
                        &&(!current().air()||mc.player.getY()>257&&!mc.player.onGround()))advance(Stage.READY);}
                case READY -> {send(mc,true,false);advance(Stage.START);}
                case START,CHARGE -> {
                    if(stageTicks%10==0)send(mc,true,false);
                    var sample=serverSample;
                    if(sample!=null&&sample.caseIndex()==index&&sample.kind()==current().kind()){
                        observedSession=true;observedSessionId=sample.session();
                        if(current().mode()==Mode.CANCEL&&sample.phase()==0){send(mc,false,true);advance(Stage.TAIL);}
                        else if(current().mode()==Mode.SHORT&&sample.phase()==0){
                            shortReleaseAgeTicks=sample.tick()-sample.session();
                            require(shortReleaseAgeTicks<=2,"short-charge keyup did not occur within two source ticks");
                            send(mc,false,false);advance(Stage.TAIL);
                        }
                        else if(sample.phase()>0){observedRelease=true;send(mc,false,false);advance(Stage.RELEASE);}
                        else if(stage==Stage.START)advance(Stage.CHARGE);
                    }
                    if(current().mode()==Mode.LOW_RESERVE||current().mode()==Mode.LOCKED){
                        if(stageTicks>=45){send(mc,false,false);advance(Stage.TAIL);}
                    }else if(stageTicks>=100){send(mc,false,false);advance(Stage.RELEASE);}
                }
                case RELEASE -> {
                    var sample=serverSample;
                    if(sample!=null&&sample.caseIndex()==index){
                        if(sample.kind()>=0&&sample.phase()>0)observedRelease=true;
                        if(current().name().equals("ground_graveyard")&&sample.kind()==0&&sample.phase()>0&&!injectionScheduled)injectOrdinaryRequest(mc);
                        if(sample.phase()==3)observedLanding=true;
                        if(sample.kind()<0&&stageTicks>=12)advance(Stage.TAIL);
                    }
                }
                case TAIL -> {if(stageTicks>=35)advance(Stage.VERIFY);}
                case VERIFY -> {verify(mc);advance(Stage.NEXT);}
                case NEXT -> {index++;if(index<CASES.size())prepare(mc);else{send(mc,false,true);advance(Stage.DRAIN);writer.shutdown();}}
                case DRAIN -> {if(writer.isTerminated()){completed=true;restore(mc);write(true,null);mc.stop();}}
                default -> { }
            }
        }catch(Exception error){fail(error);}
    }
    private static void setup(Minecraft mc)throws Exception {
        output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();Files.createDirectories(output);
        previousCamera=mc.options.getCameraType();previousGui=mc.options.hideGui;previousPause=mc.options.pauseOnLostFocus;previousMouse=mc.mouseHandler.isMouseGrabbed();previousFov=mc.options.fov().get();previousYaw=mc.player.getYRot();previousPitch=mc.player.getXRot();
        for(var key:List.of(mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift,mc.options.keySprint,mc.options.keyAttack,mc.options.keyUse,mc.options.keyTogglePerspective))savedKeys.put(key,key.isDown());
        mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.fov().set(70);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.setScreen(null);mc.mouseHandler.releaseMouse();
        pngLevel=STBImageWrite.stbi_write_png_compression_level.get(0);pngFilter=STBImageWrite.stbi_write_force_png_filter.get(0);STBImageWrite.stbi_write_png_compression_level.put(0,1);STBImageWrite.stbi_write_force_png_filter.put(0,0);
        writer=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(6),r->{var t=new Thread(r,"skill-qa-png");t.setDaemon(true);return t;});
        ClientWeaponCombat.setFixtureOwnsInput(true);
        fixtureOwner=mc.player.getUUID();
        authoredModelAvailable=AuthoredDevastatorMesh.available();require(authoredModelAvailable,"missing authored three-dimensional devastator model");
        initialized=true;
    }
    private static void startCriticalChecks(Minecraft mc) {
        criticalCheckFuture=new CompletableFuture<>();advance(Stage.CHECKS);
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        server.execute(()->{try {
            var p=server.getPlayerList().getPlayer(uuid);
            StandaloneCriticalMassChecks.run(p).whenComplete((result,error)->{
                if(error!=null)criticalCheckFuture.completeExceptionally(error);else criticalCheckFuture.complete(result);
            });
        }catch(Exception error){criticalCheckFuture.completeExceptionally(error);}});
    }
    private static void startTerrainChecks(Minecraft mc) {
        terrainCheckFuture=new CompletableFuture<>();var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        server.execute(()->{try {
            var p=server.getPlayerList().getPlayer(uuid);
            StandaloneBulletTerrainChecks.run(p).whenComplete((result,error)->{
                if(error!=null)terrainCheckFuture.completeExceptionally(error);else terrainCheckFuture.complete(result);
            });
        }catch(Exception error){terrainCheckFuture.completeExceptionally(error);}});
    }
    private static void prepare(Minecraft mc) {
        restoreSideCamera(mc);
        primaryTargetId=-1;primaryTargetUuid=null;primaryTargetPosition=null;primaryServerLocked=false;primaryTrackingAck=null;observedUniquePrimaryHit=false;observedVerticalServerDirection=false;observedVerticalClientDirection=false;
        serverReady=false;observedSession=observedRelease=observedEffect=observedLanding=false;reserveDrops=0;highestControllerHits=0;controllerSpent=false;lastControllerSnapshot=null;lastTerrainSnapshot=null;injectionScheduled=false;ordinaryInjection=null;shortReleaseAgeTicks=-1;previousReserve=Float.NaN;observedSessionId=Long.MIN_VALUE;sourceClips.clear();caseFrameDeltasMs.clear();sideCaptureName=null;advance(Stage.PREPARE);
        send(mc,false,true);var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();int caseId=index;Case c=current();
        server.execute(()->{try {
            var level=server.overworld();var p=server.getPlayerList().getPlayer(uuid);DevastatorSystem.cancel(p);WeaponCombatSystem.cancel(p);NativePlayerActionBackend.clear(p);CombatTargeting.requestLock(p,-1);
            synchronized(fixtureTargets){for(int id:fixtureTargets){var old=level.getEntity(id);if(old!=null&&!(old instanceof net.minecraft.world.entity.player.Player))old.discard();}}
            for(var old:level.getEntitiesOfClass(LivingEntity.class,new AABB(-25,240,-25,25,288,25)))if(!(old instanceof net.minecraft.world.entity.player.Player))old.discard();
            for(int x=-22;x<=22;x++)for(int z=-22;z<=22;z++){
                var floor=new BlockPos(x,250,z);if(!level.getBlockState(floor).is(Blocks.GRAY_CONCRETE))level.setBlockAndUpdate(floor,Blocks.GRAY_CONCRETE.defaultBlockState());
                if(!laneReady)for(int y=251;y<=275;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }laneReady=true;
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);state.addEvolutionPoints(10_000);state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);state.buyCombatSkill(CombatSkill.CRITICAL_MASS);
            if(c.mode()!=Mode.LOCKED)state.buyCombatSkill(c.skill());
            p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(c.mode()==Mode.LOW_RESERVE?.25F:1F,true));p.syncData(ModAttachments.CRITICAL_MASS);
            p.setData(ModAttachments.ADRENALINE,AdrenalineState.EMPTY);p.syncData(ModAttachments.ADRENALINE);
            p.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);
            p.setData(ModAttachments.UTILITY_POWERS,0);p.syncData(ModAttachments.UTILITY_POWERS);p.setData(ModAttachments.POWER_MORPH,PowerMorph.NONE);p.syncData(ModAttachments.POWER_MORPH);
            p.getData(ModAttachments.CLAW_COMBAT).reset();p.getData(ModAttachments.MOVEMENT_STATE).reset();p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();
            p.connection.teleport(.5,c.air()?259:251,.5,0,0);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(!c.air());p.fallDistance=0;
            p.setData(ModAttachments.GLIDE_UNTIL,0L);p.syncData(ModAttachments.GLIDE_UNTIL);
            synchronized(fixtureTargets){fixtureTargets.clear();farTargets.clear();fixtureInitialRanges.clear();hitCounts.clear();}
            int primary=-1;
            if(c.distantPain())for(int x=-1;x<=1;x++)for(int z=99;z<=101;z++)level.setBlockAndUpdate(new BlockPos(x,250,z),Blocks.GRAY_CONCRETE.defaultBlockState());
            for(int target=0;target<(c.uniquePainTarget()?1:12);target++){
                boolean far=c.distantPain()||target>=8;double angle=target>=8?(target-8)*Math.PI/2:target*Math.PI/4;
                double range=c.distantPain()?100:c.verticalPain()?12:far?20:5;
                var mob=new Husk(EntityType.HUSK,level);mob.setNoAi(true);mob.setPersistenceRequired();mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20_000);mob.setHealth(20_000);
                mob.setCustomName(Component.literal((c.uniquePainTarget()?"QA unique "+c.name()+" ":far?"QA 20格范围目标 ":"QA 技能目标 ")+target));
                mob.moveTo(c.verticalPain()?.5:.5+Math.sin(angle)*range,c.verticalPain()?263:251,c.verticalPain()?.5:.5+Math.cos(angle)*range,180,0);
                if(c.verticalPain())mob.setNoGravity(true);
                level.addFreshEntity(mob);
                synchronized(fixtureTargets){fixtureTargets.add(mob.getId());fixtureInitialRanges.put(mob.getId(),range);if(far)farTargets.add(mob.getId());}
                if(target==0){primary=mob.getId();primaryTargetId=primary;primaryTargetUuid=mob.getUUID();primaryTargetPosition=mob.position();}
            }
            if(c.kind()==2){CombatTargeting.requestLock(p,primary);primaryServerLocked=p.getData(ModAttachments.TARGET_LOCK_ID)==primary&&CombatTargeting.locked(p)!=null;}
            serverReady=true;
        }catch(Exception error){fail(error);}});
    }
    private static boolean targetTrackingAcknowledged(Minecraft mc) {
        Case c=current();if(c==null||!c.uniquePainTarget())return true;
        var target=mc.level.getEntity(primaryTargetId);
        if(!(target instanceof Husk)||primaryTargetUuid==null||primaryTargetPosition==null
                ||!target.getUUID().equals(primaryTargetUuid)||target.position().distanceToSqr(primaryTargetPosition)>.25
                ||!primaryServerLocked||mc.player.getData(ModAttachments.TARGET_LOCK_ID)!=primaryTargetId
                ||c.verticalPain()&&!target.isNoGravity())return false;
        if(primaryTrackingAck==null){
            var ack=new JsonObject();ack.addProperty("tick",mc.level.getGameTime());ack.addProperty("entityId",primaryTargetId);ack.addProperty("uuid",primaryTargetUuid.toString());ack.addProperty("clientTracked",true);ack.addProperty("serverLocked",primaryServerLocked);ack.addProperty("clientLocked",true);ack.addProperty("x",target.getX());ack.addProperty("y",target.getY());ack.addProperty("z",target.getZ());ack.addProperty("noGravity",target.isNoGravity());primaryTrackingAck=ack;
        }return true;
    }
    private static void send(Minecraft mc,boolean held,boolean cancelled) {
        Case c=current();boolean attack=held&&(c==null||c.kind()==0||c.kind()==2),special=held&&(c==null||c.kind()==0||c.kind()==1||c.kind()==3),action=held&&c!=null&&(c.kind()==1||c.kind()==2);
        PacketDistributor.sendToServer(new WeaponInputPayload(attack,special,action,false,cancelled));
        var row=new JsonObject();row.addProperty("case",c==null?"done":c.name());row.addProperty("tick",mc.level.getGameTime());row.addProperty("attack",attack);row.addProperty("special",special);row.addProperty("action",action);row.addProperty("cancelled",cancelled);requests.add(row);
    }
    private static void injectOrdinaryRequest(Minecraft mc) {
        injectionScheduled=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        server.execute(()->{try {
            var p=server.getPlayerList().getPlayer(uuid);var before=DevastatorSystem.snapshot(p);
            require(before!=null&&before.move().equals("dev_groundspike"),"ordinary RPC injection missed the active formal release");
            WeaponCombatSystem.request(p,com.codex.blacklight.combat.WeaponMove.Input.ATTACK);
            var after=DevastatorSystem.snapshot(p);var weapon=p.getData(ModAttachments.WEAPON_VISUAL).spec();
            boolean preserved=before!=null&&after!=null&&before.session()==after.session()&&before.move().equals(after.move())
                    &&weapon!=null&&weapon.id.equals(before.move())&&p.getData(ModAttachments.PLAYER_ACTION).clipId().equals(before.move());
            require(preserved,"ordinary public request overwrote the formal devastator session/visual");
            var row=new JsonObject();row.addProperty("injection",true);row.addProperty("path","WeaponCombatSystem.request(player, ATTACK)");row.addProperty("tick",p.level().getGameTime());row.addProperty("preserved",preserved);
            if(before!=null){row.addProperty("beforeMove",before.move());row.addProperty("beforeSession",before.session());}
            if(after!=null){row.addProperty("afterMove",after.move());row.addProperty("afterSession",after.session());}
            row.addProperty("afterSourceClip",p.getData(ModAttachments.PLAYER_ACTION).clipId());ordinaryInjection=row;
        }catch(Exception error){fail(error);}});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void server(PlayerTickEvent.Post event) {
        if(!active()||!serverReady||!(event.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(fixtureOwner))return;
        var visual=p.getData(ModAttachments.DEVASTATOR_VISUAL);var mass=p.getData(ModAttachments.CRITICAL_MASS);var action=p.getData(ModAttachments.PLAYER_ACTION);
        var probe=DevastatorSystem.snapshot(p);
        if(current()!=null&&current().verticalPain()&&visual.kind()==2&&visual.phase()>0&&visual.direction().y>.99)observedVerticalServerDirection=true;
        if(probe!=null){lastControllerSnapshot=probe;highestControllerHits=Math.max(highestControllerHits,probe.hits());controllerSpent|=probe.spent();
            if(current()!=null)require(probe.move().equals(current().move()),"server selected wrong ground/air source move: "+probe.move()+" for "+current().name());
        }
        if(current()!=null&&current().kind()==3&&lastControllerSnapshot!=null){
            var terrain=BulletdiveTerrain.snapshot(p,lastControllerSnapshot.session());if(terrain!=null)lastTerrainSnapshot=terrain;
        }
        int total=0,duplicates=0,farHits=0;synchronized(fixtureTargets){for(var entry:hitCounts.entrySet()){int count=entry.getValue();total+=count;duplicates+=Math.max(0,count-1);if(farTargets.contains(entry.getKey()))farHits+=count;}}
        boolean gliding=p.getData(ModAttachments.MOVEMENT_STATE).mode()==MovementState.Mode.GLIDE&&p.getData(ModAttachments.GLIDE_UNTIL)>p.level().getGameTime();
        serverSample=new ServerSample(index,p.level().getGameTime(),visual.kind(),visual.phase(),visual.session(),visual.released(),mass.reserve(),action.clipId(),p.getY(),p.onGround(),gliding,total,duplicates,farHits);
        var row=new JsonObject();row.addProperty("case",current()==null?"done":current().name());row.addProperty("tick",p.level().getGameTime());row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("session",visual.session());row.addProperty("released",visual.released());row.addProperty("reserve",mass.reserve());row.addProperty("clip",action.clipId());row.addProperty("y",p.getY());row.addProperty("ground",p.onGround());row.addProperty("totalHits",total);row.addProperty("duplicateHits",duplicates);row.addProperty("farTargetHits",farHits);row.addProperty("directionX",visual.direction().x);row.addProperty("directionY",visual.direction().y);row.addProperty("directionZ",visual.direction().z);if(probe!=null){row.addProperty("controllerMove",probe.move());row.addProperty("controllerSpent",probe.spent());row.addProperty("controllerHits",probe.hits());row.addProperty("controllerLanded",probe.landed());row.addProperty("controllerAge",probe.age());}synchronized(serverTicks){serverTicks.add(row);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void hit(LivingDamageEvent.Post event) {
        if(!active()||!serverReady||event.getNewDamage()<=0||!(event.getSource().getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(fixtureOwner))return;
        boolean far;double initialRange;synchronized(fixtureTargets){if(!fixtureTargets.contains(event.getEntity().getId()))return;hitCounts.merge(event.getEntity().getId(),1,Integer::sum);far=farTargets.contains(event.getEntity().getId());initialRange=fixtureInitialRanges.getOrDefault(event.getEntity().getId(),Double.NaN);}
        var probe=DevastatorSystem.snapshot(p);Case c=current();
        if(c!=null&&c.mode()==Mode.RELEASE){
            require(probe!=null&&probe.move().equals(c.move()),"fixture damage did not originate from the expected live skill "+c.name());
            if(c.kind()==3||c.kind()==0&&c.air())require(probe!=null&&probe.landed()&&probe.phase()==DevastatorSystem.LAND,"landing skill damaged a target before authoritative supporting-surface landing "+c.name());
            if(c.uniquePainTarget()){
                require(event.getEntity().getId()==primaryTargetId,"unique Pain fixture damaged another target "+c.name());observedUniquePrimaryHit|=event.getEntity().getId()==primaryTargetId;
                if(c.distantPain())require(p.distanceTo(event.getEntity())>=95,"100-block Pain fixture hit a nearby substitute");
                if(c.verticalPain())require(event.getEntity().getY()-p.getY()>=10&&Math.hypot(event.getEntity().getX()-p.getX(),event.getEntity().getZ()-p.getZ())<.25,"vertical Pain fixture did not hit the real overhead actor");
            }
        }
        var row=new JsonObject();row.addProperty("case",c==null?"done":c.name());row.addProperty("tick",p.level().getGameTime());row.addProperty("target",event.getEntity().getId());row.addProperty("farTarget",far);row.addProperty("initialTargetRange",initialRange);row.addProperty("targetDistanceAtHit",p.distanceTo(event.getEntity()));row.addProperty("targetX",event.getEntity().getX());row.addProperty("targetY",event.getEntity().getY());row.addProperty("targetZ",event.getEntity().getZ());row.addProperty("damage",event.getNewDamage());row.addProperty("playerY",p.getY());row.addProperty("ground",p.onGround());if(probe!=null){row.addProperty("sourceMove",probe.move());row.addProperty("session",probe.session());row.addProperty("phase",probe.phase());row.addProperty("landed",probe.landed());row.addProperty("sourceAge",probe.age());}synchronized(hits){hits.add(row);}
    }
    private static void recordClient(Minecraft mc) {
        var visual=mc.player.getData(ModAttachments.DEVASTATOR_VISUAL);var action=mc.player.getData(ModAttachments.PLAYER_ACTION);var mass=mc.player.getData(ModAttachments.CRITICAL_MASS);
        var row=new JsonObject();row.addProperty("case",current()==null?"done":current().name());row.addProperty("stage",stage.name());row.addProperty("tick",mc.level.getGameTime());row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("clip",action.clipId());row.addProperty("reserve",mass.reserve());clientTicks.add(row);
        if(stage!=Stage.PREPARE&&stage!=Stage.SETTLE&&stage!=Stage.CHECKS){
            if(Float.isFinite(previousReserve)&&mass.reserve()<previousReserve-.001F)reserveDrops++;previousReserve=mass.reserve();
            if(!action.clipId().isEmpty()&&visual.kind()>=0){sourceClips.add(action.clipId());require(MotionLibrary.find(action.clipId()).isPresent(),"missing live source clip "+action.clipId());}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void sideFrame(RenderFrameEvent.Pre event) {
        if(!active()||sideCaptureName==null||sideCameraActive)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(current()==null){sideCaptureName=null;return;}
        try {
            sidePreviousCameraEntity=mc.getCameraEntity();sidePreviousCameraType=mc.options.getCameraType();
            var camera=new ArmorStand(EntityType.ARMOR_STAND,mc.level);
            double yaw=Math.toRadians(67.5),distance=sideDistance();
            camera.moveTo(mc.player.getX()+Math.sin(yaw)*distance,mc.player.getY()+.2,
                    mc.player.getZ()-Math.cos(yaw)*distance,67.5F,8F);
            camera.yHeadRot=camera.yHeadRotO=camera.yBodyRot=camera.yBodyRotO=67.5F;
            camera.setOldPosAndRot();
            // A detached one-frame first-person camera avoids the vanilla orbit
            // interpolating through foreground targets. No player pose is changed.
            sideCameraActive=true;mc.setCameraEntity(camera);mc.options.setCameraType(CameraType.FIRST_PERSON);
        }catch(Exception error){restoreSideCamera(mc);fail(error);}
    }
    private static void restoreSideCamera(Minecraft mc) {
        if(!sideCameraActive)return;
        sideCameraActive=false;mc.setCameraEntity(sidePreviousCameraEntity);mc.options.setCameraType(sidePreviousCameraType);
        sidePreviousCameraEntity=null;sidePreviousCameraType=null;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void frame(RenderFrameEvent.Post event) {
        if(!active())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        try {
            long now=System.nanoTime();double frameMs=lastNativeFrameNanos==0?Double.NaN:(now-lastNativeFrameNanos)/1_000_000D;lastNativeFrameNanos=now;
            if(Double.isFinite(frameMs)&&frameMs>0)nativeFrameDeltasMs.add(frameMs);
            totalFrames++;var stats=DevastatorEffectRenderer.statistics();maximumVertices=Math.max(maximumVertices,stats.vertices());require(stats.vertices()<=DevastatorEffectRenderer.MAX_VERTICES_PER_FRAME,"devastator geometry exceeded global budget");
            var visual=mc.player.getData(ModAttachments.DEVASTATOR_VISUAL);Case c=current();if(c==null){sideCaptureName=null;restoreSideCamera(mc);return;}
            if(stats.effects()>0&&visual.kind()==c.kind()){
                observedEffect=true;require(LiquidBiomassMaterial.ready(),"active skill lost its actual GPU wet material");
                authoredModelAvailable=AuthoredDevastatorMesh.available();require(authoredModelAvailable,"active skill lost its authored three-dimensional effect model");
                gpuMeshesReady=RigidBiomassMaterial.ready();require(gpuMeshesReady,"active authored effect did not use the actual indexed GPU mesh material");
                if(c.verticalPain()&&visual.direction().y>.99)observedVerticalClientDirection=true;
                if(Double.isFinite(frameMs)&&frameMs>0){effectFrameDeltasMs.add(frameMs);caseFrameDeltasMs.add(frameMs);}
                double progress=(visual.age(mc.player,0)-visual.effectStart())/Math.max(.001,visual.effectEnd()-visual.effectStart());
                if(!sideCameraActive&&progress>.28&&progress<.65){capture(mc,c.name()+"_effect",stats);if(c.mode()==Mode.RELEASE&&!c.air())scheduleSide(c.name()+"_effect_side");}
            }
            if(sideCameraActive){try{if(visual.kind()==c.kind()&&stats.effects()>0)capture(mc,sideCaptureName,stats);}finally{sideCaptureName=null;restoreSideCamera(mc);}return;}
            String suffix=visual.kind()==c.kind()?visual.phase()==0?"charge":visual.phase()==3?"land":"release":stage==Stage.TAIL?"cleared":null;
            if(suffix!=null&&((suffix.equals("release")&&stats.effects()>0)||!suffix.equals("release")))capture(mc,c.name()+"_"+suffix,stats);
            if(c.mode()==Mode.RELEASE&&(c.kind()==3||c.kind()==0&&c.air())&&visual.kind()==c.kind()&&visual.phase()==3)scheduleSide(c.name()+"_land_side");
        }catch(Exception error){sideCaptureName=null;restoreSideCamera(mc);fail(error);}
    }
    private static void scheduleSide(String name) {
        if(sideCaptureName==null&&!captures.contains(name)&&writer.getQueue().remainingCapacity()>0)sideCaptureName=name;
    }
    private static int sideDistance(){return current()!=null&&current().kind()==0?38:current()!=null&&current().kind()==1?30:7;}
    private static void capture(Minecraft mc,String name,DevastatorEffectRenderer.Statistics stats) {
        if(captures.contains(name)||writer.getQueue().remainingCapacity()==0)return;
        var image=Screenshot.takeScreenshot(mc.getMainRenderTarget());captures.add(name);submittedImages.incrementAndGet();
        var visual=mc.player.getData(ModAttachments.DEVASTATOR_VISUAL);var row=new JsonObject();row.addProperty("file",name+".png");row.addProperty("tick",mc.level.getGameTime());row.addProperty("camera",mc.options.getCameraType().name());row.addProperty("fixtureSideCamera",sideCameraActive);row.addProperty("fixtureCameraDistance",sideCameraActive?sideDistance():12);row.addProperty("authoredModelAvailable",authoredModelAvailable);row.addProperty("gpuMeshesReady",gpuMeshesReady);row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("sourceClip",mc.player.getData(ModAttachments.PLAYER_ACTION).clipId());row.addProperty("effects",stats.effects());row.addProperty("branches",stats.branches());row.addProperty("vertices",stats.vertices());row.addProperty("triangleIndices",gpuMeshesReady?stats.vertices():0);views.add(row);
        writer.execute(()->{try(image){image.writeToFile(output.resolve(name+".png"));writtenImages.incrementAndGet();}catch(Exception error){imageError=error.toString();}});
    }
    private static void verify(Minecraft mc) {
        Case c=current();var sample=serverSample;require(sample!=null&&sample.caseIndex()==index,"no authoritative observation for "+c.name());
        if(c.mode()==Mode.RELEASE){
            require(observedSession,"real network input did not start "+c.name());require(observedRelease,"no authoritative release for "+c.name());require(!sourceClips.isEmpty(),"no source action clip for "+c.name());require(observedEffect,"no actual GPU effect for "+c.name());require(captures.contains(c.name()+"_effect"),"no actual developed effect framebuffer for "+c.name());require(sample.totalHits()>0,"no real target damage for "+c.name());require(sample.duplicates()==0,"target damaged repeatedly by one skill "+c.name());
            require(highestControllerHits==sample.totalHits(),"actual damage events did not match controller ledger for "+c.name());
            if(c.kind()==0||c.kind()==1)require(sample.farTargetHits()>0,"expanded devastator range did not hit any real 20-block target "+c.name());
            if(c.uniquePainTarget()){
                require(primaryTrackingAck!=null,"unique Pain target lacked actual client/server tracking acknowledgment "+c.name());
                require(observedUniquePrimaryHit&&sample.totalHits()==1,"unique Pain target was not damaged exactly once by the real skill "+c.name());
                if(c.distantPain())require(sample.farTargetHits()==1,"100-block primary was not reached by the formal Pain release");
                if(c.verticalPain())require(observedVerticalServerDirection&&observedVerticalClientDirection,"near-vertical Pain direction was not observed on server and native client");
            }
            if(!c.air())require(captures.contains(c.name()+"_effect_side"),"missing one-frame side view of authored release "+c.name());
            if(c.kind()!=3){require(controllerSpent,"release lacked authoritative reserve commit "+c.name());require(reserveDrops==1,"critical reserve did not drop exactly once for "+c.name());require(sample.reserve()==0,"released devastator retained reserve "+c.name());}
            else require(reserveDrops==0&&!controllerSpent,"Bulletdive incorrectly spent Critical Mass reserve");
            if(c.kind()==3||c.kind()==0&&c.air())require(observedLanding&&lastControllerSnapshot!=null&&lastControllerSnapshot.landed(),"landing skill never reached actual landing phase "+c.name());
            if(c.kind()==3||c.kind()==0&&c.air())require(captures.contains(c.name()+"_land_side"),"missing one-frame side view of actual landing "+c.name());
            if(c.kind()==3)require(lastTerrainSnapshot!=null&&lastTerrainSnapshot.complete(),"actual Bulletdive landing did not produce a completed terrain job");
            if(c.name().equals("ground_graveyard"))require(ordinaryInjection!=null&&ordinaryInjection.get("preserved").getAsBoolean(),"formal release was not tested against the ordinary public RPC");
        }else if(c.mode()==Mode.CANCEL){require(observedSession,"cancel case never charged");require(!observedRelease,"cancelled charge released damage");require(sample.totalHits()==0,"cancelled charge damaged a target");require(reserveDrops==0&&sample.reserve()==1,"cancelled charge consumed reserve");}
        else if(c.mode()==Mode.SHORT){require(observedSession,"short-charge case never entered formal charge");require(!observedRelease&&!controllerSpent,"short keyup released a formal devastator");require(reserveDrops==0&&sample.reserve()==1,"short keyup consumed reserve");require(shortReleaseAgeTicks>=0&&shortReleaseAgeTicks<=2,"short-charge source age was not verified");}
        else {require(!observedSession,"denied case started a devastator "+c.name());require(reserveDrops==0,"denied case consumed critical reserve "+c.name());}
        require(sample.kind()<0&&mc.player.getData(ModAttachments.DEVASTATOR_VISUAL).kind()<0,"finished/cancelled skill retained synchronized state "+c.name());require(DevastatorEffectRenderer.statistics().effects()==0,"finished/cancelled effect remained visible "+c.name());
        var row=new JsonObject();row.addProperty("name",c.name());row.addProperty("expectedSourceMove",c.move());row.addProperty("observedSession",observedSession);row.addProperty("observedRelease",observedRelease);row.addProperty("observedEffect",observedEffect);row.addProperty("observedLanding",observedLanding);row.addProperty("reserveDrops",reserveDrops);row.addProperty("totalHits",sample.totalHits());row.addProperty("farTargetHits",sample.farTargetHits());row.addProperty("farTargetInitialRange",c.distantPain()?100:20);row.addProperty("duplicateHits",sample.duplicates());row.addProperty("maximumObservedControllerHits",highestControllerHits);row.addProperty("controllerSpent",controllerSpent);row.addProperty("finalReserve",sample.reserve());row.addProperty("sessionCleared",sample.kind()<0);row.addProperty("gpuMeshesReady",gpuMeshesReady);row.add("effectFramePerformance",framePerformance(caseFrameDeltasMs));
        if(c.uniquePainTarget()){
            row.addProperty("uniquePrimaryTargetId",primaryTargetId);row.addProperty("uniquePrimaryInitialRange",c.distantPain()?100:12);row.addProperty("observedUniquePrimaryHit",observedUniquePrimaryHit);
            if(primaryTrackingAck!=null)row.add("primaryTrackingAck",primaryTrackingAck.deepCopy());
        }
        if(c.verticalPain()){row.addProperty("observedVerticalServerDirection",observedVerticalServerDirection);row.addProperty("observedVerticalClientDirection",observedVerticalClientDirection);}
        if(lastTerrainSnapshot!=null)row.add("terrain",terrainEvidence(lastTerrainSnapshot));
        if(c.mode()==Mode.SHORT)row.addProperty("shortReleaseAgeTicks",shortReleaseAgeTicks);
        if(ordinaryInjection!=null)row.add("ordinaryRpcInjection",ordinaryInjection.deepCopy());
        if(lastControllerSnapshot!=null){row.addProperty("lastObservedControllerMove",lastControllerSnapshot.move());row.addProperty("lastObservedControllerSession",lastControllerSnapshot.session());row.addProperty("lastObservedControllerPhase",lastControllerSnapshot.phase());row.addProperty("lastObservedControllerAge",lastControllerSnapshot.age());row.addProperty("lastObservedControllerLanded",lastControllerSnapshot.landed());}
        var clips=new JsonArray();sourceClips.forEach(clips::add);row.add("clips",clips);results.add(row);write(false,null);
    }
    private static JsonObject framePerformance(List<Double> samples) {
        var out=new JsonObject();out.addProperty("source","elapsed System.nanoTime between consecutive actual RenderFrameEvent.Post callbacks; includes native capture overhead");out.addProperty("samples",samples.size());
        var values=new JsonArray();samples.forEach(values::add);out.add("nativeFrameDeltasMs",values);
        if(!samples.isEmpty()){
            var sorted=new ArrayList<>(samples);sorted.sort(Double::compare);double p95=sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*.95)-1));
            out.addProperty("p50FrameMs",sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*.5)-1)));out.addProperty("p95FrameMs",p95);out.addProperty("maximumFrameMs",sorted.getLast());out.addProperty("fpsAtP95FrameMs",1000D/p95);
        }return out;
    }
    private static JsonObject terrainEvidence(BulletdiveTerrain.Snapshot snapshot) {
        var plan=snapshot.plan();var out=new JsonObject();out.addProperty("height",plan.height());out.addProperty("radius",plan.radius());out.addProperty("depth",plan.depth());out.addProperty("budget",plan.budget());out.addProperty("destroyed",snapshot.destroyed());out.addProperty("visited",snapshot.visited());out.addProperty("protectedBlocks",snapshot.protectedBlocks());out.addProperty("complete",snapshot.complete());out.addProperty("maximumRadius",snapshot.maximumRadius());out.addProperty("maximumDepth",snapshot.maximumDepth());out.addProperty("reason",snapshot.reason());return out;
    }
    private static void require(boolean condition,String reason){if(!condition)synchronized(violations){violations.add(reason);}}
    private static void restore(Minecraft mc) {
        sideCaptureName=null;restoreSideCamera(mc);
        ClientWeaponCombat.setFixtureOwnsInput(false);
        savedKeys.forEach(KeyMapping::setDown);mc.options.setCameraType(previousCamera);mc.options.fov().set(previousFov);mc.options.hideGui=previousGui;mc.options.pauseOnLostFocus=previousPause;
        if(mc.player!=null){mc.player.setYRot(previousYaw);mc.player.setXRot(previousPitch);}if(previousMouse)mc.mouseHandler.grabMouse();else mc.mouseHandler.releaseMouse();
        STBImageWrite.stbi_write_png_compression_level.put(0,pngLevel);STBImageWrite.stbi_write_force_png_filter.put(0,pngFilter);
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if(!active())return;disabled=true;ClientWeaponCombat.setFixtureOwnsInput(false);
        restore(Minecraft.getInstance());if(writer!=null)writer.shutdown();write(false,"QA interrupted by disconnect");
    }
    private static void fail(Exception error) {
        if(disabled)return;disabled=true;BlacklightNeoMod.LOGGER.error("Standalone skill QA failed",error);var mc=Minecraft.getInstance();mc.execute(()->{if(initialized)restore(mc);if(writer!=null)writer.shutdown();write(false,error.toString());mc.stop();});
    }
    private static synchronized void write(boolean complete,String error) {
        if(output==null)return;try {
            var report=new JsonObject();report.addProperty("complete",complete);synchronized(violations){report.addProperty("passed",complete&&violations.isEmpty()&&imageError==null);}report.addProperty("source","WeaponInputPayload -> authoritative skill/session -> synchronized attachments/source action -> actual client GPU framebuffer; target damage observed from real LivingDamageEvent.Post");report.addProperty("gameDirectory",QA_DIRECTORY.toString());report.addProperty("save",QA_SAVE);report.addProperty("requestedCases",System.getProperty("blacklight.test.standaloneSkill.cases","all"));report.addProperty("nativeFrames",totalFrames);report.addProperty("maximumEffectVertices",maximumVertices);report.addProperty("submittedImages",submittedImages.get());report.addProperty("writtenImages",writtenImages.get());report.add("requests",requests);report.add("clientTicks",clientTicks);synchronized(serverTicks){report.add("serverTicks",serverTicks.deepCopy());}synchronized(hits){report.add("hits",hits.deepCopy());}report.add("views",views);report.add("cases",results);var errors=new JsonArray();synchronized(violations){violations.forEach(errors::add);}report.add("violations",errors);if(error!=null)report.addProperty("error",error);if(imageError!=null)report.addProperty("imageError",imageError);
            if(criticalMassChecks!=null)report.add("criticalMassChecks",criticalMassChecks.deepCopy());
            if(terrainChecks!=null)report.add("terrainChecks",terrainChecks.deepCopy());
            var authored=new JsonObject();authored.addProperty("available",authoredModelAvailable);authored.addProperty("materialReady",LiquidBiomassMaterial.ready());authored.addProperty("gpuMeshesReady",gpuMeshesReady);report.add("authoredModel",authored);report.addProperty("gpuMeshesReady",gpuMeshesReady);
            report.addProperty("effectVertexBudget",DevastatorEffectRenderer.MAX_VERTICES_PER_FRAME);
            report.addProperty("effectTriangleIndexBudget",DevastatorEffectRenderer.MAX_VERTICES_PER_FRAME);report.addProperty("maximumTriangleIndices",gpuMeshesReady?maximumVertices:0);
            report.add("nativeFramePerformance",framePerformance(nativeFrameDeltasMs));report.add("activeEffectFramePerformance",framePerformance(effectFrameDeltasMs));
            var pending=output.resolve("native-skills.pending.json");Files.writeString(pending,new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.move(pending,output.resolve("native-skills-report.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception errorWriting){BlacklightNeoMod.LOGGER.error("Cannot write standalone skill evidence",errorWriting);}
    }
}
