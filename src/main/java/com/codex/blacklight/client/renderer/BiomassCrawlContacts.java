package com.codex.blacklight.client.renderer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import java.util.WeakHashMap;

final class BiomassCrawlContacts {
    // Values contain only coordinates, never their weak player key or the world.
    private static final WeakHashMap<Player,TentacleGait> GAITS=new WeakHashMap<>();
    static TentacleGait update(Player p,float partial,float yaw,boolean fatal){
        TentacleGait gait=GAITS.computeIfAbsent(p,k->new TentacleGait());
        Vec3 body=p.getPosition(partial);
        gait.update(p.level().getGameTime()+partial,body,yaw,p.onGround(),fatal?.95:.82,point->{
            if(!p.level().hasChunkAt(BlockPos.containing(point)))return point;
            var hit=p.level().clip(new ClipContext(point.add(0,.65,0),point.add(0,-1.15,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            return hit.getType()==HitResult.Type.BLOCK?hit.getLocation().add(0,.025,0):point;
        });
        return gait;
    }
    static void clear(Player p){GAITS.remove(p);}
}
