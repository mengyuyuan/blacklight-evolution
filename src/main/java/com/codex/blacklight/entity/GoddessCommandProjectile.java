package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Six independently flying seekers, or two vulnerable joints on each fingertip-to-rock tendon. */
public final class GoddessCommandProjectile extends Projectile {
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(GoddessCommandProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CALLER = SynchedEntityData.defineId(GoddessCommandProjectile.class, EntityDataSerializers.INT);
    private int sequence;
    private UUID target;
    private boolean fired;

    public GoddessCommandProjectile(EntityType<? extends GoddessCommandProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }
    public GoddessCommandProjectile(ServerLevel level, EncounterMob boss, ServerPlayer player, int slot, Vec3 start) {
        this(EncounterEntities.GODDESS_COMMAND_PROJECTILE.get(), level);
        setOwner(boss); setPos(start);
        entityData.set(SLOT, slot);
        entityData.set(CALLER, boss.getId());
        sequence = boss.goddess.attacks.sequence();
        target = player.getUUID();
        getPersistentData().putInt("GoddessSequence", sequence);
    }
    @Override protected void defineSynchedData() {
        entityData.define(SLOT,0); entityData.define(CALLER,-1);
    }
    public int slot() { return entityData.get(SLOT); }
    public EncounterMob caller() {
        if (level().isClientSide) return level().getEntity(entityData.get(CALLER)) instanceof EncounterMob b ? b : null;
        return getOwner() instanceof EncounterMob b ? b : null;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Slot", slot()); tag.putInt("Cast", sequence); tag.putBoolean("Fired",fired);
        if (target != null) tag.putUUID("Victim", target);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SLOT, tag.getInt("Slot")); sequence = tag.getInt("Cast"); fired = tag.getBoolean("Fired");
        target = tag.hasUUID("Victim") ? tag.getUUID("Victim") : null;
    }
    @Override public boolean isPickable() { return slot() >= 6 && slot() < 10; }
    @Override public float getPickRadius() { return slot() >= 6 ? .65F : 0; }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (slot() < 6 || slot() >= 10 || !(source.getEntity() instanceof ServerPlayer) || caller() == null || amount <= 0) return false;
        if (!caller().goddess.attacks.commands.sever(slot() % 2, position())) return false;
        discard();
        return true;
    }
    public static Vec3 steer(Vec3 heading, Vec3 desired, double maxAngle) {
        Vec3 current = heading.normalize(), goal = desired.normalize();
        if (goal.lengthSqr() < .1) return current;
        if (current.lengthSqr() < .1) return goal;
        double dot = Mth.clamp(current.dot(goal),-1,1);
        if (dot >= Math.cos(maxAngle)) return goal;
        var tangent = goal.subtract(current.scale(dot)).normalize();
        if (tangent.lengthSqr() < .1) tangent = current.cross(Math.abs(current.y) < .9 ? new Vec3(0,1,0) : new Vec3(1,0,0)).normalize();
        return current.scale(Math.cos(maxAngle)).add(tangent.scale(Math.sin(maxAngle)));
    }
    public Vec3 tendonPoint(EncounterMob boss, float age, float partial, double fraction) {
        int side = slot() % 2;
        var commands = boss.goddess.attacks.commands;
        var root = commands.tendonRoot(side, partial);
        var end = commands.stoneAnchor(side);
        return tendonPoint(root,end,age,fraction);
    }
    public static Vec3 tendonPoint(Vec3 root, Vec3 end, float age, double fraction) {
        double advance = Mth.clamp(age / 11.,0,1);
        double t = advance * fraction;
        double sag = Math.sin(Math.PI * t);
        return root.lerp(end,t).add(0, (.40 + .65 * (1 - advance)) * sag, 0);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        var boss = caller(); var slot = slot();
        if (boss == null || !boss.isAlive() || !boss.goddess.active() || boss.goddess.action() != GoddessEncounter.Action.FLYING
                || boss.goddess.attacks.sequence() != sequence) { discard(); return; }
        if (entityData.get(CALLER) != boss.getId()) entityData.set(CALLER, boss.getId());
        int age = (int)boss.goddess.attacks.age(0);
        if (slot >= 10) {
            var mob=boss.goddess.attacks.commands.member(level,slot-10);
            if (boss.goddess.attacks.move()!=GoddessAttacks.Move.PACK_POUNCE || age>=GoddessCommandCast.SIGNAL+(slot-10)*3
                    || mob==null || !mob.isAlive()) { discard(); return; }
            setPos(mob.position().add(0,.14,0));
            return;
        }
        if (slot >= 6) {
            if (boss.goddess.attacks.move() != GoddessAttacks.Move.TENDRIL_CRUSH || age >= GoddessCommandCast.PULL
                    || boss.goddess.attacks.commands.cut(slot % 2)) { discard(); return; }
            setPos(tendonPoint(boss, age, 0, slot < 8 ? .98 : .74));
            return;
        }
        int release = slot < 3 ? GoddessCommandCast.FIRST : GoddessCommandCast.SECOND;
        if (boss.goddess.attacks.move() != GoddessAttacks.Move.SEEKING_ORBS || age > release + 55
                || slot >= 3 && boss.goddess.attacks.commands.secondBroken()) { discard(); return; }
        if (age < release) { setPos(boss.goddess.attacks.commands.orbSocket(slot, 0)); return; }
        if (target == null || !(level.getEntity(target) instanceof ServerPlayer player) || !GoddessCommandCast.opponent(player)) {
            discard(); return;
        }
        var current = position();
        var desired = player.getBoundingBox().getCenter().subtract(current);
        if (!fired) {
            // Diverging release lines coalesce only while the player stays on the recorded bearing.
            var forward = desired.normalize();
            var right = new Vec3(forward.z,0,-forward.x).normalize();
            setDeltaMovement(forward.add(right.scale((slot % 3 - 1) * .28)).add(0, slot % 3 == 1 ? .13 : -.04, 0).normalize());
            fired = true;
        }
        int flight = age - release;
        double turn = Math.toRadians(flight < 19 ? 3.8 : 1.1);
        var velocity = steer(getDeltaMovement(),desired,turn).scale(Math.min(2.20, .98 + flight * .14));
        setDeltaMovement(velocity);
        var end = current.add(velocity);
        var wall = level.clip(new ClipContext(current,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Vec3 stop = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : end;
        double nearest = Double.POSITIVE_INFINITY;
        ServerPlayer victim = null;
        Vec3 impact = stop;
        for (var p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(current,stop).inflate(.43), GoddessCommandCast::opponent)) {
            var box = p.getBoundingBox().inflate(.42);
            Vec3 contact = box.contains(current) ? current : box.clip(current,stop).orElse(null);
            if (contact != null && current.distanceToSqr(contact) < nearest) {
                nearest = current.distanceToSqr(contact); victim = p; impact = contact;
            }
        }
        if (victim != null || wall.getType() == HitResult.Type.BLOCK) {
            if (victim != null && boss.goddess.attacks.commands.debit(victim,slot < 3 ? "GreenOne" : "GreenTwo"))
                victim.hurt(boss.damageSources().mobProjectile(this,boss), GoddessImpact.FINGER_NEEDLE.incoming(victim));
            SpiderFoley.play(boss,impact,SpiderFoley.Cue.RUPTURE,.95F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,impact.x,impact.y,impact.z,12,.48,.48,.48,.06);
            discard(); return;
        }
        setPos(end);
    }
}
