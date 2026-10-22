package com.codex.blacklight.client.camera;

import net.minecraft.client.CameraType;

/** Enter behind the actor once; subsequent manual perspective changes win. */
public final class ModePerspective {
    private boolean active,owned;
    private CameraType previous;
    /** Forget our restoration lease without changing another controller's perspective. */
    public void relinquish(boolean mode){active=mode;owned=false;previous=null;}
    public CameraType update(CameraType current,boolean mode,boolean manual){
        if(mode&&!active){previous=current;owned=true;active=true;return CameraType.THIRD_PERSON_BACK;}
        if(owned&&(manual||current!=CameraType.THIRD_PERSON_BACK))owned=false;
        if(!mode&&active){active=false;var result=owned?previous:current;owned=false;previous=null;return result;}
        return current;
    }
}
