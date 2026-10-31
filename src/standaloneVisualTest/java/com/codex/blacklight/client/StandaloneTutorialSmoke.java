package com.codex.blacklight.client;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.screen.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline demonstration export. Never changes or sends input for the real player. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class StandaloneTutorialSmoke {
    private static final List<String> IDS=List.of("mode","grab","target_lock","disguise","jump_1","sprint_1","wall_1","glide_1","air_dash_plus","groundspike","dashing_slice","claws","blade","hammerfists","whipfist","muscle_mass","armor","shield","thermal_vision","critical_mass","vitality","reach_1","adrenaline_surge","bulletdive_drop","groundspike_graveyard","tendril_barrage","critical_pain")
            .stream().filter(id->System.getProperty("blacklight.test.tutorialFilter","").isBlank()||Arrays.asList(System.getProperty("blacklight.test.tutorialFilter").split(",")).contains(id)).toList();
    private static Path output;private static boolean disabled,started,done;private static int ticks,index,frame;
    private static DemoScreen screen;private static final ExecutorService writer=Executors.newSingleThreadExecutor();
    private static volatile Throwable error;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        String value=System.getProperty("blacklight.test.standaloneTutorial","");if(value.isBlank()||disabled||done)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null)return;
        var game=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if(!game.equals(Path.of("D:/Blacklight-Standalone/run-native-qa"))||!mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(game.resolve("saves/Blacklight Standalone QA v4"))){disabled=true;throw new IllegalStateException("Tutorial capture refused non-QA save");}
        if(++ticks<50)return;
        if(!started){
            output=Path.of(value).toAbsolutePath().normalize();
            try{Files.createDirectories(output);}catch(Exception e){throw new RuntimeException(e);}
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.guiScale().set(2);mc.resizeDisplay();
            screen=new DemoScreen();mc.setScreen(screen);started=true;
        }
        if(error!=null)throw new RuntimeException(error);
        if(index>=IDS.size()&&writer.isTerminated()){
            try{Files.writeString(output.resolve("complete.json"),"{\"passed\":true,\"clips\":"+IDS.size()+",\"framesPerClip\":60}");}catch(Exception e){throw new RuntimeException(e);}
            done=true;mc.stop();
        }
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!started||done||index>=IDS.size()||mc.screen!=screen)return;
        try(var full=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var cropped=new NativeImage(320,180,false);double scale=mc.getWindow().getGuiScale();
            int x=(screen.width-320)/2,y=(screen.height-180)/2;
            for(int py=0;py<180;py++)for(int px=0;px<320;px++)cropped.setPixelRGBA(px,py,full.getPixelRGBA((int)((x+px)*scale),(int)((y+py)*scale)));
            var family=SkillTutorial.forSkill(IDS.get(index)).family();var file=output.resolve(family).resolve(String.format(java.util.Locale.ROOT,"%03d.png",frame));
            writer.submit(()->{try(cropped){Files.createDirectories(file.getParent());cropped.writeToFile(file);}catch(Throwable e){error=e;}});
        }
        if(++frame>=60){frame=0;index++;screen.actor=null;if(index>=IDS.size()){writer.shutdown();mc.setScreen(null);}}
    }
    private static final class DemoScreen extends Screen {
        RemotePlayer actor,victim;
        DemoScreen(){super(Component.literal("Tutorial capture"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            if(index>=IDS.size())return;
            var tutorial=SkillTutorial.forSkill(IDS.get(index));float t=frame/15F;
            if(actor==null){
                actor=new RemotePlayer(minecraft.level,minecraft.player.getGameProfile());
                var state=com.codex.blacklight.registry.ModAttachments.getData(actor, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(tutorial.form());state.activate(tutorial.form());com.codex.blacklight.registry.ModAttachments.setData(actor, ModAttachments.PLAYER_STATE,state);
                int utility=tutorial.family().equals("armor")?PowerForm.ARMOR.bit():tutorial.family().equals("shield")?PowerForm.SHIELD.bit():0;
                com.codex.blacklight.registry.ModAttachments.setData(actor, ModAttachments.UTILITY_POWERS,utility);actor.setOnGround(true);actor.tickCount=100;
                victim=new RemotePlayer(minecraft.level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("fc58491a-0000-0000-0000-000000000002"),"Target"));
                var vs=com.codex.blacklight.registry.ModAttachments.getData(victim, ModAttachments.PLAYER_STATE);vs.infect();vs.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.setData(victim, ModAttachments.PLAYER_STATE,vs);
            }
            int x=(width-320)/2,y=(height-180)/2;
            g.fill(0,0,width,height,0xFF101114);g.fill(x,y,x+320,y+180,0xFF24292E);
            for(int i=0;i<12;i++){int gx=x+i*32-(int)(tutorial.family().equals("sprint")?t*24:0)%32;g.fill(gx,y+143,gx+1,y+180,0xFF353C40);}
            for(int i=0;i<5;i++)g.fill(x,y+143+i*9,x+320,y+144+i*9,0xFF353C40);
            String clip=tutorial.clip();float seconds=t/4*Math.max(.1F,PlayerMotionController.duration(clip));
            if(tutorial.family().equals("jump")){
                clip=t<1.5?"basic_jump_charge":t<2.9?"basic_jump_rise":"basic_land_light";
                seconds=t<1.5?t/1.5F*PlayerMotionController.duration(clip):t<2.9?(t-1.5F)/1.4F*PlayerMotionController.duration(clip):(t-2.9F)/1.1F*PlayerMotionController.duration(clip);
            }
            if(PlayerMotionController.duration(clip)<=0)throw new IllegalStateException("Missing demo clip "+clip);
            PlayerMotionController.preview(actor,clip,seconds);
            if(tutorial.family().equals("mode")){
                var state=com.codex.blacklight.registry.ModAttachments.getData(actor, ModAttachments.PLAYER_STATE);state.unlock(PowerForm.CLAWS);state.activate(t>1&&t<3?PowerForm.CLAWS:PowerForm.NONE);
                PlayerMotionController.preview(actor,t>1&&t<3?"basic_idle_claw":"basic_idle",seconds);
            }
            if(tutorial.family().equals("disguise")){
                com.codex.blacklight.registry.ModAttachments.getData(actor, ModAttachments.PLAYER_STATE).captureIdentity("minecraft:zombie");
                com.codex.blacklight.registry.ModAttachments.setData(actor, ModAttachments.DISGUISE_IDENTITY,new com.codex.blacklight.disguise.DisguiseIdentity("minecraft:zombie","zombie",new net.minecraft.nbt.CompoundTag()));
                float progress=t<2?(t-.6F)/.8F:(t-2.6F)/.8F;
                com.codex.blacklight.registry.ModAttachments.getData(actor, ModAttachments.PLAYER_STATE).setDisguised(t>=1&&t<3);
                com.codex.blacklight.registry.ModAttachments.setData(actor, ModAttachments.DISGUISE_MORPH,progress>=0&&progress<1?new com.codex.blacklight.disguise.DisguiseMorph(actor.level().getGameTime()-(long)(progress*16),t<2):com.codex.blacklight.disguise.DisguiseMorph.EMPTY);
            }
            if(tutorial.family().equals("wall"))com.codex.blacklight.movement.WallPoseState.set(actor,com.codex.blacklight.movement.WallTraversal.Mode.RUN,0,1);
            int lift=tutorial.family().equals("jump")?(int)(t>1.5&&t<2.9?Math.sin((t-1.5)/1.4*Math.PI)*35:0):0;
            if(tutorial.family().equals("wall")){g.fill(x+207,y+4,x+232,y+176,0xFF474B4C);for(int i=0;i<9;i++)g.fill(x+207,y+i*20,x+232,y+i*20+1,0xFF727373);}
            int center=x+160,scale=56;
            if(tutorial.family().equals("consume")){
                center=x+141;PlayerMotionController.preview(victim,"consume_normal_victim",seconds);
                InventoryScreen.renderEntityInInventoryFollowsAngle(g,x+121,y+15,x+235,y+166,scale,0,-1.1F,.12F,victim);
            }
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,center-95,y+10-lift,center+95,y+166-lift,scale,0,-1.1F,.12F,actor);
            if(tutorial.family().equals("vision")){
                // A diagram of the sensing rule, separate from the pose demos.
                g.fill(x+50,y+38,x+270,y+147,0xFF19252F);
                for(int i=0;i<3;i++){
                    int bx=x+88+i*64,color=t>1&&t<3?(i==1?0xFFE46B38:0xFF88B9D1):0xFF6F7D86;
                    g.fill(bx,y+72,bx+13,y+85,color);g.fill(bx-3,y+86,bx+16,y+117,color);g.fill(bx-1,y+117,bx+5,y+135,color);g.fill(bx+8,y+117,bx+14,y+135,color);
                }
                g.fill(x+183,y+56,x+218,y+148,0xFF39434D);
                int scan=x+52+(int)(t/4*216);g.fill(scan,y+40,scan+1,y+146,0xFFB05043);
            }
            if(tutorial.family().equals("lock")){g.renderOutline(x+127,y+38,65,97,0xFFEB4642);}
            if(List.of("critical","health","adrenaline").contains(tutorial.family())){
                g.fill(x+55,y+13,x+265,y+20,0xFF111316);g.fill(x+56,y+14,x+186,y+19,0xFFC13232);
                int size=(int)(Math.min(1,t/2.5)*75);g.fill(x+186,y+14,x+186+size,y+19,tutorial.family().equals("critical")?0xFF489ABE:0xFFCD6660);
            }
            g.fill(x,y+178,x+(int)(320*(frame+1)/60F),y+180,0xFF9E403A);
        }
    }
}
