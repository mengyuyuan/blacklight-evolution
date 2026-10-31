package com.codex.blacklight.client;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;

/** Opt-in, hidden client: actual kills, synchronized nutrition and native lock HUD. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class TissueEvolutionSmoke {
    private static final String WORLD="Tissue ecology QA "+System.currentTimeMillis();
    private static boolean creating,setup,requested,capture,done;
    private static volatile boolean ready,finished;
    private static volatile int actor=-1;
    private static volatile double serverDamage;
    private static int ticks,mark,index,settled;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.tissueEvolutionVisual")||done)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.mouseHandler.releaseMouse();
        mc.options.pauseOnLostFocus=false;
        if(mc.getWindow().getWidth()!=1280||mc.getWindow().getHeight()!=800||mc.getMainRenderTarget().width!=1280||mc.getMainRenderTarget().height!=800){
            mc.getWindow().setWidth(1280);mc.getWindow().setHeight(800);mc.getMainRenderTarget().resize(1280,800,Minecraft.ON_OSX);
        }
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(30);
            var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(87512,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD))return;
        ticks++;if(ticks>2400)throw new IllegalStateException("Tissue ecology QA stalled at "+index);
        mc.setScreen(null);mc.getToasts().clear();mc.options.hideGui=false;mc.options.fov().set(55);
        if(!setup){setup=true;mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            for(int x=-12;x<=12;x++)for(int z=-12;z<=14;z++)level.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            player.teleportTo(level,0,101,0,0,0);var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);
            spawn();
        });mark=ticks;}
        if(ready&&index<5&&ticks-mark>30&&mc.level.getEntity(actor) instanceof BlacklightInfectedHost host){
            if(!requested&&ClientTargetLock.active()){
                ClientTargetLock.clear();mc.hitResult=new EntityHitResult(host);ClientTargetLock.toggle();requested=true;mark=ticks;
            }
            if(requested&&ticks-mark>12&&ClientTargetLock.target()==host&&!ClientTargetLock.pending()&&host.tissueNutrition()>0){
                if(host.tissueAge()<816000||host.getMaxHealth()<=90)throw new IllegalStateException("Native host growth did not synchronize");
                capture=true;ready=false;
            }
        }
        if(index>=5&&ticks%20==0)mc.getSingleplayerServer().execute(()->{
            boolean busy=false;for(var level:mc.getSingleplayerServer().getAllLevels())for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                var holder=level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x,z));if(holder!=null&&!holder.isReadyForSaving())busy=true;
            }
            settled=busy?0:settled+1;if(settled>=5)finished=true;
        });
        if(finished){done=true;mc.stop();}
    }
    private static void spawn(){
        var mc=Minecraft.getInstance();var level=mc.getSingleplayerServer().overworld();
        if(level.getEntity(actor) instanceof BlacklightInfectedHost previous)previous.discard();
        var core=TissueCore.values()[index];var host=ModEntities.INFECTED_HOST.get().create(level);host.inherit(EntityType.COW.create(level));
        long seed=1;while(TissueCore.forSeed(seed)!=core)seed++;host.setUUID(new UUID(0,seed));host.setNoAi(true);host.setPos(0,101,7);level.addFreshEntity(host);host.advanceTissue(816000);
        for(int meal=0;meal<4;meal++){
            var food=EntityType.COW.create(level);food.setNoAi(true);food.setPos(host.position().add(2,0,0));level.addFreshEntity(food);
            food.hurt(food.damageSources().mobAttack(host),10000);food.discard();
        }
        if(host.tissueNutrition()<=0)throw new IllegalStateException("Actual native kill did not supply nutrition");
        serverDamage=host.getAttributeValue(Attributes.ATTACK_DAMAGE);actor=host.getId();ready=true;
    }
    @SubscribeEvent public static void frame(ViewportEvent.ComputeCameraAngles event){
        if(!Boolean.getBoolean("blacklight.tissueEvolutionVisual"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actor) instanceof BlacklightInfectedHost host))return;
        var center=host.getBoundingBox().getCenter();var position=center.add(7,3,9);var direction=center.subtract(position);
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)),pitch=(float)Math.toDegrees(Math.atan2(-direction.y,direction.horizontalDistance()));
        event.setYaw(yaw);event.setPitch(pitch);event.setRoll(0);
        var access=(com.codex.blacklight.mixin.client.PrototypeCameraAccess)event.getCamera();access.blacklight$setPosition(position);access.blacklight$setRotation(yaw,pitch,0);
    }
    @SubscribeEvent public static void capture(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.tissueEvolutionVisual")||!capture||done)return;
        var mc=Minecraft.getInstance();if(mc.getMainRenderTarget().width!=1280||mc.getMainRenderTarget().height!=800)return;
        if(!(mc.level.getEntity(actor) instanceof BlacklightInfectedHost host))throw new IllegalStateException("Capture target vanished");
        try{
            var folder=mc.gameDirectory.toPath().resolve("evolution-captures");Files.createDirectories(folder);String name=TissueCore.values()[index].id;
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){if(image.getWidth()!=1280||image.getHeight()!=800)return;image.writeToFile(folder.resolve(name+".png"));}
            var json=new JsonObject();json.addProperty("core",name);json.addProperty("age",host.tissueAge());json.addProperty("nutrition",host.tissueNutrition());
            json.addProperty("maxHealth",host.getMaxHealth());json.addProperty("serverDamage",serverDamage);json.addProperty("bodyScale",host.tissueBodyScale());
            json.addProperty("targetLockConfirmed",ClientTargetLock.target()==host&&!ClientTargetLock.pending());json.addProperty("width",1280);json.addProperty("height",800);
            Files.writeString(folder.resolve(name+".json"),json.toString());capture=false;requested=false;ClientTargetLock.clear();index++;mark=ticks;
            if(index<5)mc.getSingleplayerServer().execute(TissueEvolutionSmoke::spawn);
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    @SubscribeEvent public static void hand(RenderHandEvent event){if(Boolean.getBoolean("blacklight.tissueEvolutionVisual"))event.setCanceled(true);}
}
