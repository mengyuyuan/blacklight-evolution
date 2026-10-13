package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.animation.NativePlayerActionBackend;
import com.codex.blacklight.client.renderer.AuthoredDevastatorMesh;
import com.codex.blacklight.client.renderer.DevastatorEffectRenderer;
import com.codex.blacklight.combat.DevastatorEffectTimeline;
import com.codex.blacklight.client.camera.ClientDevastatorPostEffect;
import com.codex.blacklight.client.camera.ClientDevastatorCamera;
import com.codex.blacklight.client.renderer.BulletFlightVisualRenderer;
import com.codex.blacklight.client.renderer.LiquidBiomassMaterial;
import com.codex.blacklight.client.renderer.RigidBiomassMaterial;
import com.codex.blacklight.client.renderer.WhipTetherRenderer;
import com.codex.blacklight.combat.BulletdiveTerrain;
import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.combat.DevastatorCollision;
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
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
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
    private enum Mode { RELEASE, LOW_RESERVE, ZERO_RESERVE, LOCKED, CANCEL, SHORT }
    private record Case(String name,int kind,boolean air,Mode mode,double fallHeight) {
        Case(String name,int kind,boolean air,Mode mode){this(name,kind,air,mode,kind==3?12:air?8:0);}
        CombatSkill skill(){return switch(kind){case 0->CombatSkill.GROUNDSPIKE_GRAVEYARD;case 1->CombatSkill.TENDRIL_BARRAGE;case 2->CombatSkill.CRITICAL_PAIN;default->CombatSkill.BULLETDIVE_DROP;};}
        String move(){return kind==3?"bulletdive_drop":(air?"dev_air_":"dev_")+new String[]{"groundspike","tendril","critical_pain"}[kind];}
        boolean uniquePainTarget(){return name.equals("ground_pain_far")||name.equals("ground_pain_up");}
        boolean distantPain(){return name.equals("ground_pain_far");}
        boolean verticalPain(){return name.equals("ground_pain_up");}
        boolean formal(){return mode==Mode.RELEASE||mode==Mode.LOW_RESERVE;}
        float initialReserve(){return mode==Mode.ZERO_RESERVE?0:mode==Mode.LOW_RESERVE?.25F:1;}
    }
    private enum Stage { SETTLE,CHECKS,PREPARE,READY,START,CHARGE,RELEASE,TAIL,VERIFY,NEXT,DRAIN,DONE }
    private static final List<Case> CASES=cases();
    private static final JsonArray requests=new JsonArray(),serverTicks=new JsonArray(),clientTicks=new JsonArray(),hits=new JsonArray(),views=new JsonArray(),results=new JsonArray();
    private static final Set<String> violations=new LinkedHashSet<>(),captures=new LinkedHashSet<>(),sourceClips=new LinkedHashSet<>();
    private static final Map<KeyMapping,Boolean> savedKeys=new IdentityHashMap<>();
    private record HitLane(int target,String lane,int sourceTrack) { }
    private static final Map<HitLane,Integer> hitCounts=new HashMap<>();
    private record BulletMeasure(double fallHeight,double peakDown,int radius,int depth,long destroyed) { }
    private static final Map<Double,BulletMeasure> bulletMeasures=new HashMap<>();
    private static final Map<Integer,Double> fixtureInitialRanges=new HashMap<>();
    private static final Set<Integer> fixtureTargets=new HashSet<>(),farTargets=new HashSet<>();
    private static final List<Double> nativeFrameDeltasMs=new ArrayList<>(),effectFrameDeltasMs=new ArrayList<>(),caseFrameDeltasMs=new ArrayList<>();
    private static Stage stage=Stage.SETTLE;
    private static volatile boolean serverReady;
    private static volatile ServerSample serverSample;
    private record ServerSample(int caseIndex,long tick,int kind,int phase,long session,long released,
                                float reserve,String clip,double y,boolean ground,boolean gliding,int totalHits,int duplicates,int farTargetHits,
                                float health,float maximum,double sourceSeconds,double fallHeight,double downSpeed) { }
    private static boolean initialized,disabled,completed,observedSession,observedRelease,observedEffect,observedLanding;
    private static int index,stageTicks,totalFrames,reserveDrops,pngLevel,pngFilter,maximumVertices;
    private static volatile int highestControllerHits;
    private static volatile boolean controllerSpent;
    private static volatile DevastatorSystem.Snapshot lastControllerSnapshot;
    private static volatile BulletdiveTerrain.Snapshot lastTerrainSnapshot;
    private record LandingAnchor(int caseIndex,BlockPos position,long placedAt,double actorX,double actorY,double actorZ,int phase) { }
    private static volatile LandingAnchor landingAnchor;
    private record LandingProof(int caseIndex,long tick,Vec3 position,boolean ground) { }
    private static volatile LandingProof firstBulletLanding;
    private static final List<LandingAnchor> landingAnchorPlacements=new ArrayList<>();
    private static long bulletClientLandingAt=Long.MIN_VALUE;
    private static volatile double maximumControllerFallHeight,maximumControllerPeakDown,maximumActualDownSpeed;
    private static double maximumClientFallHeight,maximumClientDownSpeed;
    private static volatile float healthBeforeSpend,maximumBeforeSpend,healthAfterSpend,expectedSpendHealthLoss;
    private static volatile boolean spendHealthObserved;
    private static String lastFormalSourceClip="";
    private static double lastFormalSourceSeconds,lastFormalSourceDuration,lastFormalHeldTime;
    private static boolean observedRetraction,observedBodyRecovery;
    private static long postPassesBefore;
    private static boolean observedPostGpu;
    private static boolean observedFlightGeometry,observedWorldFov,observedContrastFlight;
    private static final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> flightSceneBlocks=new LinkedHashMap<>();
    private static volatile boolean flightSceneCleaned;
    private static long terrainWaitStartedNanos;
    private static double terrainWaitSeconds;
    private static boolean flightScene(){return Boolean.getBoolean(PROPERTY+".flightScene");}
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
    private enum ShotPurpose { RANGE, HERO, POST, LANDED_POST, RETURN, RECOVERY }
    private static String sideCaptureName;
    private static ShotPurpose sidePurpose=ShotPurpose.RANGE;
    private static double sideCameraDistance=7,sideAimHeight=.95;
    private static float sideCameraYaw=67.5F,sideCameraPitch=8;
    private record MeasuredHand(Vec3 point,String source) { }
    private record TissueReturn(int branch,double distance,double reverseAt,double duration,
                                double progress,double extent,boolean synchronizedContact,String handMeasurement) { }

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
        for(double height:new double[]{20,50,52,80,640,2000})cases.add(new Case("bulletdive_"+(int)height,3,true,Mode.RELEASE,height));
        cases.add(new Case("graveyard_low_reserve",0,false,Mode.LOW_RESERVE));
        cases.add(new Case("graveyard_zero_reserve",0,false,Mode.ZERO_RESERVE));
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
        if(active()){event.setYaw(sideCameraActive?sideCameraYaw:-112.5F);event.setPitch(sideCameraActive?sideCameraPitch:-12);event.setRoll(0);}
    }
    @SubscribeEvent public static void sideHand(net.neoforged.neoforge.client.event.RenderHandEvent event){
        if(active()&&sideCameraActive)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void distance(CalculateDetachedCameraDistanceEvent event) {
        if(active())event.setDistance((float)(sideCameraActive?sideCameraDistance:12)/Math.max(.001F,event.getEntityScalingFactor()));
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
            int watchdog=stage==Stage.CHECKS?1200:current()!=null&&current().kind()==3?420+(int)Math.ceil(current().fallHeight()/4):420;
            if(stage==Stage.TAIL&&current()!=null&&current().kind()==3)watchdog=Math.max(1800,watchdog);
            if(stageTicks>watchdog)throw new IllegalStateException("Skill QA timed out: "+stage+" / "+(current()==null?"done":current().name())+" / "+serverSample);
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
                        &&Math.abs(mc.player.getData(ModAttachments.CRITICAL_MASS).reserve()-current().initialReserve())<.001F
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
                    if(current().mode()==Mode.ZERO_RESERVE||current().mode()==Mode.LOCKED){
                        if(stageTicks>=45){send(mc,false,false);advance(Stage.TAIL);}
                    }else if(stageTicks>=100){send(mc,false,false);advance(Stage.RELEASE);}
                }
                case RELEASE -> {
                    var sample=serverSample;
                    if(sample!=null&&sample.caseIndex()==index){
                        if(sample.kind()>=0&&sample.phase()>0)observedRelease=true;
                        if(current().name().equals("ground_graveyard")&&sample.kind()==0&&sample.phase()>0&&!injectionScheduled)injectOrdinaryRequest(mc);
                        if(sample.phase()==3){observedLanding=true;require(sample.ground(),"landing phase was not observed on actual ground "+current().name());}
                        if(sample.kind()<0&&stageTicks>=12)advance(Stage.TAIL);
                    }
                }
                case TAIL -> {
                    if(current().kind()==3&&observedRelease&&(lastTerrainSnapshot==null||!lastTerrainSnapshot.complete())){
                        if(terrainWaitStartedNanos==0)terrainWaitStartedNanos=System.nanoTime();
                        terrainWaitSeconds=(System.nanoTime()-terrainWaitStartedNanos)/1e9;
                        if(terrainWaitSeconds>60)throw new IllegalStateException("Bullet real terrain job exceeded 60 seconds: "+lastTerrainSnapshot);
                        break;
                    }
                    if(terrainWaitStartedNanos>0)terrainWaitSeconds=(System.nanoTime()-terrainWaitStartedNanos)/1e9;
                    if(stageTicks>=35)advance(Stage.VERIFY);
                }
                case VERIFY -> {verify(mc);advance(Stage.NEXT);}
                case NEXT -> {index++;if(index<CASES.size())prepare(mc);else{send(mc,false,true);advance(Stage.DRAIN);writer.shutdown();flightSceneCleaned=false;mc.getSingleplayerServer().execute(()->{clearFlightScene(mc.getSingleplayerServer().overworld());flightSceneCleaned=true;});}}
                case DRAIN -> {if(writer.isTerminated()&&flightSceneCleaned){completed=true;restore(mc);write(true,null);mc.stop();}}
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
        maximumControllerFallHeight=maximumControllerPeakDown=maximumActualDownSpeed=maximumClientFallHeight=maximumClientDownSpeed=0;
        healthBeforeSpend=maximumBeforeSpend=healthAfterSpend=expectedSpendHealthLoss=Float.NaN;spendHealthObserved=false;
        observedRetraction=observedBodyRecovery=false;lastFormalSourceClip="";lastFormalSourceSeconds=lastFormalSourceDuration=0;lastFormalHeldTime=-1;
        postPassesBefore=ClientDevastatorPostEffect.statistics().passes();observedPostGpu=false;bulletClientLandingAt=Long.MIN_VALUE;
        observedFlightGeometry=observedWorldFov=observedContrastFlight=false;
        terrainWaitStartedNanos=0;terrainWaitSeconds=0;
        send(mc,false,true);var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();int caseId=index;Case c=current();
        server.execute(()->{try {
            var level=server.overworld();var p=server.getPlayerList().getPlayer(uuid);DevastatorSystem.cancel(p);WeaponCombatSystem.cancel(p);NativePlayerActionBackend.clear(p);CombatTargeting.requestLock(p,-1);
            // Remove only our preceding contrast columns, never the footprint
            // support or target lane. All edits remain behind the literal QA guard.
            clearFlightScene(level);
            // Remove the preceding case's one protected support before restoring the substrate.
            var previousAnchor=landingAnchor;
            if(previousAnchor!=null&&level.getBlockState(previousAnchor.position()).is(Blocks.BEDROCK))
                level.setBlockAndUpdate(previousAnchor.position(),Blocks.DIRT.defaultBlockState());
            landingAnchor=null;firstBulletLanding=null;synchronized(landingAnchorPlacements){landingAnchorPlacements.clear();}
            synchronized(fixtureTargets){for(int id:fixtureTargets){var old=level.getEntity(id);if(old!=null&&!(old instanceof net.minecraft.world.entity.player.Player))old.discard();}}
            for(var old:level.getEntitiesOfClass(LivingEntity.class,new AABB(-25,240,-25,25,288,25)))if(!(old instanceof net.minecraft.world.entity.player.Player))old.discard();
            for(int x=-22;x<=22;x++)for(int z=-22;z<=22;z++){
                var floor=new BlockPos(x,250,z);if(!level.getBlockState(floor).is(Blocks.GRAY_CONCRETE))level.setBlockAndUpdate(floor,Blocks.GRAY_CONCRETE.defaultBlockState());
                if(!laneReady)for(int y=251;y<=275;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }laneReady=true;
            if(c.kind()==3){
                int extent=BulletdiveTerrain.plan(c.fallHeight()).radius()+2;
                for(int x=-extent;x<=extent;x++)for(int z=-extent;z<=extent;z++){
                    if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)throw new IllegalStateException("Bullet fixture refused an unloaded substrate chunk");
                    int substrateDepth=Math.abs(x)<=4&&Math.abs(z)<=4?BulletdiveTerrain.plan(c.fallHeight()).depth()+2:4;
                    for(int y=251-substrateDepth;y<=250;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState());
                }
                if(flightScene()&&c.fallHeight()==80){
                    for(int x:new int[]{-5,5})for(int z:new int[]{-2,2})for(int y=251;y<=318;y++){
                        var pos=new BlockPos(x,y,z);
                        if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)throw new IllegalStateException("Flight contrast refused an unloaded chunk");
                        if(!level.getBlockState(pos).isAir())throw new IllegalStateException("Flight contrast refused a non-air fixture position "+pos);
                        var block=(y%8<2?Blocks.WHITE_CONCRETE:y%8<4?Blocks.BLUE_CONCRETE:y%8<6?Blocks.RED_CONCRETE:Blocks.STONE).defaultBlockState();
                        level.setBlockAndUpdate(pos,block);flightSceneBlocks.put(pos,block);
                    }
                }
            }
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);state.addEvolutionPoints(10_000);state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);state.buyCombatSkill(CombatSkill.CRITICAL_MASS);
            if(c.mode()!=Mode.LOCKED)state.buyCombatSkill(c.skill());
            p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(c.initialReserve(),true));p.syncData(ModAttachments.CRITICAL_MASS);
            p.setData(ModAttachments.ADRENALINE,AdrenalineState.EMPTY);p.syncData(ModAttachments.ADRENALINE);
            p.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);
            p.setData(ModAttachments.UTILITY_POWERS,0);p.syncData(ModAttachments.UTILITY_POWERS);p.setData(ModAttachments.POWER_MORPH,PowerMorph.NONE);p.syncData(ModAttachments.POWER_MORPH);
            p.getData(ModAttachments.CLAW_COMBAT).reset();p.getData(ModAttachments.MOVEMENT_STATE).reset();p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();
            p.setHealth(p.getMaxHealth());
            p.connection.teleport(.5,251+(c.air()?c.fallHeight():0),.5,0,0);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(!c.air());p.fallDistance=0;
            p.setData(ModAttachments.GLIDE_UNTIL,0L);p.syncData(ModAttachments.GLIDE_UNTIL);
            synchronized(fixtureTargets){fixtureTargets.clear();farTargets.clear();fixtureInitialRanges.clear();hitCounts.clear();}
            int primary=-1;
            if(c.distantPain())for(int x=-1;x<=1;x++)for(int z=99;z<=101;z++)level.setBlockAndUpdate(new BlockPos(x,250,z),Blocks.GRAY_CONCRETE.defaultBlockState());
            for(int target=0;target<(c.uniquePainTarget()?1:12);target++){
                boolean far=c.distantPain()||target>=8;double angle=target>=8?(target-8)*Math.PI/2:target*Math.PI/4;
                double range=c.distantPain()?100:c.verticalPain()?12:far?20:target<2?3:5;
                var mob=new Husk(EntityType.HUSK,level);mob.setNoAi(true);mob.setPersistenceRequired();mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20_000);mob.setHealth(20_000);
                mob.setCustomName(Component.literal((c.uniquePainTarget()?"QA unique "+c.name()+" ":far?"QA 20格范围目标 ":"QA 技能目标 ")+target));
                boolean airDirect=c.kind()==1&&c.air()&&target<2;
                mob.moveTo(c.verticalPain()?.5:.5+Math.sin(angle)*range,c.verticalPain()?263:airDirect?259:251,c.verticalPain()?.5:.5+Math.cos(angle)*range,180,0);
                if(c.verticalPain()||airDirect)mob.setNoGravity(true);
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
        Case c=current();if(c==null)return;
        var visual=p.getData(ModAttachments.DEVASTATOR_VISUAL);var mass=p.getData(ModAttachments.CRITICAL_MASS);var action=p.getData(ModAttachments.PLAYER_ACTION);
        var probe=DevastatorSystem.snapshot(p);
        if(c.kind()==3&&probe!=null&&probe.phase()==DevastatorSystem.FLIGHT)trackLandingSupport(p);
        if(c.kind()==3&&probe!=null&&probe.phase()==DevastatorSystem.LAND&&firstBulletLanding==null)
            firstBulletLanding=new LandingProof(index,p.level().getGameTime(),p.position(),p.onGround());
        if(stage==Stage.PREPARE||stage==Stage.READY)p.setHealth(p.getMaxHealth());
        if(current()!=null&&current().verticalPain()&&visual.kind()==2&&visual.phase()>0&&visual.direction().y>.99)observedVerticalServerDirection=true;
        if(probe!=null){lastControllerSnapshot=probe;highestControllerHits=Math.max(highestControllerHits,probe.hits());controllerSpent|=probe.spent();
            maximumControllerFallHeight=Math.max(maximumControllerFallHeight,probe.fallHeight());
            maximumControllerPeakDown=Math.max(maximumControllerPeakDown,probe.peakDown());
            maximumActualDownSpeed=Math.max(maximumActualDownSpeed,Math.max(0,-p.getDeltaMovement().y));
            if(!probe.spent()){healthBeforeSpend=p.getHealth();maximumBeforeSpend=p.getMaxHealth();}
            else if(!spendHealthObserved){
                healthAfterSpend=p.getHealth();
                expectedSpendHealthLoss=(1-current().initialReserve()+CriticalMassState.EXIT_COST_CHARGES)
                        *maximumBeforeSpend*CriticalMassState.CAPACITY_HEALTH_FRACTION;
                spendHealthObserved=true;
                require(Float.isFinite(healthBeforeSpend)&&Math.abs((healthBeforeSpend-healthAfterSpend)-expectedSpendHealthLoss)<.05F,
                        "formal release HP cost differs from native combined-health subtraction "+current().name());
            }
            if(current()!=null)require(probe.move().equals(current().move()),"server selected wrong ground/air source move: "+probe.move()+" for "+current().name());
        }
        if(current()!=null&&current().kind()==3&&lastControllerSnapshot!=null){
            var terrain=BulletdiveTerrain.snapshot(p,lastControllerSnapshot.session());if(terrain!=null)lastTerrainSnapshot=terrain;
        }
        int total=0,duplicates=0,farHits=0;synchronized(fixtureTargets){for(var entry:hitCounts.entrySet()){int count=entry.getValue();total+=count;duplicates+=Math.max(0,count-1);if(farTargets.contains(entry.getKey().target()))farHits+=count;}}
        boolean gliding=p.getData(ModAttachments.MOVEMENT_STATE).mode()==MovementState.Mode.GLIDE&&p.getData(ModAttachments.GLIDE_UNTIL)>p.level().getGameTime();
        serverSample=new ServerSample(index,p.level().getGameTime(),visual.kind(),visual.phase(),visual.session(),visual.released(),mass.reserve(),action.clipId(),p.getY(),p.onGround(),gliding,total,duplicates,farHits,
                p.getHealth(),p.getMaxHealth(),action.seconds(p.level().getGameTime()),visual.fallHeight(),visual.downSpeed());
        var row=new JsonObject();row.addProperty("case",current()==null?"done":current().name());row.addProperty("tick",p.level().getGameTime());row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("session",visual.session());row.addProperty("released",visual.released());row.addProperty("reserve",mass.reserve());row.addProperty("clip",action.clipId());row.addProperty("y",p.getY());row.addProperty("ground",p.onGround());row.addProperty("totalHits",total);row.addProperty("duplicateHits",duplicates);row.addProperty("farTargetHits",farHits);row.addProperty("directionX",visual.direction().x);row.addProperty("directionY",visual.direction().y);row.addProperty("directionZ",visual.direction().z);if(probe!=null){row.addProperty("controllerMove",probe.move());row.addProperty("controllerSpent",probe.spent());row.addProperty("controllerHits",probe.hits());row.addProperty("controllerLanded",probe.landed());row.addProperty("controllerAge",probe.age());}addVisualClock(row,visual,action,p.level().getGameTime());row.addProperty("health",p.getHealth());row.addProperty("maximumHealth",p.getMaxHealth());addControllerEvidence(row,probe);addLandingAnchor(row);synchronized(serverTicks){serverTicks.add(row);}
    }
    /** A single real block supports the actual collision footprint after glide drift.
     * Placement occurs during FLIGHT above Y251, before a later collision tick.
     * The fixture never sets onGround, position or velocity here. */
    private static void trackLandingSupport(ServerPlayer p){
        if(p.onGround()||p.getY()<=251)return;
        var position=BlockPos.containing(p.getX(),250,p.getZ());
        var old=landingAnchor;
        if(old!=null&&old.caseIndex()==index&&old.position().equals(position))return;
        var level=p.serverLevel();
        if(level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4)==null)
            throw new IllegalStateException("Bullet landing anchor refused an unloaded chunk");
        if(old!=null&&level.getBlockState(old.position()).is(Blocks.BEDROCK)){
            level.setBlockAndUpdate(old.position(),Blocks.DIRT.defaultBlockState());
            require(!level.getBlockState(old.position()).is(Blocks.BEDROCK),"moving Bullet support retained a second protected block");
        }
        level.setBlockAndUpdate(position,Blocks.BEDROCK.defaultBlockState());
        var placed=new LandingAnchor(index,position,p.level().getGameTime(),p.getX(),p.getY(),p.getZ(),DevastatorSystem.FLIGHT);
        landingAnchor=placed;synchronized(landingAnchorPlacements){landingAnchorPlacements.add(placed);}
    }
    private static void addLandingAnchor(JsonObject row){
        var anchor=landingAnchor;if(anchor==null||anchor.caseIndex()!=index)return;
        row.addProperty("actualAnchorX",anchor.position().getX());row.addProperty("actualAnchorY",anchor.position().getY());row.addProperty("actualAnchorZ",anchor.position().getZ());
        row.addProperty("anchorCaseIndex",anchor.caseIndex());row.addProperty("anchorPlacedAtTick",anchor.placedAt());row.addProperty("anchorPlacedDuringPhase",anchor.phase());
        row.addProperty("anchorActorXAtPlacement",anchor.actorX());row.addProperty("anchorActorYAtPlacement",anchor.actorY());row.addProperty("anchorActorZAtPlacement",anchor.actorZ());
        var landing=firstBulletLanding;if(landing!=null&&landing.caseIndex()==index){
            row.addProperty("firstServerLandingTick",landing.tick());row.addProperty("firstServerLandingX",landing.position().x);row.addProperty("firstServerLandingY",landing.position().y);row.addProperty("firstServerLandingZ",landing.position().z);row.addProperty("firstServerLandingGround",landing.ground());
            row.addProperty("anchorExistedBeforeCollision",anchor.placedAt()<landing.tick());
            row.addProperty("anchorMatchesLandingFootprint",anchor.position().equals(BlockPos.containing(landing.position().x,250,landing.position().z)));
        }
    }
    /** Passive healing is suppressed only while the guarded fixture measures the real release cost.
     * The consume/heal helper runs before serverReady and is unaffected. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fixtureHeal(LivingHealEvent event){
        if(active()&&serverReady&&event.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(fixtureOwner)
                &&stage!=Stage.CHECKS)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void hit(LivingDamageEvent.Post event) {
        if(!active()||!serverReady||event.getNewDamage()<=0||!(event.getSource().getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(fixtureOwner))return;
        var context=DevastatorSystem.hitContext();String lane=context==null?"ordinary":context.lane();int sourceTrack=context==null?-1:context.sourceTrack();
        boolean far;double initialRange;synchronized(fixtureTargets){if(!fixtureTargets.contains(event.getEntity().getId()))return;hitCounts.merge(new HitLane(event.getEntity().getId(),lane,sourceTrack),1,Integer::sum);far=farTargets.contains(event.getEntity().getId());initialRange=fixtureInitialRanges.getOrDefault(event.getEntity().getId(),Double.NaN);}
        var probe=DevastatorSystem.snapshot(p);Case c=current();
        if(c!=null&&c.formal()){
            require(context!=null,"formal fixture damage lacked source track context "+c.name());
            require(probe!=null&&probe.move().equals(c.move()),"fixture damage did not originate from the expected live skill "+c.name());
            if(context!=null&&context.lane().equals("target_control")&&probe!=null)
                require(probe.age()+.001>=context.scheduled(),"primary contact occurred before the actual synchronized source plan "+c.name());
            if(context!=null&&context.lane().startsWith("direct_")){
                var move=p.getData(ModAttachments.WEAPON_VISUAL).spec();
                var track=move==null?null:DevastatorCollision.tracks(move).stream().filter(t->t.sourceTrack()==context.sourceTrack()).findFirst().orElse(null);
                require(track!=null,"direct damage referenced an unrelated source CollisionHitTrack "+c.name());
                if(track!=null&&probe!=null)require(probe.age()+.001>=context.scheduled()
                        &&probe.age()<=context.scheduled()+track.end()-track.begin()+.055,
                        "actual direct damage was outside its decoded source clock "+c.name()+" / "+context.sourceTrack());
            }
            if(c.kind()==3||c.kind()==0&&c.air())require(probe!=null&&probe.landed()&&probe.phase()==DevastatorSystem.LAND&&p.onGround(),"landing skill damaged a target before actual onGround landing "+c.name());
            if(c.uniquePainTarget()){
                require(event.getEntity().getId()==primaryTargetId,"unique Pain fixture damaged another target "+c.name());observedUniquePrimaryHit|=event.getEntity().getId()==primaryTargetId;
                if(c.distantPain())require(p.distanceTo(event.getEntity())>=95,"100-block Pain fixture hit a nearby substitute");
                if(c.verticalPain())require(event.getEntity().getY()-p.getY()>=10&&Math.hypot(event.getEntity().getX()-p.getX(),event.getEntity().getZ()-p.getZ())<.25,"vertical Pain fixture did not hit the real overhead actor");
            }
        }
        var row=new JsonObject();row.addProperty("case",c==null?"done":c.name());row.addProperty("tick",p.level().getGameTime());row.addProperty("target",event.getEntity().getId());row.addProperty("farTarget",far);row.addProperty("initialTargetRange",initialRange);row.addProperty("targetDistanceAtHit",p.distanceTo(event.getEntity()));row.addProperty("targetX",event.getEntity().getX());row.addProperty("targetY",event.getEntity().getY());row.addProperty("targetZ",event.getEntity().getZ());row.addProperty("damage",event.getNewDamage());row.addProperty("playerY",p.getY());row.addProperty("ground",p.onGround());if(probe!=null){row.addProperty("sourceMove",probe.move());row.addProperty("session",probe.session());row.addProperty("phase",probe.phase());row.addProperty("landed",probe.landed());row.addProperty("sourceAge",probe.age());}row.addProperty("lane",lane);row.addProperty("sourceTrack",sourceTrack);if(context!=null){row.addProperty("scheduledSourceContact",context.scheduled());row.addProperty("nominalSourceMappedDamage",context.damage());}addCollisionWindow(row,c,sourceTrack);addControllerEvidence(row,probe);addLandingAnchor(row);synchronized(hits){hits.add(row);}
    }
    private static void recordClient(Minecraft mc) {
        Case c=current();
        var visual=mc.player.getData(ModAttachments.DEVASTATOR_VISUAL);var action=mc.player.getData(ModAttachments.PLAYER_ACTION);var mass=mc.player.getData(ModAttachments.CRITICAL_MASS);
        var row=new JsonObject();row.addProperty("case",c==null?"done":c.name());row.addProperty("stage",stage.name());row.addProperty("tick",mc.level.getGameTime());row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("clip",action.clipId());row.addProperty("reserve",mass.reserve());addVisualClock(row,visual,action,mc.level.getGameTime());row.addProperty("health",mc.player.getHealth());row.addProperty("maximumHealth",mc.player.getMaxHealth());addPostEvidence(row);addClientPosition(row,mc);clientTicks.add(row);
        // DRAIN has no Case. Preserve the final diagnostic row without mutating a finished ledger.
        if(c!=null&&stage!=Stage.PREPARE&&stage!=Stage.SETTLE&&stage!=Stage.CHECKS){
            if(Float.isFinite(previousReserve)&&mass.reserve()<previousReserve-.001F)reserveDrops++;previousReserve=mass.reserve();
            if(!action.clipId().isEmpty()&&visual.kind()>=0){sourceClips.add(action.clipId());require(MotionLibrary.find(action.clipId()).isPresent(),"missing live source clip "+action.clipId());}
            maximumClientFallHeight=Math.max(maximumClientFallHeight,visual.fallHeight());maximumClientDownSpeed=Math.max(maximumClientDownSpeed,visual.downSpeed());
            if(visual.kind()==c.kind()&&visual.phase()>0&&action.clipId().startsWith(c.move())){
                lastFormalSourceClip=action.clipId();lastFormalSourceSeconds=action.seconds(mc.level.getGameTime());
                lastFormalSourceDuration=action.duration();lastFormalHeldTime=action.heldTime();
            }
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
            double yaw=Math.toRadians(sideCameraYaw),distance=sideCameraDistance;
            camera.moveTo(mc.player.getX()+Math.sin(yaw)*distance,mc.player.getY()+.2,
                    mc.player.getZ()-Math.cos(yaw)*distance,sideCameraYaw,0);
            // Aim at the hero rather than below the platform at a constant 8 degrees.
            sideAimHeight=sidePurpose==ShotPurpose.RANGE?.95:
                    current().kind()==0&&mc.player.getData(ModAttachments.DEVASTATOR_VISUAL).phase()>0?.55:.95;
            sideCameraPitch=(float)Math.toDegrees(Math.atan2(camera.getEyeY()-(mc.player.getY()+sideAimHeight),distance));
            camera.setXRot(sideCameraPitch);
            camera.yHeadRot=camera.yHeadRotO=camera.yBodyRot=camera.yBodyRotO=sideCameraYaw;
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
            double age=visual.age(mc.player,mc.getTimer().getGameTimeDeltaPartialTick(false));
            boolean effect=stats.effects()>0&&visual.kind()==c.kind();
            boolean returning=effect&&stats.vertices()>0&&(c.kind()==2?painReturn(mc,c,visual,age)!=null:age>=retractionThreshold(c,visual));
            if(effect){
                if(c.kind()==3&&visual.phase()!=DevastatorSystem.FLIGHT)
                    require(com.codex.blacklight.client.renderer.BulletImpactMist.ready(),"Bullet impact mist shader unavailable");
                observedEffect=true;require(LiquidBiomassMaterial.ready(),"active skill lost its actual GPU wet material");
                authoredModelAvailable=AuthoredDevastatorMesh.available();require(authoredModelAvailable,"active skill lost its authored three-dimensional effect model");
                gpuMeshesReady=RigidBiomassMaterial.ready();require(gpuMeshesReady,"active authored effect did not use the actual indexed GPU mesh material");
                if(c.verticalPain()&&visual.direction().y>.99)observedVerticalClientDirection=true;
                if(Double.isFinite(frameMs)&&frameMs>0){effectFrameDeltasMs.add(frameMs);caseFrameDeltasMs.add(frameMs);}
            }
            var post=ClientDevastatorPostEffect.statistics();
            var flight=BulletFlightVisualRenderer.statistics();var worldFov=ClientDevastatorCamera.statistics();
            if(c.kind()==3&&visual.kind()==3&&visual.phase()==DevastatorSystem.FLIGHT){
                observedFlightGeometry|=flight.active()&&flight.airQuads()>0&&flight.vertices()>0;
                observedWorldFov|=worldFov.flying()&&worldFov.outputFov()>worldFov.inputFov()+.001;
                if(flight.active()&&flight.nearEnvironmentDistance()>0&&worldFov.flying()){
                    observedContrastFlight=true;
                    if(flightScene()&&c.fallHeight()==80)capture(mc,c.name()+"_flight_contrast",stats);
                }
            }
            require(!post.failed(),"native screen-space shader failed "+c.name());
            boolean postActive=visual.kind()==c.kind()&&visual.phase()>0&&post.passes()>postPassesBefore&&(post.burst()>0||post.retract()>0||post.flight()>0);
            if(postActive)observedPostGpu=true;
            if(c.kind()==3&&visual.kind()==3&&visual.phase()==DevastatorSystem.LAND&&bulletClientLandingAt==Long.MIN_VALUE)
                bulletClientLandingAt=mc.level.getGameTime();
            // Keep the original _post_close (including flight) and take a distinct impact photograph.
            // Wait a real client tick after the first LAND attachment so the confirmed server root has rendered.
            boolean landedPostEligible=c.kind()==3&&postActive&&post.burst()>0&&visual.phase()==DevastatorSystem.LAND
                    &&mc.level.getGameTime()>bulletClientLandingAt&&Math.abs(mc.player.getY()-251)<.12
                    &&serverSample!=null&&serverSample.caseIndex()==index&&serverSample.phase()==DevastatorSystem.LAND&&serverSample.ground();
            boolean bodyRecovery=c.formal()&&(visual.kind()==c.kind()&&visual.phase()==DevastatorSystem.RECOVERY
                    ||visual.kind()<0&&stage==Stage.TAIL&&stageTicks>=12&&stats.effects()==0);
            if(sideCameraActive){
                try {
                    boolean valid=switch(sidePurpose){
                        case RETURN -> returning;
                        case POST -> postActive;
                        case LANDED_POST -> landedPostEligible;
                        case RECOVERY -> bodyRecovery;
                        case HERO -> visual.kind()==c.kind()||bodyRecovery;
                        case RANGE -> effect;
                    };
                    if(valid){
                        capture(mc,sideCaptureName,stats);
                        if(sidePurpose==ShotPurpose.RETURN)observedRetraction|=captures.contains(c.name()+"_late_retract");
                    }
                }finally{sideCaptureName=null;restoreSideCamera(mc);}
                return;
            }
            // The contact-driven return can be a few milliseconds for near Pain targets.
            // Every frame checks the live strand clock; a missed frame is never relabelled.
            if(c.formal()&&returning)scheduleShot(c.name()+"_late_retract",ShotPurpose.RETURN,8);
            if(c.formal()&&(visual.kind()==c.kind()||bodyRecovery))scheduleShot(c.name()+"_hero_close",ShotPurpose.HERO,5.5);
            if(c.formal()&&postActive)scheduleShot(c.name()+"_post_close",ShotPurpose.POST,5.5);
            if(c.formal()&&landedPostEligible)scheduleShot(c.name()+"_landed_post_close",ShotPurpose.LANDED_POST,5.5);
            if(effect){
                double progress=(age-visual.effectStart())/Math.max(.001,visual.effectEnd()-visual.effectStart());
                if(c.kind()==3&&visual.phase()==3&&age-visual.effectStart()>.06&&age-visual.effectStart()<.3)capture(mc,c.name()+"_impact_clusters",stats);
                if(progress>.28&&progress<.65){capture(mc,c.name()+"_effect",stats);if(c.formal()&&!c.air())scheduleSide(c.name()+"_effect_side");}
            }
            if(c.kind()==3&&visual.kind()==3&&visual.phase()==DevastatorSystem.FLIGHT&&post.flight()>0)
                capture(mc,c.name()+"_flight",stats);
            if(bodyRecovery){
                capture(mc,c.name()+"_body_recovery",stats);observedBodyRecovery|=captures.contains(c.name()+"_body_recovery");
                scheduleShot(c.name()+"_body_recovery_close",ShotPurpose.RECOVERY,5.5);
            }
            String suffix=visual.kind()==c.kind()?visual.phase()==0?"charge":visual.phase()==3?"land":"release":stage==Stage.TAIL?"cleared":null;
            if(suffix!=null&&((suffix.equals("release")&&stats.effects()>0)||!suffix.equals("release")))capture(mc,c.name()+"_"+suffix,stats);
            if(c.formal()&&(c.kind()==3||c.kind()==0&&c.air())&&visual.kind()==c.kind()&&visual.phase()==3)scheduleSide(c.name()+"_land_side");
        }catch(Exception error){sideCaptureName=null;restoreSideCamera(mc);fail(error);}
    }
    private static void scheduleSide(String name) {scheduleShot(name,ShotPurpose.RANGE,sideDistance());}
    private static void scheduleShot(String name,ShotPurpose purpose,double distance) {
        if(sideCaptureName==null&&!captures.contains(name)&&writer.getQueue().remainingCapacity()>0){
            sideCaptureName=name;sidePurpose=purpose;sideCameraDistance=distance;sideCameraYaw=67.5F;
        }
    }
    private static int sideDistance(){return current()!=null&&current().kind()==0?38:current()!=null&&current().kind()==1?30:
            current()!=null&&current().kind()==3?Math.min(80,Math.max(12,BulletdiveTerrain.plan(current().fallHeight()).radius()+8)):7;}
    private static double retractionThreshold(Case c,com.codex.blacklight.combat.DevastatorVisual visual){
        if(c.kind()==1)return visual.effectStart()-.4+DevastatorEffectTimeline.TENDRIL_RETRACT_START+.18;
        return visual.effectEnd()-.14;
    }
    /** Reads the actual effect renderer's already-captured Tool bones without changing
     * the production renderer. Missing bones use that renderer's identical fallback.
     * This observation stays in the disposable QA source set. */
    private static final class EffectHandsObservation {
        private static final java.lang.reflect.Field FIELD=field();
        private static java.lang.reflect.Field field(){
            try{var field=DevastatorEffectRenderer.class.getDeclaredField("HANDS");field.setAccessible(true);return field;}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot observe native effect hand bones",error);}
        }
        @SuppressWarnings("unchecked")
        private static Vec3 read(int entity,boolean left){
            try{var map=(Map<Integer,Vec3[]>)FIELD.get(null);var hands=map.get(entity);return hands==null?null:hands[left?1:0];}
            catch(IllegalAccessException error){throw new IllegalStateException("Cannot read native effect hand bones",error);}
        }
    }
    private static MeasuredHand effectHand(Minecraft mc,float partial,boolean left){
        var point=EffectHandsObservation.read(mc.player.getId(),left);
        if(point!=null)return new MeasuredHand(point,"actual captured Tool_"+(left?"L":"R")+" / same rendered frame");
        if(!left){var grip=WhipTetherRenderer.capturedGrip(mc.player.getId());if(grip!=null)return new MeasuredHand(grip,"effect renderer's captured-grip fallback");}
        double yaw=Math.toRadians(mc.player.yBodyRot);Vec3 side=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
        return new MeasuredHand(mc.player.getPosition(partial).add(side.scale(left?.38:-.38)).add(0,mc.player.getBbHeight()*.58,0),"effect renderer's body-position fallback");
    }
    /** Mirrors the actual visible Pain strands' clocks using their captured bones,
     * synchronized first contact and current target point, including knockback. */
    private static TissueReturn painReturn(Minecraft mc,Case c,com.codex.blacklight.combat.DevastatorVisual visual,double age){
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 direction=visual.direction().normalize(),eye=mc.player.getEyePosition(partial);
        Vec3 across=direction.cross(new Vec3(0,1,0)).normalize();if(across.lengthSqr()<.1)across=new Vec3(1,0,0);
        double distance=visual.radius();var target=mc.level.getEntity(visual.targetId());
        if(target instanceof LivingEntity living){
            var delta=living.getBoundingBox().getCenter().subtract(eye);double along=delta.dot(direction);
            if(along>0&&delta.subtract(direction.scale(along)).length()<1.25)distance=Math.min(distance,along);
        }
        var first=visual.contacts().isEmpty()?null:visual.contacts().getFirst();
        var primary=first==null?null:mc.level.getEntity(first.targetId());
        for(int branch=0;branch<10;branch++){
            var hand=effectHand(mc,partial,(branch&1)!=0);
            Vec3 root=hand.point().add(across.scale((branch/2-2)*.10));
            boolean planned=branch==0&&primary instanceof LivingEntity&&Float.isFinite(first.at());
            if(branch==0&&!planned)continue;
            Vec3 destination=planned?(CombatTargeting.multipart((LivingEntity)primary)?CombatTargeting.closestPoint(root,(LivingEntity)primary):primary.getBoundingBox().getCenter()):eye.add(direction.scale(distance));
            double length=root.distanceTo(destination),reverse=200*DevastatorEffectTimeline.WORLD_SCALE;
            if(!Double.isFinite(length)||length<=0)continue;
            double forward=Math.max((branch==0?35:30)*DevastatorEffectTimeline.WORLD_SCALE,length/(c.air()?1.666667:1.333333));
            double reverseAt=planned?first.at()+DevastatorEffectTimeline.PAIN_CONTACT_DWELL:
                    visual.effectStart()+(branch%3)*.666667+length/forward+DevastatorEffectTimeline.PAIN_CONTACT_DWELL;
            double duration=length/reverse,progress=(age-reverseAt)/duration;
            double extent=1-progress;
            if(progress>=.12&&progress<.9&&extent>=.01)
                return new TissueReturn(branch,length,reverseAt,duration,progress,extent,planned,hand.source());
        }return null;
    }
    private static void capture(Minecraft mc,String name,DevastatorEffectRenderer.Statistics stats) {
        if(captures.contains(name)||writer.getQueue().remainingCapacity()==0)return;
        var image=Screenshot.takeScreenshot(mc.getMainRenderTarget());captures.add(name);submittedImages.incrementAndGet();
        var visual=mc.player.getData(ModAttachments.DEVASTATOR_VISUAL);var row=new JsonObject();row.addProperty("file",name+".png");row.addProperty("tick",mc.level.getGameTime());row.addProperty("camera",mc.options.getCameraType().name());row.addProperty("fixtureSideCamera",sideCameraActive);row.addProperty("fixtureCameraDistance",sideCameraActive?sideCameraDistance:12);row.addProperty("fixtureCameraYaw",sideCameraActive?sideCameraYaw:-112.5F);row.addProperty("fixtureCameraPitch",sideCameraActive?sideCameraPitch:-12);row.addProperty("fixtureAimHeight",sideCameraActive?sideAimHeight:.95);row.addProperty("fixtureShotPurpose",sideCameraActive?sidePurpose.name():"MAIN");row.addProperty("nativeRenderFrame",stats.frame());row.addProperty("authoredModelAvailable",authoredModelAvailable);row.addProperty("gpuMeshesReady",gpuMeshesReady);row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("sourceClip",mc.player.getData(ModAttachments.PLAYER_ACTION).clipId());row.addProperty("effects",stats.effects());row.addProperty("branches",stats.branches());row.addProperty("vertices",stats.vertices());row.addProperty("triangleIndices",gpuMeshesReady?stats.vertices():0);addVisualClock(row,visual,mc.player.getData(ModAttachments.PLAYER_ACTION),mc.level.getGameTime()+mc.getTimer().getGameTimeDeltaPartialTick(false));row.addProperty("lastFormalSourceClip",lastFormalSourceClip);row.addProperty("lastFormalSourceSeconds",lastFormalSourceSeconds);row.addProperty("lastFormalSourceDuration",lastFormalSourceDuration);row.addProperty("lastFormalHeldTime",lastFormalHeldTime);addControllerEvidence(row,lastControllerSnapshot);addPostEvidence(row);addLandingAnchor(row);
        addClientPosition(row,mc);
        var rendered=mc.player.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false));var camera=mc.gameRenderer.getMainCamera().getPosition();
        row.addProperty("actorX",mc.player.getX());row.addProperty("actorY",mc.player.getY());row.addProperty("actorZ",mc.player.getZ());row.addProperty("clientOnGround",mc.player.onGround());
        row.addProperty("interpolatedActorX",rendered.x);row.addProperty("interpolatedActorY",rendered.y);row.addProperty("interpolatedActorZ",rendered.z);
        row.addProperty("actualCameraX",camera.x);row.addProperty("actualCameraY",camera.y);row.addProperty("actualCameraZ",camera.z);
        row.addProperty("firstClientLandingTick",bulletClientLandingAt);
        var chest=DevastatorEffectRenderer.capturedChest(mc.player.getId());if(chest!=null)row.addProperty("capturedChestY",chest.y);
        var server=serverSample;if(server!=null&&server.caseIndex()==index){row.addProperty("photographServerY",server.y());row.addProperty("photographServerGround",server.ground());row.addProperty("photographServerPhase",server.phase());}
        Case c=current();if(c!=null&&c.kind()==2){
            var returning=painReturn(mc,c,visual,visual.age(mc.player,mc.getTimer().getGameTimeDeltaPartialTick(false)));
            if(returning!=null){var tissue=new JsonObject();tissue.addProperty("branch",returning.branch());tissue.addProperty("currentRootToTargetDistance",returning.distance());tissue.addProperty("reverseAtSourceSeconds",returning.reverseAt());tissue.addProperty("returnDurationSeconds",returning.duration());tissue.addProperty("returnProgress",returning.progress());tissue.addProperty("remainingExtent",returning.extent());tissue.addProperty("synchronizedPrimaryContact",returning.synchronizedContact());tissue.addProperty("handMeasurement",returning.handMeasurement());row.add("painReturnEvidence",tissue);}
        }views.add(row);
        writer.execute(()->{try(image){image.writeToFile(output.resolve(name+".png"));writtenImages.incrementAndGet();}catch(Exception error){imageError=error.toString();}});
    }
    private static void verify(Minecraft mc) {
        Case c=current();var sample=serverSample;require(sample!=null&&sample.caseIndex()==index,"no authoritative observation for "+c.name());
        if(c.formal()){
            require(observedSession,"real network input did not start "+c.name());require(observedRelease,"no authoritative release for "+c.name());require(!sourceClips.isEmpty(),"no source action clip for "+c.name());require(observedEffect,"no actual GPU effect for "+c.name());require(captures.contains(c.name()+"_effect"),"no actual developed effect framebuffer for "+c.name());require(sample.totalHits()>0,"no real target damage for "+c.name());require(sample.duplicates()==0,"target damaged repeatedly by one skill "+c.name());
            require(highestControllerHits==sample.totalHits(),"actual damage events did not match source-lane controller ledger for "+c.name());
            require(observedRetraction&&captures.contains(c.name()+"_late_retract"),"missing actual late-return tissue framebuffer "+c.name());
            require(observedBodyRecovery&&captures.contains(c.name()+"_body_recovery"),"missing actual cleared-body recovery framebuffer "+c.name());
            require(!lastFormalSourceClip.isEmpty()&&lastFormalSourceSeconds>0,"body recovery lacks preceding native source clock evidence "+c.name());
            require(observedPostGpu,"formal skill had no actual GPU post-chain pass "+c.name());
            require(captures.contains(c.name()+"_hero_close"),"missing real 5.5-block hero pose framebuffer "+c.name());
            require(captures.contains(c.name()+"_post_close"),"missing near-hero actual GPU post-chain framebuffer "+c.name());
            require(captures.contains(c.name()+"_body_recovery_close"),"missing near-hero cleared body framebuffer "+c.name());
            if(c.kind()==0||c.kind()==1)require(sample.farTargetHits()>0,"expanded devastator range did not hit any real 20-block target "+c.name());
            if(c.uniquePainTarget()){
                require(primaryTrackingAck!=null,"unique Pain target lacked actual client/server tracking acknowledgment "+c.name());
                require(observedUniquePrimaryHit&&laneHits(primaryTargetId,"target_control")==1,"unique Pain target did not receive exactly one primary source-control contact "+c.name());
                if(c.distantPain())require(sample.farTargetHits()==1&&sample.totalHits()==1,"100-block target received an impossible short direct-capsule contact or missed its primary");
                if(c.verticalPain()){
                    require(observedVerticalServerDirection&&observedVerticalClientDirection,"near-vertical Pain direction was not observed on server and native client");
                    require(laneHits(primaryTargetId,"direct_4456")==1,"12-block overhead target lacked its one reachable Health-mask capsule contact");
                }
            }
            if(c.kind()<3&&!c.distantPain()){
                var move=Arrays.stream(com.codex.blacklight.combat.WeaponMove.values()).filter(m->m.id.equals(c.move())).findFirst().orElseThrow();
                for(var track:DevastatorCollision.tracks(move))require(laneHits(-1,"direct_"+track.sourceTrack())>0,
                        "prepared near actors did not exercise source direct track "+track.sourceTrack()+" / "+c.name());
            }
            if(!c.air())require(captures.contains(c.name()+"_effect_side"),"missing one-frame side view of authored release "+c.name());
            if(c.kind()!=3){require(controllerSpent,"release lacked authoritative reserve commit "+c.name());require(reserveDrops==1,"critical reserve did not drop exactly once for "+c.name());require(sample.reserve()==0,"released devastator retained reserve "+c.name());
                require(spendHealthObserved&&Math.abs((healthBeforeSpend-healthAfterSpend)-expectedSpendHealthLoss)<.05F,
                        "real formal spend HP subtraction was not observed "+c.name());
                if(c.mode()==Mode.LOW_RESERVE)require(expectedSpendHealthLoss>0&&healthAfterSpend<healthBeforeSpend,"quarter reserve did not pay the missing cost through real HP");
            }
            else require(reserveDrops==0&&!controllerSpent,"Bulletdive incorrectly spent Critical Mass reserve");
            if(c.kind()==3||c.kind()==0&&c.air())require(observedLanding&&lastControllerSnapshot!=null&&lastControllerSnapshot.landed(),"landing skill never reached actual landing phase "+c.name());
            if(c.kind()==3||c.kind()==0&&c.air())require(captures.contains(c.name()+"_land_side"),"missing one-frame side view of actual landing "+c.name());
            if(c.kind()==3){
                require(lastTerrainSnapshot!=null&&lastTerrainSnapshot.complete(),"actual Bulletdive landing did not produce a completed terrain job");
                var anchor=landingAnchor;
                require(anchor!=null&&anchor.caseIndex()==index&&anchor.phase()==DevastatorSystem.FLIGHT&&anchor.actorY()>251,
                        "Bullet support was not placed under the real footprint before ground collision "+c.name());
                var landing=firstBulletLanding;
                require(landing!=null&&landing.caseIndex()==index&&landing.ground(),"no real grounded first Bullet impact "+c.name());
                if(anchor!=null&&landing!=null)require(anchor.placedAt()<landing.tick()
                        &&anchor.position().equals(BlockPos.containing(landing.position().x,250,landing.position().z)),
                        "Bullet impact did not retain the one pre-collision footprint support "+c.name());
                require(captures.contains(c.name()+"_landed_post_close"),"missing distinct near-hero actual grounded impact post-chain framebuffer "+c.name());
                require(maximumControllerFallHeight>c.fallHeight()*.80,"actual fall was far shorter than the isolated takeoff height "+c.name());
                require(maximumClientFallHeight>c.fallHeight()*.75,"large fall height was not synchronized to actual rendering client "+c.name());
                require(captures.contains(c.name()+"_flight"),"missing actual screen-space flight framebuffer "+c.name());
                require(observedFlightGeometry,"no real world-space flight geometry "+c.name());
                require(observedWorldFov,"no measured world-only FOV change "+c.name());
                if(flightScene()&&c.fallHeight()==80)require(observedContrastFlight&&captures.contains(c.name()+"_flight_contrast"),"flight contrast scene remained sky-only");
                if(c.fallHeight()>=640){
                    require(maximumControllerFallHeight>512&&maximumControllerPeakDown>5&&maximumActualDownSpeed>5,"high drop retained the old height/speed cap "+c.name());
                    require(maximumClientDownSpeed>5,"high down-speed was not synchronized to client "+c.name());
                }
                var measure=new BulletMeasure(maximumControllerFallHeight,maximumControllerPeakDown,
                        lastTerrainSnapshot==null?0:lastTerrainSnapshot.plan().radius(),lastTerrainSnapshot==null?0:lastTerrainSnapshot.plan().depth(),
                        lastTerrainSnapshot==null?0:lastTerrainSnapshot.destroyed());
                double preceding=c.fallHeight()==2000?640:c.fallHeight()==640?80:c.fallHeight()==80?12:0;
                var prior=bulletMeasures.get(preceding);
                if(prior!=null){
                    require(measure.fallHeight()>prior.fallHeight()&&measure.peakDown()>prior.peakDown(),"higher actual fall did not continue increasing controller height/velocity "+c.name());
                    require(measure.radius()>prior.radius()&&measure.depth()>=prior.depth(),"higher actual fall did not continue increasing terrain plan "+c.name());
                    require(measure.destroyed()>prior.destroyed(),"higher fall failed to destroy more real substrate blocks "+c.name());
                }
                if(c.fallHeight()==2000)require(measure.destroyed()>4096,"2000-block real fall retained the old 4096-block destruction cap");
                bulletMeasures.put(c.fallHeight(),measure);
            }
            if(c.name().equals("ground_graveyard"))require(ordinaryInjection!=null&&ordinaryInjection.get("preserved").getAsBoolean(),"formal release was not tested against the ordinary public RPC");
        }else if(c.mode()==Mode.CANCEL){require(observedSession,"cancel case never charged");require(!observedRelease,"cancelled charge released damage");require(sample.totalHits()==0,"cancelled charge damaged a target");require(reserveDrops==0&&sample.reserve()==1,"cancelled charge consumed reserve");}
        else if(c.mode()==Mode.SHORT){require(observedSession,"short-charge case never entered formal charge");require(!observedRelease&&!controllerSpent,"short keyup released a formal devastator");require(reserveDrops==0&&sample.reserve()==1,"short keyup consumed reserve");require(shortReleaseAgeTicks>=0&&shortReleaseAgeTicks<=2,"short-charge source age was not verified");}
        else {require(!observedSession,"denied case started a devastator "+c.name());require(reserveDrops==0,"denied case consumed critical reserve "+c.name());
            if(c.mode()==Mode.ZERO_RESERVE)require(sample.reserve()==0&&!controllerSpent,"zero-reserve denial spent a resource or started a release");
        }
        require(sample.kind()<0&&mc.player.getData(ModAttachments.DEVASTATOR_VISUAL).kind()<0,"finished/cancelled skill retained synchronized state "+c.name());require(DevastatorEffectRenderer.statistics().effects()==0,"finished/cancelled effect remained visible "+c.name());
        var post=ClientDevastatorPostEffect.statistics();require(post.burst()==0&&post.retract()==0&&post.flight()==0&&!post.failed(),"finished/cancelled scene post-effect did not clear "+c.name());
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
        row.addProperty("mode",c.mode().name());row.addProperty("initialReserve",c.initialReserve());row.addProperty("requestedFallHeight",c.fallHeight());
        if(c.kind()==3){
            row.addProperty("landingAnchorBlock","isolated platform Y250 / one BEDROCK block follows real X/Z during FLIGHT only; actual onGround required");
            row.addProperty("substrate","center |x|,|z| <= 4: plan.depth+2 real DIRT layers; outer 4 layers / radius plan+2 / already loaded chunks");addLandingAnchor(row);
            var placements=new JsonArray();synchronized(landingAnchorPlacements){for(var anchor:landingAnchorPlacements){var placed=new JsonObject();placed.addProperty("caseIndex",anchor.caseIndex());placed.addProperty("x",anchor.position().getX());placed.addProperty("y",anchor.position().getY());placed.addProperty("z",anchor.position().getZ());placed.addProperty("placedAtTick",anchor.placedAt());placed.addProperty("phase",anchor.phase());placed.addProperty("actorX",anchor.actorX());placed.addProperty("actorY",anchor.actorY());placed.addProperty("actorZ",anchor.actorZ());placements.add(placed);}}row.add("landingAnchorPlacements",placements);
        }
        row.addProperty("observedLateRetraction",observedRetraction);row.addProperty("observedBodyRecovery",observedBodyRecovery);
        row.addProperty("observedPostGpu",observedPostGpu);row.addProperty("postGpuPasses",post.passes()-postPassesBefore);
        row.addProperty("observedFlightGeometry",observedFlightGeometry);row.addProperty("observedWorldFov",observedWorldFov);row.addProperty("observedContrastFlight",observedContrastFlight);
        row.addProperty("flightSceneEnabled",flightScene());
        row.addProperty("terrainWaitSeconds",terrainWaitSeconds);row.addProperty("terrainCompleteBeforeNextCase",lastTerrainSnapshot!=null&&lastTerrainSnapshot.complete());
        row.addProperty("postCleared",post.burst()==0&&post.retract()==0&&post.flight()==0);row.addProperty("duplicateLedger","target + lane + sourceTrack");
        row.addProperty("maximumControllerFallHeight",maximumControllerFallHeight);row.addProperty("maximumControllerPeakDown",maximumControllerPeakDown);
        row.addProperty("maximumActualDownSpeed",maximumActualDownSpeed);row.addProperty("maximumClientFallHeight",maximumClientFallHeight);row.addProperty("maximumClientDownSpeed",maximumClientDownSpeed);
        if(spendHealthObserved){row.addProperty("healthBeforeSpend",healthBeforeSpend);row.addProperty("maximumHealthBeforeSpend",maximumBeforeSpend);
            row.addProperty("healthAfterSpend",healthAfterSpend);row.addProperty("observedSpendHealthLoss",healthBeforeSpend-healthAfterSpend);row.addProperty("expectedSpendHealthLoss",expectedSpendHealthLoss);}
        row.addProperty("lastFormalSourceClip",lastFormalSourceClip);row.addProperty("lastFormalSourceSeconds",lastFormalSourceSeconds);row.addProperty("lastFormalSourceDuration",lastFormalSourceDuration);row.addProperty("lastFormalHeldTime",lastFormalHeldTime);
        addControllerEvidence(row,lastControllerSnapshot);
        var lanes=new JsonArray();synchronized(fixtureTargets){hitCounts.forEach((key,count)->{var lane=new JsonObject();lane.addProperty("target",key.target());lane.addProperty("lane",key.lane());lane.addProperty("sourceTrack",key.sourceTrack());lane.addProperty("hits",count);lanes.add(lane);});}row.add("sourceLaneLedger",lanes);
        var clips=new JsonArray();sourceClips.forEach(clips::add);row.add("clips",clips);results.add(row);write(false,null);
    }
    private static void addClientPosition(JsonObject row,Minecraft mc){
        var flight=BulletFlightVisualRenderer.statistics();var fov=ClientDevastatorCamera.statistics();
        var presentation=new JsonObject();presentation.addProperty("actualRenderFrame",flight.frame());presentation.addProperty("active",flight.active());
        presentation.addProperty("airQuads",flight.airQuads());presentation.addProperty("tissueQuads",flight.tissueQuads());presentation.addProperty("shockQuads",flight.shockQuads());presentation.addProperty("vertices",flight.vertices());
        presentation.addProperty("nearEnvironmentDistance",flight.nearEnvironmentDistance());presentation.addProperty("worldFovBefore",fov.inputFov());presentation.addProperty("worldFovAfter",fov.outputFov());presentation.addProperty("worldFovFlying",fov.flying());
        row.add("flightPresentation",presentation);
        row.addProperty("actualX",mc.player.getX());row.addProperty("actualY",mc.player.getY());row.addProperty("actualZ",mc.player.getZ());
        row.addProperty("prevRenderY",mc.player.yo);row.addProperty("playerYo",mc.player.yo);row.addProperty("previousTickY",mc.player.yOld);row.addProperty("ground",mc.player.onGround());
        row.addProperty("actualInterpolatedPositionY",mc.player.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false)).y);
    }
    private static void clearFlightScene(net.minecraft.server.level.ServerLevel level){
        for(var entry:flightSceneBlocks.entrySet())if(level.getBlockState(entry.getKey()).equals(entry.getValue()))level.setBlockAndUpdate(entry.getKey(),Blocks.AIR.defaultBlockState());
        flightSceneBlocks.clear();
    }
    private static void addVisualClock(JsonObject row,com.codex.blacklight.combat.DevastatorVisual visual,
                                       com.codex.blacklight.animation.NativeActionState action,double worldTicks){
        row.addProperty("visualSession",visual.session());row.addProperty("visualReleased",visual.released());
        row.addProperty("visualFallHeight",visual.fallHeight());row.addProperty("visualDownSpeed",visual.downSpeed());
        row.addProperty("visualAge",visual.kind()<0?0:Math.max(0,(worldTicks-(visual.released()>=0?visual.released():visual.session()))/20D));
        row.addProperty("effectStart",visual.effectStart());row.addProperty("effectEnd",visual.effectEnd());
        row.addProperty("sourceStarted",action.started());row.addProperty("sourceSeconds",action.seconds(worldTicks));
        row.addProperty("sourceOffset",action.clipOffset());row.addProperty("sourceDuration",action.duration());
        row.addProperty("sourceHeldTime",action.heldTime());row.addProperty("sourceActive",action.active(worldTicks));row.addProperty("sourceRevision",action.revision());
        var contacts=new JsonArray();for(var planned:visual.contacts()){var contact=new JsonObject();contact.addProperty("target",planned.targetId());contact.addProperty("scheduledSourceContact",planned.at());contacts.add(contact);}row.add("visualContacts",contacts);
    }
    private static void addControllerEvidence(JsonObject row,DevastatorSystem.Snapshot probe){
        if(probe==null)return;row.addProperty("controllerFallHeight",probe.fallHeight());row.addProperty("controllerPeakDown",probe.peakDown());
        row.addProperty("controllerSession",probe.session());row.addProperty("controllerPhase",probe.phase());row.addProperty("controllerAge",probe.age());
    }
    private static void addPostEvidence(JsonObject row){
        var post=ClientDevastatorPostEffect.statistics();row.addProperty("postGpuPasses",post.passes());
        row.addProperty("postBurst",post.burst());row.addProperty("postRetract",post.retract());row.addProperty("postFlight",post.flight());row.addProperty("postFailed",post.failed());
    }
    private static int laneHits(int target,String lane){
        synchronized(fixtureTargets){return hitCounts.entrySet().stream().filter(e->(target<0||e.getKey().target()==target)&&e.getKey().lane().equals(lane)).mapToInt(Map.Entry::getValue).sum();}
    }
    private static void addCollisionWindow(JsonObject row,Case c,int sourceTrack){
        if(c==null||sourceTrack<1)return;
        var move=Arrays.stream(com.codex.blacklight.combat.WeaponMove.values()).filter(m->m.id.equals(c.move())).findFirst().orElse(null);
        if(move==null)return;
        for(var track:DevastatorCollision.tracks(move))if(track.sourceTrack()==sourceTrack){
            row.addProperty("sourceTrackBegin",track.begin());row.addProperty("sourceTrackEnd",track.end());
            row.addProperty("sourceTrackRadius",track.radius());row.addProperty("sourceTrackDamage",track.damage());break;
        }
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
        var plan=snapshot.plan();var out=new JsonObject();out.addProperty("height",plan.height());out.addProperty("radius",plan.radius());out.addProperty("depth",plan.depth());out.addProperty("budget",plan.budget());out.addProperty("destroyed",snapshot.destroyed());out.addProperty("visited",snapshot.visited());out.addProperty("protectedBlocks",snapshot.protectedBlocks());out.addProperty("complete",snapshot.complete());out.addProperty("maximumRadius",snapshot.maximumRadius());out.addProperty("maximumDepth",snapshot.maximumDepth());out.addProperty("reason",snapshot.reason());out.addProperty("elapsedTicks",snapshot.elapsedTicks());out.addProperty("firstTickDestroyed",snapshot.firstTickDestroyed());out.addProperty("peakBreaksPerTick",snapshot.peakBreaksPerTick());out.addProperty("peakSliceMillis",snapshot.peakSliceMillis());var fx=com.codex.blacklight.combat.DevastatorEffectTimeline.impact(plan.height());out.addProperty("mistEnabled",fx.mist());out.addProperty("clusterRadius",fx.clusterRadius());return out;
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
