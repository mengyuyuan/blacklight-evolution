package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.UtilityPowerSystem;
import com.codex.blacklight.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;

import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraft.world.entity.HumanoidArm;

/** Native renderer lifecycle. Each skin model has its own renderer and resource reload replaces caches. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class StandalonePlayerRendering {
    private static final Map<String,BlacklightPlayerRenderer> RENDERERS=new HashMap<>();
    private StandalonePlayerRendering() { }
    private record HandContext(AbstractClientPlayer player,BlacklightPlayerRenderer renderer,float partial) { }
    private static HandContext hands;
    public static boolean nativeHandPass(){return hands!=null;}
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;
        if(player==null||player.isScoping()||!com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).isPrototypeMode())return;
        var form=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).getActivePower();
        var morph=WeaponMorphTracker.sample(player,event.getPartialTick());
        boolean biological=com.codex.blacklight.client.animation.WeaponMorphTiming.weapon(form)||morph.visible(false)||morph.visible(true);
        boolean armored=UtilityPowerSystem.visualWeight(player,PowerForm.ARMOR,event.getPartialTick())>0;
        boolean shielded=UtilityPowerSystem.visualWeight(player,PowerForm.SHIELD,event.getPartialTick())>0;
        if(!biological&&!armored&&!shielded)return;
        var renderer=RENDERERS.get(player.getModelName());if(renderer==null)return;
        if(event.getHand()==net.minecraft.world.InteractionHand.OFF_HAND){event.setCanceled(true);return;}
        if(biological||armored)event.setCanceled(true);
        var access=(com.codex.blacklight.mixin.client.NativeHandRendererAccess)mc.gameRenderer.itemInHandRenderer;
        hands=new HandContext(player,renderer,event.getPartialTick());
        try {
            if(biological||armored)drawVanillaArm(access,event,HumanoidArm.RIGHT);
            if(armored||com.codex.blacklight.client.animation.WeaponMorphTiming.arm(form,true)||morph.visible(true))
                drawVanillaArm(access,event,HumanoidArm.LEFT);
            if(shielded)drawVanillaShield(access,event,renderer);
        }finally{hands=null;}
    }
    private static void drawVanillaArm(com.codex.blacklight.mixin.client.NativeHandRendererAccess access,RenderHandEvent event,HumanoidArm side) {
        var stack=event.getPoseStack();stack.pushPose();
        try{
            var player=net.minecraft.client.Minecraft.getInstance().player;boolean main=side==player.getMainArm();
            float equip=main?event.getEquipProgress():1-net.minecraft.util.Mth.lerp(event.getPartialTick(),access.blacklight$oldOffHandHeight(),access.blacklight$offHandHeight());
            float swing=main?event.getSwingProgress():player.swingingArm==net.minecraft.world.InteractionHand.OFF_HAND?player.getAttackAnim(event.getPartialTick()):0;
            access.blacklight$renderPlayerArm(stack,event.getMultiBufferSource(),event.getPackedLight(),equip,swing,side);
        }
        finally{stack.popPose();}
    }
    private static void drawVanillaShield(com.codex.blacklight.mixin.client.NativeHandRendererAccess access,RenderHandEvent event,BlacklightPlayerRenderer renderer) {
        var mc=net.minecraft.client.Minecraft.getInstance();var stack=event.getPoseStack();stack.pushPose();
        try {
            float equip=1-net.minecraft.util.Mth.lerp(event.getPartialTick(),access.blacklight$oldOffHandHeight(),access.blacklight$offHandHeight());
            access.blacklight$applyItemArmTransform(stack,HumanoidArm.LEFT,equip);
            var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD);
            var model=mc.getItemRenderer().getModel(item,mc.level,mc.player,mc.player.getId());
            model.getTransforms().getTransform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND).apply(true,stack);
            stack.translate(-.5,-.5,-.5);stack.scale(1,-1,-1);
            renderer.renderPoweredShield(mc.player,stack,event.getMultiBufferSource(),event.getPackedLight(),event.getPartialTick());
        }finally{stack.popPose();}
    }
    @SubscribeEvent public static void arm(RenderArmEvent event) {
        var current=hands;if(current==null||current.player()!=event.getPlayer())return;
        boolean left=event.getArm()==HumanoidArm.LEFT;
        var morph=WeaponMorphTracker.sample(event.getPlayer(),current.partial());
        boolean covered=com.codex.blacklight.client.model.BlockArmorModel.get().covers(event.getPlayer(),left?"left_arm":"right_arm",current.partial());
        // Vanilla draws the remaining skin while liquid has not yet covered the arm.
        if(covered||morph.coverage(left)>=.72F)event.setCanceled(true);
        current.renderer().renderPoweredArm(event.getPlayer(),event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight(),current.partial(),event.getArm()==HumanoidArm.LEFT);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void player(RenderPlayerEvent.Pre event) {
        if(event.getRenderer() instanceof BlacklightPlayerRenderer)return;
        if(!(event.getEntity() instanceof AbstractClientPlayer player))return;
        float partial=event.getPartialTick();
        var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        boolean active=state.isPrototypeMode()
                ||UtilityPowerSystem.visualWeight(player,PowerForm.ARMOR,partial)>0
                ||UtilityPowerSystem.visualWeight(player,PowerForm.SHIELD,partial)>0
                ||com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CONSUME_VISUAL).active();
        if(!active)return;
        var renderer=RENDERERS.get(player.getModelName());if(renderer==null)return;
        event.setCanceled(true);
        renderer.render(player,player.getYRot(),partial,event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight());
    }
    @EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
            com.codex.blacklight.client.model.BlockArmorModel.invalidate();
            RENDERERS.clear();
            for(var skin:event.getSkins())RENDERERS.put(skin,new BlacklightPlayerRenderer(event.getContext(),"slim".equals(skin)));
        }
        @SubscribeEvent public static void entities(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityType.ZOMBIE,InfectedZombieRenderer::new);
        }
    }
}
