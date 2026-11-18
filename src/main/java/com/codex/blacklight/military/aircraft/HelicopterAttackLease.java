package com.codex.blacklight.military.aircraft;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Isolated P1 strafe token semantics (10383fa0 / 10384040 / 10382ee0).
 * A future server coordinator must own one instance per ServerLevel and advance it
 * once per simulation step, never once per aircraft. That world scope is an MC
 * adaptation of P1's one active-level singleton. This class is not wired to combat.
 */
final class HelicopterAttackLease {
    private UUID owner;
    private float remainingSeconds=-1;

    /** A free or stale token is available even without prior ownership; this does not acquire it. */
    boolean available(UUID actor,Predicate<UUID> handleExists){return access(actor,-1,handleExists);}

    /** HaveStrafeRunToken(Request=true): also overwrites a longer lease held by this same actor. */
    boolean request(UUID actor,Predicate<UUID> handleExists){return access(actor,.5F,handleExists);}

    /** Actual acquisition result. The native refresh action's update callback ignores a denial. */
    boolean refresh(UUID actor,Predicate<UUID> handleExists){return access(actor,2F,handleExists);}

    private boolean access(UUID actor,float seconds,Predicate<UUID> handleExists){
        Objects.requireNonNull(actor);Objects.requireNonNull(handleExists);
        // Native handles validate index, generation and non-null entity pointer, not target or team.
        if(owner!=null&&handleExists.test(owner)&&!owner.equals(actor))return false;
        if(seconds>0){owner=actor;remainingSeconds=seconds;}
        return true;
    }

    void advance(float simulationSeconds){
        // MC boundary guard. Ordinary source float subtraction and the strict expiry threshold stay intact.
        if(!Float.isFinite(simulationSeconds)||simulationSeconds<0)throw new IllegalArgumentException("Invalid simulation delta");
        if(remainingSeconds>=0){
            remainingSeconds-=simulationSeconds;
            if(remainingSeconds<0)owner=null;
        }
    }

    UUID owner(){return owner;}
    float remainingSeconds(){return remainingSeconds;}
}
