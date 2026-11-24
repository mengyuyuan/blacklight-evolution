package com.codex.blacklight.combat;

import com.codex.blacklight.movement.LandingDebrisSystem;
import com.codex.blacklight.network.CombatImpactPayload;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

/** The player's hammer ground response, shared by confirmed creature/terrain contacts.
 * Presentation and loose-item impulses only: never mines blocks or deals extra damage. */
public final class TerrainImpactEffects {
    public record Surface(BlockHitResult hit,double angle,int ring,int index) {}
    private TerrainImpactEffects() {}

    static void weaponGroundSound(net.minecraft.server.level.ServerPlayer owner,
            com.codex.blacklight.power.PowerForm form,BlockHitResult hit,float gain,float pitch) {
        if(hit.getType()!=HitResult.Type.BLOCK)return;
        var pos=hit.getBlockPos();
        var material=owner.level().getBlockState(pos).getSoundType(owner.level(),pos,owner);
        com.codex.blacklight.registry.ModWeaponSounds.play(owner,form,"ground",hit.getLocation(),
                gain*material.getVolume(),pitch*material.getPitch());
    }
    static void surfaceSound(Entity actor, BlockHitResult contact, float volume, float pitch) {
        if (contact.getType() != HitResult.Type.BLOCK) return;
        var level = actor.level();
        var pos = contact.getBlockPos();
        var material = level.getBlockState(pos).getSoundType(level, pos, actor);
        var at = contact.getLocation();
        level.playSound(null, at.x, at.y, at.z, material.getHitSound(), net.minecraft.sounds.SoundSource.PLAYERS,
                volume * material.getVolume(), pitch * material.getPitch());
    }

    static void heldBodyFloor(LivingEntity body, int chips) {
        if (!(body.level() instanceof ServerLevel level)) return;
        var foot = body.position();
        var hit = ray(body, foot.add(0, .16, 0), foot.add(0, -.24, 0));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) return;
        var at = hit.getLocation();
        surfaceSound(body, hit, .88F, .76F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(hit.getBlockPos())),
                at.x, at.y + .04, at.z, chips, .32, .055, .32, .095);
    }

    public static boolean emit(Entity actor,BlockHitResult contact,float radius,boolean heavy) {
        if(!(actor.level() instanceof ServerLevel level)||!Float.isFinite(radius)||radius<=0
                ||contact.getType()!=HitResult.Type.BLOCK||contact.getDirection()!=Direction.UP)return false;
        var center=contact.getLocation();
        if(!level.hasChunkAt(contact.getBlockPos()))return false;
        // Stale, fabricated or removed support must not produce a ground wave.
        var support=ray(actor,center.add(0,.04,0),center.add(0,-.04,0));
        if(support.getType()!=HitResult.Type.BLOCK||support.getDirection()!=Direction.UP
                ||support.getLocation().distanceToSqr(center)>.0025)return false;
        radius=Math.min(12,radius);
        if(actor instanceof net.minecraft.world.entity.Mob){
            ContactEffects.ground(actor,contact,radius,heavy,surfaces(actor,center,radius));
            disturbItems(actor,center,radius,heavy?.35:.18);
            return true;
        }
        PacketDistributor.sendToPlayersNear(level,null,center.x,center.y,center.z,80,
                new CombatImpactPayload(center.x,center.y,center.z,radius,true));
        for(var sample:surfaces(actor,center,radius)) {
            emitSurface(level, sample, level.getBlockState(sample.hit().getBlockPos()), heavy);
        }
        disturbItems(actor,center,radius,heavy?.35:.18);
        return true;
    }

    static void emitSurface(ServerLevel level, Surface sample, BlockState material, boolean heavy) {
        var at = sample.hit().getLocation();
        int ring = sample.ring();
        double dx = Math.cos(sample.angle()), dz = Math.sin(sample.angle());
        double speed = .12 + ring * .055;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, material), at.x, at.y + .05, at.z,
                0, dx * speed, .18 + ring * .05, dz * speed, 1);
        if ((sample.index() & 3) == 0)
            level.sendParticles(com.codex.blacklight.registry.ModParticles.CONTACT_DUST.get(),
                    at.x, at.y + .08, at.z, 2, .12, .06, .12, .025);
        if (ring == 1 && sample.index() % (heavy ? 2 : 4) == 0) {
            float size = heavy ? .44F : .27F;
            LandingDebrisSystem.fragment(level, at.add(0, size * .6 + .05, 0), material,
                    new Vec3(dx * .15, heavy ? .36 : .25, dz * .15), size, heavy ? 16 : 12);
        }
    }

    /** Each sample uses its own block and collision height, including snow/slabs/stairs. */
    public static List<Surface> surfaces(Entity actor,Vec3 center,float radius) {
        var samples=new ArrayList<Surface>();
        if(!Float.isFinite(radius)||radius<=0)return samples;
        radius=Math.min(12,radius);
        for(int ring=0;ring<3;ring++)for(int i=0;i<16;i++) {
            double angle=i*Math.PI/8+ring*.19,dist=radius*(.2+ring*.28);
            var point=center.add(Math.cos(angle)*dist,0,Math.sin(angle)*dist);
            if(!actor.level().hasChunkAt(net.minecraft.core.BlockPos.containing(point)))continue;
            var hit=ray(actor,point.add(0,.45,0),point.add(0,-.65,0));
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)continue;
            var state=actor.level().getBlockState(hit.getBlockPos());
            if(state.getRenderShape()!=RenderShape.MODEL||!state.getFluidState().isEmpty())continue;
            if(ray(actor,center.add(0,.12,0),hit.getLocation().add(0,.12,0)).getType()!=HitResult.Type.MISS)continue;
            samples.add(new Surface(hit,angle,ring,i));
        }
        return List.copyOf(samples);
    }

    private static void disturbItems(Entity actor,Vec3 center,double radius,double strength) {
        for(var item:actor.level().getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(radius,.65,radius))) {
            var delta=item.position().subtract(center);double distance=delta.horizontalDistance();
            if(distance>radius||ray(actor,center.add(0,.15,0),item.position().add(0,.1,0)).getType()!=HitResult.Type.MISS)continue;
            var support=ray(actor,item.position().add(0,.05,0),item.position().add(0,-.25,0));
            if(support.getType()!=HitResult.Type.BLOCK||support.getDirection()!=Direction.UP)continue;
            double force=strength*(1-distance/radius);
            var outward=distance<.01?Vec3.ZERO:delta.multiply(1,0,1).scale(force/distance);
            item.setDeltaMovement(item.getDeltaMovement().add(outward).add(0,force*.7,0));item.hasImpulse=true;item.hurtMarked=true;
        }
    }
    private static BlockHitResult ray(Entity actor,Vec3 from,Vec3 to) {
        return actor.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
    }
}
