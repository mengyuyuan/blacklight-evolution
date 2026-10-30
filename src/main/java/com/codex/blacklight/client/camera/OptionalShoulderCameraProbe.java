package com.codex.blacklight.client.camera;

import java.lang.reflect.Method;

/** Use the public interface, not the provider implementation's visibility. */
final class OptionalShoulderCameraProbe {
    record Angles(float yaw,float pitch) {}
    private final Method instance,active,camera,yaw,pitch,freeLook;
    OptionalShoulderCameraProbe(Class<?> api) throws NoSuchMethodException {
        instance=api.getMethod("getInstance");
        active=api.getMethod("isShoulderSurfing");
        Method getter=null,y=null,x=null,free=null;
        try {
            getter=api.getMethod("getCamera");
            // Read through public interfaces; providers can be non-public.
            y=getter.getReturnType().getMethod("getYRot");
            x=getter.getReturnType().getMethod("getXRot");
        }catch(NoSuchMethodException ignored){getter=null;}
        try{free=api.getMethod("isFreeLooking");}catch(NoSuchMethodException ignored){}
        camera=getter;yaw=y;pitch=x;freeLook=free;
    }
    boolean active() throws ReflectiveOperationException {
        Object controller=instance.invoke(null);
        return controller!=null&&Boolean.TRUE.equals(active.invoke(controller));
    }
    java.util.Optional<Angles> angles(float playerYaw,float playerPitch) throws ReflectiveOperationException {
        Object controller=instance.invoke(null);
        if(controller==null||!Boolean.TRUE.equals(active.invoke(controller)))return java.util.Optional.empty();
        if(freeLook!=null&&Boolean.TRUE.equals(freeLook.invoke(controller)))
            return java.util.Optional.of(new Angles(playerYaw,playerPitch));
        if(camera==null)return java.util.Optional.empty();
        Object view=camera.invoke(controller);if(view==null)return java.util.Optional.empty();
        float y=((Number)yaw.invoke(view)).floatValue(),x=((Number)pitch.invoke(view)).floatValue();
        return Float.isFinite(y)&&Float.isFinite(x)?java.util.Optional.of(new Angles(y,x)):java.util.Optional.empty();
    }
}
