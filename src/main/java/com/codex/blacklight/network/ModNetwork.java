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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("standalone-14")
                .playToClient(TerrainGrabPreviewPayload.TYPE,TerrainGrabPreviewPayload.STREAM_CODEC,(payload,context)->context.enqueueWork(()->com.codex.blacklight.client.ClientTerrainGrab.accept(payload)))
                .playToServer(BlockThrowAimPayload.TYPE,BlockThrowAimPayload.STREAM_CODEC,(payload,context)->context.enqueueWork(()->{
                    if(context.player() instanceof ServerPlayer p){var held=com.codex.blacklight.combat.BlockThrowSystem.held(p);if(held!=null)held.acceptAim(p,new net.minecraft.world.phys.Vec3(payload.x(),payload.y(),payload.z()),payload.assist());}
                }))
                .playToServer(SkillSettingsPayload.TYPE,SkillSettingsPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            player.setData(com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS,
                                    payload.apply(player.getData(com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS)));
                            if(payload.setting()==SkillSettingsPayload.Setting.BULLETDIVE_BLOCKS)
                                com.codex.blacklight.combat.BulletdiveTerrain.settingsChanged(player);
                            player.syncData(com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS);
                        }
                    }))
                .playToClient(NativeRootMotionPayload.TYPE, NativeRootMotionPayload.STREAM_CODEC, (payload, context) ->
                    context.enqueueWork(() -> com.codex.blacklight.animation.PlayerMotionController.acceptRootMotion(payload)))
                .playToServer(DisguiseTogglePayload.TYPE,DisguiseTogglePayload.CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            if(com.codex.blacklight.power.BiomassSystem.active(player))com.codex.blacklight.power.BiomassSystem.cancel(player);
                            else if(canAct(player))com.codex.blacklight.disguise.DisguiseSystem.request(player);
                        }
                    }))
                .playToServer(TargetLockPayload.TYPE,TargetLockPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            com.codex.blacklight.combat.CombatTargeting.requestLock(player,payload.entityId());
                            PacketDistributor.sendToPlayer(player,new TargetLockAckPayload(payload.requestId(),
                                    player.getData(com.codex.blacklight.registry.ModAttachments.TARGET_LOCK_ID)));
                        }
                    }))
                .playToClient(TargetLockAckPayload.TYPE,TargetLockAckPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->com.codex.blacklight.client.ClientTargetLock.acceptAck(payload)))
                .playToServer(WeaponInputPayload.TYPE,WeaponInputPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->{
                        if(context.player() instanceof ServerPlayer player){
                            if(payload.cancelled())com.codex.blacklight.combat.WeaponCombatSystem.cancelHeldInput(player);
                            else com.codex.blacklight.combat.WeaponCombatSystem.acceptInput(player,payload.attack(),payload.special(),payload.action(),payload.jump());
                        }
                    }))
                .playToServer(PowerSelectPayload.TYPE,PowerSelectPayload.STREAM_CODEC,(payload,context)->{
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
                .playToClient(WeaponAnimationPayload.TYPE,WeaponAnimationPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->com.codex.blacklight.client.ClientWeaponCombat.animate(payload)))
                .playToClient(CombatImpactPayload.TYPE,CombatImpactPayload.STREAM_CODEC,(payload,context)->
                    context.enqueueWork(()->com.codex.blacklight.client.renderer.CombatImpactRenderer.accept(payload)))
                .playToServer(ClawActionPayload.TYPE, ClawActionPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> ClawCombatSystem.handle(player, payload.action()));
                    }
                })
                .playToServer(MovementInputPayload.TYPE, MovementInputPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> {
                            if (!PrototypeModeSystem.isActive(player)) return;
                            player.getData(com.codex.blacklight.registry.ModAttachments.MOVEMENT_STATE).setCameraYaw(payload.cameraYaw());
                            PrototypeMovementSystem.acceptInput(player, payload.jumpHeld(),
                                    payload.dashPressed(), payload.forward(), payload.sideways(), payload.sprinting(), payload.detach());
                        });
                    }
                })
                .playToServer(PrototypeModePayload.TYPE, PrototypeModePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> PrototypeModeSystem.handle(player, payload.enabled()));
                    }
                })
                .playToServer(UpgradePurchasePayload.TYPE, UpgradePurchasePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && canAct(player)) {
                        context.enqueueWork(() -> UpgradeSystem.purchase(player, payload.upgradeId()));
                    }
                })
                .playToClient(MovementTuningPayload.TYPE, MovementTuningPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> com.codex.blacklight.client.ClientMovementTuning.receive(payload.snapshot()));
                })
                .playToClient(PhysicalFeedbackPayload.TYPE, PhysicalFeedbackPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> ClientPhysicalFeedback.accept(payload));
                })
                .playToClient(ClawAnimationPayload.TYPE, ClawAnimationPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> ClientClawAnimation.accept(payload));
                })
                .playToClient(WallMotionPayload.TYPE, WallMotionPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> com.codex.blacklight.client.ClientWallMotion.accept(payload));
                })
                .playToClient(LandingVisualPayload.TYPE, LandingVisualPayload.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> ClientLandingVisuals.accept(payload));
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
        if (event.getEntity() instanceof ServerPlayer player) synchronizePersistentState(player);
    }

    @SubscribeEvent public static void onPlayerTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target) {
            target.syncData(com.codex.blacklight.registry.ModAttachments.CRITICAL_MASS);
            target.syncData(com.codex.blacklight.registry.ModAttachments.ADRENALINE);
            target.syncData(com.codex.blacklight.registry.ModAttachments.DEVASTATOR_VISUAL);
        }
    }

    private static void synchronizePersistentState(ServerPlayer player) {
        com.codex.blacklight.animation.NativePlayerActionBackend.clear(player);
        com.codex.blacklight.power.GrowthSystem.updateHealth(player);
        // Copy-on-death restores server data; explicitly publish it for the new client
        // entity as well. Client mode reconciliation tolerates either packet order.
        player.syncData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);
        player.syncData(com.codex.blacklight.registry.ModAttachments.SKILL_SETTINGS);
        player.syncData(com.codex.blacklight.registry.ModAttachments.GROWTH);
        player.syncData(com.codex.blacklight.registry.ModAttachments.UTILITY_STATE);
        player.syncData(com.codex.blacklight.registry.ModAttachments.DISGUISE_IDENTITY);
        player.syncData(com.codex.blacklight.registry.ModAttachments.DISGUISE_MORPH);
        player.syncData(com.codex.blacklight.registry.ModAttachments.CRITICAL_MASS);
        player.syncData(com.codex.blacklight.registry.ModAttachments.ADRENALINE);
        player.syncData(com.codex.blacklight.registry.ModAttachments.DEVASTATOR_VISUAL);
    }
}
