package com.codex.blacklight.client.camera;

import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModePerspectiveTest {
    @Test void entrySwitchesOnceAndExitRestoresPreviousView(){
        var state=new ModePerspective();
        assertEquals(CameraType.THIRD_PERSON_BACK,state.update(CameraType.FIRST_PERSON,true,false));
        assertEquals(CameraType.THIRD_PERSON_BACK,state.update(CameraType.THIRD_PERSON_BACK,true,false));
        assertEquals(CameraType.FIRST_PERSON,state.update(CameraType.THIRD_PERSON_BACK,false,false));
    }
    @Test void manualPerspectiveNeverGetsForcedBackDuringMode(){
        var state=new ModePerspective();state.update(CameraType.FIRST_PERSON,true,false);
        assertEquals(CameraType.THIRD_PERSON_FRONT,state.update(CameraType.THIRD_PERSON_FRONT,true,true));
        assertEquals(CameraType.FIRST_PERSON,state.update(CameraType.FIRST_PERSON,true,false));
        assertEquals(CameraType.THIRD_PERSON_BACK,state.update(CameraType.THIRD_PERSON_BACK,false,false));
    }
    @Test void nextEntryWorksAfterDisconnect(){
        var state=new ModePerspective();state.update(CameraType.THIRD_PERSON_FRONT,true,false);
        assertEquals(CameraType.THIRD_PERSON_FRONT,state.update(CameraType.THIRD_PERSON_BACK,false,false));
        assertEquals(CameraType.THIRD_PERSON_BACK,state.update(CameraType.THIRD_PERSON_FRONT,true,false));
    }
}
