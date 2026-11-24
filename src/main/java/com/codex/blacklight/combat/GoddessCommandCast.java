package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.GoddessCommandProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import java.util.List;

public final class GoddessCommandCast {
    public static final int FIRST = 14, SECOND = 34, PULL = 32, SIGNAL = 26;
    private final EncounterMob boss;
    private CompoundTag state = new CompoundTag();
    private final Vec3[] orbSources = new Vec3[2];
    private float socketAge=Float.NaN;
    private int socketSequence;
    private void sampleSockets(float partial) {
        float age=boss.goddess.attacks.age(partial);
        int sequence=boss.goddess.attacks.sequence();
        if(age==socketAge && sequence==socketSequence)return;
        socketAge=age;socketSequence=sequence;
        var root=GoddessRig.root(boss,partial);
        var joints=GoddessRig.pose(boss,partial);
        var origin=boss.getPosition(partial);
        for(int side=0;side<2;side++){
            int joint=side==0?5:19;
            if(!GoddessRig.visible(boss,joint))joint=side==0?19:5;
            var pivot=GoddessRig.visible(boss,joint)?joints[joint]:joints[0];
            orbSources[side]=origin.add(new Vec3(root.transformPosition(pivot.getTranslation(new org.joml.Vector3f()))));
        }
    }

    public GoddessCommandCast(EncounterMob boss) { this.boss = boss; }

    public boolean tick(ServerLevel level, ServerPlayer target, GoddessAttacks.Move move, int age) {
        return switch (move) {
            case SEEKING_ORBS -> seekers(level, target, age);
            case TENDRIL_CRUSH -> crush(level, target, age);
            case PACK_POUNCE -> pack(level, target, age);
            default -> true;
        };
    }

    private boolean seekers(ServerLevel level, ServerPlayer target, int age) {
        if (age == 1) {
            for (int slot = 0; slot < 6; slot++) {
                var ball = new GoddessCommandProjectile(level, boss, target, slot, orbSocket(slot, 0));
                level.addFreshEntity(ball);
            }
            SpiderFoley.play(boss, GoddessCastPose.face(boss, 0), SpiderFoley.Cue.INHALE, 2);
        }
        if (age == FIRST || age == SECOND && !secondBroken())
            SpiderFoley.play(boss, orbSocket(age == FIRST ? 1 : 4, 0), SpiderFoley.Cue.SHED, 1.95F);
        if (age >= SECOND + 6 && (age & 3) == 0) {
            boolean airborne = !level.getEntitiesOfClass(GoddessCommandProjectile.class, boss.getBoundingBox().inflate(155),
                    e -> e.isAlive() && e.slot() < 6 && e.caller() == boss).isEmpty();
            if (!airborne) return true;
        }
        return age >= SECOND + 56;
    }

    public Vec3 orbSocket(int slot, float partial) {
        boolean left = slot < 3;
        sampleSockets(partial);
        var forward = Vec3.directionFromRotation(0, boss.goddess.facing(partial));
        var side = new Vec3(forward.z, 0, -forward.x).scale(left ? -1 : 1);
        var source = orbSources[left?0:1];
        double angle = (slot % 3) * Math.PI * 2 / 3 + .065 * boss.goddess.attacks.age(partial);
        return source.add(side.scale(3.7 + 1.25 * Math.cos(angle))).add(0, 1.5 + 1.25 * Math.sin(angle), 0)
                .add(forward.scale(-1.1 + .55 * Math.sin(angle)));
    }

    public boolean secondBroken() { return state.getBoolean("CutVolley"); }

    public void hitController(DamageSource source, HydraHurtContact.Contact hit) {
        if (!(source.getEntity() instanceof ServerPlayer) || hit == null || secondBroken()
                || boss.goddess.attacks.move() != GoddessAttacks.Move.SEEKING_ORBS) return;
        int age = (int) boss.goddess.attacks.age(0);
        int slot = GoddessRig.PARTS.indexOf(hit.bone());
        int missing = boss.goddess.removed();
        int right = (missing & GoddessVitals.RIGHT_ARM) == 0 ? 9 : (missing & GoddessVitals.LEFT_ARM) == 0 ? 5 : 11;
        if (age <= FIRST || age >= SECOND || slot != right && slot != 11) return;
        state.putBoolean("CutVolley", true);
        SpiderFoley.play(boss, hit.point(), SpiderFoley.Cue.TEAR, 1.65F);
        boss.goddess.publish();
    }

    public boolean debit(ServerPlayer victim, String group) {
        var paid = state.getCompound(group);
        String player = victim.getStringUUID();
        if (paid.getBoolean(player)) return false;
        paid.putBoolean(player, true);
        state.put(group, paid);
        return true;
    }

    private BlockHitResult site(ServerLevel level, ServerPlayer target, int side) {
        var center = target.position();
        var forward = center.subtract(boss.position()).multiply(1, 0, 1).normalize();
        if (forward.lengthSqr() < .1) forward = Vec3.directionFromRotation(0, boss.goddess.facing(0));
        var lateral = new Vec3(forward.z, 0, -forward.x).scale(side == 0 ? -1 : 1);
        var hand = boss.goddess.attacks.hand;
        var start = center.add(0, 1.45, 0);
        var wall = level.clip(new ClipContext(start, start.add(lateral.scale(12)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        if (wall.getType() == HitResult.Type.BLOCK && wall.getLocation().distanceToSqr(center) > 6.25
                && hand.canLift(level, target, wall.getBlockPos())) return wall;
        for (int d : new int[]{6, 8, 4, 10}) {
            var probe = center.add(lateral.scale(d));
            var floor = level.clip(new ClipContext(probe.add(0, 2.5, 0), probe.add(0, -4, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
            if (floor.getType() == HitResult.Type.BLOCK && hand.canLift(level, target, floor.getBlockPos()))
                return floor;
        }
        return null;
    }

    boolean canCrush(ServerLevel level, ServerPlayer target) {
        return boss.goddess.attacks.hand.readyForGrip() && site(level, target, 0) != null && site(level, target, 1) != null;
    }

    public Vec3 stoneSite(int side) {
        return Vec3.atCenterOf(BlockPos.of(state.getLong("Site" + side)));
    }
    private Vec3 stoneNormal(int side) {
        var face=net.minecraft.core.Direction.from3DDataValue(state.getInt("Face" + side));
        return Vec3.atLowerCornerOf(face.getNormal());
    }
    public Vec3 stoneAnchor(int side) { return stoneSite(side).add(stoneNormal(side).scale(.61)); }
    public Vec3 tendonRoot(int side, float partial) {
        var hand = boss.goddess.attacks.hand.pose(partial);
        if (hand == null) return boss.getPosition(partial);
        var tip = hand.tip(side == 0 ? 1 : 4);
        return tip == null ? hand.palm() : tip;
    }
    public boolean cut(int side) { return (state.getInt("Cut") & (1 << side)) != 0; }
    public boolean sever(int side, Vec3 at) {
        if (cut(side) || boss.goddess.attacks.move() != GoddessAttacks.Move.TENDRIL_CRUSH
                || boss.goddess.attacks.age(0) >= PULL) return false;
        state.putInt("Cut", state.getInt("Cut") | (1 << side));
        SpiderFoley.play(boss, at, SpiderFoley.Cue.TEAR, 1.64F);
        SpiderFoley.strands(boss, at, tendonRoot(side, 0).subtract(at), 15);
        boss.goddess.publish();
        return true;
    }
    private boolean crush(ServerLevel level, ServerPlayer target, int age) {
        if (age == 1) {
            var a = site(level, target, 0);
            var b = site(level, target, 1);
            if (a == null || b == null || a.getBlockPos().equals(b.getBlockPos()) || !boss.goddess.attacks.hand.readyForGrip()) return true;
            state.putLong("Site0", a.getBlockPos().asLong()); state.putLong("Site1", b.getBlockPos().asLong());
            state.putInt("Face0",a.getDirection().get3DDataValue());state.putInt("Face1",b.getDirection().get3DDataValue());
            putPoint("Aim", target.getBoundingBox().getCenter());
            boss.goddess.publish();
            for (int slot = 6; slot < 10; slot++) {
                var strand = new GoddessCommandProjectile(level, boss, target, slot, tendonRoot(slot % 2, 0));
                level.addFreshEntity(strand);
            }
            SpiderFoley.play(boss, tendonRoot(0, 0), SpiderFoley.Cue.CATCH, 1.8F);
        }
        if (age == 12) for (int side = 0; side < 2; side++)
            if (!cut(side)) SpiderFoley.ground(boss, stoneSite(side).add(0,.55,0), 1.2F, SpiderFoley.Cue.FRACTURE);
        if (age == PULL) {
            for (int side = 0; side < 2; side++) {
                var pos = BlockPos.of(state.getLong("Site" + side));
                if (cut(side) || !boss.goddess.attacks.hand.canLift(level, target, pos)) continue;
                var block = FallingBlockEntity.fall(level, pos, level.getBlockState(pos));
                // Ease the block through its exposed face. A quarry wall releases sideways and the floor releases upwards.
                var start = Vec3.atBottomCenterOf(pos).add(stoneNormal(side).scale(1.04));
                block.setPos(start);
                var delta = point("Aim").subtract(start);
                int flight = 9;
                block.setDeltaMovement(delta.x / flight * 1.10, delta.y / flight + .23, delta.z / flight * 1.10);
                block.hurtMarked = true;
                block.getPersistentData().putUUID("EncounterOwner", boss.getUUID());
                block.getPersistentData().putInt("GoddessSequence", boss.goddess.attacks.sequence());
                state.putUUID("Rock" + side, block.getUUID());
                putPoint("PreviousRock" + side, start.add(0,.49,0));
                SpiderFoley.play(boss, start, SpiderFoley.Cue.FRACTURE, 1.7F);
            }
        }
        if (age > PULL && age <= PULL + 17) for (int side = 0; side < 2; side++) {
            String rockKey = "Rock" + side;
            if (!state.hasUUID(rockKey) || !(level.getEntity(state.getUUID(rockKey)) instanceof FallingBlockEntity rock)) continue;
            Vec3 previous = point("PreviousRock" + side), current = rock.getBoundingBox().getCenter();
            var segment = new AABB(previous, current).inflate(.51);
            for (var player : level.getEntitiesOfClass(ServerPlayer.class, segment, GoddessCommandCast::opponent)) {
                var box = player.getBoundingBox().inflate(.49);
                if ((box.contains(previous) || box.clip(previous, current).isPresent()) && debit(player, "CrushHit")) {
                    player.hurt(boss.damageSources().fallingBlock(rock), GoddessImpact.THROWN_ROCK.incoming(player));
                    player.push(rock.getDeltaMovement().x * .6, .17, rock.getDeltaMovement().z * .6);
                    player.hurtMarked = true;
                }
            }
            putPoint("PreviousRock" + side, current);
        }
        return state.getInt("Cut") == 3 || age >= PULL + 19;
    }

    public static boolean opponent(ServerPlayer p) { return p.isAlive() && !p.isCreative() && !p.isSpectator(); }

    private boolean available(BlacklightInfectedHost mob) {
        var tag = mob.getPersistentData();
        return mob.isAlive() && mob.isEvolved() && !mob.isMetamorphosing() && !QuillHarvest.protectedPet(mob)
                && !ConsumeControl.protectedEntity(mob) && !ConsumeSystem.inFlight(mob)
                && !ImpactControl.knockedDown(mob) && !mob.isPassenger()
                && (!tag.hasUUID("EncounterOwner") || tag.getUUID("EncounterOwner").equals(boss.getUUID()));
    }

    private List<BlacklightInfectedHost> candidates(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(BlacklightInfectedHost.class, player.getBoundingBox().inflate(26, 9, 26),
                m -> available(m) && m.onGround() && !m.isNoAi() && !m.getPersistentData().hasUUID("PounceCaller")
                        && m.distanceToSqr(player) <= 26 * 26 && m.hasLineOfSight(player))
                .stream().sorted(Comparator.comparingDouble((BlacklightInfectedHost m) -> m.distanceToSqr(player))
                        .thenComparingInt(BlacklightInfectedHost::getId)).limit(4).toList();
    }
    boolean canPounce(ServerLevel level, ServerPlayer player) { return !candidates(level, player).isEmpty(); }

    private boolean pack(ServerLevel level, ServerPlayer target, int age) {
        if (age == 1) {
            var candidates = candidates(level, target);
            if (candidates.isEmpty()) return true;
            state.putInt("Count", candidates.size());
            putPoint("Aim", boss.goddess.attacks.lead(target, 5));
            for (int i = 0; i < candidates.size(); i++) {
                var mob = candidates.get(i);
                state.putUUID("Member" + i, mob.getUUID());
                if(i==0) putPoint("LeaderEye", mob.getEyePosition());
                var signal = new GoddessCommandProjectile(level,boss,target,10+i,mob.position());
                level.addFreshEntity(signal);
                var tag = mob.getPersistentData();
                tag.putUUID("PounceCaller", boss.getUUID());
                // An expired command also unlocks the mob if its owner unloads in another chunk.
                tag.putLong("GoddessReleaseAt", level.getGameTime() + 68);
                mob.setTarget(null); mob.getNavigation().stop(); mob.setNoAi(true);
                if (i == 0) {
                    state.putFloat("LeaderHP", mob.getHealth());
                    SpiderFoley.play(boss, mob.getEyePosition(), SpiderFoley.Cue.STRAIN, 1.45F);
                }
            }
            boss.goddess.publish();
        }
        var count = state.getInt("Count");
        for (int i = 0; i < count; i++) {
            var key = "Member" + i;
            var member = state.hasUUID(key) ? level.getEntity(state.getUUID(key)) : null;
            if (!(member instanceof BlacklightInfectedHost mob) || !available(mob)
                    || i == 0 && age < SIGNAL && mob.getHealth() < state.getFloat("LeaderHP")) {
                if (i == 0 && age < SIGNAL) {
                    SpiderFoley.play(boss, member == null ? boss.position() : member.position(), SpiderFoley.Cue.TEAR, 1.5F);
                    return true;
                }
                continue;
            }
            int launch = SIGNAL + i * 3;
            if (age == launch - 18) {
                mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, point("Aim"));
                mob.setYBodyRot(mob.getYRot());
                mob.setEvolvedAction("evolved_pounce", target);
                SpiderFoley.strands(boss, mob.getEyePosition(), point("Aim").subtract(mob.position()), i == 0 ? 9 : 4);
            }
            if (age == launch) {
                var delta = point("Aim").subtract(mob.position());
                double ticks = Mth.clamp(delta.horizontalDistance() / 1.7, 6, 15);
                Vec3 velocity = new Vec3(delta.x / ticks * 1.4, Mth.clamp(delta.y / ticks + .04 * ticks, .39, 1), delta.z / ticks * 1.4);
                putPoint("Velocity" + i, velocity);
                mob.setDeltaMovement(Vec3.ZERO);
                mob.hurtMarked = true;
                SpiderFoley.play(boss, mob.position(), SpiderFoley.Cue.STRIKE, 1.35F);

            }
            if (age <= launch || age > launch + 16 || state.getBoolean("Ended" + i)) continue;
            Vec3 prev = mob.getBoundingBox().getCenter();
            var travel = point("Velocity" + i);
            mob.move(net.minecraft.world.entity.MoverType.SELF, travel);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0;
            putPoint("Velocity" + i, travel.multiply(.94,.98,.94).add(0,-.08,0));
            Vec3 current = mob.getBoundingBox().getCenter();
            var sweep = new AABB(prev, current).inflate(mob.getBbWidth() * .5 + .1, mob.getBbHeight() * .5, mob.getBbWidth() * .5 + .1);
            for (var p : level.getEntitiesOfClass(ServerPlayer.class, sweep, GoddessCommandCast::opponent)) {
                var box=p.getBoundingBox().inflate(mob.getBbWidth()*.5,mob.getBbHeight()*.5,mob.getBbWidth()*.5);
                if ((box.contains(prev) || box.clip(prev,current).isPresent()) && debit(p, "PackHit")) p.hurt(boss.damageSources().mobAttack(boss), GoddessImpact.FINGER_SHOT.incoming(p));
            }
            if (boss.goddess.attacks.hand.clipPounce(prev, current, mob.getBbWidth() * .5 + .45)
                    || mob.horizontalCollision || age > launch + 2 && mob.onGround()) {
                state.putBoolean("Ended" + i, true);
                SpiderFoley.ground(boss, mob.position(), 1.12F, SpiderFoley.Cue.PLANT);
            }

        }
        return age >= SIGNAL + 3 * Math.max(0, count - 1) + 18;
    }

    public Vec3 packAim() { return point("Aim"); }
    public Vec3 commandLook(float age) {
        if(!state.contains("LeaderEyeX"))return boss.goddess.attacks.center();
        double turn=Mth.clamp((age-14)/10.,0,1);
        return point("LeaderEye").lerp(point("Aim"),turn*turn*(3-2*turn));
    }
    public BlacklightInfectedHost member(ServerLevel level,int index) {
        var key="Member"+index;
        return state.hasUUID(key) && level.getEntity(state.getUUID(key)) instanceof BlacklightInfectedHost m ? m : null;
    }

    private void putPoint(String key, Vec3 point) {
        state.putDouble(key + "X",point.x); state.putDouble(key + "Y",point.y); state.putDouble(key + "Z",point.z);
    }
    private Vec3 point(String key) {
        return new Vec3(state.getDouble(key + "X"), state.getDouble(key + "Y"), state.getDouble(key + "Z"));
    }
    public CompoundTag save() { return state.copy(); }
    public void load(CompoundTag saved) { state = saved.copy(); }

    public void clear() {
        if (boss.level() instanceof ServerLevel level) for (int i = 0; i < state.getInt("Count"); i++) {
            if (!state.hasUUID("Member" + i) || !(level.getEntity(state.getUUID("Member" + i)) instanceof BlacklightInfectedHost mob)) continue;
            var tag = mob.getPersistentData();
            if (!tag.hasUUID("PounceCaller") || !tag.getUUID("PounceCaller").equals(boss.getUUID())) continue;
            tag.remove("PounceCaller");
            tag.putLong("GoddessReleaseAt", level.getGameTime());
            GoddessBrood.release(mob);
            if ("evolved_pounce".equals(mob.evolvedAction())) mob.setEvolvedAction("");
        }
        state = new CompoundTag();
    }
}
