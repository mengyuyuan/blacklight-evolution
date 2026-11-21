package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Shared source-pair selection and clock for capture and carrying. */
public final class GrappleMotion {
    private GrappleMotion() {}
    /** The exposed Greene braces face-to-face before breaking the grip. Both actors keep their own root. */
    public static boolean greeneGrip(LivingEntity victim,ConsumeVisual visual) {
        return victim instanceof com.codex.blacklight.world.GreeneHost &&
                (visual.stage()==ConsumeVisual.CAPTURE || ConsumeVisual.carrying(visual.stage()));
    }
    public static int receiverTurn(LivingEntity victim,ConsumeVisual visual) {
        return active(visual) && !greeneGrip(victim,visual) ? 0 : 180;
    }
    public static boolean active(ConsumeVisual visual){return visual.stage()==ConsumeVisual.CAPTURE||visual.stage()==ConsumeVisual.HOLD||visual.stage()==ConsumeVisual.GRAB_ATTACK||visual.stage()==ConsumeVisual.AIR_THROW||visual.stage()==ConsumeVisual.AIR_HOLD;}
    public static String key(LivingEntity owner,ConsumeVisual visual){
        if(visual.stage()==ConsumeVisual.AIR_THROW)return AerialCarryMotion.THROW_KEY;
        if(visual.stage()==ConsumeVisual.GRAB_ATTACK)return HeldSlamMotion.KEY;
        if(visual.stage()==ConsumeVisual.CAPTURE)return "capture";
        if(visual.stage()==ConsumeVisual.AIR_HOLD)return "carry_air";
        double speed=owner.getDeltaMovement().horizontalDistanceSqr();
        double dx=owner.getX()-owner.xo,dz=owner.getZ()-owner.zo;
        boolean moving=speed>.0004||dx*dx+dz*dz>.0004;
        return moving?(owner.isSprinting()?"carry_sprint":"carry_walk"):"hold";
    }
    public static float progress(LivingEntity owner,ConsumeVisual visual,float partial){
        if(visual.stage()==ConsumeVisual.AIR_HOLD)return AerialCarryMotion.holdProgress(visual,owner.level().getGameTime(),partial);
        if(visual.stage()==ConsumeVisual.AIR_THROW)return AerialCarryMotion.progress(visual,owner.level().getGameTime(),partial);
        if(visual.stage()==ConsumeVisual.GRAB_ATTACK)return HeldSlamMotion.progress(visual,owner.level().getGameTime(),partial);
        if(visual.stage()==ConsumeVisual.CAPTURE)return com.codex.blacklight.util.MathCompat.clamp((owner.level().getGameTime()-visual.started()+partial-Math.max(0,visual.duration()-26))/26F,0,1);
        String key=key(owner,visual);
        int ticks=LocalVisualMotion.find("consume_"+key+"_actor").map(d->Math.max(1,d.durationTicks()-1)).orElse(30);
        return ((owner.level().getGameTime()-visual.started()+partial)%ticks)/ticks;
    }
    public static LivingEntity owner(LivingEntity entity,ConsumeVisual visual){
        var owner=entity.level().getEntity(visual.consumerId());return owner instanceof LivingEntity living?living:entity;
    }
    public static String sceneKey(LivingEntity entity,ConsumeVisual visual){
        if(active(visual))return key(owner(entity,visual),visual);
        var form=com.codex.blacklight.power.PowerForm.values()[com.codex.blacklight.forge.WingMath.clamp(visual.form(),0,com.codex.blacklight.power.PowerForm.values().length-1)];
        return ConsumeSequence.clipKey(form,ConsumeSequence.mountedStage(visual.stage()),visual.variant());
    }
    public static float sceneProgress(LivingEntity entity,ConsumeVisual visual,float partial){
        return active(visual)?progress(owner(entity,visual),visual,partial):visual.motionProgress(entity.level().getGameTime(),partial);
    }
    public static float receiverProgress(LivingEntity entity,ConsumeVisual visual,float partial){
        float progress=sceneProgress(entity,visual,partial);
        if(!visual.consuming())return progress;
        var forms=com.codex.blacklight.power.PowerForm.values();
        var form=forms[net.minecraft.util.Mth.clamp(visual.form(),0,forms.length-1)];
        if(ConsumePlacementTrack.villagerReceiver(entity))
            progress=ConsumePlacementTrack.villagerProgress(ConsumeSequence.key(form,visual.variant()),progress);
        return ConsumeSequence.receiverProgress(form,progress,ConsumeSequence.mountedStage(visual.stage()),visual.variant());
    }
    /** The same body-relative grip is used by authority and receiver interpolation. */
    public static java.util.Optional<net.minecraft.world.phys.Vec3> carryAnchor(LivingEntity owner,LivingEntity victim,
            ConsumeVisual visual,net.minecraft.world.phys.Vec3 feet,float yaw,float partial) {
        if(greeneGrip(victim,visual)) {
            var front=net.minecraft.world.phys.Vec3.directionFromRotation(0,yaw);
            double reach=(owner.getBbWidth()+victim.getBbWidth())*.5+.19;
            return java.util.Optional.of(feet.add(front.scale(reach)).add(-front.z*.10,0,front.x*.10));
        }
        boolean gliding=visual.stage()==ConsumeVisual.HOLD&&!owner.onGround()
                &&com.codex.blacklight.registry.ModAttachments.getData(owner, ModAttachments.GLIDE_UNTIL)>=owner.level().getGameTime();
        float weight=visual.stage()==ConsumeVisual.AIR_THROW?0:com.codex.blacklight.registry.ModAttachments.getData(owner, ModAttachments.MOVEMENT_STATE).carryPosture().sample(owner.tickCount,gliding,partial);
        var offset=net.minecraft.world.phys.Vec3.directionFromRotation(0,yaw).scale(.20*weight).add(0,-.25*weight,0);
        return ConsumePlacementTrack.carryAnchor(feet,yaw,victim,key(owner,visual),progress(owner,visual,partial)).map(anchor->anchor.add(offset));
    }
    public static float facing(LivingEntity entity,ConsumeVisual visual){return facing(entity,visual,1);}
    public static float facing(LivingEntity entity,ConsumeVisual visual,float partial){
        if(!active(visual)||visual.stage()==ConsumeVisual.GRAB_ATTACK||visual.stage()==ConsumeVisual.AIR_THROW)return visual.facing();
        var carrier=owner(entity,visual);
        return net.minecraft.util.Mth.rotLerp(partial,carrier.yRotO,carrier.getYRot());
    }
}
