package com.codex.blacklight.compat;

import com.codex.blacklight.city.HydraTransport;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflection-only boundary for optional Epic Fight hooks.
 *
 * No Epic Fight classes are linked into Blacklight's runtime class path. The
 * optional Mixin passes its event hook here and the original event is always
 * dispatched before Blacklight applies its Prototype-mode cancellation rule.
 */
public final class EpicFightBlacklightBridge {
    private EpicFightBlacklightBridge() {}

    public static Object postEpicFightModeEvent(Object hook, Object event, Object listener) {
        final Object posted;
        try {
            Method dispatch = findCompatibleMethod(hook == null ? null : hook.getClass(),
                    "postWithListener", event, listener);
            if (dispatch == null) throw new NoSuchMethodException("postWithListener(2 args)");
            dispatch.setAccessible(true);
            posted = dispatch.invoke(hook, event, listener);
        } catch (Throwable error) {
            throw new IllegalStateException("Blacklight/Epic Fight event dispatch failed", unwrap(error));
        }

        // The event hook must still notify Epic Fight listeners. Only cancel the
        // mode event afterwards, and only for Blacklight's active Prototype form.
        try {
            Object patch = invokeNoArgs(event, "getPlayerPatch");
            Object original = originalEntity(patch);
            if (original instanceof Player player
                    && isPrototypeMode(player)
                    && !HydraTransport.active(player)) {
                invokeNoArgs(event, "cancel");
            }
        } catch (Throwable ignored) {
            // A changed optional API must never break the ordinary event dispatch.
        }
        return posted;
    }

    /**
     * Re-align the local Prototype body's yaw after Blacklight has applied its
     * movement/action ownership rules. Hydra transport retains its own yaw.
     */
    public static void alignPrototypeBodyYaw(Object movementEvent) {
        try {
            Object entity = invokeNoArgs(movementEvent, "getEntity");
            if (!(entity instanceof Player player)
                    || !isPrototypeMode(player)
                    || HydraTransport.active(player)) {
                return;
            }

            // Keep this common bridge safe to load on a dedicated server. Client
            // ownership and camera classes are reached by name only in this method.
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = invokeStaticNoArgs(minecraftClass, "getInstance");
            if (readField(minecraft, "player") != player) return;

            float yaw = currentViewYaw(player);
            if (!Float.isFinite(yaw)) return;
            player.setYBodyRot(yaw);
            writeFloatField(player, "yBodyRotO", yaw);
        } catch (Throwable ignored) {
            // This is a visual compatibility adjustment; never interrupt input.
        }
    }

    /**
     * EntityPatch also declares client-only signatures such as
     * isOutlineVisible(LocalPlayer). A dedicated server cannot list those
     * methods, so resolve only the erased getOriginal() accessor.
     */
    static Object originalEntity(Object patch) throws Throwable {
        if (patch == null) throw new NullPointerException("getOriginal target");
        return MethodHandles.publicLookup()
                .findVirtual(patch.getClass(), "getOriginal", MethodType.methodType(Entity.class))
                .invoke(patch);
    }

    private static boolean isPrototypeMode(Object original) {
        if (!(original instanceof Player player)) return false;
        var state = player.getData(ModAttachments.PLAYER_STATE);
        return state.isInfected() && state.isPrototypeMode();
    }

    private static float currentViewYaw(Player player) throws Exception {
        Class<?> chaseCamera = Class.forName(
                "com.codex.blacklight.client.camera.PrototypeChaseCamera");
        boolean ownsBlacklightView = Boolean.TRUE.equals(
                invokeStaticNoArgs(chaseCamera, "ownsView"));

        Class<?> cameraCompatibility = Class.forName(
                "com.codex.blacklight.client.camera.CameraCompatibility");
        boolean ownsExternalView = Boolean.TRUE.equals(
                invokeStaticNoArgs(cameraCompatibility, "externalOwnsView"));

        if (ownsBlacklightView || ownsExternalView) {
            Object value = invokeStaticNoArgs(chaseCamera, "controlYaw");
            if (value instanceof Number number && Float.isFinite(number.floatValue())) {
                return number.floatValue();
            }
        }
        return player.getYRot();
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        if (target == null) throw new NullPointerException(name + " target");
        Method method = findMethod(target.getClass(), name, 0);
        if (method == null) throw new NoSuchMethodException(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeStaticNoArgs(Class<?> owner, String name) throws Exception {
        Method method = findMethod(owner, name, 0);
        if (method == null || !Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException(name);
        }
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static Method findCompatibleMethod(Class<?> owner, String name,
                                                Object first, Object second) {
        if (owner == null) return null;
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 2) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (compatible(parameters[0], first) && compatible(parameters[1], second)) {
                    return method;
                }
            }
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 2) {
                Class<?>[] parameters = method.getParameterTypes();
                if (compatible(parameters[0], first) && compatible(parameters[1], second)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean compatible(Class<?> parameter, Object value) {
        if (value == null) return !parameter.isPrimitive();
        if (parameter.isInstance(value)) return true;
        return parameter.isPrimitive()
                && ((parameter == boolean.class && value instanceof Boolean)
                || (parameter == byte.class && value instanceof Byte)
                || (parameter == short.class && value instanceof Short)
                || (parameter == int.class && value instanceof Integer)
                || (parameter == long.class && value instanceof Long)
                || (parameter == float.class && value instanceof Float)
                || (parameter == double.class && value instanceof Double)
                || (parameter == char.class && value instanceof Character));
    }

    private static Method findMethod(Class<?> owner, String name, int parameterCount) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name)
                        && method.getParameterCount() == parameterCount) return method;
            }
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == parameterCount) return method;
        }
        return null;
    }

    private static Object readField(Object target, String name) throws Exception {
        if (target == null) throw new NullPointerException(name + " owner");
        Field field = findField(target.getClass(), name);
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean writeFloatField(Object target, String name, float value) {
        try {
            Field field = findField(target.getClass(), name);
            if (field == null || field.getType() != float.class) return false;
            field.setAccessible(true);
            field.setFloat(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the mapped Minecraft inheritance hierarchy.
            }
        }
        return null;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof java.lang.reflect.InvocationTargetException invocation
                && invocation.getCause() != null) return invocation.getCause();
        return error;
    }
}
