package com.codex.blacklight.combat;

import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Source template/mesh contracts, separate from authored cuboids and MC particles. */
public final class GreeneProjectileAppearance {
    public record Profile(String template,String shape,float scale,float finalSeconds,int finalTicks,AABB sourceBounds,boolean flightMeshVisible) {}
    private static final AABB TORSO_BOUNDS=new AABB(-.2629188597,-.4527003765,-.1734848171,.2784145772,.4870062768,.1336045414);
    private static final AABB ASPHALT_BOUNDS=new AABB(-.7196590304,-.5511087179,-.3016959727,.7196590304,.5511087179,.3016959429);
    public static final Profile TORSO=new Profile("Projectile_VomitMissile","torso",1,.3F,6,TORSO_BOUNDS,false);
    public static final Profile ASPHALT=new Profile("Projectile_VomitMissile2","asphalt",1.3F,.266667F,6,ASPHALT_BOUNDS,true);
    public static final Profile LARGE_ASPHALT=new Profile("Projectile_VomitMissile3","asphalt",1.5F,.266667F,6,ASPHALT_BOUNDS,true);
    // PrimitiveDisplay: torso initial On, mid/final Off; asphalt initial/mid
    // On and final Off. The torso's flying silhouette is its effect, not mesh.
    public static boolean meshVisible(GreeneMissileFlight.Ammo ammo,boolean finished){return !finished&&of(ammo).flightMeshVisible();}
    public static Profile of(GreeneMissileFlight.Ammo ammo){
        return switch(ammo){
            case DEATH_FLOWER,INSECT_SWARM->TORSO;
            case RIFLE,SHOTGUN->ASPHALT;
            case ANTI_AIR,ANTI_TANK->LARGE_ASPHALT;
        };
    }
    /** 10468800: normalized X*(-10) + normalized Z*(10), radians/second.
     * 107be440 negates that angle; row-vector world postmultiply becomes a
     * column-vector world premultiply. Source spin bypasses flight alignment. */
    public static Quaternionf orientation(GreeneMissileFlight.Ammo ammo,UUID seed,Vec3 heading,float seconds){
        if(!ammo.spin.isEmpty()){
            // Native randomOrient picks each Euler in [-pi,pi]. UUID hashing is
            // the MC deterministic seed adapter, not the original random stream.
            long bits=seed.getMostSignificantBits()^Long.rotateLeft(seed.getLeastSignificantBits(),23);
            float x=angle(bits),y=angle(bits+0x9e3779b97f4a7c15L),z=angle(bits+0x3c6ef372fe94f82aL);
            var initial=new Quaternionf().rotationZYX(z,y,x);
            double time=Float.isFinite(seconds)?Math.max(0,seconds):0;
            float turn=(float)(-Math.sqrt(200)*time%(2*Math.PI));
            return new Quaternionf().rotationAxis(turn,(float)(-1/Math.sqrt(2)),0,(float)(1/Math.sqrt(2))).mul(initial).normalize();
        }
        if(heading==null||!Double.isFinite(heading.lengthSqr())||heading.lengthSqr()<1e-12)return new Quaternionf();
        Vec3 forward=heading.normalize();
        float yaw=(float)Math.atan2(-forward.x,-forward.z),pitch=(float)Math.asin(Math.max(-1,Math.min(1,forward.y)));
        return new Quaternionf().rotationY(yaw).rotateX(pitch);
    }
    private static float angle(long bits){
        bits=(bits^(bits>>>30))*0xbf58476d1ce4e5b9L;bits=(bits^(bits>>>27))*0x94d049bb133111ebL;bits^=bits>>>31;
        return (float)(((bits>>>11)*0x1.0p-53*2-1)*Math.PI);
    }
    private GreeneProjectileAppearance(){}
}
