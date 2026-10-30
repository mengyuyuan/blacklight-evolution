package com.codex.blacklight.firearms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirearmReloadMotionTest {
    @Test void reloadReturnsToTheOriginalGripAndExtractionPrecedesInsertion(){
        var start=FirearmReloadMotion.sample(0);var end=FirearmReloadMotion.sample(1);
        assertEquals(start,end);assertTrue(start.magazineVisible());
        assertTrue(FirearmReloadMotion.sample(.30).magazineDrop()>.2);
        assertFalse(FirearmReloadMotion.sample(.45).magazineVisible());
        assertTrue(FirearmReloadMotion.sample(.55).magazineVisible());
        assertTrue(FirearmReloadMotion.sample(.70).magazineDrop()<.001);
        assertTrue(FirearmReloadMotion.sample(.84).bolt()>.99);
    }
    @Test void adjacentRenderedFramesRemainContinuousAndFinite(){
        var prior=FirearmReloadMotion.sample(0);
        for(int i=1;i<=1000;i++){
            var f=FirearmReloadMotion.sample(i/1000D);
            assertTrue(Double.isFinite(f.handDown())&&Double.isFinite(f.handBack()));
            assertTrue(Math.abs(f.handDown()-prior.handDown())<.015);
            assertTrue(Math.abs(f.handBack()-prior.handBack())<.015);
            assertTrue(f.lower()>=0&&f.lower()<=1&&f.release()>=0&&f.release()<=1);prior=f;
        }
    }
    @Test void synchronizedReloadResumesWithoutRestartingItsDeadline(){
        var view=new FirearmViewState();
        assertEquals(FirearmViewState.Action.NONE,view.observe("military_rifle",0,30,140,4,40,120,true,100));
        assertEquals(.5,view.reloadProgress(120),1e-8);
        assertTrue(view.frame(120).reload()>.99);
        assertEquals(1,view.reloadProgress(140),1e-8);
        assertEquals(0,view.frame(140).reload());
    }
    @Test void anotherBurstDoesNotSnapAnExistingKickBackToZero(){
        var view=new FirearmViewState();view.observe("military_rifle",0,30,0,4,40,99,true);
        view.observe("military_rifle",0,29,104,4,40,100,true);float before=view.frame(104).recoil();
        view.observe("military_rifle",0,28,108,4,40,104,true);
        assertEquals(before,view.frame(104).recoil(),1e-7);assertTrue(view.frame(104.5).recoil()>before);
        assertEquals(0,view.frame(120).recoil());
    }
}
