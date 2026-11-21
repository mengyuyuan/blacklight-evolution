package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;

/** Block/tick units for the visible receiver's detached skin, with a short ballistic interval before recall. */
final class ConsumeShardMotion {
    private ConsumeShardMotion(){}
    static Vec3 eject(Vec3 radial,int half,int layer){
        Vec3 horizontal=new Vec3(radial.x,0,radial.z).normalize();
        return horizontal.scale(.16+.017*layer).add(.029*half,.207+.026*half+.018*layer,.021*layer);
    }
    static Vec3 bounce(Vec3 v,Vec3 normal){
        double inward=v.dot(normal);
        if(inward>=0)return v;
        boolean settles=Math.abs(inward)<.055;
        Vec3 tangent=v.subtract(normal.scale(inward));
        return tangent.scale(settles?.965:.75).add(normal.scale(settles?0:-inward*.32));
    }
    static double draw(boolean groundCrush,double flow,double age){
        if(!groundCrush)return flow;
        double ramp=Math.min(1,Math.max(0,(age-5.0)/4.0));
        return flow*ramp*ramp*(3-2*ramp);
    }
    static double thickness(double flatten,double sinceLanding){
        return 1-flatten*Math.max(0,1-sinceLanding/2.5);
    }
}
