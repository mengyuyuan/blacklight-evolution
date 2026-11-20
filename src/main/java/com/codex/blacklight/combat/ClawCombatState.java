package com.codex.blacklight.combat;

/** Runtime-only server state for the claw combo. Persistent player progression lives elsewhere. */
public final class ClawCombatState {
    public enum Action {
        IDLE,
        AUTO_1,
        AUTO_2,
        AUTO_3,
        GROUND_SPIKE_CHARGING,
        GROUND_SPIKE,
        SPRINT_ATTACK,
        DASHING_SLICE,
        TRANSFORM
    }

    private Action action = Action.IDLE;
    private java.util.UUID attackId;
    java.util.UUID attackId(){return action==Action.IDLE?null:attackId;}
    private int elapsedTicks;
    private int comboIndex = -1;
    private int comboGraceTicks;
    private boolean queuedAttack;
    private boolean impactTriggered;
    private double dashX;
    private double dashZ;
    private int lastTransformTick = Integer.MIN_VALUE / 2;
    private int groundSpikeOffGroundTicks;

    public void tickIdle() {
        if (action != Action.IDLE) return;
        if (comboGraceTicks > 0) comboGraceTicks--;
        if (comboGraceTicks == 0) comboIndex = -1;
    }

    public void advance() {
        if (action != Action.IDLE) elapsedTicks++;
    }

    public Action beginNextAttack() {
        attackId=java.util.UUID.randomUUID();
        comboIndex = (comboIndex + 1) % 3;
        action = switch (comboIndex) {
            case 0 -> Action.AUTO_1;
            case 1 -> Action.AUTO_2;
            default -> Action.AUTO_3;
        };
        elapsedTicks = 0;
        comboGraceTicks = 0;
        queuedAttack = false;
        impactTriggered = false;
        return action;
    }

    public void begin(Action next) {
        action = next;
        attackId=java.util.UUID.randomUUID();
        elapsedTicks = 0;
        queuedAttack = false;
        impactTriggered = false;
        groundSpikeOffGroundTicks = 0;
        if (next != Action.AUTO_1 && next != Action.AUTO_2 && next != Action.AUTO_3) {
            comboIndex = -1;
            comboGraceTicks = 0;
        }
    }

    public void beginGroundSpikeCharge() {
        begin(Action.GROUND_SPIKE_CHARGING);
    }

    public boolean canReleaseGroundSpike(int minimumChargeTicks) {
        return action == Action.GROUND_SPIKE_CHARGING
                && elapsedTicks >= Math.max(1, minimumChargeTicks);
    }

    /** Keeps an indefinitely held groundspike charge at its planted timing marker. */
    public void holdGroundSpikeChargeAt(int maximumTicks) {
        if (action == Action.GROUND_SPIKE_CHARGING) {
            elapsedTicks = Math.min(elapsedTicks, Math.max(1, maximumTicks));
        }
    }

    /** Tracks consecutive off-ground ticks during a groundspike charge for a short grace window. */
    public int observeGroundSpikeOffGround() { return ++groundSpikeOffGroundTicks; }
    public void observeGroundSpikeOnGround() { groundSpikeOffGroundTicks = 0; }

    /** Minimum interval between claw morph toggles, keyed on the player's tick count. */
    public boolean canToggleTransform(int playerTickCount) {
        return playerTickCount - lastTransformTick >= 10;
    }
    public void markTransformToggled(int playerTickCount) { lastTransformTick = playerTickCount; }

    /** True while the post-combo grace window keeps the chain alive. */
    public boolean inComboGrace() { return comboGraceTicks > 0; }

    public void beginGroundSpikeRecovery() {
        begin(Action.GROUND_SPIKE);
        impactTriggered = true;
    }

    public void beginDash(double x, double z) {
        beginDirectional(Action.DASHING_SLICE, x, z);
    }

    public void beginSprintAttack(double x, double z) {
        beginDirectional(Action.SPRINT_ATTACK, x, z);
    }

    /** Locks a combo swing to the facing direction captured on its first frame. */
    public void setAttackDirection(double x, double z) {
        setDirection(x, z);
    }

    private void beginDirectional(Action action, double x, double z) {
        begin(action);
        setDirection(x, z);
    }

    private void setDirection(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-6D) {
            dashX = 0.0D;
            dashZ = 1.0D;
        } else {
            dashX = x / length;
            dashZ = z / length;
        }
    }

    public void finish(int comboGrace) {
        action = Action.IDLE;
        elapsedTicks = 0;
        queuedAttack = false;
        impactTriggered = false;
        dashX = 0.0D;
        dashZ = 0.0D;
        groundSpikeOffGroundTicks = 0;
        comboGraceTicks = Math.max(0, comboGrace);
    }

    public void reset() {
        action = Action.IDLE;
        elapsedTicks = 0;
        comboIndex = -1;
        comboGraceTicks = 0;
        queuedAttack = false;
        impactTriggered = false;
        dashX = 0.0D;
        dashZ = 0.0D;
        groundSpikeOffGroundTicks = 0;
    }

    public Action action() { return action; }
    public int elapsedTicks() { return elapsedTicks; }
    public int comboIndex() { return comboIndex; }
    public boolean queuedAttack() { return queuedAttack; }
    public void queueAttack() { queuedAttack = true; }
    public boolean impactTriggered() { return impactTriggered; }
    public void markImpactTriggered() { impactTriggered = true; }
    public double dashX() { return dashX; }
    public double dashZ() { return dashZ; }
}
