package com.codex.blacklight.combat;

/** Clip-derived release clock. The existing projectile collision owns the actual hit. */
public final class BlockSlamMotion {
    public static final int PHASE=5;
    private BlockSlamMotion(){}
    public static boolean available(boolean two){return LocalVisualMotion.find(BlockThrowMotion.key(two,"slam")).isPresent();}
    public static int releaseTick(boolean two){
        var clip=LocalVisualMotion.find(BlockThrowMotion.key(two,"slam")).orElseThrow();
        int tick=clip.animation().getAsJsonObject("source").get("releaseTick").getAsInt();
        if(tick<2||tick>=clip.durationTicks())throw new IllegalArgumentException("Invalid block slam release clock");
        return tick;
    }
}
