package com.codex.blacklight.movement;

/** Transient authoritative movement state; deliberately not persisted across logout/death. */
public final class MovementState {
    public enum Mode { NORMAL, CHARGING_JUMP, WALL_RUN, WALL_CLIMB, WALL_LEAP, GLIDE, AIR_DASH }

    private final JumpInputState jumpInput = new JumpInputState();
    public JumpInputState jumpInput() { return jumpInput; }

    private final WallTraversal wallTraversal = new WallTraversal();
    private int forwardInput, sidewaysInput;
    private float cameraYaw=Float.NaN;
    public void setCameraYaw(float yaw){cameraYaw=Float.isFinite(yaw)?net.minecraft.util.Mth.wrapDegrees(yaw):Float.NaN;}
    public float cameraYaw(float fallback){return Float.isFinite(cameraYaw)?cameraYaw:fallback;}
    private boolean detachHeld;
    public WallTraversal wallTraversal() { return wallTraversal; }
    public WallTraversal.Input wallInput() { return new WallTraversal.Input(forwardInput, sidewaysInput, sprinting, jumpHeld, detachHeld); }
    public void acceptInput(boolean jump, boolean dash, int forward, int sideways, boolean sprint, boolean detach) {
        acceptInput(jump, dash, forward > 0, sprint);
        forwardInput=Integer.signum(forward);sidewaysInput=Integer.signum(sideways);detachHeld=detach;
    }

    private boolean jumpHeld;
    private boolean previousJumpHeld;
    private boolean dashRequested;
    private boolean forwardHeld;
    private boolean sprinting;
    private net.minecraft.world.phys.Vec3 sprintDrive = net.minecraft.world.phys.Vec3.ZERO;
    private int sprintDriveTick = Integer.MIN_VALUE;
    public net.minecraft.world.phys.Vec3 sprintDrive(int tick) {
        return tick > sprintDriveTick && tick - (long)sprintDriveTick == 1 ? sprintDrive : net.minecraft.world.phys.Vec3.ZERO;
    }
    public void rememberSprintDrive(int tick, net.minecraft.world.phys.Vec3 drive) {
        sprintDriveTick=tick; sprintDrive=drive;
    }
    public void clearSprintDrive() { sprintDriveTick=Integer.MIN_VALUE;sprintDrive=net.minecraft.world.phys.Vec3.ZERO; }

    private int chargeTicks;
    private int hopCooldown,hopTicks;
    public boolean canHop(){return hopCooldown==0&&hopTicks==0;}
    /** First six server ticks of takeoff. MC tuning, not a decoded original frame count. */
    public boolean hopInvulnerable(){return hopTicks>13;}
    public void beginHop(){hopCooldown=24;hopTicks=19;cancelCharge();jumpInput.cancelPendingCharge();clearSprintDrive();}
    public boolean hopping(){return hopTicks>0;}
    public void tickHop(){if(hopCooldown>0)hopCooldown--;if(hopTicks>0)hopTicks--;}
    public void cancelHop(){hopTicks=0;}
    private boolean runningCharge;
    public boolean runningCharge(){return chargeTicks>0&&runningCharge;}
    public void beginRunningCharge(){beginCharge();runningCharge=true;}
    private int chargeOffGroundTicks;
    private int dashTicks;
    private int airDashes = 1;
    private int maxAirDashes = 1;
    private double dashDirectionX;
    private double dashDirectionZ;
    private Mode mode = Mode.NORMAL;
    private double glideSpeed;
    public boolean brakingGlide() { return forwardInput < 0; }
    public double glideSpeed() { return glideSpeed; }
    public void setGlideSpeed(double speed) { glideSpeed = speed; }

    private boolean runningAnimation;
    private boolean sprintColliding;
    private double wallNormalX;
    private double wallNormalZ;
    private boolean airborne;
    private double peakFallSpeed;
    private double airborneApexY = Double.NaN;
    private int landingRecoveryTicks;
    private LandingImpact.Strength landingStrength = LandingImpact.Strength.NONE;
    private int wallStallTicks;
    private double lastWallY = Double.NaN;
    private int wallJumpCooldownTicks;
    private int wallChargeTicks;
    private int wallVaultTicks;
    private double wallVaultDirX;
    private double wallVaultDirZ;

    /** Brief window after a wall vault/jump during which wall run cannot re-latch. */
    public void startWallJumpCooldown() { wallJumpCooldownTicks = 6; }
    public void tickWallJumpCooldown() { if (wallJumpCooldownTicks > 0) wallJumpCooldownTicks--; }
    public boolean wallJumpCoolingDown() { return wallJumpCooldownTicks > 0; }

    /** Charged wall leap: space held on the wall coils, release fires. */
    public void beginWallCharge() { wallChargeTicks = 1; }
    public void tickWallCharge(int maximum) {
        if (wallChargeTicks > 0 && wallChargeTicks < Math.max(1, maximum)) wallChargeTicks++;
    }
    public int wallChargeTicks() { return wallChargeTicks; }
    public int releaseWallCharge() { int value = wallChargeTicks; wallChargeTicks = 0; return value; }
    public void cancelWallCharge() { wallChargeTicks = 0; }

    /** Scripted overhang vault: out, up, then back in over the lip. */
    public void beginWallVault(double intoWallX, double intoWallZ) {
        wallVaultTicks = 1;
        wallVaultDirX = intoWallX;
        wallVaultDirZ = intoWallZ;
    }
    public int wallVaultTicks() { return wallVaultTicks; }
    public void tickWallVault() { if (wallVaultTicks > 0) wallVaultTicks++; }
    public void endWallVault() { wallVaultTicks = 0; }
    public double wallVaultDirX() { return wallVaultDirX; }
    public double wallVaultDirZ() { return wallVaultDirZ; }

    /**
     * Counts consecutive wall-run ticks with no real upward progress; the caller
     * turns a sustained stall into a vault over whatever ledge is blocking the climb.
     */
    public int observeWallStall(double y) {
        if (!Double.isFinite(lastWallY) || y - lastWallY > 0.02D) wallStallTicks = 0;
        else wallStallTicks++;
        lastWallY = y;
        return wallStallTicks;
    }
    public void resetWallStall() { wallStallTicks = 0; lastWallY = Double.NaN; }

    public void acceptInput(boolean jumpHeld, boolean dashPressed,
                            boolean forwardHeld, boolean sprinting) {
        this.jumpHeld = jumpHeld;
        this.dashRequested |= dashPressed;
        this.forwardHeld = forwardHeld;
        this.sprinting = sprinting;
        this.forwardInput = forwardHeld ? 1 : 0;
    }
    public boolean jumpHeld() { return jumpHeld; }
    public boolean jumpPressed() { return jumpHeld && !previousJumpHeld; }
    public boolean jumpReleased() { return !jumpHeld && previousJumpHeld; }
    public void finishInputTick() { previousJumpHeld = jumpHeld; }
    public boolean consumeDashRequest() { boolean value = dashRequested; dashRequested = false; return value; }
    public boolean forwardHeld() { return forwardHeld; }
    public boolean sprinting() { return sprinting; }
    public boolean runningAnimation() { return runningAnimation; }
    public void setRunningAnimation(boolean value) { runningAnimation = value; }
    /** Reports only the first tick of a sprint collision, preventing wall-impact spam. */
    public boolean updateSprintCollision(boolean colliding) {
        boolean started = colliding && !sprintColliding;
        sprintColliding = colliding;
        return started;
    }
    public boolean beginWallRun(double normalX, double normalZ) {
        boolean started = mode != Mode.WALL_RUN;
        double length = Math.sqrt(normalX * normalX + normalZ * normalZ);
        if (length > 1.0E-5D) {
            wallNormalX = normalX / length;
            wallNormalZ = normalZ / length;
        }
        mode = Mode.WALL_RUN;
        return started;
    }
    public boolean wallRunning() { return mode == Mode.WALL_RUN; }
    public double wallNormalX() { return wallNormalX; }
    public double wallNormalZ() { return wallNormalZ; }
    public boolean endWallRun() {
        if (mode != Mode.WALL_RUN) return false;
        mode = Mode.NORMAL;
        wallNormalX = 0.0D;
        wallNormalZ = 0.0D;
        wallChargeTicks = 0;
        return true;
    }
    public int chargeTicks() { return chargeTicks; }
    public void beginCharge() { jumpInput.claimCharge(); chargeTicks = 1; runningCharge=false; chargeOffGroundTicks = 0; mode = Mode.CHARGING_JUMP; }
    /** Advances toward maximum once and reports the exact tick maximum is reached. */
    public boolean tickCharge(int maximum) {
        int cappedMaximum = Math.max(1, maximum);
        int before = chargeTicks;
        if (chargeTicks > 0 && chargeTicks < cappedMaximum) chargeTicks++;
        return before < cappedMaximum && chargeTicks == cappedMaximum;
    }
    public boolean isFullyCharged(int maximum) {
        return chargeTicks > 0 && chargeTicks >= Math.max(1, maximum);
    }
    public int observeChargeOffGround() { return ++chargeOffGroundTicks; }
    public void observeChargeOnGround() { chargeOffGroundTicks = 0; }
    public int chargeOffGroundTicks() { return chargeOffGroundTicks; }
    public int releaseCharge() { int value = chargeTicks; chargeTicks = 0; chargeOffGroundTicks = 0; mode = Mode.NORMAL; return value; }
    public void cancelCharge() { chargeTicks = 0; chargeOffGroundTicks = 0; mode = Mode.NORMAL; }
    public int dashTicks() { return dashTicks; }
    public void beginDash(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-6D) {
            dashDirectionX = 0.0D;
            dashDirectionZ = 1.0D;
        } else {
            dashDirectionX = x / length;
            dashDirectionZ = z / length;
        }
        dashTicks = MovementTuningSettings.airDashTicks();
        mode = Mode.AIR_DASH;
        airDashes--;
    }
    public void tickDash() { if (dashTicks > 0) dashTicks--; if (dashTicks == 0) mode = Mode.NORMAL; }
    /** Ends the current dash immediately (e.g. the pounce reached its target). */
    public void endDash() { dashTicks = 0; mode = Mode.NORMAL; }
    public boolean canAirDash() { return airDashes > 0 && dashTicks == 0; }
    public void setMaxAirDashes(int value) { maxAirDashes = Math.max(1, value); }
    public double dashDirectionX() { return dashDirectionX; }
    public double dashDirectionZ() { return dashDirectionZ; }
    public void land() {
        airDashes = maxAirDashes;
        dashTicks = 0;
        wallNormalX = 0.0D;
        wallNormalZ = 0.0D;
        if (chargeTicks == 0) mode = Mode.NORMAL;
    }
    /**
     * Records the fastest downward velocity while airborne and returns it once on
     * the first grounded tick. This edge-triggered value drives landing feedback
     * without manufacturing a grounded state or trusting the already-zeroed
     * velocity on the landing tick.
     */
    public double observeGroundContact(boolean onGround, double verticalVelocity) {
        return observeLanding(onGround, verticalVelocity, Double.NaN).downwardSpeed();
    }
    public LandingImpact.Observation observeLanding(boolean onGround, double verticalVelocity,
                                                    double currentY) {
        if (!onGround) {
            if (!airborne && Double.isFinite(currentY)) airborneApexY = currentY;
            airborne = true;
            if (Double.isFinite(verticalVelocity)) {
                peakFallSpeed = Math.max(peakFallSpeed, Math.max(0.0D, -verticalVelocity));
            }
            if (Double.isFinite(currentY)) {
                airborneApexY = Double.isFinite(airborneApexY)
                        ? Math.max(airborneApexY, currentY) : currentY;
            }
            return LandingImpact.Observation.NONE;
        }
        if (!airborne) return LandingImpact.Observation.NONE;
        airborne = false;
        double impactSpeed = peakFallSpeed;
        double fallDistance = Double.isFinite(currentY) && Double.isFinite(airborneApexY)
                ? Math.max(0.0D, airborneApexY - currentY) : 0.0D;
        peakFallSpeed = 0.0D;
        airborneApexY = Double.NaN;
        return new LandingImpact.Observation(impactSpeed, fallDistance);
    }
    public double peakFallSpeed() { return peakFallSpeed; }
    public void beginLandingRecovery(LandingImpact.Strength strength) {
        landingStrength = strength == null ? LandingImpact.Strength.NONE : strength;
        landingRecoveryTicks = LandingImpact.recoveryTicks(landingStrength);
    }
    public int landingRecoveryTicks() { return landingRecoveryTicks; }
    public LandingImpact.Strength landingStrength() { return landingStrength; }
    public void tickLandingRecovery() {
        if (landingRecoveryTicks > 0) landingRecoveryTicks--;
        if (landingRecoveryTicks == 0) landingStrength = LandingImpact.Strength.NONE;
    }
    public void cancelLandingRecovery() {
        landingRecoveryTicks = 0;
        landingStrength = LandingImpact.Strength.NONE;
    }
    public Mode mode() { return mode; }
    public void setMode(Mode mode) { if (dashTicks == 0 && chargeTicks == 0) this.mode = mode; }
    public void reset() { hopTicks=hopCooldown=0; clearSprintDrive(); cameraYaw=Float.NaN; jumpInput.reset(); wallTraversal.reset();forwardInput=sidewaysInput=0;detachHeld=false; jumpHeld = false; previousJumpHeld = false; dashRequested = false; forwardHeld = false; sprinting = false; chargeTicks = 0; chargeOffGroundTicks = 0; dashTicks = 0; airDashes = maxAirDashes; dashDirectionX = 0.0D; dashDirectionZ = 0.0D; runningAnimation = false; sprintColliding = false; wallNormalX = 0.0D; wallNormalZ = 0.0D; airborne = false; peakFallSpeed = 0.0D; airborneApexY = Double.NaN; landingRecoveryTicks = 0; landingStrength = LandingImpact.Strength.NONE; wallStallTicks = 0; lastWallY = Double.NaN; wallJumpCooldownTicks = 0; wallChargeTicks = 0; wallVaultTicks = 0; mode = Mode.NORMAL; }
}
