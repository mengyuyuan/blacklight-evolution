package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.ClawCombatState;
import com.codex.blacklight.movement.MovementState;
import com.codex.blacklight.state.BlacklightState;
import com.codex.blacklight.state.BlacklightStateStreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BlacklightNeoMod.MOD_ID);

    public static final Supplier<AttachmentType<BlacklightState>> PLAYER_STATE = ATTACHMENTS.register(
            "player_state",
            () -> AttachmentType.builder(BlacklightState::new)
                    .serialize(BlacklightState.CODEC)
                    .copyOnDeath()
                    .sync(BlacklightStateStreamCodec.CODEC)
                    .build());

    public static final Supplier<AttachmentType<com.codex.blacklight.power.SkillSettings>> SKILL_SETTINGS=
            ATTACHMENTS.register("skill_settings",()->AttachmentType.builder(
                    ()->com.codex.blacklight.power.SkillSettings.DEFAULT)
                    .serialize(com.codex.blacklight.power.SkillSettings.CODEC).copyOnDeath()
                    .sync(com.codex.blacklight.power.SkillSettings.STREAM_CODEC).build());

    public static final Supplier<AttachmentType<ClawCombatState>> CLAW_COMBAT = ATTACHMENTS.register(
            "claw_combat",
            () -> AttachmentType.builder(ClawCombatState::new).build());

    public static final Supplier<AttachmentType<MovementState>> MOVEMENT_STATE = ATTACHMENTS.register(
            "movement_state",
            () -> AttachmentType.builder(MovementState::new).build());

    /** Action playback belongs to Blacklight and survives a new observer's tracking handshake. */
    public static final Supplier<AttachmentType<com.codex.blacklight.animation.NativeActionState>> PLAYER_ACTION =
            ATTACHMENTS.register("player_action", () -> AttachmentType.builder(
                    () -> com.codex.blacklight.animation.NativeActionState.EMPTY)
                    .sync(com.codex.blacklight.animation.NativeActionState.CODEC).build());

    /** Confirmed explicit target, used by observers to face the same actor as the server. */
    public static final Supplier<AttachmentType<Integer>> TARGET_LOCK_ID = ATTACHMENTS.register(
            "target_lock_id", () -> AttachmentType.builder(() -> -1)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

    /** Short-lived glide visual lease; expires on interruption, logout or dimension change. */
    public static final Supplier<AttachmentType<Long>> GLIDE_UNTIL = ATTACHMENTS.register(
            "glide_until", () -> AttachmentType.builder(() -> -1L)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG).build());

    /** Server game time of infection onset; -1 denotes a pre-existing infection. */
    public static final Supplier<AttachmentType<Long>> INFECTION_START = ATTACHMENTS.register(
            "infection_start", () -> AttachmentType.builder(() -> -1L)
                    .serialize(com.mojang.serialization.Codec.LONG)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG).build());

    /** Transient action lock; never carried through logout/death or persisted. */
    public static final Supplier<AttachmentType<Boolean>> CONSUMING = ATTACHMENTS.register(
            "consuming", () -> AttachmentType.builder(() -> false)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.BOOL).build());

    public static final Supplier<AttachmentType<Integer>> GRAB_STAGE = ATTACHMENTS.register(
            "grab_stage", () -> AttachmentType.builder(() -> 0)
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

    public static final Supplier<AttachmentType<com.codex.blacklight.combat.WeaponVisual>> WEAPON_VISUAL=ATTACHMENTS.register(
            "weapon_visual",()->AttachmentType.builder(()->com.codex.blacklight.combat.WeaponVisual.EMPTY).sync(com.codex.blacklight.combat.WeaponVisual.CODEC).build());

    public static final Supplier<AttachmentType<com.codex.blacklight.state.GrowthProgress>> GROWTH=ATTACHMENTS.register(
            "growth",()->AttachmentType.builder(()->com.codex.blacklight.state.GrowthProgress.EMPTY).serialize(com.codex.blacklight.state.GrowthProgress.CODEC).copyOnDeath().sync(com.codex.blacklight.state.GrowthProgress.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.UtilityState>> UTILITY_STATE = ATTACHMENTS.register(
            "utility_state", () -> AttachmentType.builder(() -> com.codex.blacklight.power.UtilityState.EMPTY)
                    .serialize(com.codex.blacklight.power.UtilityState.CODEC)
                    .sync(com.codex.blacklight.power.UtilityState.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.BiomassState>> BIOMASS_FORM = ATTACHMENTS.register("biomass_form", () -> AttachmentType.builder(() -> com.codex.blacklight.power.BiomassState.EMPTY).serialize(com.codex.blacklight.power.BiomassState.CODEC).copyOnDeath().sync((holder, observer) -> com.codex.blacklight.power.BiomassSync.shouldSync(holder), com.codex.blacklight.power.BiomassState.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.AdaptivePassiveState>> ADAPTIVE_PASSIVES =
            ATTACHMENTS.register("adaptive_passives", () -> AttachmentType.builder(() -> com.codex.blacklight.power.AdaptivePassiveState.EMPTY)
                    .serialize(com.codex.blacklight.power.AdaptivePassiveState.CODEC).copyOnDeath()
                    .sync(com.codex.blacklight.power.AdaptivePassiveState.STREAM_CODEC).build());

    public static final Supplier<AttachmentType<Long>> RICOCHET_LEVEL=ATTACHMENTS.register("ricochet_level",
            ()->AttachmentType.builder(()->0L).serialize(com.mojang.serialization.Codec.LONG).copyOnDeath().sync(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.DefenseImpact>> DEFENSE_IMPACT=ATTACHMENTS.register("defense_impact",
            ()->AttachmentType.builder(()->com.codex.blacklight.power.DefenseImpact.EMPTY).sync(com.codex.blacklight.power.DefenseImpact.CODEC).build());

    private ModAttachments() {
    }
    public static final Supplier<AttachmentType<Integer>> JUMP_CHARGE=ATTACHMENTS.register("jump_charge",
            ()->AttachmentType.builder(()->0).sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.combat.HitReaction>> HIT_REACTION=ATTACHMENTS.register("hit_reaction",
            ()->AttachmentType.builder(()->com.codex.blacklight.combat.HitReaction.EMPTY).sync(com.codex.blacklight.combat.HitReaction.CODEC).build());
    public static final Supplier<AttachmentType<Integer>> UTILITY_POWERS=ATTACHMENTS.register("utility_powers",()->AttachmentType.builder(()->0).sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.PowerMorph>> POWER_MORPH=ATTACHMENTS.register("power_morph",()->AttachmentType.builder(()->com.codex.blacklight.power.PowerMorph.NONE).sync(com.codex.blacklight.power.PowerMorph.CODEC).build());

    /** Successful consume overflow persists through logout, but is lost on death. */
    public static final Supplier<AttachmentType<com.codex.blacklight.power.CriticalMassState>> CRITICAL_MASS =
            ATTACHMENTS.register("critical_mass", () -> AttachmentType.builder(() -> com.codex.blacklight.power.CriticalMassState.EMPTY)
                    .serialize(com.codex.blacklight.power.CriticalMassState.CODEC)
                    .sync(com.codex.blacklight.power.CriticalMassState.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.AdrenalineState>> ADRENALINE =
            ATTACHMENTS.register("adrenaline", () -> AttachmentType.builder(() -> com.codex.blacklight.power.AdrenalineState.EMPTY)
                    .sync(com.codex.blacklight.power.AdrenalineState.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.combat.DevastatorVisual>> DEVASTATOR_VISUAL =
            ATTACHMENTS.register("devastator_visual", () -> AttachmentType.builder(() -> com.codex.blacklight.combat.DevastatorVisual.EMPTY)
                    .sync(com.codex.blacklight.combat.DevastatorVisual.CODEC).build());

    public static final Supplier<AttachmentType<com.codex.blacklight.combat.ConsumeVisual>> CONSUME_RECOVERY = ATTACHMENTS.register(
            "consume_recovery", () -> AttachmentType.builder(() -> com.codex.blacklight.combat.ConsumeVisual.EMPTY)
                    .sync(com.codex.blacklight.combat.ConsumeVisual.CODEC).build());

    public static final Supplier<AttachmentType<com.codex.blacklight.combat.ConsumeVisual>> CONSUME_VISUAL = ATTACHMENTS.register(
            "consume_visual", () -> AttachmentType.builder(() -> com.codex.blacklight.combat.ConsumeVisual.EMPTY)
                    .sync(com.codex.blacklight.combat.ConsumeVisual.CODEC).build());

    public static final Supplier<AttachmentType<com.codex.blacklight.disguise.DisguiseIdentity>> DISGUISE_IDENTITY=ATTACHMENTS.register(
            "disguise_identity",()->AttachmentType.builder(()->com.codex.blacklight.disguise.DisguiseIdentity.EMPTY)
                    .serialize(com.codex.blacklight.disguise.DisguiseIdentity.CODEC).copyOnDeath()
                    .sync(com.codex.blacklight.disguise.DisguiseIdentity.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.disguise.DisguiseMorph>> DISGUISE_MORPH=ATTACHMENTS.register(
            "disguise_morph",()->AttachmentType.builder(()->com.codex.blacklight.disguise.DisguiseMorph.EMPTY)
                    .sync(com.codex.blacklight.disguise.DisguiseMorph.CODEC).build());

    /** Observe transient state without allocating or syncing attachments on unrelated entities. */
    public static <T> T peekData(net.minecraft.world.entity.Entity entity,
                                 Supplier<AttachmentType<T>> key, T fallback) {
        return !entity.isRemoved() && entity.hasData(key) ? entity.getData(key) : fallback;
    }

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }
}
