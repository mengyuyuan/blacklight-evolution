package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.combat.LocalWeaponMotion;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.combat.WeaponVisual;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;

/** Opt-in real-frame QA. Only a disposable copied world with the explicit QA prefix may be mutated. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class StandaloneVisualSmoke {
    private record Shot(String name,PowerForm form,PowerForm utility,WeaponMove move,float seconds,boolean charge,boolean firstPerson,boolean editor,float pitch,CameraType camera,boolean nativeCamera) {
        Shot(String name,PowerForm form,PowerForm utility,WeaponMove move,float seconds,boolean charge,boolean firstPerson,boolean editor){this(name,form,utility,move,seconds,charge,firstPerson,editor,0,null,false);}
    }
    private static final List<Shot> SHOTS=List.of(
            new Shot("01_unarmed_idle",PowerForm.NONE,null,null,.6F,false,false,false),
            new Shot("02_claws_idle",PowerForm.CLAWS,null,null,.6F,false,false,false),
            new Shot("03_claws_attack",PowerForm.CLAWS,null,WeaponMove.CLAW_AUTO1,.28F,false,false,false),
            new Shot("04_blade_idle",PowerForm.BLADE,null,null,.6F,false,false,false),
            new Shot("05_blade_attack",PowerForm.BLADE,null,WeaponMove.BLADE_CUT,.31F,false,false,false),
            new Shot("06_hammerfists_idle",PowerForm.HAMMERFISTS,null,null,.6F,false,false,false),
            new Shot("07_hammerfists_charge",PowerForm.HAMMERFISTS,null,WeaponMove.HAMMER_QUAKE,.35F,true,false,false),
            new Shot("08_whipfist_idle",PowerForm.WHIPFIST,null,null,.6F,false,false,false),
            new Shot("09_whipfist_attack",PowerForm.WHIPFIST,null,WeaponMove.WHIP_LASH,.48F,false,false,false),
            new Shot("10_muscle_mass_idle",PowerForm.MUSCLE_MASS,null,null,.6F,false,false,false),
            new Shot("11_armor_idle",PowerForm.NONE,PowerForm.ARMOR,null,.6F,false,false,false),
            new Shot("12_armor_blade",PowerForm.BLADE,PowerForm.ARMOR,null,.6F,false,false,false),
            new Shot("13_shield_idle",PowerForm.NONE,PowerForm.SHIELD,null,.6F,false,false,false),
            new Shot("14_first_person_claws",PowerForm.CLAWS,null,null,.6F,false,true,false),
            new Shot("15_first_person_blade",PowerForm.BLADE,null,null,.6F,false,true,false),
            new Shot("16_first_person_armor",PowerForm.NONE,PowerForm.ARMOR,null,.6F,false,true,false),
            new Shot("17_first_person_shield",PowerForm.NONE,PowerForm.SHIELD,null,.6F,false,true,false),
            new Shot("18_native_animation_editor",PowerForm.CLAWS,null,null,2.03F,false,false,true),
            new Shot("19_walk_phase_a",PowerForm.NONE,null,null,.12F,false,false,false),
            new Shot("20_walk_phase_b",PowerForm.NONE,null,null,.42F,false,false,false),
            new Shot("21_sprint_phase_a",PowerForm.NONE,null,null,.10F,false,false,false),
            new Shot("22_sprint_phase_b",PowerForm.NONE,null,null,.35F,false,false,false),
            new Shot("23_wall_run",PowerForm.NONE,null,null,.20F,false,false,false),
            new Shot("24_wall_climb",PowerForm.NONE,null,null,.25F,false,false,false),
            new Shot("25_armor_wall_run",PowerForm.NONE,PowerForm.ARMOR,null,.20F,false,false,false),
            new Shot("26_blade_grab_start",PowerForm.BLADE,null,null,0F,false,false,false),
            new Shot("27_blade_throw_return",PowerForm.BLADE,null,null,.70F,false,false,false),
            new Shot("28_blade_throw_between_keys",PowerForm.BLADE,null,null,.586F,false,false,false),
            new Shot("29_native_front",PowerForm.NONE,null,null,.6F,false,false,false,0,CameraType.THIRD_PERSON_FRONT,true),
            new Shot("30_native_back",PowerForm.NONE,null,null,.6F,false,false,false,0,CameraType.THIRD_PERSON_BACK,true),
            fps("31_blade_live_idle",PowerForm.BLADE,null,null,.6F,0),
            fps("32_blade_look_down",PowerForm.BLADE,null,null,.6F,60),
            fps("33_blade_look_up",PowerForm.BLADE,null,null,.6F,-60),
            fps("34_blade_attack",PowerForm.BLADE,null,WeaponMove.BLADE_CUT,.31F,0),
            fps("35_blade_attack_down",PowerForm.BLADE,null,WeaponMove.BLADE_CUT,.31F,60),
            fps("36_blade_attack_up",PowerForm.BLADE,null,WeaponMove.BLADE_CUT,.31F,-60),
            fps("37_claws_look_down",PowerForm.CLAWS,null,null,.6F,60),
            fps("38_claws_look_up",PowerForm.CLAWS,null,null,.6F,-60),
            fps("39_claws_attack_down",PowerForm.CLAWS,null,WeaponMove.CLAW_AUTO1,.28F,60),
            fps("40_claws_attack_up",PowerForm.CLAWS,null,WeaponMove.CLAW_AUTO1,.28F,-60),
            fps("41_armor_look_down",PowerForm.NONE,PowerForm.ARMOR,null,.6F,60),
            fps("42_armor_look_up",PowerForm.NONE,PowerForm.ARMOR,null,.6F,-60),
            fps("43_armor_attack_down",PowerForm.NONE,PowerForm.ARMOR,WeaponMove.MUSCLE_JAB,.25F,60),
            fps("44_armor_attack_up",PowerForm.NONE,PowerForm.ARMOR,WeaponMove.MUSCLE_JAB,.25F,-60),
            fps("45_shield_look_down",PowerForm.NONE,PowerForm.SHIELD,null,.6F,60),
            fps("46_shield_look_up",PowerForm.NONE,PowerForm.SHIELD,null,.6F,-60),
            fps("47_shield_attack_down",PowerForm.NONE,PowerForm.SHIELD,WeaponMove.MUSCLE_JAB,.25F,60),
            fps("48_shield_attack_up",PowerForm.NONE,PowerForm.SHIELD,WeaponMove.MUSCLE_JAB,.25F,-60),
            new Shot("49_native_back_orbit_180",PowerForm.NONE,null,null,.6F,false,false,false,0,CameraType.THIRD_PERSON_BACK,true),
            fps("50_hammer_native_idle",PowerForm.HAMMERFISTS,null,null,.6F,0),
            fps("51_hammer_native_attack",PowerForm.HAMMERFISTS,null,WeaponMove.HAMMER_QUAKE,.7F,0),
            fps("52_whip_native_idle",PowerForm.WHIPFIST,null,null,.6F,0),
            fps("53_whip_native_attack",PowerForm.WHIPFIST,null,WeaponMove.WHIP_LASH,.48F,0),
            fps("54_muscle_native_idle",PowerForm.MUSCLE_MASS,null,null,.6F,0),
            fps("55_muscle_native_attack",PowerForm.MUSCLE_MASS,null,WeaponMove.MUSCLE_JAB,.25F,0),
            new Shot("56_vision_baseline",PowerForm.NONE,null,null,.6F,false,false,false),
            new Shot("57_thermal_alignment",PowerForm.NONE,PowerForm.THERMAL_VISION,null,.6F,false,false,false),
            new Shot("58_whip_attachment",PowerForm.WHIPFIST,null,WeaponMove.WHIP_LASH,.45F,false,false,false),
            new Shot("59_hammer_ram_impact",PowerForm.HAMMERFISTS,null,WeaponMove.HAMMER_RAM,1.466667F,false,false,false),
            new Shot("60_hammer_ram_recovery",PowerForm.HAMMERFISTS,null,WeaponMove.HAMMER_RAM,1.95F,false,false,false),
            new Shot("61_hammer_ram_stand",PowerForm.HAMMERFISTS,null,WeaponMove.HAMMER_RAM,2.4F,false,false,false))
            .stream().filter(StandaloneVisualSmoke::selected).toList();
    private static final String PROPERTY="blacklight.test.standaloneVisual";
    private static Path output;
    private static boolean initialized,disabled,completed,queued,capture,preview;
    private static volatile boolean serverReady;
    private static int tick,index,readyAt,stageAt;
    private static float orbitBeforeYaw,orbitBeforeFacing,orbitBeforeControl,orbitMouseY;
    private static CameraType previousCamera;
    private static int previousFov;
    private static boolean previousGui,previousPause,previousMouse;
    private static final java.util.Map<KeyMapping,Boolean> savedKeys=new java.util.IdentityHashMap<>();
    private static final JsonArray captures=new JsonArray();
    private StandaloneVisualSmoke() { }
    private static Shot fps(String name,PowerForm form,PowerForm utility,WeaponMove move,float seconds,float pitch){return new Shot(name,form,utility,move,seconds,false,true,false,pitch,CameraType.FIRST_PERSON,true);}
    private static CameraType camera(Shot shot){return shot.camera()!=null?shot.camera():shot.firstPerson()?CameraType.FIRST_PERSON:wall(shot)!=com.codex.blacklight.movement.WallTraversal.Mode.NONE?CameraType.THIRD_PERSON_BACK:CameraType.THIRD_PERSON_FRONT;}
    private static boolean selected(Shot shot) {
        String filter=System.getProperty("blacklight.test.standaloneVisual.shots","");
        if(filter.isBlank())return true;
        for(String token:filter.split(",")) {
            token=token.trim();
            if(!token.isEmpty()&&(shot.name().equals(token)||shot.name().startsWith(token+"_")))return true;
        }
        return false;
    }
    private static boolean active(){return initialized&&!disabled&&!completed;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void isolate(ClientTickEvent.Pre event) {
        if(!active())return;var client=Minecraft.getInstance();client.mouseHandler.releaseMouse();
        for(var key:savedKeys.keySet()){key.setDown(false);while(key.consumeClick()){} }
        if(index<SHOTS.size()&&queued){
            var shot=SHOTS.get(index);client.options.setCameraType(camera(shot));
            if(client.player!=null&&!(shot.name().equals("49_native_back_orbit_180")&&preview)){client.player.setYRot(180);client.player.yRotO=180;client.player.setXRot(shot.pitch());client.player.xRotO=shot.pitch();
                client.player.yBodyRot=client.player.yBodyRotO=180;client.player.yHeadRot=client.player.yHeadRotO=180;}
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pointer(InputEvent.MouseButton.Pre event){
        if(active()){event.setCanceled(true);Minecraft.getInstance().mouseHandler.releaseMouse();}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(disabled||completed||System.getProperty(PROPERTY,"").isBlank())return;
        Minecraft client=Minecraft.getInstance();
        var server=client.getSingleplayerServer();
        if(server==null||client.player==null||client.level==null)return;
        var savePath=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        if(!savePath.getFileName().toString().startsWith("Blacklight Standalone QA")) {
            BlacklightNeoMod.LOGGER.error("Standalone visual QA refused non-QA save path: {}",savePath);
            disabled=true;return;
        }
        tick++;
        if(!initialized) {
            try{output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath();Files.createDirectories(output);}
            catch(Exception e){fail(e);return;}
            if(ModList.get().isLoaded("epicfight")||ModList.get().isLoaded("wom")) {fail(new IllegalStateException("QA classpath includes an unwanted combat framework"));return;}
            previousCamera=client.options.getCameraType();previousFov=client.options.fov().get();previousGui=client.options.hideGui;previousPause=client.options.pauseOnLostFocus;
            previousMouse=client.mouseHandler.isMouseGrabbed();
            for(var key:client.options.keyMappings)savedKeys.put(key,key.isDown());
            client.mouseHandler.releaseMouse();
            client.getWindow().setTitle("Blacklight · 自动第一人称 QA（请勿操作测试窗口）");
            client.options.pauseOnLostFocus=false;
            client.options.hideGui=true;client.options.fov().set(60);client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            var uuid=client.player.getUUID();
            server.execute(()->{
                try {
                    var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                    for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
                        level.setBlockAndUpdate(new BlockPos(x,250,z),((x+z)&1)==0?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.POLISHED_ANDESITE.defaultBlockState());
                        for(int y=251;y<258;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("QA player missing");
                    p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();
                    p.connection.teleport(.5,251,.5,180,0);p.setDeltaMovement(0,0,0);p.yBodyRot=p.yBodyRotO=180;p.yHeadRot=p.yHeadRotO=180;
                    serverReady=true;
                }catch(Exception e){fail(e);}
            });
            initialized=true;readyAt=tick+100;return;
        }
        if(!serverReady||tick<readyAt)return;
        if(index>=SHOTS.size()) {finish(client);return;}
        var shot=SHOTS.get(index);
        if(!queued) {
            PlayerMotionController.clearPreview(client.player);
            if(client.screen instanceof com.codex.blacklight.client.screen.AnimationTunerScreen)client.setScreen(null);
            // Minecraft suppresses the entire RenderHand pipeline while F1 hides the HUD.
            client.options.hideGui=!shot.editor()&&!shot.firstPerson();client.options.fov().set(shot.firstPerson()?70:60);
            client.options.setCameraType(camera(shot));
            var uuid=client.player.getUUID();serverReady=false;
            server.execute(()->{
                try{var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("QA player missing");configure(p,shot);serverReady=true;}
                catch(Exception e){fail(e);}
            });
            try{
                var states=PrototypeAnimationController.class.getDeclaredField("STATES");states.setAccessible(true);
                ((java.util.Map<?,?>)states.get(null)).remove(client.player);
            }catch(ReflectiveOperationException e){fail(e);return;}
            queued=true;preview=false;stageAt=tick+30;return;
        }
        if(tick<stageAt||!serverReady)return;
        if(client.player.getData(ModAttachments.PLAYER_STATE).getActivePower()!=shot.form())return;
        if(!preview) {
            client.options.setCameraType(camera(shot));
            String key=key(shot);
            PlayerMotionController.preview(client.player,key,shot.seconds());
            PlayerMotionController.seekPreview(client.player,shot.seconds());
            if(shot.name().equals("49_native_back_orbit_180")) {
                orbitBeforeYaw=client.player.getYRot();orbitBeforeFacing=com.codex.blacklight.client.animation.BlacklightRenderPose.facing(client.player,0);
                orbitBeforeControl=com.codex.blacklight.client.camera.PrototypeChaseCamera.controlYaw();
                orbitMouseY=-client.gameRenderer.getMainCamera().getXRot()/.15F;
                if(!com.codex.blacklight.client.camera.PrototypeChaseCamera.mouse(1200,orbitMouseY))throw new IllegalStateException("Native BACK orbit mouse was not owned");
            }
            if(shot.editor())client.setScreen(new com.codex.blacklight.client.screen.AnimationTunerScreen());
            preview=true;stageAt=tick+15;return;
        }
        if(shot.move()!=null)client.player.setData(ModAttachments.WEAPON_VISUAL,new WeaponVisual(shot.move().ordinal(),client.level.getGameTime(),180,shot.charge(),shot.seconds()));
        if(tick>=stageAt)capture=true;
    }
    private static String key(Shot shot) {
        if(shot.name().equals("26_blade_grab_start"))return "grab_blade";
        if(shot.name().equals("27_blade_throw_return")||shot.name().equals("28_blade_throw_between_keys"))return "grab_blade_throw";
        if(shot.name().contains("walk_phase"))return "basic_walk";
        if(shot.name().contains("sprint_phase"))return "basic_sprint";
        if(wall(shot)==com.codex.blacklight.movement.WallTraversal.Mode.RUN)return "basic_wall_run_up";
        if(wall(shot)==com.codex.blacklight.movement.WallTraversal.Mode.CLIMB)return "basic_wall_climb_up";
        if(shot.editor())return "charged_jump";
        if(shot.move()==null)return shot.firstPerson()?com.codex.blacklight.client.animation.FirstPersonPose.idle(shot.form()):"basic_idle";
        return shot.charge()?LocalWeaponMotion.find(shot.move().id).flatMap(LocalWeaponMotion.Definition::charge).map(LocalWeaponMotion.Charge::animation).orElseThrow():shot.move().id;
    }
    private static void configure(ServerPlayer p,Shot shot) {
        if(shot.name().equals("56_vision_baseline")){
            for(double x:new double[]{-1.2,1.8}){
                var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(p.serverLevel());
                sheep.setPos(x,251,2.5);sheep.setNoAi(true);sheep.setPersistenceRequired();p.serverLevel().addFreshEntity(sheep);
            }
        }
        var state=p.getData(ModAttachments.PLAYER_STATE);if(!state.isInfected())state.infect();
        state.setPrototypeMode(true);for(var form:PowerForm.values())state.unlock(form);state.activate(shot.form());state.addBiomass(1000);state.addEvolutionPoints(100000);
        for(var upgrade:ClawUpgrade.values())state.buyClawUpgrade(upgrade);
        for(var upgrade:MovementUpgrade.values())state.buyMovementUpgrade(upgrade);
        p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
        p.setData(ModAttachments.UTILITY_POWERS,shot.utility()==null?0:shot.utility().bit());p.syncData(ModAttachments.UTILITY_POWERS);
        p.setData(ModAttachments.UTILITY_STATE,new UtilityState(UtilityState.SHIELD_CAPACITY,0,p.level().getGameTime()-200,0,-1000));p.syncData(ModAttachments.UTILITY_STATE);
        p.setData(ModAttachments.POWER_MORPH,PowerMorph.NONE);p.syncData(ModAttachments.POWER_MORPH);
        p.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);
        p.setDeltaMovement(0,0,0);p.connection.teleport(.5,251,.5,180,shot.pitch());p.yBodyRot=p.yBodyRotO=180;p.yHeadRot=p.yHeadRotO=180;
        if(wall(shot)!=com.codex.blacklight.movement.WallTraversal.Mode.NONE)
            for(int x=-5;x<=5;x++)for(int y=251;y<=257;y++)p.level().setBlockAndUpdate(new BlockPos(x,y,-1),Blocks.SMOOTH_STONE.defaultBlockState());
    }
    private static com.codex.blacklight.movement.WallTraversal.Mode wall(Shot shot) {
        return shot.name().contains("wall_run")?com.codex.blacklight.movement.WallTraversal.Mode.RUN:shot.name().contains("wall_climb")?com.codex.blacklight.movement.WallTraversal.Mode.CLIMB:com.codex.blacklight.movement.WallTraversal.Mode.NONE;
    }
    @SubscribeEvent public static void beforeFrame(RenderFrameEvent.Pre event) {
        if(!initialized||disabled||completed||!preview||index>=SHOTS.size())return;
        var player=Minecraft.getInstance().player;if(player==null)return;
        // This controlled visual state samples the real wall-pose renderer after
        // normal client movement prediction. Physical wall movement is tested separately.
        com.codex.blacklight.movement.WallPoseState.set(player,wall(SHOTS.get(index)),0,1);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if(!initialized||disabled||completed||!preview||index>=SHOTS.size())return;
        var shot=SHOTS.get(index);if(shot.firstPerson()||shot.editor()||shot.nativeCamera())return;
        var player=Minecraft.getInstance().player;if(player==null)return;
        float partial=(float)event.getPartialTick();
        var pose=com.codex.blacklight.client.animation.BlacklightRenderPose.sample(player,partial);
        var skeleton=PlayerMotionController.skeleton();
        var root=skeleton.getBoundTransformFor(pose,skeleton.rootJoint).getTranslation(new org.joml.Vector3f());
        var head=skeleton.getBoundTransformFor(pose,skeleton.head).getTranslation(new org.joml.Vector3f());
        var center=new org.joml.Vector3f(root).lerp(head,.4F);
        var model=new org.joml.Matrix4f().rotationY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(player,partial))).scale(.9375F*player.getScale());
        model.transformPosition(center);
        var target=player.getPosition(partial).add(center.x,center.y,center.z);
        net.minecraft.world.phys.Vec3 camera;
        if(wall(shot)!=com.codex.blacklight.movement.WallTraversal.Mode.NONE)
            camera=target.add(-2, .45, 3.2); // Same side of the wall as the actor; whole body and contact stay visible.
        else {
            var forward=net.minecraft.world.phys.Vec3.directionFromRotation(0,player.getYRot());
            var right=new net.minecraft.world.phys.Vec3(-forward.z,0,forward.x);
            camera=target.add(forward.scale(3.8)).add(right.scale(1.3)).add(0,.35,0);
            if(shot.name().equals("58_whip_attachment"))camera=target.add(forward.scale(1.5)).add(right.scale(8)).add(0,2,0);
        }
        var look=target.subtract(camera);
        float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));
        ((com.codex.blacklight.mixin.client.PrototypeCameraAccess)event.getCamera()).blacklight$setPosition(camera);
        event.setYaw(yaw);event.setPitch(pitch);event.setRoll(0);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!capture||disabled||completed)return;
        var client=Minecraft.getInstance();var shot=SHOTS.get(index);capture=false;
        try(var image=Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(output.resolve(shot.name()+".png"));
            var record=new JsonObject();record.addProperty("file",shot.name()+".png");record.addProperty("form",shot.form().id());record.addProperty("utility",shot.utility()==null?"none":shot.utility().id());
            record.addProperty("motion",key(shot));record.addProperty("previewSeconds",PlayerMotionController.elapsed(client.player));
            record.addProperty("firstPerson",shot.firstPerson());record.addProperty("width",image.getWidth());record.addProperty("height",image.getHeight());record.addProperty("source","Minecraft framebuffer");captures.add(record);
            record.addProperty("renderPreviewWallMode",wall(shot).name());
            record.addProperty("expectedCameraType",camera(shot).name());record.addProperty("actualCameraType",client.options.getCameraType().name());
            record.addProperty("cameraTypeMatches",camera(shot)==client.options.getCameraType());record.addProperty("requestedPitch",shot.pitch());record.addProperty("actualPlayerPitch",client.player.getXRot());
            if(camera(shot)!=client.options.getCameraType())throw new IllegalStateException("Native camera perspective changed during "+shot.name());
            if(!shot.name().equals("49_native_back_orbit_180")&&Math.abs(client.player.getXRot()-shot.pitch())>.1F)throw new IllegalStateException("Requested view pitch not exercised");
            record.addProperty("nativeCamera",shot.nativeCamera());record.addProperty("skinTexture",client.player.getSkin().texture().toString());
            record.addProperty("actualPlayerYaw",client.player.getYRot());record.addProperty("actualBodyYaw",client.player.yBodyRot);
            record.addProperty("renderFacing",com.codex.blacklight.client.animation.BlacklightRenderPose.facing(client.player,0));
            record.addProperty("cameraControlYaw",com.codex.blacklight.client.camera.PrototypeChaseCamera.controlYaw());
            if(shot.name().equals("49_native_back_orbit_180")){record.addProperty("nativeOrbitRawMouseX",1200);record.addProperty("nativeOrbitRawMouseY",orbitMouseY);record.addProperty("orbitBeforePlayerYaw",orbitBeforeYaw);record.addProperty("orbitBeforeRenderFacing",orbitBeforeFacing);record.addProperty("orbitBeforeControlYaw",orbitBeforeControl);}
            var pose=com.codex.blacklight.client.animation.BlacklightRenderPose.sample(client.player,(float)event.getPartialTick().getGameTimeDeltaPartialTick(false));
            var rig=PlayerMotionController.skeleton();
            var hip=rig.getBoundTransformFor(pose,rig.rootJoint).getTranslation(new org.joml.Vector3f());
            var head=rig.getBoundTransformFor(pose,rig.head).getTranslation(new org.joml.Vector3f());
            record.addProperty("finalHeadAboveHip",head.y>hip.y);record.addProperty("finalHeadY",head.y);record.addProperty("finalHipY",hip.y);
            var camera=client.gameRenderer.getMainCamera();record.addProperty("actualCameraYaw",camera.getYRot());record.addProperty("actualCameraPitch",camera.getXRot());
            var model=new org.joml.Matrix4f().rotationY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(client.player,0))).scale(.9375F*client.player.getScale());
            var faceMatrix=new org.joml.Matrix4f(model).mul(rig.getBoundTransformFor(pose,rig.head)).mul(new org.joml.Matrix4f(rig.getBoundTransformFor(new com.codex.blacklight.animation.BlacklightPose(),rig.head)).invert());
            var normal=faceMatrix.transformDirection(new org.joml.Vector3f(0,0,-1)).normalize();
            var center=model.transformPosition(new org.joml.Vector3f(head));var toward=camera.getPosition().subtract(client.player.position().add(center.x,center.y,center.z)).normalize();
            double facingCamera=normal.x*toward.x+normal.y*toward.y+normal.z*toward.z;record.addProperty("headFrontNormalDotCamera",facingCamera);
            if(shot.name().equals("29_native_front")&&facingCamera<=.2)throw new IllegalStateException("Native FRONT camera cannot see the skin's face: "+facingCamera);
            if(shot.name().equals("30_native_back")&&facingCamera>=-.2)throw new IllegalStateException("Native BACK camera is on the face side: "+facingCamera);
            if(shot.name().equals("49_native_back_orbit_180")&&Math.abs(net.minecraft.util.Mth.wrapDegrees(client.player.getYRot()-orbitBeforeYaw))>.5F)throw new IllegalStateException("Idle character followed camera orbit");
            if(shot.name().equals("49_native_back_orbit_180")&&facingCamera<=.2)throw new IllegalStateException("Native BACK orbit cannot show the face: "+facingCamera);
            if(shot.firstPerson()) {
                var view=com.codex.blacklight.client.renderer.BlacklightPlayerRenderer.renderedView(client.player);
                if(view==null||Math.abs(view.gameTime()-(client.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false)))>1)throw new IllegalStateException("Native first-person renderer did not execute this frame");
                if(view.result().nearestDepth()<.1499F)throw new IllegalStateException("Actual first-person mesh crosses near plane");
                record.addProperty("handRenderingPath",view.route());
                record.addProperty("handRendererGameTime",view.gameTime());record.addProperty("nearestViewmodelDepth",view.result().nearestDepth());
                record.addProperty("maximumViewmodelProjectedX",view.result().maximumProjectedX());record.addProperty("maximumViewmodelProjectedY",view.result().maximumProjectedY());
            }
            var velocity=client.player.getDeltaMovement();record.addProperty("rawVelocityY",velocity.y);record.addProperty("rawVelocityX",velocity.x);record.addProperty("rawVelocityZ",velocity.z);
            BlacklightNeoMod.LOGGER.info("Standalone visual QA captured {}",shot.name());
            index++;queued=false;preview=false;
            writeReport(false,null);
        }catch(Exception e){fail(e);}
    }
    private static void finish(Minecraft client) {
        completed=true;PlayerMotionController.clearPreview(client.player);client.player.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);
        if(client.screen instanceof com.codex.blacklight.client.screen.AnimationTunerScreen)client.setScreen(null);
        client.options.setCameraType(previousCamera);client.options.fov().set(previousFov);client.options.hideGui=previousGui;client.options.pauseOnLostFocus=previousPause;
        savedKeys.forEach(KeyMapping::setDown);if(previousMouse)client.mouseHandler.grabMouse();else client.mouseHandler.releaseMouse();client.updateTitle();
        writeReport(true,null);BlacklightNeoMod.LOGGER.info("Standalone visual QA completed {} real-frame captures",captures.size());
        client.stop();
    }
    private static void fail(Exception e) {disabled=true;BlacklightNeoMod.LOGGER.error("Standalone visual QA failed",e);writeReport(false,e.toString());Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
    private static synchronized void writeReport(boolean complete,String error) {
        if(output==null)return;
        try {
            var report=new JsonObject();report.addProperty("complete",complete);report.addProperty("epicfightLoaded",ModList.get().isLoaded("epicfight"));report.addProperty("womLoaded",ModList.get().isLoaded("wom"));
            report.addProperty("captureCount",captures.size());report.add("captures",captures);if(error!=null)report.addProperty("error",error);
            report.addProperty("requestedShots",System.getProperty("blacklight.test.standaloneVisual.shots","all"));
            var mods=new JsonArray();for(var mod:ModList.get().getMods()){var m=new JsonObject();m.addProperty("id",mod.getModId());m.addProperty("version",mod.getVersion().toString());mods.add(m);}report.add("loadedMods",mods);
            Files.writeString(output.resolve("standalone-visual-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Cannot save standalone visual QA report",e);}
    }
}
