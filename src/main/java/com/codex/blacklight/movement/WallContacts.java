package com.codex.blacklight.movement;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Identical collision probes on authority and prediction; no client-only dependencies. */
public final class WallContacts {
    private WallContacts() { }
    public static WallTraversal.Surface sample(Player player, WallTraversal wall) {
        Vec3 look=TraversalSteering.forward(player);
        Vec3 into=wall.attached() ? new Vec3(-wall.normalX(),0,-wall.normalZ()) : look;
        BlockHitResult torso=hit(player,into,.78),lower=hit(player,into,.16);
        // Let vanilla step over a ledge before treating it as a wall. Both sides
        // must make this decision, otherwise one frame switches RUN to CLIMB.
        if (!wall.attached() && torso != null && TerrainTraversal.canStepPast(player, into,
                Math.max(.72, player.getDeltaMovement().horizontalDistance()),
                Vec3.atLowerCornerOf(torso.getDirection().getOpposite().getNormal()))) {
            torso = null;
            lower = null;
        }
        // A fast upward leap can cross both original probes in one tick. Sweep
        // below the feet by the previous ascent so the roof edge is not skipped.
        if(torso==null && lower==null && wall.attached() && player.getDeltaMovement().y>0) {
            double ascent=Math.min(1.5,player.getDeltaMovement().y);
            for(double height=-.15;height>=-ascent-.15;height-=.20) {
                lower=hit(player,into,height);
                if(lower!=null)break;
            }
        }
        BlockHitResult contact=torso!=null?torso:lower;
        Vec3 normal=contact==null?Vec3.ZERO:Vec3.atLowerCornerOf(contact.getDirection().getNormal());
        // Initial latching needs actual proximity, not a long ray through empty air.
        if (!wall.attached() && torso!=null && torso.getLocation().subtract(player.position()).horizontalDistance()>player.getBbWidth()*.5+.22) torso=null;
        boolean ceiling=!player.level().noCollision(player,player.getBoundingBox().move(0,.12,0));
        double ledgeY = Double.NaN;
        if (ceiling && wall.needsVaultProbe()) {
            ledgeY = TerrainTraversal.vaultLanding(player, into);
        } else if (wall.attached() || player.onGround() && torso != null && com.codex.blacklight.power.UtilityPowerSystem.parkourAllowed(player)) {
            Vec3 inward = wall.attached() ? into : normal.scale(-1);
            double distance = Math.max(.72, contact == null ? 0
                    : contact.getLocation().subtract(player.position()).horizontalDistance() + player.getBbWidth() * .5 + .10);
            double rise = wall.attached() ? .85 : 2.05;
            ledgeY = TerrainTraversal.ledgeHeight(player, inward, distance, rise,
                    rise + (wall.attached() ? 1.5 : .05));
        }
        return new WallTraversal.Surface(torso!=null,lower!=null,ceiling,normal.x,normal.z,look.dot(normal),
                player.onGround(), TerrainTraversal.centerSupported(player), ledgeY);
    }
    private static BlockHitResult hit(Player player, Vec3 direction, double height) {
        Vec3 start=player.position().add(0,height,0);
        BlockHitResult hit=player.level().clip(new ClipContext(start,start.add(direction.scale(MovementTuningSettings.wallDetectDistance())),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK && hit.getDirection().getAxis()!=Direction.Axis.Y?hit:null;
    }
}
