package com.codex.blacklight.combat;

import com.codex.blacklight.movement.LandingDebrisSystem;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Set;

/** Server-side terrain contact using the same active intervals as weapon damage. */
public final class CombatEnvironmentEffects {
    private CombatEnvironmentEffects(){}
    public static void contact(ServerPlayer player,WeaponMove move,int tick,float yaw,float clipOffset,Set<BlockPos> touched){
        if(touched.size()>=6||move.quake()||move.groundSpike())return;
        var local=LocalWeaponMotion.find(move.id);
        var whip=local.flatMap(LocalWeaponMotion.Definition::whipCurve);
        if(whip.isPresent()){
            double correction=clipOffset-Math.round(clipOffset*20)/20.0;
            double time=Math.max(clipOffset,tick/20.0-local.orElseThrow().transitionSeconds()+correction);
            var points=WhipCurveContact.worldPoints(whip.orElseThrow(),time,player.position(),yaw,player);
            for(int i=1;i<points.size();i++){
                var hit=ray(player,points.get(i-1),points.get(i));
                if(hit.getType()==HitResult.Type.BLOCK){emit(player,move,hit,touched);break;}
            }
            return;
        }
        var forward=Vec3.directionFromRotation(0,yaw);var right=new Vec3(-forward.z,0,forward.x);
        var bounds=AttackReach.bounds(player,move,tick);
        if(bounds!=null){
            // Probe the authored contact volume, not a sphere around the player.
            for(int x=0;x<3&&touched.size()<6;x++)for(int y=0;y<3&&touched.size()<6;y++)for(int z=0;z<2&&touched.size()<6;z++){
                double rx=bounds.minRight()+(bounds.maxRight()-bounds.minRight())*x/2;
                double up=bounds.minUp()+(bounds.maxUp()-bounds.minUp())*y/2;
                double f=z==0?bounds.minForward():bounds.maxForward();
                var end=player.position().add(right.scale(rx)).add(0,up,0).add(forward.scale(f));
                if(end.distanceTo(player.position())>AttackReach.reach(player,move)+1.5)continue;
                var start=player.position().add(0,Math.clamp(up,.45,1.5),0);
                emit(player,move,ray(player,start,end),touched);
            }
        }else{
            var direction=WeaponCombatSystem.strikeDirection(move,yaw,tick);
            double height=move==WeaponMove.BLADE_DIVE?.2:1.0;
            var start=player.position().add(0,height,0);
            emit(player,move,ray(player,start,start.add(direction.scale(AttackReach.reach(player,move)))),touched);
        }
    }
    private static BlockHitResult ray(ServerPlayer p,Vec3 from,Vec3 to){
        return p.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
    }
    private static void emit(ServerPlayer p,WeaponMove move,BlockHitResult hit,Set<BlockPos> touched){
        if(hit.getType()!=HitResult.Type.BLOCK||!touched.add(hit.getBlockPos().immutable()))return;
        var block=p.level().getBlockState(hit.getBlockPos());
        if(block.isAir()||block.getRenderShape()!=net.minecraft.world.level.block.RenderShape.MODEL)return;
        boolean heavy=WeaponImpact.finisher(move)||move.form==PowerForm.HAMMERFISTS||move==WeaponMove.BLADE_DIVE;
        var normal=Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        var at=hit.getLocation().add(normal.scale(.06));
        var sound=block.getSoundType();
        if(touched.size()==1)p.level().playSound(null,hit.getBlockPos(),sound.getBreakSound(),SoundSource.PLAYERS,heavy?.85F:.5F,heavy?.75F:1.05F);
        for(int i=0;i<(heavy?9:5);i++){
            var velocity=normal.scale(heavy?.22:.13).add((p.getRandom().nextDouble()-.5)*.15,.08+p.getRandom().nextDouble()*.12,(p.getRandom().nextDouble()-.5)*.15);
            p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,block),at.x,at.y,at.z,0,velocity.x,velocity.y,velocity.z,1);
        }
        if(hit.getDirection()==Direction.UP)p.serverLevel().sendParticles(com.codex.blacklight.registry.ModParticles.CONTACT_DUST.get(),at.x,at.y,at.z,heavy?2:1,.15,.04,.15,.025);
        if(touched.size()<=3){
            float size=heavy?.30F:.16F;
            LandingDebrisSystem.fragment(p.serverLevel(),at.add(normal.scale(size*.6)),block,normal.scale(.12).add(0,heavy?.27:.18,0),size,heavy?13:9);
        }
    }

    /** Ground shock disturbs loose drops only inside visible, supported surroundings. */
    public static void disturbLooseItems(ServerPlayer p,Vec3 center,double radius,double strength){
        for(var item:p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(radius,.65,radius))){
            var delta=item.position().subtract(center);double distance=delta.horizontalDistance();
            if(distance>radius||ray(p,center.add(0,.15,0),item.position().add(0,.1,0)).getType()!=HitResult.Type.MISS)continue;
            double force=strength*(1-distance/radius);
            var outward=distance<.01?Vec3.ZERO:delta.multiply(1,0,1).scale(force/distance);
            item.setDeltaMovement(item.getDeltaMovement().add(outward).add(0,force*.7,0));item.hasImpulse=true;item.hurtMarked=true;
        }
    }
}
