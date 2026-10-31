package com.codex.blacklight.client;

import com.codex.blacklight.client.screen.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import java.nio.file.*;
import java.util.*;

/** Native GUI interaction checks, restricted to the disposable acceptance save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class StandaloneTutorialInspectSmoke {
    private static boolean started,done,disabled,capture,initialBreaking;
    private static int ticks,step,oldScale,oldEP,captureWait;
    private static UpgradeScreen screen;
    private static Path output;private static long waitSince;
    private static final Map<KeyMapping,InputConstants.Key> saved=new IdentityHashMap<>();
    private static final List<String> checks=new ArrayList<>();
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        String property=System.getProperty("blacklight.test.tutorialInspect","");if(property.isBlank()||done||disabled)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null)return;
        var game=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if(!game.equals(Path.of("D:/Blacklight-Standalone/run-native-qa"))||!mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(game.resolve("saves/Blacklight Standalone QA v4"))){disabled=true;throw new IllegalStateException("Refusing non-QA tutorial test");}
        try {
            if(!started){
                if(++ticks<50)return;
                output=Path.of(property);Files.createDirectories(output);oldScale=mc.options.guiScale().get();mc.options.guiScale().set(2);mc.resizeDisplay();
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;oldEP=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getEvolutionPoints();
                initialBreaking=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking();
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();screen=new UpgradeScreen();mc.setScreen(screen);started=true;ticks=0;return;
            }
            if(++ticks<25||capture)return;ticks=0;
            switch(step){
                case 0 -> {checkGif();capture=true;}
                case 1 -> {category(4);select(3);require(selected().equals("groundspike_graveyard"),"Click selects locked/owned skills");capture=true;}
                case 2 -> {
                    remap(mc.options.keyAttack,InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_R));
                    remap(mc.options.keyUse,InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_Y));
                    String controls=SkillTutorial.forSkill(selected()).controls().getString();require(controls.contains("R")&&controls.contains("Y"),"Live keyboard bindings in combo");capture=true;
                }
                case 3 -> {
                    remap(mc.options.keyAttack,InputConstants.UNKNOWN);remap(mc.options.keyUse,InputConstants.Type.MOUSE.getOrCreate(4));
                    String controls=SkillTutorial.forSkill(selected()).controls().getString();require(controls.contains(SkillBindings.label("key.attack").getString())&&controls.contains(SkillBindings.label("key.use").getString()),"Unbound and mouse bindings supported");capture=true;
                }
                case 4 -> {saved.forEach(KeyMapping::setKey);KeyMapping.resetMapping();
                    require(com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).getEvolutionPoints()==oldEP,"Selection and remapping never purchase skills");
                    category(8);screen.mouseClicked((int)value("detailX")+15,screen.height-18,0);capture=true;}
                case 5 -> {require(com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking()!=initialBreaking,"Server-authoritative settings synchronized");screen.mouseClicked((int)value("detailX")+15,screen.height-18,0);category(7);capture=true;}
                case 6 -> {mc.options.guiScale().set(4);mc.resizeDisplay();screen=(UpgradeScreen)mc.screen;category(4);select(2);capture=true;}
                case 7 -> {checkGif();screen.mouseScrolled(screen.width-20,100,0,-20);capture=true;}
                case 8 -> {
                    remap(SkillBindings.find("key.blacklight.upgrades"),InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_O));
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_O,0,0);require(mc.screen==null,"Remapped skill key closes the menu");
                    saved.forEach(KeyMapping::setKey);KeyMapping.resetMapping();mc.options.guiScale().set(oldScale);mc.resizeDisplay();
                    var json=new com.google.gson.JsonObject();json.addProperty("passed",true);json.add("checks",new com.google.gson.Gson().toJsonTree(checks));Files.writeString(output.resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));done=true;mc.stop();return;
                }
            }
        }catch(Throwable error){
            try{saved.forEach(KeyMapping::setKey);KeyMapping.resetMapping();Files.writeString(output.resolve("error.txt"),error.toString());}catch(Exception ignored){}
            done=true;mc.stop();throw new RuntimeException(error);
        }
    }
    private static void remap(KeyMapping key,InputConstants.Key value){saved.putIfAbsent(key,key.getKey());key.setKey(value);KeyMapping.resetMapping();}
    private static Object value(String name)throws Exception{var f=UpgradeScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private static String selected()throws Exception{return (String)value("selected");}
    private static void category(int index){int stride=Math.max(14,Math.min(27,(screen.height-54)/9));screen.mouseClicked(15,48+index*stride+4,0);}
    private static void select(int index)throws Exception{screen.mouseClicked((int)value("listX")+20,48+index*31+5,0);}
    private static void checkGif()throws Exception{Object gif=value("gif");var field=gif.getClass().getDeclaredField("textureId");field.setAccessible(true);require(field.get(gif)!=null,"Animated GIF uploaded to native GPU texture");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);checks.add(message);}
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!capture||done)return;
        if(Boolean.getBoolean("blacklight.test.tutorialInspect.finalOnly")&&step==1){if(waitSince==0)waitSince=System.nanoTime();if(System.nanoTime()-waitSince<3_500_000_000L)return;}
        if(++captureWait<40)return;capture=false;captureWait=0;waitSince=0;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(output.resolve("page-"+step+".png"));step++;if(Boolean.getBoolean("blacklight.test.tutorialInspect.finalOnly")){if(step==2)step=6;else if(step==7)step=8;}}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
