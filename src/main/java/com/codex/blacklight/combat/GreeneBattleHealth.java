package com.codex.blacklight.combat;

/** P1 e08m04 normal outer path10 ->19 ->30 ->38, without owning the entity's
 * animation/event clock. Health amounts use the source6000 core scale. Native
 * restore/external-event path10 ->16 is deliberately not inferred from wave2.
 * See greene-mission-stage-reachability-proof.json for the original branches. */
public final class GreeneBattleHealth {
    public static final int INITIAL_WAVE=1,FINAL_FLOOR_DELAY_TICKS=60;
    public static final float SOURCE_CORE_HEALTH=6000F,SOURCE_STUN_MARGIN=50F,SOURCE_LAST_STAGE_MIN=500F;
    // Lua numbers contain these decoded float constants, then use double math.
    public static final double STUN_FRACTION=.08500000089406967D;
    public static final double RECOVERY_FRACTION=.25999999046325684D;
    public static final double FIRST_FLOOR_FRACTION=1D-STUN_FRACTION-RECOVERY_FRACTION;
    public static final double SECOND_NORMAL_FLOOR_FRACTION=1D-3D*(STUN_FRACTION+RECOVERY_FRACTION);
    private static final float[] SEGMENT_HEALTH={500F,700F,900F,1000F};

    private GreeneBattleHealth(){}

    public static float sourceToMinecraft(double sourceAmount,float maxHealth){
        if(!Double.isFinite(sourceAmount)||!validMax(maxHealth))return 0;
        return (float)(sourceAmount/SOURCE_CORE_HEALTH*maxHealth);
    }

    /** waveCount starts1, increments on DOWN entry, and supplies the NEXT
     * emergence's segment health. Keep the current armour-wave index separate
     * until those segments are actually replaced. The last tier is unbounded. */
    public static int nextWaveCount(int waveCount){return waveCount<Integer.MAX_VALUE?Math.max(INITIAL_WAVE,waveCount)+1:Integer.MAX_VALUE;}
    public static float segmentHealth(int armourWave,float maxHealth){
        return sourceToMinecraft(SEGMENT_HEALTH[Math.min(SEGMENT_HEALTH.length,Math.max(INITIAL_WAVE,armourWave))-1],maxHealth);
    }

    /** Floor requested by the collapse event, before DOWN. Wave2 corresponds
     * to outer30/STAGE3, not the otherwise reachable outer16/STAGE2 (.31).
     * A negative source floor is retained here; the MC deduction clamp treats
     * it as no positive survival floor. For wave3+, retain the previous floor
     * until finalFloorDelayTicks has elapsed, then apply this zero. */
    public static float collapseFloor(int armourWave,float maxHealth){
        if(!validMax(maxHealth))return 0;
        return (float)(maxHealth*(armourWave<=1?FIRST_FLOOR_FRACTION:armourWave==2?SECOND_NORMAL_FLOOR_FRACTION:0));
    }
    /** The source waits out the player's Devastator before starting this delay.
     * It starts at collapse-event handling, not after the125tick fall. */
    public static int finalFloorDelayTicks(int armourWave){return armourWave>=3?FINAL_FLOOR_DELAY_TICKS:0;}
    public static boolean releaseFloorOnEmergence(float health,float maxHealth){
        return validHealth(health)&&validMax(maxHealth)&&health<=sourceToMinecraft(SOURCE_LAST_STAGE_MIN,maxHealth);
    }

    /** Capture before scripted stun damage and before the Devastator wait.
     * The entity must call this exactly once per DOWN transition and persist
     * its returned values; reloading an active DOWN cannot debit stun again. */
    public record DownEntry(int nextWaveCount,float initialStunnedHealth){}
    public static DownEntry enterDown(int waveCount,float currentHealth){
        return new DownEntry(nextWaveCount(waveCount),validHealth(currentHealth)?currentHealth:0);
    }
    /** Call after the Devastator wait using the then-current health. The source
     * strict comparison uses a50 source-HP margin; equality chooses half. */
    public static float stunDamage(float currentHealth,float maxHealth){
        if(!validHealth(currentHealth)||currentHealth<=0||!validMax(maxHealth))return 0;
        double stun=STUN_FRACTION*maxHealth;
        double margin=(double)SOURCE_STUN_MARGIN/SOURCE_CORE_HEALTH*maxHealth;
        return (float)(stun+margin<currentHealth?stun:currentHealth/2D);
    }
    /** Evaluate after an accepted health deduction, including the stun debit.
     * This requests recovery; it never clamps the triggering hit to26% and
     * never asks a dead core to recover. The caller owns Devastator deferral. */
    public static boolean recoveryRequested(float initialStunnedHealth,float currentHealth,float maxHealth){
        return validHealth(initialStunnedHealth)&&validHealth(currentHealth)&&currentHealth>0&&validMax(maxHealth)
                &&initialStunnedHealth/(double)maxHealth-currentHealth/(double)maxHealth>=RECOVERY_FRACTION;
    }

    /** Forge's LivingDamageEvent exposes this post-absorption amount. Clamp
     * before setHealth/death processing, never by resurrecting after hurt.
     * Zero/negative floors preserve overkill. A floor above current health is
     * left to the actuallyHurt final-write guard: the native next damage
     * write can raise health to that floor, and a zero amount would skip it. */
    public static float limitFinalDamage(float health,float incomingFinal,float floor){
        if(!validHealth(health)||!Float.isFinite(incomingFinal)||incomingFinal<=0)return 0;
        if(!Float.isFinite(floor)||floor<=0||floor>health)return incomingFinal;
        float allowed=Math.min(incomingFinal,Math.max(0,health-floor));
        if(health-allowed<floor)allowed=Math.max(0,Math.nextDown(allowed));
        return allowed;
    }
    /** Neo's ABSORPTION reduction modifier runs after all damage-Pre listeners.
     * Preserve its passed absorption spend while limiting only health loss;
     * Neo subtracts that returned absorption from this new damage afterward. */
    public static float limitBeforeAbsorption(float health,float incoming,float absorption,float floor){
        if(!validHealth(health)||!Float.isFinite(incoming)||incoming<=0)return 0;
        if(!Float.isFinite(floor)||floor<=0||floor>health)return incoming;
        float absorbed=Float.isFinite(absorption)?Math.max(0,Math.min(absorption,incoming)):0;
        float finalAmount=Math.max(0,incoming-absorbed);
        float limited=limitFinalDamage(health,finalAmount,floor);
        if(limited>=finalAmount)return incoming;
        float result=Math.min(incoming,absorbed+limited);
        // The addition/subtraction pair may round differently from the desired
        // final amount. One adjacent float downward restores the upper bound.
        if(result-Math.min(absorbed,result)>limited)result=Math.max(0,Math.nextDown(result));
        return result;
    }
    private static boolean validMax(float value){return Float.isFinite(value)&&value>0;}
    private static boolean validHealth(float value){return Float.isFinite(value)&&value>=0;}
}
