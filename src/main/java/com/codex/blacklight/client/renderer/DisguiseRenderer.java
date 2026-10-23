package com.codex.blacklight.client.renderer;

import com.codex.blacklight.disguise.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderPlayerEvent;
import java.util.*;

/** Render-only species surrogate, never registered/ticked/spawned as a world entity. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class DisguiseRenderer {
    private static final Map<Player,Entry> CACHE=new WeakHashMap<>();
    private static final Set<Player> RENDERING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final class Entry {
        final DisguiseIdentity identity;final Mob avatar;int tick=Integer.MIN_VALUE;
        Entry(DisguiseIdentity i,Mob m){identity=i;avatar=m;}
    }
    private DisguiseRenderer(){}
    private static Mob avatar(Player p){
        var identity=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_IDENTITY);if(!identity.valid())return null;
        Entry entry=CACHE.get(p);
        if(entry==null||!entry.identity.equals(identity)||entry.avatar!=null&&entry.avatar.level()!=p.level()){
            Mob mob=null;
            try{
                var entity=identity.entityType().create(p.level());
                if(entity instanceof Mob m){
                    m.load(identity.appearance().copy());m.setHealth(m.getMaxHealth());m.setNoAi(true);
                    m.setCustomName(null);m.setCustomNameVisible(false);m.setInvisible(false);mob=m;
                }
            }catch(RuntimeException ex){com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Cannot render disguise {}",identity.type(),ex);}
            entry=new Entry(identity,mob);CACHE.put(p,entry);
        }
        var mob=entry.avatar;if(mob==null)return null;
        if(entry.tick!=p.tickCount){
            entry.tick=p.tickCount;
            float speed=(float)Math.min(1,p.getDeltaMovement().horizontalDistance()*4);
            if(p.onGround())speed=Math.max(speed,p.walkAnimation.speed());else speed=0;
            mob.walkAnimation.update(speed,.5F);
        }
        mob.setPos(p.getX(),p.getY(),p.getZ());mob.xo=p.xo;mob.yo=p.yo;mob.zo=p.zo;
        mob.tickCount=p.tickCount;mob.setYRot(p.getYRot());mob.yRotO=p.yRotO;
        mob.setXRot(p.getXRot());mob.xRotO=p.xRotO;
        Float previousFacing=p instanceof net.minecraft.client.player.AbstractClientPlayer cp
                ?com.codex.blacklight.client.PrototypeAnimationController.movementFacing(cp,0):null;
        Float facing=p instanceof net.minecraft.client.player.AbstractClientPlayer cp
                ?com.codex.blacklight.client.PrototypeAnimationController.movementFacing(cp,1):null;
        mob.yBodyRot=facing==null?p.yBodyRot:facing;mob.yBodyRotO=previousFacing==null?p.yBodyRotO:previousFacing;
        mob.yHeadRot=p.yHeadRot;mob.yHeadRotO=p.yHeadRotO;
        mob.setOnGround(p.onGround());mob.setDeltaMovement(p.getDeltaMovement());
        mob.hurtTime=p.hurtTime;mob.hurtDuration=p.hurtDuration;mob.deathTime=0;
        mob.attackAnim=p.attackAnim;mob.oAttackAnim=p.oAttackAnim;mob.swinging=p.swinging;
        // The surrogate never ticks AI. Copy presentation state explicitly so
        // its species renderer can select crouch, swim and attack poses.
        mob.setPose(p.getPose());mob.setShiftKeyDown(p.isShiftKeyDown());
        mob.setSprinting(p.isSprinting());mob.setSwimming(p.isSwimming());
        mob.setAggressive(p.swinging||p.attackAnim>0);
        return mob;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void render(RenderPlayerEvent.Pre e){
        Player p=e.getEntity();if(RENDERING.contains(p))return;
        boolean disguised=DisguiseSystem.active(p);var morph=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DISGUISE_MORPH);
        boolean transition=morph.active(p.level().getGameTime());
        if(!disguised&&!transition){CACHE.remove(p);return;}
        Mob avatar=avatar(p);if(disguised&&avatar==null)return;
        e.setCanceled(true);var stack=e.getPoseStack();stack.pushPose();RENDERING.add(p);
        try{
            float progress=transition?morph.progress(p.level().getGameTime(),e.getPartialTick()):0;
            float weight=transition?(float)Math.sin(progress*Math.PI):0;
            stack.scale(1-.08F*weight,1-.04F*weight,1-.08F*weight);
            if(disguised)renderAvatar(avatar,e);
            else if(p instanceof net.minecraft.client.player.AbstractClientPlayer cp)
                e.getRenderer().render(cp,p.getYRot(),e.getPartialTick(),stack,e.getMultiBufferSource(),e.getPackedLight());
            if(weight>.02F)cover(p,avatar,stack,e.getMultiBufferSource(),e.getPackedLight(),progress,weight);
        }finally{RENDERING.remove(p);stack.popPose();}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void renderAvatar(Mob avatar,RenderPlayerEvent.Pre e){
        var renderer=(net.minecraft.client.renderer.entity.EntityRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(avatar);
        var humanoid=renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living
                &&living.getModel() instanceof net.minecraft.client.model.HumanoidModel model?model:null;
        boolean crouching=humanoid!=null&&humanoid.crouching;
        // Epic Fight's real-world animator does not tick this visual surrogate.
        // Draw the animated species model once, without its frozen second rig.
        try(var scope=NativePlayerRenderScope.enter(avatar)) {
            if(humanoid!=null)humanoid.crouching=avatar.isCrouching();
            renderer.render(avatar,avatar.getYRot(),e.getPartialTick(),e.getPoseStack(),e.getMultiBufferSource(),e.getPackedLight());
        }finally{if(humanoid!=null)humanoid.crouching=crouching;}
    }
    /** Authored Minecraft adaptation: block tissue closes over both silhouettes before the swap. */
    private static void cover(Player p,Mob avatar,PoseStack stack,MultiBufferSource buffers,int light,float progress,float weight){
        float height=Math.max(p.getBbHeight(),avatar==null?0:avatar.getBbHeight());
        float width=Math.max(p.getBbWidth(),avatar==null?0:avatar.getBbWidth());
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        stack.pushPose();stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-p.yBodyRot));
        for(int row=0;row<12;row++){
            float local=net.minecraft.util.Mth.clamp(weight*2-Math.abs(row-5.5F)*.09F,0,1);
            if(local<=0)continue;
            float y=height*(row+.5F)/12;
            float radius=(width*.6F+.08F)*local;
            int color=row%3==0?0xFF4C1722:0xFF21151C;
            cube(stack,out,light,-radius,y-height/24,-radius,radius,y+height/24,radius,color);
            float curl=(float)Math.sin(progress*Math.PI*2+row*.8)*.035F*local;
            ConsumedMobVisuals.tube(stack,out,light,new net.minecraft.world.phys.Vec3(-radius,y,-radius),
                    new net.minecraft.world.phys.Vec3(radius+curl,y+.025,radius),.013*local,0xFF782536);
        }
        stack.popPose();
    }
    private static void cube(PoseStack s,VertexConsumer v,int light,float x0,float y0,float z0,float x1,float y1,float z1,int color){
        face(s,v,light,color,0,0,-1,new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}});
        face(s,v,light,color,0,0,1,new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}});
        face(s,v,light,color,-1,0,0,new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}});
        face(s,v,light,color,1,0,0,new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}});
        face(s,v,light,color,0,1,0,new float[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}});
        face(s,v,light,color,0,-1,0,new float[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}});
    }
    private static void face(PoseStack s,VertexConsumer v,int light,int color,float nx,float ny,float nz,float[][] points){
        for(var p:points)v.vertex((s.last()).pose(),p[0],p[1],p[2]).color(color).uv(.5F,.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal((s.last()).normal(),nx,ny,nz).endVertex();
    }
}
