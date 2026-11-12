package com.codex.blacklight.military;

import com.codex.blacklight.firearms.AmmoState;
import com.codex.blacklight.firearms.WeaponCatalog;
import com.codex.blacklight.firearms.WeaponDefinition;

/** P1 firing-window structure, with explicit Minecraft pacing and finite ammunition. */
public final class MilitaryFireControl {
    public static final int IDLE=0, AIM=1, FIRE=2, RELOAD=3, RECOVER=4, MELEE=5;
    // startup_fig offsets 1322756, 1323176, 1323772: range, hit window, reservation.
    public static final double MELEE_RANGE=2.5;
    public static final int MELEE_HIT_BEGIN=9, MELEE_HIT_END=10, MELEE_TICKS=20, MELEE_COOLDOWN=80;

    public record Plan(int aimTicks,int fireTicks,int recoveryTicks) {
        public int leaseTicks(){return aimTicks+fireTicks;}
        public int cycleTicks(){return leaseTicks()+recoveryTicks;}
        public int phase(long age){return age<0||age>=cycleTicks()?IDLE:age<aimTicks?AIM:age<leaseTicks()?FIRE:RECOVER;}
        public boolean canShoot(long age,AmmoState ammunition,long now){return phase(age)==FIRE&&ammunition.canFire(now);}
    }
    public static Plan plan(WeaponDefinition weapon,MilitaryCombatProfile profile){
        // P1 standing rifle Shoot spans 0..0.5 s (1334876); SAW is open-ended (1340576).
        // Bound the SAW's turn to 1.5 s for the Minecraft squad's finite fire lease.
        int window=weapon.kind()!=WeaponDefinition.Kind.BULLET?1:weapon.id().equals(WeaponCatalog.MACHINE_GUN.id())?30:10;
        return new Plan(profile.aimTicks(),window,Math.max(8,profile.cycleTicks()-profile.aimTicks()-window));
    }
    public static boolean reloading(AmmoState ammunition,WeaponDefinition weapon,long started,long now){
        return started>=0&&now>=started&&now<ammunition.readyAt()&&ammunition.readyAt()-started==weapon.reloadTicks();
    }
    public static boolean meleeContact(long age){return age>=MELEE_HIT_BEGIN&&age<=MELEE_HIT_END;}
    private MilitaryFireControl(){}
}
