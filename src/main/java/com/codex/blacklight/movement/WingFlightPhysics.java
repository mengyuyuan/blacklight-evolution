package com.codex.blacklight.movement;

/** Pure controller shared by prediction and server velocity validation. Units: blocks/tick. */
public final class WingFlightPhysics {
    public record Velocity(double x,double y,double z) {}
    public static Velocity step(double x,double y,double z,int forward,int right,boolean up,boolean down,boolean boost,float yaw,float pitch,boolean takeoff) {
        return step(x,y,z,forward,right,up,down,boost,yaw,pitch,takeoff,0);
    }
    public static Velocity step(double x,double y,double z,int forward,int right,boolean up,boolean down,boolean boost,float yaw,float pitch,boolean takeoff,int tier) {
        var tuning=WingFlightTuning.tier(tier);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)){x=y=z=0;}
        if(!Float.isFinite(yaw))yaw=0;if(!Float.isFinite(pitch))pitch=0;
        forward=Integer.signum(forward);right=Integer.signum(right);
        double a=Math.toRadians(yaw),p=Math.toRadians(com.codex.blacklight.forge.WingMath.clamp(pitch,-75,75));
        double dx=-Math.sin(a)*Math.cos(p)*forward-Math.cos(a)*right;
        double dz=Math.cos(a)*Math.cos(p)*forward-Math.sin(a)*right;
        double dy=-Math.sin(p)*forward+(up?1:0)-(down?1:0);
        // Upgrades extend forward sprint only; sideways/backwards/vertical precision remains available.
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz),speed=boost?(forward>0&&!takeoff?tuning.boostSpeed():1.25):.52;
        if(length>0){dx=dx/Math.max(1,length)*speed;dy=dy/Math.max(1,length)*speed;dz=dz/Math.max(1,length)*speed;}
        if(takeoff)dy=Math.max(.3,dy);
        double accel=length==0?tuning.braking():boost?tuning.acceleration():.075;
        // Redirect existing momentum promptly while preserving the normal forward
        // acceleration ramp. A 180-degree mouse turn must not feel heading-locked.
        double current=Math.sqrt(x*x+y*y+z*z),desired=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(current>desired+.001)accel=Math.max(accel,tuning.braking());
        if(current>.0001&&desired>.0001){
            double alignment=com.codex.blacklight.forge.WingMath.clamp((x*dx+y*dy+z*dz)/(current*desired),-1,1);
            accel+=Math.min(1,1-alignment)*(boost?.11+com.codex.blacklight.forge.WingMath.clamp(tier,0,3)*.12:.055);
        }
        double ex=dx-x,ey=dy-y,ez=dz-z,d=Math.sqrt(ex*ex+ey*ey+ez*ez);
        if(d>accel){ex*=accel/d;ey*=accel/d;ez*=accel/d;}
        x+=ex;y+=ey;z+=ez;
        double total=Math.sqrt(x*x+y*y+z*z);
        double cap=Math.max(1.5,tuning.boostSpeed());
        if(total>cap){x*=cap/total;y*=cap/total;z*=cap/total;}
        return new Velocity(x,y,z);
    }
    private WingFlightPhysics(){}
}
