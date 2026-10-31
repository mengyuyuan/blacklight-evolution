package com.codex.blacklight.firearms;

import net.minecraft.world.phys.Vec3;

/** Native thrown-projectile integration: movement, drag .99, then gravity .03. */
public final class FirearmBallistics {
    public record Shot(Vec3 velocity,double ticks){}
    private FirearmBallistics(){}
    public static Vec3 at(Vec3 start,Vec3 velocity,double ticks,boolean gravity){
        double drag=Math.pow(.99,ticks),factor=(1-drag)/.01;
        return start.add(velocity.scale(factor)).add(0,gravity?-.03/.01*(ticks-factor):0,0);
    }
    private static Vec3 needed(Vec3 start,Vec3 target,Vec3 motion,double ticks,boolean gravity){
        double factor=(1-Math.pow(.99,ticks))/.01;
        return target.add(motion.scale(ticks)).subtract(start).add(0,gravity?.03/.01*(ticks-factor):0,0).scale(1/factor);
    }
    public static Shot solve(Vec3 start,Vec3 target,Vec3 motion,double speed,boolean gravity){
        if(!Double.isFinite(start.lengthSqr())||!Double.isFinite(target.lengthSqr())||!Double.isFinite(motion.lengthSqr())||!Double.isFinite(speed)||speed<=0)return null;
        double before=.05;
        for(double ticks=.25;ticks<=99.75;ticks+=.25){
            if(needed(start,target,motion,ticks,gravity).lengthSqr()<=speed*speed){
                double lo=before,hi=ticks;
                for(int i=0;i<30;i++){double mid=(lo+hi)/2;if(needed(start,target,motion,mid,gravity).lengthSqr()>speed*speed)lo=mid;else hi=mid;}
                return new Shot(needed(start,target,motion,hi,gravity),hi);
            }
            before=ticks;
        }
        return null;
    }
}
