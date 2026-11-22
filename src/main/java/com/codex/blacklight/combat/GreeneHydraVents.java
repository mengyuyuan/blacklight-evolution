package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

// World-space planting points survive stage reloads and are shared by hitboxes, the rig and sound.
public final class GreeneHydraVents {
    public static final int FIRST=12, SECOND=13;
    public static final float BURIAL_DEPTH=24F;
    private final Vec3[] ground=new Vec3[2];
    boolean paired, prepared;

    boolean owns(int slot){return slot==FIRST || paired && slot==SECOND;}
    Vec3 ground(int slot){return ground[slot-FIRST];}
    public static float emergence(int slot,float age){
        float t=net.minecraft.util.Mth.clamp((age-(slot==SECOND?14:0))/52F,0,1);
        return t*t*(3-2*t);
    }
    private static BlockHitResult ray(EncounterMob boss,Vec3 from,Vec3 to){
        return boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
    }
    private static Vec3 floor(EncounterMob boss,Vec3 p,double above,double below){
        var hit=ray(boss,p.add(0,above,0),p.add(0,-below,0));
        if(hit.getType()!=HitResult.Type.BLOCK || hit.isInside() || hit.getDirection()!=Direction.UP)return null;
        return hit.getLocation();
    }
    private static boolean supported(EncounterMob boss,Vec3 p){
        if(p==null)return false;
        var center=floor(boss,p,.12,.3);
        if(center==null || Math.abs(center.y-p.y)>.06)return false;
        for(var d:Direction.Plane.HORIZONTAL){
            var point=p.add(1.3*d.getStepX(),0,1.3*d.getStepZ());
            if(floor(boss,point,1.7,1.7)==null)return false;
        }
        return !boss.level().getBlockState(BlockPos.containing(p.add(0,-1.2,0))).getCollisionShape(boss.level(),BlockPos.containing(p.add(0,-1.2,0))).isEmpty();
    }
    private Vec3 find(EncounterMob boss,int slot){
        var origin=boss.position();
        var reach=new Vec3(slot==FIRST?-29:28,0,slot==FIRST?5:-6)
                .yRot((float)Math.toRadians(180-boss.greeneBattle().collapseYaw()));
        for(int angle:new int[]{0,25,-25,50,-50,80,-80,120,-120,180}){
            for(double radius:new double[]{1,.85,.68,.52}){
                var candidate=origin.add(reach.yRot((float)Math.toRadians(angle)).scale(radius));
                candidate=new Vec3(Math.floor(candidate.x)+.5,candidate.y,Math.floor(candidate.z)+.5);
                if(!boss.level().hasChunkAt(BlockPos.containing(candidate)))continue;
                var foot=floor(boss,candidate,9,32);
                if(!supported(boss,foot))continue;
                if(slot==SECOND && ground[0]!=null && (foot.distanceToSqr(ground[0])<20*20 ||
                        foot.subtract(origin).multiply(1,0,1).dot(ground[0].subtract(origin))>=0))continue;
                var space=new AABB(foot.add(-2,1.8,-2),foot.add(2,16,2));
                if(boss.level().getBlockCollisions(boss,space).iterator().hasNext())continue;
                if(ray(boss,origin.add(0,9,0),foot.add(0,8,0)).getType()==HitResult.Type.MISS)return foot;
            }
        }
        return null;
    }
    void prepare(EncounterMob boss,boolean usePair){
        paired=usePair;prepared=true;
        ground[0]=find(boss,FIRST);ground[1]=paired?find(boss,SECOND):null;
    }
    boolean validate(EncounterMob boss,int slot){
        Vec3 old=ground(slot);
        if(old==null || supported(boss,old))return false;
        // The player can mine the floor during the cutscene. Settle straight down, or withdraw.
        var lower=floor(boss,old,.3,32);
        ground[slot-FIRST]=supported(boss,lower)?lower:null;
        return true;
    }
    private static String key(int slot){return slot==FIRST?"HydraVentLeft":"HydraVentRight";}
    void write(CompoundTag tag){
        tag.putBoolean("HydraPair",paired);tag.putBoolean("VentsPrepared",prepared);
        for(int slot=FIRST;slot<=SECOND;slot++){
            var p=ground(slot);if(p==null)continue;
            var v=new CompoundTag();v.putDouble("X",p.x);v.putDouble("Y",p.y);v.putDouble("Z",p.z);tag.put(key(slot),v);
        }
    }
    public static Vec3 read(CompoundTag tag,int slot){
        if(!tag.contains(key(slot)))return null;
        var v=tag.getCompound(key(slot));return new Vec3(v.getDouble("X"),v.getDouble("Y"),v.getDouble("Z"));
    }
    void load(CompoundTag tag){
        paired=tag.getBoolean("HydraPair");prepared=tag.getBoolean("VentsPrepared");
        ground[0]=read(tag,FIRST);ground[1]=read(tag,SECOND);
    }
    public static boolean isHydra(CompoundTag tag,int slot){
        return slot==FIRST || slot==SECOND && tag.getBoolean("HydraPair");
    }
}
