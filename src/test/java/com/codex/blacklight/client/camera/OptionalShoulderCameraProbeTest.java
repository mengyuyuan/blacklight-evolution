package com.codex.blacklight.client.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionalShoulderCameraProbeTest {
    public interface ViewApi {
        ViewState STATE=new ViewState();
        static ViewApi getInstance(){return STATE.instance;}
        boolean isShoulderSurfing();
        boolean isFreeLooking();
        CameraApi getCamera();
    }
    public interface CameraApi { float getYRot();float getXRot(); }
    public static class ViewState { ViewApi instance; }
    private static class ViewProvider implements ViewApi,CameraApi {
        float yaw,pitch;boolean free,active=true;
        public boolean isShoulderSurfing(){return active;}
        public boolean isFreeLooking(){return free;}
        public CameraApi getCamera(){return this;}
        public float getYRot(){return yaw;}public float getXRot(){return pitch;}
    }
    @Test void decoupledAndAbsoluteTouchCameraReadLogicalAnglesInsteadOfFrozenEntityYaw() throws Exception {
        var p=new ViewProvider();ViewApi.STATE.instance=p;var probe=new OptionalShoulderCameraProbe(ViewApi.class);
        for(float yaw:new float[]{0,90,-160,179,-179}) {
            p.yaw=yaw;p.pitch=-32;
            var angles=probe.angles(12,7).orElseThrow();assertEquals(yaw,angles.yaw());assertEquals(-32,angles.pitch());
        }
        p.free=true;var free=probe.angles(12,7).orElseThrow();assertEquals(12,free.yaw());assertEquals(7,free.pitch());
        p.free=false;p.yaw=Float.NaN;assertTrue(probe.angles(12,7).isEmpty());
        p.yaw=90;p.active=false;assertTrue(probe.angles(12,7).isEmpty());
        ViewApi.STATE.instance=null;assertTrue(probe.angles(12,7).isEmpty());
    }
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
