package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.world.BossSiteBlock;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** World-space extension of the actual left Slug tip during the scripted truck strike. */
public final class PlazaLash {
    private PlazaLash(){}
    public static Vec3[] points(EncounterMob mother,float partial){
        var truck=mother.plazaTruck().orElse(null);
        float time=mother.greeneCloseAge(partial);
        if(truck==null || mother.greeneCloseSide()!=GreeneCloseAttack.Side.LEFT || time<34 || time>63) return new Vec3[0];
        var state=mother.level().getBlockState(truck);
        if(!(state.getBlock() instanceof BossSiteBlock)) return new Vec3[0];
        float extend=Mth.clamp((time-34)/10F,0,1)*(1-Mth.clamp((time-48)/15F,0,1));
        var tip=GreeneCloseContact.sphere(GreeneCloseAttack.Side.LEFT,GreeneCloseAttack.Track.TIP,GreeneMotion.pose(mother,partial),
                mother.getPosition(partial),Mth.rotLerp(partial,mother.yBodyRotO,mother.yBodyRot)).center();
        Direction side=state.getValue(BossSiteBlock.FACING).getClockWise();
        var destination=Vec3.atCenterOf(truck.relative(side,2)).add(0,.85,0);
        var end=tip.lerp(destination,extend);
        double bow=(1-extend)*3+.45;
        return new Vec3[]{tip,tip.lerp(end,.29).add(0,bow,0),tip.lerp(end,.72).add(0,bow*.6,0),end};
    }
    static void strike(EncounterMob mother,ServerLevel level){
        Vec3[] path=points(mother,0);
        var hit=new java.util.HashSet<LivingEntity>();
        for(int i=1;i<path.length;i++){
            Vec3 start=path[i-1], end=path[i];
            var block=level.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,mother));
            boolean blocked=block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS;
            if(blocked) end=block.getLocation();
            for(var victim:level.getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(.45),mother::enemy)){
                var box=victim.getBoundingBox().inflate(.35);
                if((box.contains(start)||box.clip(start,end).isPresent()) && hit.add(victim) &&
                        !com.codex.blacklight.firearms.FirearmsCombat.protectedTarget(mother,victim)){
                    victim.hurt(mother.damageSources().mobAttack(mother),8);
                }
            }
            if(blocked) break;
        }
    }
}
