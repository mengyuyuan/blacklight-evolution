package com.codex.blacklight.combat;

import com.codex.blacklight.network.CombatImpactPayload;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.forge.PacketDistributor;
import org.joml.Vector3f;

/** Contact-only cutting feedback. Trail movement is sampled separately from the posed blade. */
public final class BladeEffects {
    private static final DustParticleOptions RED=new DustParticleOptions(new Vector3f(.55F,.012F,.025F),.8F);
    private static final DustParticleOptions DARK=new DustParticleOptions(new Vector3f(.07F,.015F,.02F),.7F);
    private BladeEffects(){}
    public static boolean heavy(WeaponMove move){return WeaponImpact.finisher(move)||move==WeaponMove.BLADE_DIVE||move==WeaponMove.BLADE_RUSH;}
    public static void ground(ServerPlayer player){
        var center=player.position().add(Vec3.directionFromRotation(0,player.getYRot()).scale(.7));
        var surface=player.level().clip(new net.minecraft.world.level.ClipContext(center.add(0,.8,0),center.add(0,-1.2,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        if(surface.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||surface.getDirection()!=net.minecraft.core.Direction.UP)return;
        var at=surface.getLocation();var block=player.level().getBlockState(surface.getBlockPos());
        player.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,block),at.x,at.y+.06,at.z,16,.4,.08,.4,.16);
        player.serverLevel().sendParticles(ParticleTypes.POOF,at.x,at.y+.08,at.z,4,.4,.06,.4,.035);
        var forward=Vec3.directionFromRotation(0,player.getYRot());
        for(int i=-2;i<=2;i++){
            var point=at.add(forward.scale(i*.25));
            var ground=player.level().clip(new net.minecraft.world.level.ClipContext(point.add(0,.25,0),point.add(0,-.3,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            if(ground.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||ground.getDirection()!=net.minecraft.core.Direction.UP)continue;
            var state=player.level().getBlockState(ground.getBlockPos());
            var side=new Vec3(-forward.z,0,forward.x).scale(i%2==0?.16:-.16);
            com.codex.blacklight.movement.LandingDebrisSystem.fragment(player.serverLevel(),ground.getLocation().add(0,.19,0),state,side.add(0,.26+Math.abs(i)*.025,0),.25F,12);
        }
        CombatEnvironmentEffects.disturbLooseItems(player,at,1.6,.2);
        com.codex.blacklight.registry.ModWeaponSounds.play(player,com.codex.blacklight.power.PowerForm.BLADE,"ground",at,1F,1F);
    }
    public static void hit(ServerPlayer player,LivingEntity target,WeaponMove move){
        boolean heavy=heavy(move);
        var center=target.getBoundingBox().getCenter();
        var eye=player.getEyePosition();
        var contact=target.getBoundingBox().clip(eye,center).orElse(center);
        contact=contact.add(eye.subtract(center).normalize().scale(.04));
        var forward=Vec3.directionFromRotation(0,player.getYRot());
        var right=new Vec3(-forward.z,0,forward.x);
        boolean reverse=move==WeaponMove.BLADE_RETURN||move==WeaponMove.BLADE_COMBO_C2;
        var cut=move==WeaponMove.BLADE_DIVE?new Vec3(0,-1,0):right.scale(reverse?-1:1).add(0,reverse?.28:-.35,0).normalize();
        float size=(float)com.codex.blacklight.util.MathCompat.clamp(target.getBbHeight()*.45,heavy?.7:.45,heavy?1.35:.95);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new CombatImpactPayload(contact.x,contact.y,contact.z,size,false,true,(float)cut.x,(float)cut.y,(float)cut.z));
        boolean metal=target instanceof net.minecraft.world.entity.animal.IronGolem;
        int count=heavy?18:11;
        for(int i=0;i<count;i++){
            double along=(i/(double)(count-1)-.5)*size;
            var point=contact.add(cut.scale(along));
            var velocity=cut.scale(.12+player.getRandom().nextDouble()*.2).add(forward.scale(.04)).add(0,player.getRandom().nextDouble()*.1,0);
            player.serverLevel().sendParticles(metal?ParticleTypes.CRIT:(i%3==0?DARK:RED),point.x,point.y,point.z,0,velocity.x,velocity.y,velocity.z,1);
        }
        if(metal)player.level().playSound(null,target.blockPosition(),SoundEvents.ANVIL_HIT,SoundSource.PLAYERS,.18F,heavy?.7F:.9F);
    }
}
