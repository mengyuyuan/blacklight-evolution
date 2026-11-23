package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Sample the actual resting wrist/head and find solid footing beside it before committing the fall. */
final class GoddessLanding {
    record Site(float yaw, double lift, Vec3 approach) {}
    static Site find(EncounterMob boss, int part, float heading, boolean melee) {
        Site best=null;
        double bestScore=Double.MAX_VALUE;
        int slot=part==GoddessVitals.HEAD?0:part==GoddessVitals.LEFT_ARM?5:9;
        var level=boss.level();
        var audience=level.getNearestPlayer(boss,100);
        for(int turn:new int[]{0,1,-1,2,-2,3,-3,4}){
            float yaw=heading+turn*45;
            var skin=GoddessRig.resting(boss,yaw,melee);
            var region=skin.get(slot);
            if(region.boxes().isEmpty()) continue;
            var joint=region.bounds().getCenter();
            for(int side=0;side<8;side++){
                double angle=side*Math.PI/4;
                double distance=melee ? 2.7 : 3.4;
                Vec3 scan=joint.add(Math.cos(angle)*distance,0,Math.sin(angle)*distance);
                var ground=level.clip(new ClipContext(scan.add(0,6,0),scan.add(0,-30,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
                if(ground.getType()!=HitResult.Type.BLOCK || !level.getFluidState(ground.getBlockPos().above()).isEmpty())continue;
                var feet=ground.getLocation().add(0,.002,0);
                var standing=new AABB(feet.x-.32,feet.y,feet.z-.32,feet.x+.32,feet.y+1.86,feet.z+.32);
                if(level.getBlockCollisions(boss,standing).iterator().hasNext())continue;
                double lift=feet.y+1.45-joint.y;
                var eye=feet.add(0,1.62,0);
                var target=joint.add(0,lift,0);
                if(level.clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.BLOCK)continue;
                var contact=HydraHurtContact.ray(skin,eye.subtract(0,lift,0),joint,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
                if(contact==null || !GoddessRig.belongsTo(contact.bone(),part))continue;
                if(melee && (!region.bone().equals(contact.bone()) || eye.distanceToSqr(contact.point().add(0,lift,0))>9))continue;
                double score=Math.abs(lift)*1.3+Math.abs(turn)*.2+(audience==null?0:Math.sqrt(feet.distanceToSqr(audience.position()))*.12);
                if(score<bestScore){bestScore=score;best=new Site(yaw,lift,feet);}
            }
        }
        return best;
    }
    private GoddessLanding(){}
}
