package com.codex.blacklight.firearms;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirearmViewStateTest {
    private final FirearmViewState view = new FirearmViewState();
    private FirearmViewState.Action observe(int magazine, long readyAt, long now) {
        return view.observe("military_rifle", 0, magazine, readyAt, 4, 40, now, true);
    }

    private static FirearmViewState.HeldSnapshot held(int slot, int magazine, long readyAt) {
        return new FirearmViewState.HeldSnapshot("military_rifle", slot, magazine, readyAt, 4, 40);
    }

    @Test void firstSnapshotDoesNotReplayAnOldShot() {
        assertEquals(FirearmViewState.Action.NONE, observe(29, 104, 100));
        assertEquals(0, view.frame(100).recoil());
    }

    @Test void authoritativeMagazineDecreaseStartsOneRecoil() {
        observe(30, 0, 99);
        assertEquals(FirearmViewState.Action.SHOT, observe(29, 104, 100));
        assertTrue(view.frame(100.5).recoil() > 0);
        assertEquals(FirearmViewState.Action.NONE, observe(29, 104, 101));
    }

    @Test void emptyAndCooldownSnapshotsNeverStartRecoil() {
        observe(0, 104, 100);
        assertEquals(FirearmViewState.Action.NONE, observe(0, 104, 101));
        assertEquals(0, view.frame(101).recoil());
    }

    @Test void inventoryEditWithoutAdvancingServerDeadlineIsNotAShot() {
        observe(30, 104, 100);
        assertEquals(FirearmViewState.Action.NONE, observe(29, 104, 101));
        assertEquals(0, view.frame(101).recoil());
    }

    @Test void delayedExpiredSnapshotDoesNotReplayShot() {
        observe(30, 0, 99);
        assertEquals(FirearmViewState.Action.NONE, observe(29, 104, 110));
    }

    @Test void smallNetworkDelayStillShowsAnAuthoritativeShotOnce() {
        observe(30, 0, 99);
        assertEquals(FirearmViewState.Action.SHOT, observe(29, 104, 106));
        assertEquals(FirearmViewState.Action.NONE, observe(29, 104, 107));
    }

    @Test void successfulReloadDipsGunUntilServerDeadline() {
        observe(10, 0, 99);
        assertEquals(FirearmViewState.Action.RELOAD, observe(30, 140, 100));
        assertTrue(view.frame(120).reload() > .9F);
        assertEquals(0, view.frame(140).reload());
        assertEquals(0, view.frame(141).reload());
    }

    @Test void unsuccessfulFullMagazineReloadProducesNoFeedback() {
        observe(30, 0, 99);
        assertEquals(FirearmViewState.Action.NONE, observe(30, 0, 100));
        assertEquals(0, view.frame(100).reload());
    }

    @Test void switchingSlotDoesNotMistakeOtherGunForAShot() {
        observe(30, 0, 99);
        assertEquals(FirearmViewState.Action.NONE, view.observe("military_rifle", 1, 29, 104, 4, 40, 100, true));
        assertEquals(0, view.frame(100).recoil());
    }

    @Test void switchingWeaponOrHandSlotClearsPreviousFeedback() {
        observe(30, 0, 99);
        observe(29, 104, 100);
        assertEquals(FirearmViewState.Action.NONE, view.observe("military_carbine", -1, 20, 105, 3, 35, 101, true));
        assertEquals(0, view.frame(101).recoil());
    }

    @Test void menuAndReconnectDiscardPendingFeedback() {
        observe(30, 0, 99);
        observe(29, 104, 100);
        view.observe("military_rifle", 0, 28, 108, 4, 40, 104, false);
        assertEquals(0, view.frame(104).recoil());
        assertEquals(FirearmViewState.Action.NONE, observe(28, 108, 105));
        view.reset();
        assertEquals(FirearmViewState.Action.NONE, observe(27, 112, 109));
    }

    @Test void worldClockRollbackRebaselinesInsteadOfReplaying() {
        observe(30, 0, 99);
        assertEquals(FirearmViewState.Action.NONE, observe(29, 14, 10));
        assertEquals(0, view.frame(10).recoil());
    }

    @Test void repeatedShotsStayBoundedAndReturnToRest() {
        observe(30, 0, 99);
        for (int i = 0; i < 5; i++) {
            assertEquals(FirearmViewState.Action.SHOT, observe(29 - i, 104 + 4L * i, 100 + 4L * i));
            for (double partial = 0; partial < 1; partial += .1) {
                float recoil = view.frame(100 + 4L * i + partial).recoil();
                assertTrue(recoil >= 0 && recoil <= 1);
            }
        }
        assertEquals(0, view.frame(130).recoil());
    }

    @Test void partialReloadRemainsBoundedAndCannotLatch() {
        observe(0, 0, 99);
        observe(7, 140, 100);
        for (double t = 100; t < 145; t += .25) {
            float reload = view.frame(t).reload();
            assertTrue(reload >= 0 && reload <= 1);
        }
        assertEquals(0, view.frame(145).reload());
    }

    @Test void twoHandsTrackIndependently() {
        FirearmViewState offhand = new FirearmViewState();
        observe(30, 0, 99);
        offhand.observe("military_rifle", -1, 20, 0, 4, 40, 99, true);
        observe(29, 104, 100);
        assertEquals(0, offhand.frame(100).recoil());
        assertTrue(view.frame(100.5).recoil() > 0);
    }

    @Test void swappingSameModelGunsRebaselinesBothHandsWithoutAShot() {
        FirearmViewState off = new FirearmViewState();
        FirearmViewState.observeHands(view, held(0, 30, 0), off, held(-1, 10, 104), 100, true);
        var swapped = FirearmViewState.observeHands(view, held(0, 10, 104), off, held(-1, 30, 0), 101, true);
        assertEquals(FirearmViewState.Action.NONE, swapped.main());
        assertEquals(FirearmViewState.Action.NONE, swapped.off());
        assertEquals(0, view.frame(101).recoil());
        assertEquals(0, off.frame(101).recoil());
    }

    @Test void mainHandShotMatchingOtherHandSnapshotIsStillAShot() {
        FirearmViewState off = new FirearmViewState();
        FirearmViewState.observeHands(view, held(0, 30, 0), off, held(-1, 29, 104), 100, true);
        var fired = FirearmViewState.observeHands(view, held(0, 29, 104), off, held(-1, 29, 104), 101, true);
        assertEquals(FirearmViewState.Action.SHOT, fired.main());
        assertEquals(FirearmViewState.Action.NONE, fired.off());
        assertTrue(view.frame(101.5).recoil() > 0);
    }

    @Test void offHandShotMatchingOtherHandSnapshotIsStillAShot() {
        FirearmViewState off = new FirearmViewState();
        FirearmViewState.observeHands(view, held(0, 29, 104), off, held(-1, 30, 0), 100, true);
        var fired = FirearmViewState.observeHands(view, held(0, 29, 104), off, held(-1, 29, 104), 101, true);
        assertEquals(FirearmViewState.Action.NONE, fired.main());
        assertEquals(FirearmViewState.Action.SHOT, fired.off());
        assertTrue(off.frame(101.5).recoil() > 0);
    }

    @Test void realShotAfterSwapUsesNewGunBaseline() {
        FirearmViewState off = new FirearmViewState();
        FirearmViewState.observeHands(view, held(0, 30, 0), off, held(-1, 10, 104), 100, true);
        FirearmViewState.observeHands(view, held(0, 10, 104), off, held(-1, 30, 0), 101, true);
        var fired = FirearmViewState.observeHands(view, held(0, 9, 108), off, held(-1, 30, 0), 104, true);
        assertEquals(FirearmViewState.Action.SHOT, fired.main());
        assertEquals(FirearmViewState.Action.NONE, fired.off());
    }

    @Test void identicalHandSnapshotsProduceNoNewFeedback() {
        FirearmViewState off = new FirearmViewState();
        FirearmViewState.observeHands(view, held(0, 29, 104), off, held(-1, 29, 104), 100, true);
        var unchanged = FirearmViewState.observeHands(view, held(0, 29, 104), off, held(-1, 29, 104), 101, true);
        assertEquals(FirearmViewState.Action.NONE, unchanged.main());
        assertEquals(FirearmViewState.Action.NONE, unchanged.off());
        assertEquals(0, view.frame(101).recoil());
        assertEquals(0, off.frame(101).recoil());
    }

    @Test void pairedObservationStillResetsOnMenuAndEmptyHand() {
        FirearmViewState off = new FirearmViewState();
        FirearmViewState.observeHands(view, held(0, 30, 0), off, held(-1, 30, 0), 99, true);
        FirearmViewState.observeHands(view, held(0, 29, 104), off, held(-1, 30, 0), 100, true);
        FirearmViewState.observeHands(view, held(0, 29, 104), off, null, 101, false);
        var reopened = FirearmViewState.observeHands(view, held(0, 29, 104), off, null, 102, true);
        assertEquals(FirearmViewState.Action.NONE, reopened.main());
        assertEquals(FirearmViewState.Action.NONE, reopened.off());
        assertEquals(0, view.frame(102).recoil());
    }

    @Test void sourceShotStartsAtRestThenRisesAndRecoversOnce() {
        observe(30,0,99);observe(29,104,100);
        assertEquals(0,view.frame(100).recoil());
        assertTrue(view.frame(100.5).recoil()>0);
        assertEquals(1,view.frame(100+20/12d).recoil(),.0001);
        assertEquals(0,view.frame(110).recoil(),.0001);
        assertEquals(0,view.frame(120).recoil());
        assertEquals(.05,view.shotAgeSeconds(101),.00001);
    }
    @Test void multiRoundInventoryReplacementIsNotARealSingleShot() {
        observe(30,0,99);
        assertEquals(FirearmViewState.Action.NONE,observe(10,104,100));
        assertEquals(0,view.frame(101).recoil());
    }
    @Test void reloadProgressIsServerTimedAndDoesNotTriggerAShotAtCompletion() {
        observe(10,0,99);observe(30,140,100);
        assertEquals(0,view.reloadProgress(100));assertEquals(.5,view.reloadProgress(120));
        assertEquals(1,view.reloadProgress(140));
        assertEquals(FirearmViewState.Action.NONE,observe(30,140,140));
        assertEquals(0,view.frame(140).recoil());
    }
}
