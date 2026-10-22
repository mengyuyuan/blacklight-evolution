package com.codex.blacklight.client.camera;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

/** Read optional camera state without changing its mode, target, or settings. */
public final class CameraCompatibility {
    private static boolean initialized,epicPresent,shoulderPresent,warnedEpic,warnedShoulder;
    private static OptionalCameraProbe epic;
    private static OptionalShoulderCameraProbe shoulder;
    private CameraCompatibility() { }

    public static boolean externalOwnsView() {
        if(Minecraft.getInstance().player==null)return false;
        if(!initialized) {
            initialized=true;
            epicPresent=ModList.get().isLoaded("epicfight");
            shoulderPresent=ModList.get().isLoaded("shouldersurfing");
        }
        // Check both controllers independently: a missing Epic Fight installation
        // must not prevent Shoulder Surfing from receiving its camera back.
        return epicOwnsView()||shoulderOwnsView();
    }

    private static boolean epicOwnsView() {
        if(!epicPresent)return false;
        try {
            if(epic==null) {
                var loader=CameraCompatibility.class.getClassLoader();
                var engine=Class.forName("yesman.epicfight.client.ClientEngine",false,loader);
                Class<?> camera=null;
                try{camera=Class.forName("yesman.epicfight.api.client.camera.EpicFightCameraAPI",false,loader);}
                catch(ClassNotFoundException ignored){ /* Older versions expose battle state only. */ }
                epic=new OptionalCameraProbe(engine,camera);
            }
            return epic.active();
        }catch(ReflectiveOperationException|LinkageError|RuntimeException error) {
            if(!warnedEpic){warnedEpic=true;BlacklightNeoMod.LOGGER.warn("Cannot read Epic Fight camera state; yielding Blacklight camera",error);}
            return true;
        }
    }

    private static boolean shoulderOwnsView() {
        if(!shoulderPresent)return false;
        try {
            if(shoulder==null)shoulder=new OptionalShoulderCameraProbe(Class.forName(
                    "com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing",false,
                    CameraCompatibility.class.getClassLoader()));
            return shoulder.active();
        }catch(ReflectiveOperationException|LinkageError|RuntimeException error) {
            if(!warnedShoulder){warnedShoulder=true;BlacklightNeoMod.LOGGER.warn("Cannot read Shoulder Surfing camera state; yielding Blacklight camera",error);}
            return true;
        }
    }
}
