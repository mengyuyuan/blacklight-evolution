package com.codex.blacklight.combat;

/** Per-move outcomes. The victim controller owns flight, landing and recovery. */
public record ImpactProfile(int kind,int motion,int stun,double push,double lift,boolean knockdown) {
    public static final int BODY=0,LEFT=1,RIGHT=2,BACKHAND=3,UP=4,FALL=5,DOWN=6,RISE=7;
    public static final int CLAW_RIP=8, HAMMER_BUCKLE=18;
    public static ImpactProfile of(WeaponMove move){
        var m=move.uncharged();boolean charged=m!=move;
        if(m==WeaponMove.CLAW_POUNCE)
            return new ImpactProfile(HitReaction.HEAVY,FALL,32,.50,.12,true);
        if(m==WeaponMove.COMBO_FLOAT||m==WeaponMove.MUSCLE_UPPERCUT)
            return new ImpactProfile(HitReaction.LAUNCH,UP,55,.08,.8,true);
        if(m==WeaponMove.AIR_AUTO4||m==WeaponMove.BLADE_DIVE||m==WeaponMove.COMBO_AXE||m==WeaponMove.COMBO_AXE_HANDLE)
            return new ImpactProfile(HitReaction.SLAM,FALL,55,.18,-.95,true);
        if(m.quake()||m==WeaponMove.CLAW_GROUNDSPIKE)
            return new ImpactProfile(HitReaction.LAUNCH,UP,55,charged?.72:.48,charged?.9:.65,true);
        if(m.airCombo()||m==WeaponMove.AIR_LAUNCH_FOLLOW)
            return new ImpactProfile(HitReaction.LAUNCH,UP,45,.035,.22,true);
        if(m==WeaponMove.AIR_KICK||m==WeaponMove.AIR_PAIN||m==WeaponMove.MUSCLE_SUPERMAN||m==WeaponMove.COMBO_PAIN)
            return new ImpactProfile(HitReaction.HEAVY,FALL,50,1.05,.25,true);
        if(m==WeaponMove.WHIP_SWEEP||m==WeaponMove.COMBO_SPIN_LEFT||m==WeaponMove.COMBO_CLAP||m==WeaponMove.AIR_CLAP)
            return new ImpactProfile(HitReaction.LASH,BACKHAND,45,charged?.9:.7,.25,true);
        boolean end=m==WeaponMove.CLAW_AUTO3||m==WeaponMove.BLADE_COMBO_D||m==WeaponMove.HAMMER_COMBO_D
                ||m==WeaponMove.MUSCLE_COMBO_C||m==WeaponMove.COMBO_MACH||m==WeaponMove.WHIP_COMBO_C;
        int motion=switch(m){
            case CLAW_AUTO3 -> CLAW_RIP;
            case CLAW_AUTO2,BLADE_RETURN,BLADE_COMBO_C2,HAMMER_HOOK,WHIP_RETURN,COMBO_BACKHAND -> LEFT;
            case CLAW_AUTO1,BLADE_CUT,BLADE_COMBO_C,HAMMER_JAB,WHIP_LASH,MUSCLE_JAB -> RIGHT;
            case COMBO_AXE,COMBO_AXE_HANDLE -> FALL;
            default -> BODY;
        };
        boolean hammer=m.form==com.codex.blacklight.power.PowerForm.HAMMERFISTS;
        return new ImpactProfile(m.form==com.codex.blacklight.power.PowerForm.WHIPFIST?HitReaction.LASH:end||charged||hammer?HitReaction.HEAVY:HitReaction.STAGGER,
                motion,end||charged?40:hammer?12:8,end||charged?(hammer?1.05:.7):hammer?.22:.10,end||charged?.2:0,end||charged);
    }
}
