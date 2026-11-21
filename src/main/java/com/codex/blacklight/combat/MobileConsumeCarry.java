package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Per-prey transfer from the actual last paired feet to a turning, shoulder-side carry socket. */
public record MobileConsumeCarry(int ownerId,long sceneStart,long handoff,Vec3 localFeet,int slot) {
    public static final MobileConsumeCarry EMPTY=new MobileConsumeCarry(-1,-1,-1,Vec3.ZERO,0);
    public static final StreamCodec<FriendlyByteBuf,MobileConsumeCarry> CODEC=new StreamCodec<>(){
        public MobileConsumeCarry decode(FriendlyByteBuf b){
            return new MobileConsumeCarry(b.readVarInt(),b.readVarLong(),b.readVarLong(),
                    new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt());
        }
        public void encode(FriendlyByteBuf b,MobileConsumeCarry v){
            b.writeVarInt(v.ownerId);b.writeVarLong(v.sceneStart);b.writeVarLong(v.handoff);
            b.writeDouble(v.localFeet.x);b.writeDouble(v.localFeet.y);b.writeDouble(v.localFeet.z);b.writeVarInt(v.slot);
        }
    };
    public static MobileConsumeCarry start(LivingEntity owner,LivingEntity prey,ConsumeVisual scene,int slot){
        Vec3 delta=prey.position().subtract(owner.position()).yRot((float)Math.toRadians(owner.yBodyRot));
        return new MobileConsumeCarry(owner.getId(),scene.started(),owner.level().getGameTime(),delta,slot);
    }
    public static MobileConsumeCarry state(LivingEntity prey,ConsumeVisual scene){
        if(scene.stage()!=ConsumeVisual.MOBILE_CONSUME||prey.isRemoved())return null;
        var data=ModAttachments.peekData(prey,ModAttachments.MOBILE_CONSUME_CARRY,EMPTY);
        return data.ownerId==scene.consumerId()&&data.sceneStart==scene.started()?data:null;
    }
    private static float ease(double t){float v=(float)Math.max(0,Math.min(1,t));return v*v*(3-2*v);}
    public Vec3 feet(Vec3 ownerFeet,float yaw,LivingEntity owner,LivingEntity prey,ConsumeVisual scene,double clock){
        double age=Math.max(0,clock-handoff);
        int finish=ConsumeSequence.completionTick(com.codex.blacklight.power.PowerForm.values()[scene.form()],false,scene.variant());
        double remaining=1-ease(age/Math.max(1,scene.started()+scene.landingDelay()+finish-handoff));
        double side=(slot%2==0?-1:1)*(owner.getBbWidth()*.51+.065+Math.min(.12,prey.getBbWidth()*.15)*remaining);
        double fore=.15+.06*remaining+(slot/2)*.12;
        double height=owner.getBbHeight()*(.63+.065*remaining)-(slot%2)*.075-Math.min(2,slot/2)*.06;
        Vec3 rest=new Vec3(side,height-prey.getBbHeight()*.54,fore);
        return ownerFeet.add(localFeet.lerp(rest,ease(age/5)).yRot((float)Math.toRadians(-yaw)));
    }
    public Vec3 predictedFeet(LivingEntity prey,LivingEntity owner,ConsumeVisual scene,float partial){
        Vec3 current=prey.getPosition(partial);
        float yaw=Mth.rotLerp(partial,owner.yBodyRotO,owner.yBodyRot);
        Vec3 goal=feet(owner.getPosition(partial),yaw,owner,prey,scene,prey.level().getGameTime()+partial);
        Vec3 delta=goal.subtract(current);
        if(delta.lengthSqr()>16)return current;
        Vec3 allowed=Entity.collideBoundingBox(prey,delta,prey.getBoundingBox().move(current.subtract(prey.position())),prey.level(),java.util.List.of());
        return current.add(allowed);
    }
    public static Vec3 center(LivingEntity prey,LivingEntity owner,ConsumeVisual scene,float partial){
        var state=state(prey,scene);
        if(state==null||owner.isRemoved()||prey.level()!=owner.level()||owner.getId()!=state.ownerId)return null;
        return state.predictedFeet(prey,owner,scene,partial).add(0,prey.getBbHeight()*.54,0);
    }
    /** Keep recognizable pieces inside the external weave until late in the return. */
    public static float contraction(float flow){
        return .58F*flow+.42F*ease((flow-.78F)/.22F);
    }
}
