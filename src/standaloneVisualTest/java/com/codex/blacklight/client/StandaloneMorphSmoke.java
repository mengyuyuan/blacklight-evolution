package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.NativePlayerActionBackend;
import com.codex.blacklight.client.animation.WeaponMorphTiming;
import com.codex.blacklight.client.renderer.BlacklightPlayerRenderer;
import com.codex.blacklight.client.renderer.LiquidBiomassMaterial;
import com.codex.blacklight.client.renderer.StandalonePlayerRendering;
import com.codex.blacklight.client.renderer.WeaponMorphTracker;
import com.codex.blacklight.combat.WeaponVisual;
import com.codex.blacklight.network.PowerSelectPayload;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.PowerMorph;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import com.codex.blacklight.forge.PacketDistributor;
import org.lwjgl.stb.STBImageWrite;

/**
 * Opt-in real packet/clock/render QA. A literal isolated directory and copied
 * save are required before any fixture, options or input can be changed.
 * No production interpolation clock, morph progress or pose is ever injected.
 */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class StandaloneMorphSmoke {
    private static final String PROPERTY="blacklight.test.standaloneMorph";
    private static final Path QA_DIRECTORY=Path.of("D:/Blacklight-Standalone/run-native-qa").toAbsolutePath().normalize();
    private static final String QA_SAVE="Blacklight Standalone QA v4";
    private static final float[] MILESTONES={0,.25F,.5F,.75F,1};
    private enum Kind { SHORT,LONG,RAPID }
    private record Case(String name,PowerForm form,CameraType camera,Kind kind) { }
    private enum Stage { SETTLE,RESET,GROW,HOLD,RETRACT,TAIL,RAPID_CLAWS,RAPID_BLADE,RAPID_HAMMER,RAPID_CLEAR,NEXT,DRAIN,DONE }
    private static final List<Case> CASES=cases();
    private static Stage stage=Stage.SETTLE;
    private static boolean initialized,disabled,completed,observedGrow,observedRetract,longDelayedFrame;
    private static boolean liquidMaterialChecked,liquidMaterialReadyAtSettle;
    private static volatile boolean serverReady;
    private record ServerGate(long gameTime,int playerTick,PowerForm form,boolean alive,boolean infected,boolean prototype,boolean consuming,String action,boolean cooldownReady,int unlockedMask) {
        boolean ready(PowerForm desired){return alive&&infected&&prototype&&!consuming&&(desired==PowerForm.NONE||action.equals("IDLE")&&cooldownReady&&(unlockedMask&desired.bit())!=0);}
    }
    private static volatile ServerGate serverGate;
    private static Path output;
    private static int index,stageTicks,growthMask,retractMask,rightNativeArms,leftNativeArms,thirdPersonPasses;
    private static int longStableHoldTicks;
    private static double previousClock=Double.NEGATIVE_INFINITY,sequenceStart=Double.NaN;
    private static double previousMaterialClock=Double.NEGATIVE_INFINITY,maximumMaterialRawClampTicks;
    private static int nextSequenceSlot,sequenceFrameCount,sequenceDropped;
    private static int validatedFrames;
    private static PowerForm previousTarget=PowerForm.NONE;
    private static PowerForm previousFrom=PowerForm.NONE;
    private static float previousPhase=1;
    private static boolean previousTransitioning,previousLongTransform;
    private static long previousLongStart=Long.MIN_VALUE;
    private static final Map<PowerForm,Float> previousGrowth=new java.util.EnumMap<>(PowerForm.class);
    private static float maximumRetargetStep;
    private static String sequenceDirectory;
    private static boolean sequenceDone;
    private static final JsonArray views=new JsonArray(),frames=new JsonArray(),frameTrace=new JsonArray(),clockDiscontinuities=new JsonArray(),requests=new JsonArray(),serverTicks=new JsonArray(),caseResults=new JsonArray();
    private static final JsonArray imageTimings=new JsonArray(),imageQueueRejections=new JsonArray(),sequenceGaps=new JsonArray();
    private static final JsonArray materialClockClamps=new JsonArray(),visualClockFailures=new JsonArray();
    private static final LinkedHashSet<String> violations=new LinkedHashSet<>();
    private static final Map<KeyMapping,Boolean> savedKeys=new IdentityHashMap<>();
    private static CameraType previousCamera;
    private static boolean previousGui,previousPause,previousMouse;
    private static int previousFov;
    private static int previousPngCompression;
    private static int previousPngFilter;
    private static boolean compressionConfigured;
    private static float previousYaw,previousPitch;
    private static final AtomicInteger submittedImages=new AtomicInteger(),writtenImages=new AtomicInteger();
    private static volatile String imageFailure;
    private static ThreadPoolExecutor imageWriter;
    private static final ThreadMXBean THREAD_CPU=ManagementFactory.getThreadMXBean();
    private static long runStartedNanos,frameStartedNanos,previousFrameStartedNanos,frameIntervalNanos,frameReadbackNanos;
    private static long totalReadbackNanos,maximumReadbackNanos,totalReadbackCpuNanos,reportWriteNanos,maximumReportWriteNanos;
    private static int readbackCount,queueFullAttempts,sequenceQueueFullAttempts,reportWriteCount;
    private StandaloneMorphSmoke() { }

    private static List<Case> cases() {
        var result=new ArrayList<Case>();
        for(var camera:List.of(CameraType.THIRD_PERSON_FRONT,CameraType.FIRST_PERSON)) {
            String view=camera==CameraType.FIRST_PERSON?"first":"third";
            for(var form:List.of(PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS))
                result.add(new Case(view+"_"+form.id(),form,camera,Kind.SHORT));
            if(camera==CameraType.THIRD_PERSON_FRONT) {
                for(var form:List.of(PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS))
                    result.add(new Case(view+"_"+form.id()+"_long",form,camera,Kind.LONG));
            } else result.add(new Case(view+"_blade_long",PowerForm.BLADE,camera,Kind.LONG));
            result.add(new Case(view+"_rapid_retarget",PowerForm.CLAWS,camera,Kind.RAPID));
        }
        String filter=System.getProperty("blacklight.test.standaloneMorph.cases","");
        return result.stream().filter(c->{if(filter.isBlank())return true;for(String name:filter.split(",")){name=name.trim();if(c.name().equals(name)||(name.equals("third")||name.equals("first"))&&c.name().startsWith(name+"_"))return true;}return false;}).toList();
    }
    private static boolean active(){return initialized&&!disabled&&!completed;}
    private static Case current(){return index<CASES.size()?CASES.get(index):null;}
    private static double clock(Minecraft mc,float partial){return mc.level.getGameTime()+partial;}
    private static boolean paired(PowerForm form){return form==PowerForm.CLAWS||form==PowerForm.HAMMERFISTS||form==PowerForm.MUSCLE_MASS;}
    private static void advance(Stage next){stage=next;stageTicks=0;}

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void isolate(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        if(!active())return;var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();
        for(var key:savedKeys.keySet()){key.setDown(false);while(key.consumeClick()){} }
        var c=current();if(c!=null)mc.options.setCameraType(c.camera());
        if(mc.player!=null){mc.player.setYRot(180);mc.player.yRotO=180;mc.player.setXRot(0);mc.player.xRotO=0;mc.player.yBodyRot=mc.player.yBodyRotO=180;mc.player.yHeadRot=mc.player.yHeadRotO=180;}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event){if(active()){event.setCanceled(true);Minecraft.getInstance().mouseHandler.releaseMouse();}}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeFrame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;if(active()){
        frameStartedNanos=System.nanoTime();frameIntervalNanos=previousFrameStartedNanos==0?0:frameStartedNanos-previousFrameStartedNanos;previousFrameStartedNanos=frameStartedNanos;frameReadbackNanos=0;
        rightNativeArms=0;leftNativeArms=0;thirdPersonPasses=0;
    }}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if(!active()||current()==null||current().camera()==CameraType.FIRST_PERSON)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        // Photographer fixture only. The actor, body pose and liquid material
        // clock keep their real production animation; native FPS is untouched.
        // Camera.setup posts this event before resetting eye position and then
        // flips THIRD_PERSON_FRONT by 180 degrees. Compensate that native flip.
        event.setYaw(-145);event.setPitch(-10);event.setRoll(0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void distance(CalculateDetachedCameraDistanceEvent event) {
        if(active()&&current()!=null&&current().camera()!=CameraType.FIRST_PERSON)
            event.setDistance(3.6F/Math.max(.001F,event.getEntityScalingFactor()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void nativeArm(RenderArmEvent event) {
        if(!active()||!StandalonePlayerRendering.nativeHandPass()||event.getPlayer()!=Minecraft.getInstance().player)return;
        if(event.getArm()==HumanoidArm.LEFT)leftNativeArms++;else rightNativeArms++;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void nativePlayer(RenderPlayerEvent.Pre event) {
        if(active()&&event.getEntity()==Minecraft.getInstance().player&&event.getRenderer() instanceof BlacklightPlayerRenderer)thirdPersonPasses++;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(disabled||completed||System.getProperty(PROPERTY,"").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(server==null||mc.level==null||mc.player==null)return;
        var game=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var save=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!game.equals(QA_DIRECTORY)||!save.equals(QA_DIRECTORY.resolve("saves").resolve(QA_SAVE))) {
            disabled=true;BlacklightNeoMod.LOGGER.error("Morph QA refused non-disposable directory/save: {} / {}",game,save);return;
        }
        try {
            if(!initialized){setup(mc);return;}
            if(imageFailure!=null)throw new IllegalStateException("Frame image writer failed: "+imageFailure);
            if(!serverReady)return;
            recordServer(mc);stageTicks++;
            // Preserve raw evidence even if a production renderer crashes before
            // the first case reaches its completion checkpoint.
            if(stageTicks%20==0)write(false,null);
            if(stageTicks>240)throw new IllegalStateException("Timed out in morph QA stage "+stage+" / "+(current()==null?"complete":current().name())+"; last authoritative gate="+serverGate);
            var sample=WeaponMorphTracker.sample(mc.player,0);
            switch(stage) {
                case SETTLE -> {if(stageTicks>=80){
                    if(CASES.isEmpty())throw new IllegalStateException("Morph case filter selected no cases");
                    liquidMaterialChecked=true;liquidMaterialReadyAtSettle=LiquidBiomassMaterial.ready();
                    if(!liquidMaterialReadyAtSettle)throw new IllegalStateException("Wet tissue shader did not load on the actual client GPU");
                    prepareCase(mc);
                }}
                case RESET -> {
                    if(stageTicks>=12&&com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.NONE&&!sample.transitioning()
                            &&serverGate!=null&&serverGate.form()==PowerForm.NONE&&serverGate.ready(current().form())) {
                        if(current().kind()==Kind.RAPID){advance(Stage.RAPID_CLAWS);request(mc,PowerForm.CLAWS,false);}
                        else {advance(Stage.GROW);request(mc,current().form(),current().kind()==Kind.LONG);}
                    }
                }
                case HOLD -> {
                    if(current().kind()==Kind.LONG) {
                        if(com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.POWER_MORPH).visible(mc.player,0))longStableHoldTicks=0;
                        else longStableHoldTicks++;
                    }
                    if(stageTicks>=8&&(current().kind()!=Kind.LONG||longStableHoldTicks>=8)){advance(Stage.RETRACT);request(mc,PowerForm.NONE,false);}
                }
                case TAIL -> {if(Double.isNaN(sequenceStart)||sequenceDone)advance(Stage.NEXT);}
                case NEXT -> {
                    var result=new JsonObject();result.addProperty("case",current().name());result.addProperty("growthMilestones",Integer.bitCount(growthMask));result.addProperty("retractMilestones",Integer.bitCount(retractMask));
                    result.addProperty("longDelayObserved",longDelayedFrame);result.addProperty("sequenceFrames",sequenceFrameCount);result.addProperty("sequenceDroppedSlots",sequenceDropped);caseResults.add(result);
                    if(current().kind()!=Kind.RAPID){require(growthMask==31,"missing growth milestones for "+current().name());require(retractMask==31,"missing retract milestones for "+current().name());}
                    if(current().kind()==Kind.LONG)require(longDelayedFrame,"long transformation did not preserve the original activation delay for "+current().form().id());
                    index++;write(false,null);if(index<CASES.size())prepareCase(mc);else advance(Stage.DRAIN);
                }
                case DRAIN -> {if(writtenImages.get()==submittedImages.get()&&imageWriter.getQueue().isEmpty())advance(Stage.DONE);}
                default -> { }
            }
        }catch(Exception e){fail(e);}
    }

    private static void setup(Minecraft mc)throws Exception {
        output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();
        if(output.startsWith(QA_DIRECTORY.resolve("saves")))throw new IllegalStateException("QA output must be separate from save data");
        Files.createDirectories(output);
        if(ModList.get().isLoaded("epicfight")||ModList.get().isLoaded("wom"))throw new IllegalStateException("Old combat framework found on standalone QA classpath");
        previousCamera=mc.options.getCameraType();previousFov=mc.options.fov().get();previousGui=mc.options.hideGui;previousPause=mc.options.pauseOnLostFocus;previousMouse=mc.mouseHandler.isMouseGrabbed();
        previousYaw=mc.player.getYRot();previousPitch=mc.player.getXRot();for(var key:mc.options.keyMappings)savedKeys.put(key,key.isDown());
        mc.options.pauseOnLostFocus=false;mc.setScreen(null);mc.mouseHandler.releaseMouse();mc.getWindow().setTitle("Blacklight · 液态变形自动 QA（隔离副本，请勿操作）");
        runStartedNanos=System.nanoTime();previousPngCompression=STBImageWrite.stbi_write_png_compression_level.get(0);previousPngFilter=STBImageWrite.stbi_write_force_png_filter.get(0);
        STBImageWrite.stbi_write_png_compression_level.put(0,1);STBImageWrite.stbi_write_force_png_filter.put(0,0);compressionConfigured=true;
        imageWriter=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(6),r->{var thread=new Thread(r,"blacklight-morph-qa-image-writer");thread.setDaemon(true);return thread;});
        initialized=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        server.execute(()->{
            try {
                var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,250,z),((x+z)&1)==0?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.POLISHED_ANDESITE.defaultBlockState());
                    for(int y=251;y<=258;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                for(var entity:level.getEntitiesOfClass(ExperienceOrb.class,new AABB(-9,251,-9,10,259,10)))entity.discard();
                for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(-9,251,-9,10,259,10)))entity.discard();
                var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("QA player missing");
                var state=new BlacklightState();state.infect();state.setPrototypeMode(true);for(var form:PowerForm.values())state.unlock(form);state.activate(PowerForm.NONE);state.addBiomass(1000);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS,0);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.POWER_MORPH,PowerMorph.NONE);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.POWER_MORPH);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
                NativePlayerActionBackend.clear(p);com.codex.blacklight.combat.CombatTargeting.requestLock(p,-1);
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.getInventory().clearContent();
                p.connection.teleport(.5,251,.5,180,0);p.setDeltaMovement(0,0,0);p.yBodyRot=p.yBodyRotO=180;p.yHeadRot=p.yHeadRotO=180;
                serverReady=true;
            }catch(Exception e){fail(e);}
        });
    }
    private static void prepareCase(Minecraft mc) {
        var c=current();growthMask=0;retractMask=0;observedGrow=false;observedRetract=false;longDelayedFrame=false;longStableHoldTicks=0;
        sequenceStart=Double.NaN;nextSequenceSlot=0;sequenceFrameCount=0;sequenceDropped=0;sequenceDone=false;sequenceDirectory=null;
        mc.options.setCameraType(c.camera());mc.options.hideGui=c.camera()!=CameraType.FIRST_PERSON;mc.options.fov().set(c.camera()==CameraType.FIRST_PERSON?70:55);
        advance(Stage.RESET);
        // The preceding real retraction already confirmed NONE on both ends.
        // Repeating NONE creates a fresh server cooldown and has no wire ACK
        // by which a 12-client-tick delay can prove that it has expired.
        var sample=WeaponMorphTracker.sample(mc.player,0);
        if(serverGate==null||serverGate.form()!=PowerForm.NONE||com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower()!=PowerForm.NONE||sample.transitioning())
            request(mc,PowerForm.NONE,false);
    }
    private static void request(Minecraft mc,PowerForm form,boolean longTransform) {
        var row=new JsonObject();row.addProperty("case",current().name());row.addProperty("stage",stage.name());row.addProperty("form",form.id());row.addProperty("longTransform",longTransform);row.addProperty("clientGameTime",mc.level.getGameTime());requests.add(row);
        row.addProperty("clientUnlockedMask",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getUnlockedMask());row.addProperty("clientPrototypeMode",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).isPrototypeMode());
        var gate=serverGate;if(gate!=null){row.addProperty("serverGateGameTime",gate.gameTime());row.addProperty("serverGatePlayerTick",gate.playerTick());row.addProperty("serverGateForm",gate.form().id());row.addProperty("serverGateCanSelectDesired",gate.ready(form));row.addProperty("serverGateCombatAction",gate.action());row.addProperty("serverGateCooldownReady",gate.cooldownReady());row.addProperty("serverGateUnlockedMask",gate.unlockedMask());row.addProperty("serverGateConsuming",gate.consuming());}
        PacketDistributor.sendToServer(new PowerSelectPayload(form.ordinal(),longTransform));
    }
    private static void recordServer(Minecraft mc) {
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();String label=stage.name();String c=current()==null?"done":current().name();
        server.execute(()->{
            var p=server.getPlayerList().getPlayer(uuid);if(p==null)return;
            var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var combat=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT);
            serverGate=new ServerGate(p.level().getGameTime(),p.tickCount,state.getActivePower(),p.isAlive()&&!p.isSpectator()&&!p.isDeadOrDying(),state.isInfected(),state.isPrototypeMode(),com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING),combat.action().name(),combat.canToggleTransform(p.tickCount),state.getUnlockedMask());
            var row=new JsonObject();row.addProperty("case",c);row.addProperty("stage",label);row.addProperty("gameTime",p.level().getGameTime());row.addProperty("form",com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower().id());
            row.addProperty("playerTickCount",serverGate.playerTick());row.addProperty("canAct",serverGate.alive());row.addProperty("infected",serverGate.infected());row.addProperty("prototypeMode",serverGate.prototype());row.addProperty("consuming",serverGate.consuming());
            row.addProperty("combatAction",serverGate.action());row.addProperty("transformCooldownReady",serverGate.cooldownReady());row.addProperty("unlockedMask",serverGate.unlockedMask());
            row.addProperty("powerMorphFormId",com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.POWER_MORPH).formId());row.addProperty("powerMorphStart",com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.POWER_MORPH).started());
            synchronized(serverTicks){serverTicks.add(row);}
        });
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!active())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        long qaStartedNanos=System.nanoTime();JsonObject performanceRow=null;
        try {
            float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);double now=clock(mc,partial);
            var sample=WeaponMorphTracker.sample(mc.player,partial);validateFrame(mc,sample,now);
            performanceRow=frameTrace.get(frameTrace.size()-1).getAsJsonObject();
            performanceRow.addProperty("nativeFrameIntervalWallMs",frameIntervalNanos/1E6);
            performanceRow.addProperty("nativeRenderWallMs",(qaStartedNanos-frameStartedNanos)/1E6);
            performanceRow.addProperty("wallSecondsSinceQaStart",(qaStartedNanos-runStartedNanos)/1E9);
            if(stage==Stage.GROW&&sample.target()==current().form()&&com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower()==current().form()) {
                if(!observedGrow) {
                    observedGrow=true;require(sample.phase()<.12F,"first client morph frame skipped the onset for "+current().name());
                    if(current().kind()==Kind.SHORT&&current().form()==PowerForm.BLADE){sequenceStart=now;sequenceDirectory=current().name();}
                }
                for(int i=0;i<MILESTONES.length;i++)if((growthMask&(1<<i))==0&&sample.phase()+1e-5>=MILESTONES[i]) {
                    if(capture(mc,current().name()+"_grow_"+String.format("%03d",Math.round(MILESTONES[i]*100)),sample,partial,MILESTONES[i]))growthMask|=1<<i;
                }
                if(current().kind()==Kind.LONG) {
                    var morph=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.POWER_MORPH);float age=morph.seconds(mc.player,partial)*20;
                    float onset=WeaponMorphTiming.onset(current().form());
                    if(morph.formId()==current().form().ordinal()&&age>=onset-11&&age<onset-2&&sample.phase()<.001F) {
                        if(!longDelayedFrame)longDelayedFrame=capture(mc,current().name()+"_original_delay",sample,partial,null);
                    }
                    if(morph.formId()==current().form().ordinal()&&age<onset-2)require(sample.phase()<.001F,"long liquid growth began before its source activation cue for "+current().form().id());
                }
                if(!sample.transitioning()&&sample.phase()>=1&&growthMask==31) {
                    require(sample.visible(false),"steady weapon missing right side for "+current().name());
                    require(sample.visible(true)==paired(current().form()),"steady weapon has the wrong left side for "+current().name());
                    require(sample.projectionForm(false)==current().form(),"steady right-hand projection is wrong for "+current().name());
                    require(sample.projectionForm(true)==(paired(current().form())?current().form():PowerForm.NONE),"steady left-hand projection is wrong for "+current().name());
                    advance(Stage.HOLD);
                }
            } else if(stage==Stage.RETRACT&&sample.target()==PowerForm.NONE&&com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.NONE) {
                if(!observedRetract){observedRetract=true;require(sample.phase()<.12F,"first retraction frame skipped the onset for "+current().name());}
                for(int i=0;i<MILESTONES.length;i++)if((retractMask&(1<<i))==0&&sample.phase()+1e-5>=MILESTONES[i]) {
                    if(capture(mc,current().name()+"_retract_"+String.format("%03d",Math.round(MILESTONES[i]*100)),sample,partial,MILESTONES[i]))retractMask|=1<<i;
                }
                if(!sample.transitioning()&&sample.phase()>=1&&retractMask==31) {
                    require(sample.parts().isEmpty(),"weapon retraction left residual geometry for "+current().name());require(!sample.visible(false)&&!sample.visible(true),"weapon retraction still hides normal arms for "+current().name());
                    require(sample.flow()==0,"weapon retraction left residual liquid flow for "+current().name());advance(Stage.TAIL);
                }
            }
            rapidFrame(mc,sample,partial);
            sequence(mc,sample,partial,now);
            if(stage==Stage.DONE) {
                completed=true;restore(mc);imageWriter.shutdown();write(true,null);
                BlacklightNeoMod.LOGGER.info("Native liquid morph QA complete: {} cases, {} images, {} violations",caseResults.size(),writtenImages.get(),violations.size());mc.stop();
            }
        }catch(Exception e){fail(e);}
        finally {if(performanceRow!=null){performanceRow.addProperty("qaPostFrameWorkWallMs",(System.nanoTime()-qaStartedNanos)/1E6);performanceRow.addProperty("frameReadbackWallMs",frameReadbackNanos/1E6);performanceRow.addProperty("imageWriterQueueDepth",imageWriter.getQueue().size());}}
    }

    private static void validateFrame(Minecraft mc,WeaponMorphTracker.Sample sample,double now) {
        double materialTime=sample.materialTime();
        boolean rawMonotonic=Double.isFinite(now)&&now+1e-3>=previousClock;
        boolean materialMonotonic=Double.isFinite(materialTime)&&materialTime+1e-6>=previousMaterialClock;
        long longStart=sample.longTransform()?com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.POWER_MORPH).started():Long.MIN_VALUE;
        boolean sameTransition=previousTransitioning&&sample.transitioning()&&previousTarget==sample.target()&&previousFrom==sample.from()
                &&previousLongTransform==sample.longTransform()&&previousLongStart==longStart;
        boolean phaseMonotonic=!sameTransition||sample.phase()+1e-5F>=previousPhase;
        double clamp=materialTime-now;
        if(Double.isFinite(clamp)&&clamp>.001) {
            maximumMaterialRawClampTicks=Math.max(maximumMaterialRawClampTicks,clamp);
            var row=new JsonObject();row.addProperty("stage",stage.name());row.addProperty("rawGameTime",now);row.addProperty("materialTime",materialTime);row.addProperty("clampTicks",clamp);row.addProperty("target",sample.target().id());row.addProperty("phase",sample.phase());materialClockClamps.add(row);
        }
        if(!rawMonotonic) {
            var correction=new JsonObject();correction.addProperty("stage",stage.name());correction.addProperty("stageTick",stageTicks);
            correction.addProperty("case",current()==null?"done":current().name());correction.addProperty("worldGameTime",mc.level.getGameTime());
            if(Double.isFinite(previousClock))correction.addProperty("previousGameTimeWithPartial",previousClock);else correction.addProperty("previousGameTimeWithPartial",Double.toString(previousClock));
            if(Double.isFinite(now)) {correction.addProperty("rawGameTimeWithPartial",now);correction.addProperty("partial",now-mc.level.getGameTime());}
            else correction.addProperty("rawGameTimeWithPartial",Double.toString(now));
            if(Double.isFinite(previousClock)&&Double.isFinite(now))correction.addProperty("rawDelta",now-previousClock);
            correction.addProperty("materialTime",materialTime);if(Double.isFinite(previousMaterialClock))correction.addProperty("previousMaterialTime",previousMaterialClock);
            correction.addProperty("productionMaterialClockMonotonic",materialMonotonic);if(Double.isFinite(clamp))correction.addProperty("materialRawClampTicks",clamp);
            correction.addProperty("target",sample.target().id());correction.addProperty("phase",sample.phase());correction.addProperty("transitioning",sample.transitioning());
            correction.addProperty("partsEmpty",sample.parts().isEmpty());correction.addProperty("startupStableNone",stage==Stage.SETTLE&&!sample.transitioning()&&sample.target()==PowerForm.NONE&&sample.parts().isEmpty());
            correction.addProperty("disposition",Double.isFinite(now)&&materialMonotonic?"raw server correction; production materialTime preserved/clamped":"failure; source/material clock non-finite or materialTime regressed");clockDiscontinuities.add(correction);
            if(Double.isFinite(now)&&materialMonotonic)BlacklightNeoMod.LOGGER.info("Liquid morph QA raw server clock correction: {}",correction);
            else BlacklightNeoMod.LOGGER.error("Liquid morph QA raw clock discontinuity: {}",correction);
        }
        if(!materialMonotonic||!phaseMonotonic) {
            var failure=new JsonObject();failure.addProperty("stage",stage.name());failure.addProperty("target",sample.target().id());failure.addProperty("rawGameTime",now);failure.addProperty("materialTime",materialTime);
            if(Double.isFinite(previousMaterialClock))failure.addProperty("previousMaterialTime",previousMaterialClock);
            failure.addProperty("previousPhase",previousPhase);failure.addProperty("phase",sample.phase());failure.addProperty("sameTransition",sameTransition);failure.addProperty("materialMonotonic",materialMonotonic);failure.addProperty("phaseMonotonic",phaseMonotonic);visualClockFailures.add(failure);
        }
        require(Double.isFinite(now),"raw source game clock became non-finite");
        require(materialMonotonic,"production materialTime ran backwards or became non-finite");
        require(phaseMonotonic,"same visual transition phase ran backwards");
        boolean retarget=sample.target()!=previousTarget&&now-previousClock<1;
        if(retarget)for(var entry:previousGrowth.entrySet()) {
            float step=Math.abs(sample.frame(entry.getKey()).growth()-entry.getValue());maximumRetargetStep=Math.max(maximumRetargetStep,step);
            require(step<.18F,"rapid retarget snapped an existing form instead of preserving its current liquid coverage");
        }
        previousClock=now;previousMaterialClock=materialTime;previousTarget=sample.target();previousFrom=sample.from();previousPhase=sample.phase();previousTransitioning=sample.transitioning();previousLongTransform=sample.longTransform();previousLongStart=longStart;previousGrowth.clear();validatedFrames++;
        require(unit(sample.phase())&&unit(sample.flow()),"morph phase/flow became non-finite or outside [0,1]");
        for(var part:sample.parts()) {
            var surface=part.surface();require(unit(surface.growth())&&unit(surface.phase())&&unit(surface.flow()),"morph part became non-finite or outside [0,1]");
            require(part.form()!=PowerForm.NONE,"NONE appeared as a renderable morph part");
            previousGrowth.put(part.form(),surface.growth());
        }
        if(!sample.transitioning()) {
            require(sample.phase()==1&&sample.flow()==0,"steady morph left an active liquid clock");
            if(sample.target()!=PowerForm.NONE)require(sample.frame(sample.target()).growth()==1,"steady target did not fully form");
        }
        var c=current();if(c!=null&&stage!=Stage.SETTLE&&stage!=Stage.DRAIN)require(mc.options.getCameraType()==c.camera(),"morph QA view changed from its requested native perspective");
        if(c!=null&&c.camera()!=CameraType.FIRST_PERSON&&stage!=Stage.SETTLE) {
            var camera=mc.gameRenderer.getMainCamera();require(Math.abs(net.minecraft.util.Mth.wrapDegrees(camera.getYRot()-35))<.1F&&Math.abs(camera.getXRot()-10)<.1F,"third-person photographer fixture did not reach its actual 35/10 view angles");
            require(Math.abs(camera.getPosition().distanceTo(mc.player.getEyePosition())-3.6)<.05,"third-person photographer fixture did not reach its actual 3.6 eye distance");
        }
        boolean any=sample.parts().stream().anyMatch(p->p.surface().growth()>.005F);
        if(liquidMaterialChecked&&sample.flow()>.001F)require(LiquidBiomassMaterial.ready(),"active liquid frame lost the actual GPU wet tissue shader");
        if(any&&c!=null&&stage!=Stage.RESET&&stage!=Stage.SETTLE&&stage!=Stage.DRAIN) {
            if(c.camera()==CameraType.FIRST_PERSON) {
                require(rightNativeArms>0,"visible weapon did not pass through native right RenderArmEvent");
                if(sample.visible(true))require(leftNativeArms>0,"visible paired weapon did not pass through native left RenderArmEvent");
                var rendered=BlacklightPlayerRenderer.renderedView(mc.player);
                require(rendered!=null&&Math.abs(rendered.gameTime()-now)<=1,"visible morph did not execute the native first-person mesh renderer this frame");
            } else require(thirdPersonPasses>0,"visible weapon did not pass through the real third-person player renderer");
        }
        var trace=new JsonObject();trace.addProperty("gameTime",now);trace.addProperty("rawGameTime",now);trace.addProperty("materialTime",materialTime);trace.addProperty("materialRawClampTicks",clamp);trace.addProperty("stage",stage.name());trace.addProperty("target",sample.target().id());trace.addProperty("phase",sample.phase());trace.addProperty("flow",sample.flow());
        trace.addProperty("transitioning",sample.transitioning());trace.addProperty("rightNativeArmPasses",rightNativeArms);trace.addProperty("leftNativeArmPasses",leftNativeArms);trace.addProperty("thirdPersonPasses",thirdPersonPasses);
        trace.addProperty("clientUnlockedMask",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getUnlockedMask());trace.addProperty("powerMorphVisible",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.POWER_MORPH).visible(mc.player,0));trace.addProperty("stableHoldTicksAfterLongClip",longStableHoldTicks);
        var amounts=new JsonObject();previousGrowth.forEach((form,growth)->amounts.addProperty(form.id(),growth));trace.add("growth",amounts);frameTrace.add(trace);
        if(!rawMonotonic||!materialMonotonic||!phaseMonotonic)write(false,null);
    }
    private static boolean unit(float value){return Float.isFinite(value)&&value>=-1e-5F&&value<=1.00001F;}
    private static void require(boolean condition,String message){if(!condition&&violations.add(message))BlacklightNeoMod.LOGGER.error("Liquid morph QA: {}",message);}

    private static void rapidFrame(Minecraft mc,WeaponMorphTracker.Sample sample,float partial) {
        var actual=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower();
        if(stage==Stage.RAPID_CLAWS&&stageTicks>=12&&sample.target()==PowerForm.CLAWS&&actual==PowerForm.CLAWS&&serverGate!=null&&serverGate.form()==PowerForm.CLAWS&&serverGate.ready(PowerForm.BLADE)) {
            require(sample.transitioning(),"rapid CLAWS request did not interrupt an active liquid transition");
            if(capture(mc,current().name()+"_claws_before_retarget",sample,partial,null)){advance(Stage.RAPID_BLADE);request(mc,PowerForm.BLADE,false);}
        } else if(stage==Stage.RAPID_BLADE&&stageTicks>=12&&sample.target()==PowerForm.BLADE&&actual==PowerForm.BLADE&&serverGate!=null&&serverGate.form()==PowerForm.BLADE&&serverGate.ready(PowerForm.HAMMERFISTS)) {
            require(sample.transitioning(),"rapid BLADE request did not interrupt an active liquid transition");
            if(capture(mc,current().name()+"_blade_before_retarget",sample,partial,null)){advance(Stage.RAPID_HAMMER);request(mc,PowerForm.HAMMERFISTS,false);}
        } else if(stage==Stage.RAPID_HAMMER&&stageTicks>=12&&sample.target()==PowerForm.HAMMERFISTS&&actual==PowerForm.HAMMERFISTS&&serverGate!=null&&serverGate.form()==PowerForm.HAMMERFISTS&&serverGate.ready(PowerForm.NONE)) {
            if(capture(mc,current().name()+"_hammer_before_retract",sample,partial,null)){advance(Stage.RAPID_CLEAR);request(mc,PowerForm.NONE,false);}
        } else if(stage==Stage.RAPID_CLEAR&&stageTicks>=20&&sample.target()==PowerForm.NONE&&actual==PowerForm.NONE&&!sample.transitioning()) {
            require(sample.parts().isEmpty(),"rapid retarget left residual parts");require(sample.flow()==0,"rapid retarget left residual liquid flow");
            if(capture(mc,current().name()+"_cleared",sample,partial,null))advance(Stage.NEXT);
        }
    }

    private static void sequence(Minecraft mc,WeaponMorphTracker.Sample sample,float partial,double now)throws Exception {
        if(Double.isNaN(sequenceStart)||sequenceDone)return;
        int slot=(int)Math.floor(Math.max(0,now-sequenceStart)*1.5);
        if(slot>=90){recordSequenceGap(nextSequenceSlot,89,now);sequenceDropped+=Math.max(0,90-nextSequenceSlot);sequenceDone=true;return;}
        if(slot<nextSequenceSlot)return;
        String file="sequence/"+sequenceDirectory+"/frame_"+String.format("%05d",sequenceFrameCount)+".png";
        if(queueImage(mc,file,slot)) {
            recordSequenceGap(nextSequenceSlot,slot-1,now);
            sequenceDropped+=Math.max(0,slot-nextSequenceSlot);nextSequenceSlot=slot+1;
            var row=sampleRow(mc,sample,partial);row.addProperty("image",file);row.addProperty("sequence",sequenceDirectory);row.addProperty("sequenceFrame",sequenceFrameCount++);
            row.addProperty("desired30FpsSlot",slot);row.addProperty("secondsSinceStart",(now-sequenceStart)/20);frames.add(row);
        }
    }
    private static void recordSequenceGap(int first,int last,double now) {
        if(last<first)return;
        var row=new JsonObject();row.addProperty("sequence",sequenceDirectory);row.addProperty("firstMissing30FpsSlot",first);row.addProperty("lastMissing30FpsSlot",last);row.addProperty("count",last-first+1);
        row.addProperty("gameTimeWhenObserved",now);row.addProperty("wallSecondsSinceQaStart",(System.nanoTime()-runStartedNanos)/1E9);row.addProperty("imageQueueDepth",imageWriter.getQueue().size());
        row.addProperty("disposition","missing source frames retained as gaps; no frame copying or interpolation");sequenceGaps.add(row);
    }
    private static boolean capture(Minecraft mc,String name,WeaponMorphTracker.Sample sample,float partial,Float milestone) {
        try {
            String file=name+".png";if(!queueImage(mc,file))return false;
            var row=sampleRow(mc,sample,partial);row.addProperty("image",file);if(milestone!=null)row.addProperty("requestedPhaseMilestone",milestone);views.add(row);
            BlacklightNeoMod.LOGGER.info("Liquid morph QA captured {} phase={} flow={}",name,sample.phase(),sample.flow());return true;
        }catch(Exception e){fail(e);return false;}
    }
    private static JsonObject sampleRow(Minecraft mc,WeaponMorphTracker.Sample sample,float partial) {
        var row=new JsonObject();row.addProperty("case",current()==null?"done":current().name());row.addProperty("stage",stage.name());row.addProperty("gameTime",clock(mc,partial));row.addProperty("rawGameTime",clock(mc,partial));row.addProperty("materialTime",sample.materialTime());
        row.addProperty("source","Minecraft framebuffer after actual native renderer");row.addProperty("cameraType",mc.options.getCameraType().name());
        row.addProperty("from",sample.from().id());row.addProperty("target",sample.target().id());row.addProperty("phase",sample.phase());row.addProperty("flow",sample.flow());row.addProperty("transitioning",sample.transitioning());row.addProperty("longTransform",sample.longTransform());
        row.addProperty("serverSyncedForm",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower().id());row.addProperty("powerMorphFormId",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.POWER_MORPH).formId());row.addProperty("powerMorphStart",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.POWER_MORPH).started());
        row.addProperty("rightNativeArmPasses",rightNativeArms);row.addProperty("leftNativeArmPasses",leftNativeArms);row.addProperty("thirdPersonPasses",thirdPersonPasses);
        row.addProperty("rightCoverage",sample.coverage(false));row.addProperty("leftCoverage",sample.coverage(true));row.addProperty("rightProjection",sample.projectionForm(false).id());row.addProperty("leftProjection",sample.projectionForm(true).id());
        row.addProperty("width",mc.getMainRenderTarget().width);row.addProperty("height",mc.getMainRenderTarget().height);
        row.addProperty("wallSecondsSinceQaStart",(System.nanoTime()-runStartedNanos)/1E9);
        row.addProperty("liquidMaterialGpuReady",LiquidBiomassMaterial.ready());
        boolean fixture=current()!=null&&current().camera()!=CameraType.FIRST_PERSON;
        row.addProperty("thirdPersonCameraFixture",fixture);row.addProperty("cameraFixture",fixture?"quarter front photographer: yaw 35, pitch 10, eye distance 3.6; actor and material timing untouched":"none; native first-person camera");
        if(fixture){row.addProperty("fixtureRequestedYaw",35);row.addProperty("fixtureRequestedPitch",10);row.addProperty("fixtureEyeDistance",3.6);}
        var camera=mc.gameRenderer.getMainCamera();var position=new JsonArray();position.add(camera.getPosition().x);position.add(camera.getPosition().y);position.add(camera.getPosition().z);
        row.add("actualCameraPosition",position);row.addProperty("actualCameraYaw",camera.getYRot());row.addProperty("actualCameraPitch",camera.getXRot());
        row.addProperty("actualCameraEyeDistance",camera.getPosition().distanceTo(mc.player.getEyePosition()));
        var parts=new JsonArray();for(var part:sample.parts()) {var item=new JsonObject();item.addProperty("form",part.form().id());item.addProperty("growth",part.surface().growth());item.addProperty("phase",part.surface().phase());item.addProperty("flow",part.surface().flow());item.addProperty("retracting",part.surface().retracting());parts.add(item);}row.add("parts",parts);
        return row;
    }
    private static boolean queueImage(Minecraft mc,String relative)throws Exception {
        return queueImage(mc,relative,null);
    }
    private static boolean queueImage(Minecraft mc,String relative,Integer sequenceSlot)throws Exception {
        int depth=imageWriter.getQueue().size();
        if(imageWriter.getQueue().remainingCapacity()==0) {
            queueFullAttempts++;if(sequenceSlot!=null)sequenceQueueFullAttempts++;
            var row=new JsonObject();row.addProperty("image",relative);row.addProperty("case",current()==null?"done":current().name());row.addProperty("stage",stage.name());row.addProperty("gameTime",mc.level.getGameTime());
            row.addProperty("queueDepth",depth);row.addProperty("wallSecondsSinceQaStart",(System.nanoTime()-runStartedNanos)/1E9);row.addProperty("reason","bounded PNG writer queue full; render thread was not blocked");
            if(sequenceSlot!=null)row.addProperty("requested30FpsSlot",sequenceSlot);imageQueueRejections.add(row);return false;
        }
        long readStarted=System.nanoTime(),readCpuStarted=cpuTime();
        var image=Screenshot.takeScreenshot(mc.getMainRenderTarget());
        long readFinished=System.nanoTime(),readCpuFinished=cpuTime(),readNanos=readFinished-readStarted;
        totalReadbackNanos+=readNanos;maximumReadbackNanos=Math.max(maximumReadbackNanos,readNanos);frameReadbackNanos+=readNanos;readbackCount++;
        if(readCpuStarted>=0&&readCpuFinished>=0)totalReadbackCpuNanos+=readCpuFinished-readCpuStarted;
        var timing=new JsonObject();timing.addProperty("image",relative);timing.addProperty("case",current()==null?"done":current().name());timing.addProperty("stage",stage.name());
        timing.addProperty("readbackWallMs",readNanos/1E6);if(readCpuStarted>=0&&readCpuFinished>=0)timing.addProperty("readbackCpuMs",(readCpuFinished-readCpuStarted)/1E6);
        timing.addProperty("queueDepthBeforeCapture",depth);timing.addProperty("readbackStartedSecondsSinceQaStart",(readStarted-runStartedNanos)/1E9);timing.addProperty("pngCompressionLevel",1);timing.addProperty("pngForcedFilter",0);
        if(sequenceSlot!=null)timing.addProperty("requested30FpsSlot",sequenceSlot);
        Path file=output.resolve(relative);submittedImages.incrementAndGet();long queuedNanos=System.nanoTime();
        try {
            imageWriter.execute(()->{
                long workerStarted=System.nanoTime(),workerCpuStarted=cpuTime();
                timing.addProperty("workerQueueWaitWallMs",(workerStarted-queuedNanos)/1E6);
                try(image) {
                    Files.createDirectories(file.getParent());image.writeToFile(file);
                    long workerFinished=System.nanoTime(),workerCpuFinished=cpuTime();
                    timing.addProperty("pngWriteWallMs",(workerFinished-workerStarted)/1E6);if(workerCpuStarted>=0&&workerCpuFinished>=0)timing.addProperty("pngWriteCpuMs",(workerCpuFinished-workerCpuStarted)/1E6);
                    timing.addProperty("writtenBytes",Files.size(file));timing.addProperty("finishedSecondsSinceQaStart",(workerFinished-runStartedNanos)/1E9);
                    synchronized(imageTimings){imageTimings.add(timing);}writtenImages.incrementAndGet();
                }catch(Exception e){imageFailure=e.toString();}
            });
        }catch(RuntimeException e){image.close();submittedImages.decrementAndGet();throw e;}
        return true;
    }
    private static long cpuTime(){return THREAD_CPU.isCurrentThreadCpuTimeSupported()&&THREAD_CPU.isThreadCpuTimeEnabled()?THREAD_CPU.getCurrentThreadCpuTime():-1;}
    private static void restore(Minecraft mc) {
        savedKeys.forEach(KeyMapping::setDown);if(previousCamera!=null){mc.options.setCameraType(previousCamera);mc.options.fov().set(previousFov);mc.options.hideGui=previousGui;mc.options.pauseOnLostFocus=previousPause;}
        if(mc.player!=null){mc.player.setYRot(previousYaw);mc.player.setXRot(previousPitch);}
        if(previousMouse)mc.mouseHandler.grabMouse();else mc.mouseHandler.releaseMouse();mc.updateTitle();
        if(compressionConfigured){STBImageWrite.stbi_write_png_compression_level.put(0,previousPngCompression);STBImageWrite.stbi_write_force_png_filter.put(0,previousPngFilter);compressionConfigured=false;}
    }
    private static void fail(Exception e) {
        if(disabled)return;disabled=true;BlacklightNeoMod.LOGGER.error("Native liquid morph QA failed",e);
        var mc=Minecraft.getInstance();mc.execute(()->{if(initialized)restore(mc);if(imageWriter!=null)imageWriter.shutdown();write(false,e.toString());mc.stop();});
    }
    private static synchronized void write(boolean complete,String error) {
        if(output==null)return;
        long began=System.nanoTime();
        try {
            var report=new JsonObject();report.addProperty("complete",complete);report.addProperty("passed",complete&&violations.isEmpty()&&imageFailure==null);
            report.addProperty("source","PowerSelectPayload -> authoritative PowerFormSystem / optional PowerMorph -> synchronized state -> untouched production interpolation -> native first/third person framebuffer");
            report.addProperty("gameDirectory",QA_DIRECTORY.toString());report.addProperty("save",QA_SAVE);report.addProperty("requestedCases",System.getProperty("blacklight.test.standaloneMorph.cases","all"));
            report.addProperty("imageQueueCapacity",6);report.addProperty("submittedImages",submittedImages.get());report.addProperty("writtenImages",writtenImages.get());report.addProperty("sequenceNominalFps",30);report.addProperty("sequenceDurationSeconds",3);
            report.addProperty("validatedNativeFrames",validatedFrames);report.addProperty("maximumRetargetGrowthStep",maximumRetargetStep);
            report.addProperty("maximumMaterialRawClampTicks",maximumMaterialRawClampTicks);report.addProperty("materialClockClampFrameCount",materialClockClamps.size());report.addProperty("rawClockCorrectionCount",clockDiscontinuities.size());
            report.addProperty("visualClockAssertion","actual production Sample.materialTime must be finite/monotonic; same identified visual transition phase must not regress; raw source corrections are preserved");
            report.add("materialClockClamps",materialClockClamps);report.add("visualClockFailures",visualClockFailures);
            report.addProperty("liquidMaterialGpuLoadChecked",liquidMaterialChecked);report.addProperty("liquidMaterialGpuReadyAtSettle",liquidMaterialReadyAtSettle);report.addProperty("liquidMaterialGpuReady",LiquidBiomassMaterial.ready());
            report.addProperty("thirdPersonCameraFixture","quarter front photographer: yaw 35, pitch 10, eye distance 3.6; first-person unmodified");
            report.add("cases",caseResults);report.add("requests",requests);synchronized(serverTicks){report.add("serverTicks",serverTicks.deepCopy());}report.add("views",views);report.add("sequenceFrames",frames);report.add("frameTrace",frameTrace);report.add("clockDiscontinuities",clockDiscontinuities);
            JsonArray timings;synchronized(imageTimings){timings=imageTimings.deepCopy();}
            report.add("imageTimings",timings);report.add("imageQueueRejections",imageQueueRejections);report.add("sequenceGaps",sequenceGaps);
            var performance=new JsonObject();performance.addProperty("pngCompressionLevel",1);performance.addProperty("originalPngCompressionLevel",previousPngCompression);performance.addProperty("pngForcedFilter",0);performance.addProperty("originalPngForcedFilter",previousPngFilter);
            performance.addProperty("readbackCount",readbackCount);performance.addProperty("readbackMeanWallMs",readbackCount==0?0:totalReadbackNanos/1E6/readbackCount);performance.addProperty("readbackMaximumWallMs",maximumReadbackNanos/1E6);
            performance.addProperty("readbackMeanCpuMs",readbackCount==0?0:totalReadbackCpuNanos/1E6/readbackCount);performance.addProperty("threadCpuTimingAvailable",THREAD_CPU.isCurrentThreadCpuTimeSupported()&&THREAD_CPU.isThreadCpuTimeEnabled());
            performance.addProperty("boundedQueueFullAttempts",queueFullAttempts);performance.addProperty("sequenceQueueFullAttempts",sequenceQueueFullAttempts);
            performance.add("pngWorkerWriteWallMs",summarize(timings,"pngWriteWallMs"));performance.add("pngWorkerWriteCpuMs",summarize(timings,"pngWriteCpuMs"));performance.add("pngWorkerQueueWaitWallMs",summarize(timings,"workerQueueWaitWallMs"));
            performance.add("nativeFrameIntervalWallMs",summarize(frameTrace,"nativeFrameIntervalWallMs"));performance.add("nativeRenderWallMs",summarize(frameTrace,"nativeRenderWallMs"));performance.add("qaPostFrameWorkWallMs",summarize(frameTrace,"qaPostFrameWorkWallMs"));
            performance.addProperty("completedReportWriteCount",reportWriteCount);performance.addProperty("reportWriteMeanWallMs",reportWriteCount==0?0:reportWriteNanos/1E6/reportWriteCount);performance.addProperty("reportWriteMaximumWallMs",maximumReportWriteNanos/1E6);
            report.add("performance",performance);
            var errors=new JsonArray();for(String violation:violations)errors.add(violation);report.add("violations",errors);if(error!=null)report.addProperty("error",error);if(imageFailure!=null)report.addProperty("imageFailure",imageFailure);
            var mods=new JsonArray();for(var mod:ModList.get().getMods()){var item=new JsonObject();item.addProperty("id",mod.getModId());item.addProperty("version",mod.getVersion().toString());mods.add(item);}report.add("loadedMods",mods);
            Path pending=output.resolve("native-morph.pending.json");Files.writeString(pending,new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.move(pending,output.resolve("native-morph-report.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Cannot save native liquid morph QA evidence",e);}
        finally {long elapsed=System.nanoTime()-began;reportWriteNanos+=elapsed;maximumReportWriteNanos=Math.max(maximumReportWriteNanos,elapsed);reportWriteCount++;}
    }
    private static JsonObject summarize(JsonArray rows,String property) {
        var values=new ArrayList<Double>();for(var element:rows){var row=element.getAsJsonObject();if(row.has(property)){double value=row.get(property).getAsDouble();if(Double.isFinite(value)&&value>=0)values.add(value);}}
        values.sort(Double::compareTo);var result=new JsonObject();result.addProperty("count",values.size());
        if(!values.isEmpty()){double total=0;for(double value:values)total+=value;int count=values.size();result.addProperty("mean",total/count);result.addProperty("median",count%2==0?(values.get(count/2-1)+values.get(count/2))/2:values.get(count/2));result.addProperty("p95",values.get(Math.max(0,(int)Math.ceil(count*.95)-1)));result.addProperty("maximum",values.get(count-1));}
        return result;
    }
}
