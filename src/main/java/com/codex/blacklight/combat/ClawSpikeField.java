package com.codex.blacklight.combat;

import com.codex.blacklight.entity.GroundSpikeEntity;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Visual shard families from the three ordinary master tracks in alex_fig. */
public final class ClawSpikeField {
    private ClawSpikeField() {}

    // Original type order is LargeType, MidType, SmallType. Some slots point to the same model.
    public enum Master {
        SMALL(6, 1, 5, .2, new int[]{2,2,1}, new double[]{.1,.3,.9}),
        MEDIUM(9, 2, 6, .2, new int[]{3,2,1}, new double[]{.3,.3,.3}),
        LARGE(15, 2.5, 9, .1, new int[]{3,2,1}, new double[]{.4,.4,.2});
        final int count;
        final double inner, outer, riseMin;
        final int[] sizes;
        final double[] weights;
        Master(int count,double inner,double outer,double riseMin,int[] sizes,double[] weights) {
            this.count=count; this.inner=inner; this.outer=outer; this.riseMin=riseMin;
            this.sizes=sizes; this.weights=weights;
        }
        int size(double sample) {
            double roll=sample*(weights[0]+weights[1]+weights[2]);
            if (roll<weights[0]) return sizes[0];
            return roll<weights[0]+weights[1] ? sizes[1] : sizes[2];
        }
    }

    static BlockHitResult floor(ServerPlayer player, Vec3 origin) {
        return player.level().clip(new ClipContext(origin.add(0,.65,0), origin.add(0,-6,0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    static boolean supported(BlockHitResult hit) {
        return hit.getType()==HitResult.Type.BLOCK && !hit.isInside() && hit.getDirection()==Direction.UP;
    }

    static java.util.List<Vec3> spawn(ServerPlayer player,Vec3 center,Master master,
            java.util.List<net.minecraft.world.entity.LivingEntity> targets) {
        var anchors=new java.util.ArrayList<Vec3>();
        var preferred=new java.util.ArrayList<Vec3>();
        for(var target:targets){
            if(preferred.size()>=master.count)break;
            var root=GroundSpikePin.groundAt(player,CombatTargeting.positionNear(center,target));
            if(root!=null&&Math.abs(root.y-center.y)<=1.25&&GroundSpikePin.touches(target,root)
                    &&preferred.stream().noneMatch(other->other.distanceToSqr(root)<.16))preferred.add(root);
        }
        var random=player.getRandom();
        double start=random.nextDouble()*Math.PI*2;
        for (int i=0; i<master.count; i++) {
            double angle=start+(i+random.nextDouble()*.5)*Math.PI*2/master.count;
            double radius=(master.inner+(master.outer-master.inner)*random.nextDouble())
                    *DevastatorEffectTimeline.WORLD_SCALE;
            Vec3 at=center.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            Vec3 foot;
            if(i<preferred.size())foot=preferred.get(i);
            else{
                var floor=floor(player,at);
                if(!supported(floor))continue;
                foot=floor.getLocation();
            }
            var spike=new GroundSpikeEntity(ModEntities.GROUND_SPIKE.get(),player.level());
            double rise=master.riseMin+random.nextDouble()*(.5-master.riseMin);
            spike.setPlayerShard(master.size(random.nextDouble()),(int)Math.round(rise*20));
            spike.moveTo(foot.x,foot.y,foot.z,random.nextFloat()*360,0);
            if(player.level().addFreshEntity(spike))anchors.add(foot);
        }
        return anchors;
    }
}
