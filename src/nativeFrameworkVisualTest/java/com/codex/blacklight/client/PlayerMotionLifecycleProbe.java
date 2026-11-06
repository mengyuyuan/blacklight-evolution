package com.codex.blacklight.client;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Actual client respawn regression, opt-in and excluded from release artifacts. */
public final class PlayerMotionLifecycleProbe {
    private PlayerMotionLifecycleProbe() {}
    private static void inactive(LivingEntity stale) {
        if(ModAttachments.available(stale))throw new IllegalStateException("Fixture still has live capability");
        if(PlayerMotionController.isPlaying(stale,"basic_jump_charge")
                ||!PlayerMotionController.ended(stale)||PlayerMotionController.elapsed(stale)!=0
                ||PlayerMotionController.previewKey(stale)!=null)
            throw new IllegalStateException("Stale animation remained active after respawn");
        if(!PlayerMotionController.pose(stale,.5F).getJointTransformData().isEmpty()
                ||!PlayerMotionController.locomotionPose(stale,.5F).getJointTransformData().isEmpty())
            throw new IllegalStateException("Invalid capability still emitted a pose");
        if(!Float.isFinite(PlayerMotionController.facing(stale,.5F)))throw new IllegalStateException("Non-finite stale facing");
        PlayerMotionController.clear(stale);PlayerMotionController.clearPreview(stale);
        PlayerMotionController.pause(stale,false);PlayerMotionController.seek(stale,0);
        PlayerMotionController.reverse(stale,false);
    }
    public static void verify(LivingEntity stale,LivingEntity current) {
        if(stale==current)throw new IllegalStateException("Respawn did not replace the player");
        // Epic Fight can revive old-player capabilities during its own clone hook.
        // Cleanup must drop our clocks in either lifecycle, without altering theirs.
        PlayerMotionController.discard(stale);
        if(!ModAttachments.available(stale))inactive(stale);
        else if(PlayerMotionController.previewKey(stale)!=null)throw new IllegalStateException("Old player retained preview");
        if(!ModAttachments.available(current))throw new IllegalStateException("Respawn missing live capability");
        var storage=ModAttachments.storage(current);
        current.invalidateCaps();
        try { inactive(current); }
        finally { current.reviveCaps(); }
        if(ModAttachments.storage(current)!=storage)throw new IllegalStateException("Animation query replaced player storage");
    }
}
