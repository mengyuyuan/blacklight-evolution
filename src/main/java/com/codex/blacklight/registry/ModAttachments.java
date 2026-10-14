package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.ClawCombatState;
import com.codex.blacklight.movement.MovementState;
import com.codex.blacklight.state.BlacklightState;
import com.codex.blacklight.state.BlacklightStateStreamCodec;
import net.minecraftforge.eventbus.api.IEventBus;
import com.codex.blacklight.forge.AttachmentType;
import com.codex.blacklight.forge.BlacklightAttachments;
import com.codex.blacklight.forge.ForgePayloadRegistrar;
import com.codex.blacklight.forge.PacketDistributor;
import com.codex.blacklight.network.AttachmentSyncPayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.function.Supplier;

public final class ModAttachments {
    private static final AttachmentRegistry ATTACHMENTS = new AttachmentRegistry();

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
                    .sync(com.codex.blacklight.forge.codec.ByteBufCodecs.VAR_INT).build());

    /** Short-lived glide visual lease; expires on interruption, logout or dimension change. */
    public static final Supplier<AttachmentType<Long>> GLIDE_UNTIL = ATTACHMENTS.register(
            "glide_until", () -> AttachmentType.builder(() -> -1L)
                    .sync(com.codex.blacklight.forge.codec.ByteBufCodecs.VAR_LONG).build());

    /** Server game time of infection onset; -1 denotes a pre-existing infection. */
    public static final Supplier<AttachmentType<Long>> INFECTION_START = ATTACHMENTS.register(
            "infection_start", () -> AttachmentType.builder(() -> -1L)
                    .serialize(com.mojang.serialization.Codec.LONG)
                    .sync(com.codex.blacklight.forge.codec.ByteBufCodecs.VAR_LONG).build());

    /** Transient action lock; never carried through logout/death or persisted. */
    public static final Supplier<AttachmentType<Boolean>> CONSUMING = ATTACHMENTS.register(
            "consuming", () -> AttachmentType.builder(() -> false)
                    .sync(com.codex.blacklight.forge.codec.ByteBufCodecs.BOOL).build());

    public static final Supplier<AttachmentType<Integer>> GRAB_STAGE = ATTACHMENTS.register(
            "grab_stage", () -> AttachmentType.builder(() -> 0)
                    .sync(com.codex.blacklight.forge.codec.ByteBufCodecs.VAR_INT).build());

    public static final Supplier<AttachmentType<com.codex.blacklight.combat.WeaponVisual>> WEAPON_VISUAL=ATTACHMENTS.register(
            "weapon_visual",()->AttachmentType.builder(()->com.codex.blacklight.combat.WeaponVisual.EMPTY).sync(com.codex.blacklight.combat.WeaponVisual.CODEC).build());

    public static final Supplier<AttachmentType<com.codex.blacklight.state.GrowthProgress>> GROWTH=ATTACHMENTS.register(
            "growth",()->AttachmentType.builder(()->com.codex.blacklight.state.GrowthProgress.EMPTY).serialize(com.codex.blacklight.state.GrowthProgress.CODEC).copyOnDeath().sync(com.codex.blacklight.state.GrowthProgress.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.UtilityState>> UTILITY_STATE = ATTACHMENTS.register(
            "utility_state", () -> AttachmentType.builder(() -> com.codex.blacklight.power.UtilityState.EMPTY)
                    .serialize(com.codex.blacklight.power.UtilityState.CODEC)
                    .sync(com.codex.blacklight.power.UtilityState.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.BiomassState>> BIOMASS_FORM = ATTACHMENTS.register("biomass_form", () -> AttachmentType.builder(() -> com.codex.blacklight.power.BiomassState.EMPTY).serialize(com.codex.blacklight.power.BiomassState.CODEC).copyOnDeath().sync(com.codex.blacklight.power.BiomassState.STREAM_CODEC).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.power.AdaptivePassiveState>> ADAPTIVE_PASSIVES =
            ATTACHMENTS.register("adaptive_passives", () -> AttachmentType.builder(() -> com.codex.blacklight.power.AdaptivePassiveState.EMPTY)
                    .serialize(com.codex.blacklight.power.AdaptivePassiveState.CODEC).copyOnDeath()
                    .sync(com.codex.blacklight.power.AdaptivePassiveState.STREAM_CODEC).build());

    private ModAttachments() {
    }
    public static final Supplier<AttachmentType<Integer>> JUMP_CHARGE=ATTACHMENTS.register("jump_charge",
            ()->AttachmentType.builder(()->0).sync(com.codex.blacklight.forge.codec.ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<com.codex.blacklight.combat.HitReaction>> HIT_REACTION=ATTACHMENTS.register("hit_reaction",
            ()->AttachmentType.builder(()->com.codex.blacklight.combat.HitReaction.EMPTY).sync(com.codex.blacklight.combat.HitReaction.CODEC).build());
    public static final Supplier<AttachmentType<Integer>> UTILITY_POWERS=ATTACHMENTS.register("utility_powers",()->AttachmentType.builder(()->0).sync(com.codex.blacklight.forge.codec.ByteBufCodecs.VAR_INT).build());
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

    public static void register(IEventBus bus) {
        bus.addListener((RegisterCapabilitiesEvent event) -> event.register(BlacklightAttachments.class));
        MinecraftForge.EVENT_BUS.register(ModAttachments.class);
    }

    public static Map<String, AttachmentType<?>> types() {
        return Collections.unmodifiableMap(ATTACHMENTS.types);
    }

    public static BlacklightAttachments storage(Entity entity) {
        return entity.getCapability(BlacklightAttachments.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Missing Blacklight entity capability"));
    }

    /** Passive hooks must not create/sync state or revive missing/invalid capabilities. */
    public static <T> T peekData(Entity entity, Supplier<AttachmentType<T>> key) {
        AttachmentType<T> type = key.get();
        if (entity.isRemoved()) return type.createDefault();
        return entity.getCapability(BlacklightAttachments.CAPABILITY).resolve()
                .filter(storage -> storage.has(type)).map(storage -> storage.get(type))
                .orElseGet(type::createDefault);
    }

    public static boolean available(Entity entity) {
        return !entity.isRemoved() && entity.getCapability(BlacklightAttachments.CAPABILITY).isPresent();
    }

    public static <T> T getData(Entity entity, Supplier<AttachmentType<T>> key) {
        AttachmentType<T> type = key.get();
        // Minecraft retains its removed LocalPlayer/camera until respawn or disconnect.
        // Read-only presentation during that interval sees an empty snapshot, never a
        // resurrected capability. Live client prediction and all server access stay strict.
        if (entity.level().isClientSide && entity.isRemoved()) return type.createDefault();
        BlacklightAttachments storage = storage(entity);
        boolean created = !storage.has(type);
        T value = storage.get(type);
        // NeoForge also synchronizes a sync-enabled attachment when its default is first created.
        if (created && !entity.level().isClientSide && !entity.isRemoved()) publish(entity, type);
        return value;
    }

    public static <T> T setData(Entity entity, Supplier<AttachmentType<T>> key, T value) {
        AttachmentType<T> type = key.get();
        T previous = storage(entity).set(type, value);
        // Preserve AttachmentHolder#setData: callers replacing a value need no explicit syncData call.
        if (!entity.level().isClientSide && !entity.isRemoved()) publish(entity, type);
        return previous;
    }

    public static boolean hasData(Entity entity, Supplier<? extends AttachmentType<?>> key) {
        return storage(entity).has(key.get());
    }

    public static void removeData(Entity entity, Supplier<? extends AttachmentType<?>> key) {
        AttachmentType<?> type = key.get();
        storage(entity).remove(type);
        // Observers must clear an old replicated value even though no default is stored on the server.
        if (!entity.level().isClientSide && type.syncCodec() != null && !entity.isRemoved())
            publish(entity, type);
    }

    public static void syncData(Entity entity, Supplier<? extends AttachmentType<?>> key) {
        if (!entity.level().isClientSide && !entity.isRemoved()) publish(entity, key.get());
    }

    private static void publish(Entity entity, AttachmentType<?> type) {
        if (type.syncCodec() == null) return;
        String name = ATTACHMENTS.types.entrySet().stream().filter(field -> field.getValue() == type)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packet(entity,
                Map.of(name, encodeField(entity, type))));
    }

    private static AttachmentSyncPayload packet(Entity entity, Map<String, byte[]> fields) {
        return new AttachmentSyncPayload(entity.getId(), entity.getUUID(), entity.level().dimension().location(), fields);
    }

    public static void syncAll(Entity entity, ServerPlayer observer) {
        if (!available(entity)) return;
        Map<String, byte[]> fields = new LinkedHashMap<>();
        ATTACHMENTS.types.forEach((name, type) -> {
            if (type.syncCodec() != null && (entity instanceof Player || storage(entity).has(type)))
                fields.put(name, encodeField(entity, type));
        });
        if (!fields.isEmpty()) PacketDistributor.sendToPlayer(observer, packet(entity, Map.copyOf(fields)));
    }

    private static <T> byte[] encodeField(Entity entity, AttachmentType<T> type) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(128, ForgePayloadRegistrar.MAX_PACKET_BYTES));
        try {
            BlacklightAttachments storage = storage(entity);
            type.syncCodec().encode(buffer, storage.has(type) ? storage.get(type) : type.createDefault());
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.readBytes(encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    /** Invoked solely by the registered PLAY_TO_CLIENT message consumer. */
    public static void applyClientUpdate(Entity entity, Map<String, byte[]> fields) {
        if (!entity.level().isClientSide) throw new IllegalStateException("Client attachment update on server");
        if (!available(entity)) return; // A late packet cannot repopulate a removed player's state.
        fields.forEach((name, bytes) -> {
            AttachmentType<?> type = ATTACHMENTS.types.get(name);
            if (type == null || type.syncCodec() == null) throw new DecoderException("Unknown Blacklight attachment");
            decodeField(entity, type, bytes);
        });
    }

    private static <T> void decodeField(Entity entity, AttachmentType<T> type, byte[] bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            T value = type.syncCodec().decode(buffer);
            if (buffer.isReadable()) throw new DecoderException("Trailing bytes in Blacklight attachment");
            storage(entity).set(type, value);
        } finally {
            buffer.release();
        }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        BlacklightAttachments storage = new BlacklightAttachments();
        event.addCapability(new ResourceLocation(BlacklightNeoMod.MOD_ID, "entity_state"), storage);
        event.addListener(storage::invalidate);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        try {
            storage(event.getEntity()).copyPersistentFrom(storage(event.getOriginal()), event.isWasDeath());
        } finally {
            event.getOriginal().invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer) syncAll(event.getTarget(), observer);
    }

    private static final class AttachmentRegistry {
        private final Map<String, AttachmentType<?>> types = new LinkedHashMap<>();

        private <T> Supplier<AttachmentType<T>> register(String name, Supplier<AttachmentType<T>> factory) {
            AttachmentType<T> type = factory.get();
            if (types.putIfAbsent(name, type) != null) throw new IllegalStateException("Duplicate Blacklight attachment " + name);
            return () -> type;
        }
    }
}
