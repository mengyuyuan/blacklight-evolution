package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.screen.MovementTunerScreen;
import com.codex.blacklight.animation.PlayerAction;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.movement.JumpInputState;
import com.codex.blacklight.movement.MovementTuningSettings;
import com.codex.blacklight.movement.PrototypeLocomotion;
import com.codex.blacklight.movement.WallTraversal;
import com.codex.blacklight.movement.WallContacts;
import com.codex.blacklight.movement.WaterRunning;
import com.codex.blacklight.network.MovementInputPayload;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import com.codex.blacklight.forge.PacketDistributor;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMovementEvents {
    private static final String CATEGORY = "key.categories.blacklight";
    private static final KeyMapping ENGINE_TUNER = new KeyMapping(
            "key.blacklight.engine_tuner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);
    private static final KeyMapping DODGE = new KeyMapping(
            "key.blacklight.dodge", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    private static boolean lastJumpHeld;
    private static final com.codex.blacklight.movement.SprintTap DODGE_TAP=new com.codex.blacklight.movement.SprintTap();
    private static boolean lastForwardHeld;
    private static boolean lastMovingHeld;
    private static boolean lastSprinting;
    private static boolean lastChargingAnimation;
    private static int clientChargeTicks;
    private static boolean runningCharge;
    private static int clientOffGroundTicks;
    private static final JumpInputState JUMP_INPUT = new JumpInputState();
    private static boolean wasPrototype;
    private static boolean wasHoldingFinalChargePose;
    private static final WallTraversal CLIENT_WALL = new WallTraversal();
    private static int lastForwardInput, lastSidewaysInput;
    private static boolean lastDetach, lastSprintKey;
    private static float lastCameraYaw;
    private static net.minecraft.world.entity.player.Player weaponMovementOwner;
    private static com.codex.blacklight.combat.WeaponVisual ownedWeaponVisual;
    private static net.minecraft.world.entity.player.Player pendingWeaponOwner;
    private static int pendingWeaponUntil;
    private static net.minecraft.client.player.LocalPlayer inputOwner;


    private ClientMovementEvents() { }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
        if (DeveloperTools.enabled()) event.register(ENGINE_TUNER);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void lockWeaponMovement(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if(com.codex.blacklight.city.HydraTransport.active(event.getEntity())){clearWeaponTravelInput(event.getInput());return;}
        var hop=com.codex.blacklight.registry.ModAttachments.getData(event.getEntity(),ModAttachments.PLAYER_ACTION);
        if(hop.clipId().equals("basic_dodge_roll")&&hop.active(event.getEntity().level().getGameTime())
                &&hop.seconds(event.getEntity().level().getGameTime())<.7F){clearWeaponTravelInput(event.getInput());return;}
        if(com.codex.blacklight.movement.WingFlightSystem.eligible(event.getEntity())){
            // Suppress vanilla jumping, but still apply weapon ownership below.
            event.getInput().jumping=false;
        }
        beginWeaponMovementOwnership(event.getEntity());
        if(com.codex.blacklight.registry.ModAttachments.getData(event.getEntity(), ModAttachments.WEAPON_VISUAL).spec()==null
                && !pendingWeaponMovement(event.getEntity())) {
            var mc=Minecraft.getInstance();
            if(mc.player==event.getEntity()&&isPrototype(mc)&&isChargingJump()
                    &&mappingDown(mc,mc.options.keyJump)&&mc.player.onGround()&&!CLIENT_WALL.attached()){
                // Keep physical key mappings available to charge/release and wall
                // traversal, but do not let vanilla travel slide the planted pose.
                clearWeaponTravelInput(event.getInput());
                mc.player.setDeltaMovement(0,mc.player.getDeltaMovement().y,0);
                return;
            }
            if(mc.player==event.getEntity()&&isPrototype(mc)
                    &&com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView()
                    &&(Math.abs(event.getInput().forwardImpulse)>1e-5F||Math.abs(event.getInput().leftImpulse)>1e-5F)
                    &&(com.codex.blacklight.registry.ModAttachments.getData(event.getEntity(), ModAttachments.GRAB_STAGE)==com.codex.blacklight.combat.ConsumeVisual.NONE&&!com.codex.blacklight.registry.ModAttachments.getData(event.getEntity(), ModAttachments.CONSUMING)
                        ||com.codex.blacklight.registry.ModAttachments.getData(event.getEntity(), ModAttachments.GRAB_STAGE)==com.codex.blacklight.combat.ConsumeVisual.HOLD)) {
                if(com.codex.blacklight.registry.ModAttachments.getData(event.getEntity(), ModAttachments.GRAB_STAGE)==com.codex.blacklight.combat.ConsumeVisual.HOLD) {
                    var input=event.getInput();var player=event.getEntity();
                    var step=com.codex.blacklight.movement.CarrySteering.steer(cameraYaw(mc),player.getYRot(),input.forwardImpulse,input.leftImpulse);
                    player.setYRot(step.yaw());player.setYHeadRot(step.yaw());player.yBodyRot=step.yaw();
                    input.forwardImpulse=step.forward();input.leftImpulse=step.left();
                } else event.getEntity().setYRot(cameraYaw(mc));
            }
            return;
        }
        // Suppress vanilla walking/jumping as well as our traversal prediction.
        // Physical KeyMappings remain readable so held inputs resume after the action.
        clearWeaponTravelInput(event.getInput());
    }

    private static void clearWeaponTravelInput(net.minecraft.client.player.Input input) {
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
    }

    static void sendAttackDirection(Minecraft minecraft) {
        if(minecraft.player==null||minecraft.getConnection()==null||minecraft.screen!=null)return;
        int forward=(mappingDown(minecraft,minecraft.options.keyUp)?1:0)-(mappingDown(minecraft,minecraft.options.keyDown)?1:0);
        int sideways=(mappingDown(minecraft,minecraft.options.keyRight)?1:0)-(mappingDown(minecraft,minecraft.options.keyLeft)?1:0);
        // Deliver the direction before the attack packet, even when both keys
        // were pressed between ticks. Preserve traversal's own jump/sprint edges.
        PacketDistributor.sendToServer(new MovementInputPayload(lastJumpHeld,false,forward,sideways,lastSprinting,lastDetach,cameraYaw(minecraft)));
    }

    /** Camera-relative intent is independent of action input ownership. */
    static Float movementHeading(Minecraft minecraft) {
        if(minecraft.player==null||minecraft.screen!=null)return null;
        int forward=(mappingDown(minecraft,minecraft.options.keyUp)?1:0)-(mappingDown(minecraft,minecraft.options.keyDown)?1:0);
        int right=(mappingDown(minecraft,minecraft.options.keyRight)?1:0)-(mappingDown(minecraft,minecraft.options.keyLeft)?1:0);
        return forward==0&&right==0?null:cameraYaw(minecraft)+(float)Math.toDegrees(Math.atan2(right,forward));
    }

    private static float cameraYaw(Minecraft mc) {
        if(com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView())
            return com.codex.blacklight.client.camera.PrototypeChaseCamera.controlYaw();
        return mc.player.getYRot();
    }

    static boolean prepareGroundedWeaponMovement(Minecraft minecraft) {
        var player=minecraft.player;
        if(player==null||pendingWeaponMovement(player))return false;
        // Cover the short interval before the authoritative visual arrives. A
        // rejected request expires instead of trapping the player in a local lock.
        pendingWeaponOwner=player;pendingWeaponUntil=player.tickCount+6;
        player.setDeltaMovement(0,player.getDeltaMovement().y,0);
        player.xxa=0;player.zza=0;
        clearWeaponTravelInput(player.input);
        return true;
    }

    private static boolean pendingWeaponMovement(net.minecraft.world.entity.player.Player player) {
        if(pendingWeaponOwner!=player)return false;
        if(player.tickCount>=pendingWeaponUntil||!player.isAlive()
                ||!com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).isPrototypeMode()
                ||com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).getActivePower()!=com.codex.blacklight.power.PowerForm.BLADE
                ||com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL).spec()!=null){
            pendingWeaponOwner=null;
            return false;
        }
        return true;
    }

    private static void beginWeaponMovementOwnership(net.minecraft.world.entity.player.Player player) {
        var visual=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL);
        var move=visual.spec();
        if(move==null){weaponMovementOwner=null;ownedWeaponVisual=null;return;}
        pendingWeaponOwner=null;
        if(weaponMovementOwner==player&&visual.equals(ownedWeaponVisual))return;
        boolean sameAction=weaponMovementOwner==player&&ownedWeaponVisual!=null
                &&visual.move()==ownedWeaponVisual.move()&&visual.started()==ownedWeaponVisual.started()
                &&visual.charging()==ownedWeaponVisual.charging()&&visual.clipOffset()==ownedWeaponVisual.clipOffset();
        weaponMovementOwner=player;ownedWeaponVisual=visual;
        // Render/contact yaw belongs to the action. Never feed its updates back
        // into the camera, or mouse steering fights the authoritative body yaw.
        player.yBodyRot=visual.yaw();
        if(!sameAction&&player.onGround()&&(com.codex.blacklight.combat.LocalWeaponMotion.find(move.id).isPresent()
                ||com.codex.blacklight.combat.WeaponMotionProfile.forMove(move)!=null)){
            // Claim pre-action running inertia once, before vanilla travel. Later
            // knockback and airborne actions keep their own deliberate velocity.
            player.setDeltaMovement(0,player.getDeltaMovement().y,0);
            player.xxa=0;player.zza=0;
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || minecraft.player == null) return;
        if (DeveloperTools.enabled() && ENGINE_TUNER.matches(event.getKey(), event.getScanCode())) {
            if (!isPrototype(minecraft)) return;
            if (minecraft.screen == null && canTuneMovement(minecraft)) {
                minecraft.setScreen(new MovementTunerScreen());
            } else if (minecraft.screen == null) {
                minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.blacklight.tuner_singleplayer_only"), true);
            }
            return;
        }
    }

    /** Same collision probes and wall-local physics as the authoritative controller. */
    private static boolean predictWallRun(Minecraft minecraft, int forward, int sideways,
                                          boolean sprinting, boolean jump, boolean detach) {
        var player=minecraft.player;
        if (player==null || minecraft.level==null || player.isPassenger() || player.isFallFlying()
                || player.getAbilities().flying || player.isSpectator() || !player.isAlive()
                || player.isInWater() || player.isInLava() || ClientClawEvents.isGroundSpikeActionActive()) {
            resetClientWallRun();return false;
        }
        if(com.codex.blacklight.combat.ShieldBorerSystem.groundDrive(player,sprinting,forward)){
            resetClientWallRun();return false;
        }
        var frame=CLIENT_WALL.tick(new WallTraversal.Input(forward,sideways,sprinting,jump,detach),
                WallContacts.sample(player,CLIENT_WALL),player.getY(),player.getDeltaMovement().horizontalDistance(),
                MovementTuningSettings.wallClimbVelocity(),MovementUpgrade.wallMultiplier(
                        com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).getMovementUpgradeMask()));
        com.codex.blacklight.movement.WallPoseState.set(player,CLIENT_WALL.mode(),CLIENT_WALL.normalX(),CLIENT_WALL.normalZ());
        if (!frame.handled()) return false;
        if (jump) JUMP_INPUT.claimCharge();
        player.setDeltaMovement(frame.x(),frame.y(),frame.z());player.setOnGround(false);player.resetFallDistance();
        player.setSprinting(sprinting && (frame.mode()==WallTraversal.Mode.RUN
                || frame.mode()==WallTraversal.Mode.LEAP || WallTraversal.crossingTop(frame.mode())));
        if (lastChargingAnimation) {
            lastChargingAnimation=false;clientChargeTicks=0;releaseFinalChargedJumpPose(minecraft);
        }
        return true;
    }

    static WallTraversal.Mode wallMode() { return CLIENT_WALL.mode(); }
    static boolean isWallRunning() { return CLIENT_WALL.attached(); }
    private static void resetClientWallRun() {
        var player=Minecraft.getInstance().player;
        if(player!=null)com.codex.blacklight.movement.WallPoseState.set(player,WallTraversal.Mode.NONE,0,0);
        CLIENT_WALL.reset();lastForwardInput=lastSidewaysInput=0;lastDetach=lastSprintKey=false;
    }

    /** Airborne claw left-click entry point owned by ClientClawEvents. */
    static void requestAirDashFromAttack(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null
                || minecraft.player.onGround() || !isPrototype(minecraft)
                || weaponOwnsMovement(minecraft)) return;
        PacketDistributor.sendToServer(new MovementInputPayload(
                mappingDown(minecraft, minecraft.options.keyJump), true,
                lastForwardInput, lastSidewaysInput, lastSprinting, lastDetach,cameraYaw(minecraft)));
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        Minecraft minecraft = Minecraft.getInstance();
        // Consume queued clicks even in menus or another form; never dodge later from a stale press.
        boolean directDodge=false;
        while(DODGE.consumeClick()) directDodge=true;
        if(minecraft.player!=null&&(com.codex.blacklight.disguise.DisguiseSystem.active(minecraft.player)||com.codex.blacklight.power.BiomassSystem.active(minecraft.player))){DODGE_TAP.reset();JUMP_INPUT.reset();lastJumpHeld=false;clientChargeTicks=0;resetClientWallRun();return;}
        // Respawn can replace LocalPlayer without a tick in which player is null.
        // Charge/wall latches belong to the old entity, never the next life.
        if (inputOwner != minecraft.player || minecraft.player == null || minecraft.getConnection() == null) {
            if(inputOwner!=null){
                if(PlayerMotionController.isPlaying(inputOwner,PlayerAction.CHARGED_JUMP))
                    PlayerMotionController.pause(inputOwner,false);
            }
            inputOwner=minecraft.player;
            DODGE_TAP.reset();directDodge=false;
            weaponMovementOwner=null;ownedWeaponVisual=null;pendingWeaponOwner=null;
            lastJumpHeld = false;
            lastForwardHeld = false;
            lastMovingHeld = false;
            lastSprinting = false;
            lastChargingAnimation = false;
            clientChargeTicks = 0;
            clientOffGroundTicks = 0;
            JUMP_INPUT.reset();
            PrototypeAnimationController.tickLevel(minecraft, false, false, false);
            wasPrototype = false;
            wasHoldingFinalChargePose = false;
            runningCharge = false;
            lastCameraYaw = Float.NaN;
            resetClientWallRun();
            if (minecraft.player == null || minecraft.getConnection() == null) return;
        }
        if(ClientWingFlight.tick(minecraft)){
            DODGE_TAP.reset();
            lastChargingAnimation=false;clientChargeTicks=0;JUMP_INPUT.reset();
            releaseFinalChargedJumpPose(minecraft);resetClientWallRun();return;
        }
        // Hardware state and remapped KeyMappings share one input latch so visual
        // ownership does not lose releases during a combat or traversal action.
        if (com.codex.blacklight.registry.ModAttachments.getData(minecraft.player,ModAttachments.CONSUMING)&&com.codex.blacklight.combat.ConsumeVisual.blocksInput(com.codex.blacklight.registry.ModAttachments.getData(minecraft.player,ModAttachments.GRAB_STAGE))) {
            DODGE_TAP.reset();
            lastJumpHeld=false;lastForwardHeld=false;lastMovingHeld=false;lastSprinting=false;
            lastChargingAnimation=false;clientChargeTicks=0;
            clientOffGroundTicks=0;JUMP_INPUT.reset();
            resetClientWallRun();
            if(com.codex.blacklight.combat.ConsumeVisual.blocksTravel(com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.GRAB_STAGE)))
                minecraft.player.setDeltaMovement(0, minecraft.player.getDeltaMovement().y, 0);
            return;
        }
        boolean prototype = isPrototype(minecraft);
        var hop=com.codex.blacklight.registry.ModAttachments.getData(minecraft.player,ModAttachments.PLAYER_ACTION);
        boolean dodging=hop.clipId().equals("basic_dodge_roll")&&hop.active(minecraft.level.getGameTime());
        boolean jumpHeld = minecraft.screen == null && mappingDown(minecraft, minecraft.options.keyJump)&&com.codex.blacklight.registry.ModAttachments.getData(minecraft.player,ModAttachments.GRAB_STAGE)!=com.codex.blacklight.combat.ConsumeVisual.THROW;
        int forwardInput=minecraft.screen==null ? (mappingDown(minecraft,minecraft.options.keyUp)?1:0)-(mappingDown(minecraft,minecraft.options.keyDown)?1:0) : 0;
        int sidewaysInput=minecraft.screen==null ? (mappingDown(minecraft,minecraft.options.keyRight)?1:0)-(mappingDown(minecraft,minecraft.options.keyLeft)?1:0) : 0;
        boolean forwardHeld=forwardInput>0;
        boolean movingHeld=forwardInput!=0 || sidewaysInput!=0;
        boolean sprintKey=minecraft.screen==null && mappingDown(minecraft,minecraft.options.keySprint);
        boolean dodgeEligible=prototype&&minecraft.screen==null&&minecraft.player.onGround()&&!CLIENT_WALL.attached()
                &&!dodging&&!jumpHeld&&!weaponOwnsMovement(minecraft)&&com.codex.blacklight.registry.ModAttachments.getData(minecraft.player,ModAttachments.GRAB_STAGE)==0;
        boolean sprintDodge=DODGE_TAP.update(sprintKey,dodgeEligible);
        boolean dodgePressed=dodgeEligible&&(directDodge||sprintDodge);
        boolean detach=minecraft.screen==null && mappingDown(minecraft,minecraft.options.keyShift);
        // Double-tap momentum can enter the wall. Releasing W stops it; releasing
        // an explicitly held sprint key returns to the slower climbing mode.
        boolean sprinting=minecraft.screen==null && !detach && (CLIENT_WALL.attached()
                ? movingHeld && (sprintKey || (lastSprinting && !lastSprintKey))
                : physicalSprint(minecraft) || (forwardHeld && lastSprinting));
        // The server also suspends traversal in fluids. Cancel the local charge,
        // including its frozen pose, and resend held inputs when leaving the water.
        if (!prototype || minecraft.player.isInWater() && !WaterRunning.onSurface(minecraft.player) || minecraft.player.isInLava()) {
            releaseFinalChargedJumpPose(minecraft);
            lastJumpHeld = false;
            lastForwardHeld = false;
            lastMovingHeld = false;
            lastSprinting = false;
            lastChargingAnimation = false;
            clientChargeTicks = 0;
            runningCharge = false;
            clientOffGroundTicks = 0;
            JUMP_INPUT.reset();
            PrototypeAnimationController.tickLevel(minecraft, false, false, false);
            wasPrototype = false;
            resetClientWallRun();
            var movement=com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.MOVEMENT_STATE);
            movement.clearSprintDrive();
            if(prototype&&minecraft.player.isInWater()&&!minecraft.player.isInLava()) {
                float yaw=cameraYaw(minecraft);
                movement.setCameraYaw(yaw);
                movement.acceptInput(false,false,forwardInput,sidewaysInput,sprinting,detach);
                PacketDistributor.sendToServer(new MovementInputPayload(false,false,
                        forwardInput,sidewaysInput,sprinting,detach,yaw));
                WaterRunning.riseToSurface(minecraft.player);
            }
            return;
        }
        if (minecraft.player.onGround()) clientOffGroundTicks = 0;
        else clientOffGroundTicks++;
        JUMP_INPUT.acceptInput(jumpHeld, minecraft.player, CLIENT_WALL.attached());
        JUMP_INPUT.observeGround(minecraft.player.onGround());
        boolean weaponAction = weaponOwnsMovement(minecraft);
        if (weaponAction) {
            JUMP_INPUT.cancelPendingCharge();
            beginWeaponMovementOwnership(minecraft.player);
            lastChargingAnimation = false;
            clientChargeTicks = 0;
            releaseFinalChargedJumpPose(minecraft);
        }
        // Latch charging across a brief terrain correction. The server authorizes
        // the action and synchronizes Blacklight's clock to observers.
        if (!weaponAction && !dodging && !lastChargingAnimation && !CLIENT_WALL.attached()
                && !minecraft.player.getAbilities().flying
                && JUMP_INPUT.consumeChargeRequest(minecraft.player)) {
            lastChargingAnimation = true;
            runningCharge=sprinting&&forwardHeld;
            clientChargeTicks = 0;
        }
        if (lastChargingAnimation) {
            if (clientOffGroundTicks > PrototypeLocomotion.CHARGE_GROUND_GRACE_TICKS) {
                lastChargingAnimation = false;
                clientChargeTicks = 0;
                releaseFinalChargedJumpPose(minecraft);
            } else if (!jumpHeld) {
                lastChargingAnimation = false;
                clientChargeTicks = 0;
                // Predict the launch using the same own clip as the server.
                playLocalLaunchAnimation(minecraft);
            } else if (minecraft.player.onGround()) {
                clientChargeTicks = Math.min(MovementTuningSettings.jumpChargeTicks(),
                        clientChargeTicks + 1);
            }
        }
        float viewYaw=cameraYaw(minecraft);
        com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.MOVEMENT_STATE).setCameraYaw(viewYaw);
        com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.MOVEMENT_STATE).acceptInput(jumpHeld,false,
                forwardInput,sidewaysInput,sprinting,detach);
        boolean inputChanged = jumpHeld != lastJumpHeld || forwardInput != lastForwardInput
                || sidewaysInput != lastSidewaysInput || detach != lastDetach || sprinting != lastSprinting;
        boolean keysChanged=inputChanged;
        inputChanged|=Math.abs(net.minecraft.util.Mth.wrapDegrees(viewYaw-lastCameraYaw))>.1F||minecraft.player.tickCount%10==0;
        if (!wasPrototype || inputChanged || dodgePressed) {
            PacketDistributor.sendToServer(new MovementInputPayload(
                    jumpHeld, dodgePressed, forwardInput, sidewaysInput, sprinting, detach,viewYaw));
            if(keysChanged||!wasPrototype)BlacklightNeoMod.LOGGER.debug(
                    "Movement input sent jump={} forward={} sprint={}",
                    jumpHeld, forwardHeld, sprinting);
        }

        lastCameraYaw=viewYaw;lastForwardInput=forwardInput;lastSidewaysInput=sidewaysInput;lastDetach=detach;lastSprintKey=sprintKey;
        lastJumpHeld = jumpHeld;
        lastForwardHeld = forwardHeld;
        lastMovingHeld = movingHeld;
        lastSprinting = sprinting;
        wasPrototype = true;
        if(dodging){
            clientChargeTicks=0;lastChargingAnimation=false;JUMP_INPUT.cancelPendingCharge();CLIENT_WALL.reset();
            PrototypeAnimationController.tickLevel(minecraft,false,false,false);return;
        }

        if (weaponAction) {
            // The input latches above continue tracking releases and sprint intent.
            // Avoid resetClientWallRun(): it would erase those input latches every
            // tick and manufacture a new edge when the weapon action finishes.
            CLIENT_WALL.reset();
            com.codex.blacklight.movement.WallPoseState.set(minecraft.player,WallTraversal.Mode.NONE,0,0);
            PrototypeAnimationController.tickLevel(minecraft, false, lastMovingHeld, lastSprinting);
            return;
        }

        // Resolve wall attachment before pose sampling to avoid an intermediate jump.
        boolean wallHandled = predictWallRun(minecraft, forwardInput, sidewaysInput, sprinting, jumpHeld, detach);

        if(!wallHandled&&minecraft.player.onGround()&&isChargingJump()&&jumpHeld)
            minecraft.player.setDeltaMovement(0,minecraft.player.getDeltaMovement().y,0);

        // Release the previous action hold before the next phase is sampled.
        updateFinalChargedJumpPose(minecraft);

        // Stabilize locomotion once per tick; the pose runtime keeps a continuing
        // stride on one clock even when terrain correction flickers for a frame.
        PrototypeAnimationController.tickLevel(minecraft, lastChargingAnimation&&!runningCharge,
                lastMovingHeld, lastSprinting);

        // Match the server acceleration locally.  Without this prediction the integrated
        // server repeatedly corrects the vanilla-speed client, which looks like a ghost
        // copy or stuttering trail behind the player.
        if (!wallHandled && !isChargingJump() && (minecraft.player.onGround() || WaterRunning.onSurface(minecraft.player)) && forwardHeld && sprinting
                && !ClientClawEvents.isGroundSpikeActionActive()) {
            minecraft.player.setSprinting(true);
            var forward = com.codex.blacklight.movement.TraversalSteering.forward(minecraft.player);
            if (forward.lengthSqr() > 1.0E-5D) {
                forward = forward.normalize();
                var velocity = minecraft.player.getDeltaMovement();
                double max = MovementTuningSettings.sprintMaxSpeed()
                        * MovementUpgrade.sprintMultiplier(com.codex.blacklight.registry.ModAttachments.getData(minecraft.player
                        , ModAttachments.PLAYER_STATE).getMovementUpgradeMask());
                if (com.codex.blacklight.power.UtilityPowerSystem.active(minecraft.player,
                        com.codex.blacklight.power.PowerForm.ARMOR)) max *= .75;
                minecraft.player.setDeltaMovement(com.codex.blacklight.movement.TraversalSteering.sprint(minecraft.player,velocity,max,
                        MovementTuningSettings.sprintAcceleration(),forwardInput,sidewaysInput));
            }
        }
    }

    /**
     * Immediate local launch pose on key release, using the same authored
     * JUMP_RISE clip that the server synchronizes.
     */
    private static void playLocalLaunchAnimation(Minecraft minecraft) {
        if(minecraft.player==null)return;
        PlayerMotionController.pause(minecraft.player,false);
        PlayerMotionController.request(minecraft.player,PlayerAction.JUMP_RISE,0);
    }
    private static void updateFinalChargedJumpPose(Minecraft minecraft) {
        // PlayerMotionController interpolates the committed charge interval.
        // Re-seeking a freely advancing override every tick produces a sawtooth
        // whenever clip duration differs from the configured charging duration.
        wasHoldingFinalChargePose=lastChargingAnimation&&!runningCharge
                &&clientChargeTicks>=Math.max(1,MovementTuningSettings.jumpChargeTicks());
    }
    private static void releaseFinalChargedJumpPose(Minecraft minecraft) {
        // ChargePoseClock holds the final sample without a mutable playback override.
        wasHoldingFinalChargePose=false;
    }

    /** Prevents the locomotion controller from replacing the latched final charge pose. */
    static boolean isHoldingFinalChargePose() {
        return wasHoldingFinalChargePose;
    }

    /** True while the local player is charging a jump, including the held full charge. */
    static boolean isChargingJump() {
        return lastChargingAnimation&&!runningCharge;
    }
    public static int visualJumpCharge(){return CLIENT_WALL.attached()?CLIENT_WALL.visualChargeTicks():lastChargingAnimation?clientChargeTicks:0;}

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getPlayer() != minecraft.player || !isPrototype(minecraft)) return;
        if(com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView()) {
            // Source camera owns optical zoom. MC movement attributes must not add a second speed FOV.
            event.setNewFovModifier(1);return;
        }
        double horizontalSpeed = minecraft.player.getDeltaMovement().horizontalDistance();
        double ratio = PrototypeLocomotion.cinematicSpeedRatio(horizontalSpeed,
                MovementTuningSettings.sprintMaxSpeed()
                        * MovementUpgrade.sprintMultiplier(com.codex.blacklight.registry.ModAttachments.getData(minecraft.player
                        , ModAttachments.PLAYER_STATE).getMovementUpgradeMask()));
        event.setNewFovModifier((float)(event.getFovModifier()
                * (1.0D + MovementTuningSettings.fovBoost() * ratio)));
    }

    /** Uses the same input ownership and terrain clearance as the server. */
    private static boolean isGliding(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.onGround()
                || weaponOwnsMovement(minecraft)
                || com.codex.blacklight.power.UtilityPowerSystem.active(minecraft.player,com.codex.blacklight.power.PowerForm.ARMOR)
                || lastChargingAnimation || CLIENT_WALL.attached()
                || ClientClawEvents.isGroundSpikeActionActive()) return false;
        return JUMP_INPUT.canGlide(minecraft.player);
    }

    private static boolean isPrototype(Minecraft minecraft) {
        return minecraft.player != null && !com.codex.blacklight.city.HydraTransport.active(minecraft.player)
                && com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.PLAYER_STATE).isInfected()
                && com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.PLAYER_STATE).isPrototypeMode();
    }

    static boolean weaponOwnsMovement(Minecraft minecraft) {
        return minecraft.player != null && !com.codex.blacklight.city.HydraTransport.active(minecraft.player)
                && (com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.WEAPON_VISUAL).spec() != null
                ||pendingWeaponMovement(minecraft.player));
    }

    /** Stable sprint intent used to choose the moving attack on mouse release. */
    static boolean isRunningForAttack(Minecraft minecraft) {
        if (minecraft.player == null || !minecraft.player.onGround()) return false;
        return (lastMovingHeld && lastSprinting)
                || minecraft.player.getDeltaMovement().horizontalDistance()
                >= PrototypeLocomotion.RUN_ANIMATION_SPEED;
    }

    private static boolean canTuneMovement(Minecraft minecraft) {
        return minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null
                && !minecraft.getSingleplayerServer().isPublished();
    }

    private static boolean physicalSprint(Minecraft minecraft) {
        return minecraft.player != null && (minecraft.player.isSprinting()
                || mappingDown(minecraft, minecraft.options.keySprint));
    }

    private static boolean mappingDown(Minecraft minecraft, KeyMapping mapping) {
        if (mapping.isDown()) return true;
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0) {
            return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), key.getValue())
                    == GLFW.GLFW_PRESS;
        }
        return false;
    }
}
