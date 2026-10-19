package com.codex.blacklight.city;

import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

/** Opt-in renderer acceptance; creates its own save and never controls desktop input. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ShenzhenVisualSmoke {
    private static final String NAME="Shenzhen world.3 QA "+System.currentTimeMillis();
    private static final Path OUT=Path.of("city-frames");
    private static final int[][] POINTS={{670,820},{1450,800},{-10880,3900},{7000,500},{18000,-15000},{45000,2000}};
    private static boolean creating,setup,done;private static int tick,index=-1,next,shot=-1;
    private static volatile boolean positioned;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("blacklight.cityVisual"))return;
        var mc=Minecraft.getInstance();
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60);mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.createWorldOpenFlows().createFreshLevel(NAME,new LevelSettings(NAME,GameType.SPECTATOR,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(3434L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
        }
        if(done||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(NAME))return;
        mc.mouseHandler.releaseMouse();tick++;
        if(!setup){setup=true;mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);next=tick+40;}
        if(tick>=next&&shot<0){
            index++;
            if(index==POINTS.length){done=true;write("result.txt","accepted=true; rendered 6 locations in blacklight:world; no existing saves touched");mc.stop();return;}
            positioned=false;shot=index;next=Integer.MAX_VALUE;int current=index;
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=p.server.getLevel(CityTravel.WORLD);
                if(level==null)throw new IllegalStateException("Blacklight world missing");
                level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);CityPregenerator.pause(level);
                int x=POINTS[current][0],z=POINTS[current][1];level.getChunk(x>>4,z>>4);int y=Math.min(1400,Math.max(WorldAtlas.ground(x,z)+80,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z)+40));
                p.setGameMode(GameType.SPECTATOR);p.teleportTo(level,x+.5,y,z+.5,180,65);
                write("point-"+current+".txt","dimension="+level.dimension().location()+" position="+p.position());positioned=true;
            });
        }
        if(positioned){mc.player.setYRot(180);mc.player.setXRot(65);mc.player.yRotO=180;mc.player.xRotO=65;}
        if(positioned&&next==Integer.MAX_VALUE&&mc.level.dimension().equals(CityTravel.WORLD))next=tick+300;
        if(tick>4000){done=true;write("result.txt","accepted=false; screenshot route timed out at "+index);mc.stop();}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!Boolean.getBoolean("blacklight.cityVisual")||done||shot<0||!positioned||tick<next)return;
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().equals(CityTravel.WORLD))return;
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){Files.createDirectories(OUT);image.writeToFile(OUT.resolve("point-"+shot+".png"));write("camera-"+shot+".txt","client="+mc.player.position()+" rot="+mc.player.getYRot()+","+mc.player.getXRot()+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" cameraRot="+mc.gameRenderer.getMainCamera().getYRot()+","+mc.gameRenderer.getMainCamera().getXRot()+" screen="+mc.screen);}
        catch(Exception e){throw new RuntimeException(e);}shot=-1;next=tick+20;
    }
    private static void write(String name,String text){try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(name),text);}catch(Exception e){throw new RuntimeException(e);}}
}
