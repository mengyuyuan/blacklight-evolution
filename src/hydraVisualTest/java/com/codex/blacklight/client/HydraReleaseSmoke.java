package com.codex.blacklight.client;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.registry.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in, creates its own new world, never operates an existing player save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HydraReleaseSmoke {
    private static final String WORLD="Hydra release QA "+System.currentTimeMillis();
    private static final Path OUT=Path.of("hydra-close-frames");
    private static boolean creating,setup,done,entered,returned,thrown,returnThrown;
    private static int returnedTick;
    private static boolean cityEmerging,cityToss,homeEmerging,homeToss;
    private static volatile boolean seeded,approached;
    private static volatile int actualFeeds;
    private static int tick,frameTick=-1,cityTick;
    private static volatile UUID hydra;
    private static int lastScene=-1,cameraFrames,cameraCuts;
    private static Object lastWorld;
    private static net.minecraft.world.phys.Vec3 lastAnchor;
    private static double cameraDrift;
    private static int heldFrames;
    private static boolean everRiding,dead;
    private static float minHealth=20;
    private static final List<String> damageLog=new java.util.concurrent.CopyOnWriteArrayList<>();
    @SubscribeEvent public static void damage(net.minecraftforge.event.entity.living.LivingDamageEvent e){
        if(Boolean.getBoolean("blacklight.hydraVisual")&&e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)
            damageLog.add("tick="+tick+" source="+e.getSource().getMsgId()+" damage="+e.getAmount()+" held="+HydraTransport.active(p));
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.hydraVisual")||Boolean.getBoolean("blacklight.creatureCombatVisual"))return;
        var mc=Minecraft.getInstance();
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
            mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(3434L,false,false),
                r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(done||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD))return;
        mc.mouseHandler.releaseMouse();
        if(!setup){setup=true;write("result.txt","pending: "+WORLD);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var l=p.serverLevel();
                l.setDayTime(6000);l.setWeatherParameters(12000,0,false,false);
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)l.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                var e=ModEntities.BLACKLIGHT_HYDRA.get().create(l);e.moveTo(0,101,0,180,0);e.yBodyRot=180;e.yHeadRot=180;e.setNoAi(true);l.addFreshEntity(e);hydra=e.getUUID();

                p.teleportTo(l,0,101,-13,0,-14);
            });return;
        }
        tick++;
        if(approached&&tick%20==0)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var e=p.serverLevel().getEntity(hydra);
            if(e instanceof BlacklightHydra h){
                var target=mc.getSingleplayerServer().getLevel(CityTravel.CITY);
                var origin=target==null?null:CityTravel.arrivalOrigin(target);
                write("server-state-"+tick+".txt","action="+h.action()+" time="+h.actionTime()+" held="+h.heldId()+" active="+HydraTransport.active(p)+" player="+p.position()+" safe="+(target==null?null:CityTravel.safePosition(target,origin)));
            }
        });
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlacklightHydra h&&h.scenePlayerId()==mc.player.getId()){
            if(mc.level.dimension().equals(CityTravel.CITY)){cityEmerging|=h.action()==7;cityToss|=h.action()==8;}
            else if(entered){homeEmerging|=h.action()==7;homeToss|=h.action()==8;}
        }
        everRiding|=mc.player.isPassenger();dead|=!mc.player.isAlive();minHealth=Math.min(minHealth,mc.player.getHealth());
        if(HydraTransport.active(mc.player))heldFrames++;
        if(tick==80)server(mc,()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(p.serverLevel(),13,101,0,90,-14);});
        if(tick==140)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var l=p.serverLevel();
            l.getEntity(hydra).discard();
            var hive=ModEntities.BLACKLIGHT_HIVE.get().create(l);hive.moveTo(0,101,0);l.addFreshEntity(hive);
            hive.hurt(l.damageSources().genericKill(),10000);
            var drops=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-4,100,-4,4,110,4),e->e.getItem().is(ModItems.LIVING_HIVE_TISSUE.get()));
            if(drops.size()!=1)throw new IllegalStateException("Hive tissue drop missing or duplicated: "+drops.size());
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,drops.get(0).getItem().copy());drops.get(0).discard();
            var floor=new BlockPos(0,100,0);
            var use=new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(floor.getCenter().add(0,.5,0),Direction.UP,floor,false));
            if(!p.getMainHandItem().useOn(use).consumesAction())throw new IllegalStateException("Actual tissue item could not plant");
            var portals=l.getEntitiesOfClass(BlacklightHydra.class,new net.minecraft.world.phys.AABB(-3,100,-3,3,106,3),BlacklightHydra::planted);
            if(portals.size()!=1)throw new IllegalStateException("Planting must create exactly one growing organ");
            hydra=portals.get(0).getUUID();seeded=true;
            p.teleportTo(l,0,101,-13,0,-14);
        });
        if(tick>=160&&tick<=480&&(tick-160)%80==0)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var l=p.serverLevel();
            var cow=net.minecraft.world.entity.EntityType.COW.create(l);cow.moveTo(1.5,101,0);cow.setNoAi(true);l.addFreshEntity(cow);
        });
        if(seeded&&!approached&&tick%20==0)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var e=(BlacklightHydra)p.serverLevel().getEntity(hydra);actualFeeds=e.feeds();
            if(e.growth()>=1&&e.action()==0){
                approached=true;write("growth.txt","real feeds="+actualFeeds+" age="+e.growthAge()+" client tick="+tick);
                p.teleportTo(p.serverLevel(),0,101,-2.5,180,0);p.setGameMode(GameType.SURVIVAL);
            }
        });
        if(mc.level.dimension().equals(CityTravel.CITY)){
            if(!entered){entered=true;cityTick=tick;write("entered-city.txt","Actual client arrived in blacklight:city at tick "+tick);}
            if(cityEmerging&&cityToss&&!thrown&&tick>cityTick+100&&!HydraTransport.active(mc.player)){
                thrown=true;write("thrown.txt","Released in city at tick "+tick+" velocity="+mc.player.getDeltaMovement());
            }
            if(tick==cityTick+390)server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var portals=p.serverLevel().getEntitiesOfClass(BlacklightHydra.class,p.getBoundingBox().inflate(100),h->h.link()!=null&&h.planted());
                if(portals.size()!=1)throw new IllegalStateException("Expected exactly one persistent return endpoint: "+portals.size());
                var portal=portals.get(0);p.teleportTo(p.serverLevel(),portal.getX()+2.5,portal.getY(),portal.getZ(),0,0);
                portal.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND);
            });
        }else if(entered&&!returned){returned=true;returnedTick=tick;write("returned.txt","Actual client returned to "+mc.player.position());}
        if(homeEmerging&&homeToss&&returned&&tick>returnedTick+145&&!HydraTransport.active(mc.player))returnThrown=true;
        if(returnThrown&&tick>returnedTick+270||tick>6000){done=true;write("result.txt","accepted="+(seeded&&actualFeeds==5&&entered&&thrown&&returned&&returnThrown&&!everRiding&&!dead&&cameraCuts>=4&&cameraDrift<.001&&mc.options.getCameraType()==CameraType.FIRST_PERSON)+" cityEmerging="+cityEmerging+" cityToss="+cityToss+" homeEmerging="+homeEmerging+" homeToss="+homeToss+" seeded="+seeded+" actualFeeds="+actualFeeds+" entered="+entered+" thrown="+thrown+" returned="+returned+" returnThrown="+returnThrown+" ticks="+tick+" heldFrames="+heldFrames+" everRiding="+everRiding+" dead="+dead+" minHealth="+minHealth+" cameraFrames="+cameraFrames+" cameraCuts="+cameraCuts+" cameraDrift="+cameraDrift+" restored="+(mc.options.getCameraType()==CameraType.FIRST_PERSON));write("damage.txt",String.join("\n",damageLog));mc.stop();}
    }
    private static void server(Minecraft mc,Runnable r){mc.getSingleplayerServer().execute(r);}
    private static void write(String name,String text){try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(name),text);}catch(Exception e){throw new RuntimeException(e);}}
    private static org.joml.Matrix4f renderedBone(software.bernie.geckolib.cache.object.GeoBone bone){
        var stack=new com.mojang.blaze3d.vertex.PoseStack();
        if(bone.getParent()!=null)stack.mulPoseMatrix(renderedBone(bone.getParent()));
        software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(stack,bone);
        return new org.joml.Matrix4f(stack.last().pose());
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.hydraVisual")||done)return;var mc=Minecraft.getInstance();
        if(com.codex.blacklight.client.camera.HydraCinematicCamera.active()){
            int scene=com.codex.blacklight.client.camera.HydraCinematicCamera.sceneId();
            var anchor=mc.gameRenderer.getMainCamera().getPosition();cameraFrames++;
            if(lastScene==scene&&lastWorld==mc.level&&lastAnchor!=null)cameraDrift=Math.max(cameraDrift,anchor.distanceTo(lastAnchor));
            else cameraCuts++;
            lastScene=scene;lastWorld=mc.level;lastAnchor=anchor;
        }else{lastScene=-1;lastAnchor=null;}
        if(!setup||mc.player==null||frameTick==tick||tick<40||tick%40!=0)return;
        frameTick=tick;
        try {
            Files.createDirectories(OUT);
            StringBuilder row=new StringBuilder("tick="+tick+" dimension="+mc.level.dimension().location()+" player="+mc.player.getId()+" position="+mc.player.position()+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" type="+mc.options.getCameraType()+" scene="+com.codex.blacklight.client.camera.HydraCinematicCamera.sceneId());
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlacklightHydra hydraEntity)row.append(" hydra="+hydraEntity.getId()+" scenePlayer="+hydraEntity.scenePlayerId()+" held="+hydraEntity.heldId()+" action="+hydraEntity.action()+":"+hydraEntity.actionTime()+" pos="+hydraEntity.position()+" scale="+hydraEntity.visualScale());
            Files.writeString(OUT.resolve("telemetry.txt"),row+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(Exception e){throw new RuntimeException(e);}
        var h=HydraTransport.carrier(mc.player);
        if(h!=null){
            var renderer=(com.codex.blacklight.client.renderer.BlacklightHydraRenderer)mc.getEntityRenderDispatcher().getRenderer(h);
            var model=renderer.getGeoModel();
            var left=renderedBone((software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone("claw_left")).transformPosition(new org.joml.Vector3f(-24/16F,106/16F,-40/16F));
            var right=renderedBone((software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone("claw_right")).transformPosition(new org.joml.Vector3f(24/16F,106/16F,-40/16F));
            var grip=left.add(right).mul(.5F);
            var expected=HydraMotion.INSTANCE.grip(h.motionPose(mc.getFrameTime()));
            write("alignment-"+tick+".txt","action="+h.action()+" time="+h.actionTime()+" yaw="+h.getYRot()+" body="+h.yBodyRot+" prevBody="+h.yBodyRotO+" player="+mc.player.position()+" target="+h.gripPosition(mc.player,0)+" renderedLocal="+grip+" expectedLocal="+expected);
        }
        try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){Files.createDirectories(OUT);shot.writeToFile(OUT.resolve(String.format("tick-%04d.png",tick)));}catch(Exception e){throw new RuntimeException(e);}
    }
}
