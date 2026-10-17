package com.codex.blacklight.state;

import com.codex.blacklight.power.ClawUpgrade;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.power.PowerForm;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class BlacklightState {
    public static final int MAX_BIOMASS = 1000;
    public static final Codec<BlacklightState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("infected", false).forGetter(BlacklightState::isInfected),
            Codec.INT.optionalFieldOf("biomass", 0).forGetter(BlacklightState::getBiomass),
            Codec.INT.optionalFieldOf("evolution_points", 0).forGetter(BlacklightState::getEvolutionPoints),
            Codec.INT.optionalFieldOf("unlocked", 0).forGetter(BlacklightState::getUnlockedMask),
            Codec.STRING.optionalFieldOf("active_power", "none").forGetter(state -> state.getActivePower().id()),
            Codec.STRING.optionalFieldOf("disguise", "").forGetter(BlacklightState::getDisguiseIdentity),
            Codec.BOOL.optionalFieldOf("disguised", false).forGetter(BlacklightState::isDisguised),
            Codec.INT.optionalFieldOf("alert", 0).forGetter(BlacklightState::getAlert),
            Codec.INT.optionalFieldOf("claw_upgrades", 0).forGetter(BlacklightState::getClawUpgradeMask),
            Codec.INT.optionalFieldOf("movement_upgrades", 0).forGetter(BlacklightState::getMovementUpgradeMask),
            Codec.BOOL.optionalFieldOf("prototype_mode", false).forGetter(BlacklightState::isPrototypeMode),
            Codec.INT.optionalFieldOf("combat_skill_mask", 0).forGetter(BlacklightState::getCombatSkillMask)
    ).apply(instance, BlacklightState::new));

    private boolean infected;
    private int biomass;
    private int evolutionPoints;
    private int unlockedMask;
    private PowerForm activePower;
    private String disguiseIdentity;
    private boolean disguised;
    private int alert;
    private int clawUpgradeMask;
    private int movementUpgradeMask;
    private boolean prototypeMode;
    private int combatSkillMask;

    public BlacklightState() {
        this(false, 0, 0, 0, "none", "", false, 0, 0, 0, false);
    }

    BlacklightState(boolean infected, int biomass, int evolutionPoints, int unlockedMask,
                    String activePower, String disguiseIdentity, boolean disguised, int alert,
                    int clawUpgradeMask, int movementUpgradeMask, boolean prototypeMode) {
        this(infected, biomass, evolutionPoints, unlockedMask, activePower, disguiseIdentity,
                disguised, alert, clawUpgradeMask, movementUpgradeMask, prototypeMode, 0);
    }

    BlacklightState(boolean infected, int biomass, int evolutionPoints, int unlockedMask,
                    String activePower, String disguiseIdentity, boolean disguised, int alert,
                    int clawUpgradeMask, int movementUpgradeMask, boolean prototypeMode, int combatSkillMask) {
        this.infected = infected;
        this.biomass = clamp(biomass, 0, MAX_BIOMASS);
        this.evolutionPoints = Math.max(0, evolutionPoints);
        this.unlockedMask = Math.max(0, unlockedMask);
        this.prototypeMode = infected && prototypeMode;
        this.activePower = this.prototypeMode ? PowerForm.byId(activePower) : PowerForm.NONE;
        this.disguiseIdentity = disguiseIdentity == null ? "" : disguiseIdentity;
        this.disguised = this.prototypeMode && disguised;
        this.alert = clamp(alert, 0, 100);
        this.clawUpgradeMask = Math.max(0, clawUpgradeMask);
        this.movementUpgradeMask = Math.max(0, movementUpgradeMask);
        this.combatSkillMask = Math.max(0, combatSkillMask) & CombatSkill.validMask();
    }

    public void infect() {
        infected = true;
        biomass = Math.max(biomass, 250);
        unlock(PowerForm.CLAWS);
        prototypeMode = false;
        activePower = PowerForm.NONE;
        disguised = false;
    }

    public void cure() {
        infected = false;
        biomass = 0;
        evolutionPoints = 0;
        unlockedMask = 0;
        activePower = PowerForm.NONE;
        disguiseIdentity = "";
        disguised = false;
        alert = 0;
        clawUpgradeMask = 0;
        movementUpgradeMask = 0;
        combatSkillMask = 0;
        prototypeMode = false;
    }

    public boolean unlock(PowerForm form) {
        if (form == PowerForm.NONE || hasUnlocked(form)) return false;
        unlockedMask |= form.bit();
        return true;
    }

    public boolean buy(PowerForm form) {
        if (!infected || form == PowerForm.NONE || hasUnlocked(form) || evolutionPoints < form.epCost()) return false;
        evolutionPoints -= form.epCost();
        return unlock(form);
    }

    public boolean activate(PowerForm form) {
        if (form != PowerForm.NONE && (!prototypeMode || !hasUnlocked(form))) return false;
        activePower = form;
        return true;
    }

    public boolean spendBiomass(int amount) {
        // Death conversion reserve only. Upgrades spend EP; learned actions do not spend biomass.
        if (amount < 0 || biomass < amount) return false;
        biomass -= amount;
        return true;
    }

    public void addBiomass(int amount) {
        biomass = (int)com.codex.blacklight.util.MathCompat.clamp((long)biomass + amount, 0, MAX_BIOMASS);
    }

    public void addEvolutionPoints(int amount) {
        evolutionPoints = (int)com.codex.blacklight.util.MathCompat.clamp((long)evolutionPoints + amount, 0, Integer.MAX_VALUE);
    }

    public boolean buyClawUpgrade(ClawUpgrade upgrade) {
        if (!infected || upgrade == null || !hasUnlocked(PowerForm.CLAWS)
                || hasClawUpgrade(upgrade) || evolutionPoints < upgrade.epCost()) return false;
        evolutionPoints -= upgrade.epCost();
        clawUpgradeMask |= upgrade.bit();
        return true;
    }

    public boolean hasClawUpgrade(ClawUpgrade upgrade) {
        return upgrade != null && (clawUpgradeMask & upgrade.bit()) != 0;
    }

    /** Movement tiers are sequential: a tier only sells after its predecessor. */
    public boolean buyMovementUpgrade(MovementUpgrade upgrade) {
        if (!infected || upgrade == null || hasMovementUpgrade(upgrade)
                || evolutionPoints < upgrade.epCost()) return false;
        if (upgrade.wingUpgrade() && !hasUnlocked(PowerForm.SOVEREIGN_WINGS)) return false;
        MovementUpgrade requires = upgrade.requires();
        if (requires != null && !hasMovementUpgrade(requires)) return false;
        evolutionPoints -= upgrade.epCost();
        movementUpgradeMask |= upgrade.bit();
        return true;
    }

    public boolean hasMovementUpgrade(MovementUpgrade upgrade) {
        return upgrade != null && (movementUpgradeMask & upgrade.bit()) != 0;
    }

    public boolean buyCombatSkill(CombatSkill skill) {
        if (!infected || skill == null || hasCombatSkill(skill) || evolutionPoints < skill.epCost()
                || skill.requires() != null && !hasCombatSkill(skill.requires())
                || skill.movementRequires() != null && !hasMovementUpgrade(skill.movementRequires())
                || skill.powerRequires() != null && !hasUnlocked(skill.powerRequires())) return false;
        evolutionPoints -= skill.epCost();
        combatSkillMask |= skill.bit();
        return true;
    }

    public boolean hasCombatSkill(CombatSkill skill) {
        return skill != null && (combatSkillMask & skill.bit()) != 0;
    }

    public void captureIdentity(String identity) {
        disguiseIdentity = identity == null ? "" : identity;
    }

    public boolean toggleDisguise() {
        if (!infected || !prototypeMode || disguiseIdentity.isBlank()) return false;
        disguised = !disguised;
        return true;
    }

    public void setDisguised(boolean enabled) {
        disguised=enabled && infected && prototypeMode && !disguiseIdentity.isBlank();
    }

    public void raiseAlert(int amount) {
        alert = (int)com.codex.blacklight.util.MathCompat.clamp((long)alert + amount, 0, 100);
    }

    public boolean hasUnlocked(PowerForm form) {
        return form == PowerForm.BIOMASS ? infected : form != PowerForm.NONE && (unlockedMask & form.bit()) != 0;
    }

    public boolean setPrototypeMode(boolean enabled) {
        if (enabled && !infected) return false;
        prototypeMode = enabled;
        activePower = enabled && hasUnlocked(PowerForm.CLAWS) ? PowerForm.CLAWS : PowerForm.NONE;
        if (!enabled) disguised = false;
        return true;
    }

    public boolean isInfected() { return infected; }
    public int getBiomass() { return biomass; }
    public int getEvolutionPoints() { return evolutionPoints; }
    public int getUnlockedMask() { return unlockedMask; }
    public PowerForm getActivePower() { return activePower; }
    public String getDisguiseIdentity() { return disguiseIdentity; }
    public boolean isDisguised() { return disguised; }
    public int getAlert() { return alert; }
    public int getClawUpgradeMask() { return clawUpgradeMask; }
    public int getMovementUpgradeMask() { return movementUpgradeMask; }
    public boolean isPrototypeMode() { return prototypeMode; }
    public int getCombatSkillMask() { return combatSkillMask; }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
