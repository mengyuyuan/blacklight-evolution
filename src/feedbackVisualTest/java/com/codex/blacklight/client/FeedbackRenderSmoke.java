package com.codex.blacklight.client;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Opt-in native framebuffer regression, isolated from personal saves and human input. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class FeedbackRenderSmoke {
    private static boolean setup,done,creating;private static int ticks;private static volatile boolean ready;
    private static String lastScreen;private static Path output;private static final JsonObject report=new JsonObject();
    private static boolean enabled(){return Boolean.getBoolean("blacklight.feedbackVisual");}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled()||done)return;
        var mc=Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.mouseHandler.releaseMouse();
        mc.options.pauseOnLostFocus=false;
        String screen=mc.screen==null?"none":mc.screen.getClass().getName();
        if(!screen.equals(lastScreen)){com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Feedback QA screen: {}",screen);lastScreen=screen;}
        if(mc.player==null||mc.getSingleplayerServer()==null){
            if(Boolean.getBoolean("blacklight.feedbackFreshWorld")&&!creating&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){
                try{
                    Path expected=Path.of(System.getProperty("blacklight.feedbackDirectory")).toAbsolutePath().normalize();
                    if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(expected))throw new IllegalStateException("Not the disposable feedback directory");
                    creating=true;String name="Blacklight Feedback QA "+System.currentTimeMillis();
                    mc.createWorldOpenFlows().createFreshLevel(name,new net.minecraft.world.level.LevelSettings(name,GameType.CREATIVE,false,
                            net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                            new net.minecraft.world.level.levelgen.WorldOptions(7712L,false,false),
                            registries->registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                }catch(Throwable t){finish(t);}
            }
            return;
        }
        try {
            Path expected=Path.of(System.getProperty("blacklight.feedbackDirectory")).toAbsolutePath().normalize();
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(expected))throw new IllegalStateException("Not the disposable feedback directory");
            output=expected.resolve("captures");Files.createDirectories(output);
            if(!setup){setup=true;mc.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
                    p.teleportTo(0,100,0);p.setXRot(15);p.setYRot(0);
                    p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(12000,0,false,false);ready=true;
                });
            }
            if(ready){ticks++;mc.player.setXRot(15);mc.player.setYRot(0);}
        }catch(Throwable t){finish(t);}
    }
    private static void intensity(float v)throws Exception{
        for(String n:new String[]{"intensity","previousIntensity"}){var f=ClientSpeedPostEffect.class.getDeclaredField(n);f.setAccessible(true);f.setFloat(null,v);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void before(RenderLevelStageEvent e){
        if(!enabled()||done||!ready||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||ticks<80)return;
        var mc=Minecraft.getInstance();if(mc.screen!=null)return;
        try {
            var main=mc.getMainRenderTarget();
            if(!report.has("baseline")){
                int sky=mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,mc.player.blockPosition().above(3));
                report.addProperty("nativeSkyLight",sky);
                if(sky<1)throw new IllegalStateException("Native daylight scene has not acquired real skylight");
                report.addProperty("baseline",capture("baseline"));
                intensity(.8F);ClientSpeedPostEffect.onRenderLevel(e);
                report.addProperty("sprint",capture("sprint"));
                while(GL11.glGetError()!=GL11.GL_NO_ERROR){}
                var foreign=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
                try{
                    foreign.setClearColor(.1F,.7F,.4F,1);foreign.clear(Minecraft.ON_OSX);foreign.bindWrite(true);
                    int before=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                    ClientSpeedPostEffect.onRenderLevel(e);
                    report.addProperty("foreignTargetPreserved",before==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
                }finally{foreign.destroyBuffers();main.bindWrite(true);}
                intensity(0);
                report.addProperty("glError",GL11.glGetError());
                double base=report.get("baseline").getAsDouble(),sprint=report.get("sprint").getAsDouble();
                if(base<.03||sprint<base*.5)throw new IllegalStateException("Sprint output became black");
                if(!report.get("foreignTargetPreserved").getAsBoolean())throw new IllegalStateException("Speed post effect hijacked another renderer's framebuffer");
                if(report.get("glError").getAsInt()!=0)throw new IllegalStateException("OpenGL error");
                finish(null);
            }
        }catch(Throwable t){finish(t);}
    }
    private static double capture(String name)throws Exception{
        var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            image.writeToFile(output.resolve(name+".png"));double total=0;int count=0;
            for(int y=0;y<image.getHeight();y+=8)for(int x=0;x<image.getWidth();x+=8){int c=image.getPixelRGBA(x,y);total+=((c&255)+((c>>>8)&255)+((c>>>16)&255))/765.;count++;}
            return total/count;
        }
    }
    private static void finish(Throwable t){
        done=true;report.addProperty("passed",t==null);if(t!=null)report.addProperty("error",t.toString());
        try{if(output!=null)Files.writeString(output.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception ignored){}
        Minecraft.getInstance().stop();
    }
}
