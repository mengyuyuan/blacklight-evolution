package com.codex.blacklight.client.camera;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionalCameraProbeTest {
    public static class Legacy {
        static Legacy current;
        boolean battle;
        public static Legacy getInstance(){return current;}
        public boolean isBattleMode(){return battle;}
    }
    public static class Modern {
        static Modern current;
        boolean battle;
        public static Modern getInstance(){return current;}
        public boolean isEpicFightMode(){return battle;}
        public boolean isBattleMode(){throw new AssertionError("Prefer modern API");}
    }
    public static class Camera {
        static Camera current;
        boolean tps,lock;
        public static Camera getInstance(){return current;}
        public boolean isTPSMode(){return tps;}
        public boolean isLockingOnTarget(){return lock;}
    }
    public static class Broken {
        public static Broken getInstance(){throw new IllegalStateException("Not ready");}
        public boolean isBattleMode(){return false;}
    }
    @BeforeEach void reset(){
        Legacy.current=new Legacy();Modern.current=new Modern();Camera.current=new Camera();
    }
    @Test void legacyBattleModeHandsBackOnExit() throws Exception {
        var probe=new OptionalCameraProbe(Legacy.class,null);
        assertFalse(probe.active());
        Legacy.current.battle=true;assertTrue(probe.active());
        Legacy.current.battle=false;assertFalse(probe.active());
    }
    @Test void modernModeUsesModernMethod() throws Exception {
        var probe=new OptionalCameraProbe(Modern.class,Camera.class);
        assertFalse(probe.active());
        Modern.current.battle=true;assertTrue(probe.active());
        Modern.current.battle=false;assertFalse(probe.active());
    }
    @Test void cameraZoomAndLockMustAlsoYield() throws Exception {
        var probe=new OptionalCameraProbe(Modern.class,Camera.class);
        Camera.current.tps=true;assertTrue(probe.active());
        Camera.current.tps=false;Camera.current.lock=true;assertTrue(probe.active());
        Camera.current.lock=false;assertFalse(probe.active());
    }
    @Test void respawnAndReconnectReadNewInstances() throws Exception {
        var probe=new OptionalCameraProbe(Legacy.class,null);
        Legacy.current.battle=true;assertTrue(probe.active());
        Legacy.current=null;assertFalse(probe.active());
        Legacy.current=new Legacy();assertFalse(probe.active());
    }
    @Test void cameraMayNotExistAtLogin() throws Exception {
        Camera.current=null;
        assertFalse(new OptionalCameraProbe(Modern.class,Camera.class).active());
    }
    @Test void unknownEngineFailsExplicitlyForConservativeFallback() {
        assertThrows(NoSuchMethodException.class,()->new OptionalCameraProbe(String.class,null));
    }
    @Test void invocationFailureReachesConservativeFallback() throws Exception {
        var probe=new OptionalCameraProbe(Broken.class,null);
        assertThrows(ReflectiveOperationException.class,probe::active);
    }
}
