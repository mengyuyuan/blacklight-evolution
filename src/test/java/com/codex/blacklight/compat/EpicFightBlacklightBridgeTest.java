package com.codex.blacklight.compat;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Epic Fight's EntityPatch declares client-only signatures such as isOutlineVisible(LocalPlayer).
 * A dedicated server cannot list that class's methods, so the bridge must resolve getOriginal()
 * without reflecting over the whole patch hierarchy.
 */
class EpicFightBlacklightBridgeTest {
    public static final class ClientOnly {}

    public static class Patch {
        public final Entity getOriginal() { return null; }
        public boolean isOutlineVisible(ClientOnly viewer) { return false; }
    }

    public static final class ServerPatch extends Patch {}

    @Test void resolvesOriginalWhenThePatchHierarchyHasClientOnlySignatures() throws Throwable {
        Class<?> serverPatch = Class.forName(ServerPatch.class.getName(), true, new DedicatedServerLoader());
        Object patch = serverPatch.getConstructor().newInstance();
        assertThrows(NoClassDefFoundError.class, () -> serverPatch.getSuperclass().getDeclaredMethods());
        assertNull((Entity) EpicFightBlacklightBridge.originalAccessor(serverPatch).invokeExact(patch));
    }

    @Test void missingBattleModeEventLeavesEpicFightUntouched() {
        // Older or changed Epic Fight builds must not break Blacklight's mod construction.
        assertFalse(EpicFightBlacklightBridge.register(null, "yesman.epicfight.api.forgeevent.MissingBattleModeEvent"));
    }

    /** Defines the patch classes itself and, like a dedicated server, cannot load the client-only type. */
    private static final class DedicatedServerLoader extends ClassLoader {
        DedicatedServerLoader() { super(EpicFightBlacklightBridgeTest.class.getClassLoader()); }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(ClientOnly.class.getName())) throw new ClassNotFoundException(name);
            if (!name.equals(Patch.class.getName()) && !name.equals(ServerPatch.class.getName())) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }
    }
}