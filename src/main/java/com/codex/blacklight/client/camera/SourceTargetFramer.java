package com.codex.blacklight.client.camera;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** Source target framing approaches the half-angle between actor and victim.
 * Minecraft's differently sized entities use their actual bounds in that frame.
 * This class never selects a target or changes gameplay facing.
 */
final class SourceTargetFramer {
    private double yaw,pitch;
    private boolean tracking;
    void reset(){tracking=false;}
    Vec3 aim(Vec3 camera,Vec3 ordinary,LivingEntity player,net.minecraft.world.entity.Entity target,float partial,double dt) {
        if(target==null){tracking=false;return ordinary;}
        if(!tracking){yaw=SourceCameraOrbit.yaw(ordinary);pitch=SourceCameraOrbit.pitch(ordinary);tracking=true;}
        boolean multipart=target instanceof LivingEntity living&&com.codex.blacklight.combat.CombatTargeting.multipart(living)
                &&player instanceof net.minecraft.world.entity.player.Player;
        AABB targetBounds=multipart?com.codex.blacklight.client.ClientTargetLock.focusBounds(target):target.getBoundingBox();
        Vec3 targetPosition=multipart?targetBounds.getCenter():target.getPosition(partial);
        Vec3 centre=player.getPosition(partial).add(targetPosition).scale(.5).subtract(camera);
        double reference=centre.horizontalDistanceSqr()>.001?SourceCameraOrbit.yaw(centre):yaw;
        double minYaw=Double.POSITIVE_INFINITY,maxYaw=Double.NEGATIVE_INFINITY,minPitch=90,maxPitch=-90;
        for(var entity:new net.minecraft.world.entity.Entity[]{player,target}) {
            Vec3 shift=entity.getPosition(partial).subtract(entity.position());
            AABB box=(entity==target?targetBounds:entity.getBoundingBox()).move(shift);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++) {
                Vec3 delta=new Vec3(x==0?box.minX:box.maxX,y==0?box.minY:box.maxY,z==0?box.minZ:box.maxZ).subtract(camera);
                if(delta.lengthSqr()<.01)continue;
                double angle=Mth.wrapDegrees(SourceCameraOrbit.yaw(delta)-reference);
                minYaw=Math.min(minYaw,angle);maxYaw=Math.max(maxYaw,angle);
                double p=SourceCameraOrbit.pitch(delta);minPitch=Math.min(minPitch,p);maxPitch=Math.max(maxPitch,p);
            }
        }
        if(minPitch<=maxPitch) {
            yaw=SourceCameraMath.angle(yaw,reference+(minYaw+maxYaw)*.5,150,dt,5);
            pitch=SourceCameraMath.approach(pitch,(minPitch+maxPitch)*.5,150,dt,5);
        }
        return Vec3.directionFromRotation((float)pitch,(float)yaw);
    }
}
