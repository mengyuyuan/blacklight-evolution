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
    private record HandContext(AbstractClientPlayer player,BlacklightPlayerRenderer renderer,float partial,HumanoidArm consumeArm,FirstPersonConsumeTendrils.Scene weave,boolean probeOnly) { }
    private static HandContext hands;
    public static boolean nativeHandPass(){return hands!=null;}
    public static boolean allowFirstPersonHands(boolean mountedVehicle){return !mountedVehicle;}
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;
        if(player!=null){
            var action=ModAttachments.getData(player,ModAttachments.PLAYER_ACTION);
            double clock=com.codex.blacklight.animation.PlayerMotionController.actionTime(player,event.getPartialTick());
            var pressure=com.codex.blacklight.client.animation.GoddessPressurePose.owner(player);
            if(pressure!=null || action.clipId().startsWith("greene_mouth_") && action.active(clock)){
                event.setCanceled(true);
                if(event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND){
                    var renderer=RENDERERS.get(player.getModelName());
                    var access=(com.codex.blacklight.mixin.client.NativeHandRendererAccess)mc.gameRenderer.itemInHandRenderer;
                    hands=renderer==null?null:new HandContext(player,renderer,event.getPartialTick(),player.getMainArm(),null,false);
                    try{
                        for(var side:HumanoidArm.values()){
                            var stack=event.getPoseStack();stack.pushPose();
                            try{
                                if(pressure==null)mouthHand(stack,side,action.clipId(),action.seconds(clock)*20);
                                else if(pressure.goddess.attacks.move()==com.codex.blacklight.combat.GoddessAttacks.Move.RUSH)
                                    com.codex.blacklight.client.animation.GoddessThrowActor.hand(stack,side,pressure.goddess.attacks,event.getPartialTick());
                                else pressureHand(stack,side,pressure.goddess.attacks.age(event.getPartialTick()),pressure.goddess.attacks.grounded());
                                access.blacklight$renderPlayerArm(stack,event.getMultiBufferSource(),event.getPackedLight(),0,0,side);
                            }finally{stack.popPose();}
                        }
                    }finally{hands=null;}
                }
                return;
            }
        }
        if(player==null||!allowFirstPersonHands(player.getVehicle() instanceof com.codex.blacklight.entity.MilitaryVehicle)||player.isScoping()||!com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).isPrototypeMode())return;
        if(com.codex.blacklight.power.BiomassSystem.active(player)){event.setCanceled(true);return;}
        if (com.codex.blacklight.item.GreeneTissueItem.isTissue(player.getItemInHand(event.getHand()))) return;
        var form=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).getActivePower();
        var morph=WeaponMorphTracker.sample(player,event.getPartialTick());
        if(morph.shoulder()>.001F&&event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND)
            com.codex.blacklight.client.renderer.layer.BackspineRack.firstPerson(player,event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight(),event.getPartialTick(),morph);
        boolean biological=com.codex.blacklight.client.animation.WeaponMorphTiming.weapon(form)||morph.visible(false)||morph.visible(true);
        boolean armored=UtilityPowerSystem.visualWeight(player,PowerForm.ARMOR,event.getPartialTick())>0;
        boolean shielded=UtilityPowerSystem.visualWeight(player,PowerForm.SHIELD,event.getPartialTick())>0;
        var weave=event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND
                ?FirstPersonConsumeTendrils.active(player,event.getPartialTick()):null;
        if(!biological&&!armored&&!shielded&&weave==null)return;
        var renderer=RENDERERS.get(player.getModelName());if(renderer==null)return;
        var access=(com.codex.blacklight.mixin.client.NativeHandRendererAccess)mc.gameRenderer.itemInHandRenderer;
        if(!biological&&!armored&&weave!=null){
            boolean bareMain=player.getMainHandItem().isEmpty(),freeOff=player.getOffhandItem().isEmpty()&&!shielded;
            if(!bareMain&&!freeOff&&weave.envelope()<=.004F&&!shielded)return;
            var arm=bareMain||!freeOff?player.getMainArm():player.getMainArm().getOpposite();
            // With two held items, sample the vanilla grip without submitting another arm or hiding an item.
            hands=new HandContext(player,renderer,event.getPartialTick(),arm,weave,!bareMain&&!freeOff);
            try{drawVanillaArm(access,event,arm);}finally{hands=null;}
            if(bareMain)event.setCanceled(true);
            if(!shielded)return;
            weave=null;
        }
        if(event.getHand()==net.minecraft.world.InteractionHand.OFF_HAND){event.setCanceled(true);return;}
        if(biological||armored)event.setCanceled(true);
        boolean drawLeft=armored||com.codex.blacklight.client.animation.WeaponMorphTiming.arm(form,true)||morph.visible(true);
        var tetherArm=drawLeft?player.getMainArm():HumanoidArm.RIGHT;
        hands=new HandContext(player,renderer,event.getPartialTick(),tetherArm,weave,false);
        try {
            if(biological||armored)drawVanillaArm(access,event,HumanoidArm.RIGHT);
            if(drawLeft)
                drawVanillaArm(access,event,HumanoidArm.LEFT);
            if(shielded)drawVanillaShield(access,event,renderer);
        }finally{hands=null;}
    }
    /** Advance one palm while the other bears weight, also for an unarmed first-person player. */
    private static void mouthHand(com.mojang.blaze3d.vertex.PoseStack stack,HumanoidArm arm,String clip,float t){
        boolean right=arm==HumanoidArm.RIGHT;
        float sign=right?1:-1;
        float lift,reach,roll;
        if(clip.equals("greene_mouth_kick")){
            float plant=beat(t,right?0:4,right?5:9),push=beat(t,9,17),clear=beat(t,18,26);
            lift=(.32F+.18F*plant-.13F*push)*(1-clear);reach=(.16F+.15F*plant-.22F*push)*(1-clear);
            roll=(sign*18+24*push)*(1-clear);
        }else if(clip.equals("greene_mouth_slip")){
            float claw=beat(t,right?0:3,right?4:8),drop=beat(t,right?6:10,18);
            lift=.28F+claw*.15F-.62F*drop;reach=.18F+.19F*claw-.32F*drop;roll=sign*(15+18*claw-43*drop);
        }else{
            float grip=beat(t,right?2:6,right?12:16);
            float tug=(float)Math.sin((t+(right?0:4))*.50);
            lift=grip*(.28F+.105F*tug);reach=grip*(.15F+.09F*tug);roll=sign*grip*(15+9*tug);
        }
        stack.translate(-sign*.10F,lift,-reach);
        stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
    }
    private static void pressureHand(com.mojang.blaze3d.vertex.PoseStack stack,HumanoidArm side,float age,boolean grounded){
        boolean right=side==HumanoidArm.RIGHT;
        float sign=right?1:-1;
        float brace=beat(age,right?28:32,right?34:39);
        float fold=beat(age,38,51);
        float release=1-beat(age,52,61);
        float shock=beat(age,28,31)*(1-beat(age,32,39));
        float effort=.018F*net.minecraft.util.Mth.sin(age*1.65F)*fold;
        stack.translate(sign*(.08F*brace-.41F*fold)*release,
                ((right?.37F:.23F)*brace-.13F*fold+effort)*release,
                (-.26F*brace+.21F*fold)*release);
        stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(release*(sign*(24*brace-47*fold)+(grounded?5:16)*shock)));
        stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees((-13*brace+22*fold)*release));
    }
    private static float beat(float t,float start,float end){
        float u=net.minecraft.util.Mth.clamp((t-start)/(end-start),0,1);
        return u*u*(3-2*u);
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
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void compatibleHeldArm(RenderArmEvent event) {
        if(hands!=null||ModAttachments.getData(event.getPlayer(),ModAttachments.PLAYER_STATE).isPrototypeMode()
                ||!(net.minecraftforge.fml.ModList.get().isLoaded("holdmyitemsnf")||net.minecraftforge.fml.ModList.get().isLoaded("holdmyitems"))
                ||!com.codex.blacklight.client.compat.YsmAppearanceBridge.selected(event.getPlayer()))return;
        var mesh=com.codex.blacklight.client.model.MercerPlayerMesh.get();if(mesh==null)return;
        event.setCanceled(true);
        mesh.renderHeldArm(event.getPoseStack(),event.getMultiBufferSource().getBuffer(
                net.minecraft.client.renderer.RenderType.entityCutoutNoCull(com.codex.blacklight.client.model.MercerPlayerMesh.TEXTURE)),
                event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,event.getArm()==HumanoidArm.LEFT);
    }
    @SubscribeEvent public static void arm(RenderArmEvent event) {
        var current=hands;if(current==null||current.player()!=event.getPlayer())return;
        boolean left=event.getArm()==HumanoidArm.LEFT;
        var morph=WeaponMorphTracker.sample(event.getPlayer(),current.partial());
        boolean covered=com.codex.blacklight.client.model.BlockArmorModel.get().covers(event.getPlayer(),left?"left_arm":"right_arm",current.partial());
        // Vanilla draws the remaining skin while liquid has not yet covered the arm.
        if(current.probeOnly()||covered||morph.coverage(left)>=.72F)event.setCanceled(true);
        current.renderer().renderPoweredArm(event.getPlayer(),event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight(),current.partial(),left,current.consumeArm()==event.getArm()?current.weave():null,current.probeOnly());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void player(RenderPlayerEvent.Pre event) {
        if(event.getRenderer() instanceof BlacklightPlayerRenderer)return;
        if(!(event.getEntity() instanceof AbstractClientPlayer player))return;
        float partial=event.getPartialTick();
        boolean ysm=com.codex.blacklight.client.compat.YsmAppearanceBridge.prepare(player,partial);
        if(com.codex.blacklight.power.BiomassSystem.active(player)){
            event.setCanceled(true);
            if(player==net.minecraft.client.Minecraft.getInstance().player&&net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(player.getPosition(partial).add(0,.15,0))<.16)return;
            BiomassPlayerRendering.render(player,partial,event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight());
            var slime=com.codex.blacklight.power.BiomassSystem.state(player);
            if(slime.phase()==com.codex.blacklight.power.BiomassState.SHATTERING){
                float f=slime.progress(player.level().getGameTime()+partial,com.codex.blacklight.power.BiomassState.SHATTER_TICKS);
                var renderer=RENDERERS.get(player.getModelName());
                if(renderer!=null&&f<.99F){var stack=event.getPoseStack();var fragments=new BiomassShatterBuffers(player,stack,event.getMultiBufferSource(),f,0,player.tickCount+partial);renderer.render(player,player.getYRot(),partial,stack,fragments,event.getPackedLight());fragments.finish();}
            }
            if(slime.consumed()){
                float f=slime.phase()==com.codex.blacklight.power.BiomassState.READY?1:slime.progress(player.level().getGameTime()+partial,com.codex.blacklight.power.BiomassState.REFORM_TICKS);
                var renderer=RENDERERS.get(player.getModelName());
                if(renderer!=null){var stack=event.getPoseStack();var coat=new BiomassCoatBuffers(player,stack,event.getMultiBufferSource(),1-BiomassSurfaceBounds.ease((f-.55F)/.45F),1-BiomassSurfaceBounds.ease(f/.8F),player.tickCount+partial);renderer.render(player,player.getYRot(),partial,stack,coat,event.getPackedLight());coat.finish();}
            }
            return;
        }
        var state=com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        boolean active=com.codex.blacklight.client.animation.BlacklightRenderPose.mouthStruggle(player,partial)
                ||com.codex.blacklight.client.animation.HydraPlayerPose.visible(player)||state.isPrototypeMode()
                ||UtilityPowerSystem.visualWeight(player,PowerForm.ARMOR,partial)>0
                ||UtilityPowerSystem.visualWeight(player,PowerForm.SHIELD,partial)>0
                ||com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CONSUME_VISUAL).active();
        if(!active)return;
        var renderer=RENDERERS.get(player.getModelName());if(renderer==null)return;
        if(ysm){renderer.renderYsmAttachments(player,partial,event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight());return;}
        event.setCanceled(true);
        renderer.render(player,player.getYRot(),partial,event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight());
    }
    @EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
            com.codex.blacklight.client.model.BlockArmorModel.invalidate();
            com.codex.blacklight.client.model.MercerPlayerMesh.invalidate();
            com.codex.blacklight.client.model.MercerArmorMesh.invalidate();
            com.codex.blacklight.client.compat.YsmAppearanceBridge.invalidate();
            RENDERERS.clear();
            for(var skin:event.getSkins())RENDERERS.put(skin,new BlacklightPlayerRenderer(event.getContext(),"slim".equals(skin)));
        }
        @SubscribeEvent public static void entities(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityType.ZOMBIE,InfectedZombieRenderer::new);
        }
    }
}
