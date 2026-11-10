package com.codex.blacklight.movement;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Collision probes shared by terrain animation and wall entry; never moves the player. */
public final class TerrainTraversal {
    private TerrainTraversal() { }

    /** Continue an existing sprint motor over a short downward terrain step. */
    public static boolean sprintSupported(Player player) {
        if (player.onGround() || WaterRunning.onSurface(player)) return true;
        if (player.isFallFlying() || player.getAbilities().flying || player.isNoGravity()
                || player.isInWater() || player.isInLava()) return false;
        var state = player.getData(com.codex.blacklight.registry.ModAttachments.MOVEMENT_STATE);
        if (state.jumpHeld() || state.sprintDrive(player.tickCount).horizontalDistanceSqr() <= 1.0e-8) return false;
        double downward = player.getDeltaMovement().y;
        return downward <= .035 && downward >= -.5 && supportBelow(player, 1.05);
    }

    /** Largest terrain drop a sprint follows down instead of launching off it. */
    public static final double STAIR_FOLLOW = 1.05, STAIR_FOLLOW_MAX = 2.05;
    public static double stairReach(double horizontalSpeed) {
        return Math.clamp(horizontalSpeed + 1.25, STAIR_FOLLOW, STAIR_FOLLOW_MAX);
    }
    /**
     * Distance to drop a sprinting player onto the step just below, or 0. At sprint speed a
     * stair run outpaces gravity, so the body sails off the steps and plays a fall. Vertical
     * collision resolves before horizontal travel, so the body can still report ground while
     * already hanging over the next step. Only bodies at most one tick away from real ground
     * contact qualify: jumps, cliffs and falls that merely pass near a ledge are never pulled.
     */
    public static double stairFollow(Player player, boolean jumpHeld, int airTicks) {
        if (airTicks > 1 || jumpHeld) return 0;
        if (player.isFallFlying() || player.getAbilities().flying || player.isNoGravity()
                || player.isPassenger() || player.isInWater() || player.isInLava()) return 0;
        double vertical = player.getDeltaMovement().y;
        if (vertical > .035 || vertical < -.5) return 0;
        // The step is found only after the body has already moved out over it, so a
        // faster sprint sits higher above the next stair. Real drops beyond this fall.
        double reach = stairReach(player.getDeltaMovement().horizontalDistance());
        Vec3 resolved = Entity.collideBoundingBox(player, new Vec3(0, -reach, 0),
                player.getBoundingBox(), player.level(), List.of());
        double drop = -resolved.y;
        return drop > 1.0e-3 && drop < reach - 1.0e-5 ? drop : 0;
    }

    public static boolean supportBelow(Player player, double distance) {
        Vec3 resolved = Entity.collideBoundingBox(player, new Vec3(0, -distance, 0),
                player.getBoundingBox(), player.level(), List.of());
        return resolved.y > -distance + 1.0e-5;
    }

    public static boolean canStepPast(Player player, Vec3 direction, double distance) {
        if (!player.onGround()) return false;
        double height = player.maxUpStep();
        var box = player.getBoundingBox();
        Vec3 up = Entity.collideBoundingBox(player, new Vec3(0, height, 0), box, player.level(), List.of());
        if (up.y < height - 1.0e-5) return false;
        Vec3 across = direction.scale(distance).multiply(1, 0, 1);
        Vec3 resolved = Entity.collideBoundingBox(player, across, box.move(0, height, 0), player.level(), List.of());
        return resolved.subtract(across).lengthSqr() < 1.0e-8;
    }

    public static boolean centerSupported(Player player) {
        var box = player.getBoundingBox().deflate(player.getBbWidth() * .3, 0, player.getBbWidth() * .3);
        return Entity.collideBoundingBox(player, new Vec3(0, -.12, 0), box,
                player.level(), List.of()).y > -.12 + 1.0e-5;
    }

    /** Find a reachable top in front of the body; no teleport or collision bypass. */
    public static double ledgeHeight(Player player, Vec3 into, double distance, double rise, double drop) {
        var box = player.getBoundingBox();
        Vec3 up = Entity.collideBoundingBox(player, new Vec3(0, rise, 0), box, player.level(), List.of());
        if (up.y < rise - 1.0e-5) return Double.NaN;
        Vec3 across = into.scale(distance).multiply(1, 0, 1);
        var raised = box.move(0, rise, 0);
        Vec3 resolved = Entity.collideBoundingBox(player, across, raised, player.level(), List.of());
        if (resolved.subtract(across).lengthSqr() > 1.0e-8) return Double.NaN;
        var landing = raised.move(across).deflate(player.getBbWidth() * .3, 0, player.getBbWidth() * .3);
        double down = Entity.collideBoundingBox(player, new Vec3(0, -drop, 0), landing,
                player.level(), List.of()).y;
        return down > -drop + 1.0e-5 ? player.getY() + rise + down : Double.NaN;
    }
}
