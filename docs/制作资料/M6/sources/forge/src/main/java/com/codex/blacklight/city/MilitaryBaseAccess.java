package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.MilitaryOrganizationSystem;
import com.codex.blacklight.military.MilitaryRole;
import com.codex.blacklight.military.MilitaryUnitType;
import com.codex.blacklight.registry.MilitaryFacilityBlocks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Read-only city boundary: no mutable base maps, personnel creation or roster edits. */
public final class MilitaryBaseAccess {
    public record BaseView(String id, MilitaryFaction faction, BlockPos center,
            BoundingBox bounds, boolean loaded, boolean operational,
            int issuedPeople, int personnelCapacity) { }

    private MilitaryBaseAccess() { }

    /** Bounded read-only command suggestions; no core state or rosters are rewritten. */
    public static List<BaseView> nearby(ServerLevel level,net.minecraft.world.phys.Vec3 observer) {
        return CityMilitaryData.get(level).bases.values().stream()
                .sorted(java.util.Comparator.comparingDouble(b->observer.distanceToSqr(b.site.x(),b.site.floor(),b.site.z())))
                .limit(8).map(b->find(level,b.id).orElseThrow()).toList();
    }

    public static List<net.minecraft.world.phys.Vec3> approach(
            MilitarySoldier soldier, net.minecraft.world.phys.Vec3 destination) {
        return List.copyOf(CityMilitaryService.route(soldier, destination));
    }

    public static Optional<BaseView> find(ServerLevel level, String baseId) {
        if (level == null || baseId == null) return Optional.empty();
        var base = CityMilitaryData.get(level).bases.get(baseId);
        if (base == null) return Optional.empty();
        boolean loaded = true, terminalPresent = false;
        for (var local : LargeBaseBlueprint.get(base.site.blackwatch()).commandCores()) {
            int[] world = base.site.world(local[0], local[2]);
            var at = new BlockPos(world[0], base.site.floor() + local[1], world[1]);
            if (!level.hasChunkAt(at)) {
                loaded = false;
                continue;
            }
            if (level.getBlockState(at).is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get()))
                terminalPresent = true;
        }
        int issued = 0;
        for (UUID id : base.slots) if (id != null) issued++;
        var center = new BlockPos(base.site.x() + base.site.width() / 2,
                base.site.floor() + 1, base.site.z() + base.site.depth() / 2);
        // A real replacement terminal can reactivate a previously inactive base.
        // Missing terminals stop garage work immediately, without mutating the
        // city's delayed core observation or guessing from an unloaded chunk.
        return Optional.of(new BaseView(base.id,
                base.site.blackwatch() ? MilitaryFaction.BLACKWATCH : MilitaryFaction.MILITARY,
                center, CityMilitaryPiece.bounds(base.site), loaded, loaded && terminalPresent,
                issued, base.slots.length));
    }

    public static List<MilitarySoldier> idleCrew(
            ServerLevel level, String baseId, int limit, Set<UUID> reserved) {
        if (limit <= 0 || reserved == null) return List.of();
        var view = find(level, baseId).orElse(null);
        if (view == null || !view.loaded() || !view.operational()) return List.of();
        var base = CityMilitaryData.get(level).bases.get(baseId);
        String dimension = level.dimension().location().toString();
        UUID nativeUnit = UUID.nameUUIDFromBytes(
                ("unit:" + dimension + ":" + base.id).getBytes(StandardCharsets.UTF_8));
        var organization = MilitaryOrganizationSystem.organization(level);
        var unit = organization.unit(nativeUnit).orElse(null);
        if (unit == null || unit.faction() != view.faction()
                || unit.type() != MilitaryUnitType.TACTICAL_SQUAD
                || unit.parent() != null || unit.capacity() != base.slots.length
                || MilitaryOrganizationSystem.data(level).commanderManual(nativeUnit)
                || organization.effectiveOrder(nativeUnit).isPresent()) return List.of();
        Set<UUID> nativePeople = new HashSet<>();
        for (UUID id : base.slots) if (id != null) nativePeople.add(id);
        if (!nativePeople.containsAll(unit.members().keySet())) return List.of();

        var result = new ArrayList<MilitarySoldier>();
        for (int slot = 0; slot < base.slots.length && result.size() < limit; slot++) {
            UUID id = base.slots[slot];
            if (id == null || reserved.contains(id) || (base.losses & (1 << slot)) != 0
                    || !(level.getEntity(id) instanceof MilitarySoldier soldier)) continue;
            var tag = soldier.getPersistentData();
            if (soldier.level() != level || soldier.faction() != view.faction()
                    || !level.isPositionEntityTicking(soldier.blockPosition())
                    || soldier.commandRole() != MilitaryRole.TROOPER
                    || soldier.getTarget() != null || soldier.isPassenger() || soldier.isVehicle()
                    || !MilitaryVehicle.crewOperational(soldier)
                    || MilitaryHomeSystem.currentBase(soldier).orElse(null) != base
                    || "field".equals(tag.getString(MilitaryHomeSystem.MODE_TAG))
                    || !base.id.equals(tag.getString(CityMilitaryService.BASE_TAG))
                    || !dimension.equals(tag.getString(MilitaryHomeSystem.ORIGIN_DIMENSION_TAG))
                    || !nativeUnit.equals(soldier.unitId())
                    || organization.leafOf(id).filter(nativeUnit::equals).isEmpty()) continue;
            // automaticUnitJoin=false is the normal native-base value. It is
            // not evidence that the soldier is absent from the real roster.
            result.add(soldier);
        }
        return List.copyOf(result);
    }
}
