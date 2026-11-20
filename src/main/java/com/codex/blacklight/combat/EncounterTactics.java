package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import java.util.function.Predicate;
import static com.codex.blacklight.combat.EncounterMove.*;

/** Species decisions separate from animation timing. All branches run through begin(),
 * collision, recovery and the synchronized move clock; they cannot deal direct AI damage. */
public final class EncounterTactics {
    public record Situation(double distance,int phase,float health,boolean threatened,int nearbyEnemies,int brood,EncounterMove previous) {}
    private static EncounterMove first(Predicate<EncounterMove> ready,EncounterMove... moves){
        for(var move:moves)if(ready.test(move))return move;return NONE;
    }
    public static EncounterMove choose(EncounterKind kind,Situation s,Predicate<EncounterMove> ready){
        if(!kind.enabled())return NONE;
        double d=s.distance;boolean crowd=s.nearbyEnemies>=2;
        return switch(kind){
            case LEADER_HUNTER->s.brood<2&&d>3&&ready.test(HOWL)?HOWL:d>4?first(ready,POUNCE):first(ready,s.previous==DOUBLE?CLAW:DOUBLE,CLAW,DOUBLE);
            case BRAWLER->d>4?first(ready,POUNCE):crowd?first(ready,SLAM,DOUBLE):first(ready,DOUBLE,SLAM);
            case JUGGERNAUT->s.threatened&&ready.test(GUARD)?GUARD:d>5?first(ready,CHARGE):crowd?first(ready,SLAM,HAMMER):first(ready,HAMMER,SLAM);
            case GOLIATH->s.phase==2?first(ready,SLAM):s.phase==1?first(ready,ERUPTION):d>8?first(ready,CHARGE):d>4||crowd?first(ready,SLAM,HAMMER):first(ready,HAMMER,SLAM);
            case FLYER->d<=16?first(ready,DIVE):NONE;
            // Caller supplies the concurrent source distance/timer gates and
            // suppresses HOWL for the higher-priority mechanical-target branch.
            case ELIZABETH_GREENE->first(ready,HOWL,VOLLEY);
            case ALEX_MERCER->s.threatened&&ready.test(DODGE)?DODGE:s.phase==0?(d>5?first(ready,POUNCE):first(ready,DOUBLE)):
                s.phase==1?(d>6?first(ready,CHARGE):first(ready,SLAM)):(d>5?first(ready,WHIP):crowd?first(ready,ERUPTION,BLADE):first(ready,BLADE,ERUPTION));
            case ROLAND->s.threatened&&ready.test(DODGE)?DODGE:d>5?first(ready,POUNCE):first(ready,BLADE);
            case ARCHER->s.brood<2&&d>5&&ready.test(HOWL)?HOWL:d>7?first(ready,VOLLEY):first(ready,WHIP,VOLLEY);
            case GALLOWAY->d>6?first(ready,CHARGE):crowd||s.phase>0?first(ready,ERUPTION,HAMMER):first(ready,HAMMER,ERUPTION);
            case KOENIG->s.threatened&&ready.test(DODGE)?DODGE:d>5?first(ready,WHIP):first(ready,DOUBLE);
            case CAPTAIN_CROSS->s.threatened&&d<4&&ready.test(DODGE)?DODGE:d>5?first(ready,GRENADE):first(ready,BATON);
            case SUPER_SOLDIER->s.threatened&&ready.test(GUARD)?GUARD:d>4?first(ready,POUNCE):first(ready,HAMMER);
        };
    }
    public static int recharge(EncounterMove move){return switch(move){
        case HOWL->300;case GUARD,DODGE->100;case CHARGE,POUNCE,DIVE->85;
        case ERUPTION->160;case SLAM,ORBS,GRENADE->100;case VOLLEY->75;default->40;
    };}
    public static double preferredDistance(EncounterKind kind){return switch(kind){
        case FLYER->10;case ARCHER->8;case CAPTAIN_CROSS->4;case ELIZABETH_GREENE->12;
        case GOLIATH->3.5;default->2.5;
    };}
    private EncounterTactics(){}
}
