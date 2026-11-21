package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;

/** Material response used by listeners without a posed receiver surface. */
public final class ConsumeImpactKind {
    private ConsumeImpactKind() {}

    public static String forContact(PowerForm form,boolean large,int stage,int variant,int ordinal,int count) {
        if(stage==ConsumeVisual.HUNTER_CONSUME)return "tear";
        if(large)return switch(form) {
            case BLADE,CLAWS -> "slash";
            case HAMMERFISTS -> "crush";
            case WHIPFIST,MUSCLE_MASS -> "tear";
            default -> "dent";
        };
        return switch(ConsumeSequence.key(form,variant)) {
            case "normal" -> "pierce";
            case "blade","claws" -> "cleave";
            case "claws_head","hammer","hammer_smush","normal_head_stomp" -> "crush";
            case "normal_beatdown" -> ordinal==count-1?"crush":"dent";
            case "muscle_heart" -> ordinal==0?"pierce":"dent";
            case "whip","whip_lash","muscle","stealth_jugular" -> "tear";
            default -> "dent";
        };
    }
}
