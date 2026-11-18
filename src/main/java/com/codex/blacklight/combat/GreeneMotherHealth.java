package com.codex.blacklight.combat;

/** P1 MotherHealth receiver arithmetic, independent of Minecraft damage hooks.
 * Native 102c07a0 counts valid child handles before dispatch, including children
 * which later reject damage. The Minecraft proxy adapter detaches a broken
 * slot immediately; native dead-handle retirement timing is not reproduced. */
public final class GreeneMotherHealth {
    public static final float CHILD_REDIRECT=.6000000238418579F;

    /** Divide first, then multiply, matching the native float operation order. */
    public static float perChild(float incoming,int validChildren){
        if(!Float.isFinite(incoming)||incoming<=0||validChildren<=0)return 0;
        return incoming/validChildren*CHILD_REDIRECT;
    }
    private GreeneMotherHealth(){}
}
