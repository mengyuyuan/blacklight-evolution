package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.renderer.GroundSpikeRenderer;
import com.codex.blacklight.client.screen.ClawTunerScreen;
import com.codex.blacklight.client.screen.AnimationTunerScreen;
import com.codex.blacklight.client.screen.UpgradeScreen;
import com.codex.blacklight.client.model.ClawArmModel;
import com.codex.blacklight.entity.GroundSpikeEntity;
import com.codex.blacklight.animation.PlayerAction;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.network.ClawActionPayload;
import com.codex.blacklight.network.PrototypeModePayload;
import com.codex.blacklight.mode.PrototypeSurvivalLock;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT)
public final class ClientClawEvents {
    public static KeyMapping targetLockKey(){return TARGET_LOCK;}
    public static KeyMapping targetInfoKey(){return TARGET_INFO;}
    private static final String CATEGORY = "key.categories.blacklight";
    private static final KeyMapping CYCLE_FORM = new KeyMapping("key.blacklight.cycle_form", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    private static final KeyMapping GRAB = new KeyMapping(
            "key.blacklight.grab", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);
    private static final KeyMapping TOGGLE_CLAWS = new KeyMapping(
            "key.blacklight.toggle_claws", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    private static final KeyMapping PROTOTYPE_MODE = new KeyMapping(
            "key.blacklight.prototype_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    private static final KeyMapping CLAW_TUNER = new KeyMapping(
            "key.blacklight.claw_tuner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    private static final KeyMapping ANIMATION_TUNER = new KeyMapping(
            "key.blacklight.animation_tuner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    private static final KeyMapping TARGET_LOCK = new KeyMapping(
            "key.blacklight.target_lock", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, CATEGORY);
    private static final KeyMapping TARGET_INFO = new KeyMapping(
            "key.blacklight.target_info", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_ALT, CATEGORY);
    private static final KeyMapping UPGRADE_PANEL = new KeyMapping(
            "key.blacklight.upgrades", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY);
    /** Grounded left-clicks held this long become a dashing slice instead of a combo hit. */
    private static final int DASHING_SLICE_HOLD_TICKS = 4;
    private static boolean automaticTunerOpened;
    private static boolean lastGrabLeft,lastGrabRight;
    private static boolean lastGrabDown;
    private static net.minecraft.client.player.LocalPlayer inputOwner;
    private static boolean leftAttackHeld;
    private static boolean leftAttackConsumed;
    private static boolean leftAttackTapSent;
    private static boolean lastPhysicalLeftDown;
    private static int leftAttackHoldTicks;
    private static boolean groundSpikeHeld;
    private static boolean groundSpikePosePaused;
    private static boolean groundSpikeAutoReleased;
    private static boolean groundSpikeReversing;
    private static int groundSpikeEruptionTicks;
    private static int groundSpikeStartupWaitTicks;
    // The authored insertion reaches contact after 11 ticks. Capture within half
    // a tick so we do not skip the last visible downward motion before holding.
    // The same 0.55 s insertion is reversed to withdraw the claw.
    private static final float GROUND_SPIKE_PLANT_TIME = 0.55F;
    private static final float GROUND_SPIKE_END_CAPTURE_WINDOW = 0.025F;

    private ClientClawEvents() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(GRAB);
        event.register(CYCLE_FORM);
        event.register(TOGGLE_CLAWS);
        event.register(PROTOTYPE_MODE);
        if (DeveloperTools.enabled()) event.register(CLAW_TUNER);
        if (DeveloperTools.enabled()) event.register(ANIMATION_TUNER);
        event.register(TARGET_LOCK);
        event.register(TARGET_INFO);
        event.register(UPGRADE_PANEL);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BLACKWATCH_SOLDIER.get(),context->new com.codex.blacklight.client.renderer.BlackwatchRenderer(context,false));
        event.registerEntityRenderer(ModEntities.BLACKWATCH_HEAVY.get(),context->new com.codex.blacklight.client.renderer.BlackwatchRenderer(context,true));
        event.registerEntityRenderer(ModEntities.THROWN_BLOCK.get(),com.codex.blacklight.client.renderer.ThrownBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.BIOMASS_HUSK.get(),com.codex.blacklight.client.renderer.BiomassHuskRenderer::new);
        event.registerEntityRenderer(ModEntities.INFECTED_HOST.get(), com.codex.blacklight.client.renderer.InfectedHostRenderer::new);
        event.registerEntityRenderer(ModEntities.COMMON_INFECTED.get(), com.codex.blacklight.client.renderer.CommonInfectedRenderer::new);
        event.registerEntityRenderer(ModEntities.GROUND_SPIKE.get(), GroundSpikeRenderer::new);
        for(var type:java.util.List.of(ModEntities.BLACKLIGHT_STALKER,ModEntities.BLACKLIGHT_SPITTER,ModEntities.BLACKLIGHT_BRUTE))
            event.registerEntityRenderer(type.get(),context->new com.codex.blacklight.client.renderer.BlacklightCreatureRenderer<com.codex.blacklight.entity.BlacklightMutant>(context,com.codex.blacklight.entity.BlacklightMutant::asset));
        event.registerEntityRenderer(ModEntities.HIVE_TENDRIL.get(),com.codex.blacklight.client.renderer.HiveTendrilRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKLIGHT_HIVE.get(),context->new com.codex.blacklight.client.renderer.BlacklightCreatureRenderer<com.codex.blacklight.entity.BlacklightHive>(context,e->"blacklight_hive"));
        event.registerEntityRenderer(ModEntities.BLACKLIGHT_SPIT.get(),ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.EVOLVED_QUILL.get(),com.codex.blacklight.client.renderer.EvolvedQuillRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKLIGHT_HUNTER.get(), com.codex.blacklight.client.renderer.BlacklightHunterRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKLIGHT_HYDRA.get(), com.codex.blacklight.client.renderer.BlacklightHydraRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKLIGHT_VIRUS_VIAL.get(), com.codex.blacklight.client.renderer.VirusVialRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ClawArmModel.LAYER, ClawArmModel::createLayer);
    }

    @SubscribeEvent
    public static void lockConsumptionMovement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        if (!event.getEntity().getData(ModAttachments.CONSUMING)
                || !com.codex.blacklight.combat.ConsumeVisual.blocksInput(event.getEntity().getData(ModAttachments.GRAB_STAGE))) return;
        var input = event.getInput();
        input.forwardImpulse = 0; input.leftImpulse = 0; input.jumping = false;
        input.up = false; input.down = false; input.left = false; input.right = false;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.screen==null && (minecraft.options.keyAttack.matches(event.getKey(),event.getScanCode()) || minecraft.options.keyUse.matches(event.getKey(),event.getScanCode()))){
            ClientWeaponCombat.poll();tickGrabButtons();
        }
        if (event.getAction() == GLFW.GLFW_RELEASE && GRAB.matches(event.getKey(),event.getScanCode())) lastGrabDown=false;
        if (event.getAction() != GLFW.GLFW_PRESS || minecraft.player == null
                || minecraft.getConnection() == null) return;

        handleKeyPress(minecraft, key->key.matches(event.getKey(),event.getScanCode()) && key.getKeyModifier().isActive(key.getKeyConflictContext()));
    }

    private static void handleKeyPress(Minecraft minecraft,java.util.function.Predicate<KeyMapping> matches){
        var state = minecraft.player.getData(ModAttachments.PLAYER_STATE);
        if (minecraft.screen == null && matches.test(PROTOTYPE_MODE)) {
            PacketDistributor.sendToServer(new PrototypeModePayload(!state.isPrototypeMode()));
            return;
        }
        if (minecraft.screen == null && matches.test(UPGRADE_PANEL)) {
            minecraft.setScreen(new UpgradeScreen());
            return;
        }
        if (!state.isPrototypeMode()) return;
        ClientTargetLock.sync();
        if (minecraft.screen == null && matches.test(CYCLE_FORM)) {
            if(!minecraft.player.getData(ModAttachments.CONSUMING))minecraft.setScreen(new com.codex.blacklight.client.screen.PowerWheelScreen(CYCLE_FORM)); return;
        }
        if (minecraft.screen == null && matches.test(GRAB)) {
            pollGrabPress(true);
            return;
        }

        if (DeveloperTools.enabled() && minecraft.screen == null && matches.test(CLAW_TUNER)) {
            minecraft.setScreen(new ClawTunerScreen());
            return;
        }
        if (DeveloperTools.enabled() && minecraft.screen == null && matches.test(ANIMATION_TUNER)) {
            minecraft.setScreen(new AnimationTunerScreen());
            return;
        }
        if (minecraft.screen != null) return;

        if (matches.test(TARGET_LOCK)) {
            toggleTargetLock(minecraft);
            return;
        }

        if (matches.test(TOGGLE_CLAWS)) {
            PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.TOGGLE_CLAWS));
        }
    }

    /** Opens the timeline once for repeatable development screenshots and nothing else. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        resetInputOwner();
        pollGrabPress(physicalGrabDown());
        ClientTerrainGrab.tick();
        suppressPrototypePlayerList();
        tickGrabButtons();
        ClientWeaponCombat.poll();
        tickDeferredGrab();
        tickPhysicalLeftAttack();
        holdGroundSpikePlantPose();
        if (automaticTunerOpened || !Boolean.getBoolean("blacklight.test.openAnimationTuner")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        automaticTunerOpened = true;
        BlacklightNeoMod.LOGGER.info("Opening animation timeline from development acceptance hook");
        minecraft.setScreen(new AnimationTunerScreen());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRawMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        if(event.getAction()==GLFW.GLFW_PRESS){
            boolean mapped=java.util.stream.Stream.of(PROTOTYPE_MODE,UPGRADE_PANEL,CYCLE_FORM,GRAB,TARGET_LOCK,TOGGLE_CLAWS,CLAW_TUNER,ANIMATION_TUNER)
                    .filter(key->DeveloperTools.enabled() || key!=CLAW_TUNER && key!=ANIMATION_TUNER)
                    .anyMatch(key->SkillBindings.mouse(key,event.getButton()));
            if(mapped){handleKeyPress(minecraft,key->SkillBindings.mouse(key,event.getButton()));event.setCanceled(true);return;}
        }
        if(ClientBlockThrowInput.mouseEdge(event.getButton(),event.getAction())){
            event.setCanceled(true);return;
        }
        var state = minecraft.player.getData(ModAttachments.PLAYER_STATE);
        if(ClientWeaponCombat.active()&&(SkillBindings.mouse(minecraft.options.keyAttack,event.getButton())||SkillBindings.mouse(minecraft.options.keyUse,event.getButton()))){
            event.setCanceled(true);ClientWeaponCombat.poll();return;
        }
        if (SkillBindings.mouse(minecraft.options.keyAttack,event.getButton())
                && event.getAction() == GLFW.GLFW_PRESS) {
            BlacklightNeoMod.LOGGER.debug(
                    "Observed physical left press infected={} prototype={} power={} ground={}",
                    state.isInfected(), state.isPrototypeMode(), state.getActivePower().id(),
                    minecraft.player.onGround());
        }
        if(state.isPrototypeMode() && minecraft.player.getData(ModAttachments.CONSUMING)
                && (SkillBindings.mouse(minecraft.options.keyAttack,event.getButton())||SkillBindings.mouse(minecraft.options.keyUse,event.getButton()))) {
            event.setCanceled(true);tickGrabButtons();return;
        }
        if (!state.isInfected() || !state.isPrototypeMode()
                || state.getActivePower() != PowerForm.CLAWS) return;

        if (SkillBindings.mouse(minecraft.options.keyAttack,event.getButton())
                && (event.getAction() == GLFW.GLFW_PRESS
                || event.getAction() == GLFW.GLFW_RELEASE)) {
            // Callbacks cover short taps; tick polling covers held inputs.
            // Both paths share the edge latch so one click sends one attack.
            event.setCanceled(true);
            if (event.getAction() == GLFW.GLFW_PRESS) {
                beginLeftAttack(minecraft);
            } else {
                finishLeftAttack(minecraft);
                lastPhysicalLeftDown = false;
            }
            return;
        }

        if (SkillBindings.mouse(minecraft.options.keyUse,event.getButton())
                && (event.getAction() == GLFW.GLFW_PRESS
                || event.getAction() == GLFW.GLFW_RELEASE)) {
            // Groundspike is a one-shot planted action. RMB-down starts the authored
            // insertion and RMB-up is deliberately ignored; the server erupts exactly
            // when the hand reaches the planted marker, then the client holds that pose
            // until the spike visual completes and reverses the hand back to neutral.
            event.setCanceled(true);
            if (event.getAction() == GLFW.GLFW_PRESS) {
                if (groundSpikeHeld || groundSpikeAutoReleased || groundSpikeReversing) return;
                groundSpikeHeld = true;
                groundSpikeStartupWaitTicks = 30;
                PacketDistributor.sendToServer(new ClawActionPayload(
                        ClawActionPayload.Action.GROUND_SPIKE_CHARGE_START));
            }
            return;
        }
    }

    /** Raw callbacks and polling share the same edge latch: one click sends one intent. */
    private static void tickGrabButtons() {
        var mc=Minecraft.getInstance();if(mc.getWindow()==null)return;
        long window=mc.getWindow().getWindow();
        boolean left=SkillBindings.down(mc.options.keyAttack);
        boolean right=SkillBindings.down(mc.options.keyUse);
        if(ClientBlockThrowInput.poll(left,right)){lastGrabLeft=left;lastGrabRight=right;return;}
        if(mc.player!=null&&mc.screen==null&&mc.isWindowActive()&&mc.player.getData(ModAttachments.CONSUMING)) {
            clearLeftAttackHold();
            mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
            int stage=mc.player.getData(ModAttachments.GRAB_STAGE);
            boolean holding=com.codex.blacklight.combat.ConsumeVisual.holding(stage);
            if(right&&!lastGrabRight&&(holding||com.codex.blacklight.combat.ConsumeVisual.acquiring(stage)))
                PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.CONSUME));
            else if(left&&!lastGrabLeft&&holding)
                PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.THROW));
        }
        lastGrabLeft=left;lastGrabRight=right;
    }

    private static int deferredGrabTick=-1;
    private static void resetInputOwner(){
        var mc=Minecraft.getInstance();
        if(inputOwner==mc.player)return;
        if(inputOwner!=null){
            PlayerMotionController.pause(inputOwner,false);
        }
        inputOwner=mc.player;
        clearLeftAttackHold();
        groundSpikeHeld=groundSpikePosePaused=groundSpikeAutoReleased=groundSpikeReversing=false;
        groundSpikeEruptionTicks=groundSpikeStartupWaitTicks=0;
        deferredGrabTick=-1;lastGrabDown=physicalGrabDown();
        lastPhysicalLeftDown=lastGrabLeft=lastGrabRight=false;
    }
    /** Callback preserves short taps; polling covers keys swallowed by other input handlers. */
    private static void pollGrabPress(boolean down){
        var mc=Minecraft.getInstance();
        resetInputOwner();
        boolean pressed=down&&!lastGrabDown;
        lastGrabDown=down;
        if(!pressed||mc.player==null||mc.getConnection()==null||mc.screen!=null||!mc.isWindowActive()
                ||!mc.player.isAlive()||!com.codex.blacklight.mode.PrototypeModeSystem.isActive(mc.player))return;
        if(ClientWeaponCombat.active())deferredGrabTick=mc.player.tickCount+2;
        else sendGrab(mc);
        mc.options.keyInventory.setDown(false);
        while(mc.options.keyInventory.consumeClick()){}
    }
    private static void sendGrab(Minecraft mc){
        ClientMovementEvents.sendAttackDirection(mc);
        ClientTargetLock.sync();
        if(com.codex.blacklight.combat.BlockThrowSystem.canUse(mc.player)&&com.codex.blacklight.combat.BlockThrowSystem.held(mc.player)==null)ClientTerrainGrab.arm();
        PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.GRAB));
        ClientTerrainGrab.tick();
    }
    static boolean physicalGrabDown(){return SkillBindings.down(GRAB);}
    private static void tickDeferredGrab(){
        if(deferredGrabTick<0)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||!mc.player.isAlive()||mc.screen!=null||!mc.isWindowActive()
                ||!com.codex.blacklight.mode.PrototypeModeSystem.isActive(mc.player)){deferredGrabTick=-1;return;}
        long window=mc.getWindow().getWindow();
        if(SkillBindings.down(mc.options.keyAttack)||SkillBindings.down(mc.options.keyUse)){deferredGrabTick=-1;return;}
        if(mc.player.tickCount>=deferredGrabTick){
            deferredGrabTick=-1;
            sendGrab(mc);
        }
    }

    private static void toggleTargetLock(Minecraft minecraft) {
        ClientTargetLock.toggle();
    }

    /** Tab belongs to lock-on while Prototype mode is active, not the player-list overlay. */
    private static void suppressPrototypePlayerList() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        var state = minecraft.player.getData(ModAttachments.PLAYER_STATE);
        if (state.isPrototypeMode() && TARGET_LOCK.isDown()) {
            minecraft.options.keyPlayerList.setDown(false);
        }
    }

    /** Used by local sprint prediction so the planted action wins immediately. */
    static boolean isGroundSpikeActionActive() {
        return groundSpikeHeld || groundSpikeAutoReleased || groundSpikeReversing;
    }

    /**
     * Polls GLFW directly so attacks remain reliable while WALK/RUN motion and Epic
     * Fight's own input engine are active. Also classifies air press, ground tap and hold.
     */
    private static void tickPhysicalLeftAttack() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean physicallyDown = minecraft.getWindow() != null
                && SkillBindings.down(minecraft.options.keyAttack);
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            clearLeftAttackHold();
            lastPhysicalLeftDown = physicallyDown;
            return;
        }
        var state = minecraft.player.getData(ModAttachments.PLAYER_STATE);
        if (ClientWeaponCombat.active() || minecraft.player.getData(ModAttachments.CONSUMING) || !state.isInfected() || !state.isPrototypeMode()
                || state.getActivePower() != PowerForm.CLAWS) {
            clearLeftAttackHold();
            lastPhysicalLeftDown = physicallyDown;
            return;
        }

        if (physicallyDown && !lastPhysicalLeftDown) {
            beginLeftAttack(minecraft);
        }

        if (physicallyDown && leftAttackHeld && !leftAttackConsumed
                && minecraft.player.onGround()
                && ++leftAttackHoldTicks >= DASHING_SLICE_HOLD_TICKS) {
            leftAttackConsumed = true;
            PacketDistributor.sendToServer(new ClawActionPayload(
                    ClawActionPayload.Action.DASHING_SLICE));
        }

        if (!physicallyDown && lastPhysicalLeftDown) {
            finishLeftAttack(minecraft);
        }
        lastPhysicalLeftDown = physicallyDown;
    }

    private static void beginLeftAttack(Minecraft minecraft) {
        if (leftAttackHeld) return;
        clearLeftAttackHold();
        leftAttackHeld = true;
        lastPhysicalLeftDown = true;
        if (!minecraft.player.onGround()) {
            leftAttackConsumed = true;
            ClientMovementEvents.requestAirDashFromAttack(minecraft);
            return;
        }
        // While sprinting, hold the tap in a short charge window instead of sending
        // it immediately: a long press then goes STRAIGHT into the dashing slice
        // with no normal swing telegraphed first, and a quick release still fires
        // the regular chain attack.
        if (ClientMovementEvents.isRunningForAttack(minecraft)) {
            leftAttackTapSent = false;
            return;
        }
        // Send the visible attack on the press edge. Long-hold recognition remains
        // active and can blend this startup into the upgraded dashing slice later.
        sendTapAttack(minecraft);
        leftAttackTapSent = true;
    }

    private static void finishLeftAttack(Minecraft minecraft) {
        if (leftAttackHeld && !leftAttackConsumed && !leftAttackTapSent) {
            sendTapAttack(minecraft);
        }
        clearLeftAttackHold();
    }

    private static void clearLeftAttackHold() {
        leftAttackHeld = false;
        leftAttackConsumed = false;
        leftAttackTapSent = false;
        leftAttackHoldTicks = 0;
    }

    private static void sendTapAttack(Minecraft minecraft) {
        // Walking and sprinting use the same full-body three-hit chain. The old
        // SPRINT_ATTACK branch selected an empty composite placeholder while running,
        // which made the accepted click look like no attack at all. Long LMB still
        // upgrades this startup into DASHING_SLICE after the hold threshold.
        PacketDistributor.sendToServer(new ClawActionPayload(
                ClawActionPayload.Action.ATTACK));
    }

    /** Hold the own motion clock at the authored right-claw contact marker. */
    private static void holdGroundSpikePlantPose() {
        if(groundSpikeAutoReleased){tickGroundSpikeEruptionHold();return;}
        if(groundSpikeReversing){tickGroundSpikeReverse();return;}
        if(!groundSpikeHeld){releaseGroundSpikePlantPose();return;}
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){clearGroundSpikePlaybackState(mc);return;}
        var state=mc.player.getData(ModAttachments.PLAYER_STATE);
        if(!state.isInfected()||!state.isPrototypeMode()||state.getActivePower()!=PowerForm.CLAWS){
            clearGroundSpikePlaybackState(mc);return;
        }
        var velocity=mc.player.getDeltaMovement();mc.player.setDeltaMovement(0,velocity.y,0);
        if(!groundSpikePlaying(mc)) {
            if(groundSpikeStartupWaitTicks--<=0)clearGroundSpikePlaybackState(mc);
            return;
        }
        groundSpikeStartupWaitTicks=0;
        if(PlayerMotionController.elapsed(mc.player)>=GROUND_SPIKE_PLANT_TIME-GROUND_SPIKE_END_CAPTURE_WINDOW){
            PlayerMotionController.seek(mc.player,GROUND_SPIKE_PLANT_TIME);
            PlayerMotionController.pause(mc.player,true);
            groundSpikePosePaused=groundSpikeAutoReleased=true;
            groundSpikeEruptionTicks=GroundSpikeEntity.ERUPTION_DURATION_TICKS;
        }
    }
    private static void tickGroundSpikeEruptionHold() {
        var mc=Minecraft.getInstance();
        if(!groundSpikePlaying(mc)){clearGroundSpikePlaybackState(mc);return;}
        PlayerMotionController.seek(mc.player,GROUND_SPIKE_PLANT_TIME);
        PlayerMotionController.pause(mc.player,true);
        if(groundSpikeEruptionTicks-->0)return;
        groundSpikeAutoReleased=false;groundSpikeReversing=true;groundSpikePosePaused=false;
        PlayerMotionController.reverse(mc.player,true);
        PlayerMotionController.pause(mc.player,false);
    }
    private static void tickGroundSpikeReverse() {
        var mc=Minecraft.getInstance();
        if(!groundSpikePlaying(mc)){clearGroundSpikePlaybackState(mc);return;}
        if(PlayerMotionController.elapsed(mc.player)>.001F&&!PlayerMotionController.ended(mc.player))return;
        PlayerMotionController.reverse(mc.player,false);
        PlayerMotionController.clear(mc.player);
        groundSpikeReversing=groundSpikeHeld=false;
    }
    private static void releaseGroundSpikePlantPose() {
        if(!groundSpikePosePaused)return;
        var player=Minecraft.getInstance().player;
        if(player!=null)PlayerMotionController.pause(player,false);
        groundSpikePosePaused=false;
    }
    private static void clearGroundSpikePlaybackState(Minecraft mc) {
        if(groundSpikePlaying(mc)){PlayerMotionController.pause(mc.player,false);PlayerMotionController.reverse(mc.player,false);}
        groundSpikeHeld=groundSpikePosePaused=groundSpikeAutoReleased=groundSpikeReversing=false;
        groundSpikeEruptionTicks=groundSpikeStartupWaitTicks=0;
    }
    private static boolean groundSpikePlaying(Minecraft mc) {
        return mc.player!=null&&PlayerMotionController.isPlaying(mc.player,PlayerAction.CLAW_GROUNDSPIKE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        var state = minecraft.player.getData(ModAttachments.PLAYER_STATE);
        if(ClientWeaponCombat.active()) {event.setSwingHand(false);event.setCanceled(true);ClientWeaponCombat.poll();return;}
        if(state.isPrototypeMode()&&minecraft.player.getData(ModAttachments.CONSUMING)) {
            event.setSwingHand(false);event.setCanceled(true);tickGrabButtons();return;
        }
        if (!state.isInfected() || !state.isPrototypeMode()
                || state.getActivePower() != PowerForm.CLAWS) return;

        if (event.isUseItem()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (!event.isAttack()) return;

        event.setSwingHand(false);
        event.setCanceled(true);
        // Key mappings provide a remapped-input fallback; physical mouse edges
        // and mapping events share the same attack latch.
        beginLeftAttack(minecraft);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !PrototypeSurvivalLock.isLocked(minecraft.player)
                || !(event.getNewScreen() instanceof AbstractContainerScreen<?>)
                && !(event.getNewScreen() instanceof InventoryScreen)) return;
        event.setCanceled(true);
        if (!GRAB.isDown()) PrototypeSurvivalLock.notifyClient(minecraft.player);
    }

}
