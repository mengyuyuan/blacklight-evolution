package com.codex.blacklight.power;

import com.codex.blacklight.combat.ClawCombatState;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Selection is server-authoritative; unavailable and busy forms cannot be selected by packets. */
public final class PowerFormSystem {
    private static final PowerForm[] FORMS = {PowerForm.NONE, PowerForm.CLAWS, PowerForm.HAMMERFISTS, PowerForm.BLADE, PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS};
    private PowerFormSystem() {}
    public static boolean supported(PowerForm form) {
        if(form==PowerForm.BIOMASS||UtilityPowerSystem.supported(form))return true;
        for (var candidate : FORMS) if (form == candidate) return true;
        return false;
    }
    public static boolean select(ServerPlayer player, PowerForm form, boolean purchase) {
        if(BiomassSystem.active(player))return false;
        if(form==PowerForm.BIOMASS)return BiomassSystem.begin(player,false);
        var state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        var combat = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CLAW_COMBAT);
        if (!state.isInfected() || !state.isPrototypeMode() || com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CONSUMING)
                || !supported(form)) return false;
        // Stowing a weapon must take effect even during its action. Previously the
        // wheel closed while this request silently failed, leaving claws active.
        if (form == PowerForm.NONE) {
            com.codex.blacklight.combat.WeaponCombatSystem.cancel(player);
            com.codex.blacklight.combat.WeaponCombatSystem.cancelHeldInput(player);
            combat.reset();
        } else if (combat.action() != ClawCombatState.Action.IDLE || !combat.canToggleTransform(player.tickCount)) {
            player.displayClientMessage(Component.literal("当前动作尚未结束，能力未切换；选择无能力可收起武器。"), true);
            return false;
        }
        if (form != PowerForm.NONE && !state.hasUnlocked(form) && !(purchase && state.buy(form))) {
            player.displayClientMessage(Component.translatable("message.blacklight.form_locked", Component.translatable("power.blacklight." + form.id()), form.epCost()), true);
            return false;
        }
        boolean visible=UtilityPowerSystem.supported(form)
                ?(form==PowerForm.SHIELD||form==PowerForm.ARMOR)&&!UtilityPowerSystem.active(player,form)
                :form!=PowerForm.NONE;
        if(visible)com.codex.blacklight.disguise.DisguiseSystem.visiblePower(player);
        if(UtilityPowerSystem.supported(form))UtilityPowerSystem.toggle(player,form);
        else if (!state.activate(form)) return false;
        combat.markTransformToggled(player.tickCount);
        com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE, state); com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);
        String message = UtilityPowerSystem.supported(form) && !UtilityPowerSystem.active(player, form)
                ? "message.blacklight.form_disabled" : "message.blacklight.form_selected";
        player.displayClientMessage(Component.translatable(message, Component.translatable("power.blacklight." + form.id())), true);
        return true;
    }
    public static void cycle(ServerPlayer player) {
        var state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE); int index = 0;
        for (int i = 0; i < FORMS.length; i++) if (FORMS[i] == state.getActivePower()) index = i;
        for (int i = 1; i <= FORMS.length; i++) {
            var next = FORMS[(index + i) % FORMS.length];
            if (next == PowerForm.NONE || state.hasUnlocked(next)) { select(player, next, false); return; }
        }
    }
}
