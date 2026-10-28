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
