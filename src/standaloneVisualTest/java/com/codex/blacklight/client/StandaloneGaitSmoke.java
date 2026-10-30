package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.animation.BlacklightRenderPose;
import com.codex.blacklight.client.model.BlacklightPlayerModel;
import com.codex.blacklight.client.renderer.BlacklightPlayerRenderer;
import com.codex.blacklight.client.renderer.StandalonePlayerRendering;
import com.codex.blacklight.movement.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.stb.STBImageWrite;

/** Opt-in continuous native-input QA; refuses every save outside the disposable QA copy. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class StandaloneGaitSmoke {
    private static final String PROPERTY="blacklight.test.standaloneGait";
    private enum Stage { SETTLE,SPRINT,STOP,ARMOR_SETTLE,ARMOR_SPRINT,ARMOR_STOP,JUMP_SETTLE,JUMP_SPRINT,JUMP_STOP,DRAIN,DONE }
    private static Stage stage=Stage.SETTLE;
    private static boolean initialized,disabled,completed,capturePending;
    private static volatile boolean serverReady;
    private static int stageTicks;
    private static long firstJumpAirTick=-1,jumpContactTick=-1;
    private static int jumpAirTicks,jumpHoldTicks=2;
    private static boolean movingLandingObserved,standingChargeObserved;
    private static Vec3 plantedClient,plantedServer;
    private static final java.util.Map<KeyMapping,com.mojang.blaze3d.platform.InputConstants.Key> savedBindings=new java.util.IdentityHashMap<>();
    private static double maximumChargeClientDrift;
    private static volatile double maximumChargeServerDrift;
    private static int chargeClientSamples;
    private static volatile int chargeServerSamples;
    private static boolean jumpOnly(){return Boolean.getBoolean(PROPERTY+".jumpOnly");}
    private static boolean claws(){return Boolean.getBoolean(PROPERTY+".claws");}
    private static boolean standingCharge(){return Boolean.getBoolean(PROPERTY+".standingCharge");}
    private static long latestTick=Long.MIN_VALUE;
    private static Path output;
    private static CameraType previousCamera;
    private static int previousFov;
    private static boolean previousGui,previousPause,previousMouseGrabbed;
    private static float previousYaw,previousPitch,yawBeforeInputLock;
    private static int suppressedMouseEvents,suppressedPerspectiveClicks;
    private static final Map<KeyMapping,Boolean> savedKeys=new IdentityHashMap<>();
    private static final JsonArray ticks=new JsonArray(),frames=new JsonArray(),violations=new JsonArray();
    private static Field statesField,gaitField,positionField;
    private static JsonObject previousTick;
    private static final Set<String> captureNames=new HashSet<>();
    private static Field renderersField,renderedPlayerField,renderPartialField;
    private static final Map<String,Integer> sequenceFrames=new LinkedHashMap<>();
    private static String captureSequence;
    private static long renderFrame;
    private static double observerDistance=12;
    private static ThreadPoolExecutor writer;
    private static final AtomicInteger submittedImages=new AtomicInteger(),writtenImages=new AtomicInteger();
    private static final Map<String,AtomicInteger> writtenSequences=new ConcurrentHashMap<>();
    private static volatile String imageError;
    private static int previousPngLevel,previousPngFilter,queueMisses;
    private static long drainStartedNanos;
    private StandaloneGaitSmoke() {}
    private static boolean active(){return initialized&&!disabled&&!completed;}
    private static boolean sprinting(){return stage==Stage.SPRINT||stage==Stage.ARMOR_SPRINT||stage==Stage.JUMP_SPRINT;}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void input(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        if(!active())return;
        var mc=Minecraft.getInstance();
        // This fixture owns native W/sprint but must not consume the human's pointer.
        // Lock before LocalPlayer physics; the observer camera is never a control yaw.
        mc.mouseHandler.releaseMouse();
        // This isolated measurement fixture owns its observer. Human F5 must not
        // switch to the source chase camera and feed that camera yaw into W.
        while(mc.options.keyTogglePerspective.consumeClick())suppressedPerspectiveClicks++;
        mc.options.keyTogglePerspective.setDown(false);
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if(stageTicks%20==0)mc.getWindow().setTitle("Blacklight · 自动跑跳 QA（请勿操作测试窗口）");
        if(mc.player!=null){
            yawBeforeInputLock=mc.player.getYRot();
            mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
        }
        boolean run=serverReady&&sprinting()&&!(standingCharge()&&stage==Stage.JUMP_SPRINT);
        boolean probe=serverReady&&standingCharge()&&stage==Stage.JUMP_SPRINT&&stageTicks>=25&&stageTicks<20+jumpHoldTicks;
        mc.options.keyUp.setDown(run||probe);mc.options.keySprint.setDown(run||probe);
        if(probe&&mc.player!=null)mc.player.setDeltaMovement(.3,mc.player.getDeltaMovement().y,.2);
        mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(probe);
        mc.options.keyJump.setDown(serverReady&&stage==Stage.JUMP_SPRINT&&stageTicks>=20&&stageTicks<20+jumpHoldTicks);mc.options.keyShift.setDown(false);
        mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void observePlantedServer(net.minecraftforge.event.TickEvent.PlayerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!active()||!serverReady||!standingCharge()||!(event.getEntity() instanceof ServerPlayer p))return;
        var movement=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE);
        if(movement.chargeTicks()>3&&!movement.runningCharge()&&movement.jumpHeld()&&p.onGround()){
            if(plantedServer==null)plantedServer=p.position();
            maximumChargeServerDrift=Math.max(maximumChargeServerDrift,p.position().subtract(plantedServer).horizontalDistance());
            chargeServerSamples++;
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void humanPerspective(InputEvent.Key event) {
        if(active()&&Minecraft.getInstance().options.keyTogglePerspective.matches(event.getKey(),event.getScanCode())) {
            var mc=Minecraft.getInstance();
            while(mc.options.keyTogglePerspective.consumeClick())suppressedPerspectiveClicks++;
            mc.options.keyTogglePerspective.setDown(false);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void humanMouse(InputEvent.MouseButton.Pre event) {
        if(active()) {
            suppressedMouseEvents++;
            // Prevent vanilla's onPress from reacquiring capture or attacking the lane.
            event.setCanceled(true);Minecraft.getInstance().mouseHandler.releaseMouse();
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(disabled||completed||System.getProperty(PROPERTY,"").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(server==null||mc.player==null||mc.level==null)return;
        var save=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        if(!save.getFileName().toString().equals("Blacklight Standalone QA v4")
                ||!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-native-qa")) {
            disabled=true;BlacklightNeoMod.LOGGER.error("Continuous gait QA refused non-QA save {}",save);return;
        }
        if(!initialized) {
            try {
                output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();Files.createDirectories(output);
                if(ModList.get().isLoaded("epicfight")||ModList.get().isLoaded("wom"))throw new IllegalStateException("Combat framework present on QA classpath");
                previousCamera=mc.options.getCameraType();previousFov=mc.options.fov().get();previousGui=mc.options.hideGui;previousPause=mc.options.pauseOnLostFocus;
                previousMouseGrabbed=mc.mouseHandler.isMouseGrabbed();previousYaw=mc.player.getYRot();previousPitch=mc.player.getXRot();
                for(var key:List.of(mc.options.keyUp,mc.options.keySprint,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,
                        mc.options.keyJump,mc.options.keyShift,mc.options.keyAttack,mc.options.keyUse,mc.options.keyTogglePerspective))savedKeys.put(key,key.isDown());
                // Physical GLFW fallback must not inject human/stale key presses
                // into this explicit disposable fixture. Feed native KeyMappings
                // below; restore all bindings before saving options on exit.
                for(var key:savedKeys.keySet()){
                    savedBindings.put(key,key.getKey());key.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
                }
                KeyMapping.resetMapping();
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.fov().set(60);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.setScreen(null);mc.mouseHandler.releaseMouse();mc.getWindow().setTitle("Blacklight · 自动跑跳 QA（请勿操作测试窗口）");
                statesField=PlayerMotionController.class.getDeclaredField("STATES");statesField.setAccessible(true);
                previousPngLevel=STBImageWrite.stbi_write_png_compression_level.get(0);
                previousPngFilter=STBImageWrite.stbi_write_force_png_filter.get(0);
                STBImageWrite.stbi_write_png_compression_level.put(0,0);
                STBImageWrite.stbi_write_force_png_filter.put(0,0);
                writer=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(12),r->{var t=new Thread(r,"gait-qa-png");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
                renderersField=StandalonePlayerRendering.class.getDeclaredField("RENDERERS");renderersField.setAccessible(true);
                renderedPlayerField=BlacklightPlayerModel.class.getDeclaredField("renderedPlayer");renderedPlayerField.setAccessible(true);
                renderPartialField=BlacklightPlayerModel.class.getDeclaredField("renderPartial");renderPartialField.setAccessible(true);
                initialized=true;var uuid=mc.player.getUUID();
                // Exercise a real light moving impact, rather than a tiny jump with
                // no landing action. Derive hold from the configured native physics.
                int maxCharge=MovementTuningSettings.jumpChargeTicks();jumpHoldTicks=maxCharge+(standingCharge()?12:0);
                for(int held=1;!standingCharge()&&held<=maxCharge;held++)
                    if(MovementTuningSettings.estimatedJumpHeight(held/(double)maxCharge,0)>=5.5){jumpHoldTicks=held;break;}
                server.execute(()->{
                    try {
                        var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                        // A 384-block lane covers the complete 120-tick default sprint.
                        for(int x=-4;x<=4;x++)for(int z=-8;z<=384;z++) {
                            level.setBlockAndUpdate(new BlockPos(x,250,z),(z%8==0?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE).defaultBlockState());
                            for(int y=251;y<=255;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                        }
                        var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("QA actor missing");
                        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.NONE);state.unlock(PowerForm.CLAWS);state.unlock(PowerForm.ARMOR);state.activate(claws()?PowerForm.CLAWS:PowerForm.NONE);state.addBiomass(1000);
                        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS,0);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS);
                        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_STATE,new UtilityState(UtilityState.SHIELD_CAPACITY,0,level.getGameTime()-200,0,-1000));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_STATE);
                        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.POWER_MORPH,PowerMorph.NONE);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.POWER_MORPH);
                        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,com.codex.blacklight.combat.WeaponVisual.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
                        NativePlayerActionBackend.clear(p);com.codex.blacklight.combat.CombatTargeting.requestLock(p,-1);
                        p.getInventory().clearContent();
                        p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();
                        p.connection.teleport(.5,251,.5,0,0);p.setDeltaMovement(0,0,0);p.setSprinting(false);
                        serverReady=true;
                    }catch(Exception e){fail(e);}
                });
            }catch(Exception e){fail(e);}return;
        }
        if(imageError!=null){fail(new IllegalStateException("Asynchronous gait image write failed: "+imageError));return;}
        if(stage==Stage.DRAIN){
            if(writer.isTerminated()){
                if(submittedImages.get()!=writtenImages.get())violation("not every framebuffer image reached disk");
                stage=Stage.DONE;finish(mc);
            }else if((System.nanoTime()-drainStartedNanos)/1e9>60)fail(new IllegalStateException("Gait PNG writer drain exceeded 60 seconds"));
            return;
        }
        try {recordTick(mc);}catch(Exception e){fail(e);return;}
        if(!serverReady)return;
        stageTicks++;
        if(stage==Stage.SETTLE&&stageTicks>=100)advance(jumpOnly()?Stage.JUMP_SPRINT:Stage.SPRINT);
        else if(stage==Stage.SPRINT&&stageTicks>=120)advance(Stage.STOP);
        else if(stage==Stage.STOP&&stageTicks>=30) {
            advance(Stage.ARMOR_SETTLE);serverReady=false;var uuid=mc.player.getUUID();
            server.execute(()->{
                try {
                    var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("QA actor missing");
                    p.connection.teleport(.5,251,.5,0,0);p.setDeltaMovement(0,0,0);p.setSprinting(false);
                    UtilityPowerSystem.toggle(p,PowerForm.ARMOR);serverReady=true;
                }catch(Exception e){fail(e);}
            });
        } else if(stage==Stage.ARMOR_SETTLE&&stageTicks>=20)advance(Stage.ARMOR_SPRINT);
        else if(stage==Stage.ARMOR_SPRINT&&stageTicks>=120)advance(Stage.ARMOR_STOP);
        else if(stage==Stage.ARMOR_STOP&&stageTicks>=30) {
            advance(Stage.JUMP_SETTLE);serverReady=false;var uuid=mc.player.getUUID();
            server.execute(()->{
                try {
                    var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("QA actor missing");
                    p.connection.teleport(.5,251,.5,0,0);p.setDeltaMovement(0,0,0);p.setSprinting(false);
                    if(UtilityPowerSystem.active(p,PowerForm.ARMOR))UtilityPowerSystem.toggle(p,PowerForm.ARMOR);
                    NativePlayerActionBackend.clear(p);serverReady=true;
                }catch(Exception e){fail(e);}
            });
        } else if(stage==Stage.JUMP_SETTLE&&stageTicks>=20)advance(Stage.JUMP_SPRINT);
        else if(stage==Stage.JUMP_SPRINT&&stageTicks>=140)advance(Stage.JUMP_STOP);
        else if(stage==Stage.JUMP_STOP&&stageTicks>=30){stage=Stage.DRAIN;capturePending=false;drainStartedNanos=System.nanoTime();writer.shutdown();}
    }
    private static void advance(Stage next){stage=next;stageTicks=0;}
    private static Object runtime(Minecraft mc)throws Exception {
        var states=(Map<?,?>)statesField.get(null);var runtime=states.get(mc.player);
        if(runtime!=null&&gaitField==null) {
            gaitField=runtime.getClass().getDeclaredField("gait");gaitField.setAccessible(true);
            positionField=runtime.getClass().getDeclaredField("committedPosition");positionField.setAccessible(true);
        }
        return runtime;
    }
    private static void recordTick(Minecraft mc)throws Exception {
        var runtime=runtime(mc);if(runtime==null)return;
        var clock=(LocomotionClock)gaitField.get(runtime);if(clock.key()==null||clock.tick()==latestTick)return;
        var position=(Vec3)positionField.get(runtime);if(position==null)return;
        var row=new JsonObject();row.addProperty("tick",clock.tick());row.addProperty("stage",stage.name());row.addProperty("stageTick",stageTicks);
        row.addProperty("key",clock.key());row.addProperty("phase",PrototypeAnimationController.phaseFor(mc.player).name());
        row.addProperty("loop",clock.looping());row.addProperty("duration",clock.duration());row.addProperty("previousSeconds",clock.seconds(0));row.addProperty("currentSeconds",clock.current());
        row.addProperty("x",position.x);row.addProperty("y",position.y);row.addProperty("z",position.z);
        row.addProperty("playerYaw",mc.player.getYRot());row.addProperty("playerPitch",mc.player.getXRot());
        row.addProperty("movementYaw",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.MOVEMENT_STATE).cameraYaw(mc.player.getYRot()));
        row.addProperty("observerYaw",mc.gameRenderer.getMainCamera().getYRot());row.addProperty("yawBeforeInputLock",yawBeforeInputLock);
        row.addProperty("chaseOwnsView",com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView());
        row.addProperty("cameraType",mc.options.getCameraType().name());
        row.addProperty("mouseGrabbed",mc.mouseHandler.isMouseGrabbed());row.addProperty("windowFocused",mc.isWindowActive());
        if(sprinting()&&com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView())violation("source chase entered isolated gait fixture at "+clock.tick());
        if(sprinting()&&(Math.abs(mc.player.getYRot())>.01F||Math.abs(row.get("movementYaw").getAsFloat())>.01F))
            violation("QA steering yaw changed at "+clock.tick());
        var nativeAction=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_ACTION);
        row.addProperty("nativeAction",nativeAction.clipId());row.addProperty("nativeActionSeconds",nativeAction.seconds(mc.level.getGameTime()));
        var sampled=PlayerMotionController.pose(mc.player,1);var root=sampled.get("Root");
        if(root!=null){row.addProperty("bodyRootX",root.translation().x);row.addProperty("bodyRootY",root.translation().y);row.addProperty("bodyRootZ",root.translation().z);}
        if(standingCharge()&&stage==Stage.JUMP_SPRINT&&"basic_jump_charge".equals(clock.key())
                &&mc.player.onGround()&&mc.player.getDeltaMovement().horizontalDistance()<.001)standingChargeObserved=true;
        if(standingCharge()&&stage==Stage.JUMP_SPRINT&&stageTicks>=24&&stageTicks<20+jumpHoldTicks){
            if(plantedClient==null)plantedClient=mc.player.position();
            maximumChargeClientDrift=Math.max(maximumChargeClientDrift,mc.player.position().subtract(plantedClient).horizontalDistance());
            chargeClientSamples++;
        }
        if(stage==Stage.JUMP_SPRINT&&stageTicks>=20+jumpHoldTicks) {
            if(nativeAction.clipId().equals("basic_land_moving")||nativeAction.clipId().equals("basic_land_light"))movingLandingObserved=true;
            if(!mc.player.onGround()){if(firstJumpAirTick<0)firstJumpAirTick=clock.tick();jumpAirTicks++;}
            else if(firstJumpAirTick>=0&&jumpContactTick<0){jumpContactTick=clock.tick();capturePending=true;}
            row.addProperty("ticksSinceJumpContact",jumpContactTick<0?-1:clock.tick()-jumpContactTick);
            if(!standingCharge()&&jumpContactTick>=0&&mc.player.onGround()&&clock.tick()-jumpContactTick<=30
                    &&(!LandingMotionPolicy.isLanding(nativeAction.clipId())||LandingMotionPolicy.permitsStride(nativeAction.clipId(),nativeAction.seconds(mc.level.getGameTime())))
                    &&!(clock.key().equals("basic_walk")||clock.key().equals("basic_run")||clock.key().startsWith("basic_sprint")))
                violation("landing stole ground stride at "+clock.tick()+": "+clock.key());
        }
        row.addProperty("armor",UtilityPowerSystem.active(mc.player,PowerForm.ARMOR));row.addProperty("onGround",mc.player.onGround());
        row.addProperty("activePower",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower().name());
        double nominal=MotionLibrary.nominalSpeed(clock.key())*mc.player.getScale();row.addProperty("nominalBlocksPerTick",nominal);
        if(previousTick!=null) {
            double dx=position.x-previousTick.get("x").getAsDouble(),dy=position.y-previousTick.get("y").getAsDouble(),dz=position.z-previousTick.get("z").getAsDouble();
            double distance=Math.hypot(dx,dz);row.addProperty("distance",distance);row.addProperty("verticalDistance",dy);
            long delta=clock.tick()-previousTick.get("tick").getAsLong();
            if(previousTick.get("key").getAsString().equals(clock.key())&&clock.looping()&&nominal>0&&distance<4&&delta>0) {
                double expected=Math.min(distance/(nominal*20),delta/20.0*8),actual=clock.current()-previousTick.get("currentSeconds").getAsDouble();
                row.addProperty("expectedPhaseAdvance",expected);row.addProperty("actualPhaseAdvance",actual);
                if(Math.abs(expected-actual)>.00001)violation("distance-clock mismatch at "+clock.tick()+": "+actual+" != "+expected);
                if(actual<-.000001)violation("clock rewound at "+clock.tick());
            }
        }
        // Repeated body/layer/camera samples must leave the committed gait unchanged.
        double before=clock.current();
        PlayerMotionController.pose(mc.player,.8F);PlayerMotionController.pose(mc.player,.2F);PlayerMotionController.pose(mc.player,.8F);
        if(before!=clock.current())violation("render sampling advanced gait at "+clock.tick());
        ticks.add(row);latestTick=clock.tick();previousTick=row;
    }
    private static void violation(String message){violations.add(message);BlacklightNeoMod.LOGGER.error("Continuous gait QA: {}",message);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event){if(active())event.setFOV(60);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if(!active())return;var mc=Minecraft.getInstance();if(mc.player==null)return;
        var actor=mc.player.getPosition((float)event.getPartialTick());
        var direction=new Vec3(5,1.3,4).normalize();var target=actor.add(0,.9,0);
        var eye=target.add(direction.scale(observerDistance));var look=target.subtract(eye);
        ((com.codex.blacklight.mixin.client.PrototypeCameraAccess)event.getCamera()).blacklight$setPosition(eye);
        event.setYaw((float)Math.toDegrees(Math.atan2(-look.x,look.z)));
        event.setPitch((float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));event.setRoll(0);
    }
    @SubscribeEvent public static void beforeFrame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        if(!active()||!serverReady)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        mc.mouseHandler.releaseMouse();
        try {
            recordTick(mc);renderFrame++;captureSequence=null;
            if((stage==Stage.SPRINT||stage==Stage.ARMOR_SPRINT)&&stageTicks>=25)captureSequence=stage.name().toLowerCase(Locale.ROOT);
            if(stage==Stage.JUMP_SPRINT&&stageTicks>=20){
                if(stageTicks<20+jumpHoldTicks)captureSequence="jump_charge";
                else if(!mc.player.onGround())captureSequence="jump_flight";
                else if(jumpContactTick>=0&&latestTick-jumpContactTick<=40)captureSequence="jump_landing";
            }
            int ordinal=captureSequence==null?16:sequenceFrames.getOrDefault(captureSequence,0);
            // Compare early frames with a later close view: armor must finish
            // hardening, and landing smoke must clear before inspecting limbs.
            if(ordinal>=8&&((stage==Stage.SPRINT||stage==Stage.ARMOR_SPRINT)&&stageTicks<75
                    ||"jump_landing".equals(captureSequence)&&latestTick-jumpContactTick<22))captureSequence=null;
            if(ordinal>=16)captureSequence=null;
            observerDistance=ordinal<8?12:5;
            if(captureSequence!=null&&writer.getQueue().remainingCapacity()>0)capturePending=true;
            else if(captureSequence!=null){queueMisses++;captureSequence=null;}
            if(stage==Stage.DONE)capturePending=true;
        }catch(Exception e){fail(e);}
    }
    private static JsonArray vector(Vec3 v){var a=new JsonArray();a.add(v.x);a.add(v.y);a.add(v.z);return a;}
    @SubscribeEvent public static void actualBody(RenderLevelStageEvent event) {
        if(!active()||!serverReady||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        try {
            var renderer=((Map<?,?>)renderersField.get(null)).get(mc.player.getSkin().model());
            // Current rendering uses the renderer directly. Permit a future wrapper
            // only by following its actual parent, never by creating a test model.
            if(!(renderer instanceof BlacklightPlayerRenderer)){
                if(renderer==null)return;var parent=renderer.getClass().getDeclaredField("parent");parent.setAccessible(true);renderer=parent.get(renderer);
            }
            if(!(renderer instanceof BlacklightPlayerRenderer nativeRenderer)||!(nativeRenderer.getModel() instanceof BlacklightPlayerModel model))return;
            if(renderedPlayerField.get(model)!=mc.player)return;
            var matrices=model.matrices();if(matrices==null)return;
            var runtime=runtime(mc);if(runtime==null)return;var clock=(LocomotionClock)gaitField.get(runtime);
            float partial=renderPartialField.getFloat(model);
            var row=new JsonObject();row.addProperty("frame",renderFrame);row.addProperty("stage",stage.name());row.addProperty("stageTick",stageTicks);
            row.addProperty("tick",clock.tick());row.addProperty("partial",partial);row.addProperty("key",clock.key());row.addProperty("sampleSeconds",clock.sampleTime(partial));
            row.addProperty("actualRenderedPlayerId",mc.player.getId());row.addProperty("actualModelIdentity",System.identityHashCode(model));row.addProperty("observerDistance",observerDistance);
            row.addProperty("activePower",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getActivePower().name());row.addProperty("armor",UtilityPowerSystem.active(mc.player,PowerForm.ARMOR));
            row.addProperty("nativeAction",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_ACTION).clipId());
            var sample=new JsonObject();var skeleton=PlayerMotionController.skeleton();
            for(String boneName:new String[]{"Root","Head","Arm_R","Hand_R","Tool_R","Arm_L","Hand_L","Tool_L","Leg_R","Leg_L"}){
                var bone=skeleton.searchJointByName(boneName);if(bone==null||bone.getId()>=matrices.length)continue;
                var matrix=matrices[bone.getId()];var values=new float[16];matrix.get(values);var data=new JsonObject();var array=new JsonArray();boolean finite=true;
                for(float value:values){finite&=Float.isFinite(value);array.add(Float.isFinite(value)?value:0);}
                float determinant=matrix.determinant();finite&=Float.isFinite(determinant);data.addProperty("finite",finite);data.addProperty("determinant",Float.isFinite(determinant)?determinant:0);data.add("matrix",array);sample.add(boneName,data);
                if(!finite)violation("non-finite actual rendered "+boneName+" at frame "+renderFrame);
            }
            row.add("actualBodyMatrices",sample);
            double before=clock.current();float high=PlayerMotionController.elapsed(mc.player,.8F),low=PlayerMotionController.elapsed(mc.player,.2F),again=PlayerMotionController.elapsed(mc.player,.8F);
            row.addProperty("elapsedHigh",high);row.addProperty("elapsedLow",low);row.addProperty("elapsedHighRepeated",again);row.addProperty("committedGaitBeforeQueries",before);row.addProperty("committedGaitAfterQueries",clock.current());
            if(high!=again||before!=clock.current())violation("same-tick elapsed query changed actual playback at frame "+renderFrame);
            row.addProperty("elapsedPartialMonotonic",high>=low); // Evidence; reverse source clips are legitimate.
            var anchor=mc.player.getPosition(partial);row.add("actor",vector(anchor));
            var modelToWorld=new Matrix4f().rotationY((float)Math.toRadians(180-BlacklightRenderPose.facing(mc.player,partial))).scale(.9375F*mc.player.getScale());
            for(String side:new String[]{"R","L"}){
                var leg=skeleton.searchJointByName("Leg_"+side);if(leg==null)continue;
                var local=new Matrix4f(matrices[leg.getId()]).transformPosition(new Vector3f(0,.37472F,0));modelToWorld.transformPosition(local);
                row.add("foot_"+side,vector(anchor.add(local.x,local.y,local.z)));
            }
            if(captureSequence!=null){int ordinal=sequenceFrames.getOrDefault(captureSequence,0);row.addProperty("captureSequence",captureSequence);row.addProperty("sequenceFrame",ordinal);row.addProperty("image",captureSequence+"_"+stageTicks+"_frame"+renderFrame+".png");sequenceFrames.put(captureSequence,ordinal+1);}
            frames.add(row);
        }catch(Exception e){fail(e);}
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!active()||!capturePending)return;capturePending=false;var mc=Minecraft.getInstance();
        String name=(captureSequence==null?stage.name().toLowerCase(Locale.ROOT):captureSequence)+"_"+stageTicks+"_frame"+renderFrame+".png";
        try {
            if(writer.isShutdown()||writer.getQueue().remainingCapacity()==0){queueMisses++;return;}
            if(captureNames.add(name)){
                // OpenGL readback stays on the render thread. Encoding, I/O and
                // NativeImage ownership transfer to one bounded background worker.
                var image=Screenshot.takeScreenshot(mc.getMainRenderTarget());
                String sequenceAtCapture=captureSequence;
                try{
                    writer.execute(()->{try(image){image.writeToFile(output.resolve(name));writtenImages.incrementAndGet();if(sequenceAtCapture!=null)writtenSequences.computeIfAbsent(sequenceAtCapture,key->new AtomicInteger()).incrementAndGet();}catch(Exception e){imageError=e.toString();}});
                    submittedImages.incrementAndGet();
                }catch(RejectedExecutionException e){image.close();captureNames.remove(name);queueMisses++;}
            }
        }catch(Exception e){fail(e);}
    }
    private static void finish(Minecraft mc) {
        for(String sequence:jumpOnly()?new String[]{"jump_charge","jump_flight","jump_landing"}:new String[]{"sprint","armor_sprint","jump_charge","jump_flight","jump_landing"}){
            if(sequenceFrames.getOrDefault(sequence,0)<8)violation("fewer than 8 actual rendered body frames for "+sequence);
            if(writtenSequences.getOrDefault(sequence,new AtomicInteger()).get()<8)violation("fewer than 8 actual framebuffer images written for "+sequence);
        }
        for(Stage phase:jumpOnly()?new Stage[]{}:new Stage[]{Stage.SPRINT,Stage.ARMOR_SPRINT}) {
            int count=0,running=0,grounded=0;double distance=0;
            for(var element:ticks) {
                var row=element.getAsJsonObject();if(!row.get("stage").getAsString().equals(phase.name()))continue;
                count++;if(row.get("phase").getAsString().equals("RUN"))running++;
                if(row.get("onGround").getAsBoolean())grounded++;
                if(row.has("distance"))distance+=row.get("distance").getAsDouble();
            }
            if(count<100)violation(phase+" captured only "+count+" committed ticks");
            if(distance<30)violation(phase+" native input did not produce continuous travel: "+distance+" blocks");
            if(running<60)violation(phase+" never established running gait: "+running+" ticks");
            if(grounded<count*.8)violation(phase+" left the flat lane: "+grounded+"/"+count+" grounded");
        }
        if(!standingCharge()&&!movingLandingObserved)violation("native jump did not exercise the moving/light landing action");
        if(standingCharge()&&!standingChargeObserved)violation("native standing jump did not exercise grounded stationary charge");
        if(standingCharge()&&(chargeClientSamples<20||chargeServerSamples<20))violation("insufficient planted charge samples");
        if(standingCharge()&&(maximumChargeClientDrift>.001||maximumChargeServerDrift>.001))
            violation("planted charge drifted under held directional keys/residual velocity: client="+maximumChargeClientDrift+" server="+maximumChargeServerDrift);
        if(jumpAirTicks<5)violation("native Space input did not produce an observed jump: "+jumpAirTicks+" airborne ticks");
        if(jumpContactTick<0)violation("native jump never returned to real ground contact");
        else if(latestTick-jumpContactTick<30)violation("jump QA ended before 30 post-contact ticks");
        completed=true;restore(mc);write(true,null);
        BlacklightNeoMod.LOGGER.info("Continuous native gait QA completed {} committed ticks, {} real frames, {} violations",ticks.size(),frames.size(),violations.size());
        mc.stop();
    }
    private static void restore(Minecraft mc) {
        savedBindings.forEach(KeyMapping::setKey);KeyMapping.resetMapping();
        savedKeys.forEach(KeyMapping::setDown);
        if(mc.player!=null){mc.player.setYRot(previousYaw);mc.player.yRotO=previousYaw;mc.player.setXRot(previousPitch);mc.player.xRotO=previousPitch;}
        if(previousMouseGrabbed)mc.mouseHandler.grabMouse();else mc.mouseHandler.releaseMouse();
        if(previousCamera!=null){mc.options.setCameraType(previousCamera);mc.options.fov().set(previousFov);mc.options.hideGui=previousGui;mc.options.pauseOnLostFocus=previousPause;}
        // Changing STB globals while a worker encodes would corrupt its settings.
        if(writer==null||writer.isTerminated()){
            STBImageWrite.stbi_write_png_compression_level.put(0,previousPngLevel);
            STBImageWrite.stbi_write_force_png_filter.put(0,previousPngFilter);
        }
        mc.updateTitle();
    }
    private static void fail(Exception e) {
        if(disabled)return;disabled=true;BlacklightNeoMod.LOGGER.error("Continuous gait QA failed",e);
        if(writer!=null)writer.shutdown();
        var mc=Minecraft.getInstance();mc.execute(()->{restore(mc);write(false,e.toString());mc.stop();});
    }
    private static synchronized void write(boolean complete,String error) {
        if(output==null)return;
        try {
            var report=new JsonObject();report.addProperty("complete",complete);report.addProperty("source","real native KeyMapping input, packet/physics and Minecraft framebuffer");
            report.addProperty("imageWriteMode","OpenGL readback only on render thread; single bounded asynchronous PNG worker");
            report.addProperty("pngCompressionLevel",0);report.addProperty("pngFilter",0);report.addProperty("imageQueueCapacity",12);report.addProperty("imageQueueMisses",queueMisses);
            report.addProperty("submittedImages",submittedImages.get());report.addProperty("writtenImages",writtenImages.get());report.addProperty("writerDrained",writer!=null&&writer.isTerminated());
            report.addProperty("schema",2);report.addProperty("matrixSource","StandalonePlayerRendering.RENDERERS actual BlacklightPlayerModel.matrices after world rendering; renderedPlayer identity checked");
            report.addProperty("claws",claws());report.addProperty("standingCharge",standingCharge());
            var sequences=new JsonObject();sequenceFrames.forEach(sequences::addProperty);report.add("sequenceFrames",sequences);
            var written=new JsonObject();writtenSequences.forEach((key,count)->written.addProperty(key,count.get()));report.add("sequenceImagesWritten",written);
            report.addProperty("passed",complete&&violations.isEmpty());report.addProperty("epicfightLoaded",ModList.get().isLoaded("epicfight"));report.addProperty("womLoaded",ModList.get().isLoaded("wom"));
            report.addProperty("fixedFov",60);report.addProperty("camera","constant actor-relative observer; own chase disabled by front perspective");
            report.addProperty("controls","native W/sprint; QA player yaw/pitch locked to zero before physics; pointer released and click recapture suppressed");
            report.addProperty("suppressedMouseEvents",suppressedMouseEvents);report.addProperty("suppressedPerspectiveClicks",suppressedPerspectiveClicks);
            report.addProperty("jumpOnly",jumpOnly());report.addProperty("firstJumpAirTick",firstJumpAirTick);report.addProperty("jumpContactTick",jumpContactTick);report.addProperty("jumpAirTicks",jumpAirTicks);
            report.addProperty("jumpHoldTicks",jumpHoldTicks);report.addProperty("movingLandingObserved",movingLandingObserved);
            report.addProperty("standingChargeObserved",standingChargeObserved);
            report.addProperty("chargeClientSamples",chargeClientSamples);report.addProperty("chargeServerSamples",chargeServerSamples);
            report.addProperty("maximumChargeClientDrift",maximumChargeClientDrift);report.addProperty("maximumChargeServerDrift",maximumChargeServerDrift);
            report.addProperty("jumpInput",standingCharge()?"stationary Space charge from tick20 including 12-tick full hold; inject W/D/sprint and residual XZ velocity from tick25; verify client/server planted position, then actual takeoff/contact":"W+sprint140 ticks; Space held from tick20 for configured charge yielding a light impact, then released; actual airborne and real ground contact observed");
            report.add("committedTicks",ticks);report.add("renderFrames",frames);report.add("violations",violations);if(error!=null)report.addProperty("error",error);
            var images=new JsonArray();captureNames.stream().sorted().forEach(images::add);report.add("images",images);
            Path pending=output.resolve("continuous-gait-report.pending.json");Files.writeString(pending,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.move(pending,output.resolve("continuous-gait-report.json"),StandardCopyOption.REPLACE_EXISTING);
            String[] columns={"tick","stage","stageTick","phase","key","x","y","z","distance","currentSeconds","expectedPhaseAdvance","actualPhaseAdvance","playerYaw","playerPitch","movementYaw","observerYaw","yawBeforeInputLock","mouseGrabbed","windowFocused","onGround","armor","cameraType","chaseOwnsView","nativeAction","nativeActionSeconds","ticksSinceJumpContact","bodyRootX","bodyRootY","bodyRootZ"};
            var csv=new StringBuilder(String.join(",",columns)).append('\n');
            for(var element:ticks){var row=element.getAsJsonObject();for(int i=0;i<columns.length;i++){if(i>0)csv.append(',');var value=row.get(columns[i]);if(value!=null)csv.append(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()?value.getAsString():value.toString());}csv.append('\n');}
            Files.writeString(output.resolve("continuous-gait-ticks.csv"),csv);
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Cannot write continuous gait evidence",e);}
    }
}
