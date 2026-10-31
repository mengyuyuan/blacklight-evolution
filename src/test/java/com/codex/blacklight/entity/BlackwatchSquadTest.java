package com.codex.blacklight.entity;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlackwatchSquadTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2),
            C = new UUID(0, 3), D = new UUID(0, 4), E = new UUID(0, 5), TARGET = new UUID(0, 99);

    private BlackwatchSquad squad(UUID... members) {
        var squad = new BlackwatchSquad();
        for (var member : members) assertTrue(squad.add(member));
        return squad;
    }

    @Test void fifthSoldierCannotOverfillAnExistingTeam() {
        var squad = squad(A, B, C, D);
        assertFalse(squad.add(E));
        assertFalse(squad.add(A));
        assertEquals(List.of(A, B, C, D), squad.members());
    }

    @Test void lateJoinDoesNotDisplaceALivingLeaderButDeathElectsDeterministically() {
        var squad = squad(B, C);
        squad.add(A);
        assertEquals(B, squad.leader());
        squad.remove(B);
        assertEquals(A, squad.leader());
        squad.remove(A);
        squad.remove(C);
        assertNull(squad.leader());
    }

    @Test void callersCannotMutateTeamMembershipBehindTheCoordinator() {
        var squad = squad(A, B);
        assertThrows(UnsupportedOperationException.class, () -> squad.members().clear());
        assertEquals(List.of(A, B), squad.members());
    }

    @Test void contactExpiresExactlyOneHundredTicksAfterTheActualObservation() {
        var squad = squad(A, B);
        squad.observe(contact(A, 40));
        assertEquals(TARGET, squad.contact(139).orElseThrow().target());
        assertTrue(squad.contact(140).isEmpty());
    }

    @Test void forwardingTheSameObservationCannotKeepAHiddenTargetAlive() {
        var squad = squad(A, B);
        var observed = contact(A, 40);
        squad.observe(observed);
        squad.observe(observed);
        assertTrue(squad.contact(140).isEmpty());
    }

    @Test void staleOrFutureInformationDoesNotReplaceAnActualRecentContact() {
        var squad = squad(A, B);
        squad.observe(contact(A, 80));
        squad.observe(contact(B, 20));
        assertEquals(80, squad.contact(90).orElseThrow().observedAt());
        assertTrue(squad.contact(79).isEmpty());
    }

    @Test void outsiderCannotInjectATargetIntoTheTeam() {
        var squad = squad(A, B);
        squad.observe(contact(E, 40));
        assertTrue(squad.contact(41).isEmpty());
    }

    @Test void contactKeepsTheObservedPositionInsteadOfFollowingALiveEntity() {
        var squad = squad(A, B);
        squad.observe(contact(A, 40));
        assertEquals(new BlackwatchSquad.Position(6, 2, 7), squad.contact(90).orElseThrow().position());
        assertEquals(new BlackwatchSquad.Position(0, 2, 0), squad.contact(90).orElseThrow().observerPosition());
        squad.forget(E);
        assertTrue(squad.contact(90).isPresent());
        squad.forget(TARGET);
        assertTrue(squad.contact(90).isEmpty());
    }

    @Test void aPairCanOnlyAimAndShootWithOneSoldierAtATime() {
        var squad = squad(A, B);
        assertTrue(squad.requestFire(A, 0));
        assertFalse(squad.requestFire(B, 0));
        assertTrue(squad.hasFirePermit(A, 28));
        assertFalse(squad.hasFirePermit(B, 28));
    }

    @Test void fourSoldiersCanCoverEachOtherWithoutAllFiringTogether() {
        var squad = squad(A, B, C, D);
        assertTrue(squad.requestFire(A, 0));
        assertTrue(squad.requestFire(B, 0));
        assertFalse(squad.requestFire(C, 0));
        assertFalse(squad.requestFire(D, 0));
        squad.releaseFire(A);
        assertTrue(squad.requestFire(C, 1));
        assertFalse(squad.requestFire(A, 1));
    }

    @Test void waitingSoldierGetsTheNextTurnEvenWhenEarlierEntityTicksFirst() {
        var squad = squad(A, B);
        assertTrue(squad.requestFire(A, 0));
        assertFalse(squad.requestFire(B, 1));
        squad.releaseFire(A);
        assertFalse(squad.requestFire(A, 29));
        assertTrue(squad.requestFire(B, 29));
    }

    @Test void abandonedPermitExpiresAtTheEndOfTheBurstInsteadOfBlockingForever() {
        var squad = squad(A, B);
        assertTrue(squad.requestFire(A, 0));
        assertFalse(squad.requestFire(B, 28));
        assertTrue(squad.requestFire(B, 29));
        assertFalse(squad.hasFirePermit(A, 29));
    }

    @Test void removalOrControlReleaseCannotLeaveADeadWaiterAtTheHeadOfTheQueue() {
        var squad = squad(A, B, C, D);
        squad.requestFire(A, 0);
        squad.requestFire(B, 0);
        squad.requestFire(C, 0);
        squad.requestFire(D, 0);
        squad.releaseFire(C);
        squad.remove(A);
        assertTrue(squad.requestFire(D, 1));
        assertFalse(squad.hasFirePermit(A, 1));
    }

    @Test void nonMembersCannotAcquireOrRetainFirePermissions() {
        var squad = squad(A);
        assertFalse(squad.requestFire(B, 0));
        assertTrue(squad.requestFire(A, 0));
        squad.remove(A);
        assertFalse(squad.hasFirePermit(A, 1));
        assertFalse(squad.requestFire(A, 1));
    }

    @Test void aClockResetDoesNotReviveAFuturePermit() {
        var squad = squad(A, B);
        assertTrue(squad.requestFire(A, 100));
        assertFalse(squad.hasFirePermit(A, 99));
        assertTrue(squad.requestFire(B, 99));
    }

    private BlackwatchSquad.Contact contact(UUID observer, long tick) {
        return new BlackwatchSquad.Contact(observer, TARGET,
                new BlackwatchSquad.Position(6, 2, 7), new BlackwatchSquad.Position(0, 2, 0), tick, false);
    }

    @Test void aLongerFactionAimRetainsPermissionThroughItsLastShot() {
        var squad = squad(A, B);
        assertTrue(squad.requestFire(A, 100, 33));
        assertTrue(squad.hasFirePermit(A, 132));
        assertFalse(squad.requestFire(B, 132, 33));
        assertTrue(squad.requestFire(B, 133, 33));
        assertFalse(squad.requestFire(A, 134, 0));
    }
}
