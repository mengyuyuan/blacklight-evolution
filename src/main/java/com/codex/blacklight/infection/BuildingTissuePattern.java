package com.codex.blacklight.infection;

/** Metre-scale fields, not independent per-block noise: broad flesh sheets and sweeping roots. */
public final class BuildingTissuePattern {
    public record Patch(boolean covered, int variant) {}
    private BuildingTissuePattern() {}
    public static Patch at(long seed, int u, int v, boolean wall) {
        double phase=TissueGrowth.sample(seed,17)*Math.PI*2;
        double noise=noise(seed,u/6.0,v/7.0);
        double root;
        if(wall) {
            double height=Math.max(0,v);
            double fork=2+height*.18;
            double sway=Math.sin(v*.16+phase)*1.65;
            root=Math.min(Math.abs(u-fork-sway),Math.abs(u+fork-sway*.65));
            // Side branches merge into the two primary roots, with quiet wall between them.
            double branch=Math.abs(u-(Math.sin(v*.11+phase)*7));
            if(v>4&&v<21)root=Math.min(root,branch+1.2);
        } else {
            double radius=Math.sqrt((double)u*u+(double)v*v);
            double angle=Math.atan2(v,u);
            root=Math.abs(Math.sin(angle*3+Math.sin(radius*.14)*.4+phase)) * Math.max(1,radius*.45);
        }
        double width=wall?Math.max(.9,2.6-Math.max(0,v)*.045):1.8;
        boolean trunk=root<width;
        // Base zones accumulate flesh; higher facade keeps holes and the original architecture.
        double threshold=wall?.38+Math.min(.22,Math.max(0,v)*.008):.53;
        boolean covered=trunk||noise>threshold;
        int variant=trunk&&root<width*.42?2:trunk||noise>.74?1:0;
        return new Patch(covered,variant);
    }
    private static double noise(long seed,double x,double y) {
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        double fx=x-ix,fy=y-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        double a=value(seed,ix,iy),b=value(seed,ix+1,iy),c=value(seed,ix,iy+1),d=value(seed,ix+1,iy+1);
        return (a+(b-a)*fx)*(1-fy)+(c+(d-c)*fx)*fy;
    }
    private static double value(long seed,int x,int y) {
        return TissueGrowth.sample(seed ^ (long)x*0x632be59bd9b4e019L ^ (long)y*0x9e3779b97f4a7c15L,0);
    }
}
