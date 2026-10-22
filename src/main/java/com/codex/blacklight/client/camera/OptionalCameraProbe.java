package com.codex.blacklight.client.camera;

import java.lang.reflect.Method;

/** Optional, read-only bridge: no static link to an installed combat engine. */
final class OptionalCameraProbe {
    private final Method instance, battle, cameraInstance, tps, lock;
    OptionalCameraProbe(Class<?> engine,Class<?> camera) throws NoSuchMethodException {
        instance=engine.getMethod("getInstance");
        Method mode=find(engine,"isEpicFightMode");
        battle=mode!=null?mode:engine.getMethod("isBattleMode");
        cameraInstance=camera==null?null:camera.getMethod("getInstance");
        tps=camera==null?null:find(camera,"isTPSMode");
        lock=camera==null?null:find(camera,"isLockingOnTarget");
    }
    private static Method find(Class<?> type,String name) {
        try{return type.getMethod(name);}catch(NoSuchMethodException e){return null;}
    }
    boolean active() throws ReflectiveOperationException {
        Object engine=instance.invoke(null);
        if(engine==null)return false;
        if(Boolean.TRUE.equals(battle.invoke(engine)))return true;
        Object camera=cameraInstance==null?null:cameraInstance.invoke(null);
        return camera!=null&&((tps!=null&&Boolean.TRUE.equals(tps.invoke(camera)))
                ||(lock!=null&&Boolean.TRUE.equals(lock.invoke(camera))));
    }
}
