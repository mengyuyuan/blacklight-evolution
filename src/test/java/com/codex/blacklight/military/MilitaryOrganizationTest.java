package com.codex.blacklight.military;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MilitaryOrganizationTest {
    private final MilitaryOrganization org = new MilitaryOrganization();
    private int serial;
    private UUID id() { return new UUID(0, ++serial); }
    private UUID unit(MilitaryUnitType type, int size) {
        UUID id = id();
        assertTrue(org.create(id, MilitaryFaction.BLACKWATCH, type, "unit-" + serial, size, new MilitaryOrder.Point(0, 64, 0)));
        return id;
    }
    private UUID leaf(int size) { return unit(MilitaryUnitType.TACTICAL_SQUAD, size); }
    private UUID member(UUID unit, MilitaryRank rank) {
        var id = id(); assertTrue(org.assign(unit, id, rank)); return id;
    }

    @Test void oneCommanderCanLeadTwelveActualMembers() {
        var unit = leaf(12); var commander = member(unit, MilitaryRank.SERGEANT);
        for (int i = 0; i < 11; i++) member(unit, MilitaryRank.PRIVATE);
        assertEquals(12, org.allMembers(unit).size());
        assertEquals(commander, org.unit(unit).orElseThrow().commander());
        assertFalse(org.assign(unit, id(), MilitaryRank.PRIVATE));
    }
    @Test void variableLeafCapacitiesOneEightAndThirtyTwoAreRealLimits() {
        for (int size : new int[]{1, 8, 32}) {
            var unit = leaf(size);
            for (int i = 0; i < size; i++) member(unit, MilitaryRank.PRIVATE);
            assertFalse(org.assign(unit, id(), MilitaryRank.PRIVATE));
            assertEquals(size, org.allMembers(unit).size());
        }
        assertFalse(org.create(id(), MilitaryFaction.BLACKWATCH, MilitaryUnitType.SECTION, "oversize", 33, new MilitaryOrder.Point(0, 0, 0)));
    }
    @Test void understrengthUnitsDoNotCreatePhantomMembers() {
        var unit = leaf(32); member(unit, MilitaryRank.PRIVATE);
        assertEquals(1, org.allMembers(unit).size());
        assertEquals(32, org.unit(unit).orElseThrow().capacity());
    }
    @Test void oneMemberCannotBelongToTwoLeaves() {
        var a = leaf(8); var b = leaf(8); var member = member(a, MilitaryRank.CORPORAL);
        assertFalse(org.assign(b, member, MilitaryRank.GENERAL));
        assertEquals(a, org.leafOf(member).orElseThrow());
        assertEquals(Map.of(member, MilitaryRank.CORPORAL), org.unit(a).orElseThrow().members());
        assertTrue(org.allMembers(b).isEmpty());
    }
    @Test void realHierarchyAggregatesMembersWithoutDuplicatingCommanders() {
        var company = unit(MilitaryUnitType.COMPANY, 72); var platoon = unit(MilitaryUnitType.PLATOON, 24);
        assertTrue(org.attach(company, platoon));
        UUID officer = null;
        for (int i = 0; i < 3; i++) {
            var section = unit(MilitaryUnitType.SECTION, 8); assertTrue(org.attach(platoon, section));
            for (int j = 0; j < 8; j++) officer = member(section, j == 0 ? MilitaryRank.SERGEANT : MilitaryRank.PRIVATE);
        }
        assertEquals(24, org.allMembers(company).size());
        assertTrue(org.appoint(company, officer));
        assertEquals(24, org.allMembers(company).size());
        assertFalse(org.assign(platoon, id(), MilitaryRank.PRIVATE));
    }
    @Test void mixedFactionsAndCyclesAreRejectedWithoutPartialEdges() {
        var parent = unit(MilitaryUnitType.PLATOON, 24); var child = leaf(8);
        var foreign = id(); assertTrue(org.create(foreign, MilitaryFaction.MILITARY, MilitaryUnitType.SECTION,
                "military", 8, new MilitaryOrder.Point(0, 64, 0)));
        assertFalse(org.attach(parent, foreign)); assertNull(org.unit(foreign).orElseThrow().parent());
        assertTrue(org.attach(parent, child)); assertFalse(org.attach(child, parent));
        assertFalse(org.attach(parent, parent)); assertEquals(parent, org.unit(child).orElseThrow().parent());
    }
    @Test void parentHasAtMostEightChildren() {
        var parent = unit(MilitaryUnitType.PLATOON, 24);
        for (int i = 0; i < 8; i++) assertTrue(org.attach(parent, leaf(1)));
        var ninth = leaf(1); assertFalse(org.attach(parent, ninth));
        assertEquals(8, org.unit(parent).orElseThrow().children().size()); assertNull(org.unit(ninth).orElseThrow().parent());
    }
    @Test void registrationCannotGrowOneTreeBeyond256Members() {
        var root = unit(MilitaryUnitType.COMPANY, 72);
        for (int p = 0; p < 2; p++) {
            var platoon = unit(MilitaryUnitType.PLATOON, 24);
            for (int s = 0; s < 4; s++) {
                var section = leaf(32);
                for (int i = 0; i < 32; i++) member(section, MilitaryRank.PRIVATE);
                assertTrue(org.attach(platoon, section));
            }
            assertTrue(org.attach(root, platoon));
        }
        var extra = unit(MilitaryUnitType.PLATOON, 24); var section = leaf(1); member(section, MilitaryRank.PRIVATE);
        assertTrue(org.attach(extra, section)); assertFalse(org.attach(root, extra));
        assertEquals(256, org.allMembers(root).size()); assertNull(org.unit(extra).orElseThrow().parent());
    }
    @Test void assigningIntoAnExistingChildRespectsTheWholeTreeLimit() {
        var root = unit(MilitaryUnitType.PLATOON, 24); UUID last = null;
        for (int s = 0; s < 8; s++) {
            last = leaf(32); assertTrue(org.attach(root, last));
            for (int i = 0; i < 32; i++) member(last, MilitaryRank.PRIVATE);
        }
        var person = org.allMembers(last).get(0); org.removeMember(person);
        assertTrue(org.assign(last, person, MilitaryRank.PRIVATE));
        assertEquals(256, org.allMembers(root).size());
    }
    @Test void fourLevelsAreSupportedButSameOrReverseEchelonsAreNot() {
        var battalion = unit(MilitaryUnitType.BATTALION, 216); var company = unit(MilitaryUnitType.COMPANY, 72);
        var platoon = unit(MilitaryUnitType.PLATOON, 24); var section = unit(MilitaryUnitType.SECTION, 8);
        assertTrue(org.attach(battalion, company)); assertTrue(org.attach(company, platoon)); assertTrue(org.attach(platoon, section));
        assertFalse(org.attach(company, unit(MilitaryUnitType.BATTALION, 216)));
        assertFalse(org.attach(battalion, unit(MilitaryUnitType.BATTALION, 216)));
    }
    @Test void formalAppointmentIsIndependentOfRankAndTemporaryAvailability() {
        var unit = leaf(8); var low = member(unit, MilitaryRank.PRIVATE); var high = member(unit, MilitaryRank.CAPTAIN);
        assertTrue(org.appoint(unit, low));
        assertEquals(low, org.actingCommander(unit, Set.of(low, high)).orElseThrow());
        assertEquals(high, org.actingCommander(unit, Set.of(high)).orElseThrow());
        assertEquals(low, org.unit(unit).orElseThrow().commander());
        assertEquals(low, org.actingCommander(unit, Set.of(low, high)).orElseThrow());
        assertTrue(org.actingCommander(unit, Set.of()).isEmpty());
    }
    @Test void commanderDeathChoosesHighestRankThenStableUuid() {
        var unit = leaf(8); var leader = member(unit, MilitaryRank.GENERAL);
        var a = member(unit, MilitaryRank.SERGEANT); var b = member(unit, MilitaryRank.SERGEANT);
        member(unit, MilitaryRank.PRIVATE); org.removeMember(leader);
        assertEquals(a.compareTo(b) < 0 ? a : b, org.unit(unit).orElseThrow().commander());
        assertTrue(org.leafOf(leader).isEmpty());
    }
    @Test void officerCannotBeAppointedOutsideTheDescendantRoster() {
        var root = unit(MilitaryUnitType.PLATOON, 24); var unit = leaf(8); assertTrue(org.attach(root, unit));
        var member = member(unit, MilitaryRank.LIEUTENANT);
        assertTrue(org.appoint(root, member)); assertFalse(org.appoint(root, id()));
        assertEquals(member, org.unit(root).orElseThrow().commander());
    }
    @Test void rankChangesDoNotSilentlyReplaceTheFormalCommander() {
        var unit = leaf(8); var a = member(unit, MilitaryRank.SERGEANT); var b = member(unit, MilitaryRank.PRIVATE);
        org.rank(b, MilitaryRank.GENERAL);
        assertEquals(a, org.unit(unit).orElseThrow().commander());
        assertEquals(MilitaryRank.GENERAL, org.unit(unit).orElseThrow().members().get(b));
    }
    @Test void childOrderOverridesAndThenResumesParentOrder() {
        var parent = unit(MilitaryUnitType.PLATOON, 24); var child = leaf(8); assertTrue(org.attach(parent, child));
        var march = new MilitaryOrder(MilitaryOrder.Mode.MOVE, new MilitaryOrder.Point(30, 64, 10));
        var guard = new MilitaryOrder(MilitaryOrder.Mode.GUARD, new MilitaryOrder.Point(0, 64, 0));
        assertTrue(org.order(parent, march)); assertEquals(march, org.effectiveOrder(child).orElseThrow());
        assertTrue(org.order(child, guard)); assertEquals(guard, org.effectiveOrder(child).orElseThrow());
        assertTrue(org.order(child, null)); assertEquals(march, org.effectiveOrder(child).orElseThrow());
    }
    @Test void detachRepairsAncestorCommandersAndInheritedOrders() {
        var parent = unit(MilitaryUnitType.PLATOON, 24); var a = leaf(8); var b = leaf(8);
        assertTrue(org.attach(parent, a)); assertTrue(org.attach(parent, b));
        var departing = member(a, MilitaryRank.CAPTAIN); var staying = member(b, MilitaryRank.SERGEANT);
        assertTrue(org.appoint(parent, departing)); assertTrue(org.detach(a));
        assertEquals(staying, org.unit(parent).orElseThrow().commander()); assertNull(org.unit(a).orElseThrow().parent());
    }
    @Test void returnedCollectionsAreDeeplyImmutable() {
        var unit = leaf(8); var person = member(unit, MilitaryRank.PRIVATE); var view = org.unit(unit).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> view.members().put(id(), MilitaryRank.GENERAL));
        assertThrows(UnsupportedOperationException.class, () -> view.children().add(id()));
        assertThrows(UnsupportedOperationException.class, () -> org.allMembers(unit).add(person));
    }
    @Test void transactionCopyIsIsolatedFromTheLiveOrganization() {
        var unit = leaf(8); var original = member(unit, MilitaryRank.PRIVATE); var draft = org.copy();
        draft.removeMember(original); assertTrue(draft.assign(unit, id(), MilitaryRank.SERGEANT));
        assertEquals(java.util.List.of(original), org.allMembers(unit)); assertFalse(draft.allMembers(unit).contains(original));
    }
    @Test void malformedIdentityAndNonFinitePositionsCannotEnterTheModel() {
        assertFalse(org.create(id(), MilitaryFaction.BLACKWATCH, MilitaryUnitType.SECTION, "bad", 0, new MilitaryOrder.Point(0, 0, 0)));
        assertFalse(org.create(id(), MilitaryFaction.BLACKWATCH, MilitaryUnitType.SECTION, "bad", 8, new MilitaryOrder.Point(Double.NaN, 0, 0)));
        var unit = leaf(8);
        assertFalse(org.assign(unit, null, MilitaryRank.PRIVATE));
        assertFalse(org.order(unit, new MilitaryOrder(MilitaryOrder.Mode.MOVE, new MilitaryOrder.Point(0, Double.POSITIVE_INFINITY, 0))));
    }
}
