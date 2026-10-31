package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;

/** Explicit real-Player/framebuffer QA; root includes this source only in an isolated runClient. */
public final class VehicleViewSmoke {
    private static final String PROPERTY="blacklight.test.vehicleView",SAVE="Military6 Vehicle View QA";
    private record Shot(MilitaryFaction faction,String chassis,String seat,CameraType camera,boolean prototype,boolean mercer) {
        String name(){return faction.id()+"_"+chassis+"_"+seat+"_"+camera.name().toLowerCase(java.util.Locale.ROOT)+(mercer?"_mercer_physical":prototype?"_native":"_skin");}
    }
    private static final List<Shot> SHOTS=shots();
    private static final JsonArray FRAMES=new JsonArray();
    private static boolean registered,creating,initialized,queued,disabled,complete,capture;
    private static volatile boolean serverReady;
    private static volatile String serverFailure;
    private static int index,ticks,wait,vehicleId=-1;
    private static Path output;
    private static JsonObject playerRender;
    private VehicleViewSmoke() {}
    /** Root calls this once from the QA client's setup enqueueWork. */
    public static void init() {
        if(registered||!Boolean.getBoolean(PROPERTY))return;
        registered=true;net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(VehicleViewSmoke.class);
    }
    private static List<Shot> shots() {
        var shots=new ArrayList<Shot>();
        for(var faction:MilitaryFaction.values())for(String chassis:List.of("truck","apc","tank")) {
            for(var camera:CameraType.values())shots.add(new Shot(faction,chassis,"driver",camera,false,false));
            shots.add(new Shot(faction,chassis,"driver",CameraType.THIRD_PERSON_BACK,true,false));
            shots.add(new Shot(faction,chassis,"driver",CameraType.THIRD_PERSON_BACK,true,true));
            if(!chassis.equals("truck")) {
                shots.add(new Shot(faction,chassis,"gunner",CameraType.FIRST_PERSON,true,false));
                shots.add(new Shot(faction,chassis,"gunner",CameraType.THIRD_PERSON_BACK,true,true));
            }
            String other=chassis.equals("truck")?"officer":chassis.equals("apc")?"passenger_1":"commander";
            shots.add(new Shot(faction,chassis,other,CameraType.THIRD_PERSON_BACK,true,false));
            shots.add(new Shot(faction,chassis,other,CameraType.THIRD_PERSON_BACK,true,true));
        }
        return List.copyOf(shots);
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;tick();}
    private static void tick() {
        if(!Boolean.getBoolean(PROPERTY)||disabled||complete)return;
        var mc=Minecraft.getInstance();var game=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if(!game.getFileName().toString().startsWith("run-military6-client-")){fail("Refused game directory: "+game);return;}
        output=game.resolve("vehicle-view-evidence");
        try {
            if(!creating&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.screen.onClose();return;}
            if(!creating&&mc.screen instanceof TitleScreen) {
                if(Files.exists(game.resolve("saves").resolve(SAVE)))throw new IllegalStateException("Use a new QA directory; existing QA save is retained");
                creating=true;mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
                mc.createWorldOpenFlows().createFreshLevel(SAVE,new net.minecraft.world.level.LevelSettings(SAVE,GameType.CREATIVE,false,
                        net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(7716L,false,false),
                        registries->registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            var server=mc.getSingleplayerServer();
            if(server==null||mc.level==null||mc.player==null)return;
            if(!server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(SAVE))throw new IllegalStateException("Refused non-QA save");
            if(serverFailure!=null)throw new IllegalStateException(serverFailure);
            for(var key:mc.options.keyMappings)key.setDown(false);
            mc.mouseHandler.releaseMouse();mc.options.pauseOnLostFocus=false;
            mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
            if(!initialized) {Files.createDirectories(output);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);initialized=true;wait=80;}
            mc.getToasts().clear();
            if(wait>0){wait--;return;}
            if(index>=SHOTS.size()){complete=true;write(null);mc.stop();return;}
            var shot=SHOTS.get(index);
            if(com.codex.blacklight.client.tuning.MercerAppearance.active(mc.player)!=shot.mercer()
                    &&com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).isPrototypeMode())com.codex.blacklight.client.tuning.MercerAppearance.toggle();
            if(!queued) {
                queued=true;serverReady=false;ticks=0;playerRender=null;mc.options.hideGui=false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                for(int n=0;n<shot.camera().ordinal();n++)KeyMapping.click(mc.options.keyTogglePerspective.getKey());
                var uuid=mc.player.getUUID();double x=index*48;
                server.execute(()->{
                    try {
                        var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                        for(int dx=-18;dx<=18;dx++)for(int z=-18;z<=24;z++)level.setBlockAndUpdate(new BlockPos((int)x+dx,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        for(int y=101;y<=108;y++)for(int dx=-1;dx<=1;dx++) {
                            level.setBlockAndUpdate(new BlockPos((int)x+dx-5,y,18),Blocks.BLUE_CONCRETE.defaultBlockState());
                            level.setBlockAndUpdate(new BlockPos((int)x+dx+5,y,18),Blocks.YELLOW_CONCRETE.defaultBlockState());
                        }
                        var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("Missing real server Player");
                        p.stopRiding();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.getInventory().clearContent();
                        int slot=0;for(String faction:new String[]{"military","blackwatch"})for(String chassis:new String[]{"truck","apc","tank"})
                            p.getInventory().setItem(slot++,new net.minecraft.world.item.ItemStack(VehicleRegistry.item(faction+"_"+chassis+"_chassis")));
                        for(String item:new String[]{"autocannon_round","tank_he_shell","tank_ap_shell"})p.getInventory().setItem(slot++,new net.minecraft.world.item.ItemStack(VehicleRegistry.item(item),16));
                        p.getInventory().selected=8;p.containerMenu.broadcastChanges();
                        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(shot.prototype());
                        com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                        p.connection.teleport(x,101,0,0,0);
                        var vehicle=VehicleRegistry.type(shot.faction(),shot.chassis()).create(level);
                        if(vehicle==null)throw new IllegalStateException("Missing actual vehicle type");
                        vehicle.setPos(x,101,0);vehicle.setYRot(0);vehicle.setPlayerOwner(uuid);
                        if(!level.addFreshEntity(vehicle)||!p.startRiding(vehicle,true))throw new IllegalStateException("Real Player failed to board");
                        if(!shot.seat().equals("driver")&&!vehicle.switchSeat(p,shot.seat()))throw new IllegalStateException("Real Player failed to switch seat");
                        vehicle.syncVehicleState();vehicleId=vehicle.getId();serverReady=true;
                    }catch(Exception error){serverFailure=error.toString();}
                });
                return;
            }
            if(++ticks>400)throw new IllegalStateException("Timed out waiting for synchronized vehicle/Player/render");
            if(!serverReady||!(mc.player.getVehicle() instanceof MilitaryVehicle vehicle)||vehicle.getId()!=vehicleId||!shot.seat().equals(vehicle.seatOf(mc.player.getUUID())))return;
            if(ticks>=40&&mc.options.getCameraType()==shot.camera())capture=true;
        }catch(Exception error){fail(error.toString());}
    }
    @SubscribeEvent public static void player(RenderPlayerEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(!Boolean.getBoolean(PROPERTY)||!queued||event.getEntity()!=mc.player||!(event.getEntity().getVehicle() instanceof MilitaryVehicle))return;
        var model=event.getRenderer().getModel();playerRender=new JsonObject();
        playerRender.addProperty("renderer",event.getRenderer().getClass().getName());playerRender.addProperty("model",model.getClass().getName());
        playerRender.addProperty("rightLegXRot",model.rightLeg.xRot);playerRender.addProperty("leftLegXRot",model.leftLeg.xRot);
        playerRender.addProperty("headVisible",model.head.visible);playerRender.addProperty("headY",model.head.y);
        playerRender.addProperty("sampledAfterSharedPoseRestore",true);
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;frame();}
    private static void frame() {
        if(!capture||disabled||complete)return;capture=false;var mc=Minecraft.getInstance();var shot=SHOTS.get(index);
        try {
            if(!(mc.player.getVehicle() instanceof MilitaryVehicle vehicle))throw new IllegalStateException("Player dismounted before frame");
            var camera=mc.gameRenderer.getMainCamera();
            var expected=VehicleCamera.view(vehicle.definition(),shot.seat(),vehicle.position(),vehicle.getYRot(),vehicle.turretYaw(),vehicle.gunPitch(),0,0,shot.camera().isFirstPerson(),shot.camera().isMirrored());
            if(camera.getEntity()!=mc.player||camera.getPosition().distanceTo(expected.position())>.08)throw new IllegalStateException("Real camera differs from vehicle placement: "+camera.getPosition()+" expected "+expected.position());
            if(com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView())throw new IllegalStateException("Personal chase camera retained vehicle ownership");
            if(com.codex.blacklight.client.camera.PrototypeChaseCamera.mouse(0,0))throw new IllegalStateException("Personal chase consumed mounted mouse input");
            if(!shot.camera().isFirstPerson()&&playerRender==null)throw new IllegalStateException("Actual Player render did not complete");
            var drawn=VehicleSeatPose.drawn();
            if(!shot.camera().isFirstPerson()&&(drawn==null||drawn.playerId()!=mc.player.getId()||drawn.vehicleId()!=vehicle.getId()
                    ||Math.abs(drawn.rightLeg()+Math.PI/2)>.001||Math.abs(drawn.leftLeg()+Math.PI/2)>.001||!drawn.headVisible()))throw new IllegalStateException("Actual Player seated draw hook did not run correctly; check mixin registration");
            if(shot.mercer()&&!com.codex.blacklight.client.model.MercerPlayerMesh.active(mc.player))throw new IllegalStateException("Mercer appearance was not actually selected");
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(shot.name()+".png"));}
            var row=new JsonObject();row.addProperty("image",shot.name()+".png");row.addProperty("playerClass",mc.player.getClass().getName());
            row.addProperty("vehicleUuid",vehicle.getUUID().toString());row.addProperty("seat",vehicle.seatOf(mc.player.getUUID()));
            row.addProperty("cameraType",mc.options.getCameraType().name());row.add("camera",vector(camera.getPosition()));row.add("seatEntity",vector(mc.player.position()));
            row.addProperty("cameraYaw",camera.getYRot());row.addProperty("cameraPitch",camera.getXRot());
            row.addProperty("physicalPlayerHeight",mc.player.getBbHeight());row.addProperty("mercerSelected",com.codex.blacklight.client.model.MercerPlayerMesh.active(mc.player));
            row.addProperty("characterDisplayScale",com.codex.blacklight.client.model.MercerPlayerMesh.displayScale(mc.player,0));
            if(!shot.camera().isFirstPerson())row.add("sampledBeforePlayerDraw",new Gson().toJsonTree(drawn));
            row.addProperty("ysmLoaded",net.minecraftforge.fml.ModList.get().isLoaded("yes_steve_model"));
            row.addProperty("dedicatedYsmBridgeSelected",com.codex.blacklight.client.compat.YsmAppearanceBridge.selected(mc.player));
            if(playerRender!=null)row.add("playerRender",playerRender);FRAMES.add(row);index++;queued=false;write(null);
        }catch(Exception error){fail(error.toString());}
    }
    private static JsonArray vector(Vec3 point){var row=new JsonArray();row.add(point.x);row.add(point.y);row.add(point.z);return row;}
    private static void fail(String error){disabled=true;BlacklightNeoMod.LOGGER.error("Vehicle client smoke failed: {}",error);write(error);Minecraft.getInstance().stop();}
    private static void write(String error) {
        if(output==null)return;
        try {
            Files.createDirectories(output);var report=new JsonObject();report.addProperty("complete",complete);report.addProperty("automaticPass",complete&&!disabled&&FRAMES.size()==SHOTS.size());
            report.addProperty("requiresVisualReview",true);report.addProperty("arbitrarySkinRendererCompatibilityVerified",false);
            report.addProperty("source","real integrated-server Player, synchronized seat, production Camera, native F5 queue and GL framebuffer");
            report.addProperty("plannedFrames",SHOTS.size());report.add("frames",FRAMES);if(error!=null)report.addProperty("error",error);
            Files.writeString(output.resolve("vehicle-view-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception errorWriting){BlacklightNeoMod.LOGGER.error("Cannot save vehicle smoke evidence",errorWriting);}
    }
}
