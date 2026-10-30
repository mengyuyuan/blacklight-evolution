package com.codex.blacklight.movement;

/** Visual routing only: it never moves the actor or changes traversal collision rules. */
public final class TraversalMotionSelector {
    private TraversalMotionSelector() {}
    /** Minecraft yaw zero faces +Z: the actor's right is -X. */
    public static double rightward(double x,double z,double yawDegrees){
        double yaw=Math.toRadians(yawDegrees);
        return -x*Math.cos(yaw)-z*Math.sin(yaw);
    }
    public static String ground(LocomotionPhase phase,boolean sprint,double speed){
        if(phase==LocomotionPhase.BRAKE)return sprint?"basic_sprint_brake":"basic_brake";
        if(phase==LocomotionPhase.TURN)return "basic_turn";
        if(!sprint)return speed>.22?"basic_run":"basic_walk";
        return speed>1.1?"basic_sprint_fastest":speed>.8?"basic_sprint_fast":"basic_sprint";
    }
    /** Keep small speed changes near a boundary from rebuilding the source blend. */
    public static String stableGround(LocomotionPhase phase, boolean sprint, double speed, String previous) {
        if (phase == LocomotionPhase.BRAKE || phase == LocomotionPhase.TURN || previous == null)
            return ground(phase, sprint, speed);
        if (sprint) {
            if (previous.equals("basic_sprint_fastest") && speed > 1.02) return previous;
            if (previous.equals("basic_sprint_fast") && speed > .72 && speed < 1.18) return previous;
            if (previous.equals("basic_sprint") && speed < .88) return previous;
        } else {
            if (previous.equals("basic_run") && speed > .19) return previous;
            if (previous.equals("basic_walk") && speed < .25) return previous;
        }
        return ground(phase, sprint, speed);
    }
    public static String wall(boolean running,double up,double right){
        if(!running){
            if(Math.abs(right)>Math.abs(up)*1.25)return right>0?"basic_wall_climb_right":"basic_wall_climb_left";
            return up<-.025?"basic_wall_climb_down":"basic_wall_climb_up";
        }
        if(Math.abs(right)<.06)return "basic_wall_run_up";
        if(up>Math.abs(right)*.55)return right>0?"basic_wall_run_diag_right":"basic_wall_run_diag_left";
        return right>0?"basic_wall_run_right":"basic_wall_run_left";
    }
    public static String glide(double forward,double right){
        if(Math.abs(right)>Math.abs(forward)+.08)return right>0?"basic_glide_right":"basic_glide_left";
        return forward<-.08?"basic_glide_backward":"basic_glide_forward";
    }
    public static float cadence(double speed,double nominal){
        if(!Double.isFinite(speed)||!Double.isFinite(nominal)||nominal<=0)return 1;
        return (float)com.codex.blacklight.util.MathCompat.clamp(speed/nominal,.55,8.0);
    }
}
