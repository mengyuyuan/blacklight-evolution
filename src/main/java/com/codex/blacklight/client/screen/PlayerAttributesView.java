package com.codex.blacklight.client.screen;

import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.movement.MovementTuningSettings;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read the same synchronized state used by gameplay; no editable duplicate stats. */
final class PlayerAttributesView {
    record Entry(Component label,Component value){}
    private PlayerAttributesView(){}
    static List<Entry> entries(){
        var p=Minecraft.getInstance().player;
        if(p==null)return List.of();
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var form=state.getActivePower();
        var values=new ArrayList<Entry>();
        add(values,"health",Component.literal(number(p.getHealth())+" / "+number(p.getMaxHealth())));
        add(values,"absorption",Component.literal(number(p.getAbsorptionAmount())));
        add(values,"form",Component.translatable("power.blacklight."+form.id()));
        if(state.hasUnlocked(PowerForm.SHIELD))add(values,"shield",Component.literal(number(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE).shield())+" / "+number(UtilityState.SHIELD_CAPACITY)));
        if(ProjectileDefenseSystem.unlocked(p)){
            long reflection=ProjectileDefenseSystem.level(p);
            add(values,"ricochet_level",Component.literal(Long.toString(reflection)));
            add(values,"ricochet_power",Component.literal(number(RicochetRules.capacity(reflection,true))+" / "+number(RicochetRules.capacity(reflection,false))));
            add(values,"ricochet_damage",Component.literal("×"+number(RicochetRules.multiplier(reflection))));
        }
        var move=WeaponMove.choose(form,WeaponMove.Input.ATTACK,false,false,false);
        add(values,"damage",move==null?Component.literal("—"):Component.literal(number(move.damageFor(form)*CombatScale.UNITS)));
        add(values,"attack_speed",Component.translatable("screen.blacklight.stats.per_second",number(p.getAttributeValue(Attributes.ATTACK_SPEED))));
        add(values,"movement",Component.literal(number(p.getAttributeValue(Attributes.MOVEMENT_SPEED))));
        add(values,"current_speed",Component.translatable("screen.blacklight.stats.blocks_second",number(p.getDeltaMovement().horizontalDistance()*20)));
        double cap=MovementTuningSettings.sprintMaxSpeed()*MovementUpgrade.sprintMultiplier(state.getMovementUpgradeMask())*InfiniteEvolutionSystem.mobility(p);
        if(UtilityPowerSystem.active(p,PowerForm.ARMOR))cap*=.75;
        add(values,"sprint",Component.translatable("screen.blacklight.stats.blocks_second",number(cap*20)));
        add(values,"jump",Component.translatable("screen.blacklight.stats.jump_range",
                number(MovementTuningSettings.estimatedJumpHeight(0,state.getMovementUpgradeMask())),
                number(MovementTuningSettings.estimatedJumpHeight(1,state.getMovementUpgradeMask()))));
        add(values,"notes",Component.translatable("screen.blacklight.stats.notes_value"));
        return values;
    }
    private static void add(List<Entry> values,String key,Component value){values.add(new Entry(Component.translatable("screen.blacklight.stats."+key),value));}
    private static String number(double n){
        return n==Math.rint(n)?String.format(Locale.ROOT,"%,.0f",n):String.format(Locale.ROOT,"%,.2f",n);
    }
}
