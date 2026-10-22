package com.codex.blacklight.client.camera;

import java.lang.reflect.Method;

/** Use the public interface, not the provider implementation's visibility. */
final class OptionalShoulderCameraProbe {
    private final Method instance,active;
    OptionalShoulderCameraProbe(Class<?> api) throws NoSuchMethodException {
        instance=api.getMethod("getInstance");
        active=api.getMethod("isShoulderSurfing");
    }
    boolean active() throws ReflectiveOperationException {
        Object controller=instance.invoke(null);
        return controller!=null&&Boolean.TRUE.equals(active.invoke(controller));
    }
}
