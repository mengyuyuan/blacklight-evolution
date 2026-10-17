package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.WingFlightPresentation;
import com.codex.blacklight.movement.WingFlightSystem;
import com.codex.blacklight.movement.WingFlightState;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;

/** One observer-local tick snapshot shared by body, wings, wake and camera. */
public final class WingFlightVisuals {
    private static final class State {
        final WingFlightPresentation.Controller controller=new WingFlightPresentation.Controller();
        int tick=Integer.MIN_VALUE;
        WingFlightPresentation.Frame previous=WingFlightPresentation.Frame.STILL,current=previous;
    }
    private static final Map<Player,State> STATES=new WeakHashMap<>();
    public static WingFlightPresentation.Frame sample(Player player,float partial){
        var s=STATES.computeIfAbsent(player,p->new State());
        if(s.tick!=player.tickCount){
            var v=player.isControlledByLocalInstance()?player.getDeltaMovement():player.position().subtract(player.xo,player.yo,player.zo);
            float speed=(float)v.length();
            if(speed>com.codex.blacklight.movement.WingFlightTuning.tier(WingFlightSystem.tier(player)).boostSpeed()*1.5+1)speed=0;
            s.previous=s.current;
            s.current=s.controller.update(player.tickCount/20.,speed,WingFlightSystem.flying(player),player.getData(ModAttachments.WING_FLIGHT).phase()==WingFlightState.BOOST);
            s.tick=player.tickCount;
        }
        return WingFlightPresentation.Frame.mix(s.previous,s.current,partial);
    }
    private WingFlightVisuals(){}
}
