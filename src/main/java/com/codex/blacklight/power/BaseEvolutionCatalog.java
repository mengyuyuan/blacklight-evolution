package com.codex.blacklight.power;

import com.codex.blacklight.state.*;
import java.util.*;

/** Version-one finite curriculum. New city content never silently extends it.
 * Repeatable ricochet, organ research and final adaptive passives are separate. */
public final class BaseEvolutionCatalog {
    public static final int VERSION=1;
    public enum Kind { FORM,MOVEMENT,CLAW,COMBAT,GROWTH }
    public record Requirement(Kind kind,String id){
        public boolean owns(BlacklightState state,GrowthProgress growth){return switch(kind){
            case FORM->state.hasUnlocked(PowerForm.byId(id));
            case MOVEMENT->state.hasMovementUpgrade(MovementUpgrade.byId(id));
            case CLAW->state.hasClawUpgrade(ClawUpgrade.byId(id));
            case COMBAT->state.hasCombatSkill(CombatSkill.byId(id));
            case GROWTH->growth.owns(GrowthUpgrade.byId(id));
        };}
    }
    public static final List<Requirement> REQUIREMENTS;
    static {
        var list=new ArrayList<Requirement>();
        add(list,Kind.FORM,"claws hammerfists blade whipfist muscle_mass shield armor thermal_vision infected_vision biomass sovereign_wings");
        add(list,Kind.MOVEMENT,"jump_1 jump_2 jump_3 sprint_1 sprint_2 wall_1 wall_2 glide_1 glide_2 air_dash_plus wing_speed_1 wing_speed_2 wing_speed_3");
        add(list,Kind.CLAW,"range damage dashing_slice pounce");
        add(list,Kind.COMBAT,"critical_mass adrenaline_surge bulletdive_drop groundspike_graveyard tendril_barrage critical_pain critical_mass_boost_1 critical_mass_boost_2 critical_mass_boost_3 shield_borer shield_borer_boost_1 shield_borer_boost_2 block_throw_1 block_throw_2 block_throw_3");
        add(list,Kind.GROWTH,"vitality regeneration assimilation reach_1 reach_2 reach_3 vitality_2 vitality_3 vitality_4 slime_size_1 slime_size_2 slime_size_3 slime_decay_1 slime_decay_2 slime_decay_3 assimilation_2 slime_mobility_1 slime_mobility_2 slime_mobility_3 biomass_metabolism");
        REQUIREMENTS=List.copyOf(list);
    }
    private BaseEvolutionCatalog(){}
    private static void add(List<Requirement> list,Kind kind,String ids){for(String id:ids.split(" "))list.add(new Requirement(kind,id));}
    public static List<Requirement> missing(BlacklightState state,GrowthProgress growth){return REQUIREMENTS.stream().filter(r->!r.owns(state,growth)).toList();}
    public static boolean complete(BlacklightState state,GrowthProgress growth){return state.isInfected()&&missing(state,growth).isEmpty();}
}
