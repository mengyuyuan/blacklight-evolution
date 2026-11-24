package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A coarse collision-height field drapes the mist over the actual, editable arena floor. */
final class MoltSurgeFloor {
    static final int SIDE=25,STEP=5;
    static final int CLEAR_AGE=com.codex.blacklight.combat.GreeneMolt.DURATION+22;
    Vec3 origin=Vec3.ZERO;
    float age,light;
    double lastFrame;
    final float[] samples=new float[SIDE*SIDE*3];
    private int row;
    private long updated=Long.MIN_VALUE;

    void update(EncounterMob boss){
        long tick=boss.level().getGameTime();
        if(tick==updated)return;
        updated=tick;
        // Sweep 25 cells per game tick instead of raycasting for every vertex or shader pass.
        var origin=boss.position();
        double z=origin.z+(row-SIDE/2)*STEP;
        for(int x=0;x<SIDE;x++){
            double worldX=origin.x+(x-SIDE/2)*STEP;
            Vec3 from=new Vec3(worldX,origin.y+6,z);
            var hit=boss.level().clip(new ClipContext(from,from.add(0,-14,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            int at=3*(row*SIDE+x);
            samples[at]=hit.getType()==HitResult.Type.BLOCK?(float)(hit.getLocation().y-origin.y):0;
            samples[at+1]=hit.getType()==HitResult.Type.BLOCK?0:1;
        }
        row=(row+1)%SIDE;
    }
}
