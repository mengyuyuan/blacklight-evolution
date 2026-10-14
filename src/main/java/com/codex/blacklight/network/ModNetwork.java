package com.codex.blacklight.network;

import com.codex.blacklight.client.ClientPhysicalFeedback;
import com.codex.blacklight.client.ClientClawAnimation;
import com.codex.blacklight.client.ClientLandingVisuals;
import com.codex.blacklight.combat.ClawCombatSystem;
import com.codex.blacklight.movement.PrototypeMovementSystem;
import com.codex.blacklight.movement.MovementTuningSettings;
import com.codex.blacklight.mode.PrototypeModeSystem;
import com.codex.blacklight.power.UpgradeSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.codex.blacklight.forge.PacketDistributor;
import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.forge.ForgePayloadRegistrar;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "forge-1.20.1-6";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BlacklightNeoMod.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    public static SimpleChannel channel() { return CHANNEL; }
    private ModNetwork() {
    }

    public static void register() {
        if (registered) throw new IllegalStateException("Blacklight network registered twice");
        registered = true;
        new ForgePayloadRegistrar(CHANNEL)
                .playToClient(TerrainGrabPreviewPayload.class,TerrainGrabPreviewPayload.STREAM_CODEC,(payload,context)->context.enqueueWork(()->com.codex.blacklight.client.ClientTerrainGrab.accept(payload)))
                .playToServer(BlockThrowAimPayload.class,BlockThrowAimPayload.STREAM_CODEC,(payload,context)->context.enqueueWork(()->{
                    if(context.player() instanceof ServerPlayer p){var held=com.codex.blacklight.combat.BlockThrowSystem.held(p);if(held!=null)held.acceptAim(p,new net.minecraft.world.phys.Vec3(payload.x(),payload.y(),payload.z()),payload.assist());}
                }))
                .playToServer(SkillSettingsPayload.class,SkillSettingsPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            com.codex.blacklight.registry.ModAttachments.setData(player, com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS,payload.apply(com.codex.blacklight.registry.ModAttachments.getData(player, com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS)));
                            if(payload.setting()==SkillSettingsPayload.Setting.BULLETDIVE_BLOCKS)
                                com.codex.blacklight.combat.BulletdiveTerrain.settingsChanged(player);
                            com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS);
                        }
                    }))
                .playToClient(NativeRootMotionPayload.class, NativeRootMotionPayload.STREAM_CODEC, (payload, context) ->

                    context.enqueueWork(() -> com.codex.blacklight.animation.PlayerMotionController.acceptRootMotion(payload)))
                .playToServer(DisguiseTogglePayload.class,DisguiseTogglePayload.CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            if(com.codex.blacklight.power.BiomassSystem.active(player))com.codex.blacklight.power.BiomassSystem.cancel(player);
                            else if(canAct(player))com.codex.blacklight.disguise.DisguiseSystem.request(player);
                        }
                    }))
                .playToServer(TargetLockPayload.class,TargetLockPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            com.codex.blacklight.combat.CombatTargeting.requestLock(player,payload.entityId());
                            PacketDistributor.sendToPlayer(player,new TargetLockAckPayload(payload.requestId(),
                                    com.codex.blacklight.registry.ModAttachments.getData(player, com.codex.blacklight.registry.ModAttachments.TARGET_LOCK_ID)));
                        }
                    }))
                .playToClient(TargetLockAckPayload.class,TargetLockAckPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->com.codex.blacklight.client.ClientTargetLock.acceptAck(payload)))
                .playToServer(WeaponInputPayload.class,WeaponInputPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            if(payload.cancelled())com.codex.blacklight.combat.WeaponCombatSystem.cancelHeldInput(player);
                            else com.codex.blacklight.combat.WeaponCombatSystem.acceptInput(player,payload.attack(),payload.special(),payload.action(),payload.jump());
                        }
                    }))
                .playToServer(PowerSelectPayload.class,PowerSelectPayload.STREAM_CODEC,(payload,context)->{
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player && canAct(player)) {
                            var forms=com.codex.blacklight.power.PowerForm.values();
                            if(payload.formId()>=0 && payload.formId()<forms.length) {
                                var form=forms[payload.formId()];
                                if(com.codex.blacklight.power.PowerFormSystem.select(player,form,false)&&form!=com.codex.blacklight.power.PowerForm.BIOMASS&&payload.longTransform())
                                    com.codex.blacklight.power.PowerMorph.begin(player,form);
                            }
                        }
                    });
                })
                .playToClient(WeaponAnimationPayload.class,WeaponAnimationPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->com.codex.blacklight.client.ClientWeaponCombat.animate(payload)))
                .playToClient(CombatImpactPayload.class,CombatImpactPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->com.codex.blacklight.client.renderer.CombatImpactRenderer.accept(payload)))
                .playToServer(ClawActionPayload.class, ClawActionPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> ClawCombatSystem.handle(player, payload.action()));
                    }
                })
                .playToServer(MovementInputPayload.class, MovementInputPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> {
                            if (!PrototypeModeSystem.isActive(player)) return;
                            com.codex.blacklight.registry.ModAttachments.getData(player, com.codex.blacklight.registry.ModAttachments.MOVEMENT_STATE).setCameraYaw(payload.cameraYaw());
                            PrototypeMovementSystem.acceptInput(player, payload.jumpHeld(),
                                    payload.dashPressed(), payload.forward(), payload.sideways(), payload.sprinting(), payload.detach());
                        });
                    }
                })
                .playToServer(PrototypeModePayload.class, PrototypeModePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> PrototypeModeSystem.handle(player, payload.enabled()));
                    }
                })
                .playToServer(UpgradePurchasePayload.class, UpgradePurchasePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> UpgradeSystem.purchase(player, payload.upgradeId()));
                    }
                })
                .playToClient(MovementTuningPayload.class, MovementTuningPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> com.codex.blacklight.client.ClientMovementTuning.receive(payload.snapshot()));
                })
                .playToClient(PhysicalFeedbackPayload.class, PhysicalFeedbackPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> ClientPhysicalFeedback.accept(payload));
                })
                .playToClient(ClawAnimationPayload.class, ClawAnimationPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> ClientClawAnimation.accept(payload));
                })
                .playToClient(WallMotionPayload.class, WallMotionPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> com.codex.blacklight.client.ClientWallMotion.accept(payload));
                })
                .playToClient(LandingVisualPayload.class, LandingVisualPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> ClientLandingVisuals.accept(payload));
                })
                .playToClient(AttachmentSyncPayload.class, AttachmentSyncPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> com.codex.blacklight.forge.ClientAttachmentSync.accept(payload));
                });
    }

    private static boolean canAct(ServerPlayer player) {
        return player != null && !com.codex.blacklight.power.BiomassSystem.active(player) && player.isAlive() && !player.isSpectator() && !player.isDeadOrDying();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)
            com.codex.blacklight.combat.CombatTargeting.requestLock(player,-1);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            synchronizePersistentState(player);
            PacketDistributor.sendToPlayer(player,
                    new MovementTuningPayload(MovementTuningSettings.snapshot()));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) synchronizePersistentState(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.codex.blacklight.registry.ModAttachments.storage(player).clearTransient();
            synchronizePersistentState(player);
        }
    }

    @SubscribeEvent public static void onPlayerTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target) {
            com.codex.blacklight.registry.ModAttachments.syncData(target, com.codex.blacklight.registry.ModAttachments.CRITICAL_MASS);
            com.codex.blacklight.registry.ModAttachments.syncData(target, com.codex.blacklight.registry.ModAttachments.ADRENALINE);
            com.codex.blacklight.registry.ModAttachments.syncData(target, com.codex.blacklight.registry.ModAttachments.DEVASTATOR_VISUAL);
        }
    }

    private static void synchronizePersistentState(ServerPlayer player) {
        com.codex.blacklight.animation.NativePlayerActionBackend.clear(player);
        com.codex.blacklight.power.GrowthSystem.updateHealth(player);
        // Copy-on-death restores server data; explicitly publish it for the new client
        // entity as well. Client mode reconciliation tolerates either packet order.
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.GROWTH);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.UTILITY_STATE);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.DISGUISE_IDENTITY);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.DISGUISE_MORPH);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.CRITICAL_MASS);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.ADRENALINE);
        com.codex.blacklight.registry.ModAttachments.syncData(player, com.codex.blacklight.registry.ModAttachments.DEVASTATOR_VISUAL);
        com.codex.blacklight.registry.ModAttachments.syncAll(player, player);

    }
}
