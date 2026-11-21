package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import java.util.Collections;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.Map.entry;

/** Audio score for the paired execution. Beat indices match the authoritative damage events. */
public final class ConsumeImpactKind {
    private record Score(String family,List<String> hits) {
        Score(String family,String... hits) { this(family,List.of(hits)); }
    }
    private static final Map<String,Score> SCORES=Map.ofEntries(
            entry("normal",new Score("bare","chest_drive")),
            entry("normal_head_stomp",new Score("bare","heel_stamp")),
            entry("normal_knee_head",new Score("bare","knee_bump")),
            entry("normal_beatdown",new Score("bare","beat_jab","beat_hook","beat_cross","beat_drive","beat_crush")),
            entry("stealth_neck",new Score("quiet","neck_snap")),
            entry("stealth_bearhug",new Score("quiet","rib_squeeze")),
            entry("stealth_nelson",new Score("quiet","joint_lock")),
            entry("stealth_guillotine",new Score("quiet","choke_press")),
            entry("stealth_jugular",new Score("quiet","throat_rip")),
            entry("claws",new Score("claw","claw_shear")),
            entry("claws_head",new Score("claw","skull_click")),
            entry("blade",new Score("blade","blade_split")),
            entry("hammer",new Score("hammer","head_drop")),
            entry("hammer_smush",new Score("hammer","hammer_press")),
            entry("whip",new Score("lash","hook_rend")),
            entry("whip_lash",new Score("lash","lash_bite","lash_recoil")),
            entry("muscle",new Score("muscle","arm_wrench")),
            entry("muscle_heart",new Score("muscle","heart_grab","heart_slap")),
            entry("large",new Score("mount","mount_fist")),
            entry("large_claws",new Score("mount","mount_claw")),
            entry("large_blade",new Score("mount","mount_blade")),
            entry("large_hammer",new Score("mount","mount_maul")),
            entry("large_whip",new Score("mount","mount_cable")),
            entry("large_muscle",new Score("mount","mount_heave")),
            entry("hunter",new Score("hunter","hunter_jaw","hunter_rend")));

    private ConsumeImpactKind() {}

    /** Includes the shoulder organ's independent lifecycle and the moment the body weave actually appears. */
    public static Set<String> registeredCues() {
        var cues=new TreeSet<>(List.of("quill_extract","quill_return","quill_merge","weave"));
        for(var score:SCORES.values()) {
            cues.addAll(score.hits);
            for(String phase:List.of("grip","absorb","finish"))cues.add(phase+"_"+score.family);
        }
        return Collections.unmodifiableSet(cues);
    }

    private static Score score(PowerForm form,boolean large,int stage,int variant) {
        if(stage==ConsumeVisual.HUNTER_CONSUME)return SCORES.get("hunter");
        if(large && form==PowerForm.BLADE)return SCORES.get("large_blade");
        return SCORES.get(ConsumeSequence.clipKey(form,large,variant));
    }

    public static String forPhase(PowerForm form,boolean large,int stage,int variant,String phase) {
        if(!phase.equals("grip")&&!phase.equals("absorb")&&!phase.equals("finish"))
            throw new IllegalArgumentException("Unknown consume audio phase "+phase);
        return phase+"_"+score(form,large,stage,variant).family;
    }

    public static String forContact(PowerForm form,boolean large,int stage,int variant,int ordinal,int count) {
        var hits=score(form,large,stage,variant).hits;
        return hits.get(Math.max(0,Math.min(ordinal,hits.size()-1)));
    }

    public static String forContact(ConsumeVisual visual,int ordinal) {
        var forms=PowerForm.values();
        var form=visual.form()>=0 && visual.form()<forms.length?forms[visual.form()]:PowerForm.NONE;
        return forContact(form,visual.stage()==ConsumeVisual.MOUNT_CONSUME,visual.stage(),visual.variant(),ordinal,0);
    }
}
