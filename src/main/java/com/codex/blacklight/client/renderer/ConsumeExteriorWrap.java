package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.ClientCarryPlacement;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.codex.blacklight.client.renderer.ConsumeExteriorShape.*;

/** A single world-space draw after both third-person bodies have submitted their posed surfaces. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ConsumeExteriorWrap {
    private record Actor(Surface body,Surface right,Surface left,Vec3 forward){}
    private record Pulse(float weight,double age){}
    public record Draw(int owner,int victim,boolean paired,int ribbons,int points,double thickness,float weight){}
    public static java.util.function.Consumer<Draw> observer;
    private static final Map<AbstractClientPlayer,Actor> ACTORS=new IdentityHashMap<>();
    private static boolean capturing;
    private static boolean visible(AbstractClientPlayer actor){
        var mc=Minecraft.getInstance();
        return actor.isAlive()&&!actor.isInvisible()&&!actor.isSpectator()&&actor.level()==mc.level
                &&(actor!=mc.getCameraEntity()||!mc.options.getCameraType().isFirstPerson());
    }
    private static boolean wanted(AbstractClientPlayer actor){
        if(!capturing||StandalonePlayerRendering.nativeHandPass()||VisionPowerRenderer.drawingSilhouettes()||!visible(actor))return false;
        return actor.getData(ModAttachments.CONSUME_BODY_VISUAL).active()||actor.getData(ModAttachments.CONSUME_RESIDUE_VISUAL).active()
                ||ConsumeVisual.consumeStage(actor.getData(ModAttachments.GRAB_STAGE))
                ||actor.getData(ModAttachments.MOBILE_CONSUME_START)>=actor.level().getGameTime()-120 &&actor.getData(ModAttachments.MOBILE_CONSUME_START)>=0;
    }
    static void capture(AbstractClientPlayer actor,PoseStack stack,Matrix4f[] bones,float partial){
        if(!wanted(actor))return;
        var rig=PlayerMotionController.skeleton();
        Vec3 hipR=joint(stack,bones,rig.thighR.getId()),hipL=joint(stack,bones,rig.thighL.getId());
        Vec3 shoulderR=joint(stack,bones,rig.armR.getId()),shoulderL=joint(stack,bones,rig.armL.getId());
        var skin=ConsumeSocketFit.Skin.between(hipR,hipL,shoulderR.lerp(shoulderL,.5),shoulderR.distanceTo(shoulderL)*.51);
        if(skin==null)return;
        Vec3 facing=Vec3.directionFromRotation(0,Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot));
        Vec3 rightElbow=joint(stack,bones,rig.handR.getId()),leftElbow=joint(stack,bones,rig.handL.getId());
        var right=Surface.arm(rightElbow,joint(stack,bones,rig.searchJointByName("Tool_R").getId()),skin.side());
        var left=Surface.arm(leftElbow,joint(stack,bones,rig.searchJointByName("Tool_L").getId()),skin.side());
        ACTORS.put(actor,new Actor(Surface.torso(skin,skin.width()+.034,skin.depth()+.034),right,left,facing));
    }
    private static Vec3 joint(PoseStack stack,Matrix4f[] bones,int id){
        return BlacklightBoneRendering.worldPoint(stack,bones[id].getTranslation(new Vector3f()));
    }
    /** A disguise renders a separate species surrogate and cancels the native player's layer.
     * Measure it in that draw's own model frame, including its pose, yaw, baby scale and morph scaling. */
    static void captureDisguise(AbstractClientPlayer owner,Mob surrogate,PoseStack stack,float partial){
        if(!wanted(owner))return;
        double h=surrogate.getBbHeight(),w=surrogate.getBbWidth();
        Vec3 forward=Vec3.directionFromRotation(0,Mth.rotLerp(partial,surrogate.yBodyRotO,surrogate.yBodyRot));
        Vec3 left=new Vec3(forward.z,0,-forward.x);
        boolean tall=h>w*1.65;
        Vec3 feet=BlacklightBoneRendering.worldPoint(stack,new Vector3f());
        Vec3 center=BlacklightBoneRendering.worldPoint(stack,new Vector3f(0,(float)(h*(tall?.64:.52)),0));
        Vec3 top=BlacklightBoneRendering.worldPoint(stack,new Vector3f(0,(float)h,0));
        Vec3 wing=BlacklightBoneRendering.worldPoint(stack,left.scale(w*.48).toVector3f()).subtract(feet);
        Vec3 front=BlacklightBoneRendering.worldPoint(stack,forward.scale(w*(tall?.35:.59)).toVector3f()).subtract(feet);
        var body=new Surface(center,top.subtract(feet).normalize(),wing.normalize(),front.normalize(),wing.length(),front.length(),h*(tall?.21:.33));
        ACTORS.put(owner,new Actor(body,null,null,front.normalize()));
    }
    private static Pulse pulse(ConsumeVisual scene,long now,float partial){
        float time=scene.elapsed(now,partial)/20F;
        return new Pulse(ConsumeGiverEffect.exterior(scene,time),time-ConsumeGiverEffect.exteriorStart(scene));
    }
    private static Surface receiver(LivingEntity prey,float partial,float extent){
        var physics=AdaptiveRagdoll.exteriorTorso(prey,partial);
        if(physics!=null)return physics;
        if(extent>=0)return null; // No physical torso submitted in this frame. Do not bind to an absent limb.
        var joints=ChestConsumeTendrils.receiverPoints(prey,partial);var skin=ConsumeSocketFit.receiverSkin(joints);
        if(skin==null)return null;
        double w=prey.getBbWidth(),h=prey.getBbHeight();
        if(h>w*1.65)return Surface.torso(skin,Math.min(w*.56,Math.max(skin.width(),w*.405)),Math.max(skin.depth(),w*.29));
        Vec3 feet=prey.getPosition(partial).add(ClientCarryPlacement.consumeOffset(prey,partial));
        Vec3 center=feet.add(ConsumeVictimPose.effectPoint(prey,new Vec3(0,h*.53,0),partial));
        return new Surface(center,skin.up(),skin.side(),skin.front(),w*.47,w*.57,Math.min(skin.height(),h*.32));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){ACTORS.clear();capturing=true;return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        capturing=false;
        var mc=Minecraft.getInstance();if(mc.level==null||ACTORS.isEmpty())return;
        var camera=event.getCamera().getPosition();
        var stack=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        long now=mc.level.getGameTime();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var type=RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE);
        // Select the strongest active event once for this actor, even when two previously claimed prey travel together.
        var actorPulses=new IdentityHashMap<AbstractClientPlayer,Pulse>();
        for(var owner:ACTORS.keySet()){
            var scene=owner.getData(ModAttachments.CONSUME_BODY_VISUAL);
            if(scene.active()&&scene.consumerId()==owner.getId()){
                var pulse=pulse(scene,now,partial);if(pulse.weight>0)actorPulses.put(owner,pulse);
            }
        }
        for(var e:mc.level.entitiesForRendering()){
            if(!(e instanceof LivingEntity prey)||prey.isRemoved()||!prey.isAlive()||prey.isInvisible()||prey.distanceToSqr(camera)>64*64)continue;
            var scene=prey.getData(ModAttachments.CONSUME_VISUAL);
            if((scene.stage()!=ConsumeVisual.CONSUME&&scene.stage()!=ConsumeVisual.MOBILE_CONSUME)
                    ||!(mc.level.getEntity(scene.consumerId()) instanceof AbstractClientPlayer owner))continue;
            var actor=ACTORS.get(owner);if(actor==null||!ConsumeSystem.smallDirectReceiver(prey))continue;
            var pulse=pulse(scene,now,partial);if(!(pulse.weight>0))continue;
            actorPulses.merge(owner,pulse,(a,b)->a.weight>=b.weight?a:b);
            float extent=AdaptiveRagdoll.carrySkinFraction(prey,partial);
            if(extent>=0&&extent<.065F)continue;
            var target=receiver(prey,partial,extent);if(target==null)continue;
            Vec3 delta=target.center().subtract(actor.body.center());double distance=delta.length();
            // Weapons may launch or throw the receiver first. The tissue takes its weight only near the player.
            float near=(float)Mth.clamp((2.04-distance)/.75,0,1);
            float strength=pulse.weight*near*(extent<0?1:Mth.clamp((extent-.065F)/.32F,0F,1F));
            if(strength<.016)continue;
            Vec3 direction=delta.subtract(actor.body.up().scale(delta.dot(actor.body.up()))).add(actor.forward.scale(.18)).normalize();
            if(direction.lengthSqr()<.1)direction=actor.forward;
            var bodies=new Bodies(actor.body,target,actor.right,actor.left,direction,drag(owner));
            paint(stack,buffers.getBuffer(type),mc.getEntityRenderDispatcher().getPackedLightCoords(prey,partial),camera,owner.getId(),
                    prey.getId(),PAIR,bodies,strength,pulse.age);
        }
        for(var entry:actorPulses.entrySet()){
            var owner=entry.getKey();if(owner.distanceToSqr(camera)>64*64)continue;
            var actor=ACTORS.get(owner);var pulse=entry.getValue();
            var bodies=new Bodies(actor.body,null,actor.right,actor.left,actor.forward,drag(owner));
            paint(stack,buffers.getBuffer(type),mc.getEntityRenderDispatcher().getPackedLightCoords(owner,partial),camera,owner.getId(),-1,
                    GIVER,bodies,pulse.weight,pulse.age);
        }
        boolean drewResidue=false;
        for(var entry:ACTORS.entrySet()){
            var owner=entry.getKey();if(owner.distanceToSqr(camera)>64*64)continue;
            var settled=owner.getData(ModAttachments.CONSUME_RESIDUE_VISUAL);
            var active=owner.getData(ModAttachments.CONSUME_BODY_VISUAL);
            float settledWeight=residue(owner,settled,now,partial),activeWeight=residue(owner,active,now,partial);
            float strength=Math.max(settledWeight,activeWeight);if(!(strength>.012F))continue;
            var scene=settledWeight>=activeWeight?settled:active;var actor=entry.getValue();
            // The same flank bears the load during consecutive claims, even when their pulses cross.
            // The blade is fitted to Tool_R. Keep the left scapula free to silhouette the absorbed mass.
            boolean blade=scene.form()==com.codex.blacklight.power.PowerForm.BLADE.ordinal()
                    ||owner.getData(ModAttachments.PLAYER_STATE).getActivePower()==com.codex.blacklight.power.PowerForm.BLADE;
            boolean flip=!blade&&(owner.getUUID().getLeastSignificantBits()&1L)!=0;
            var strips=ConsumeResidueKnot.folds(actor.body,actor.forward,flip,strength,scene.elapsed(now,partial)/20F,drag(owner));
            var out=buffers.getBuffer(type);int light=mc.getEntityRenderDispatcher().getPackedLightCoords(owner,partial);
            int points=0;double radius=0;
            for(var strip:strips){
                var path=strip.points();
                for(int j=0;j<path.length;j++){path[j]=path[j].subtract(camera);radius=Math.max(radius,strip.widths()[j]);}
                ConsumeTendrilSegmentMesh.draw(stack,out,light,path,strip.widths(),actor.body.side(),0xffffffff);
                points+=path.length;
            }
            if(observer!=null)observer.accept(new Draw(owner.getId(),-2,false,strips.length,points,radius,strength));
            drewResidue=true;
        }
        if(drewResidue||!actorPulses.isEmpty())buffers.endBatch(type);
        ACTORS.clear();
    }
    private static float residue(AbstractClientPlayer owner,ConsumeVisual scene,long now,float partial){
        return scene.active()&&scene.consumerId()==owner.getId()?ConsumeGiverEffect.residue(scene,scene.elapsed(now,partial)/20F):0;
    }
    private static Vec3 drag(LivingEntity owner){
        Vec3 delta=owner.getPosition(1).subtract(owner.getPosition(0));
        return delta.lengthSqr()>.12?delta.normalize().scale(.345):delta;
    }
    private static void paint(PoseStack stack,VertexConsumer out,int light,Vec3 camera,int owner,int prey,
                              Ribbon[] ribbons,Bodies bodies,float weight,double age){
        int count=0,points=0;double radius=0;
        for(int i=0;i<ribbons.length;i++){
            var strip=strip(ribbons[i],i,bodies,weight,age);if(strip==null)continue;
            var path=strip.points();for(int j=0;j<path.length;j++)path[j]=path[j].subtract(camera);
            ConsumeTendrilSegmentMesh.draw(stack,out,light,path,strip.widths(),bodies.giver().side(),0xffffffff);
            count++;points+=path.length;radius=Math.max(radius,ribbons[i].width());
        }
        if(observer!=null&&count>0)observer.accept(new Draw(owner,prey,prey>=0,count,points,radius,weight));
    }
    private ConsumeExteriorWrap(){}
}
