package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.BiomassSurface;
import com.codex.blacklight.client.animation.WeaponMorphTimeline;
import com.codex.blacklight.client.animation.WeaponMorphTiming;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** One game-time material clock for both native hands and the world renderer. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WeaponMorphTracker {
    public record Part(PowerForm form,BiomassSurface.Frame surface) { }
    public record Sample(PowerForm from,PowerForm target,float phase,float flow,boolean transitioning,
                         boolean longTransform,List<Part> parts,double materialTime) {
        public BiomassSurface.Frame frame(PowerForm form) {
            for(var part:parts)if(part.form()==form)return part.surface();
            return BiomassSurface.Frame.stable(0);
        }
        public float coverage(boolean left) {
            float largest=0;for(var part:parts)if(WeaponMorphTiming.arm(part.form(),left))largest=Math.max(largest,part.surface().growth());
            return largest;
        }
        public boolean visible(boolean left){return coverage(left)>.001F;}
        public PowerForm projectionForm(boolean left) {
            if(WeaponMorphTiming.arm(target,left))return target;
            var selected=PowerForm.NONE;float largest=0;
            for(var part:parts)if(WeaponMorphTiming.arm(part.form(),left)&&part.surface().growth()>largest){selected=part.form();largest=part.surface().growth();}
            return selected;
        }
    }
    private static final Map<AbstractClientPlayer,Observed> STATES=new WeakHashMap<>();
    private static final class Observed {
        WeaponMorphTimeline timeline;long lastLong=Long.MIN_VALUE;boolean longVisible;double lastTime;
        Observed(PowerForm form,double now){timeline=new WeaponMorphTimeline(form,now);lastTime=now;}
    }
    private WeaponMorphTracker() { }
    public static Sample sample(AbstractClientPlayer player,float partial) {
        double now=player.level().getGameTime()+partial;
        if (!player.isAlive() || !ModAttachments.available(player)) {
            STATES.remove(player);
            return new Sample(PowerForm.NONE, PowerForm.NONE, 1, 0, false, false, List.of(), now);
        }
        var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        boolean enabled=state.isInfected()&&state.isPrototypeMode()&&player.isAlive()&&!player.isSpectator();
        var form=enabled&&WeaponMorphTiming.weapon(state.getActivePower())?state.getActivePower():PowerForm.NONE;
        var observed=STATES.get(player);
        if(observed==null){observed=new Observed(form,now);STATES.put(player,observed);}
        if(!enabled) {
            observed=new Observed(PowerForm.NONE,now);STATES.put(player,observed);
        }
        // Server time synchronization can move a client's world time backwards.
        // Keep this player's cosmetic clock monotonic; wait for raw game time to
        // catch up instead of reversing growth or restarting visible material.
        now=Math.max(now,observed.lastTime);
        observed.lastTime=now;
        var morph=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.POWER_MORPH);
        boolean longVisible=enabled&&WeaponMorphTiming.weapon(form)&&morph.formId()==form.ordinal()&&morph.visible(player,partial);
        boolean changed=observed.timeline.target()!=form;
        boolean newLong=longVisible&&observed.lastLong!=morph.started();
        if(changed||newLong) {
            float ticks=longVisible?WeaponMorphTiming.SWITCH_TICKS:form==PowerForm.NONE?WeaponMorphTiming.RETRACT_TICKS
                    :observed.timeline.target()==PowerForm.NONE?WeaponMorphTiming.QUICK_TICKS:WeaponMorphTiming.SWITCH_TICKS;
            double onset=longVisible?morph.started()+WeaponMorphTiming.onset(form):now;
            observed.timeline.retarget(form,now,onset,ticks,longVisible);
            if(longVisible)observed.lastLong=morph.started();
        }else if(observed.longVisible&&!longVisible&&observed.timeline.transitioning(now)) {
            observed.timeline.retarget(form,now,now,WeaponMorphTiming.QUICK_TICKS,false);
        }
        observed.longVisible=longVisible;
        var timeline=observed.timeline;float phase=timeline.phase(now);
        float flow=timeline.flow(now);
        var weights=timeline.weights(now);var parts=new ArrayList<Part>(3);
        for(var candidate:PowerForm.values())if(WeaponMorphTiming.weapon(candidate)&&weights[candidate.ordinal()]>.00001F)
            parts.add(new Part(candidate,new BiomassSurface.Frame(weights[candidate.ordinal()],phase,flow,candidate!=form)));
        return new Sample(timeline.from(),form,phase,flow,timeline.transitioning(now),timeline.longTransform(),List.copyOf(parts),now);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        STATES.keySet().removeIf(p -> p.level()!=mc.level || !p.isAlive() || !ModAttachments.available(p));
        if(mc.player!=null && mc.level!=null)sample(mc.player,0);
    }
}
