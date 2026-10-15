package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

/** Server-owned consume overflow and last-chance permission, separate from ordinary skills.
 * Capacity, the five-second release opportunity and rearming at half health are
 * Minecraft adaptations; consume gain and 1.8-second damage grace use verified fields.
 */
@EventBusSubscriber(modid="blacklight")
public final class CriticalMassSystem {
    private static final ThreadLocal<HealProbe> CONSUME_HEAL = new ThreadLocal<>();
    private static final class HealProbe {
        final ServerPlayer player;
        LivingHealEvent event;
        HealProbe(ServerPlayer player) { this.player = player; }
    }
    private CriticalMassSystem() { }
    private static boolean enabled(Player p) {
        var state = p.getData(ModAttachments.PLAYER_STATE);
        return !BiomassSystem.active(p) && p.isAlive() && !p.isSpectator() && state.isInfected() && state.isPrototypeMode();
    }
    public static boolean ready(ServerPlayer p) {
        if (!enabled(p) || !p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.CRITICAL_MASS)) return false;
        var mass=p.getData(ModAttachments.CRITICAL_MASS);
        long now=p.level().getGameTime();
        return mass.ready() || p.getData(ModAttachments.ADRENALINE).ready(now) && mass.adrenalineReleaseAllowed(now);
    }
    /** The caller checks its own skill and action conditions before committing one release. */
    public static boolean spend(ServerPlayer p) {
        if (!ready(p)) return false;
        var adrenaline = p.getData(ModAttachments.ADRENALINE);
        long now=p.level().getGameTime();
        var mass=p.getData(ModAttachments.CRITICAL_MASS);
        if (adrenaline.ready(now) && mass.adrenalineReleaseAllowed(now)) {
            publish(p, adrenaline.spend());
            publish(p, mass.adrenalineSpent(now));
        }
        else {
            var spent=mass.spend(p.getMaxHealth());
            // This is a resource subtraction, not an incoming weapon hit: armor,
            // golden hearts and invulnerability must not erase the native cost.
            publish(p,spent.state());
            if(spent.healthLoss()>0) {
                p.setHealth(Math.max(0,p.getHealth()-spent.healthLoss()));
                // ServerPlayer.die dispatches the real NeoForge death event. A
                // lethal resource cost follows normal death/our Adrenaline policy
                // instead of leaving a zero-health player without a death event.
                if(p.isDeadOrDying())p.die(p.damageSources().generic());
            }
        }
        return true;
    }
    /** Amount is in the old healing units; CombatScale converts LivingHealEvent exactly once. */
    public static void healFromConsume(ServerPlayer p, float amount) {
        if (!p.isAlive() || !Float.isFinite(amount) || amount <= 0) return;
        float before = p.getHealth(), maximum = p.getMaxHealth();
        var previous = CONSUME_HEAL.get();
        var probe = new HealProbe(p);
        CONSUME_HEAL.set(probe);
        try { p.heal(amount); }
        finally {
            if (previous == null) CONSUME_HEAL.remove(); else CONSUME_HEAL.set(previous);
        }
        // Read the event after every listener has run, including CombatScale and
        // other mods. Canceled/reduced healing must not mint hidden reserve.
        float offered = probe.event == null || probe.event.isCanceled() ? 0 : probe.event.getAmount();
        // Only healing delivered by this successful consume can add reserve. Passive
        // regeneration, commands and health upgrades never enter this path.
        if (enabled(p) && p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.CRITICAL_MASS)
                && p.getHealth() >= maximum - .001F) {
            float used = Math.max(0, p.getHealth() - before);
            int capacity=CombatSkill.criticalMassCapacity(p.getData(ModAttachments.PLAYER_STATE).getCombatSkillMask());
            publish(p, p.getData(ModAttachments.CRITICAL_MASS).addOverheal(Math.max(0, offered - used), maximum, capacity));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public static void observeConsumeHeal(LivingHealEvent event) {
        var probe = CONSUME_HEAL.get();
        if (probe != null && event.getEntity() == probe.player) probe.event = event;
    }
    private static void publish(ServerPlayer p, CriticalMassState state) {
        if (state.equals(p.getData(ModAttachments.CRITICAL_MASS))) return;
        p.setData(ModAttachments.CRITICAL_MASS, state); p.syncData(ModAttachments.CRITICAL_MASS);
    }
    private static void publish(ServerPlayer p, AdrenalineState state) {
        if (state.equals(p.getData(ModAttachments.ADRENALINE))) return;
        p.setData(ModAttachments.ADRENALINE, state); p.syncData(ModAttachments.ADRENALINE);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void reserveProtection(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !enabled(p)
                || !p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.CRITICAL_MASS)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // NeoForge invokes this modifier after ALL LivingDamageEvent.Pre listeners,
        // including CombatScale and later cancellation/zero-damage decisions. The
        // reserve is not spent until that finalized damage actually reaches absorption.
        event.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, vanillaAbsorption) -> {
            float damage=container.getNewDamage();
            if (!Float.isFinite(damage) || damage <= 0 || !enabled(p)
                    || !p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.CRITICAL_MASS)) return vanillaAbsorption;
            // Match Forge: golden hearts protect first; reserve covers only the remainder.
            float golden=Math.clamp(vanillaAbsorption,0,damage);
            float afterGolden=damage-golden;
            if(afterGolden<=0)return golden;
            var result=p.getData(ModAttachments.CRITICAL_MASS).absorb(afterGolden, p.getMaxHealth());
            if (result.absorbed() <= 0) return vanillaAbsorption;
            publish(p,result.state());
            // setReduction subtracts the returned gold value after this callback.
            container.setNewDamage(golden+result.remaining());
            return golden;
        });
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void grace(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && enabled(p)
                && p.getData(ModAttachments.ADRENALINE).undamageable(p.level().getGameTime())
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var state = p.getData(ModAttachments.PLAYER_STATE);
        var mass = p.getData(ModAttachments.CRITICAL_MASS);
        // A death callback has health zero, so use mode/skill flags directly here.
        if (BiomassSystem.active(p) || !state.isInfected() || !state.isPrototypeMode() || !state.hasCombatSkill(CombatSkill.ADRENALINE_SURGE)
                || !mass.adrenalineArmed()) return;
        event.setCanceled(true);
        p.setHealth(Math.max(1, p.getMaxHealth() * .01F));
        publish(p, new CriticalMassState(0, false, mass.lastAdrenalineDevastatorAt()));
        long now = p.level().getGameTime();
        publish(p, new AdrenalineState(now + AdrenalineState.DURATION_TICKS,
                now + AdrenalineState.UNDAMAGEABLE_TICKS, true));
        p.displayClientMessage(Component.translatable("message.blacklight.adrenaline_started"), true);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var state = p.getData(ModAttachments.PLAYER_STATE);
        var adrenaline = p.getData(ModAttachments.ADRENALINE);
        if (!state.isInfected()) { publish(p, CriticalMassState.EMPTY); clearTransient(p); return; }
        if (!enabled(p)) { clearTransient(p); return; }
        var mass = p.getData(ModAttachments.CRITICAL_MASS);
        int capacity=CombatSkill.criticalMassCapacity(state.getCombatSkillMask());
        if (mass.reserve() > capacity) publish(p, new CriticalMassState(capacity, mass.adrenalineArmed(), mass.lastAdrenalineDevastatorAt()));
        if (!adrenaline.active(p.level().getGameTime())) {
            clearTransient(p);
            mass = p.getData(ModAttachments.CRITICAL_MASS);
            if (!mass.adrenalineArmed() && p.getHealth() >= p.getMaxHealth() * .5F) publish(p, mass.armed(true));
        }
    }
    public static void clearTransient(ServerPlayer p) { publish(p, AdrenalineState.EMPTY); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clearTransient(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clearTransient(p);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clearTransient(p);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if(event.isWasDeath()&&event.getEntity() instanceof ServerPlayer p){
            var old=event.getOriginal().getData(ModAttachments.CRITICAL_MASS);
            // Death loses overhealth, but cannot reset the free-release interval.
            p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(0,true,old.lastAdrenalineDevastatorAt()));
        }
    }
}
