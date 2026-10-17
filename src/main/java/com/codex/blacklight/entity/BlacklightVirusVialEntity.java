package com.codex.blacklight.entity;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.infection.InfectionRules;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BlacklightVirusVialEntity extends ThrowableItemProjectile {
    public static final double INFECTION_RADIUS = InfectionRules.VIAL_RADIUS;

    public BlacklightVirusVialEntity(EntityType<? extends BlacklightVirusVialEntity> type, Level level) {
        super(type, level);
    }

    public BlacklightVirusVialEntity(Level level, LivingEntity owner) {
        super(ModEntities.BLACKLIGHT_VIRUS_VIAL.get(), owner, level);
    }

    public BlacklightVirusVialEntity(Level level, double x, double y, double z) {
        super(ModEntities.BLACKLIGHT_VIRUS_VIAL.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BLACKLIGHT_SAMPLE.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel serverLevel)) return;

        if (isRemoved()) return;
        Entity owner = getOwner();
        int infected = 0;
        Entity hitEntity = result instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (hitEntity instanceof net.neoforged.neoforge.entity.PartEntity<?> part) hitEntity = part.getParent();
        LivingEntity directTarget = hitEntity instanceof LivingEntity living ? living : null;
        if (directTarget != null && BlacklightInfectionSystem.infect(directTarget, this)) infected++;
        // A direct creature hit is intentional and guaranteed when eligible.
        // A deliberately shattered vial seeds a wide outbreak, respecting solid walls.
        Vec3 burst = result.getLocation();
        if (result instanceof BlockHitResult block)
            burst = burst.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.02));
        setPos(burst);
        // Throwing at your feet is voluntary awakening; never infect bystanders in PvP.
        if (owner instanceof net.minecraft.world.entity.player.Player player
                && player.distanceToSqr(burst) <= INFECTION_RADIUS * INFECTION_RADIUS
                && serverLevel.clip(new ClipContext(burst, player.getEyePosition(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS)
            BlacklightInfectionSystem.awaken(player);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(
                LivingEntity.class, new AABB(burst, burst).inflate(INFECTION_RADIUS),
                BlacklightInfectionSystem::canInfect)) {
            if (target == directTarget) continue;
            AABB box = target.getBoundingBox();
            Vec3 point = new Vec3(Math.clamp(burst.x, box.minX, box.maxX),
                    Math.clamp(burst.y, box.minY, box.maxY), Math.clamp(burst.z, box.minZ, box.maxZ));
            double distance = burst.distanceTo(point);
            if (distance > INFECTION_RADIUS) continue;
            if (serverLevel.clip(new ClipContext(burst, point, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            if (random.nextFloat() < InfectionRules.splashChance(distance)
                    && BlacklightInfectionSystem.infect(target, this)) infected++;
        }

        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY() + 0.15D, getZ(), 30, 1.0D, 0.65D, 1.0D, 0.025D);
        serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                getX(), getY() + 0.3D, getZ(), 120, INFECTION_RADIUS * .45D, 1.5D, INFECTION_RADIUS * .45D, 0.025D);
        serverLevel.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.35F, 0.72F);
        serverLevel.playSound(null, blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.HOSTILE, 1.1F, 0.62F);
        serverLevel.broadcastEntityEvent(this, (byte) 3);
        BlacklightInfectionSystem.notifyBurstOwner(owner, infected);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != 3) return;
        for (int i = 0; i < 28; i++) {
            level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, getItem()),
                    getX(), getY(), getZ(),
                    (random.nextDouble() - 0.5D) * 0.34D,
                    random.nextDouble() * 0.26D,
                    (random.nextDouble() - 0.5D) * 0.34D);
        }
        for (int i = 0; i < 18; i++) {
            level().addParticle(ParticleTypes.SMOKE,
                    getX(), getY() + 0.1D, getZ(),
                    (random.nextDouble() - 0.5D) * 0.18D,
                    random.nextDouble() * 0.12D,
                    (random.nextDouble() - 0.5D) * 0.18D);
        }
    }
}
