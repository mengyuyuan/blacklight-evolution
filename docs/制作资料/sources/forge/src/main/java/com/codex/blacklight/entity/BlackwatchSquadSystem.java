package com.codex.blacklight.entity;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.military.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Local, loaded-entity coordination. Values contain UUIDs and positions, never worlds/entities. */
public final class BlackwatchSquadSystem {
    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();

    private static final class State {
        final Map<UUID, Group> members = new HashMap<>();
    }

    private static final class Group {
        final BlackwatchSquad squad = new BlackwatchSquad();
        final BlockPos home;
        final Map<UUID, Long> separated = new HashMap<>(), navigation = new HashMap<>();
        long maintenance = -1, discovery = -1, nextPatrol;
        BlockPos patrol;
        UUID unit;
        Group(BlockPos home) { this.home = home.immutable(); }
    }

    private BlackwatchSquadSystem() {}

    private static Group group(MilitarySoldier soldier) {
        var level = (ServerLevel) soldier.level();
        var state = LEVELS.computeIfAbsent(level, ignored -> new State());
        var previous=state.members.get(soldier.getUUID());
        if(previous!=null&&soldier.unitId()!=null){
            if(previous.unit==null)previous.unit=soldier.unitId();
            else if(!previous.unit.equals(soldier.unitId()))detach(state,soldier.getUUID());
        }
        return state.members.computeIfAbsent(soldier.getUUID(), ignored -> {
            if(soldier.unitId()!=null)for(var existing:new java.util.HashSet<>(state.members.values())){
                if(soldier.unitId().equals(existing.unit)&&existing.squad.members().size()<BlackwatchSquad.MAX_MEMBERS){existing.squad.add(soldier.getUUID());return existing;}
            }
            var group = new Group(soldier.home());
            group.unit=soldier.unitId();
            group.squad.add(soldier.getUUID());
            return group;
        });
    }

    public static BlackwatchSquad squad(MilitarySoldier soldier) {
        return soldier.level() instanceof ServerLevel && soldier.tacticsAvailable()
                ? group(soldier).squad : new BlackwatchSquad();
    }

    public static void tick(MilitarySoldier soldier) {
        if (!(soldier.level() instanceof ServerLevel level)) return;
        var state = LEVELS.get(level);
        if (!soldier.tacticsAvailable()) {
            if (state != null) detach(state, soldier.getUUID());
            return;
        }
        var group = group(soldier);
        state = LEVELS.get(level);
        long now = level.getGameTime();
        if (group.maintenance < 0 || now - group.maintenance >= 10) {
            group.maintenance = now;
            for (var id : group.squad.members()) {
                var entity = level.getEntity(id);
                if (!(entity instanceof MilitarySoldier member) || !member.tacticsAvailable()) detach(state, id);
            }
            var leader = level.getEntity(group.squad.leader());
            if (leader != null) for (var id : group.squad.members()) {
                var member = level.getEntity(id);
                if (member != null && member.distanceToSqr(leader) > 32 * 32) {
                    long since = group.separated.computeIfAbsent(id, ignored -> now);
                    if (now - since >= BlackwatchSquad.CONTACT_TICKS) detach(state, id);
                } else group.separated.remove(id);
            }
        }
        group = group(soldier);
        if (group.squad.members().size() < BlackwatchSquad.MAX_MEMBERS
                && (group.discovery < 0 || now - group.discovery >= 20)) {
            group.discovery = now;
            recruit(soldier, level, state, group);
        }

        if (!withinTerritory(soldier, soldier.position())) {
            soldier.setTarget(null); release(soldier); return;
        }
        var target = soldier.getTarget();
        if (target != null && (!soldier.validTarget(target) || !knownThreat(soldier, group, target, now))) {
            soldier.setTarget(null); release(soldier); target = null;
        }
        if (target != null && soldier.hasLineOfSight(target)
                && soldier.distanceToSqr(target) <= 28 * 28) {
            if (withinTerritory(soldier, target.position())) {
                var observation=new BlackwatchSquad.Contact(soldier.getUUID(), target.getUUID(),
                        position(target.position()), position(soldier.position()), now,
                        !BlacklightInfectionSystem.isInfected(target));
                group.squad.observe(observation);MilitaryFormationSystem.observe(soldier,observation);
            } else {
                soldier.setTarget(null); release(soldier); target = null;
            }
        }
        var shared = contact(soldier);
        if (shared.isPresent() && (target == null || !soldier.hasLineOfSight(target))) {
            var entity = level.getEntity(shared.get().target());
            if (entity instanceof LivingEntity living) soldier.setTarget(living);
        } else if (shared.isEmpty() && target != null && !soldier.hasLineOfSight(target)) {
            soldier.setTarget(null); release(soldier);
        }
    }

    private static boolean knownThreat(MilitarySoldier soldier, Group group, LivingEntity target, long now) {
        return BlacklightInfectionSystem.isInfected(target) || soldier.getLastHurtByMob() == target
                && soldier.tickCount - soldier.getLastHurtByMobTimestamp() < BlackwatchSquad.CONTACT_TICKS
                || group.squad.contact(now).filter(c -> c.target().equals(target.getUUID()) && c.retaliation()).isPresent()
                || MilitaryFormationSystem.contact(soldier).filter(c->c.target().equals(target.getUUID())&&c.retaliation()).isPresent();
    }

    private static void recruit(MilitarySoldier soldier, ServerLevel level, State state, Group group) {
        var leader = level.getEntity(group.squad.leader());
        if (!(leader instanceof MilitarySoldier captain)) return;
        if(group.unit==null)return; // Persistent registration alone performs budgeted spatial discovery.
        var nearby = new ArrayList<MilitarySoldier>();
        for(var id:MilitaryOrganizationSystem.organization(level).allMembers(group.unit)){
            if(level.getEntity(id) instanceof MilitarySoldier member&&member!=captain&&member.tacticsAvailable()
                    &&captain.distanceToSqr(member)<=16*16&&captain.hasLineOfSight(member))nearby.add(member);
        }
        nearby.sort(Comparator.comparing(MilitarySoldier::getUUID));
        for (var candidate : nearby) {
            if(group.unit!=null&&candidate.unitId()!=null&&!group.unit.equals(candidate.unitId()))continue;
            if (group.squad.members().size() >= BlackwatchSquad.MAX_MEMBERS) break;
            var previous = state.members.get(candidate.getUUID());
            if (previous == group || previous != null && previous.squad.members().size() > 1) continue;
            if (previous != null) detach(state, candidate.getUUID());
            if (group.squad.add(candidate.getUUID())) state.members.put(candidate.getUUID(), group);
        }
    }

    public static Optional<BlackwatchSquad.Contact> contact(MilitarySoldier soldier) {
        if (!(soldier.level() instanceof ServerLevel level) || !soldier.tacticsAvailable()) return Optional.empty();
        var group = group(soldier);
        var contact = group.squad.contact(level.getGameTime());
        if (contact.isEmpty()) return MilitaryFormationSystem.contact(soldier);
        var sighting = contact.get();
        var entity = level.getEntity(sighting.target());
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()
                || target.level() != level || !sighting.retaliation() && !BlacklightInfectionSystem.isInfected(target)) {
            group.squad.forget(sighting.target()); return Optional.empty();
        }
        if (!soldier.validTarget(target) || soldier.position().distanceToSqr(vector(sighting.observerPosition())) > 32 * 32
                || !withinTerritory(soldier, soldier.position())) return Optional.empty();
        return contact;
    }

    public static void release(MilitarySoldier soldier) {
        if (!(soldier.level() instanceof ServerLevel level)) return;
        var state = LEVELS.get(level);
        var group = state == null ? null : state.members.get(soldier.getUUID());
        if (group != null) group.squad.releaseFire(soldier.getUUID());
    }

    public static boolean withinTerritory(MilitarySoldier soldier, Vec3 position) {
        return position.distanceToSqr(com.codex.blacklight.city.CityMilitaryService.post(soldier).orElseGet(()->MilitaryFormationSystem.territory(soldier))) <= 48 * 48;
    }

    public static void patrol(MilitarySoldier soldier) {
        if(MilitaryFormationSystem.patrol(soldier))return;
        var group = group(soldier);
        var level = (ServerLevel) soldier.level();
        var leader = level.getEntity(group.squad.leader());
        if (!(leader instanceof MilitarySoldier captain)) return;
        if (soldier.distanceToSqr(Vec3.atBottomCenterOf(group.home)) > 16 * 16) {
            navigate(soldier, Vec3.atBottomCenterOf(group.home), .85); return;
        }
        if (captain == soldier) {
            long now = level.getGameTime();
            if ((group.patrol == null || soldier.distanceToSqr(Vec3.atBottomCenterOf(group.patrol)) < 2)
                    && now >= group.nextPatrol) {
                group.nextPatrol = now + 40;
                group.patrol = null;
                for (int attempt = 0; attempt < 8; attempt++) {
                    int x = soldier.getRandom().nextInt(25) - 12, z = soldier.getRandom().nextInt(25) - 12;
                    if (x * x + z * z > 12 * 12 || x * x + z * z < 4) continue;
                    var point = standing(soldier, Vec3.atBottomCenterOf(group.home.offset(x, 0, z)));
                    if (point == null) continue;
                    group.patrol = point; break;
                }
            }
            if (group.patrol != null) navigate(soldier, Vec3.atBottomCenterOf(group.patrol), .75);
        } else {
            var members = new ArrayList<>(group.squad.members()); members.remove(captain.getUUID());
            int slot = members.indexOf(soldier.getUUID());
            double yaw = Math.toRadians(captain.getYRot());
            var forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            var right = new Vec3(forward.z, 0, -forward.x);
            var point = captain.position().subtract(forward.scale(2.5 + slot / 2 * 1.5))
                    .add(right.scale(slot % 2 == 0 ? -2.5 : 2.5));
            if (soldier.distanceToSqr(point) > 4) navigate(soldier, point, .9);
            else soldier.getNavigation().stop();
        }
    }

    public static void reposition(MilitarySoldier soldier, LivingEntity target) {
        if (!soldier.hasLineOfSight(target)) return;
        var away = soldier.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < .1) return;
        var group = group(soldier);
        int slot = group.squad.members().indexOf(soldier.getUUID());
        var side = new Vec3(away.z, 0, -away.x).scale(slot % 2 == 0 ? -2.5 : 2.5);
        var point = target.position().add(away.scale(9)).add(side);
        if (withinTerritory(soldier, point)) navigate(soldier, point, .8);
    }

    public static void navigate(MilitarySoldier soldier, Vec3 destination, double speed) {
        MilitaryFormationSystem.navigate(soldier,destination,speed);
    }

    private static BlockPos standing(MilitarySoldier soldier, Vec3 destination) {
        var level = soldier.level();
        var base = BlockPos.containing(destination);
        for (int offset : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            var point = base.above(offset);
            if (!level.hasChunkAt(point) || !level.getWorldBorder().isWithinBounds(point)) continue;
            if (!level.getBlockState(point.below()).isFaceSturdy(level, point.below(), Direction.UP)
                    || !level.getFluidState(point).isEmpty() || !level.getFluidState(point.above()).isEmpty()) continue;
            var box = new AABB(point.getX() + .15, point.getY(), point.getZ() + .15,
                    point.getX() + .85, point.getY() + 1.95, point.getZ() + .85);
            if (level.noCollision(soldier, box)) return point;
        }
        return null;
    }

    private static void detach(State state, UUID member) {
        var group = state.members.remove(member);
        if (group == null) return;
        group.squad.remove(member); group.separated.remove(member); group.navigation.remove(member);
    }

    private static BlackwatchSquad.Position position(Vec3 value) {
        return new BlackwatchSquad.Position(value.x, value.y, value.z);
    }

    public static Vec3 vector(BlackwatchSquad.Position value) { return new Vec3(value.x(), value.y(), value.z()); }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof MilitarySoldier soldier && event.getLevel() instanceof ServerLevel level) {
            var state = LEVELS.get(level);
            if (state != null) detach(state, soldier.getUUID());
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); }
}
