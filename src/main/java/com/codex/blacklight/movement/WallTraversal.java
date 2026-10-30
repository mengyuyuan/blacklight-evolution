package com.codex.blacklight.movement;

/** Wall-local traversal shared by server authority and client prediction. Units: blocks/tick. */
public final class WallTraversal {
    public enum Mode { NONE, CLING, CLIMB, RUN, LEAP, MANTLE, VAULT, TOP, PARKOUR }
    public record Input(int forward, int sideways, boolean sprint, boolean jump, boolean detach) {
        public Input { forward = Integer.signum(forward); sideways = Integer.signum(sideways); }
        public boolean moving() { return forward != 0 || sideways != 0; }
    }
    public record Surface(boolean torso, boolean lower, boolean blockedAbove,
                          double normalX, double normalZ, double approachDot,
                          boolean grounded, boolean centerSupported, double ledgeY) {
        public Surface(boolean torso, boolean lower, boolean blockedAbove,
                       double normalX, double normalZ, double approachDot) {
            this(torso, lower, blockedAbove, normalX, normalZ, approachDot, false, false, Double.NaN);
        }
    }
    public record Frame(Mode mode, boolean handled, double x, double y, double z) { }

    private Mode mode = Mode.NONE;
    private double normalX, normalZ, lastY = Double.NaN, runSpeed;
    private int leapTicks, arcTicks, stallTicks, detachTicks;
    private boolean previousJump;
    private int chargeTicks;
    private double leapCharge;
    private double mantleTop = Double.NaN, carrySpeed;
    private boolean finishedOnTop;
    public int chargeTicks(){return chargeTicks;}
    public int visualChargeTicks(){return Math.round(chargeTicks/20F*MovementTuningSettings.jumpChargeTicks());}

    public Mode mode() { return mode; }
    public boolean attached() { return mode != Mode.NONE; }
    public double normalX() { return normalX; }
    public double normalZ() { return normalZ; }
    public boolean finishedOnTop() { return finishedOnTop; }
    public static boolean crossingTop(Mode mode) {
        return mode == Mode.MANTLE || mode == Mode.VAULT || mode == Mode.TOP || mode == Mode.PARKOUR;
    }

    public Frame tick(Input input, Surface surface, double y, double incomingSpeed,
                      double configuredRun, double upgrade) {
        finishedOnTop = false;
        boolean jumpPressed = input.jump() && !previousJump;
        boolean jumpReleased = !input.jump() && previousJump;
        previousJump = input.jump();
        if (input.detach()) {
            boolean wasAttached = attached();
            double x = normalX * .18, z = normalZ * .18;
            clearSurface(); detachTicks = 8;
            return new Frame(Mode.NONE, wasAttached, x, -.08, z);
        }
        if (detachTicks > 0) { detachTicks--; return idle(); }
        if (crossingTop(mode)) {
            return finishMantle(input, surface, y);
        }
        boolean wasAttached = attached();
        if (!wasAttached && (!surface.torso() || input.forward() <= 0 || surface.approachDot() > -.28)) return idle();
        if (surface.torso() || surface.lower()) {
            double length = Math.hypot(surface.normalX(), surface.normalZ());
            if (!Double.isFinite(length) || length < .001) { clearSurface(); return idle(); }
            normalX = surface.normalX()/length; normalZ = surface.normalZ()/length;
        }
        boolean reachableTop = Double.isFinite(surface.ledgeY());
        boolean lowObstacle = !wasAttached && surface.grounded() && input.sprint() && !input.jump()
                && reachableTop && surface.ledgeY() > y + 1.0 && surface.ledgeY() <= y + 2.05;
        boolean wallTop = wasAttached && !surface.torso() && reachableTop
                && surface.ledgeY() >= y - 1.5 && surface.ledgeY() <= y + .85
                && (input.forward() > 0 || mode == Mode.LEAP);
        if (lowObstacle || wallTop) {
            mode = lowObstacle ? Mode.PARKOUR : input.sprint() ? Mode.TOP : Mode.MANTLE;
            arcTicks = leapTicks = stallTicks = chargeTicks = 0;
            mantleTop = surface.ledgeY();
            carrySpeed = input.sprint() ? com.codex.blacklight.forge.WingMath.clamp(Math.max(incomingSpeed, runSpeed * .75), .40, .90) : .24;
            return finishMantle(input, surface, y);
        }
        if (!surface.torso() && !surface.lower()) {
            if (mode == Mode.LEAP && leapTicks > 0) return leap();
            clearSurface(); return idle();
        }
        double multiplier = Double.isFinite(upgrade) ? com.codex.blacklight.forge.WingMath.clamp(upgrade, .25, 3) : 1;
        double target = Math.min(1.4, WallRunPhysics.climbVelocity(configuredRun,
                Math.max(incomingSpeed, runSpeed)) * 1.2 * multiplier);
        runSpeed = wasAttached ? runSpeed + com.codex.blacklight.forge.WingMath.clamp(target-runSpeed, -.10, .10) : target;
        if (jumpPressed || !wasAttached&&input.jump()) chargeTicks=1;
        else if(input.jump()&&chargeTicks>0)chargeTicks=Math.min(20,chargeTicks+1);
        if (jumpReleased && chargeTicks>0) {
            leapCharge=chargeTicks/20.0;chargeTicks=0;
            mode = Mode.LEAP; leapTicks = 8; stallTicks = 0;
            return leap();
        }
        if (mode == Mode.LEAP && leapTicks > 0) return leap();
        mode = !input.moving() ? Mode.CLING : input.sprint() ? Mode.RUN : Mode.CLIMB;
        double speed = mode == Mode.RUN ? runSpeed : Math.min(.22, Math.max(.08, configuredRun*.32)*multiplier);
        double length = Math.max(1, Math.hypot(input.forward(),input.sideways()));
        double vertical = input.forward()*speed/length;
        double lateral = input.sideways()*speed/length;
        boolean stuck = vertical > .05 && surface.blockedAbove() && Double.isFinite(lastY) && y-lastY < .02;
        stallTicks = stuck ? stallTicks+1 : 0; lastY = y;
        if (stallTicks >= 3) {
            mode = Mode.VAULT; arcTicks = 0; stallTicks = 0;
            mantleTop = Double.NaN;
            carrySpeed = input.sprint() ? com.codex.blacklight.forge.WingMath.clamp(runSpeed * .75, .40, .90) : .24;
            return finishMantle(input, surface, y);
        }
        return frame(.055, vertical, lateral);
    }

    private Frame finishMantle(Input input, Surface surface, double y) {
        // Finish when the center of the body has genuinely landed on the platform.
        // A timeout only releases a failed/stale route; it never declares a landing.
        if (Double.isFinite(mantleTop) && surface.grounded() && surface.centerSupported()
                && Math.abs(y - mantleTop) < .18) {
            clearSurface();
            finishedOnTop = true;
            return idle();
        }
        if (++arcTicks > 20) {
            clearSurface(); detachTicks = 4;
            return idle();
        }
        if (mode == Mode.VAULT && arcTicks <= 3) return frame(-.32, .72, 0);
        if (!Double.isFinite(mantleTop) && Double.isFinite(surface.ledgeY())) mantleTop = surface.ledgeY();
        double rise = Double.isFinite(mantleTop) ? mantleTop + .06 - y : 0;
        boolean feetBelowLip = Double.isFinite(mantleTop) && y < mantleTop + .02;
        if (feetBelowLip && surface.blockedAbove()) {
            clearSurface(); detachTicks = 4;
            return idle();
        }
        double vertical = feetBelowLip ? com.codex.blacklight.forge.WingMath.clamp(rise * .7, .08, .55) : -.10;
        double inward = input.forward() > 0 ? carrySpeed : 0;
        // Normal movement collision clips inward travel until the feet clear the lip.
        return frame(inward, vertical, 0);
    }

    private Frame leap() {
        mode = Mode.LEAP;
        double vertical = Math.max(runSpeed, .98 + leapCharge*.65 + runSpeed*.20 - (8-leapTicks)*.065);
        leapTicks--;
        return frame(.055, vertical, 0);
    }
    private Frame frame(double inward, double vertical, double lateral) {
        // Looking into the wall: D follows its right tangent, W goes upward.
        return new Frame(mode, true, -normalX*inward + normalZ*lateral,
                vertical, -normalZ*inward - normalX*lateral);
    }
    private Frame idle() { return new Frame(Mode.NONE, false, 0, 0, 0); }
    private void clearSurface() {
        mode=Mode.NONE;normalX=normalZ=runSpeed=0;lastY=Double.NaN;leapTicks=arcTicks=stallTicks=chargeTicks=0;leapCharge=0;
        mantleTop=Double.NaN;carrySpeed=0;
    }
    public void reset() { clearSurface();previousJump=false;detachTicks=0;finishedOnTop=false; }
}
