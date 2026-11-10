package com.codex.blacklight.movement;

/** Client sprint intent. Only the sprint key, a vanilla double tap on the forward press edge,
 * or a sprint that is still being held forward keeps running. The server echo of the sprint
 * flag is never read back as intent, so releasing W always returns to walking. */
public final class SprintIntent {
    private SprintIntent(){}
    public static boolean resolve(boolean sprintKey,boolean forwardHeld,boolean lastForwardHeld,boolean lastSprinting,boolean vanillaSprinting){
        if(sprintKey)return true;
        if(!forwardHeld)return false;
        return lastSprinting||!lastForwardHeld&&vanillaSprinting;
    }
}
