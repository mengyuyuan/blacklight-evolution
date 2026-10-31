package com.codex.blacklight.infection;

/** Telegraph, contact, withdrawal and recovery are shared by client and server. */
public enum EvolvedSpecialMove {
    SLAM("evolved_slam",EvolvedArchetype.STRENGTH,64,29,35,155,4.5),
    BASH("evolved_bash",EvolvedArchetype.ARMORED,44,17,23,120,2.8),
    POUNCE("evolved_pounce",EvolvedArchetype.PREDATOR,46,19,26,105,7),
    LASH("evolved_lash",EvolvedArchetype.TENDRIL,58,23,36,135,10),
    DASH("evolved_dash",EvolvedArchetype.SKIRMISHER,42,16,24,95,9),
    VOLLEY("evolved_volley",EvolvedArchetype.SENSORY,56,21,35,125,22),
    MEND("evolved_mend",EvolvedArchetype.REGENERATOR,66,25,42,200,10);

    public final String key;public final EvolvedArchetype type;
    public final int duration,contact,retract,cooldown;public final double range;
    EvolvedSpecialMove(String key,EvolvedArchetype type,int duration,int contact,int retract,int cooldown,double range){this.key=key;this.type=type;this.duration=duration;this.contact=contact;this.retract=retract;this.cooldown=cooldown;this.range=range;}
    public float extension(float ticks){return EvolvedForm.smooth((ticks-contact+10)/10)*(1-EvolvedForm.smooth((ticks-retract)/(duration-retract)));}
    public static EvolvedSpecialMove resolve(String key){for(var move:values())if(move.key.equals(key))return move;return null;}
    public static EvolvedSpecialMove of(EvolvedArchetype type){for(var move:values())if(move.type==type)return move;return null;}
}
