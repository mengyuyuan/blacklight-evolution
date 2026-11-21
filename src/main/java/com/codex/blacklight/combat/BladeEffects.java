package com.codex.blacklight.combat;

import com.codex.blacklight.network.CombatImpactPayload;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
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
        player.serverLevel().sendParticles(com.codex.blacklight.registry.ModParticles.CONTACT_DUST.get(),at.x,at.y+.08,at.z,4,.4,.06,.4,.035);
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
        TerrainImpactEffects.weaponGroundSound(player,com.codex.blacklight.power.PowerForm.BLADE,surface,.88F,1F);
    }
    public static void hit(ServerPlayer player,LivingEntity target,WeaponMove move){
        CombatImpactEffects.target(player,target,move);
    }
}
