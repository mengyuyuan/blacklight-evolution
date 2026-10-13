package com.codex.blacklight.client.screen;

import com.codex.blacklight.client.SkillBindings;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Tutorials describe existing server actions; opening a page never sends an action. */
public record SkillTutorial(String family,String clip,PowerForm form,String... bindings) {
    public static SkillTutorial forSkill(String id) {
        if(id.startsWith("jump_"))return of("jump","basic_jump_charge",PowerForm.NONE,"key.jump");
        if(id.startsWith("sprint_"))return of("sprint","basic_run",PowerForm.NONE,"key.forward","key.sprint");
        if(id.startsWith("wall_"))return of("wall","basic_wall_run_up",PowerForm.NONE,"key.forward","key.sprint","key.sneak");
        if(id.startsWith("glide_"))return of("glide","basic_glide_forward",PowerForm.NONE,"key.jump");
        if(id.startsWith("critical_mass"))return of("critical","basic_idle",PowerForm.NONE,"key.blacklight.grab","key.use");
        if(id.startsWith("vitality")||id.equals("regeneration"))return of("health","basic_idle",PowerForm.NONE);
        if(id.startsWith("reach_"))return of("reach","blade_cut",PowerForm.BLADE,"key.attack");
        return switch(id) {
            case "mode" -> of("mode","basic_idle",PowerForm.NONE,"key.blacklight.prototype_mode","key.blacklight.upgrades");
            case "grab","assimilation" -> of("consume","consume_normal_actor",PowerForm.NONE,"key.blacklight.grab","key.use","key.attack");
            case "target_lock" -> of("lock","claw_auto1",PowerForm.CLAWS,"key.blacklight.target_lock");
            case "disguise" -> of("disguise","basic_idle",PowerForm.NONE,"key.blacklight.disguise");
            case "air_dash_plus" -> of("dash","basic_air_dash",PowerForm.NONE);
            case "range","groundspike" -> of("groundspike","claw_groundspike",PowerForm.CLAWS,"key.use");
            case "dashing_slice","pounce" -> of("slice","claw_sprint",PowerForm.CLAWS,"key.forward","key.sprint","key.attack");
            case "claws","damage" -> of("claws","claw_auto1",PowerForm.CLAWS,"key.blacklight.cycle_form","key.attack","key.use");
            case "blade" -> of("blade","blade_cut",PowerForm.BLADE,"key.blacklight.cycle_form","key.attack","key.use");
            case "hammerfists" -> of("hammer","hammer_quake",PowerForm.HAMMERFISTS,"key.blacklight.cycle_form","key.attack","key.use");
            case "whipfist" -> of("whip","whip_lash",PowerForm.WHIPFIST,"key.blacklight.cycle_form","key.attack","key.use","key.blacklight.grab");
            case "muscle_mass" -> of("muscle","muscle_jab",PowerForm.MUSCLE_MASS,"key.blacklight.cycle_form","key.attack","key.use");
            case "armor","shield" -> of(id,"basic_run",PowerForm.NONE,"key.blacklight.cycle_form","key.blacklight.defense");
            case "thermal_vision","infected_vision" -> of("vision","basic_idle",PowerForm.NONE,"key.blacklight.cycle_form","key.blacklight.senses");
            case "adrenaline_surge" -> of("adrenaline","basic_idle",PowerForm.NONE);
            case "bulletdive_drop" -> of("dive","bulletdive_drop",PowerForm.NONE,"key.jump","key.use");
            case "groundspike_graveyard" -> of("graveyard","dev_groundspike",PowerForm.CLAWS,"key.attack","key.use");
            case "tendril_barrage" -> of("barrage","dev_tendril",PowerForm.NONE,"key.use","key.blacklight.grab");
            case "critical_pain" -> of("pain","dev_critical_pain",PowerForm.NONE,"key.attack","key.blacklight.grab");
            default -> throw new IllegalArgumentException("Missing tutorial: "+id);
        };
    }
    private static SkillTutorial of(String family,String clip,PowerForm form,String... keys){return new SkillTutorial(family,clip,form,keys);}
    public Component controls(){return Component.translatable("tutorial.blacklight."+family+".controls",labels());}
    public Component steps(){return Component.translatable("tutorial.blacklight."+family+".steps",labels());}
    private Object[] labels(){return java.util.Arrays.stream(bindings).map(SkillBindings::label).toArray();}
    public List<Component> keyLabels(){return java.util.Arrays.stream(bindings).map(key->
            (Component)Component.translatable("tutorial.blacklight.binding",Component.translatable(key),SkillBindings.label(key))).toList();}
}
