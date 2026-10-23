package com.codex.blacklight.disguise;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Player-owned projectiles and bounded movement, never copied mob AI or client-supplied targets. */
public final class SpeciesAbilities {
    private SpeciesAbilities() {}
    public static SpeciesTrait trait(net.minecraft.world.entity.player.Player p) {
        return SpeciesTrait.of(p.getData(ModAttachments.DISGUISE_IDENTITY).type());
    }
    public static boolean activate(ServerPlayer p) {
        if(!DisguiseSystem.active(p)||p.isSpectator()||DisguiseSystem.busy(p))return false;
        SpeciesTrait trait=trait(p);long now=p.level().getGameTime();
        if(trait==SpeciesTrait.NONE)return false;
        long left=p.getData(ModAttachments.SPECIES_READY_AT)-now;
        if(left>0) {
            p.displayClientMessage(Component.translatable("message.blacklight.species_cooldown",(left+19)/20),true);
            return false;
        }
        Vec3 look=p.getLookAngle();boolean accepted=switch(trait) {
            case CLIMB -> {
                Vec3 horizontal=new Vec3(look.x,0,look.z).normalize();
                var hit=p.level().clip(new ClipContext(p.position().add(0,.6,0),p.position().add(0,.6,0).add(horizontal.scale(1.2)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection().getAxis().isVertical())yield false;
                impulse(p,new Vec3(0,.38,0));yield true;
            }
            case FLAP -> { if(p.isInWater()||p.isInLava())yield false;impulse(p,new Vec3(look.x*.28,.38,look.z*.28));yield true; }
            case LEAP -> { if(!p.onGround())yield false;impulse(p,new Vec3(look.x*.42,.65,look.z*.42));yield true; }
            case SWIM -> { if(!p.isInWater())yield false;impulse(p,look.scale(.85));yield true; }
            case BLINK -> blink(p,look);
            case ARROW -> {
                var arrow=EntityType.ARROW.create(p.level());if(arrow==null)yield false;
                arrow.setOwner(p);arrow.setPos(p.getX(),p.getEyeY()-.12,p.getZ());
                arrow.setBaseDamage(3);arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
                arrow.shootFromRotation(p,p.getXRot(),p.getYRot(),0,2.5F,1);
                yield p.serverLevel().addFreshEntity(arrow);
            }
            case FIREBALL -> {
                var fireball=EntityType.SMALL_FIREBALL.create(p.level());if(fireball==null)yield false;
                fireball.setOwner(p);fireball.setPos(p.getEyePosition().add(look.scale(.5)));
                fireball.setDeltaMovement(look.scale(.7));
                yield p.serverLevel().addFreshEntity(fireball);
            }
            case EXPLODE -> {
                // Entity attribution, protection events and gamerules stay on the normal explosion path.
                // This species action never edits terrain and never damages its own source entity.
                p.level().explode(p,p.getX(),p.getY()+.3,p.getZ(),2.5F,Level.ExplosionInteraction.NONE);
                yield true;
            }
            default -> false;
        };
        if(!accepted){p.displayClientMessage(Component.translatable("message.blacklight.species_conditions"),true);return false;}
        p.setData(ModAttachments.SPECIES_READY_AT,now+trait.cooldown);p.syncData(ModAttachments.SPECIES_READY_AT);
        if(trait==SpeciesTrait.ARROW||trait==SpeciesTrait.FIREBALL||trait==SpeciesTrait.EXPLODE)DisguiseSystem.speciesAttack(p);
        p.serverLevel().sendParticles(ParticleTypes.SMOKE,p.getX(),p.getY()+.6,p.getZ(),6,.15,.2,.15,.01);
        p.level().playSound(null,p.blockPosition(),trait==SpeciesTrait.ARROW?SoundEvents.SKELETON_SHOOT:
                trait==SpeciesTrait.FIREBALL?SoundEvents.BLAZE_SHOOT:SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.PLAYERS,.6F,1.15F);
        return true;
    }
    private static void impulse(ServerPlayer p,Vec3 velocity) {
        p.setDeltaMovement(velocity);p.hurtMarked=true;
    }
    private static boolean blink(ServerPlayer p,Vec3 look) {
        var level=p.serverLevel();
        var end=p.getEyePosition().add(look.scale(8));
        if(!level.hasChunksAt(p.blockPosition(),net.minecraft.core.BlockPos.containing(end)))return false;
        var hit=level.clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(look.scale(8)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double distance=Math.min(8,p.getEyePosition().distanceTo(hit.getLocation())-.7);
        for(double d=distance;d>=1.5;d-=.5) {
            Vec3 next=p.position().add(look.scale(d));var box=p.getBoundingBox().move(next.subtract(p.position()));
            if(next.y<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight()
                    ||!level.hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))
                    ||!level.getWorldBorder().isWithinBounds(box)||!level.noCollision(p,box))continue;
            level.sendParticles(ParticleTypes.PORTAL,p.getX(),p.getY()+.7,p.getZ(),24,.3,.5,.3,.1);
            p.connection.teleport(next.x,next.y,next.z,p.getYRot(),p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;
            level.sendParticles(ParticleTypes.PORTAL,next.x,next.y+.7,next.z,24,.3,.5,.3,.1);return true;
        }
        return false;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e) {
        if(!(e.getEntity() instanceof ServerPlayer p)||!DisguiseSystem.active(p)||DisguiseSystem.busy(p))return;
        var trait=trait(p);
        if(trait==SpeciesTrait.SWIM&&p.isUnderWater())p.setAirSupply(p.getMaxAirSupply());
        if(trait==SpeciesTrait.FLAP&&!p.onGround()&&!p.isInWater()&&!p.isInLava()&&p.getDeltaMovement().y<-.12) {
            Vec3 v=p.getDeltaMovement();p.setDeltaMovement(v.x,-.12,v.z);p.hurtMarked=true;
        }
    }
    @SubscribeEvent public static void fire(LivingIncomingDamageEvent e) {
        if(e.getEntity() instanceof ServerPlayer p&&DisguiseSystem.active(p)
                &&SpeciesTrait.fireproof(p.getData(ModAttachments.DISGUISE_IDENTITY).type())
                &&e.getSource().is(DamageTypeTags.IS_FIRE))e.setCanceled(true);
    }
}
