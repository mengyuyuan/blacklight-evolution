package com.codex.blacklight.movement;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;

/** Shared collision eligibility: water supports a running player, never a swimmer below it. */
public final class WaterRunning {
    private WaterRunning() { }

    public static boolean canRun(Player player) {
        // Spawn placement queries water collisions before the new player has a network connection.
        var state = ModAttachments.peekData(player, ModAttachments.PLAYER_STATE, null);
        if (state == null || !state.isInfected() || !state.isPrototypeMode() || !player.isAlive()
                || player.isSpectator() || player.isPassenger() || player.isFallFlying()
                || player.getAbilities().flying || player.isShiftKeyDown() || player.isInLava()) return false;
        var movement = ModAttachments.peekData(player, ModAttachments.MOVEMENT_STATE, null);
        if (!player.isSprinting() && (movement == null || !movement.sprinting() || !movement.forwardHeld())) return false;
        var weapon = ModAttachments.peekData(player, ModAttachments.WEAPON_VISUAL, null);
        if ((weapon != null && weapon.spec() != null) || ModAttachments.peekData(player, ModAttachments.CONSUMING, false)) return false;
        BlockPos pos = player.blockPosition();
        FluidState fluid = player.level().getFluidState(pos);
        // Surface support starts only after swimming/riseToSurface clears the water.
        return !fluid.is(FluidTags.WATER) || surface(player.level(), pos, fluid)
                && player.getY() >= pos.getY() + fluid.getHeight(player.level(), pos) - .025;
    }

    /** Sprint + forward swims upward, then the normal surface collision takes over.
     * No teleport, forged ground flag or collision bypass; sneak always releases it. */
    public static boolean riseToSurface(Player player) {
        var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        var movement=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.MOVEMENT_STATE);
        if(!state.isInfected() || !state.isPrototypeMode() || !player.isAlive()
                || !player.isInWater() || player.isInLava() || player.isSpectator()
                || player.isPassenger() || player.getAbilities().flying || player.isFallFlying()
                || player.isShiftKeyDown() || movement.wallInput().detach()
                || !movement.sprinting() || !movement.forwardHeld()
                || com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL).spec()!=null
                || com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.CONSUMING))return false;
        player.setSprinting(true);
        player.setSwimming(false);
        double up=.28;
        BlockPos feet=player.blockPosition();
        FluidState fluid=player.level().getFluidState(feet);
        if(surface(player.level(),feet,fluid))
            up=Math.min(up,Math.max(.035,feet.getY()+fluid.getHeight(player.level(),feet)+.035-player.getY()));
        var velocity=player.getDeltaMovement();
        var direction=net.minecraft.world.phys.Vec3.directionFromRotation(0,TraversalSteering.cameraYaw(player)
                +(float)Math.toDegrees(Math.atan2(movement.wallInput().sideways(),movement.wallInput().forward())));
        double speed=Math.min(.38,Math.max(.12,velocity.horizontalDistance()+.045));
        // Solid ceilings remain solid. Steer normally while waiting for an opening.
        if(!player.level().noCollision(player,player.getBoundingBox().move(0,up,0)))up=0;
        player.setDeltaMovement(direction.x*speed,up,direction.z*speed);
        player.resetFallDistance();
        if(!player.level().isClientSide)player.hurtMarked=true;
        return true;
    }

    public static boolean surface(BlockGetter level, BlockPos pos, FluidState fluid) {
        return fluid.is(FluidTags.WATER)
                && !(fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING))
                && !level.getFluidState(pos.above()).is(FluidTags.WATER);
    }

    public static boolean onSurface(Player player) {
        if (!canRun(player)) return false;
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : new BlockPos[]{feet, feet.below()}) {
            FluidState fluid = player.level().getFluidState(pos);
            if (surface(player.level(), pos, fluid)) {
                double gap = player.getY() - pos.getY() - fluid.getHeight(player.level(), pos);
                if (gap >= -.025 && gap <= .12) return true;
            }
        }
        return false;
    }
}
