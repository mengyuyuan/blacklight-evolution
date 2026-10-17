package com.codex.blacklight.combat;

import com.codex.blacklight.network.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Different cues for a swing, a solid ground contact, and a successful body hit. */
public final class CombatImpactEffects {
    private CombatImpactEffects(){}
    public static void swing(ServerPlayer p,WeaponMove move,Vec3 forward){
        boolean hammer=move.form==PowerForm.HAMMERFISTS;
        boolean blade=move.form==PowerForm.BLADE;
        com.codex.blacklight.registry.ModWeaponSounds.play(p,com.codex.blacklight.registry.ModWeaponSounds.equipped(p),"swing",p.position(),.75F,WeaponImpact.finisher(move)?.86F:1F);
        if(hammer){
            var c=p.position().add(0,1.05,0).add(forward.scale(.8));
            p.serverLevel().sendParticles(ParticleTypes.POOF,c.x,c.y,c.z,5,.24,.2,.24,.035);
        }
    }
    public static void ground(ServerPlayer p,WeaponMove move,Vec3 forward){
        if(!p.onGround())return;
        if(move==WeaponMove.BLADE_DIVE){BladeEffects.ground(p);return;}
        var level=p.serverLevel();var center=p.position().add(forward.scale(move.quake()?0:1.05));
        var hit=level.clip(new ClipContext(center.add(0,.9,0),center.add(0,-1.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)return;
        center=hit.getLocation();float radius=(float)(move.quake()?AttackReach.reach(p,move):(move==move.uncharged()?1.65:2.2)*AttackReach.scale(p));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new CombatImpactPayload(center.x,center.y,center.z,radius,true));
        // Each fragment uses the struck terrain and stays on a supported local surface.
        for(int ring=0;ring<3;ring++)for(int i=0;i<16;i++){
            double angle=i*Math.PI/8+ring*.19,dist=radius*(.2+ring*.28);
            var point=center.add(Math.cos(angle)*dist,0,Math.sin(angle)*dist);
            var surface=level.clip(new ClipContext(point.add(0,.45,0),point.add(0,-.65,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(surface.getType()!=HitResult.Type.BLOCK||surface.getDirection()!=net.minecraft.core.Direction.UP)continue;
            var at=surface.getLocation();var block=level.getBlockState(surface.getBlockPos());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,block),at.x,at.y+.05,at.z,0,Math.cos(angle)*(.12+ring*.055),.18+ring*.05,Math.sin(angle)*(.12+ring*.055),1);
            if((i&3)==0)level.sendParticles(ParticleTypes.DUST_PLUME,at.x,at.y+.08,at.z,2,.12,.06,.12,.025);
            if(ring==1&&i%(move.quake()?2:4)==0){
                float size=move.quake()?.44F:.27F;
                var outward=new Vec3(Math.cos(angle)*.15,move.quake()?.36:.25,Math.sin(angle)*.15);
                com.codex.blacklight.movement.LandingDebrisSystem.fragment(level,at.add(0,size*.6+.05,0),block,outward,size,move.quake()?16:12);
            }
        }
        CombatEnvironmentEffects.disturbLooseItems(p,center,radius,move.quake()?.35:.18);
        com.codex.blacklight.registry.ModWeaponSounds.play(p,com.codex.blacklight.registry.ModWeaponSounds.equipped(p),"ground",center,1F,move.quake()?.9F:1F);
        PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(move.quake()?PhysicalFeedbackPayload.Effect.GROUND_SPIKE:PhysicalFeedbackPayload.Effect.CLAW_HEAVY,move.quake()?1:.42F));
    }
    public static void target(ServerPlayer p,LivingEntity v,WeaponMove move){
        if(move.form==PowerForm.BLADE){BladeEffects.hit(p,v,move);return;}
        boolean hammer=move.form==PowerForm.HAMMERFISTS;
        boolean bare=p.getData(ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.NONE;
        if(!hammer&&!bare)return;
        var c=v.getBoundingBox().getCenter();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new CombatImpactPayload(c.x,c.y,c.z,hammer?.8F:.38F,false));
        p.serverLevel().sendParticles(ParticleTypes.CRIT,c.x,c.y,c.z,hammer?12:5,.18,.24,.18,.08);

    }
}
