package com.codex.blacklight.infection;

/** Finite, recorded mono cues. Motion is audible on a miss; contact requires accepted damage. */
public enum InfectedSoundCue {
    TENSION("tension",Group.CHARGE,.55F), SWING("swing",Group.MOTION,.43F),
    TEAR("tear",Group.CONTACT,.59F), PIERCE("pierce",Group.CONTACT,.57F),
    CRUSH("crush",Group.CONTACT,.68F), LASH("lash",Group.MOTION,.55F),
    RETRACT("retract",Group.RETRACT,.39F), LAUNCH("launch",Group.MOTION,.58F),
    BURST("burst",Group.CONTACT,.72F), ERUPT("erupt",Group.RELEASE,.68F),
    RESONANCE_CHARGE("resonance_charge",Group.CHARGE,.48F),
    RESONANCE("resonance",Group.CONTACT,.59F),
    MEND("mend",Group.MEND,.46F), GROUND("ground",Group.RELEASE,.68F),
    // Source event roles; packaged waveforms remain the credited authored Foley bank.
    COMMON_ATTACK("common_attack",Group.VOICE,.65F), COMMON_LAND("common_land",Group.RELEASE,.4F),
    COMMON_DEATH("common_death",Group.DEATH,.7F),
    HUNTER_GRUNT("hunter_grunt",Group.VOICE,.75F), HUNTER_ROAR("hunter_roar",Group.VOICE,.65F),
    HUNTER_WHOOSH("hunter_whoosh",Group.MOTION,.55F),
    HUNTER_HURT("hunter_hurt",Group.HURT,.75F), HUNTER_DEATH("hunter_death",Group.DEATH,.85F);

    public enum Group { CHARGE, MOTION, CONTACT, RELEASE, RETRACT, MEND, VOICE, HURT, DEATH }
    public final String id; public final Group group; public final float volume;
    InfectedSoundCue(String id,Group group,float volume){this.id=id;this.group=group;this.volume=volume;}
    public static InfectedSoundCue contact(InfectedAttackStyle style){return switch(style){
        case PIERCE->PIERCE;case CRUSH->CRUSH;case WHIP->TEAR;case BURST->BURST;case RESONANCE->RESONANCE;default->TEAR;
    };}
}
