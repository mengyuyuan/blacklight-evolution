package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Shared source-pair selection and clock for capture and carrying. */
public final class GrappleMotion {
    private GrappleMotion() {}
    public static boolean active(ConsumeVisual visual){return visual.stage()==ConsumeVisual.CAPTURE||visual.stage()==ConsumeVisual.HOLD;}
    public static String key(LivingEntity owner,ConsumeVisual visual){
        if(visual.stage()==ConsumeVisual.CAPTURE)return "capture";
        double speed=owner.getDeltaMovement().horizontalDistanceSqr();
        double dx=owner.getX()-owner.xo,dz=owner.getZ()-owner.zo;
        boolean moving=speed>.0004||dx*dx+dz*dz>.0004;
        return moving?(owner.isSprinting()?"carry_run":"carry_walk"):"hold";
    }
    public static float progress(LivingEntity owner,ConsumeVisual visual,float partial){
        if(visual.stage()==ConsumeVisual.CAPTURE)return Math.clamp((owner.level().getGameTime()-visual.started()+partial-Math.max(0,visual.duration()-26))/26F,0,1);
        String key=key(owner,visual);
        int ticks=LocalVisualMotion.find("consume_"+key+"_actor").map(d->Math.max(1,d.durationTicks()-1)).orElse(30);
        return ((owner.level().getGameTime()-visual.started()+partial)%ticks)/ticks;
    }
    public static LivingEntity owner(LivingEntity entity,ConsumeVisual visual){
        var owner=entity.level().getEntity(visual.consumerId());return owner instanceof LivingEntity living?living:entity;
    }
    public static String sceneKey(LivingEntity entity,ConsumeVisual visual){
        if(active(visual))return key(owner(entity,visual),visual);
        var form=com.codex.blacklight.power.PowerForm.values()[Math.clamp(visual.form(),0,com.codex.blacklight.power.PowerForm.values().length-1)];
        return ConsumeSequence.clipKey(form,ConsumeSequence.mountedStage(visual.stage()));
    }
    public static float sceneProgress(LivingEntity entity,ConsumeVisual visual,float partial){
        return active(visual)?progress(owner(entity,visual),visual,partial):visual.motionProgress(entity.level().getGameTime(),partial);
    }
    /** The same body-relative grip is used by authority and receiver interpolation. */
    public static java.util.Optional<net.minecraft.world.phys.Vec3> carryAnchor(LivingEntity owner,LivingEntity victim,
            ConsumeVisual visual,net.minecraft.world.phys.Vec3 feet,float yaw,float partial) {
        boolean gliding=visual.stage()==ConsumeVisual.HOLD&&!owner.onGround()
                &&owner.getData(ModAttachments.GLIDE_UNTIL)>=owner.level().getGameTime();
        float weight=owner.getData(ModAttachments.MOVEMENT_STATE).carryPosture().sample(owner.tickCount,gliding,partial);
        var offset=net.minecraft.world.phys.Vec3.directionFromRotation(0,yaw).scale(.20*weight).add(0,-.25*weight,0);
        return ConsumePlacementTrack.carryAnchor(feet,yaw,victim,key(owner,visual),progress(owner,visual,partial)).map(anchor->anchor.add(offset));
    }
    public static float facing(LivingEntity entity,ConsumeVisual visual){return facing(entity,visual,1);}
    public static float facing(LivingEntity entity,ConsumeVisual visual,float partial){
        if(!active(visual))return visual.facing();
        var carrier=owner(entity,visual);
        return net.minecraft.util.Mth.rotLerp(partial,carrier.yRotO,carrier.getYRot());
    }
}
