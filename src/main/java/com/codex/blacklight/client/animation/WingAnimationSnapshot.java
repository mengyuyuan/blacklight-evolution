package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.movement.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;

/** One tick-owned wingbeat for both the body response and the accessory renderer. */
public final class WingAnimationSnapshot {
    private static final class State {
        final SovereignWingMotion.Controller clock=new SovereignWingMotion.Controller();
        int tick=Integer.MIN_VALUE;boolean airborne;int changed;float airFrom;
        SovereignWingMotion.Pose previous,current;
    }
    private static final Map<Player,State> STATES=new WeakHashMap<>();
    public static SovereignWingMotion.Pose sample(Player p,float partial) {
        var s=STATES.computeIfAbsent(p,k->new State());
        if(s.current==null||p.tickCount!=s.tick) {
            if(s.current!=null&&p.tickCount<s.tick)s.changed+=p.tickCount-s.tick;
            var f=p.getData(ModAttachments.WING_FLIGHT);
            float air=s.airFrom+((s.airborne?1:0)-s.airFrom)*SovereignFlightMotion.smooth((p.tickCount-s.changed)/24F);
            if(f.flying()!=s.airborne){s.airFrom=air;s.airborne=f.flying();s.changed=p.tickCount;}
            boolean equipped=WingFlightSystem.equipped(p);
            double morphAge=p.level().getGameTime()-f.transformedAt();
            float deployment=f.transformedAt()>0?WingMorphTimeline.progress(equipped,morphAge):equipped?1:0;
            air=Math.max(air,WingMorphTimeline.unfurl(equipped,morphAge));
            var v=p.getDeltaMovement();if(!p.isControlledByLocalInstance())v=p.position().subtract(p.xo,p.yo,p.zo);
            double yaw=Math.toRadians(p.yBodyRot);
            float speed=(float)Math.clamp(v.horizontalDistance()/1.25,0,1);
            float forward=(float)Math.clamp((-Math.sin(yaw)*v.x+Math.cos(yaw)*v.z)/.52,-1,1);
            float side=(float)Math.clamp((Math.cos(yaw)*v.x+Math.sin(yaw)*v.z)/.52,-1,1);
            float vertical=(float)Math.clamp(v.y/.52,-1,1);
            float turn=-net.minecraft.util.Mth.wrapDegrees(p.getYRot()-p.yRotO)/6;
            float age=(p.level().getGameTime()-f.started())/20F;
            float brake=f.phase()==WingFlightState.HOVER?1-SovereignFlightMotion.smooth(age/.65F):0;
            var dynamics=WingFlightVisuals.sample(p,1);
            brake=Math.max(brake,dynamics.brake());
            s.previous=s.current;
            s.current=s.clock.update(p.tickCount/20.,deployment,air,speed,forward,side,vertical,turn,brake,dynamics.thrust());
            s.current=SovereignWingMotion.streamline(s.current,dynamics.streamline()*air*(1-dynamics.brake())*(1-dynamics.thrust()*.35F));
            if(s.previous==null)s.previous=s.current;
            s.tick=p.tickCount;
        }
        return SovereignWingMotion.interpolate(s.previous,s.current,Math.clamp(partial,0,1));
    }
    private WingAnimationSnapshot(){}
}
