package com.codex.blacklight.movement;

/** Forgiving optional sprint-key shortcut; the dedicated dodge key triggers on press. */
public final class SprintTap {
    private int heldTicks;
    public boolean update(boolean down,boolean eligible) {
        if(!eligible){heldTicks=0;return false;}
        if(down){heldTicks=Math.min(11,heldTicks+1);return false;}
        boolean tap=heldTicks>0&&heldTicks<=10;
        heldTicks=0;return tap;
    }
    public void reset(){heldTicks=0;}
}
