package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.WeaponMotionClock;
import com.codex.blacklight.combat.WeaponAttackScales;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.player.AbstractClientPlayer;
import java.util.*;

/** Same clock for body, weapon mesh, glow and trail; transitions use world time, never frame count. */
public final class WeaponScaleRenderer {
    private record Action(int move,long started,String clip){}
    private static final class State {
        PowerForm form;Object level;Action action;double changed,lastTime;
        WeaponAttackScales.Shape[] from=identity(),last=identity();
    }
    private static final Map<AbstractClientPlayer,State> STATES=new WeakHashMap<>();
    private WeaponScaleRenderer(){}
    private static WeaponAttackScales.Shape[] identity(){return new WeaponAttackScales.Shape[]{WeaponAttackScales.Shape.IDENTITY,WeaponAttackScales.Shape.IDENTITY};}
    public static WeaponAttackScales.Shape[] sample(AbstractClientPlayer p,float partial){
        var player=ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var form=player.getActivePower();
        if(!p.isAlive()||p.isRemoved()||!player.isPrototypeMode()||form==PowerForm.NONE){STATES.remove(p);return identity();}
        double now=p.level().getGameTime()+(double)partial;
        var s=STATES.get(p);
        if(s==null||s.form!=form||s.level!=p.level()||now<s.lastTime){s=new State();s.form=form;s.level=p.level();STATES.put(p,s);}
        var visual=ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL);var move=visual.spec();
        var sample=move!=null&&move.form==form?WeaponMotionClock.sample(visual,now):null;
        Action action=sample==null?null:new Action(visual.move(),visual.started(),sample.key());
        if(!Objects.equals(s.action,action)){s.from=s.last;s.action=action;s.changed=now;}
        float blend=(float)Math.max(0,Math.min(1,(now-s.changed)/2));blend=blend*blend*(3-2*blend);
        var result=identity();
        for(int i=0;i<2;i++){
            var target=sample==null?WeaponAttackScales.Shape.IDENTITY:WeaponAttackScales.sample(sample.key(),sample.seconds(),i==1);
            result[i]=s.from[i].blend(target,blend);
        }
        s.last=result;s.lastTime=now;return result;
    }
}
