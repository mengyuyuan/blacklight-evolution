package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.P1MilitaryMotion;

/** Per-actor distance clock. Rendering never moves the server entity or advances a stationary foot cycle. */
public final class BlackwatchGait {
    private double clock=Double.NaN,x,z,phase,weight,running,speed;
    private double walkStride=1.23,runStride=2.03,walkSpeed=.058,runSpeed=.145;
    private P1MilitaryMotion source;
    public double phase(){return phase;}public double weight(){return weight;}public double running(){return running;}
    public void reset(){clock=Double.NaN;weight=running=speed=0;}
    public void update(double px,double pz,double time,boolean grounded,P1MilitaryMotion motion){
        if(source!=motion){source=motion;if(motion!=null&&motion.hasClip("walk")&&motion.hasClip("run")){
            walkStride=Math.max(.1,motion.rootTravel("walk")*13/16);runStride=Math.max(.1,motion.rootTravel("run")*13/16);
            walkSpeed=walkStride/(motion.seconds("walk")*20);runSpeed=runStride/(motion.seconds("run")*20);
        }}
        if(!Double.isFinite(px+pz+time)){reset();return;}
        if(!Double.isFinite(clock)){clock=time;x=px;z=pz;return;}
        double dt=time-clock;if(dt<=0)return;
        double distance=Math.hypot(px-x,pz-z);x=px;z=pz;clock=time;
        if(dt>5||distance>1.5||distance/dt>.75){weight=running=speed=0;return;}
        double rate=1-Math.exp(-dt*.35);speed+=(grounded?distance/dt-speed:-speed)*rate;
        double run=Math.max(0,Math.min(1,(speed-walkSpeed)/Math.max(.01,runSpeed-walkSpeed)));run=run*run*(3-2*run);
        running+=(run-running)*rate;weight+=((grounded?Math.min(1,speed/.025):0)-weight)*rate;
        if(grounded&&distance>.00001)phase=(phase+distance/(walkStride+(runStride-walkStride)*running))%1;
    }
}
