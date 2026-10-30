package com.codex.blacklight.military;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Command duty, independent from the saved rank and a unit's actual appointment. */
public enum MilitaryRole {
    TROOPER(MilitaryRank.PRIVATE), TEAM_LEADER(MilitaryRank.SERGEANT),
    OFFICER(MilitaryRank.LIEUTENANT), COMMANDER(MilitaryRank.MAJOR);
    private final MilitaryRank defaultRank;
    MilitaryRole(MilitaryRank rank){this.defaultRank=rank;}
    public String id(){return name().toLowerCase(Locale.ROOT);}
    public MilitaryRank defaultRank(){return defaultRank;}
    public static Optional<MilitaryRole> parse(String id){return Arrays.stream(values()).filter(role->role.id().equals(id)).findFirst();}
    /** Missing pre-role data inherits the higher of saved rank duty and its registered entity duty. */
    public static MilitaryRole migrate(MilitaryRank rank,MilitaryRole entityDefault){
        MilitaryRole inferred=rank.ordinal()>=MilitaryRank.MAJOR.ordinal()?COMMANDER:
                rank.ordinal()>=MilitaryRank.LIEUTENANT.ordinal()?OFFICER:
                rank.ordinal()>=MilitaryRank.SERGEANT.ordinal()?TEAM_LEADER:TROOPER;
        return inferred.ordinal()>entityDefault.ordinal()?inferred:entityDefault;
    }
}
