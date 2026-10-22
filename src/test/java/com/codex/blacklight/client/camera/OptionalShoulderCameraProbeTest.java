package com.codex.blacklight.client.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionalShoulderCameraProbeTest {
    public interface Api {
        State STATE=new State();
        static Api getInstance(){return STATE.instance;}
        boolean isShoulderSurfing();
        boolean isTemporaryFirstPerson();
    }
    public static class State { Api instance; }
    private static class Provider implements Api {
        boolean active,temporaryFirstPerson;
        public boolean isShoulderSurfing(){return active;}
        public boolean isTemporaryFirstPerson(){return temporaryFirstPerson;}
    }
    @Test void activeCameraAndItsAimingTransitionRetainOwnership() throws Exception {
        var provider=new Provider();Api.STATE.instance=provider;
        var probe=new OptionalShoulderCameraProbe(Api.class);
        assertFalse(probe.active());
        provider.active=true;assertTrue(probe.active());
        provider.temporaryFirstPerson=true;assertTrue(probe.active());
        provider.active=false;assertFalse(probe.active());
    }
    @Test void reconnectReadsTheCurrentController() throws Exception {
        var probe=new OptionalShoulderCameraProbe(Api.class);
        Api.STATE.instance=null;assertFalse(probe.active());
        var provider=new Provider();provider.active=true;Api.STATE.instance=provider;
        assertTrue(probe.active());
        Api.STATE.instance=new Provider();assertFalse(probe.active());
    }
}
