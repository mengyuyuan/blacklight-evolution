package com.codex.blacklight.animation;

import com.codex.blacklight.combat.LocalWeaponMotion;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.combat.WeaponVisual;

/** One authored weapon clock for both skeletal pose and camera events. */
public final class WeaponMotionClock {
    public record Sample(String key,float seconds,boolean looping,float blend) {}
    private WeaponMotionClock() {}
    public static String actionKey(WeaponVisual visual) {
        var move=visual.spec();
        if(move==null)return null;
        return visual.charging()?LocalWeaponMotion.find(move.id).flatMap(LocalWeaponMotion.Definition::charge)
                .map(LocalWeaponMotion.Charge::animation).orElse(move.id):move.id;
    }
    public static Sample sample(WeaponVisual visual,double worldTicks) {
        var move=visual.spec();if(move==null)return null;
        String key=actionKey(visual);
        var definition=LocalWeaponMotion.find(move.id);
        double age=Math.max(0,(worldTicks-visual.started())/20);
        float transition=MotionLibrary.transition(key);
        // started is backdated by the rounded offset in server ticks. Restore
        // its fractional remainder exactly once rather than adding the offset twice.
        float fraction=visual.charging()?0:visual.clipOffset()-Math.round(visual.clipOffset()*20)/20F;
        float time=Math.max(visual.clipOffset(),(float)(age-transition)+fraction);
        if(visual.charging())time=Math.min(time,MotionLibrary.duration(key));
        else if(visual.attackPose()>=0)time=move==WeaponMove.AIR_PAIN
                ?move.start/20F+(float)(age%Math.max(.05F,(move.end-move.start)/20F)):visual.attackPose();
        else if(visual.traveling()) {
            if(!move.aerial())return new Sample("basic_sprint",(float)age,true,1);
            time=Math.min(visual.travelPose(),(float)age);
        } else if(move==WeaponMove.CLAW_POUNCE&&visual.clipOffset()<.5F)time=Math.min(time,.40F);
        else if(definition.isPresent()&&definition.get().landing().isPresent()) {
            var landing=definition.get().landing().get();
            if(visual.clipOffset()<landing.impactTime()-.001F)time=Math.min(time,landing.holdTime());
        }
        float blend=com.codex.blacklight.util.MathCompat.clamp((float)(age/Math.max(.001F,transition)),0,1);
        return new Sample(key,com.codex.blacklight.util.MathCompat.clamp(time,0,MotionLibrary.duration(key)),false,blend);
    }
}
