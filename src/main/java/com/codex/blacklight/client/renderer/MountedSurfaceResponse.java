package com.codex.blacklight.client.renderer;

import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.combat.ConsumeVisual;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.client.renderer.ConsumeFracture.V;

/** Short material and body recoil on a native mesh. Forward is measured into the contacted triangle. */
enum MountedSurfaceResponse {
    PALM,CLAWS,BLADE,HAMMER,MUSCLE,WHIP,HUNTER_CLAMP,HUNTER_PULL;
    static MountedSurfaceResponse of(PowerForm form,boolean hunter,int ordinal){
        if(hunter)return ordinal==0?HUNTER_CLAMP:HUNTER_PULL;
        return switch(form){
            case CLAWS->CLAWS;case HAMMERFISTS->HAMMER;case MUSCLE_MASS->MUSCLE;case BLADE->BLADE;case WHIPFIST->WHIP;default->PALM;
        };
    }
    Vec3 delta(Vec3 point,Vec3 center,Vec3 inward,Vec3 tangent,double radius,double age){
        double spread=radius*(this==HAMMER?2.55:this==MUSCLE||this==HUNTER_PULL?2.35:1.85);
        Vec3 d=point.subtract(center);
        double w=1-d.lengthSqr()/(spread*spread);if(w<=0||age<0||age>=8)return Vec3.ZERO;
        Vec3 transverse=inward.cross(tangent);
        double advance=Math.min(1,.55+age*.65),release=Math.max(0,Math.min(1,(8-age)/3.8));
        double pulse=advance*release*release*(3-2*release);
        double depth,slide,spreadOut;
        switch(this){
            case CLAWS->{depth=.15*Math.cos(age*.62);slide=.34*Math.exp(-age*.24);spreadOut=.12;}
            case BLADE->{depth=.18;slide=.57;spreadOut=.20;}
            case HAMMER->{depth=.64*Math.cos(age*.73)*Math.exp(-age*.15);slide=.01;spreadOut=.32;}
            case MUSCLE->{depth=-.59;slide=.25;spreadOut=-.22;}
            case WHIP->{depth=-.23*Math.cos(age*.81);slide=.69*Math.cos(age*.66);spreadOut=.04;}
            case HUNTER_CLAMP->{depth=.41;slide=-.24;spreadOut=-.26;}
            case HUNTER_PULL->{depth=-.51;slide=.51;spreadOut=.16;}
            default->{depth=.32*Math.cos(age*.54);slide=.12;spreadOut=-.10;}
        }
        return inward.scale(depth).add(tangent.scale(slide)).add(transverse.scale(spreadOut*d.dot(transverse)/spread))
                .scale(radius*w*w*pulse);
    }
    void apply(List<V> skin,Vec3 center,Vec3 inward,Vec3 stroke,double radius,double age,
               int victim,ConsumeVisual scene,int event,int region){
        if(age<0||age>=8)return;
        Vec3 tangent=ConsumeSurfaceResponse.tangent(inward,stroke);
        boolean audit=MountedConsumeWound.responseObserver!=null;
        int moved=0;double sum=0,max=0;
        for(var vertex:skin){
            Vec3 p=new Vec3(vertex.p),offset=delta(p,center,inward,tangent,radius,age);
            if(offset.lengthSqr()>1e-12){
                vertex.p=p.add(offset).toVector3f();
                if(audit){double d=p.distanceToSqr(new Vec3(vertex.p));sum+=d;max=Math.max(max,d);moved++;}
            }
        }
        if(audit)MountedConsumeWound.responseObserver.accept(new MountedConsumeWound.Response(victim,scene.consumerId(),scene.started(),
                name(),event,region,age,skin.size(),moved,Math.sqrt(max),Math.sqrt(sum/Math.max(1,skin.size()))));
    }
}
