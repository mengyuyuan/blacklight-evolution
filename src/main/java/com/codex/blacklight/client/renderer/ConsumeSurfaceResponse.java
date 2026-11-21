package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeVariant;
import java.util.function.Consumer;
import net.minecraft.world.phys.Vec3;

/** Closed-skin contact fields in bind space. The same fields produce rendered positions and normals. */
public enum ConsumeSurfaceResponse {
    KNEE(.66,1,.47,.85), NECK(.45,1,.31,4.4), BEARHUG(.95,.78,.33,6.5),
    NELSON(.57,1,.32,5.3), GUILLOTINE(.45,1,.41,4.7), JUGULAR(.38,1,.29,2.9);

    public record Sample(int victim,int owner,long scene,String mode,int region,double age,
                         int submitted,int displaced,double peak,double rms,Vec3 peakOffset){}
    public static Consumer<Sample> observer;
    final double across,along,depth,hold;
    ConsumeSurfaceResponse(double across,double along,double depth,double hold){
        this.across=across;this.along=along;this.depth=depth;this.hold=hold;
    }
    static ConsumeSurfaceResponse of(int variant){
        return switch(variant){
            case ConsumeVariant.KNEE_HEAD->KNEE;
            case ConsumeVariant.STEALTH_NECK->NECK;
            case ConsumeVariant.STEALTH_BEARHUG->BEARHUG;
            case ConsumeVariant.STEALTH_NELSON->NELSON;
            case ConsumeVariant.STEALTH_GUILLOTINE->GUILLOTINE;
            case ConsumeVariant.STEALTH_JUGULAR->JUGULAR;
            default->null;
        };
    }
    static Vec3 tangent(Vec3 direction,Vec3 cue){
        Vec3 projected=cue.subtract(direction.scale(cue.dot(direction)));
        if(projected.lengthSqr()<1e-7){
            Vec3 fallback=Math.abs(direction.y)<.75?new Vec3(0,1,0):new Vec3(1,0,0);
            projected=fallback.subtract(direction.scale(fallback.dot(direction)));
        }
        return projected.normalize();
    }
    Vec3 offset(Vec3 delta,Vec3 inward,Vec3 tangent,Vec3 transverse,double radius,double strength){
        double u=delta.dot(tangent)/radius,v=delta.dot(transverse)/radius,n=delta.dot(inward)/radius;
        double w=1-u*u/(along*along)-v*v/(across*across)-n*n;
        if(w<=0)return Vec3.ZERO;
        double side=0,pull=0,pressure=depth;
        switch(this){
            case KNEE -> {pull=.23;pressure+=.09*(1-Math.abs(u));}
            case NECK -> {side=.22+.24*u;pull=-.07;}
            case BEARHUG -> {pressure+=.13*Math.abs(v);pull=-.13*u;side=-.20*v;}
            case NELSON -> {pull=.32;side=.23*v;}
            case GUILLOTINE -> {pull=-.31;pressure-=.21*u;}
            case JUGULAR -> {side=.27;pull=.14+.12*v;}
        }
        return inward.scale(pressure).add(tangent.scale(pull)).add(transverse.scale(side))
                .scale(radius*strength*w*w);
    }
    double strength(double age){
        if(age<1)return .68+.32*Math.max(0,age);
        double t=Math.max(0,Math.min(1,(age-hold)/3.7));
        return 1-.77*t*t*(3-2*t);
    }
}
