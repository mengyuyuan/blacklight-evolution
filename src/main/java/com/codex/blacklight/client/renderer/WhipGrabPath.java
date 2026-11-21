package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.AerialCarryMotion;
import com.codex.blacklight.combat.ConsumeVisual;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** One deployed weapon. The shaft follows the hand up to the body, then bends around the flank. */
public final class WhipGrabPath {
    public record Deployment(double reach,double grip) { }
    public static Deployment deployment(int stage,float ticks,float opening,float returnFlow) {
        double reach=1,grip=1;
        switch(stage) {
            case ConsumeVisual.CAPTURE,ConsumeVisual.WHIP_PULL -> { reach=ease((ticks-3)/10);grip=ease((ticks-11)/5); }
            case ConsumeVisual.HOLD,ConsumeVisual.AIR_HOLD,ConsumeVisual.MOUNT,ConsumeVisual.MOUNT_HOLD,ConsumeVisual.GRAB_ATTACK -> { }
            case ConsumeVisual.THROW,ConsumeVisual.AIR_THROW -> {
                float release=stage==ConsumeVisual.AIR_THROW?AerialCarryMotion.releaseTick():6;
                grip=1-ease(ticks/release);reach=1-ease((ticks/release-.4)/.6);
            }
            case ConsumeVisual.CONSUME,ConsumeVisual.MOUNT_CONSUME,ConsumeVisual.HUNTER_CONSUME -> {
                reach=WhipConsumeRetraction.cable(returnFlow);grip=WhipConsumeRetraction.coil(opening);
            }
            default -> {reach=0;grip=0;}
        }
        return new Deployment(reach,grip);
    }
    private static double ease(double v){double t=Mth.clamp(v,0,1);return t*t*(3-2*t);}

    static final int BANDS=40;
    public record Frame(Vec3 center,Vec3 right,Vec3 tangent,Vec3 back) {
        Vec3 point(double x,double y,double z){return center.add(right.scale(x)).add(tangent.scale(y)).add(back.scale(z));}
        Vec3 normal(Vec3 n){return right.scale(n.x).add(tangent.scale(n.y)).add(back.scale(n.z)).normalize();}
    }
    private final Frame[] frames=new Frame[BANDS+1];
    public Frame frame(int band){return frames[band];}

    public WhipGrabPath(Vec3 wrist,Vec3 restRight,Vec3 restForward,Vec3 center,Vec3 up,Vec3 front,
                        double width,double height,Deployment amount) {
        // Use the rendered receiver's vertical axis even when it has been lifted or tilted.
        up=up.normalize();
        Vec3 side=up.cross(front).normalize();
        if(side.lengthSqr()<.1)side=new Vec3(1,0,0);
        front=side.cross(up).normalize();
        Vec3 radial=wrist.subtract(center);
        double rx=width*.44+.025,rz=width*.34+.025;
        double angle=Math.atan2(radial.dot(front)/rz,radial.dot(side)/rx);
        double sweep=2.78*amount.grip(); // a single oblique bite around the far flank
        Vec3 entry=surface(center,up,side,front,rx,rz,height,angle,0);
        // During the consume strike the restraint opens and the hook drives into the contact point.
        Vec3 released=center.lerp(entry,amount.grip());
        Vec3 catchPoint=wrist.lerp(released,amount.reach());
        Vec3 startDir=restForward.normalize();
        Vec3 out=side.scale(-Math.sin(angle)*rx).add(front.scale(Math.cos(angle)*rz)).normalize();
        double span=wrist.distanceTo(catchPoint);
        Vec3 c1=wrist.add(startDir.scale(Math.min(span*.29,.50)));
        Vec3 c2=catchPoint.subtract(out.scale(Math.min(span*.26,.46)*amount.grip()));
        Vec3[] path=new Vec3[73];double[] lengths=new double[path.length];
        for(int i=0;i<path.length;i++) {
            if(i<=40) {
                double t=i/40.,s=1-t;
                path[i]=wrist.scale(s*s*s).add(c1.scale(3*s*s*t)).add(c2.scale(3*s*t*t)).add(catchPoint.scale(t*t*t));
            } else {
                double t=(i-40)/32.;
                Vec3 bend=surface(center,up,side,front,rx,rz,height,angle+sweep*t,t*amount.grip());
                path[i]=catchPoint.add(bend.subtract(entry).scale(amount.reach()));
            }
            if(i>0)lengths[i]=lengths[i-1]+path[i].distanceTo(path[i-1]);
        }
        Vec3[] centers=new Vec3[BANDS+1];int segment=1;
        for(int i=0;i<=BANDS;i++){
            double d=lengths[72]*i/BANDS;
            while(segment<72 && lengths[segment]<d)segment++;
            double n=lengths[segment]-lengths[segment-1];
            centers[i]=path[segment-1].lerp(path[segment],n<1e-9?0:(d-lengths[segment-1])/n);
        }
        Vec3 right=restRight;
        for(int i=0;i<=BANDS;i++) {
            Vec3 tangent=centers[Math.min(i+1,BANDS)].subtract(centers[Math.max(0,i-1)]).normalize();
            if(tangent.lengthSqr()<.1)tangent=startDir;
            right=right.subtract(tangent.scale(right.dot(tangent))).normalize();
            if(right.lengthSqr()<.1)right=(Math.abs(tangent.y)<.9?new Vec3(0,1,0):new Vec3(0,0,1)).cross(tangent).normalize();
            frames[i]=new Frame(centers[i],right,tangent,right.cross(tangent).normalize());
        }
    }
    private static Vec3 surface(Vec3 c,Vec3 up,Vec3 side,Vec3 front,double rx,double rz,double h,double a,double progress){
        return c.add(side.scale(rx*Math.cos(a))).add(front.scale(rz*Math.sin(a))).add(up.scale(h*.14*progress));
    }
}
