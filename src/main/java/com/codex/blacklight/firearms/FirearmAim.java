package com.codex.blacklight.firearms;

import com.codex.blacklight.combat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Aim at a server-accepted, exposed lock; the real bullet/projectile still collides normally. */
public final class FirearmAim {
    private FirearmAim(){}
    public static Vec3 direction(ServerPlayer p,WeaponDefinition weapon){
        var target=CombatTargeting.lockedEntity(p);
        if(target==null||!ExplicitTarget.visibleWithinRange(p,target,weapon.range()))return p.getLookAngle();
        var start=p.getEyePosition().add(0,-.18,0);var point=ExplicitTarget.point(p,target);
        if(!CombatTargeting.clearRay(p,start,point))return p.getLookAngle();
        if(weapon.kind()!=WeaponDefinition.Kind.BULLET){
            boolean gravity=weapon.kind()!=WeaponDefinition.Kind.ROCKET&&weapon.kind()!=WeaponDefinition.Kind.MISSILE;
            var motion=target.getDeltaMovement();if(target.onGround())motion=new Vec3(motion.x,0,motion.z);
            var shot=FirearmBallistics.solve(start,point,motion,gravity?1.15:2.2,gravity);
            if(shot==null)return p.getLookAngle();
            var from=start;
            for(double tick=1;tick<shot.ticks()+1;tick++){
                var to=FirearmBallistics.at(start,shot.velocity(),Math.min(tick,shot.ticks()),gravity);
                if(!CombatTargeting.clearRay(p,from,to))return p.getLookAngle();from=to;
            }
            return shot.velocity().normalize();
        }
        var delta=point.subtract(start);return delta.lengthSqr()<1e-8?p.getLookAngle():delta.normalize();
    }
}
