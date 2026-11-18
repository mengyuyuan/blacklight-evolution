package com.codex.blacklight.combat;

import java.util.function.BooleanSupplier;

/** The mother users have infinite ammo and no heat. The remaining weapon clock
 * is per spawner/socket, not per Shoot track. P1 0x104720d0 / 0x10471fc0. */
public final class GreeneWeaponCadence {
    private double remaining;
    public double remainingSeconds(){return remaining;}
    public void restore(double seconds){
        if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid weapon cooldown");
        remaining=seconds;
    }
    /** Exactly one spawn attempt per positive update. Failed spawns consume no
     * cooldown. There is no catch-up loop and no negative-time carry between shots.
     * Call every server update, including outside Shoot windows, with requested=false. */
    public boolean tick(double dt,boolean requested,double fireRate,BooleanSupplier tryFire){
        if(!Double.isFinite(dt)||dt<=0)return false;
        double next=remaining-dt;
        // MC double arithmetic must not postpone an exactly due shot by a whole
        // 20 Hz update because .2-.05-.05-.05-.05 leaves a few ULPs above zero.
        remaining=next<=8*Math.ulp(Math.max(remaining,dt))?0:next;
        if(!requested||remaining>0||!Double.isFinite(fireRate)||tryFire==null||!tryFire.getAsBoolean())return false;
        remaining=fireRate>0?1/fireRate:0;return true;
    }
}
