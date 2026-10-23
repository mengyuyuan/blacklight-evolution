package com.codex.blacklight.client;

import com.codex.blacklight.client.screen.UpgradeScreen;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Hidden disposable client: real consume, attachment sync and actual purchase packet. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class AssimilationVisualSmoke {
    private static final String WORLD="Assimilation QA "+System.currentTimeMillis();
    private static boolean creating,setup,done,capture,requested;
    private static int ticks,stage,mark,actor=-1,settled;
    private static String frame;
    private static volatile int result;
    private static volatile boolean finished;
    private static Entity camera;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("blacklight.assimilationVisual")||done)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.mouseHandler.releaseMouse();mc.options.pauseOnLostFocus=false;
        if(mc.getWindow().getWidth()!=1280||mc.getWindow().getHeight()!=800||mc.getMainRenderTarget().width!=1280||mc.getMainRenderTarget().height!=800){mc.getWindow().setWidth(1280);mc.getWindow().setHeight(800);mc.getMainRenderTarget().resize(1280,800,Minecraft.ON_OSX);}
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(30);
            var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(87512,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD))return;
        ticks++;if(ticks>2400)throw new IllegalStateException("Assimilation native QA stalled at "+stage+"; result="+result);
        mc.getToasts().clear();
        mc.options.hideGui=false;mc.options.fov().set(55);
        if(stage<=2&&result==1){
            if(camera==null){
                camera=EntityType.BAT.create(mc.level);camera.setPos(4,103.5,7);
                camera.xo=camera.xOld=4;camera.yo=camera.yOld=103.5;camera.zo=camera.zOld=7;
                camera.setYRot(147);camera.yRotO=147;camera.setXRot(14);camera.xRotO=14;
            }
            mc.setCameraEntity(camera);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.options.hideGui=true;
        }
        if(!setup){setup=true;mc.setScreen(null);mc.getSingleplayerServer().execute(()->{
            var l=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            l.setDayTime(6000);l.setWeatherParameters(12000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)l.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            p.teleportTo(l,0,101,0,0,0);p.setOnGround(true);var s=p.getData(ModAttachments.PLAYER_STATE);
            s.infect();s.setPrototypeMode(true);s.unlock(PowerForm.MUSCLE_MASS);s.activate(PowerForm.MUSCLE_MASS);s.addEvolutionPoints(1000);p.syncData(ModAttachments.PLAYER_STATE);
            var h=ModEntities.INFECTED_HOST.get().create(l);h.inherit(EntityType.CHICKEN.create(l));long seed=1;while(TissueCore.forSeed(seed)!=TissueCore.GRASP)seed++;
            h.setUUID(new UUID(0,seed));h.advanceTissue(72000);h.setNoAi(true);h.setPos(0,101,1.2);l.addFreshEntity(h);h.setHealth(h.getMaxHealth()*.1F);actor=h.getId();result=1;
        });mark=ticks;}
        if(stage==0&&result==1&&ticks-mark>40){
            mc.getSingleplayerServer().execute(()->ConsumeSystem.request(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));stage=1;mark=ticks;
        }
        if(stage==1&&ConsumeVisual.holding(mc.player.getData(ModAttachments.GRAB_STAGE))){
            mc.getSingleplayerServer().execute(()->ConsumeSystem.consume(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));stage=2;mark=ticks;
        }
        if(stage==2&&!requested&&ticks-mark>8&&mc.level.getEntity(actor) instanceof BlacklightInfectedHost h&&h.getData(ModAttachments.CONSUME_VISUAL).consuming()){
            frame="human-absorption";capture=true;requested=true;
        }
        if(stage==2&&mc.player.getData(ModAttachments.ORGAN_RESEARCH).sample(TissueCore.GRASP)==1){
            mc.setCameraEntity(mc.player);mc.setScreen(new UpgradeScreen());stage=3;mark=ticks;requested=false;
        }
        if(stage==3&&ticks-mark>10&&mc.screen instanceof UpgradeScreen screen){
            int step=Math.max(14,Math.min(27,(screen.height-54)/10));screen.mouseClicked(15,48+7*step+step/2,0);
            stage=4;mark=ticks;
        }
        if(stage==4&&ticks-mark>8&&!capture){frame="research-unseen";capture=true;stage=5;mark=ticks;}
        if(stage==5&&ticks-mark>8&&!capture&&mc.screen instanceof UpgradeScreen screen){
            int rail=Math.clamp(screen.width/6,62,108);screen.mouseClicked(rail+20,48+3*31+6,0);stage=6;mark=ticks;
        }
        if(stage==6&&ticks-mark>8&&!capture){frame="research-specimen";capture=true;stage=7;mark=ticks;}
        if(stage==7&&ticks-mark>8&&!capture&&mc.screen instanceof UpgradeScreen screen){
            int rail=Math.clamp(screen.width/6,62,108),list=Math.clamp((screen.width-rail)/3,85,170),detail=rail+7+list+10;
            screen.mouseClicked(detail+20,screen.height-16,0);stage=8;
        }
        if(stage==8&&mc.player.getData(ModAttachments.ORGAN_RESEARCH).rank(TissueCore.GRASP)==1){frame="research-purchased";capture=true;stage=9;mark=ticks;}
        if(stage==9&&ticks-mark>8&&!capture){
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                if(p.getData(ModAttachments.ORGAN_RESEARCH).sample(TissueCore.GRASP)!=1||p.getData(ModAttachments.ORGAN_RESEARCH).rank(TissueCore.GRASP)!=1)throw new IllegalStateException("Client/server research disagrees");
                result=2;
            });mc.setScreen(null);stage=10;
        }
        if(stage==10&&result==2&&ticks%20==0)mc.getSingleplayerServer().execute(()->{
            boolean busy=false;for(var l:mc.getSingleplayerServer().getAllLevels())for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                var holder=l.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x,z));if(holder!=null&&!holder.isReadyForSaving())busy=true;
            }
            settled=busy?0:settled+1;if(settled>=5)finished=true;
        });
        if(finished){done=true;mc.stop();}
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post e){
        if(!Boolean.getBoolean("blacklight.assimilationVisual")||!capture||done)return;
        var mc=Minecraft.getInstance();if(mc.getMainRenderTarget().width!=1280||mc.getMainRenderTarget().height!=800)return;
        try{
            var output=mc.gameDirectory.toPath().resolve("assimilation-captures");Files.createDirectories(output);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){if(image.getWidth()!=1280||image.getHeight()!=800)return;image.writeToFile(output.resolve(frame+".png"));}
            var state=mc.player.getData(ModAttachments.ORGAN_RESEARCH);
            Files.writeString(output.resolve("status.txt"),frame+"; sample="+state.sample(TissueCore.GRASP)+"; rank="+state.rank(TissueCore.GRASP)+"; ep="+mc.player.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);capture=false;
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @SubscribeEvent public static void hand(RenderHandEvent e){
        if(Boolean.getBoolean("blacklight.assimilationVisual")&&stage<=2)e.setCanceled(true);
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void frameScene(ViewportEvent.ComputeCameraAngles e){
        if(!Boolean.getBoolean("blacklight.assimilationVisual")||stage>2)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!(mc.level.getEntity(actor) instanceof BlacklightInfectedHost h))return;
        var focus=mc.player.getBoundingBox().minmax(h.getBoundingBox()).getCenter();
        var position=focus.add(6,3.5,8);var delta=focus.subtract(position);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)Math.toDegrees(Math.atan2(-delta.y,delta.horizontalDistance()));
        e.setYaw(yaw);e.setPitch(pitch);e.setRoll(0);
        var access=(com.codex.blacklight.mixin.client.PrototypeCameraAccess)e.getCamera();
        access.blacklight$setPosition(position);access.blacklight$setRotation(yaw,pitch,0);
    }
}
