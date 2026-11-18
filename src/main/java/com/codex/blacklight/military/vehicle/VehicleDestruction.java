package com.codex.blacklight.military.vehicle;

import net.minecraft.world.phys.Vec3;

/** Bounded Minecraft presentation timings; these are not recovered source-game FX constants. */
public final class VehicleDestruction {
    public enum Chassis {
        TRUCK("truck",3.2F,3.4F),APC("apc",3.8F,3.2F),TANK("tank",4.5F,3.2F),HELICOPTER("helicopter",5F,3.5F),UAV("uav",1.1F,.6F);
        public final String id;public final float radius,height;
        Chassis(String id,float radius,float height){this.id=id;this.radius=radius;this.height=height;}
        public static Chassis ground(String id){for(var c:values())if(c.id.equals(id)&&c!=HELICOPTER&&c!=UAV)return c;throw new IllegalArgumentException("Unknown vehicle chassis "+id);}
    }
    public static final int BURN_TICKS=120,SMOKE_TICKS=240,WRECK_TICKS=360,MAX_SCENES=8;
    public static float flame(float age){return age<0||age>=BURN_TICKS?0:(float)Math.pow(1-age/BURN_TICKS,.65);}
    public static float fade(float age){return age<0||age>=WRECK_TICKS?0:Math.min(1,(WRECK_TICKS-age)/40);}
    // P1 uav001Husk: fire .166667..26.6667s, fade 6.66667s; late smoke 26..83.3333s.
    // Converted at 20 ticks/s. Cosmetic lifetime includes the smoke fade; no invented second explosion.
    public static int smokeTicks(Chassis kind){return kind==Chassis.UAV?1667:SMOKE_TICKS;}
    public static int wreckTicks(Chassis kind){return kind==Chassis.UAV?1800:WRECK_TICKS;}
    public static float flame(Chassis kind,float age){return kind!=Chassis.UAV?flame(age):age<4||age>=667?0:age<534?1:Math.max(0,(667-age)/133);}
    public static float fade(Chassis kind,float age){return kind!=Chassis.UAV?fade(age):age<0||age>=1800?0:Math.min(1,(1800-age)/133);}
    public static Vec3 fragment(float age,Vec3 velocity,double floor){
        double t=Math.max(0,age)/20;double y=velocity.y*t-4.5*t*t;
        // One modest rebound, then rest; a fragment never accelerates forever or tunnels below its support plane.
        double impact=(velocity.y+Math.sqrt(velocity.y*velocity.y-18*Math.min(0,floor)))/9;
        if(t>impact){double after=t-impact;y=floor+Math.max(0,velocity.y*.2*after-4.5*after*after);}
        double slide=Math.min(t,impact+.65);return new Vec3(velocity.x*slide,y,velocity.z*slide);
    }
    private VehicleDestruction(){}
}
