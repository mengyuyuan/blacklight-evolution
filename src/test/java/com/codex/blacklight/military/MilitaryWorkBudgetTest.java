package com.codex.blacklight.military;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MilitaryWorkBudgetTest {
    private UUID id(int i) { return new UUID(0, i); }
    @Test void navigationAndDiscoveryUseIndependentEightAndFourTokens() {
        var budget = new MilitaryWorkBudget();
        for (int i = 0; i < 30; i++) for (var kind : MilitaryWorkBudget.Kind.values()) budget.enqueue(kind, id(i), 0);
        assertEquals(8, budget.drain(MilitaryWorkBudget.Kind.NAVIGATION, 0).size());
        assertEquals(4, budget.drain(MilitaryWorkBudget.Kind.DISCOVERY, 0).size());
        assertTrue(budget.drain(MilitaryWorkBudget.Kind.NAVIGATION, 0).isEmpty());
        assertEquals(8, budget.drain(MilitaryWorkBudget.Kind.NAVIGATION, 1).size());
    }
    @Test void duplicateRequestsCoalesceAndRespectPerMemberCooldown() {
        var budget = new MilitaryWorkBudget(); var kind = MilitaryWorkBudget.Kind.NAVIGATION;
        for (int i = 0; i < 100; i++) budget.enqueue(kind, id(1), 0);
        assertEquals(java.util.List.of(id(1)), budget.drain(kind, 0));
        budget.enqueue(kind, id(1), 1);
        assertTrue(budget.drain(kind, 9).isEmpty());
        assertEquals(java.util.List.of(id(1)), budget.drain(kind, 10));
        budget.enqueue(MilitaryWorkBudget.Kind.DISCOVERY, id(1), 10);
        assertEquals(1, budget.drain(MilitaryWorkBudget.Kind.DISCOVERY, 10).size());
        budget.enqueue(MilitaryWorkBudget.Kind.DISCOVERY, id(1), 11);
        assertTrue(budget.drain(MilitaryWorkBudget.Kind.DISCOVERY, 29).isEmpty());
        assertEquals(1, budget.drain(MilitaryWorkBudget.Kind.DISCOVERY, 30).size());
    }
    @Test void continuousNewRequestsCannotStarveAnExistingWaiter() {
        var budget = new MilitaryWorkBudget(); var kind = MilitaryWorkBudget.Kind.NAVIGATION;
        for (int i = 0; i < 216; i++) budget.enqueue(kind, id(i), 0);
        var served = new HashSet<UUID>();
        for (int tick = 0; tick < 27; tick++) {
            served.addAll(budget.drain(kind, tick));
            for (int i = 0; i < 216; i++) budget.enqueue(kind, id(i), tick);
        }
        assertEquals(216, served.size());
    }
    @Test void cancellationRemovesPendingWorkAndClockRollbackDoesNotFreezeIt() {
        var budget = new MilitaryWorkBudget(); var kind = MilitaryWorkBudget.Kind.NAVIGATION;
        budget.enqueue(kind, id(1), 100); budget.enqueue(kind, id(2), 100); budget.remove(id(1));
        assertEquals(java.util.List.of(id(2)), budget.drain(kind, 100));
        budget.enqueue(kind, id(2), 101);
        assertEquals(java.util.List.of(id(2)), budget.drain(kind, 2));
    }
}
