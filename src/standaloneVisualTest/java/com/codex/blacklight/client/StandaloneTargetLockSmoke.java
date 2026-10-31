package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.NativePlayerActionBackend;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

/** Real input/network/framebuffer QA, confined to the new disposable v4 save and isolated game directory. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class StandaloneTargetLockSmoke {
    private static final String PROPERTY="blacklight.test.standaloneLock";
    private enum Stage { SETTLE,ACQUIRE,HOLD,CLEAR,RAPID,REJECT_SETUP,REJECT,REACQUIRE,F5_BACK,F5_FRONT,F5_FIRST,RESPAWN_ACQUIRE,RESPAWN,RESPAWN_CLEAR,DONE }
    private static Stage stage=Stage.SETTLE;
    private static boolean initialized,disabled,completed;
    private static volatile boolean serverReady;
    private static volatile int fixtureTarget=-1,fixtureNeighbor=-1,fixtureGlowing=-1,serverLock=-1;
    private static int stageTicks,rapidAcquireRequest,rapidClearRequest;
    private static Path output;
    private static CameraType originalCamera;
    private static boolean originalGui,originalPause,originalMouse;
    private static float originalYaw,originalPitch;
    private static Object respawnOwner;
    private static final Map<KeyMapping,Boolean> originalKeys=new IdentityHashMap<>();
    private static final JsonArray clientTicks=new JsonArray(),serverTicks=new JsonArray(),views=new JsonArray(),violations=new JsonArray();
    private static final LinkedHashSet<String> captures=new LinkedHashSet<>();
    private static String capture;
    private StandaloneTargetLockSmoke() { }
    private static boolean active(){return initialized&&!disabled&&!completed;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre event) {
        if(active()){event.setCanceled(true);Minecraft.getInstance().mouseHandler.releaseMouse();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void input(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        if(!active())return;var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();
        for(var key:originalKeys.keySet())key.setDown(false);
        if(mc.player!=null){mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(disabled||completed||System.getProperty(PROPERTY,"").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(server==null||mc.level==null||mc.player==null)return;
        var save=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var game=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if(!save.getFileName().toString().equals("Blacklight Standalone QA v4")||!game.getFileName().toString().equals("run-native-qa")) {
            disabled=true;BlacklightNeoMod.LOGGER.error("Target lock QA refused save/game directory {} / {}",save,game);return;
        }
        try {
            if(!initialized){setup(mc);return;}
            record(mc);if(!serverReady&&stage!=Stage.RESPAWN&&stage!=Stage.RESPAWN_CLEAR)return;
            stageTicks++;
            if(stageTicks>160)throw new IllegalStateException("Timed out in target QA stage "+stage);
            switch(stage) {
                case SETTLE -> {if(stageTicks>=80&&ClientTargetLock.active()&&mc.level.getEntity(fixtureTarget)!=null){tab(mc);advance(Stage.ACQUIRE);}}
                case ACQUIRE -> {if(locked(mc)){advance(Stage.HOLD);capture="locked_first_person";}}
                case HOLD -> {
                    require(locked(mc),"accepted lock vanished before second Tab");
                    if(stageTicks>=30){tab(mc);advance(Stage.CLEAR);}
                }
                case CLEAR -> {if(cleared(mc)){
                    capture="cleared_by_second_tab";
                    tab(mc);rapidAcquireRequest=ClientTargetLock.requestId();
                    tab(mc);rapidClearRequest=ClientTargetLock.requestId();
                    require(ClientTargetLock.target()==null,"second pending Tab retained prediction");advance(Stage.RAPID);
                }}
                case RAPID -> {if(stageTicks>=20&&cleared(mc)){
                    boolean ignored=ClientTargetLock.acknowledgementTrace().stream().anyMatch(a->a.requestId()==rapidAcquireRequest&&!a.applied());
                    require(ignored,"rapid clear did not ignore the late acquire acknowledgement");
                    require(ClientTargetLock.acknowledgementTrace().stream().anyMatch(a->a.requestId()==rapidClearRequest&&a.applied()&&a.acceptedId()==-1),"rapid clear acknowledgement missing");
                    capture="late_acquire_kept_cleared";advance(Stage.REJECT_SETUP);serverReady=false;
                    var uuid=mc.player.getUUID();server.execute(()->{
                        var p=server.getPlayerList().getPlayer(uuid);
                        // Deliberately retain the client's last mode snapshot, as during
                        // transit, so a real Tab request reaches server validation.
                        com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).setPrototypeMode(false);serverReady=true;
                    });
                }}
                case REJECT_SETUP -> {int before=ClientTargetLock.requestId();tab(mc);require(ClientTargetLock.requestId()>before,"rejection fixture did not issue a Tab request");advance(Stage.REJECT);}
                case REJECT -> {if(!ClientTargetLock.pending()&&ClientTargetLock.requestedId()==-1){
                    require(ClientTargetLock.acknowledgementTrace().stream().anyMatch(a->a.requestId()==ClientTargetLock.requestId()&&a.applied()&&a.acceptedId()==-1),"server rejection acknowledgement absent");
                    capture="server_rejected_stale_mode";advance(Stage.REACQUIRE);serverReady=false;var uuid=mc.player.getUUID();
                    server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);serverReady=true;});
                }}
                case REACQUIRE -> {
                    if(stageTicks==2)tab(mc);
                    if(stageTicks>=4&&locked(mc)){f5(mc);advance(Stage.F5_BACK);}
                }
                case F5_BACK -> {if(stageTicks>=12){checkPerspective(mc,CameraType.THIRD_PERSON_BACK);capture="f5_back_locked";f5(mc);advance(Stage.F5_FRONT);}}
                case F5_FRONT -> {if(stageTicks>=12){checkPerspective(mc,CameraType.THIRD_PERSON_FRONT);capture="f5_front_locked";f5(mc);advance(Stage.F5_FIRST);}}
                case F5_FIRST -> {if(stageTicks>=12){checkPerspective(mc,CameraType.FIRST_PERSON);capture="f5_first_locked";advance(Stage.RESPAWN_ACQUIRE);}}
                case RESPAWN_ACQUIRE -> {if(stageTicks>=3&&locked(mc)){
                    respawnOwner=mc.player;advance(Stage.RESPAWN);serverReady=false;var uuid=mc.player.getUUID();
                    server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);p.kill();});
                }}
                case RESPAWN -> {
                    if(mc.player==respawnOwner&&mc.player.isDeadOrDying()) {mc.player.respawn();mc.setScreen(null);advance(Stage.RESPAWN_CLEAR);}
                }
                case RESPAWN_CLEAR -> {
                    if(mc.player!=respawnOwner){
                        ClientTargetLock.sync();
                        if(!ClientTargetLock.pending()&&ClientTargetLock.requestedId()==-1&&com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.TARGET_LOCK_ID)==-1){
                            require(ClientTargetLock.target()==null,"respawn restored old explicit target");capture="respawn_cleared_lock";stage=Stage.DONE;
                        }
                    }
                }
                case DONE -> { }
            }
        }catch(Exception e){fail(e);}
    }
    private static void setup(Minecraft mc)throws Exception {
        output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();Files.createDirectories(output);
        originalCamera=mc.options.getCameraType();originalGui=mc.options.hideGui;originalPause=mc.options.pauseOnLostFocus;originalMouse=mc.mouseHandler.isMouseGrabbed();
        originalYaw=mc.player.getYRot();originalPitch=mc.player.getXRot();
        for(var key:List.of(mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift,mc.options.keySprint,
                mc.options.keyAttack,mc.options.keyUse,mc.options.keyTogglePerspective,ClientClawEvents.targetLockKey()))originalKeys.put(key,key.isDown());
        mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);mc.mouseHandler.releaseMouse();
        initialized=true;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        server.execute(()->{
            try {
                var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=-6;x<=6;x++)for(int z=-6;z<=18;z++){
                    level.setBlockAndUpdate(new BlockPos(x,250,z),Blocks.GRAY_CONCRETE.defaultBlockState());
                    for(int y=251;y<=255;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                var p=server.getPlayerList().getPlayer(uuid);
                // Remove old fixture actors only inside this disposable lane.
                for(var old:level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(-20,240,-20,20,264,40)))
                    if(!(old instanceof net.minecraft.world.entity.player.Player))old.discard();
                var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.NONE);state.activate(PowerForm.NONE);state.addBiomass(1000);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS,0);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.POWER_MORPH,PowerMorph.NONE);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.POWER_MORPH);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,com.codex.blacklight.combat.WeaponVisual.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
                NativePlayerActionBackend.clear(p);com.codex.blacklight.combat.CombatTargeting.requestLock(p,-1);
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.getInventory().clearContent();
                p.setRespawnPosition(Level.OVERWORLD,new BlockPos(0,251,0),0,true,false);
                p.connection.teleport(.5,251,.5,0,0);p.setDeltaMovement(0,0,0);
                var zombie=new Zombie(level);zombie.setNoAi(true);zombie.setPersistenceRequired();zombie.setCustomName(Component.literal("QA 锁定目标"));
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
                zombie.moveTo(.5,251,8.5,180,0);level.addFreshEntity(zombie);fixtureTarget=zombie.getId();
                var neighbor=new Zombie(level);neighbor.setNoAi(true);neighbor.setPersistenceRequired();
                neighbor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
                neighbor.setCustomName(Component.literal("QA 普通邻居"));neighbor.moveTo(-3.5,251,8.5,180,0);level.addFreshEntity(neighbor);fixtureNeighbor=neighbor.getId();
                var glowing=new Zombie(level);glowing.setNoAi(true);glowing.setPersistenceRequired();glowing.setGlowingTag(true);
                glowing.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
                glowing.setCustomName(Component.literal("QA 原版发光"));glowing.moveTo(4.5,251,8.5,180,0);level.addFreshEntity(glowing);fixtureGlowing=glowing.getId();serverReady=true;
            }catch(Exception e){fail(e);}
        });
    }
    private static void advance(Stage next){stage=next;stageTicks=0;}
    private static void tab(Minecraft mc) {
        var key=ClientClawEvents.targetLockKey();int code=key.getKey().getValue();
        key.setDown(true);MinecraftForge.EVENT_BUS.post(new InputEvent.Key(code,0,GLFW.GLFW_PRESS,0));
        key.setDown(false);MinecraftForge.EVENT_BUS.post(new InputEvent.Key(code,0,GLFW.GLFW_RELEASE,0));
    }
    private static void f5(Minecraft mc){KeyMapping.click(mc.options.keyTogglePerspective.getKey());}
    private static boolean locked(Minecraft mc){return !ClientTargetLock.pending()&&ClientTargetLock.requestedId()==fixtureTarget
            &&ClientTargetLock.target()!=null&&com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.TARGET_LOCK_ID)==fixtureTarget;}
    private static boolean cleared(Minecraft mc){return !ClientTargetLock.pending()&&ClientTargetLock.requestedId()==-1&&ClientTargetLock.target()==null
            &&com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.TARGET_LOCK_ID)==-1;}
    private static void require(boolean condition,String message){if(!condition){violations.add(message);BlacklightNeoMod.LOGGER.error("Target lock QA: {}",message);}}
    private static void checkPerspective(Minecraft mc,CameraType expected) {
        require(mc.options.getCameraType()==expected,"native F5 expected "+expected+" but remained "+mc.options.getCameraType());
        require(locked(mc),"perspective change lost accepted target");
    }
    private static synchronized void serverRow(JsonObject row){serverTicks.add(row);}
    private static void record(Minecraft mc) {
        var row=new JsonObject();row.addProperty("stage",stage.name());row.addProperty("stageTick",stageTicks);row.addProperty("gameTick",mc.level.getGameTime());
        row.addProperty("requestId",ClientTargetLock.requestId());row.addProperty("desiredId",ClientTargetLock.requestedId());row.addProperty("acknowledgedId",ClientTargetLock.acknowledgedId());
        row.addProperty("pending",ClientTargetLock.pending());row.addProperty("attachmentId",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.TARGET_LOCK_ID));row.addProperty("serverId",serverLock);
        row.addProperty("cameraType",mc.options.getCameraType().name());row.addProperty("playerYaw",mc.player.getYRot());row.addProperty("bodyYaw",mc.player.yBodyRot);
        Float facing=PrototypeAnimationController.movementFacing(mc.player,1);if(facing!=null)row.addProperty("movementFacing",facing);
        row.addProperty("localPlayerIdentity",System.identityHashCode(mc.player));clientTicks.add(row);
        var target=mc.level.getEntity(fixtureTarget);
        if(target!=null){
            boolean expected=ClientTargetLock.target()==target;
            row.addProperty("nativeOutline",mc.shouldEntityAppearGlowing(target));
            row.addProperty("entityGlowingFlag",target.isCurrentlyGlowing());
            row.addProperty("originalTeamColor",target.getTeamColor());
            require(mc.shouldEntityAppearGlowing(target)==expected,"native outline did not follow current lock");
            require(!target.isCurrentlyGlowing(),"target lock mutated the entity glowing flag");
        }
        var neighbor=mc.level.getEntity(fixtureNeighbor);
        if(neighbor!=null)require(!mc.shouldEntityAppearGlowing(neighbor),"ordinary neighbor inherited target outline");
        var glowing=mc.level.getEntity(fixtureGlowing);
        if(glowing!=null){
            require(mc.shouldEntityAppearGlowing(glowing)&&glowing.isCurrentlyGlowing(),"vanilla glowing entity lost its outline");
            require(glowing.getTeamColor()==0xFFFFFF,"vanilla glowing entity team color changed");
        }
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();String label=stage.name();
        server.execute(()->{
            var p=server.getPlayerList().getPlayer(uuid);if(p==null)return;
            serverLock=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.TARGET_LOCK_ID);var s=new JsonObject();s.addProperty("stage",label);s.addProperty("tick",server.overworld().getGameTime());
            s.addProperty("id",serverLock);s.addProperty("alive",p.isAlive());s.addProperty("prototypeMode",com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).isPrototypeMode());serverRow(s);
        });
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!active())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        try {
            if(capture!=null) {
                String name=capture;capture=null;
                var row=new JsonObject();row.addProperty("image",name+".png");row.addProperty("cameraType",mc.options.getCameraType().name());row.addProperty("stage",stage.name());
                var camera=mc.gameRenderer.getMainCamera();row.add("cameraPosition",vector(camera.getPosition()));row.add("actorEye",vector(mc.player.getEyePosition()));
                row.addProperty("cameraYaw",camera.getYRot());row.addProperty("cameraPitch",camera.getXRot());row.addProperty("playerYaw",mc.player.getYRot());row.addProperty("bodyYaw",mc.player.yBodyRot);
                row.addProperty("chaseOwnsView",com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView());
                var marker=TargetLockHud.lastMarker();if(marker!=null){row.addProperty("markerTarget",marker.targetId());row.addProperty("markerOnScreen",marker.onScreen());row.addProperty("markerInFront",marker.inFront());if(Float.isFinite(marker.x())){row.addProperty("markerX",marker.x());row.addProperty("markerY",marker.y());}}
                row.addProperty("targetId",ClientTargetLock.requestedId());views.add(row);
                var target=mc.level.getEntity(fixtureTarget);
                if(target!=null){row.addProperty("nativeOutline",mc.shouldEntityAppearGlowing(target));row.addProperty("entityGlowingFlag",target.isCurrentlyGlowing());}
                if(captures.add(name))try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name+".png"));}
                write(false,null);
            }
            if(stage==Stage.DONE){completed=true;restore(mc);write(true,null);BlacklightNeoMod.LOGGER.info("Native target/F5 QA complete: {} violations",violations.size());mc.stop();}
        }catch(Exception e){fail(e);}
    }
    private static JsonArray vector(Vec3 point){var r=new JsonArray();r.add(point.x);r.add(point.y);r.add(point.z);return r;}
    private static void restore(Minecraft mc) {
        originalKeys.forEach(KeyMapping::setDown);
        if(originalCamera!=null){mc.options.setCameraType(originalCamera);mc.options.hideGui=originalGui;mc.options.pauseOnLostFocus=originalPause;}
        if(mc.player!=null){mc.player.setYRot(originalYaw);mc.player.setXRot(originalPitch);}
        if(originalMouse)mc.mouseHandler.grabMouse();else mc.mouseHandler.releaseMouse();
        ClientTargetLock.clear();
    }
    private static void fail(Exception e) {
        if(disabled)return;disabled=true;BlacklightNeoMod.LOGGER.error("Native target lock QA failed",e);
        var mc=Minecraft.getInstance();mc.execute(()->{restore(mc);write(false,e.toString());mc.stop();});
    }
    private static synchronized void write(boolean complete,String error) {
        if(output==null)return;
        try {
            var report=new JsonObject();report.addProperty("complete",complete);report.addProperty("passed",complete&&violations.isEmpty());
            report.addProperty("source","NeoForge InputEvent.Key through real Tab handler, native F5 KeyMapping queue, real packet ACK/server attachment and framebuffer");
            report.addProperty("targetId",fixtureTarget);report.addProperty("rapidAcquireRequest",rapidAcquireRequest);report.addProperty("rapidClearRequest",rapidClearRequest);
            report.add("clientTicks",clientTicks);report.add("serverTicks",serverTicks);report.add("views",views);report.add("violations",violations);
            var ack=new JsonArray();for(var a:ClientTargetLock.acknowledgementTrace()){var item=new JsonObject();item.addProperty("requestId",a.requestId());item.addProperty("acceptedId",a.acceptedId());item.addProperty("applied",a.applied());item.addProperty("gameTick",a.gameTick());ack.add(item);}report.add("acknowledgements",ack);
            if(error!=null)report.addProperty("error",error);
            var pending=output.resolve("native-target-lock.pending.json");Files.writeString(pending,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.move(pending,output.resolve("native-target-lock-report.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Cannot save native target lock QA evidence",e);}
    }
}
