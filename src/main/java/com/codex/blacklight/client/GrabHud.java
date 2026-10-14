package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;

/** Keep the two available decisions visible for as long as a target is held. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class GrabHud {
    private GrabHud() {}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||mc.options.hideGui)return;
        var slime=com.codex.blacklight.power.BiomassSystem.state(mc.player);
        if(slime.active()){
            int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()-110;
            var g=event.getGuiGraphics();float integrity=com.codex.blacklight.power.BiomassEvolution.integrity(mc.player);
            String stage=slime.phase()==com.codex.blacklight.power.BiomassState.SHATTERING?"shattering":slime.consumed()?"reforming":slime.phase()==com.codex.blacklight.power.BiomassState.ABSORBING?"absorbing":slime.fatal()?"fatal":"voluntary";
            g.drawCenteredString(mc.font,Component.translatable("hud.blacklight.slime_"+stage),x,y,0xffe5ddd7);
            float amount=slime.phase()==com.codex.blacklight.power.BiomassState.ABSORBING?slime.progress(mc.level.getGameTime(),com.codex.blacklight.power.BiomassState.ABSORB_TICKS):integrity;
            g.fill(x-50,y+12,x+50,y+16,0xb0202020);g.fill(x-50,y+12,x-50+(int)(100*amount),y+16,0xffa53142);
            int life=com.codex.blacklight.power.BiomassEvolution.lifetime(mc.player);
            g.drawCenteredString(mc.font,Component.translatable(life<0?"hud.blacklight.slime_stable":"hud.blacklight.slime_decay",Math.max(0,(life-slime.age()+19)/20)),x,y+20,0xffc6b4b8);
            return;
        }
        var selected=ClientTerrainGrab.preview();
        if(!selected.isEmpty()){event.getGuiGraphics().drawCenteredString(mc.font,Component.translatable("hud.blacklight.terrain_select",selected.size()),mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()/2+42,0xffe5ddd7);}
        int stage=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.GRAB_STAGE);
        if(!ConsumeVisual.holding(stage))return;
        var block=com.codex.blacklight.combat.BlockThrowSystem.held(mc.player);
        if(block!=null){
            int tier=com.codex.blacklight.combat.BlockThrowSystem.level(mc.player),charge=block.chargeTicks();
            int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2+42;
            var g=event.getGuiGraphics();
            int cy=mc.getWindow().getGuiScaledHeight()/2;g.fill(x-4,cy,x+5,cy+1,0xffe5ddd7);g.fill(x,cy-4,x+1,cy+5,0xffe5ddd7);
            g.drawCenteredString(mc.font,Component.translatable("hud.blacklight.block_throw",tier,charge*100/com.codex.blacklight.combat.BlockThrowTuning.MAX_CHARGE_TICKS),x,y,0xffe5ddd7);
            g.fill(x-45,y+12,x+45,y+16,0xb0202020);
            g.fill(x-45,y+12,x-45+90*charge/com.codex.blacklight.combat.BlockThrowTuning.MAX_CHARGE_TICKS,y+16,0xffc52c36);
            String mode=com.codex.blacklight.client.SkillBindings.down(mc.options.keyShift)?"manual":block.aimTarget()>=0?"locked":"aim";
            g.drawCenteredString(mc.font,Component.translatable("hud.blacklight.throw_style",Component.translatable("hud.blacklight.throw_"+(block.twoHanded()?"two":"one")),Component.translatable("hud.blacklight.throw_"+mode)),x,y+20,0xffe5ddd7);
            return;
        }
        event.getGuiGraphics().drawCenteredString(mc.font,Component.translatable(com.codex.blacklight.combat.BlockThrowSystem.held(mc.player)!=null?"message.blacklight.block_held":stage==ConsumeVisual.MOUNT_HOLD?"message.blacklight.grab_mounted":"message.blacklight.grab_held"),
                mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()/2+42,0xffe5ddd7);
    }
}
