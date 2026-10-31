package com.codex.blacklight.military;

import com.codex.blacklight.entity.MilitarySoldier;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/** Every route search, including vanilla recomputation, must hold the level's path token. */
public final class MilitaryGroundNavigation extends GroundPathNavigation {
    private final MilitarySoldier soldier;
    private boolean permitted;
    public MilitaryGroundNavigation(MilitarySoldier soldier,Level level){super(soldier,level);this.soldier=soldier;}
    Path plan(BlockPos point){
        permitted=true;
        try{return createPath(point,0);}finally{permitted=false;}
    }
    @Override public Path createPath(BlockPos point,int accuracy){
        if(!level.hasChunkAt(point)||!level.getWorldBorder().isWithinBounds(point))return null;
        return super.createPath(point,accuracy);
    }
    @Override protected Path createPath(Set<BlockPos> targets,int offset,boolean upward,int accuracy,float range){
        if(!soldier.tacticsAvailable()||targets.isEmpty()||targets.stream().anyMatch(p->!level.hasChunkAt(p)))return null;
        if(!permitted){
            if(path!=null&&!path.isDone()&&targets.contains(path.getTarget()))return path;
            MilitaryFormationSystem.request(soldier,net.minecraft.world.phys.Vec3.atBottomCenterOf(targets.iterator().next()),speedModifier>0?speedModifier:.9);
            return null;
        }
        return super.createPath(targets,offset,upward,accuracy,range);
    }
    @Override public void stop(){super.stop();if(soldier!=null)MilitaryFormationSystem.cancel(soldier);}
}
