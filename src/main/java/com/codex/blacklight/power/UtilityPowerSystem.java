package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModWeaponSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** One defence and one sense coexist with the selected offensive power. */
public final class UtilityPowerSystem {
    public static final int DEFENSE = PowerForm.SHIELD.bit() | PowerForm.ARMOR.bit();
    public static boolean supported(PowerForm f) { return f == PowerForm.SHIELD || f == PowerForm.ARMOR || f == PowerForm.THERMAL_VISION || f == PowerForm.INFECTED_VISION; }
    public static boolean active(Player p, PowerForm form) {
        var state = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        return p.isAlive() && state.isInfected() && state.isPrototypeMode() && (com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_POWERS) & form.bit()) != 0;
    }
    public static boolean shieldReady(Player p) { return active(p, PowerForm.SHIELD) && !com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE).broken(); }
    public static float visualWeight(Player p, PowerForm form, float partial) {
        if (!p.isAlive() || !com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).isInfected() || !com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).isPrototypeMode()) return 0;
        var s = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE);
        float age = p.level().getGameTime() - s.changedAt() + partial;
        // Native shield scale speed is 7/s deploying and 3/s retracting.
        // Armor body-pose fade is independent from the 121-frame native plate timeline.
        float duration = form == PowerForm.ARMOR ? (active(p, form) ? 2F : 9F) : 20F / (active(p, form) ? 7F : 3F);
        float blend = com.codex.blacklight.util.MathCompat.clamp(age / duration, 0, 1);
        float weight = active(p, form) ? blend : (s.previousDefense() & form.bit()) != 0 ? 1 - blend : 0;
        if (form == PowerForm.SHIELD && s.broken()) weight *= 1 - com.codex.blacklight.util.MathCompat.clamp((p.level().getGameTime() - s.hitAt() + partial) / 5F, 0, 1);
        return weight;
    }
    public static Vec3 shieldForward(Player p){
        Vec3 velocity=p.getDeltaMovement().multiply(1,0,1);
        var weapon=ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL);
        float facing=weapon.spec()!=null?weapon.yaw():p.onGround()&&velocity.lengthSqr()>.0064
                ?(float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z)):p.yBodyRot;
        return Vec3.directionFromRotation(0,facing);
    }
    public static boolean shieldFaces(Player p,Vec3 toward){
        Vec3 flat=toward.multiply(1,0,1);return flat.lengthSqr()>1e-6&&shieldForward(p).dot(flat.normalize())>=.35;
    }
    public static void spendShield(ServerPlayer p,float amount){
        var s=ModAttachments.getData(p,ModAttachments.UTILITY_STATE);long now=p.level().getGameTime();float hp=Math.max(0,s.shield()-amount);
        publish(p,new UtilityState(hp,hp<=0?now+UtilityState.REBUILD_TICKS:0,s.changedAt(),s.previousDefense(),now));
        if(hp<=0)ModWeaponSounds.defense(p,PowerForm.SHIELD,"break",p.position(),1);
    }
    public static boolean parkourAllowed(Player p) { return !active(p, PowerForm.ARMOR); }
    public static void toggle(ServerPlayer p, PowerForm form) {
        if (!supported(form)) return;
        int old = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_POWERS);
        int group = (form.bit() & DEFENSE) != 0 ? DEFENSE : PowerForm.THERMAL_VISION.bit() | PowerForm.INFECTED_VISION.bit();
        int mask = (old & form.bit()) != 0 ? old & ~form.bit() : (old & ~group) | form.bit();
        if (group == DEFENSE) {
            var s = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE);
            publish(p, new UtilityState(s.shield(), s.recoverAt(), p.level().getGameTime(), old & DEFENSE, s.hitAt()));
        }
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS, mask); com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS); attributes(p);
        if(group==DEFENSE)ModWeaponSounds.defense(p,form,(mask & form.bit()) != 0 ? "deploy" : "retract",p.position(),1);
        else p.level().playSound(null, p.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, .5F, (mask & form.bit()) != 0 ? .8F : 1.3F);
    }
    private static void publish(ServerPlayer p, UtilityState s) { com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_STATE, s); com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_STATE); }
    private static void modifier(ServerPlayer p, Attribute type, String name, double value, AttributeModifier.Operation operation, boolean enabled) {
        var attribute = p.getAttribute(type); if (attribute == null) return;
        var id = CombatScale.modifierId(new ResourceLocation("blacklight", name));
        if (enabled && attribute.getModifier(id)==null) attribute.addTransientModifier(new AttributeModifier(id, "blacklight:"+name, value, operation));
        else if (!enabled) attribute.removeModifier(id);
    }
    private static void attributes(ServerPlayer p) {
        boolean armor = active(p, PowerForm.ARMOR);
        // Remove RC28 plate bonuses: the new explicit resistance must not stack with them.
        modifier(p, Attributes.ARMOR, "armor_plate", 0, AttributeModifier.Operation.ADDITION, false);
        modifier(p, Attributes.ARMOR_TOUGHNESS, "armor_toughness", 0, AttributeModifier.Operation.ADDITION, false);
        modifier(p, Attributes.MOVEMENT_SPEED, "armor_weight", -.25, AttributeModifier.Operation.MULTIPLY_TOTAL, armor);
        modifier(p, Attributes.KNOCKBACK_RESISTANCE, "armor_stability", .6, AttributeModifier.Operation.ADDITION, armor);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent e) {
        if (!com.codex.blacklight.registry.ModAttachments.available(e.player)) { return; }
        if(e.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        if (!(e.player instanceof ServerPlayer p)) return;
        var state = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        if (!p.isAlive() || !state.isInfected() || !state.isPrototypeMode()) {
            if (com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_POWERS) != 0) { com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS, 0); com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS); }
            attributes(p);
            return;
        }
        var s = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE);
        if (s.broken() && p.level().getGameTime() >= s.recoverAt()) {
            publish(p, new UtilityState(UtilityState.SHIELD_CAPACITY, 0, active(p, PowerForm.SHIELD) ? p.level().getGameTime() : s.changedAt(), active(p, PowerForm.SHIELD) ? 0 : s.previousDefense(), s.hitAt()));
            if (active(p, PowerForm.SHIELD)) ModWeaponSounds.defense(p,PowerForm.SHIELD,"deploy",p.position(),1);
        }
        // Native states 0/1 heal only while retracting/retracted. Deployed state 3 does not.
        else if (!s.broken() && !active(p, PowerForm.SHIELD) && s.shield() < UtilityState.SHIELD_CAPACITY
                && p.level().getGameTime() - s.hitAt() >= UtilityState.HEAL_DELAY_TICKS) {
            publish(p, new UtilityState(s.shield() + UtilityState.HEAL_PER_TICK, 0, s.changedAt(), s.previousDefense(), s.hitAt()));
        }
        if (p.tickCount % 5 == 0) attributes(p);
    }
    @SubscribeEvent public static void damage(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() <= 0 || e.isCanceled()) return;
        var source = e.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;
        if (active(p, PowerForm.ARMOR)) {
            if(source.getDirectEntity()!=null){
                float force=Math.min(1,e.getAmount()*CombatScale.units(p)/20F);
                ModWeaponSounds.defense(p,PowerForm.ARMOR,force>=.5F?"heavy":"hit",p.position(),force);
            }
            e.setAmount(e.getAmount() * .525F); return;
        }
        if (!shieldReady(p) || com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)) return;
        Vec3 pos = source.getSourcePosition(); if (pos == null) return;
        Vec3 toward = pos.subtract(p.position()).multiply(1, 0, 1);
        if (toward.lengthSqr() < 1.0e-6) return;
        if (!shieldFaces(p,toward)) return;
        var s = com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE);
        float units=CombatScale.units(p);
        float incoming=e.getAmount()*units;
        float hp = Math.max(0, s.shield() - incoming);
        long now = p.level().getGameTime();
        // Native ShieldPower subtracts absorbed health from the incoming amount; overflow passes through.
        publish(p, new UtilityState(hp, hp <= 0 ? now + UtilityState.REBUILD_TICKS : 0, s.changedAt(), s.previousDefense(), now));
        // Shield durability is in displayed health points. Return overflow to the
        // armor pipeline's original units; CombatScale converts once after armor.
        float remaining = Math.max(0, incoming - s.shield())/units;
        if (remaining == 0) e.setCanceled(true); else e.setAmount(remaining);
        ModWeaponSounds.defense(p,PowerForm.SHIELD,hp<=0?"break":incoming>=10?"heavy":"hit",p.position(),Math.min(1,incoming/20));
        // Surface debris is emitted once by each client from the posed shield,
        // instead of spawning unrelated critical-hit particles inside the torso.
    }
}
