package com.codex.blacklight.client;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Held bodies share the carrier's interpolation instead of lagging several network ticks behind. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientCarryPlacement {
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof LivingEntity victim))continue;
            var visual=victim.getData(ModAttachments.CONSUME_VISUAL);
            if(visual.stage()==ConsumeVisual.CONSUME){
                victim.setYRot(visual.facing());victim.yRotO=visual.facing();
                victim.setYHeadRot(visual.facing());victim.yHeadRotO=visual.facing();victim.yBodyRot=visual.facing();victim.yBodyRotO=visual.facing();
                victim.setDeltaMovement(Vec3.ZERO);victim.walkAnimation.setSpeed(0);
                continue;
            }
            if(visual.stage()!=ConsumeVisual.HOLD&&visual.stage()!=ConsumeVisual.THROW)continue;
            var carrier=mc.level.getEntity(visual.consumerId());if(!(carrier instanceof LivingEntity owner))continue;
            var next=carryAnchor(owner.position(),owner.getYRot(),owner.getXRot(),owner,victim,visual,1);
            var offset=next.subtract(victim.position());
            // Never predict through obstacles or bridge a broken/teleported restraint.
            double limit=correctionLimit(owner);
            if(limit<=0||offset.lengthSqr()>limit*limit)continue;
            var allowed=net.minecraft.world.entity.Entity.collideBoundingBox(victim,offset,victim.getBoundingBox(),mc.level,java.util.List.of());
            if(allowed.subtract(offset).lengthSqr()>1e-8)continue;
            var old=carryAnchor(owner.getPosition(0),owner.yRotO,owner.xRotO,owner,victim,visual,0);
            victim.setPos(next);victim.xo=old.x;victim.yo=old.y;victim.zo=old.z;
            float turn=GrappleMotion.active(visual)?0:180;
            // Retire the stale independent receiver interpolation while the server
            // explicitly assigns this body to its carrier. New release packets resume it.
            victim.lerpTo(next.x,next.y,next.z,owner.getYRot()+turn,victim.getXRot(),0);
            victim.setYRot(owner.getYRot()+turn);victim.yRotO=owner.yRotO+turn;
            victim.setYHeadRot(victim.getYRot());victim.yHeadRotO=victim.yRotO;victim.yBodyRot=victim.getYRot();victim.yBodyRotO=victim.yRotO;
        }
    }
    /** Render-time pairing avoids the receiver's network interpolation trailing the actor. */
    public static Vec3 consumeOffset(LivingEntity victim,float partial){
        var visual=victim.getData(ModAttachments.CONSUME_VISUAL);
        if(visual.stage()!=ConsumeVisual.CONSUME&&visual.stage()!=ConsumeVisual.HOLD)return Vec3.ZERO;
        var carrier=victim.level().getEntity(visual.consumerId());if(!(carrier instanceof LivingEntity owner))return Vec3.ZERO;
        var form=PowerForm.values()[Math.clamp(visual.form(),0,PowerForm.values().length-1)];
        Vec3 current=victim.getPosition(partial);
        Vec3 goal=visual.stage()==ConsumeVisual.HOLD
                ?carryAnchor(owner.getPosition(partial),GrappleMotion.facing(victim,visual,partial),
                    net.minecraft.util.Mth.lerp(partial,owner.xRotO,owner.getXRot()),owner,victim,visual,partial)
                :ConsumePlacementTrack.anchor(visual.origin(),visual.facing(),victim,form,visual.motionProgress(victim.level().getGameTime(),partial)).orElse(current);
        float motion=visual.motionProgress(victim.level().getGameTime(),partial);
        if(visual.stage()==ConsumeVisual.CONSUME&&form==PowerForm.CLAWS&&motion<ConsumePlacementTrack.gripEnd(form))
            goal=goal.add(owner.getPosition(partial).subtract(ConsumePlacementTrack.actorAnchor(visual.origin(),visual.facing(),form,motion,false)));
        Vec3 delta=goal.subtract(current);double limit=correctionLimit(owner);
        if(limit<=0||delta.lengthSqr()>limit*limit)return Vec3.ZERO;
        var hit=victim.level().clip(new net.minecraft.world.level.ClipContext(current.add(0,victim.getBbHeight()*.5,0),goal.add(0,victim.getBbHeight()*.5,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,victim));
        if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)return Vec3.ZERO;
        float enter=Math.clamp((victim.level().getGameTime()-visual.started()+partial)/4F,0,1);
        return delta.scale(enter);
    }
    private static double correctionLimit(LivingEntity owner){
        return com.codex.blacklight.movement.CarryInterpolation.correctionLimit(
                owner.getDeltaMovement().horizontalDistance(),owner.position().subtract(owner.getPosition(0)).length());
    }
    private static Vec3 anchor(Vec3 feet,float yaw,float pitch,LivingEntity victim,int form){
        return form==PowerForm.BLADE.ordinal()?GrabPlacement.bladeAnchor(feet,yaw,pitch,victim.getBbHeight(),0)
                :GrabPlacement.anchor(feet,yaw,pitch,victim.getBbWidth(),victim.getBbHeight());
    }
    private static Vec3 carryAnchor(Vec3 feet,float yaw,float pitch,LivingEntity owner,LivingEntity victim,ConsumeVisual visual,float partial){
        if(GrappleMotion.active(visual))return GrappleMotion.carryAnchor(owner,victim,visual,feet,yaw,partial).orElseGet(()->anchor(feet,yaw,pitch,victim,visual.form()));
        return anchor(feet,yaw,pitch,victim,visual.form());
    }
}
