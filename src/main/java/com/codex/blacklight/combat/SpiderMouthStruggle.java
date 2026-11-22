package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.phys.Vec3;
import static com.codex.blacklight.combat.SpiderGesture.ease;
import static com.codex.blacklight.combat.SpiderGesture.phrase;

/** Times are ticks since SWALLOW began. Points are in the same rig as the mouth collider. */
final class SpiderMouthStruggle {
    static final int CATCH=24, PROMPT=42, LAST_PRESS=63, CLEAR=25, SWALLOWED=82;

    private SpiderMouthStruggle(){}

    static Vec3 feet(EncounterMob boss,float t,float partial){
        var state=boss.spiderData();
        var start=new Vec3(state.getDouble("Px"),state.getDouble("Py"),state.getDouble("Pz"));
        int pressed=state.getInt("PryAt");
        float braceTime=pressed>0?Math.min(t,pressed):Math.min(t,LAST_PRESS);
        float tug=phrase(braceTime,34,39,41,47)+.85F*phrase(braceTime,48,52,54,59);
        double x=1.89+.12*tug, y=3.12+.20*tug, z=-3.18-.26*tug;
        if(pressed>0){
            float age=t-pressed;
            float reach=phrase(age,0,5,7,11), heel=ease(age,5,14), shoulder=ease(age,11,21), clear=ease(age,18,CLEAR);
            x+=.74*heel+1.05*shoulder+.64*clear;
            y+=.16*reach+.42*heel+.29*shoulder-.38*clear;
            z+=.18*reach-.45*heel-.75*shoulder-.86*clear;
        }else if(t>LAST_PRESS){
            float give=ease(t,LAST_PRESS,72),drop=ease(t,70,SWALLOWED);
            x-=.31*give+.49*drop;y-=.27*give+.51*drop;z+=.46*give+1.38*drop;
        }
        var rim=SpiderAnatomy.point(boss,partial,new Vec3(x,y,z));
        float drag=ease(t,CATCH,39);
        return start.lerp(rim,drag).add(0,.82*Math.sin(Math.PI*drag),0);
    }

    static float jaw(EncounterMob boss,int index,float time){
        var state=boss.spiderData();
        float lag=(index%4)*.6F;
        if(!state.getBoolean("Gripped"))return .78F*phrase(time-lag,2,18,36,46);
        int press=state.getInt("PryAt");
        float clench=phrase(time,36,40,42,47)+.85F*phrase(time,49,53,55,60);
        float opening=.78F*ease(time-lag,2,18)-.17F*clench;
        if(press>0){
            float t=time-press;
            return opening+(index>=5?.43F:.16F)*phrase(t-lag,2,12,20,29);
        }
        return opening-.94F*ease(time-lag*.5F,LAST_PRESS+1,SWALLOWED);
    }
}
