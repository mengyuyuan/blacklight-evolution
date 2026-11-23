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
    /** Use a point on the planted skin facing the thrower. The middle of a multipart
     * wrist can be hollow or well inside a slope, even when its fingers are reachable. */
    static Site forStagger(EncounterMob boss, float heading, net.minecraft.world.entity.player.Player player) {
        Vec3 viewer = player == null ? boss.position() : player.position();
        Site landing = sampleStagger(boss, heading, viewer, false);
        return landing == null ? sampleStagger(boss, heading, viewer, true) : landing;
    }

    private static Site sampleStagger(EncounterMob boss, float heading, Vec3 viewer, boolean searchAllSurfaces) {
        Site best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        var level = boss.level();
        int wrist = boss.goddess.supportingWrist();
        for (int turn : new int[]{0, 1, -1, 2, -2, 3, -3, 4}) {
            float yaw = heading + turn * 45;
            var skin = GoddessRig.resting(boss, yaw, true);
            for (int slot : new int[]{wrist, wrist < 0 ? -1 : wrist + 1, 0, 1}) {
                if (slot < 0) continue;
                var boxes = skin.get(slot).boxes();
                if (boxes.isEmpty()) continue;
                var nearest = boxes.stream().min(java.util.Comparator.comparingDouble(box -> box.center.distanceToSqr(viewer))).orElseThrow();
                for (var patch : boxes) {
                    if (!searchAllSurfaces && patch != nearest) continue;
                    double toward = Math.atan2(viewer.z - patch.center.z, viewer.x - patch.center.x);
                    for (int side = 0; side < 8; side++) {
                        double angle = toward + side * Math.PI / 4;
                        var outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                        var edge = patch.nearest(patch.center.add(outward.scale(12)));
                        var scan = edge.add(outward.scale(1.65));
                        var ground = level.clip(new ClipContext(scan.add(0, searchAllSurfaces ? 22 : 7, 0), scan.add(0, -40, 0),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
                        if (ground.getType() != HitResult.Type.BLOCK || ground.getDirection() != net.minecraft.core.Direction.UP) continue;
                        var feet = ground.getLocation().add(0, .002, 0);
                        double lift = feet.y + 1.45 - edge.y;
                        double score = feet.distanceTo(viewer) + .40 * Math.abs(lift) + .16 * Math.abs(turn);
                        if (score >= bestScore) continue;
                        var standing = new AABB(feet.x-.32, feet.y, feet.z-.32, feet.x+.32, feet.y+1.86, feet.z+.32);
                        if (level.getBlockCollisions(boss, standing).iterator().hasNext() || !level.getFluidState(net.minecraft.core.BlockPos.containing(feet)).isEmpty()) continue;
                        var eye = feet.add(0, 1.62, 0);
                        var hit = HydraHurtContact.ray(skin, eye.subtract(0, lift, 0), edge.lerp(patch.center, .12),
                                com.codex.blacklight.infection.InfectedAttackStyle.CRUSH);
                        if (hit == null || !boss.goddess.groundOpening(GoddessRig.PARTS.indexOf(hit.bone()))) continue;
                        var target = hit.point().add(0, lift, 0);
                        if (eye.distanceToSqr(target) > 2.9*2.9 || !HydraHurtContact.clear(boss, eye, target)) continue;
                        best = new Site(yaw, lift, feet);
                        bestScore = score;
                        if (searchAllSurfaces) return best;
                    }
                }
            }
        }
        return best;
    }
    private GoddessLanding(){}
}
