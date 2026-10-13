package com.codex.blacklight.client;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.*;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in local visual acceptance, never included in the normal artifact. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WeaponModelPreview {
    private static int ticks;
    private static final PowerForm[] FORMS={PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS};
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        String output=System.getProperty("blacklight.test.weaponModels");
        if(output==null||mc.player==null||mc.level==null||mc.player.tickCount<40||mc.getSingleplayerServer()==null)return;
        int frame=ticks++;if(frame>=FORMS.length*100)return;
        int phase=frame/100;
        if(frame%100==0){
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;
            if(frame==0)mc.setScreen(new InspectionScreen());
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);if(p==null)return;
                var s=p.getData(ModAttachments.PLAYER_STATE);if(!s.isInfected())s.infect();s.setPrototypeMode(true);
                for(var f:FORMS)s.unlock(f);s.activate(FORMS[phase]);p.setData(ModAttachments.PLAYER_STATE,s);p.syncData(ModAttachments.PLAYER_STATE);
                p.setGameMode(GameType.CREATIVE);
                if(frame==0){
                    var pos=p.blockPosition();int y=p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
                    p.teleportTo(pos.getX()+.5,y+.05,pos.getZ()+.5);p.setYRot(0);p.setXRot(0);
                }
                p.serverLevel().setDayTime(6000);
            });
        }
        if(frame%100==80){
            new java.io.File(output,"screenshots").mkdirs();
            Screenshot.grab(new java.io.File(output),FORMS[phase].id()+".png",mc.getMainRenderTarget(),m->{});
            com.mojang.logging.LogUtils.getLogger().info("MC weapon model accepted for visual inspection: {}",FORMS[phase]);
        }
        if(frame==FORMS.length*100-1){mc.options.hideGui=false;if(mc.screen instanceof InspectionScreen)mc.setScreen(null);com.mojang.logging.LogUtils.getLogger().info("MC weapon preview finished; ready for free play.");}
    }

    /** Same player renderer/materials as the world, with repeatable inspection angles. */
    private static final class InspectionScreen extends net.minecraft.client.gui.screens.Screen {
        InspectionScreen(){super(net.minecraft.network.chat.Component.literal("Weapon model inspection"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xFF242D35);
            int phase=Math.min(FORMS.length-1,ticks/100);
            g.drawCenteredString(font,"MC runtime / "+FORMS[phase].id(),width/2,8,0xFFFFFF);
            var p=Minecraft.getInstance().player;if(p==null)return;
            for(int i=0;i<3;i++){
                int a=i*width/3,b=(i+1)*width/3;
                g.drawCenteredString(font,new String[]{"Front","Outer side","Back"}[i],(a+b)/2,24,0xFFFFFF);
                g.enableScissor(a,38,b,height-10);
                // Rotate the whole draw, since the combat renderer owns body facing
                // independently of the vanilla inventory's yBodyRot override.
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
                    g,(a+b)/2F,(height+28)/2F,Math.min((height-65)/3,(b-a)/2),
                    new org.joml.Vector3f(0,p.getBbHeight()/2,0),
                    new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(i*(float)Math.PI/2),
                    new org.joml.Quaternionf(),p);
                g.disableScissor();
            }
        }
    }
}
