package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import java.util.*;

/** Draw after both entities: use the animated arm's actual rendered transform, with no one-frame attachment lag. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class WhipTetherRenderer {
    private static final Map<Integer,Vec3> HANDS=new HashMap<>();
    private static final Map<Integer,Vec3> GRIPS=new HashMap<>();
    private static Vec3 camera=Vec3.ZERO;
    private WhipTetherRenderer() {}
    public static void capture(AbstractClientPlayer player,PoseStack handStack) {
        if(VisionPowerRenderer.drawingSilhouettes())return;
        // The hook's authored local tip. This stack is the very same one used to draw the right forearm.
        // LevelRenderer puts view rotation in RenderSystem's global matrix. Entity stacks are already camera-relative world space.
        HANDS.put(player.getId(),BlacklightBoneRendering.worldPoint(handStack,new Vector3f(0,.30F,.05F)));
    }
    public static void capture(AbstractClientPlayer player,PoseStack stack,Vector3f anchor) {
        if(VisionPowerRenderer.drawingSilhouettes())return;
        Vec3 point=BlacklightBoneRendering.worldPoint(stack,anchor);
        Vec3 right=BlacklightBoneRendering.worldPoint(stack,new Vector3f(anchor).add(1,0,0)).subtract(point).normalize();
        Vec3 forward=BlacklightBoneRendering.worldPoint(stack,new Vector3f(anchor).add(0,1,0)).subtract(point).normalize();
        if(StandalonePlayerRendering.nativeHandPass()){
            firstPerson=new Wrist(cameraVector(point.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()),true),
                    cameraVector(right,true),cameraVector(forward,true));
            return;
        }
        WRISTS.put(player.getId(),new Wrist(point,right,forward));HANDS.put(player.getId(),point);
    }
    public static Vec3 capturedHand(int entityId) { return HANDS.get(entityId); }
    public static boolean extended(AbstractClientPlayer player){
        if(player.getData(ModAttachments.PLAYER_STATE).getActivePower()!=PowerForm.WHIPFIST)return false;
        var visual=player.getData(ModAttachments.WEAPON_VISUAL);var move=visual.spec();
        return move!=null&&move.form==PowerForm.WHIPFIST&&!visual.traveling()||deployedGrab(player);
    }
    /** Actual rendered claw palm, also used by the opt-in contact acceptance harness. */
    public static void captureGrip(AbstractClientPlayer player,PoseStack stack) {
        if(VisionPowerRenderer.drawingSilhouettes())return;
        GRIPS.put(player.getId(),BlacklightBoneRendering.worldPoint(stack,new Vector3f(.00267F,.32362F,.07666F)));
    }
    public static void captureGrip(AbstractClientPlayer player,PoseStack stack,Vector3f anchor) {
        if(VisionPowerRenderer.drawingSilhouettes())return;
        GRIPS.put(player.getId(),BlacklightBoneRendering.worldPoint(stack,anchor));
    }
    public static void captureBlade(AbstractClientPlayer player,PoseStack stack) {
        if(VisionPowerRenderer.drawingSilhouettes())return;
        GRIPS.put(player.getId(),BlacklightBoneRendering.worldPoint(stack,new Vector3f(-.0636396F,.68F,-.0636396F)));
    }
    public static Vec3 capturedGrip(int entityId) { return GRIPS.get(entityId); }
    private record Wrist(Vec3 position,Vec3 right,Vec3 forward){}
    private record Grab(LivingEntity receiver,WhipGrabPath.Deployment amount) {}
    private static final Map<Integer,Wrist> WRISTS=new HashMap<>();
    private static final Map<Integer,Grab> GRABS=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static Wrist firstPerson;
    public record Draw(int owner,int victim,int stage,double extension,double grip,Vec3 wrist,Vec3 tip,Vec3 body,boolean measuredWrist){}
    public static java.util.function.Consumer<Draw> observer;
    /** Exact terminal groups move from the equipped forearm to the single deployed skin. */
    public static boolean deployedGrab(AbstractClientPlayer player){return GRABS.containsKey(player.getId());}
    private static Vec3 cameraVector(Vec3 v,boolean inverse){
        var q=new org.joml.Quaternionf(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        if(inverse)q.conjugate();
        return new Vec3(q.transform(v.toVector3f()));
    }
    private static void scan(Minecraft mc,float partial) {
        HANDS.clear();GRIPS.clear();WRISTS.clear();GRABS.clear();
        if(level!=mc.level){level=mc.level;firstPerson=null;}
        if(level==null)return;
        if(!mc.options.getCameraType().isFirstPerson())firstPerson=null;
        for(var entity:level.entitiesForRendering()){
            if(!(entity instanceof LivingEntity mob))continue;
            var data=mob.getData(ModAttachments.CONSUME_VISUAL);
            if(!data.active()||!data.drivesActor(data.consumerId())||data.form()!=PowerForm.WHIPFIST.ordinal())continue;
            if(!(level.getEntity(data.consumerId()) instanceof AbstractClientPlayer player)
                    ||player.getData(ModAttachments.PLAYER_STATE).getActivePower()!=PowerForm.WHIPFIST
                    ||player.getData(ModAttachments.GRAB_STAGE)!=data.stage())continue;
            var phase=data.consuming()?ConsumeFracture.phase(mob,partial):null;
            var amount=WhipGrabPath.deployment(data.stage(),data.elapsed(level.getGameTime(),partial),
                    phase==null?0:phase.opening(),phase==null?0:phase.returnFlow());
            if(amount.reach()<=.003)continue;
            var prior=GRABS.get(player.getId());
            if(prior==null||prior.receiver().getData(ModAttachments.CONSUME_VISUAL).started()<data.started())
                GRABS.put(player.getId(),new Grab(mob,amount));
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){
            camera=event.getCamera().getPosition();scan(mc,partial);return;
        }
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||mc.level==null||GRABS.isEmpty())return;
        var buffers=mc.renderBuffers().bufferSource();var stack=event.getPoseStack();
        for(var entry:GRABS.entrySet()){
            if(!(mc.level.getEntity(entry.getKey()) instanceof AbstractClientPlayer owner))continue;
            var grab=entry.getValue();var mob=grab.receiver;
            var wrist=WRISTS.get(owner.getId());
            if(owner==mc.player&&mc.options.getCameraType().isFirstPerson()&&firstPerson!=null)
                wrist=new Wrist(camera.add(cameraVector(firstPerson.position,false)),cameraVector(firstPerson.right,false),cameraVector(firstPerson.forward,false));
            boolean measured=wrist!=null;
            if(wrist==null){
                var forward=Vec3.directionFromRotation(0,owner.getYRot());var right=new Vec3(-forward.z,0,forward.x);
                wrist=new Wrist(owner.getPosition(partial).add(0,1.13,0).add(right.scale(.34)).add(forward.scale(.21)),right,forward);
            }
            var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
            var placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(mob,partial);
            var transform=ConsumeVictimPose.effectTransform(mob,partial);
            Vec3 up=new Vec3(transform.transformDirection(new Vector3f(0,1,0))).normalize();
            var facing=Vec3.directionFromRotation(0,net.minecraft.util.Mth.rotLerp(partial,mob.yBodyRotO,mob.yBodyRot));
            Vec3 front=new Vec3(transform.transformDirection(facing.toVector3f())).normalize();
            Vec3 center=mob.getPosition(partial).add(placement).add(new Vec3(transform.transformPosition(new Vector3f(0,mob.getBbHeight()*.55F,0))));
            var physical=AdaptiveRagdoll.contact(mob,32,partial);
            if(physical!=null)center=physical;
            // Seat the first full ring inside the retained forearm core at tether_R.
            var socket=wrist.forward.scale(-.11);
            var path=new WhipGrabPath(socket,wrist.right,wrist.forward,center.subtract(wrist.position),up,front,
                    mob.getBbWidth(),mob.getBbHeight(),grab.amount);
            stack.pushPose();
            try{
                stack.translate(wrist.position.x-camera.x,wrist.position.y-camera.y,wrist.position.z-camera.z);
                int light=mc.getEntityRenderDispatcher().getPackedLightCoords(mob,partial);
                WhipGrabMesh.INSTANCE.draw(owner,partial,path,stack,buffers,light);
                if(observer!=null)observer.accept(new Draw(owner.getId(),mob.getId(),visual.stage(),grab.amount.reach(),grab.amount.grip(),
                        wrist.position,wrist.position.add(path.frame(WhipGrabPath.BANDS).center()),center,measured));
            }finally{stack.popPose();}
        }
        WhipGrabMesh.INSTANCE.flush(buffers);
    }
}
