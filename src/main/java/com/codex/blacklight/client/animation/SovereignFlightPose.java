package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.movement.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Full body playback; animation never moves the camera or entity position. */
public final class SovereignFlightPose {
    private static final class State {
        final SovereignFlightMotion.Transition transition=new SovereignFlightMotion.Transition();
        int phase;boolean braking;double time=-1;float speed,forward,side,vertical,bank,pathPitch;
    }
    private static final Map<Player,State> STATES=new WeakHashMap<>();
    public static BlacklightPose apply(LivingEntity entity,BlacklightPose original,float partial) {
        if(!(entity instanceof Player p))return original;
        if(!WingFlightSystem.equipped(p)||com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec()!=null||com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)) {STATES.remove(p);return original;}
        var flight=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WING_FLIGHT);
        if(!flight.flying()&&flight.phase()!=WingFlightState.LAND){STATES.remove(p);return original;}
        var s=STATES.computeIfAbsent(p,k->new State());
        double now=(p.tickCount+partial)/20.0,dt=s.time<0?0:Math.max(0,Math.min(.1,now-s.time));
        float age=(float)Math.max(0,(p.level().getGameTime()-flight.started()+partial)/20.0);
        var velocity=p.getDeltaMovement();
        if(!p.isControlledByLocalInstance())velocity=p.position().subtract(p.xo,p.yo,p.zo);
        double length=velocity.horizontalDistance(),yaw=Math.toRadians(p.yBodyRot);
        float forward=length>.01?(float)((-Math.sin(yaw)*velocity.x+Math.cos(yaw)*velocity.z)/length):0;
        float side=length>.01?(float)((-Math.cos(yaw)*velocity.x-Math.sin(yaw)*velocity.z)/length):0;
        float alpha=1-(float)Math.exp(-dt*10);
        // Use the same tick turn for all four committed pose samples. Updating a
        // yaw baseline on the first sample erased banking in the next three.
        float turn=net.minecraft.util.Mth.wrapDegrees(p.getYRot()-p.yRotO)*1.6F;
        s.speed+=((float)velocity.length()-s.speed)*alpha;s.forward+=(forward-s.forward)*alpha;s.side+=(side-s.side)*alpha;
        s.vertical+=((float)com.codex.blacklight.forge.WingMath.clamp(velocity.y/.52,-1,1)-s.vertical)*alpha;s.bank+=(com.codex.blacklight.forge.WingMath.clamp(turn,-22,22)-s.bank)*alpha;
        s.pathPitch+=(SovereignFlightMotion.pathPitch(length,velocity.y)-s.pathPitch)*alpha;
        if(now>s.time)s.time=now;
        if(s.phase!=flight.phase()){s.braking=flight.phase()==WingFlightState.HOVER&&(s.phase==WingFlightState.CRUISE||s.phase==WingFlightState.BOOST)&&s.speed>.12;s.phase=flight.phase();}
        var air=SovereignFlightMotion.airborne(now,s.speed,s.forward,s.side,s.vertical,s.bank,s.phase==WingFlightState.BOOST);
        SovereignFlightMotion.followFlightPath(air,s.pathPitch,s.speed,s.forward,s.vertical);
        BlacklightPose target=air;float blend=.32F;
        if(s.phase==WingFlightState.TAKEOFF){
            target=FlightAnimationClips.sample("takeoff",age);
            target=BlacklightPose.interpolatePose(target,air,SovereignFlightMotion.smooth((age-.65F)/.25F));blend=.1F;
        } else if(s.phase==WingFlightState.LAND){
            target=FlightAnimationClips.sample("landing",age);
            target=BlacklightPose.interpolatePose(target,original,SovereignFlightMotion.smooth((age-.55F)/.25F));blend=.1F;
        } else if(s.braking&&age<.8F){
            target=FlightAnimationClips.sample("brake",age);
            target=BlacklightPose.interpolatePose(target,air,SovereignFlightMotion.smooth((age-.4F)/.35F));blend=.16F;
        }
        float response=s.phase==WingFlightState.LAND?1-SovereignFlightMotion.smooth(age/.4F)
                :s.phase==WingFlightState.TAKEOFF?SovereignFlightMotion.smooth((age-.35F)/.4F):1;
        SovereignFlightMotion.followWingbeat(target,WingAnimationSnapshot.sample(p,partial),response);
        SovereignFlightMotion.followMomentum(target,WingFlightVisuals.sample(p,partial),s.forward,response);
        return s.transition.apply(Integer.toString(s.phase),now,blend,target,original);
    }
    private SovereignFlightPose(){}
}
