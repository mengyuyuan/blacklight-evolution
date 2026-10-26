package com.codex.blacklight.movement;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** One camera-relative basis and sprint calculation on both logical sides. */
public final class TraversalSteering {
    private TraversalSteering() {}
    public static float cameraYaw(Player player){return player.getData(ModAttachments.MOVEMENT_STATE).cameraYaw(player.getYRot());}
    public static Vec3 forward(Player player){return Vec3.directionFromRotation(0,cameraYaw(player));}
    public static Vec3 sprint(Player player,Vec3 velocity,double maximum,double acceleration,int forward,int right) {
        double evolved=com.codex.blacklight.power.InfiniteEvolutionSystem.mobility(player);maximum*=evolved;acceleration*=evolved;
        float yaw=cameraYaw(player)+(float)Math.toDegrees(Math.atan2(right,forward));
        Vec3 direction=Vec3.directionFromRotation(0,yaw);
        Vec3 horizontal=velocity.multiply(1,0,1);
        var state=player.getData(ModAttachments.MOVEMENT_STATE);
        // MC has already applied ground drag here. Accumulate the sprint motor
        // across consecutive sprint ticks, not that damped velocity. A collision,
        // interruption or zero acceleration discards the motor's old momentum.
        if(acceleration>0 && !player.horizontalCollision) {
            Vec3 drive=state.sprintDrive(player.tickCount);
            double along=Math.max(0,horizontal.dot(direction));
            double previous=Math.max(0,drive.dot(direction));
            if(previous>along)horizontal=horizontal.add(direction.scale(previous-along));
        }
        // Active sprint steering sheds sideways/reverse momentum. With zero
        // acceleration, retain inertia instead of manufacturing acceleration.
        if(acceleration>0) {
            double along=horizontal.dot(direction);
            Vec3 lateral=horizontal.subtract(direction.scale(along)).scale(.65);
            horizontal=lateral.add(direction.scale(Math.max(0,along)+acceleration));
        }
        if(horizontal.lengthSqr()>maximum*maximum)horizontal=horizontal.normalize().scale(maximum);
        if(acceleration>0)state.rememberSprintDrive(player.tickCount,horizontal);
        else state.clearSprintDrive();
        return new Vec3(horizontal.x,velocity.y,horizontal.z);
    }
}
